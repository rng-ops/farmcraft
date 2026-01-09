package com.farmcraft.overlay;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Configuration for the privacy-preserving overlay network.
 * 
 * PRIVACY GUARANTEES:
 * - Overlay is DISABLED by default (opt-in only)
 * - No silent profile views allowed
 * - No stable global identifiers
 * - All correlation requires multi-party cooperation
 * - Handles rotate with epoch changes
 */
public class OverlayConfig {

    // ═══════════════════════════════════════════════════════════════════
    // Core Settings
    // ═══════════════════════════════════════════════════════════════════

    public static final ForgeConfigSpec.BooleanValue OVERLAY_ENABLED;
    public static final ForgeConfigSpec.BooleanValue RENDEZVOUS_ENABLED;
    public static final ForgeConfigSpec.BooleanValue FOG_OF_WAR_ENABLED;
    public static final ForgeConfigSpec.BooleanValue FRIENDS_ENABLED;

    // ═══════════════════════════════════════════════════════════════════
    // Discoverability Settings
    // ═══════════════════════════════════════════════════════════════════

    public static final ForgeConfigSpec.EnumValue<DiscoverabilityScope> DISCOVERABILITY_SCOPE;
    public static final ForgeConfigSpec.BooleanValue COOPERATIVE_CORRELATION_ENABLED;
    public static final ForgeConfigSpec.IntValue CORRELATION_THRESHOLD;

    // ═══════════════════════════════════════════════════════════════════
    // Fog-of-War Settings
    // ═══════════════════════════════════════════════════════════════════

    public static final ForgeConfigSpec.IntValue FOG_TOPIC_BUCKET_MINUTES;
    public static final ForgeConfigSpec.IntValue FOG_K_ANONYMITY_THRESHOLD;
    public static final ForgeConfigSpec.IntValue FOG_DATA_DECAY_HOURS;

    // ═══════════════════════════════════════════════════════════════════
    // Presence Settings
    // ═══════════════════════════════════════════════════════════════════

    public static final ForgeConfigSpec.EnumValue<PresenceVisibility> PRESENCE_VISIBILITY;
    public static final ForgeConfigSpec.BooleanValue SHARE_LAST_SEEN_BUCKET;

    // ═══════════════════════════════════════════════════════════════════
    // Rate Limiting
    // ═══════════════════════════════════════════════════════════════════

    public static final ForgeConfigSpec.IntValue SEARCH_RATE_LIMIT_PER_HOUR;
    public static final ForgeConfigSpec.IntValue HANDLE_DERIVATION_RATE_LIMIT;

    public static final ForgeConfigSpec SPEC;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.comment("FarmCraft Privacy-Preserving Overlay Network Configuration")
                .comment("This overlay is OPTIONAL and PRIVACY-FIRST")
                .comment("No silent tracking, no third-party queries about individuals")
                .push("overlay");

        // Core settings
        builder.push("core");

        OVERLAY_ENABLED = builder
                .comment("Enable the overlay network (default OFF for privacy)")
                .define("enabled", false);

        RENDEZVOUS_ENABLED = builder
                .comment("Enable server-associated rendezvous (preferred discovery method)")
                .define("rendezvousEnabled", true);

        FOG_OF_WAR_ENABLED = builder
                .comment("Enable fog-of-war P2P discovery as fallback")
                .define("fogOfWarEnabled", true);

        FRIENDS_ENABLED = builder
                .comment("Enable friends overlay with mutual consent")
                .define("friendsEnabled", true);

        builder.pop();

        // Discoverability settings
        builder.push("discoverability");

        DISCOVERABILITY_SCOPE = builder
                .comment("Who can discover you through search")
                .comment("NONE: Not discoverable")
                .comment("FRIENDS_ONLY: Only mutual friends")
                .comment("COHORT: Same cohort members")
                .comment("PUBLIC: Anyone with valid PoW")
                .defineEnum("scope", DiscoverabilityScope.COHORT);

        COOPERATIVE_CORRELATION_ENABLED = builder
                .comment("Allow cooperative correlation handles")
                .comment("Requires multi-party cooperation to correlate identities")
                .define("cooperativeCorrelation", true);

        CORRELATION_THRESHOLD = builder
                .comment("Minimum number of federation servers required for handle derivation (t-of-n)")
                .defineInRange("correlationThreshold", 3, 2, 10);

        builder.pop();

        // Fog-of-war settings
        builder.push("fogOfWar");

        FOG_TOPIC_BUCKET_MINUTES = builder
                .comment("Topic rotation interval in minutes")
                .defineInRange("topicBucketMinutes", 10, 5, 60);

        FOG_K_ANONYMITY_THRESHOLD = builder
                .comment("Minimum corroborations before public visibility (k-anonymity)")
                .defineInRange("kAnonymityThreshold", 3, 2, 10);

        FOG_DATA_DECAY_HOURS = builder
                .comment("Hours until discovered data decays")
                .defineInRange("dataDecayHours", 24, 1, 168);

        builder.pop();

        // Presence settings
        builder.push("presence");

        PRESENCE_VISIBILITY = builder
                .comment("Presence visibility to friends")
                .comment("FULL: online/away/offline with last_seen")
                .comment("COARSE: Only coarse buckets (today, this week)")
                .comment("HIDDEN: No presence shared")
                .defineEnum("visibility", PresenceVisibility.COARSE);

        SHARE_LAST_SEEN_BUCKET = builder
                .comment("Share coarse last-seen buckets with friends")
                .define("shareLastSeenBucket", true);

        builder.pop();

        // Rate limiting
        builder.push("rateLimits");

        SEARCH_RATE_LIMIT_PER_HOUR = builder
                .comment("Maximum search requests per hour")
                .defineInRange("searchPerHour", 20, 5, 100);

        HANDLE_DERIVATION_RATE_LIMIT = builder
                .comment("Maximum handle derivations per hour")
                .defineInRange("handleDerivationsPerHour", 50, 10, 200);

        builder.pop();

        builder.pop(); // overlay

        SPEC = builder.build();
    }

    /**
     * Discoverability scope for search and correlation.
     */
    public enum DiscoverabilityScope {
        /** Not discoverable at all */
        NONE,
        /** Only mutual friends can find you */
        FRIENDS_ONLY,
        /** Same cohort members can find you */
        COHORT,
        /** Anyone with valid PoW can find you */
        PUBLIC
    }

    /**
     * Presence visibility levels for friends.
     */
    public enum PresenceVisibility {
        /** Full presence with online/away/offline and last_seen */
        FULL,
        /** Only coarse buckets (today, this week) */
        COARSE,
        /** No presence shared */
        HIDDEN
    }
}
