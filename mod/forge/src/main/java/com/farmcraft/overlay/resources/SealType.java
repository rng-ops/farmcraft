package com.farmcraft.overlay.resources;

/**
 * Types of seals (non-transferable privilege credentials).
 * 
 * Seals grant special abilities or permissions within the overlay network.
 * They are granted by governance processes or federation authority.
 */
public enum SealType {

    /**
     * PUBLISHER_SEAL - Can publish definitions and create manifests.
     * Required to add new items or spells to the overlay network.
     */
    PUBLISHER_SEAL("Publisher", "📖", "Can publish definitions"),

    /**
     * MODERATOR_SEAL - Can moderate content and approve definitions.
     * Required to participate in the approval queue.
     */
    MODERATOR_SEAL("Moderator", "🛡️", "Can moderate and approve"),

    /**
     * FEDERATION_SEAL - Represents a federation in governance.
     * Grants voting rights on federation-level decisions.
     */
    FEDERATION_SEAL("Federation", "🏛️", "Federation representative"),

    /**
     * GOVERNANCE_SEAL - Can participate in governance decisions.
     * Required for voting on policy changes.
     */
    GOVERNANCE_SEAL("Governance", "⚖️", "Governance participant"),

    /**
     * DEVELOPER_SEAL - Access to developer tools and APIs.
     * Allows testing experimental features.
     */
    DEVELOPER_SEAL("Developer", "💻", "Developer access"),

    /**
     * SHARD_LEADER_SEAL - Leader of a shard.
     * Can manage shard policies and invite members.
     */
    SHARD_LEADER_SEAL("Shard Leader", "👑", "Shard leader"),

    /**
     * COHORT_FOUNDER_SEAL - Founder of a cohort.
     * Can manage cohort membership and settings.
     */
    COHORT_FOUNDER_SEAL("Cohort Founder", "🌟", "Cohort founder"),

    /**
     * EMERGENCY_SEAL - Can invoke emergency protocols.
     * Used for safety-critical situations.
     */
    EMERGENCY_SEAL("Emergency", "🚨", "Emergency authority"),

    /**
     * PRIVACY_COUNCIL_SEAL - Member of the privacy council.
     * Reviews spells for privacy compliance.
     */
    PRIVACY_COUNCIL_SEAL("Privacy Council", "🔒", "Privacy reviewer");

    private final String displayName;
    private final String icon;
    private final String description;

    SealType(String displayName, String icon, String description) {
        this.displayName = displayName;
        this.icon = icon;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getIcon() {
        return icon;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Parse seal type from string (case-insensitive).
     */
    public static SealType fromString(String name) {
        for (SealType seal : values()) {
            if (seal.name().equalsIgnoreCase(name)) {
                return seal;
            }
        }
        throw new IllegalArgumentException("Unknown seal type: " + name);
    }
}
