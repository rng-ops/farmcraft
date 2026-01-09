package com.farmcraft.overlay.sync;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.farmcraft.overlay.registry.DefinitionEntry;
import com.farmcraft.overlay.registry.DefinitionRegistry;
import com.farmcraft.overlay.registry.DefinitionStatus;
import com.farmcraft.overlay.registry.DefinitionType;
import com.google.gson.JsonObject;

/**
 * Service for managing sync with the manifest distribution server.
 * 
 * Coordinates HTTP polling and WebSocket push updates to keep
 * the local definition registry in sync.
 */
public class SyncService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SyncService.class);

    private static SyncService INSTANCE;

    private final ManifestSyncClient httpClient;
    private final ManifestWebSocketClient wsClient;
    private final DefinitionRegistry registry;
    private final ScheduledExecutorService scheduler;

    private ScheduledFuture<?> syncTask;
    private volatile boolean running;

    // Configuration
    private String manifestServerUrl;
    private String manifestWsUrl;
    private String primaryManifestId;
    private long syncIntervalMinutes;

    // Default configuration
    private static final String DEFAULT_HTTP_URL = "http://localhost:7430";
    private static final String DEFAULT_WS_URL = "ws://localhost:7431";
    private static final String DEFAULT_MANIFEST_ID = "farmcraft-core";
    private static final long DEFAULT_SYNC_INTERVAL = 30; // minutes

    private SyncService() {
        this.manifestServerUrl = DEFAULT_HTTP_URL;
        this.manifestWsUrl = DEFAULT_WS_URL;
        this.primaryManifestId = DEFAULT_MANIFEST_ID;
        this.syncIntervalMinutes = DEFAULT_SYNC_INTERVAL;

        this.httpClient = new ManifestSyncClient(manifestServerUrl);
        this.wsClient = new ManifestWebSocketClient(manifestWsUrl, this::handleUpdate);
        this.registry = DefinitionRegistry.getInstance();
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        this.running = false;
    }

    public static synchronized SyncService getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new SyncService();
        }
        return INSTANCE;
    }

    // ========== Configuration ==========

    public void configure(String httpUrl, String wsUrl, String manifestId, long syncIntervalMinutes) {
        this.manifestServerUrl = httpUrl;
        this.manifestWsUrl = wsUrl;
        this.primaryManifestId = manifestId;
        this.syncIntervalMinutes = syncIntervalMinutes;
    }

    public String getManifestServerUrl() {
        return manifestServerUrl;
    }

    public String getPrimaryManifestId() {
        return primaryManifestId;
    }

    // ========== Lifecycle ==========

    /**
     * Start the sync service.
     */
    public CompletableFuture<Void> start() {
        if (running) {
            LOGGER.warn("Sync service already running");
            return CompletableFuture.completedFuture(null);
        }

        LOGGER.info("Starting sync service");
        running = true;

        // Connect WebSocket
        CompletableFuture<Void> wsConnect = wsClient.connect()
                .thenRun(() -> wsClient.subscribe(primaryManifestId));

        // Do initial sync
        CompletableFuture<Void> initialSync = doSync().thenAccept(result -> {
            if (result.success) {
                LOGGER.info("Initial sync complete: {}", result);
            } else {
                LOGGER.warn("Initial sync failed: {}", result.error);
            }
        });

        // Schedule periodic sync
        syncTask = scheduler.scheduleAtFixedRate(
                () -> doSync().thenAccept(result -> LOGGER.debug("Periodic sync: {}", result)),
                syncIntervalMinutes, syncIntervalMinutes, TimeUnit.MINUTES);

        return CompletableFuture.allOf(wsConnect, initialSync);
    }

    /**
     * Stop the sync service.
     */
    public void stop() {
        if (!running)
            return;

        LOGGER.info("Stopping sync service");
        running = false;

        if (syncTask != null) {
            syncTask.cancel(false);
        }

        wsClient.disconnect();
        httpClient.shutdown();
    }

    /**
     * Check if the service is running.
     */
    public boolean isRunning() {
        return running;
    }

    // ========== Sync Operations ==========

    /**
     * Perform a sync (delta or full).
     */
    public CompletableFuture<ManifestSyncClient.SyncResult> doSync() {
        if (registry.getSyncVersion() == 0) {
            // No prior sync, do full sync
            return httpClient.fullSync();
        } else {
            // Have prior version, try delta sync
            return httpClient.deltaSync(primaryManifestId)
                    .exceptionally(e -> {
                        LOGGER.warn("Delta sync failed, falling back to full sync", e);
                        return httpClient.fullSync().join();
                    });
        }
    }

    /**
     * Force a full sync.
     */
    public CompletableFuture<ManifestSyncClient.SyncResult> forceFullSync() {
        return httpClient.fullSync();
    }

    /**
     * Fetch a specific definition by CID.
     */
    public CompletableFuture<DefinitionEntry> fetchDefinition(String cid) {
        return httpClient.getContent(cid).thenApply(content -> {
            DefinitionEntry entry = DefinitionEntry.builder()
                    .cid(cid)
                    .type(DefinitionType.fromString(content.get("type").getAsString()))
                    .name(content.get("name").getAsString())
                    .description(content.has("description") ? content.get("description").getAsString() : "")
                    .author(content.has("author") ? content.get("author").getAsString() : "Unknown")
                    .version(content.has("version") ? content.get("version").getAsInt() : 1)
                    .status(DefinitionStatus.fromString(content.get("status").getAsString()))
                    .content(content)
                    .build();

            // Add to registry
            registry.put(entry);

            return entry;
        });
    }

    // ========== WebSocket Handler ==========

    private void handleUpdate(ManifestWebSocketClient.ManifestUpdate update) {
        LOGGER.debug("Received update: {} for {}", update.type(), update.manifestId());

        switch (update.type()) {
            case ENTRY_ADDED, ENTRY_UPDATED -> {
                if (update.cid() != null) {
                    fetchDefinition(update.cid())
                            .thenAccept(entry -> LOGGER.info("Definition updated: {}", entry.getSummary()));
                }
            }
            case ENTRY_REMOVED -> {
                if (update.cid() != null) {
                    registry.remove(update.cid())
                            .ifPresent(entry -> LOGGER.info("Definition removed: {}", entry.getSummary()));
                }
            }
            case MANIFEST_UPDATED -> {
                // Manifest metadata updated, may need to refresh
                LOGGER.info("Manifest updated, scheduling sync");
                scheduler.schedule(() -> doSync(), 5, TimeUnit.SECONDS);
            }
        }
    }

    // ========== Status ==========

    /**
     * Get sync status information.
     */
    public SyncStatus getStatus() {
        return new SyncStatus(
                running,
                wsClient.isConnected(),
                registry.getLastSyncTime(),
                registry.getSyncVersion(),
                registry.size());
    }

    public record SyncStatus(
            boolean running,
            boolean wsConnected,
            long lastSyncTime,
            int syncVersion,
            int registrySize) {
        public String format() {
            return String.format(
                    "Sync: %s | WS: %s | Last: %s | v%d | %d entries",
                    running ? "Running" : "Stopped",
                    wsConnected ? "Connected" : "Disconnected",
                    lastSyncTime > 0 ? new java.util.Date(lastSyncTime).toString() : "Never",
                    syncVersion,
                    registrySize);
        }
    }
}
