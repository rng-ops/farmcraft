package com.farmcraft.overlay.registry;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DefinitionRegistry.
 * 
 * Tests the client-side cache for overlay definitions.
 */
@DisplayName("DefinitionRegistry Tests")
class DefinitionRegistryTest {

    private DefinitionRegistry registry;

    @BeforeEach
    void setUp() {
        registry = DefinitionRegistry.getInstance();
        registry.clear(); // Start fresh for each test
    }

    @AfterEach
    void tearDown() {
        registry.clear();
    }

    /**
     * Helper to create a test entry.
     */
    private DefinitionEntry createEntry(
            String name,
            DefinitionType type,
            DefinitionStatus status) {
        JsonObject content = new JsonObject();
        content.addProperty("name", name);
        content.addProperty("type", type.name());

        return DefinitionEntry.builder()
                .type(type)
                .name(name)
                .description("Test description for " + name)
                .author("TestAuthor")
                .version(1)
                .status(status)
                .content(content)
                .build();
    }

    @Nested
    @DisplayName("Core Operations")
    class CoreOperationsTests {

        @Test
        @DisplayName("getInstance returns singleton")
        void getInstanceReturnsSingleton() {
            DefinitionRegistry instance1 = DefinitionRegistry.getInstance();
            DefinitionRegistry instance2 = DefinitionRegistry.getInstance();
            assertSame(instance1, instance2);
        }

        @Test
        @DisplayName("put stores entry retrievable by CID")
        void putStoresEntry() {
            DefinitionEntry entry = createEntry("TestItem", DefinitionType.ITEM, DefinitionStatus.ACTIVE);
            registry.put(entry);

            Optional<DefinitionEntry> retrieved = registry.get(entry.cid());

            assertTrue(retrieved.isPresent());
            assertEquals("TestItem", retrieved.get().name());
        }

        @Test
        @DisplayName("put ignores null entries")
        void putIgnoresNull() {
            registry.put(null);
            assertEquals(0, registry.size());
        }

        @Test
        @DisplayName("get returns empty for non-existent CID")
        void getReturnsEmptyForNonExistent() {
            Optional<DefinitionEntry> result = registry.get("fc_nonexistent123");
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("contains returns correct results")
        void containsReturnsCorrectResults() {
            DefinitionEntry entry = createEntry("TestItem", DefinitionType.ITEM, DefinitionStatus.ACTIVE);

            assertFalse(registry.contains(entry.cid()));

            registry.put(entry);

            assertTrue(registry.contains(entry.cid()));
        }

        @Test
        @DisplayName("remove deletes entry")
        void removeDeletesEntry() {
            DefinitionEntry entry = createEntry("TestItem", DefinitionType.ITEM, DefinitionStatus.ACTIVE);
            registry.put(entry);

            Optional<DefinitionEntry> removed = registry.remove(entry.cid());

            assertTrue(removed.isPresent());
            assertFalse(registry.contains(entry.cid()));
        }

        @Test
        @DisplayName("remove returns empty for non-existent")
        void removeReturnsEmptyForNonExistent() {
            Optional<DefinitionEntry> removed = registry.remove("fc_nonexistent");
            assertTrue(removed.isEmpty());
        }

        @Test
        @DisplayName("clear removes all entries")
        void clearRemovesAll() {
            registry.put(createEntry("Item1", DefinitionType.ITEM, DefinitionStatus.ACTIVE));
            registry.put(createEntry("Item2", DefinitionType.ITEM, DefinitionStatus.ACTIVE));
            registry.put(createEntry("Spell1", DefinitionType.SPELL, DefinitionStatus.ACTIVE));

            assertEquals(3, registry.size());

            registry.clear();

            assertEquals(0, registry.size());
        }
    }

    @Nested
    @DisplayName("Query Operations")
    class QueryOperationsTests {

        @BeforeEach
        void setUpTestData() {
            registry.put(createEntry("Sword", DefinitionType.ITEM, DefinitionStatus.ACTIVE));
            registry.put(createEntry("Shield", DefinitionType.ITEM, DefinitionStatus.ACTIVE));
            registry.put(createEntry("Helmet", DefinitionType.ITEM, DefinitionStatus.DRAFT));
            registry.put(createEntry("Fireball", DefinitionType.SPELL, DefinitionStatus.ACTIVE));
            registry.put(createEntry("Ice Storm", DefinitionType.SPELL, DefinitionStatus.PENDING));
            registry.put(createEntry("Privacy Policy", DefinitionType.POLICY, DefinitionStatus.ACTIVE));
        }

