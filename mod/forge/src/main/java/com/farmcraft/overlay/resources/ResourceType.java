package com.farmcraft.overlay.resources;

/**
 * Types of resources in the FarmCraft overlay economy.
 * 
 * The resource system provides Sybil resistance and rate limiting
 * through different types of scarce resources.
 */
public enum ResourceType {
    /**
     * ASH - Proof-of-Work currency
     * 
     * Earned through computational work, provides Sybil resistance
     * by making it costly to create multiple identities.
     * Non-transferable, accumulates without cap.
     */
    ASH("Ash", "🔥", "Proof-of-Work currency", AccumulationMethod.PROOF_OF_WORK),

    /**
     * SIGILS - Action budget per time period
     * 
     * Regenerates over time (1 per 5 minutes, max 100).
     * Consumed by overlay spells and actions.
     * Rate-limits how frequently actions can be performed.
     */
    SIGILS("Sigils", "✨", "Action budget", AccumulationMethod.TIME_BASED),

    /**
     * SEALS - Privilege credentials
     * 
     * Non-transferable credentials that grant special privileges.
     * Examples: PUBLISHER_SEAL, MODERATOR_SEAL, FEDERATION_SEAL
     * Granted by governance or federation authority.
     */
    SEALS("Seals", "📜", "Privilege credentials", AccumulationMethod.GOVERNANCE),

    /**
     * WARDS - Protection tokens
     * 
     * Provide protection from certain overlay effects.
     * Can shield from queries, hide from discovery, block communications.
     * Earned through participation, consumed on use.
     */
    WARDS("Wards", "🛡️", "Protection tokens", AccumulationMethod.PARTICIPATION),

    /**
     * TOKENS - Transferable custom tokens
     * 
     * Player-created tokens that can be traded.
     * Market-determined value, no inherent cap.
     */
    TOKENS("Tokens", "🪙", "Custom transferable tokens", AccumulationMethod.TRANSFER);

    private final String displayName;
    private final String icon;
    private final String description;
    private final AccumulationMethod accumulationMethod;

    ResourceType(String displayName, String icon, String description, AccumulationMethod accumulationMethod) {
        this.displayName = displayName;
        this.icon = icon;
        this.description = description;
        this.accumulationMethod = accumulationMethod;
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

    public AccumulationMethod getAccumulationMethod() {
        return accumulationMethod;
    }

    /**
     * Check if this resource type is transferable between players.
     */
    public boolean isTransferable() {
        return this == TOKENS;
    }

    /**
     * Check if this resource type has a maximum cap.
     */
    public boolean hasCap() {
        return this == SIGILS || this == WARDS;
    }

    /**
     * Get the default maximum cap for capped resources.
     */
    public int getDefaultCap() {
        switch (this) {
            case SIGILS:
                return 100;
            case WARDS:
                return 50;
            default:
                return Integer.MAX_VALUE;
        }
    }

    /**
     * How resources are accumulated/minted.
     */
    public enum AccumulationMethod {
        /** Earned through computational proof-of-work */
        PROOF_OF_WORK,
        /** Regenerates over time */
        TIME_BASED,
        /** Granted by governance/authority */
        GOVERNANCE,
        /** Earned through active participation */
        PARTICIPATION,
        /** Received through player-to-player transfer */
        TRANSFER
    }
}
