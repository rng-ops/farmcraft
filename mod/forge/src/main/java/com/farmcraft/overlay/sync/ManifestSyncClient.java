package com.farmcraft.overlay.sync;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.farmcraft.overlay.registry.DefinitionEntry;
import com.farmcraft.overlay.registry.DefinitionRegistry;
import com.farmcraft.overlay.registry.DefinitionStatus;
import com.farmcraft.overlay.registry.DefinitionType;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Client for syncing definitions from the manifest distribution server.
 * 
 * Handles:
 * - Fetching manifests and their entries
 * - Delta sync based on version
 * - Content fetching by CID
 * - Retry and error handling
 */
public class ManifestSyncClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(ManifestSyncClient.class);

    private final String baseUrl;
    private final HttpClient httpClient;
    private final Gson gson;
    private final ExecutorService executor;

    private static final int DEFAULT_TIMEOUT_SECONDS = 30;
    private static final int MAX_RETRIES = 3;

    public ManifestSyncClient(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS))
                .build();
        this.gson = new Gson();
        this.executor = Executors.newFixedThreadPool(4);
    }

    // ========== Manifest Operations ==========

    /**
     * Fetch list of available manifests.
     */
    public CompletableFuture<List<ManifestInfo>> getManifests() {
        return get("/manifests").thenApply(response -> {
            List<ManifestInfo> manifests = new ArrayList<>();
            JsonArray array = JsonParser.parseString(response).getAsJsonArray();
            for (JsonElement element : array) {
                manifests.add(parseManifestInfo(element.getAsJsonObject()));
            }
            return manifests;
        });
    }

    /**
     * Fetch a specific manifest with its entries.
     */
    public CompletableFuture<ManifestSnapshot> getManifest(String manifestId) {
        return get("/manifests/" + manifestId).thenApply(response -> {
            JsonObject json = JsonParser.parseString(response).getAsJsonObject();
            return parseManifestSnapshot(json);
        });
    }

    /**
     * Fetch manifest snapshot (for delta sync).
     */
    public CompletableFuture<ManifestSnapshot> getManifestSnapshot(String manifestId) {
        return get("/manifests/" + manifestId + "/snapshot").thenApply(response -> {
            JsonObject json = JsonParser.parseString(response).getAsJsonObject();
            return parseManifestSnapshot(json);
        });
    }

    /**
     * Fetch delta since a specific version.
     */
    public CompletableFuture<ManifestDelta> getManifestDelta(String manifestId, int sinceVersion) {
        return get("/manifests/" + manifestId + "/delta?since=" + sinceVersion).thenApply(response -> {
            JsonObject json = JsonParser.parseString(response).getAsJsonObject();
            return parseManifestDelta(json);
        });
    }

    // ========== Content Operations ==========

    /**
     * Fetch content by CID.
     */
    public CompletableFuture<JsonObject> getContent(String cid) {
        return get("/content/" + cid).thenApply(response -> JsonParser.parseString(response).getAsJsonObject());
    }

    /**
     * Fetch multiple contents in batch.
     */
    public CompletableFuture<List<JsonObject>> getContents(List<String> cids) {
        List<CompletableFuture<JsonObject>> futures = cids.stream()
                .map(this::getContent)
                .toList();

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream()
                        .map(CompletableFuture::join)
                        .toList());
    }

    // ========== Definition Operations ==========

    /**
     * Fetch all items.
     */
    public CompletableFuture<List<DefinitionEntry>> getItems(Optional<DefinitionStatus> status) {
        String path = "/definitions/items";
        if (status.isPresent()) {
            path += "?status=" + status.get().name();
        }
        return get(path).thenApply(this::parseDefinitionList);
    }

    /**
     * Fetch all spells.
     */
    public CompletableFuture<List<DefinitionEntry>> getSpells(Optional<DefinitionStatus> status) {
        String path = "/definitions/spells";
        if (status.isPresent()) {
            path += "?status=" + status.get().name();
        }
        return get(path).thenApply(this::parseDefinitionList);
    }

    // ========== Sync Operations ==========

    /**
     * Perform a full sync of all active definitions.
     */
    public CompletableFuture<SyncResult> fullSync() {
        LOGGER.info("Starting full sync from {}", baseUrl);

        return CompletableFuture.supplyAsync(() -> {
            SyncResult result = new SyncResult();
            DefinitionRegistry registry = DefinitionRegistry.getInstance();

            try {
                // Fetch all active items
                List<DefinitionEntry> items = getItems(Optional.of(DefinitionStatus.ACTIVE)).join();
                for (DefinitionEntry item : items) {
                    registry.put(item);
                    result.added++;
                }

                // Fetch all active spells
                List<DefinitionEntry> spells = getSpells(Optional.of(DefinitionStatus.ACTIVE)).join();
                for (DefinitionEntry spell : spells) {
                    registry.put(spell);
                    result.added++;
                }

                registry.setLastSyncTime(System.currentTimeMillis());
                result.success = true;
                LOGGER.info("Full sync complete: {} entries added", result.added);

            } catch (Exception e) {
                LOGGER.error("Full sync failed", e);
                result.error = e.getMessage();
            }

            return result;
        }, executor);
    }

    /**
     * Perform a delta sync based on manifest version.
     */
    public CompletableFuture<SyncResult> deltaSync(String manifestId) {
        DefinitionRegistry registry = DefinitionRegistry.getInstance();
        int currentVersion = registry.getSyncVersion();

        LOGGER.info("Starting delta sync for manifest {} from version {}", manifestId, currentVersion);

        return getManifestDelta(manifestId, currentVersion).thenApply(delta -> {
            SyncResult result = new SyncResult();

            try {
                // Add new entries
                for (ManifestEntryInfo entry : delta.added()) {
                    getContent(entry.cid()).thenAccept(content -> {
                        DefinitionEntry def = buildDefinitionEntry(entry, content);
                        registry.put(def);
                        result.added++;
                    }).join();
                }

                // Update modified entries
                for (ManifestEntryInfo entry : delta.modified()) {
                    getContent(entry.cid()).thenAccept(content -> {
                        DefinitionEntry def = buildDefinitionEntry(entry, content);
                        registry.put(def);
                        result.updated++;
                    }).join();
                }

                // Remove deprecated entries
                for (String cid : delta.removed()) {
                    registry.remove(cid);
                    result.removed++;
                }

                registry.setSyncVersion(delta.version());
                registry.setLastSyncTime(System.currentTimeMillis());
                result.success = true;

                LOGGER.info("Delta sync complete: +{} ~{} -{}",
                        result.added, result.updated, result.removed);

            } catch (Exception e) {
                LOGGER.error("Delta sync failed", e);
                result.error = e.getMessage();
            }

            return result;
        });
    }

    // ========== HTTP Helpers ==========

    private CompletableFuture<String> get(String path) {
        return get(path, 0);
    }

    private CompletableFuture<String> get(String path, int retryCount) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS))
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        return response.body();
                    }
                    throw new RuntimeException("HTTP " + response.statusCode() + ": " + response.body());
                })
                .exceptionally(e -> {
                    if (retryCount < MAX_RETRIES) {
                        LOGGER.warn("Request failed, retrying ({}/{}): {}", retryCount + 1, MAX_RETRIES, path);
                        try {
                            Thread.sleep(1000 * (retryCount + 1));
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                        }
                        return get(path, retryCount + 1).join();
                    }
                    throw new RuntimeException("Max retries exceeded for " + path, e);
                });
    }

    // ========== Parsing Helpers ==========

    private ManifestInfo parseManifestInfo(JsonObject json) {
        return new ManifestInfo(
                json.get("id").getAsString(),
                json.get("name").getAsString(),
                json.has("description") ? json.get("description").getAsString() : "",
                json.get("version").getAsInt(),
                json.has("entryCount") ? json.get("entryCount").getAsInt() : 0);
    }

    private ManifestSnapshot parseManifestSnapshot(JsonObject json) {
        ManifestInfo info = parseManifestInfo(json);
        List<ManifestEntryInfo> entries = new ArrayList<>();

        if (json.has("entries")) {
            for (JsonElement element : json.getAsJsonArray("entries")) {
                entries.add(parseManifestEntryInfo(element.getAsJsonObject()));
            }
        }

        return new ManifestSnapshot(info, entries);
    }

    private ManifestEntryInfo parseManifestEntryInfo(JsonObject json) {
        return new ManifestEntryInfo(
                json.get("cid").getAsString(),
                DefinitionType.fromString(json.get("type").getAsString()),
                json.get("name").getAsString(),
                json.get("version").getAsInt(),
                DefinitionStatus.fromString(json.get("status").getAsString()),
                json.has("author") ? json.get("author").getAsString() : "Unknown");
    }

    private ManifestDelta parseManifestDelta(JsonObject json) {
        List<ManifestEntryInfo> added = new ArrayList<>();
        List<ManifestEntryInfo> modified = new ArrayList<>();
        List<String> removed = new ArrayList<>();

        if (json.has("added")) {
            for (JsonElement e : json.getAsJsonArray("added")) {
                added.add(parseManifestEntryInfo(e.getAsJsonObject()));
            }
        }
        if (json.has("modified")) {
            for (JsonElement e : json.getAsJsonArray("modified")) {
                modified.add(parseManifestEntryInfo(e.getAsJsonObject()));
            }
        }
        if (json.has("removed")) {
            for (JsonElement e : json.getAsJsonArray("removed")) {
                removed.add(e.getAsString());
            }
        }

        return new ManifestDelta(
                json.get("version").getAsInt(),
                added, modified, removed);
    }

    private List<DefinitionEntry> parseDefinitionList(String response) {
        List<DefinitionEntry> entries = new ArrayList<>();
        JsonArray array = JsonParser.parseString(response).getAsJsonArray();

        for (JsonElement element : array) {
            JsonObject json = element.getAsJsonObject();
            entries.add(parseDefinitionEntry(json));
        }

        return entries;
    }

    private DefinitionEntry parseDefinitionEntry(JsonObject json) {
        return DefinitionEntry.builder()
                .cid(json.has("cid") ? json.get("cid").getAsString() : null)
                .type(DefinitionType.fromString(json.get("type").getAsString()))
                .name(json.get("name").getAsString())
                .description(json.has("description") ? json.get("description").getAsString() : "")
                .author(json.has("author") ? json.get("author").getAsString() : "Unknown")
                .version(json.has("version") ? json.get("version").getAsInt() : 1)
                .status(DefinitionStatus.fromString(json.get("status").getAsString()))
                .content(json.has("content") ? json.getAsJsonObject("content") : json)
                .build();
    }

    private DefinitionEntry buildDefinitionEntry(ManifestEntryInfo entry, JsonObject content) {
        return DefinitionEntry.builder()
                .cid(entry.cid())
                .type(entry.type())
                .name(entry.name())
                .description(content.has("description") ? content.get("description").getAsString() : "")
                .author(entry.author())
                .version(entry.version())
                .status(entry.status())
                .content(content)
                .build();
    }

    // ========== Shutdown ==========

    public void shutdown() {
        executor.shutdown();
    }

    // ========== Data Classes ==========

    public record ManifestInfo(String id, String name, String description, int version, int entryCount) {
    }

    public record ManifestSnapshot(ManifestInfo info, List<ManifestEntryInfo> entries) {
    }

    public record ManifestEntryInfo(String cid, DefinitionType type, String name, int version,
            DefinitionStatus status, String author) {
    }

    public record ManifestDelta(int version, List<ManifestEntryInfo> added,
            List<ManifestEntryInfo> modified, List<String> removed) {
    }

    public static class SyncResult {
        public boolean success = false;
        public int added = 0;
        public int updated = 0;
        public int removed = 0;
        public String error = null;

        @Override
        public String toString() {
            if (success) {
                return String.format("Sync successful: +%d ~%d -%d", added, updated, removed);
            } else {
                return "Sync failed: " + error;
            }
        }
    }
}
