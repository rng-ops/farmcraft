package com.farmcraft.overlay;

import com.farmcraft.overlay.OverlayTypes.*;
import com.farmcraft.overlay.rendezvous.RendezvousClient;
import com.farmcraft.overlay.fog.FogOfWarManager;
import com.farmcraft.overlay.coop.CooperativeHandleManager;
import com.farmcraft.overlay.friends.FriendsManager;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Central manager for the privacy-preserving overlay network.
 * 
 * ARCHITECTURE:
 * 1. RENDEZVOUS - Server-associated discovery, respects delegations
 * 2. FOG - Pure P2P, topic-hashed, k-anonymous, decaying
 * 3. FRIENDS - Mutual consent, pairwise encrypted, presence
 * 
 * PRIVACY GUARANTEES:
 * - No stable global identifiers
 * - No third-party correlation without consent
 * - All searches produce receipts
 * - Rate limiting everywhere
 * - Session keys regenerated each launch
 */
@OnlyIn(Dist.CLIENT)
public class OverlayManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(OverlayManager.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    // Singleton instance
    private static OverlayManager INSTANCE;

    // Session-ephemeral identity
    private SessionIdentity sessionIdentity;
    private KeyPair sessionKeyPair;

    // Subsystem managers
    private RendezvousClient rendezvousClient;
    private FogOfWarManager fogManager;
    private CooperativeHandleManager coopManager;
    private FriendsManager friendsManager;

    // State
    private final ConcurrentHashMap<String, OverlayHandle> cachedHandles = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Instant> rateLimitTracker = new ConcurrentHashMap<>();

    // Background tasks
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2, r -> {
        Thread t = new Thread(r, "FarmCraft-Overlay");
        t.setDaemon(true);
        return t;
    });

    private volatile boolean initialized = false;
    private volatile Instant lastActivity = Instant.now();

    private OverlayManager() {
        // Private constructor for singleton
    }

    public static synchronized OverlayManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new OverlayManager();
        }
        return INSTANCE;
    }

    /**
     * Initialize the overlay network.
     * Called on game launch.
     */
    public CompletableFuture<Void> initialize() {
        if (initialized) {
            LOGGER.warn("Overlay already initialized, regenerating session identity");
        }

        return CompletableFuture.runAsync(() -> {
            try {
                // Generate session-ephemeral keys
                regenerateSessionIdentity();

                // Initialize subsystems
                initializeSubsystems();

                // Schedule background tasks
                scheduleBackgroundTasks();

                initialized = true;
                LOGGER.info("Overlay network initialized with session ID: {}",
                        truncateKey(sessionIdentity.sessionPublicKey()));

            } catch (Exception e) {
                LOGGER.error("Failed to initialize overlay network", e);
                throw new RuntimeException("Overlay initialization failed", e);
            }
        });
    }

    /**
     * Regenerate session-ephemeral identity.
     * Called on each game launch and periodically for privacy.
     */
    private void regenerateSessionIdentity() throws NoSuchAlgorithmException {
        // Generate new Ed25519 keypair (or ECDSA if Ed25519 not available)
        KeyPairGenerator keyGen;
        try {
            keyGen = KeyPairGenerator.getInstance("Ed25519");
        } catch (NoSuchAlgorithmException e) {
            // Fallback to ECDSA
            keyGen = KeyPairGenerator.getInstance("EC");
            keyGen.initialize(256, SECURE_RANDOM);
        }

        sessionKeyPair = keyGen.generateKeyPair();

        // Create session identity
        sessionIdentity = new SessionIdentity(
                sessionKeyPair.getPublic(),
                Instant.now(),
                computeCapabilitiesHash());

        LOGGER.debug("Regenerated session identity");
    }

    /**
     * Compute capabilities hash for compatibility matching.
     */
    private String computeCapabilitiesHash() {
        // Hash of installed mods, version, etc.
        // Used for compatibility discovery without revealing exact configuration
        StringBuilder caps = new StringBuilder();
        caps.append("farmcraft-overlay-v1|");
        caps.append("mc-").append(Minecraft.getInstance().getVersionType()).append("|");
        // Add more coarse capabilities as needed

        return hashToBase32(caps.toString().getBytes(), 12);
    }

    /**
     * Initialize subsystem managers.
     */
    private void initializeSubsystems() {
        // Initialize in order of dependency
        rendezvousClient = new RendezvousClient(this);
        fogManager = new FogOfWarManager(this);
        coopManager = new CooperativeHandleManager(this);
        friendsManager = new FriendsManager(this);

        LOGGER.info("Overlay subsystems initialized");
    }

    /**
     * Schedule background maintenance tasks.
     */
    private void scheduleBackgroundTasks() {
        // Decay stale fog entries every 15 minutes
        scheduler.scheduleAtFixedRate(
                () -> safeRun("fog-decay", () -> fogManager.decayStaleEntries()),
                15, 15, TimeUnit.MINUTES);

        // Renew overlay handles before expiry (weekly rotation with 1-day buffer)
        scheduler.scheduleAtFixedRate(
                () -> safeRun("handle-renewal", this::renewExpiringHandles),
                1, 24, TimeUnit.HOURS);

        // Update friend presence every 5 minutes
        scheduler.scheduleAtFixedRate(
                () -> safeRun("presence-update", () -> friendsManager.broadcastPresence()),
                5, 5, TimeUnit.MINUTES);

        // Session key rotation (every 4 hours for long sessions)
        scheduler.scheduleAtFixedRate(
                () -> safeRun("session-rotation", this::maybeRotateSession),
                4, 4, TimeUnit.HOURS);
    }

    private void safeRun(String taskName, Runnable task) {
        try {
            task.run();
        } catch (Exception e) {
            LOGGER.error("Background task '{}' failed", taskName, e);
        }
    }

    /**
     * Rotate session identity if it's been active for too long.
     */
    private void maybeRotateSession() {
        if (Duration.between(sessionIdentity.createdAt(), Instant.now()).toHours() >= 4) {
            try {
                regenerateSessionIdentity();
                LOGGER.info("Rotated session identity for privacy");
            } catch (Exception e) {
                LOGGER.error("Failed to rotate session identity", e);
            }
        }
    }

    /**
     * Renew overlay handles that are close to expiry.
     */
    private void renewExpiringHandles() {
        Instant renewalThreshold = Instant.now().plus(Duration.ofDays(2));
        cachedHandles.forEach((key, handle) -> {
            if (handle.expiresAt().isBefore(renewalThreshold)) {
                coopManager.deriveHandle(handle.cohortId())
                        .thenAccept(newHandle -> cachedHandles.put(key, newHandle))
                        .exceptionally(e -> {
                            LOGGER.warn("Failed to renew handle for cohort {}", handle.cohortId(), e);
                            return null;
                        });
            }
        });
    }

    // ═══════════════════════════════════════════════════════════════════
    // Public API
    // ═══════════════════════════════════════════════════════════════════

    public SessionIdentity getSessionIdentity() {
        return sessionIdentity;
    }

    public KeyPair getSessionKeyPair() {
        return sessionKeyPair;
    }

    public RendezvousClient getRendezvousClient() {
        return rendezvousClient;
    }

    public FogOfWarManager getFogManager() {
        return fogManager;
    }

    public CooperativeHandleManager getCoopManager() {
        return coopManager;
    }

    public FriendsManager getFriendsManager() {
        return friendsManager;
    }

    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Check if we should rate limit an operation.
     * Returns true if the operation should be blocked.
     */
    public boolean shouldRateLimit(String operationType, Duration cooldown) {
        Instant lastTime = rateLimitTracker.get(operationType);
        Instant now = Instant.now();

        if (lastTime != null && Duration.between(lastTime, now).compareTo(cooldown) < 0) {
            return true;
        }

        rateLimitTracker.put(operationType, now);
        return false;
    }

    /**
     * Record activity timestamp.
     */
    public void recordActivity() {
        lastActivity = Instant.now();
    }

    /**
     * Get time since last activity.
     */
    public Duration timeSinceActivity() {
        return Duration.between(lastActivity, Instant.now());
    }

    /**
     * Shutdown the overlay network cleanly.
     */
    public void shutdown() {
        LOGGER.info("Shutting down overlay network");

        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        if (rendezvousClient != null)
            rendezvousClient.disconnect();
        if (fogManager != null)
            fogManager.shutdown();
        if (friendsManager != null)
            friendsManager.shutdown();

        initialized = false;
        LOGGER.info("Overlay network shutdown complete");
    }

    // ═══════════════════════════════════════════════════════════════════
    // Utility Methods
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Hash bytes to base32 string of specified length.
     */
    public static String hashToBase32(byte[] input, int length) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input);
            return bytesToBase32(hash).substring(0, Math.min(length, 52));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    /**
     * Convert bytes to base32 string.
     */
    public static String bytesToBase32(byte[] bytes) {
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
        StringBuilder result = new StringBuilder();
        int buffer = 0;
        int bitsLeft = 0;

        for (byte b : bytes) {
            buffer = (buffer << 8) | (b & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                result.append(alphabet.charAt((buffer >> (bitsLeft - 5)) & 0x1F));
                bitsLeft -= 5;
            }
        }

        if (bitsLeft > 0) {
            result.append(alphabet.charAt((buffer << (5 - bitsLeft)) & 0x1F));
        }

        return result.toString();
    }

    /**
     * Truncate public key for logging (privacy).
     */
    private static String truncateKey(java.security.PublicKey key) {
        String encoded = bytesToBase32(key.getEncoded());
        if (encoded.length() > 12) {
            return encoded.substring(0, 8) + "..." + encoded.substring(encoded.length() - 4);
        }
        return encoded;
    }

    /**
     * Compute current epoch bucket (weekly rotation).
     */
    public static long getCurrentEpochBucket() {
        return System.currentTimeMillis() / (7L * 24 * 60 * 60 * 1000);
    }

    /**
     * Compute current time bucket for fog-of-war (configurable minutes).
     */
    public static long getCurrentTimeBucket(int bucketMinutes) {
        return System.currentTimeMillis() / (bucketMinutes * 60 * 1000L);
    }
}
