package com.farmcraft.overlay.resources;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Wallet containing all resources for a player in the overlay economy.
 * 
 * Thread-safe implementation supporting concurrent access.
 */
public class ResourceWallet {

    private final UUID playerId;

    // Core resources
    private volatile long ash;
    private volatile int sigils;
    private volatile int wards;

    // Seals (non-transferable privileges)
    private final Set<SealType> seals;

    // Custom tokens (transferable)
    private final Map<String, Long> tokens;

    // Tracking
    private volatile long lastSigilRegenTime;
    private volatile long lastWardRegenTime;

    // Constants
    public static final long SIGIL_REGEN_INTERVAL_MS = 5 * 60 * 1000; // 5 minutes
    public static final long WARD_REGEN_INTERVAL_MS = 24 * 60 * 60 * 1000; // 1 day
    public static final int MAX_SIGILS = 100;
    public static final int MAX_WARDS = 50;

    public ResourceWallet(UUID playerId) {
        this.playerId = playerId;
        this.ash = 0;
        this.sigils = 10; // Starting sigils
        this.wards = 0;
        this.seals = Collections.synchronizedSet(new HashSet<>());
        this.tokens = new ConcurrentHashMap<>();
        this.lastSigilRegenTime = System.currentTimeMillis();
        this.lastWardRegenTime = System.currentTimeMillis();
    }

    public UUID getPlayerId() {
        return playerId;
    }

    // ========== ASH (Proof-of-Work) ==========

    public long getAsh() {
        return ash;
    }

    public synchronized void addAsh(long amount) {
        if (amount < 0)
            throw new IllegalArgumentException("Cannot add negative ash");
        this.ash += amount;
    }

    public synchronized boolean spendAsh(long amount) {
        if (amount < 0)
            throw new IllegalArgumentException("Cannot spend negative ash");
        if (this.ash < amount)
            return false;
        this.ash -= amount;
        return true;
    }

    // ========== SIGILS (Action Budget) ==========

    public int getSigils() {
        regenerateSigils();
        return sigils;
    }

    private synchronized void regenerateSigils() {
        long now = System.currentTimeMillis();
        long elapsed = now - lastSigilRegenTime;
        int toRegen = (int) (elapsed / SIGIL_REGEN_INTERVAL_MS);

        if (toRegen > 0) {
            sigils = Math.min(MAX_SIGILS, sigils + toRegen);
            lastSigilRegenTime = now - (elapsed % SIGIL_REGEN_INTERVAL_MS);
        }
    }

    public synchronized boolean spendSigils(int amount) {
        if (amount < 0)
            throw new IllegalArgumentException("Cannot spend negative sigils");
        regenerateSigils();
        if (this.sigils < amount)
            return false;
        this.sigils -= amount;
        return true;
    }

    public synchronized void addSigils(int amount) {
        if (amount < 0)
            throw new IllegalArgumentException("Cannot add negative sigils");
        regenerateSigils();
        this.sigils = Math.min(MAX_SIGILS, this.sigils + amount);
    }

    // ========== WARDS (Protection) ==========

    public int getWards() {
        regenerateWards();
        return wards;
    }

    private synchronized void regenerateWards() {
        long now = System.currentTimeMillis();
        long elapsed = now - lastWardRegenTime;
        int toRegen = (int) (elapsed / WARD_REGEN_INTERVAL_MS);

        if (toRegen > 0) {
            wards = Math.min(MAX_WARDS, wards + toRegen);
            lastWardRegenTime = now - (elapsed % WARD_REGEN_INTERVAL_MS);
        }
    }

    public synchronized boolean spendWards(int amount) {
        if (amount < 0)
            throw new IllegalArgumentException("Cannot spend negative wards");
        regenerateWards();
        if (this.wards < amount)
            return false;
        this.wards -= amount;
        return true;
    }

    public synchronized void addWards(int amount) {
        if (amount < 0)
            throw new IllegalArgumentException("Cannot add negative wards");
        regenerateWards();
        this.wards = Math.min(MAX_WARDS, this.wards + amount);
    }

    // ========== SEALS (Privileges) ==========

    public Set<SealType> getSeals() {
        return Collections.unmodifiableSet(new HashSet<>(seals));
    }