        @Test
        @DisplayName("getByType returns entries of specified type")
        void getByTypeReturnsCorrectEntries() {
            List<DefinitionEntry> items = registry.getByType(DefinitionType.ITEM);

            assertEquals(3, items.size());
            assertTrue(items.stream().allMatch(e -> e.type() == DefinitionType.ITEM));
        }

        @Test
        @DisplayName("getByStatus returns entries with specified status")
        void getByStatusReturnsCorrectEntries() {
            List<DefinitionEntry> active = registry.getByStatus(DefinitionStatus.ACTIVE);

            assertEquals(4, active.size());
            assertTrue(active.stream().allMatch(e -> e.status() == DefinitionStatus.ACTIVE));
        }

        @Test
        @DisplayName("getByTypeAndStatus filters correctly")
        void getByTypeAndStatusFiltersCorrectly() {
            List<DefinitionEntry> activeItems = registry.getByTypeAndStatus(
                    DefinitionType.ITEM, DefinitionStatus.ACTIVE);

            assertEquals(2, activeItems.size());
            assertTrue(activeItems.stream()
                    .allMatch(e -> e.type() == DefinitionType.ITEM && e.status() == DefinitionStatus.ACTIVE));
        }

        @Test
        @DisplayName("getByName is case-insensitive")
        void getByNameIsCaseInsensitive() {
            List<DefinitionEntry> results1 = registry.getByName("Sword");
            List<DefinitionEntry> results2 = registry.getByName("sword");
            List<DefinitionEntry> results3 = registry.getByName("SWORD");

            assertEquals(1, results1.size());
            assertEquals(results1.get(0).cid(), results2.get(0).cid());
            assertEquals(results1.get(0).cid(), results3.get(0).cid());
        }

        @Test
        @DisplayName("getByName returns empty list for no matches")
        void getByNameReturnsEmptyForNoMatches() {
            List<DefinitionEntry> results = registry.getByName("NonExistent");
            assertTrue(results.isEmpty());
        }

        @Test
        @DisplayName("searchText finds by name substring")
        void searchTextFindsByName() {
            List<DefinitionEntry> results = registry.searchText("fire");

            assertEquals(1, results.size());
            assertEquals("Fireball", results.get(0).name());
        }

        @Test
        @DisplayName("searchText finds by description")
        void searchTextFindsByDescription() {
            List<DefinitionEntry> results = registry.searchText("description");

            assertEquals(6, results.size()); // All entries have "description" in desc
        }

        @Test
        @DisplayName("search with predicate works")
        void searchWithPredicateWorks() {
            List<DefinitionEntry> results = registry.search(
                    e -> e.version() == 1 && e.status() == DefinitionStatus.ACTIVE);

            assertEquals(4, results.size());
        }
    }

    @Nested
    @DisplayName("Active Definitions")
    class ActiveDefinitionsTests {

        @BeforeEach
        void setUpTestData() {
            registry.put(createEntry("Sword", DefinitionType.ITEM, DefinitionStatus.ACTIVE));
            registry.put(createEntry("Shield", DefinitionType.ITEM, DefinitionStatus.DRAFT));
            registry.put(createEntry("Fireball", DefinitionType.SPELL, DefinitionStatus.ACTIVE));
            registry.put(createEntry("Privacy", DefinitionType.POLICY, DefinitionStatus.ACTIVE));
        }

        @Test
        @DisplayName("getActiveItems returns only active items")
        void getActiveItemsReturnsOnlyActive() {
            List<DefinitionEntry> activeItems = registry.getActiveItems();

            assertEquals(1, activeItems.size());
            assertEquals("Sword", activeItems.get(0).name());
        }

        @Test
        @DisplayName("getActiveSpells returns only active spells")
        void getActiveSpellsReturnsOnlyActive() {
            List<DefinitionEntry> activeSpells = registry.getActiveSpells();

            assertEquals(1, activeSpells.size());
            assertEquals("Fireball", activeSpells.get(0).name());
        }

        @Test
        @DisplayName("getActivePolicies returns only active policies")
        void getActivePoliciesReturnsOnlyActive() {
            List<DefinitionEntry> activePolicies = registry.getActivePolicies();

            assertEquals(1, activePolicies.size());
            assertEquals("Privacy", activePolicies.get(0).name());
        }

        @Test
        @DisplayName("getActiveDefinition returns most recent version")
        void getActiveDefinitionReturnsMostRecent() {
            // Add a second version
            JsonObject content = new JsonObject();
            content.addProperty("name", "Sword");
            content.addProperty("version", 2);

            DefinitionEntry v2 = DefinitionEntry.builder()
                    .type(DefinitionType.ITEM)
                    .name("Sword")
                    .description("Upgraded sword")
                    .author("TestAuthor")
                    .version(2)
                    .status(DefinitionStatus.ACTIVE)
                    .content(content)
                    .build();
            registry.put(v2);

            Optional<DefinitionEntry> active = registry.getActiveDefinition(DefinitionType.ITEM, "Sword");

            assertTrue(active.isPresent());
            assertEquals(2, active.get().version());
        }
    }

