package com.farmcraft.overlay.resources;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages resource wallets for all players in the overlay network.
 * 
 * Provides centralized access to player wallets and handles
 * persistence, synchronization, and event notifications.
 */
public class ResourceManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(ResourceManager.class);

    private static ResourceManager INSTANCE;

    private final Map<UUID, ResourceWallet> wallets;
    private final Map<UUID, Consumer<ResourceWallet>> listeners;

    private ResourceManager() {
        this.wallets = new ConcurrentHashMap<>();
        this.listeners = new ConcurrentHashMap<>();
    }

    public static synchronized ResourceManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new ResourceManager();
        }
        return INSTANCE;
    }

    /**
     * Get or create a wallet for a player.
     */
    public ResourceWallet getWallet(UUID playerId) {
        return wallets.computeIfAbsent(playerId, ResourceWallet::new);
    }

    /**
     * Check if a player has a wallet.
     */
    public boolean hasWallet(UUID playerId) {
        return wallets.containsKey(playerId);
    }

    /**
     * Remove a player's wallet (e.g., on disconnect).
     */
    public void removeWallet(UUID playerId) {
        wallets.remove(playerId);
        listeners.remove(playerId);
    }

    /**
     * Add a listener for wallet changes.
     */
    public void addWalletListener(UUID playerId, Consumer<ResourceWallet> listener) {
        listeners.put(playerId, listener);
    }

    /**
     * Remove a wallet listener.
     */
    public void removeWalletListener(UUID playerId) {
        listeners.remove(playerId);
    }

    /**
     * Notify listeners of wallet changes.
     */
    private void notifyChange(UUID playerId) {
        Consumer<ResourceWallet> listener = listeners.get(playerId);
        if (listener != null) {
            try {
                listener.accept(getWallet(playerId));
            } catch (Exception e) {
                LOGGER.error("Error notifying wallet listener for player {}", playerId, e);
            }
        }
    }

    // ========== Convenience Methods ==========

    /**
     * Check if a player can afford a cost.
     */
    public boolean canAfford(UUID playerId, ResourceCost cost) {
        return getWallet(playerId).canAfford(cost);
    }

    /**
     * Attempt to spend resources for an action.
     */
    public boolean spend(UUID playerId, ResourceCost cost) {
        ResourceWallet wallet = getWallet(playerId);
        boolean success = wallet.spend(cost);
        if (success) {
            notifyChange(playerId);
            LOGGER.debug("Player {} spent: {}", playerId, cost);
        }
        return success;
    }

    /**
     * Add ash to a player's wallet (from proof-of-work).
     */
    public void addAsh(UUID playerId, long amount) {
        ResourceWallet wallet = getWallet(playerId);
        wallet.addAsh(amount);
        notifyChange(playerId);
        LOGGER.debug("Player {} earned {} ash", playerId, amount);
    }

    /**
     * Grant a seal to a player.
     */
    public void grantSeal(UUID playerId, SealType seal) {
        ResourceWallet wallet = getWallet(playerId);
        wallet.grantSeal(seal);
        notifyChange(playerId);
        LOGGER.info("Player {} granted seal: {}", playerId, seal);
    }

    /**
     * Revoke a seal from a player.
     */
    public void revokeSeal(UUID playerId, SealType seal) {
        ResourceWallet wallet = getWallet(playerId);
        wallet.revokeSeal(seal);
        notifyChange(playerId);
        LOGGER.info("Player {} revoked seal: {}", playerId, seal);
    }

    /**
     * Check if a player has a seal.
     */
    public boolean hasSeal(UUID playerId, SealType seal) {
        return getWallet(playerId).hasSeal(seal);
    }

    /**
     * Transfer tokens between players.
     */
    public boolean transferTokens(UUID fromPlayer, UUID toPlayer, String tokenName, long amount) {
        ResourceWallet fromWallet = getWallet(fromPlayer);
        ResourceWallet toWallet = getWallet(toPlayer);

        boolean success = fromWallet.transferTokens(tokenName, amount, toWallet);
        if (success) {
            notifyChange(fromPlayer);
            notifyChange(toPlayer);
            LOGGER.debug("Transferred {} {} from {} to {}", amount, tokenName, fromPlayer, toPlayer);
        }
        return success;
    }

    /**
     * Award participation rewards (wards).
     */
    public void awardParticipation(UUID playerId, int wards) {
        ResourceWallet wallet = getWallet(playerId);
        wallet.addWards(wards);
        notifyChange(playerId);
        LOGGER.debug("Player {} awarded {} wards for participation", playerId, wards);
    }

    // ========== Batch Operations ==========

    /**
     * Get all active wallets.
     */
    public Map<UUID, ResourceWallet> getAllWallets() {
        return Map.copyOf(wallets);
    }

    /**
     * Get wallet count.
     */
    public int getWalletCount() {
        return wallets.size();
    }

    /**
     * Clear all wallets (for testing).
     */
    public void clearAll() {
        wallets.clear();
        listeners.clear();
        LOGGER.warn("All wallets cleared");
    }

    // ========== Persistence ==========

    /**
     * Save a wallet snapshot for persistence.
     */
    public ResourceWallet.WalletSnapshot saveWallet(UUID playerId) {
        ResourceWallet wallet = wallets.get(playerId);
        return wallet != null ? wallet.snapshot() : null;
    }

    /**
     * Restore a wallet from a snapshot.
     */
    public void restoreWallet(ResourceWallet.WalletSnapshot snapshot) {
        if (snapshot != null) {
            ResourceWallet wallet = ResourceWallet.fromSnapshot(snapshot);
            wallets.put(snapshot.playerId(), wallet);
            LOGGER.debug("Restored wallet for player {}", snapshot.playerId());
        }
    }
}
