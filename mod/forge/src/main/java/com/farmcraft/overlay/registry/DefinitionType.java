package com.farmcraft.overlay.registry;

/**
 * Types of definitions that can be stored in the registry.
 */
public enum DefinitionType {
    /**
     * Item definitions - overlay items with custom properties.
     */
    ITEM("item", "⚔️"),

    /**
     * Spell definitions - overlay spells/actions.
     */
    SPELL("spell", "✨"),

    /**
     * Policy definitions - governance and privacy policies.
     */
    POLICY("policy", "📋"),

    /**
     * Asset definitions - textures, models, etc.
     */
    ASSET("asset", "🎨");

    private final String prefix;
    private final String icon;

    DefinitionType(String prefix, String icon) {
        this.prefix = prefix;
        this.icon = icon;
    }

    public String getPrefix() {
        return prefix;
    }

    public String getIcon() {
        return icon;
    }

    /**
     * Get definition type from string.
     */
    public static DefinitionType fromString(String value) {
        for (DefinitionType type : values()) {
            if (type.name().equalsIgnoreCase(value) || type.prefix.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown definition type: " + value);
    }
}