    @Nested
    @DisplayName("Statistics")
    class StatisticsTests {

        @BeforeEach
        void setUpTestData() {
            registry.put(createEntry("Sword", DefinitionType.ITEM, DefinitionStatus.ACTIVE));
            registry.put(createEntry("Shield", DefinitionType.ITEM, DefinitionStatus.ACTIVE));
            registry.put(createEntry("Helmet", DefinitionType.ITEM, DefinitionStatus.DRAFT));
            registry.put(createEntry("Fireball", DefinitionType.SPELL, DefinitionStatus.ACTIVE));
        }

        @Test
        @DisplayName("size returns total count")
        void sizeReturnsTotalCount() {
            assertEquals(4, registry.size());
        }

        @Test
        @DisplayName("sizeByType returns count for type")
        void sizeByTypeReturnsCountForType() {
            assertEquals(3, registry.sizeByType(DefinitionType.ITEM));
            assertEquals(1, registry.sizeByType(DefinitionType.SPELL));
            assertEquals(0, registry.sizeByType(DefinitionType.POLICY));
        }

        @Test
        @DisplayName("sizeByStatus returns count for status")
        void sizeByStatusReturnsCountForStatus() {
            assertEquals(3, registry.sizeByStatus(DefinitionStatus.ACTIVE));
            assertEquals(1, registry.sizeByStatus(DefinitionStatus.DRAFT));
            assertEquals(0, registry.sizeByStatus(DefinitionStatus.PENDING));
        }

        @Test
        @DisplayName("getStatistics returns complete stats")
        void getStatisticsReturnsCompleteStats() {
            Map<String, Object> stats = registry.getStatistics();

            assertEquals(4, stats.get("totalEntries"));
            assertNotNull(stats.get("byType"));
            assertNotNull(stats.get("byStatus"));
        }
    }

    @Nested
    @DisplayName("Sync Tracking")
    class SyncTrackingTests {

        @Test
        @DisplayName("sync version and time are tracked")
        void syncVersionAndTimeAreTracked() {
            registry.setSyncVersion(5);
            registry.setLastSyncTime(123456789L);

            assertEquals(5, registry.getSyncVersion());
            assertEquals(123456789L, registry.getLastSyncTime());
        }

        @Test
        @DisplayName("needsSync returns true when stale")
        void needsSyncReturnsTrueWhenStale() {
            registry.setLastSyncTime(0);
            assertTrue(registry.needsSync());
        }

        @Test
        @DisplayName("needsSync returns false when fresh")
        void needsSyncReturnsFalseWhenFresh() {
            registry.setLastSyncTime(System.currentTimeMillis());
            assertFalse(registry.needsSync());
        }
    }

    @Nested
    @DisplayName("Batch Operations")
    class BatchOperationsTests {

        @Test
        @DisplayName("putAll adds multiple entries")
        void putAllAddsMultipleEntries() {
            List<DefinitionEntry> entries = List.of(
                    createEntry("Sword", DefinitionType.ITEM, DefinitionStatus.ACTIVE),
                    createEntry("Shield", DefinitionType.ITEM, DefinitionStatus.ACTIVE),
                    createEntry("Fireball", DefinitionType.SPELL, DefinitionStatus.ACTIVE));

            registry.putAll(entries);

            assertEquals(3, registry.size());
        }

        @Test
        @DisplayName("getAllCids returns all CIDs")
        void getAllCidsReturnsAllCids() {
            DefinitionEntry e1 = createEntry("Sword", DefinitionType.ITEM, DefinitionStatus.ACTIVE);
            DefinitionEntry e2 = createEntry("Shield", DefinitionType.ITEM, DefinitionStatus.ACTIVE);
            registry.put(e1);
            registry.put(e2);

            List<String> cids = registry.getAllCids();

            assertEquals(2, cids.size());
            assertTrue(cids.contains(e1.cid()));
            assertTrue(cids.contains(e2.cid()));
        }

        @Test
        @DisplayName("getAllEntries returns all entries")
        void getAllEntriesReturnsAll() {
            registry.put(createEntry("Sword", DefinitionType.ITEM, DefinitionStatus.ACTIVE));
            registry.put(createEntry("Shield", DefinitionType.ITEM, DefinitionStatus.ACTIVE));

            List<DefinitionEntry> entries = registry.getAllEntries();

            assertEquals(2, entries.size());
        }
    }
}
