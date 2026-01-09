package com.farmcraft.overlay.registry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Local registry for storing and querying definition entries.
 * 
 * The registry is the client-side cache of definitions fetched from
 * the manifest distribution server. It provides efficient lookup by
 * CID, type, name, and status.
 */
public class DefinitionRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefinitionRegistry.class);

    private static DefinitionRegistry INSTANCE;

    // Primary storage by CID
    private final Map<String, DefinitionEntry> entriesByCid;

    // Secondary indexes
    private final Map<DefinitionType, Map<String, DefinitionEntry>> entriesByType;
    private final Map<String, List<DefinitionEntry>> entriesByName;

    // Version tracking
    private volatile long lastSyncTime;
    private volatile int syncVersion;

    private DefinitionRegistry() {
        this.entriesByCid = new ConcurrentHashMap<>();
        this.entriesByType = new ConcurrentHashMap<>();
        this.entriesByName = new ConcurrentHashMap<>();
        this.lastSyncTime = 0;
        this.syncVersion = 0;

        // Initialize type indexes
        for (DefinitionType type : DefinitionType.values()) {
            entriesByType.put(type, new ConcurrentHashMap<>());
        }
    }

    public static synchronized DefinitionRegistry getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new DefinitionRegistry();
        }
        return INSTANCE;
    }

    // ========== Core Operations ==========

    /**
     * Add or update an entry in the registry.
     */
    public void put(DefinitionEntry entry) {
        if (entry == null || entry.cid() == null) {
            LOGGER.warn("Attempted to add null entry or entry with null CID");
            return;
        }

        // Add to primary storage
        entriesByCid.put(entry.cid(), entry);

        // Update type index
        entriesByType.get(entry.type()).put(entry.cid(), entry);

        // Update name index
        entriesByName.computeIfAbsent(entry.name().toLowerCase(), k -> new ArrayList<>())
                .add(entry);

        LOGGER.debug("Added entry: {} ({})", entry.name(), entry.getShortCid());
    }

    /**
     * Get an entry by CID.
     */
    public Optional<DefinitionEntry> get(String cid) {
        return Optional.ofNullable(entriesByCid.get(cid));
    }

    /**
     * Check if an entry exists.
     */
    public boolean contains(String cid) {
        return entriesByCid.containsKey(cid);
    }

    /**
     * Remove an entry by CID.
     */
    public Optional<DefinitionEntry> remove(String cid) {
        DefinitionEntry removed = entriesByCid.remove(cid);
        if (removed != null) {
            entriesByType.get(removed.type()).remove(cid);
            List<DefinitionEntry> byName = entriesByName.get(removed.name().toLowerCase());
            if (byName != null) {
                byName.removeIf(e -> e.cid().equals(cid));
            }
            LOGGER.debug("Removed entry: {} ({})", removed.name(), cid);
        }
        return Optional.ofNullable(removed);
    }

    /**
     * Clear all entries.
     */
    public void clear() {
        entriesByCid.clear();
        for (Map<String, DefinitionEntry> typeMap : entriesByType.values()) {
            typeMap.clear();
        }
        entriesByName.clear();
        LOGGER.info("Registry cleared");
    }

    // ========== Query Operations ==========

    /**
     * Get all entries of a specific type.
     */
    public List<DefinitionEntry> getByType(DefinitionType type) {
        return new ArrayList<>(entriesByType.get(type).values());
    }

    /**
     * Get all entries with a specific status.
     */
    public List<DefinitionEntry> getByStatus(DefinitionStatus status) {
        return entriesByCid.values().stream()
                .filter(e -> e.status() == status)
                .collect(Collectors.toList());
    }

    /**
     * Get all entries of a type with a specific status.
     */
    public List<DefinitionEntry> getByTypeAndStatus(DefinitionType type, DefinitionStatus status) {
        return entriesByType.get(type).values().stream()
                .filter(e -> e.status() == status)
                .collect(Collectors.toList());
    }

    /**
     * Get entries by name (case-insensitive).
     */
    public List<DefinitionEntry> getByName(String name) {
        return entriesByName.getOrDefault(name.toLowerCase(), Collections.emptyList());
    }

    /**
     * Get the latest version of an entry by name.
     */
    public Optional<DefinitionEntry> getLatestByName(String name) {
        return getByName(name).stream()
                .max(Comparator.comparingInt(DefinitionEntry::version));
    }

    /**
     * Get entries by author.
     */
    public List<DefinitionEntry> getByAuthor(String author) {
        return entriesByCid.values().stream()
                .filter(e -> e.author().equalsIgnoreCase(author))
                .collect(Collectors.toList());
    }

    /**
     * Search entries by predicate.
     */
    public List<DefinitionEntry> search(Predicate<DefinitionEntry> predicate) {
        return entriesByCid.values().stream()
                .filter(predicate)
                .collect(Collectors.toList());
    }

    /**
     * Search entries by name or description containing text.
     */
    public List<DefinitionEntry> searchText(String query) {
        String lowerQuery = query.toLowerCase();
        return entriesByCid.values().stream()
                .filter(e -> e.name().toLowerCase().contains(lowerQuery) ||
                        e.description().toLowerCase().contains(lowerQuery))
                .collect(Collectors.toList());
    }

    // ========== Active Definitions ==========

    /**
     * Get all active item definitions.
     */
    public List<DefinitionEntry> getActiveItems() {
        return getByTypeAndStatus(DefinitionType.ITEM, DefinitionStatus.ACTIVE);
    }

    /**
     * Get all active spell definitions.
     */
    public List<DefinitionEntry> getActiveSpells() {
        return getByTypeAndStatus(DefinitionType.SPELL, DefinitionStatus.ACTIVE);
    }

    /**
     * Get all active policy definitions.
     */
    public List<DefinitionEntry> getActivePolicies() {
        return getByTypeAndStatus(DefinitionType.POLICY, DefinitionStatus.ACTIVE);
    }

    /**
     * Get an active definition by name (for use in-game).
     */
    public Optional<DefinitionEntry> getActiveDefinition(DefinitionType type, String name) {
        return getByName(name).stream()
                .filter(e -> e.type() == type && e.status() == DefinitionStatus.ACTIVE)
                .max(Comparator.comparingInt(DefinitionEntry::version));
    }

    // ========== Statistics ==========

    /**
     * Get total entry count.
     */
    public int size() {
        return entriesByCid.size();
    }

    /**
     * Get count by type.
     */
    public int sizeByType(DefinitionType type) {
        return entriesByType.get(type).size();
    }

    /**
     * Get count by status.
     */
    public int sizeByStatus(DefinitionStatus status) {
        return (int) entriesByCid.values().stream()
                .filter(e -> e.status() == status)
                .count();
    }

    /**
     * Get registry statistics.
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalEntries", size());
        stats.put("lastSyncTime", lastSyncTime);
        stats.put("syncVersion", syncVersion);

        Map<String, Integer> byType = new HashMap<>();
        for (DefinitionType type : DefinitionType.values()) {
            byType.put(type.name(), sizeByType(type));
        }
        stats.put("byType", byType);

        Map<String, Integer> byStatus = new HashMap<>();
        for (DefinitionStatus status : DefinitionStatus.values()) {
            byStatus.put(status.name(), sizeByStatus(status));
        }
        stats.put("byStatus", byStatus);

        return stats;
    }

    // ========== Sync Tracking ==========

    public long getLastSyncTime() {
        return lastSyncTime;
    }

    public void setLastSyncTime(long lastSyncTime) {
        this.lastSyncTime = lastSyncTime;
    }

    public int getSyncVersion() {
        return syncVersion;
    }

    public void setSyncVersion(int syncVersion) {
        this.syncVersion = syncVersion;
    }

    /**
     * Check if a sync is needed (no sync in last hour).
     */
    public boolean needsSync() {
        return System.currentTimeMillis() - lastSyncTime > 60 * 60 * 1000;
    }

    // ========== Batch Operations ==========

    /**
     * Add multiple entries in batch.
     */
    public void putAll(Collection<DefinitionEntry> entries) {
        for (DefinitionEntry entry : entries) {
            put(entry);
        }
        LOGGER.info("Added {} entries in batch", entries.size());
    }

    /**
     * Get all CIDs in the registry.
     */
    public List<String> getAllCids() {
        return new ArrayList<>(entriesByCid.keySet());
    }

    /**
     * Get all entries as a list.
     */
    public List<DefinitionEntry> getAllEntries() {
        return new ArrayList<>(entriesByCid.values());
    }
}