    public boolean hasSeal(SealType seal) {
        return seals.contains(seal);
    }

    public void grantSeal(SealType seal) {
        seals.add(seal);
    }

    public void revokeSeal(SealType seal) {
        seals.remove(seal);
    }

    // ========== TOKENS (Transferable) ==========

    public Map<String, Long> getTokens() {
        return Collections.unmodifiableMap(new HashMap<>(tokens));
    }

    public long getTokenBalance(String tokenName) {
        return tokens.getOrDefault(tokenName, 0L);
    }

    public synchronized void addTokens(String tokenName, long amount) {
        if (amount < 0)
            throw new IllegalArgumentException("Cannot add negative tokens");
        tokens.merge(tokenName, amount, Long::sum);
    }

    public synchronized boolean spendTokens(String tokenName, long amount) {
        if (amount < 0)
            throw new IllegalArgumentException("Cannot spend negative tokens");
        long balance = tokens.getOrDefault(tokenName, 0L);
        if (balance < amount)
            return false;

        long newBalance = balance - amount;
        if (newBalance == 0) {
            tokens.remove(tokenName);
        } else {
            tokens.put(tokenName, newBalance);
        }
        return true;
    }

    public synchronized boolean transferTokens(String tokenName, long amount, ResourceWallet recipient) {
        if (amount < 0)
            throw new IllegalArgumentException("Cannot transfer negative tokens");
        if (!spendTokens(tokenName, amount))
            return false;
        recipient.addTokens(tokenName, amount);
        return true;
    }

    // ========== COST CHECKING ==========

    /**
     * Check if the wallet can afford a given cost.
     */
    public boolean canAfford(ResourceCost cost) {
        if (cost.getAsh() > 0 && getAsh() < cost.getAsh())
            return false;
        if (cost.getSigils() > 0 && getSigils() < cost.getSigils())
            return false;
        if (cost.getWards() > 0 && getWards() < cost.getWards())
            return false;

        for (SealType seal : cost.getRequiredSeals()) {
            if (!hasSeal(seal))
                return false;
        }

        for (Map.Entry<String, Long> entry : cost.getTokenCosts().entrySet()) {
            if (getTokenBalance(entry.getKey()) < entry.getValue())
                return false;
        }

        return true;
    }

    /**
     * Attempt to spend a given cost atomically.
     */
    public synchronized boolean spend(ResourceCost cost) {
        if (!canAfford(cost))
            return false;

        // Spend each resource type
        if (cost.getAsh() > 0)
            spendAsh(cost.getAsh());
        if (cost.getSigils() > 0)
            spendSigils(cost.getSigils());
        if (cost.getWards() > 0)
            spendWards(cost.getWards());

        for (Map.Entry<String, Long> entry : cost.getTokenCosts().entrySet()) {
            spendTokens(entry.getKey(), entry.getValue());
        }

        return true;
    }

    // ========== SERIALIZATION ==========

    /**
     * Create a snapshot of the wallet state for serialization.
     */
    public WalletSnapshot snapshot() {
        return new WalletSnapshot(
                playerId,
                ash,
                getSigils(), // Includes regeneration
                getWards(), // Includes regeneration
                new HashSet<>(seals),
                new HashMap<>(tokens),
                lastSigilRegenTime,
                lastWardRegenTime);
    }

    /**
     * Restore wallet from a snapshot.
     */
    public static ResourceWallet fromSnapshot(WalletSnapshot snapshot) {
        ResourceWallet wallet = new ResourceWallet(snapshot.playerId);
        wallet.ash = snapshot.ash;
        wallet.sigils = snapshot.sigils;
        wallet.wards = snapshot.wards;
        wallet.seals.addAll(snapshot.seals);
        wallet.tokens.putAll(snapshot.tokens);
        wallet.lastSigilRegenTime = snapshot.lastSigilRegenTime;
        wallet.lastWardRegenTime = snapshot.lastWardRegenTime;
        return wallet;
    }

    /**
     * Immutable snapshot of wallet state for serialization.
     */
    public record WalletSnapshot(
            UUID playerId,
            long ash,
            int sigils,
            int wards,
            Set<SealType> seals,
            Map<String, Long> tokens,
            long lastSigilRegenTime,
            long lastWardRegenTime) {
    }
}
