package com.farmcraft.overlay.resources;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Represents a resource cost for performing an action in the overlay network.
 * 
 * Costs can include multiple resource types and are immutable.
 * Use the Builder to construct costs.
 */
public class ResourceCost {

    public static final ResourceCost FREE = new Builder().build();

    private final long ash;
    private final int sigils;
    private final int wards;
    private final Set<SealType> requiredSeals;
    private final Map<String, Long> tokenCosts;

    private ResourceCost(Builder builder) {
        this.ash = builder.ash;
        this.sigils = builder.sigils;
        this.wards = builder.wards;
        this.requiredSeals = Collections.unmodifiableSet(new HashSet<>(builder.requiredSeals));
        this.tokenCosts = Collections.unmodifiableMap(new HashMap<>(builder.tokenCosts));
    }

    public long getAsh() {
        return ash;
    }

    public int getSigils() {
        return sigils;
    }

    public int getWards() {
        return wards;
    }

    public Set<SealType> getRequiredSeals() {
        return requiredSeals;
    }

    public Map<String, Long> getTokenCosts() {
        return tokenCosts;
    }

    /**
     * Check if this cost is free (no resources required).
     */
    public boolean isFree() {
        return ash == 0 && sigils == 0 && wards == 0 &&
                requiredSeals.isEmpty() && tokenCosts.isEmpty();
    }

    /**
     * Get a human-readable description of the cost.
     */
    public String formatCost() {
        if (isFree())
            return "Free";

        StringBuilder sb = new StringBuilder();
        if (ash > 0)
            sb.append(ash).append(" Ash, ");
        if (sigils > 0)
            sb.append(sigils).append(" Sigils, ");
        if (wards > 0)
            sb.append(wards).append(" Wards, ");

        for (Map.Entry<String, Long> entry : tokenCosts.entrySet()) {
            sb.append(entry.getValue()).append(" ").append(entry.getKey()).append(", ");
        }

        for (SealType seal : requiredSeals) {
            sb.append(seal.getDisplayName()).append(" required, ");
        }

        // Remove trailing comma and space
        if (sb.length() > 2) {
            sb.setLength(sb.length() - 2);
        }

        return sb.toString();
    }

    /**
     * Combine two costs into one.
     */
    public ResourceCost plus(ResourceCost other) {
        Builder builder = new Builder()
                .ash(this.ash + other.ash)
                .sigils(this.sigils + other.sigils)
                .wards(this.wards + other.wards);

        for (SealType seal : this.requiredSeals) {
            builder.requireSeal(seal);
        }
        for (SealType seal : other.requiredSeals) {
            builder.requireSeal(seal);
        }

        for (Map.Entry<String, Long> entry : this.tokenCosts.entrySet()) {
            builder.token(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, Long> entry : other.tokenCosts.entrySet()) {
            builder.token(entry.getKey(),
                    this.tokenCosts.getOrDefault(entry.getKey(), 0L) + entry.getValue());
        }

        return builder.build();
    }

    /**
     * Scale a cost by a multiplier.
     */
    public ResourceCost times(double multiplier) {
        Builder builder = new Builder()
                .ash((long) (this.ash * multiplier))
                .sigils((int) (this.sigils * multiplier))
                .wards((int) (this.wards * multiplier));

        for (SealType seal : this.requiredSeals) {
            builder.requireSeal(seal);
        }

        for (Map.Entry<String, Long> entry : this.tokenCosts.entrySet()) {
            builder.token(entry.getKey(), (long) (entry.getValue() * multiplier));
        }

        return builder.build();
    }

    @Override
    public String toString() {
        return "ResourceCost{" + formatCost() + "}";
    }

    // ========== Builder ==========

    public static class Builder {
        private long ash = 0;
        private int sigils = 0;
        private int wards = 0;
        private final Set<SealType> requiredSeals = new HashSet<>();
        private final Map<String, Long> tokenCosts = new HashMap<>();

        public Builder ash(long amount) {
            this.ash = amount;
            return this;
        }

        public Builder sigils(int amount) {
            this.sigils = amount;
            return this;
        }

        public Builder wards(int amount) {
            this.wards = amount;
            return this;
        }

        public Builder requireSeal(SealType seal) {
            this.requiredSeals.add(seal);
            return this;
        }

        public Builder token(String tokenName, long amount) {
            this.tokenCosts.merge(tokenName, amount, Long::sum);
            return this;
        }

        public ResourceCost build() {
            return new ResourceCost(this);
        }
    }

    // ========== Common Costs ==========

    /** Small action cost (1 sigil) */
    public static ResourceCost smallAction() {
        return new Builder().sigils(1).build();
    }

    /** Medium action cost (3 sigils, 10 ash) */
    public static ResourceCost mediumAction() {
        return new Builder().sigils(3).ash(10).build();
    }

    /** Large action cost (10 sigils, 50 ash) */
    public static ResourceCost largeAction() {
        return new Builder().sigils(10).ash(50).build();
    }

    /** Publishing cost (requires publisher seal, 100 ash) */
    public static ResourceCost publishing() {
        return new Builder()
                .ash(100)
                .sigils(20)
                .requireSeal(SealType.PUBLISHER_SEAL)
                .build();
    }

    /** Protection cost (1 ward) */
    public static ResourceCost protection() {
        return new Builder().wards(1).build();
    }

    /** Emergency action (requires emergency seal) */
    public static ResourceCost emergency() {
        return new Builder()
                .requireSeal(SealType.EMERGENCY_SEAL)
                .build();
    }
}
