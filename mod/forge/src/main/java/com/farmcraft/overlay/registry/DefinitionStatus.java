package com.farmcraft.overlay.registry;

/**
 * Status of a definition in the registry.
 */
public enum DefinitionStatus {
    /**
     * DRAFT - Work in progress, not yet submitted.
     */
    DRAFT("Draft", "📝"),

    /**
     * PENDING - Submitted for approval, awaiting review.
     */
    PENDING("Pending", "⏳"),

    /**
     * ACTIVE - Approved and published, available for use.
     */
    ACTIVE("Active", "✅"),

    /**
     * DEPRECATED - No longer recommended, may be removed.
     */
    DEPRECATED("Deprecated", "⚠️");

    private final String displayName;
    private final String icon;

    DefinitionStatus(String displayName, String icon) {
        this.displayName = displayName;
        this.icon = icon;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getIcon() {
        return icon;
    }

    /**
     * Check if this status allows the definition to be used.
     */
    public boolean isUsable() {
        return this == ACTIVE;
    }

    /**
     * Check if this status allows editing.
     */
    public boolean isEditable() {
        return this == DRAFT;
    }

    /**
     * Get status from string.
     */
    public static DefinitionStatus fromString(String value) {
        for (DefinitionStatus status : values()) {
            if (status.name().equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown status: " + value);
    }
}
