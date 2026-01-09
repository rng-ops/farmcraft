package com.farmcraft.overlay.fog;

import com.farmcraft.overlay.OverlayManager;
import com.farmcraft.overlay.OverlayTypes.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Fog-of-War P2P discovery manager.
 * 
 * DESIGN:
 * - Pure P2P, no server required
 * - Topic-based discovery using DHT-style routing
 * - k-anonymity through corroboration requirements
 * - Automatic decay of stale entries
 * 
 * PRIVACY:
 * - Topics derived from coarse buckets (dimension, biome category, time)
 * - No coordinates, no usernames, no UUIDs
 * - Session-ephemeral peer IDs
 * - k-anonymity threshold (default 3) before showing
 * 
 * TOPIC DERIVATION:
 * topic_id = H("farmcraft-topic" || condition_bucket || time_bucket)
 */
@OnlyIn(Dist.CLIENT)
public class FogOfWarManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(FogOfWarManager.class);

    // k-anonymity threshold - minimum corroborations before showing
    private static final int K_ANONYMITY_THRESHOLD = 3;

    // Time bucket size in minutes (from config, default 30)
    private int timeBucketMinutes = 30;

    // Data decay hours (from config, default 72)
    private int dataDecayHours = 72;

    private final OverlayManager overlayManager;

    // Discovered fog shards by topic
    private final ConcurrentHashMap<String, Set<FogShard>> shardsByTopic = new ConcurrentHashMap<>();

    // Corroboration tracking
    private final ConcurrentHashMap<String, CorroborationTracker> corroborations = new ConcurrentHashMap<>();

    // Our current condition bucket (for announcing)
    private volatile ConditionBucket currentCondition;

    // Our current topic (derived from condition + time)
    private volatile String currentTopic;

    public FogOfWarManager(OverlayManager overlayManager) {
        this.overlayManager = overlayManager;
    }

    /**
     * Update our current condition based on game state.
     * Called periodically or on significant state changes.
     */
    public void updateCondition() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            currentCondition = null;
            currentTopic = null;
            return;
        }

        ConditionBucket newCondition = computeConditionBucket(mc.level);

        if (!Objects.equals(newCondition, currentCondition)) {
            currentCondition = newCondition;
            currentTopic = deriveTopicId(newCondition);
            LOGGER.debug("Updated condition bucket, new topic: {}", currentTopic);
        }
    }

    /**
     * Compute condition bucket from current game state.
     * Uses COARSE categories for privacy.
     */
    private ConditionBucket computeConditionBucket(Level level) {
        // Dimension category
        DimensionCategory dimension;
        if (level.dimension() == Level.OVERWORLD) {
            dimension = DimensionCategory.OVERWORLD;
        } else if (level.dimension() == Level.NETHER) {
            dimension = DimensionCategory.NETHER;
        } else if (level.dimension() == Level.END) {
            dimension = DimensionCategory.END;
        } else {
            dimension = DimensionCategory.OTHER;
        }

        // Biome category (coarse)
        BiomeCategory biome = BiomeCategory.OTHER;
        var player = Minecraft.getInstance().player;
        if (player != null) {
            biome = categorizeBiome(level.getBiome(player.blockPosition()));
        }

        // Time of day bucket
        TimeOfDayBucket timeOfDay;
        long dayTime = level.getDayTime() % 24000;
        if (dayTime < 12000) {
            timeOfDay = TimeOfDayBucket.DAY;
        } else if (dayTime < 18000) {
            timeOfDay = TimeOfDayBucket.EVENING;
        } else {
            timeOfDay = TimeOfDayBucket.NIGHT;
        }

        // Capabilities hash
        String capsHash = overlayManager.getSessionIdentity().capabilitiesHash();

        return new ConditionBucket(dimension, biome, timeOfDay, capsHash);
    }

    /**
     * Categorize biome into coarse privacy-preserving category.
     */
    private BiomeCategory categorizeBiome(Holder<Biome> biomeHolder) {
        Biome biome = biomeHolder.value();

        // Use temperature-based categorization (privacy-preserving)
        float temp = biome.getBaseTemperature();

        if (temp < 0.2f) {
            return BiomeCategory.COLD;
        } else if (temp > 0.9f) {
            return BiomeCategory.HOT;
        } else if (temp >= 0.2f && temp <= 0.9f) {
            // Check if aquatic by precipitation
            return BiomeCategory.TEMPERATE;
        }

        return BiomeCategory.OTHER;
    }

    /**
     * Derive topic ID from condition bucket.
     * 
     * topic_id = H("farmcraft-topic" || condition_bucket || time_bucket)
     */
    private String deriveTopicId(ConditionBucket condition) {
        long timeBucket = OverlayManager.getCurrentTimeBucket(timeBucketMinutes);

        String input = String.format(
                "farmcraft-topic|%s|%s|%s|%s|%d",
                condition.dimension(),
                condition.biome(),
                condition.timeOfDay(),
                condition.capabilitiesHash(),
                timeBucket);

        return OverlayManager.hashToBase32(input.getBytes(StandardCharsets.UTF_8), 24);
    }

    /**
     * Announce our presence to the fog network.
     */
    public CompletableFuture<Void> announce() {
        if (currentCondition == null || currentTopic == null) {
            updateCondition();
        }

        if (currentTopic == null) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.runAsync(() -> {
            // Rate limit announcements
            if (overlayManager.shouldRateLimit("fog-announce", Duration.ofMinutes(5))) {
                LOGGER.debug("Fog announcement rate limited");
                return;
            }

            try {
                FogAnnouncement announcement = createAnnouncement();

                // In production: broadcast to DHT topic
                LOGGER.debug("Fog announcement created for topic {}", currentTopic);

            } catch (Exception e) {
                LOGGER.error("Failed to create fog announcement", e);
            }
        });
    }

    /**
     * Create a fog announcement message.
     */
    private FogAnnouncement createAnnouncement() {
        // Generate session-ephemeral peer ID
        String peerId = OverlayManager.hashToBase32(
                overlayManager.getSessionIdentity().sessionPublicKey().getEncoded(),
                16);

        String capsHash = overlayManager.getSessionIdentity().capabilitiesHash();
        String manifestSummary = ""; // Would be actual manifest CID summary
        long timeBucket = OverlayManager.getCurrentTimeBucket(timeBucketMinutes);

        // Signature would be over the announcement data
        byte[] signature = signAnnouncement(peerId, capsHash, manifestSummary, timeBucket);

        return new FogAnnouncement(peerId, capsHash, manifestSummary, timeBucket, signature);
    }

    /**
     * Sign an announcement with our session key.
     */
    private byte[] signAnnouncement(String peerId, String capsHash, String manifestSummary, long timeBucket) {
        // In production: real signature with session private key
        try {
            String data = peerId + "|" + capsHash + "|" + manifestSummary + "|" + timeBucket;
            return MessageDigest.getInstance("SHA-256").digest(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return new byte[0];
        }
    }

    /**
     * Query fog shards for current topic.
     */
    public CompletableFuture<List<FogShard>> query() {
        return query(Optional.empty());
    }

    /**
     * Query fog shards with optional filter.
     */
    public CompletableFuture<List<FogShard>> query(Optional<String> capabilitiesFilter) {
        if (currentTopic == null) {
            updateCondition();
        }

        if (currentTopic == null) {
            return CompletableFuture.completedFuture(List.of());
        }

        return CompletableFuture.supplyAsync(() -> {
            // Rate limit queries
            if (overlayManager.shouldRateLimit("fog-query", Duration.ofMinutes(1))) {
                LOGGER.debug("Fog query rate limited, returning cached");
                return getCachedShards(capabilitiesFilter);
            }

            // In production: query DHT for topic
            List<FogShard> shards = getCachedShards(capabilitiesFilter);

            // Filter by k-anonymity threshold
            shards = shards.stream()
                    .filter(s -> s.corroborations() >= K_ANONYMITY_THRESHOLD)
                    .collect(Collectors.toList());

            LOGGER.debug("Fog query returned {} shards meeting k-anonymity", shards.size());
            return shards;
        });
    }

    /**
     * Get cached shards with optional filter.
     */
    private List<FogShard> getCachedShards(Optional<String> capabilitiesFilter) {
        if (currentTopic == null)
            return List.of();

        Set<FogShard> topicShards = shardsByTopic.getOrDefault(currentTopic, Set.of());

        return topicShards.stream()
                .filter(s -> capabilitiesFilter.isEmpty() ||
                        s.capabilitiesHash().contains(capabilitiesFilter.get()))
                .filter(s -> s.expiresAt().isAfter(Instant.now()))
                .collect(Collectors.toList());
    }

    /**
     * Process a received fog announcement.
     */
    public void processAnnouncement(FogAnnouncement announcement, String sourcePeer) {
        // Verify the announcement
        if (!verifyAnnouncement(announcement)) {
            LOGGER.warn("Invalid fog announcement from {}", sourcePeer);
            return;
        }

        // Track corroboration
        String corrobKey = announcement.peerId() + "|" + announcement.capabilitiesHash();
        CorroborationTracker tracker = corroborations.computeIfAbsent(
                corrobKey, k -> new CorroborationTracker());

        int corrobCount = tracker.addSource(sourcePeer);

        // Create or update shard
        FogShard shard = new FogShard(
                announcement.peerId(),
                announcement.capabilitiesHash(),
                announcement.manifestCidSummary(),
                computeFreshness(announcement.timeBucket()),
                corrobCount,
                computeTrustTier(corrobCount),
                Instant.now(),
                Instant.now().plus(Duration.ofHours(dataDecayHours)));

        // Store in topic map
        if (currentTopic != null) {
            shardsByTopic.computeIfAbsent(currentTopic, k -> ConcurrentHashMap.newKeySet())
                    .add(shard);
        }

        LOGGER.debug("Processed fog announcement from {}, corroborations: {}",
                announcement.peerId(), corrobCount);
    }

    /**
     * Verify an announcement's signature.
     */
    private boolean verifyAnnouncement(FogAnnouncement announcement) {
        // In production: verify signature with session public key
        // For now: basic sanity checks
        return announcement.peerId() != null &&
                !announcement.peerId().isEmpty() &&
                announcement.capabilitiesHash() != null;
    }

    /**
     * Compute freshness bucket from time bucket.
     */
    private FreshnessBucket computeFreshness(long announcementTimeBucket) {
        long currentBucket = OverlayManager.getCurrentTimeBucket(timeBucketMinutes);
        long bucketDiff = currentBucket - announcementTimeBucket;

        if (bucketDiff <= 1) {
            return FreshnessBucket.RECENT;
        } else if (bucketDiff <= 48) { // ~24 hours with 30-min buckets
            return FreshnessBucket.TODAY;
        } else {
            return FreshnessBucket.THIS_WEEK;
        }
    }

    /**
     * Compute trust tier from corroboration count.
     */
    private TrustTier computeTrustTier(int corroborations) {
        if (corroborations >= 6)
            return TrustTier.HIGH;
        if (corroborations >= 3)
            return TrustTier.MEDIUM;
        if (corroborations >= 1)
            return TrustTier.LOW;
        return TrustTier.UNVERIFIED;
    }

    /**
     * Decay stale entries.
     * Called periodically by OverlayManager.
     */
    public void decayStaleEntries() {
        Instant now = Instant.now();
        int decayed = 0;

        for (Map.Entry<String, Set<FogShard>> entry : shardsByTopic.entrySet()) {
            Set<FogShard> shards = entry.getValue();
            int before = shards.size();
            shards.removeIf(s -> s.expiresAt().isBefore(now));
            decayed += before - shards.size();

            // Remove empty topics
            if (shards.isEmpty()) {
                shardsByTopic.remove(entry.getKey());
            }
        }

        // Also decay corroboration trackers
        corroborations.entrySet()
                .removeIf(e -> e.getValue().getLastUpdate().plus(Duration.ofHours(dataDecayHours)).isBefore(now));

        if (decayed > 0) {
            LOGGER.info("Decayed {} stale fog entries", decayed);
        }
    }

    /**
     * Shutdown fog manager.
     */
    public void shutdown() {
        shardsByTopic.clear();
        corroborations.clear();
        currentCondition = null;
        currentTopic = null;
    }

    public String getCurrentTopic() {
        return currentTopic;
    }

    public ConditionBucket getCurrentCondition() {
        return currentCondition;
    }

    public int getShardCount() {
        return shardsByTopic.values().stream().mapToInt(Set::size).sum();
    }

    // ═══════════════════════════════════════════════════════════════════
    // Internal Types
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Tracks corroborations from multiple sources for k-anonymity.
     */
    private static class CorroborationTracker {
        private final Set<String> sources = ConcurrentHashMap.newKeySet();
        private volatile Instant lastUpdate = Instant.now();

        public int addSource(String source) {
            sources.add(source);
            lastUpdate = Instant.now();
            return sources.size();
        }

        public int getCount() {
            return sources.size();
        }

        public Instant getLastUpdate() {
            return lastUpdate;
        }
    }
}
