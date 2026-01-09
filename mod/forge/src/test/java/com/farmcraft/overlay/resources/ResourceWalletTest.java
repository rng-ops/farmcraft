package com.farmcraft.overlay.resources;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ResourceWallet.
 * 
 * Tests the overlay economy resource management:
 * - Ash (proof-of-work currency)
 * - Sigils (action budget)
 * - Wards (protection)
 * - Seals (non-transferable privileges)
 * - Tokens (transferable)
 */
@DisplayName("ResourceWallet Tests")
class ResourceWalletTest {

    private ResourceWallet wallet;
    private UUID testPlayerId;

    @BeforeEach
    void setUp() {
        testPlayerId = UUID.randomUUID();
        wallet = new ResourceWallet(testPlayerId);
    }

    @Test
    @DisplayName("Constructor initializes with correct defaults")
    void constructorInitializesCorrectly() {
        assertEquals(testPlayerId, wallet.getPlayerId());
        assertEquals(0, wallet.getAsh());
        assertEquals(10, wallet.getSigils()); // Starting sigils
        assertEquals(0, wallet.getWards());
        assertTrue(wallet.getSeals().isEmpty());
        assertTrue(wallet.getTokens().isEmpty());
    }

    @Nested
    @DisplayName("Ash (Proof-of-Work)")
    class AshTests {

        @Test
        @DisplayName("addAsh increases balance")
        void addAshIncreasesBalance() {
            wallet.addAsh(100);
            assertEquals(100, wallet.getAsh());
        }

        @Test
        @DisplayName("addAsh accumulates correctly")
        void addAshAccumulates() {
            wallet.addAsh(50);
            wallet.addAsh(30);
            assertEquals(80, wallet.getAsh());
        }

        @Test
        @DisplayName("spendAsh deducts balance")
        void spendAshDeductsBalance() {
            wallet.addAsh(100);
            assertTrue(wallet.spendAsh(40));
            assertEquals(60, wallet.getAsh());
        }

        @Test
        @DisplayName("spendAsh fails when insufficient funds")
        void spendAshFailsOnInsufficientFunds() {
            wallet.addAsh(50);
            assertFalse(wallet.spendAsh(100));
            assertEquals(50, wallet.getAsh()); // Balance unchanged
        }

        @Test
        @DisplayName("addAsh rejects negative amounts")
        void addAshRejectsNegative() {
            assertThrows(IllegalArgumentException.class, () -> wallet.addAsh(-10));
        }

        @Test
        @DisplayName("spendAsh rejects negative amounts")
        void spendAshRejectsNegative() {
            assertThrows(IllegalArgumentException.class, () -> wallet.spendAsh(-10));
        }
    }

    @Nested
    @DisplayName("Sigils (Action Budget)")
    class SigilTests {

        @Test
        @DisplayName("Starting sigils is 10")
        void startingSigils() {
            assertEquals(10, wallet.getSigils());
        }

        @Test
        @DisplayName("addSigils increases count")
        void addSigilsIncreasesCount() {
            wallet.addSigils(5);
            assertEquals(15, wallet.getSigils());
        }

        @Test
        @DisplayName("addSigils respects MAX_SIGILS")
        void addSigilsRespectsMax() {
            wallet.addSigils(1000);
            assertEquals(ResourceWallet.MAX_SIGILS, wallet.getSigils());
        }

        @Test
        @DisplayName("spendSigils deducts count")
        void spendSigilsDeductsCount() {
            assertTrue(wallet.spendSigils(3));
            assertEquals(7, wallet.getSigils());
        }

        @Test
        @DisplayName("spendSigils fails when insufficient")
        void spendSigilsFailsOnInsufficient() {
            assertFalse(wallet.spendSigils(20));
            assertEquals(10, wallet.getSigils()); // Unchanged
        }
    }

    @Nested
    @DisplayName("Wards (Protection)")
    class WardTests {

        @Test
        @DisplayName("Starting wards is 0")
        void startingWards() {
            assertEquals(0, wallet.getWards());
        }

        @Test
        @DisplayName("addWards increases count")
        void addWardsIncreasesCount() {
            wallet.addWards(10);
            assertEquals(10, wallet.getWards());
        }

        @Test
        @DisplayName("addWards respects MAX_WARDS")
        void addWardsRespectsMax() {
            wallet.addWards(1000);
            assertEquals(ResourceWallet.MAX_WARDS, wallet.getWards());
        }

        @Test
        @DisplayName("spendWards deducts count")
        void spendWardsDeductsCount() {
            wallet.addWards(10);
            assertTrue(wallet.spendWards(3));
            assertEquals(7, wallet.getWards());
        }

        @Test
        @DisplayName("spendWards fails when insufficient")
        void spendWardsFailsOnInsufficient() {
            wallet.addWards(5);
            assertFalse(wallet.spendWards(10));
            assertEquals(5, wallet.getWards());
        }
    }

    @Nested
    @DisplayName("Seals (Privileges)")
    class SealTests {

        @Test
        @DisplayName("grantSeal adds seal")
        void grantSealAddsSeal() {
            wallet.grantSeal(SealType.PUBLISHER_SEAL);
            assertTrue(wallet.hasSeal(SealType.PUBLISHER_SEAL));
        }

        @Test
        @DisplayName("revokeSeal removes seal")
        void revokeSealRemovesSeal() {
            wallet.grantSeal(SealType.PUBLISHER_SEAL);
            wallet.revokeSeal(SealType.PUBLISHER_SEAL);
            assertFalse(wallet.hasSeal(SealType.PUBLISHER_SEAL));
        }

        @Test
        @DisplayName("getSeals returns immutable copy")
        void getSealsReturnsImmutableCopy() {
            wallet.grantSeal(SealType.MODERATOR_SEAL);
            Set<SealType> seals = wallet.getSeals();
            assertThrows(UnsupportedOperationException.class, () -> seals.add(SealType.PUBLISHER_SEAL));
        }

        @Test
        @DisplayName("Multiple seals can be granted")
        void multipleSealsCanBeGranted() {
            wallet.grantSeal(SealType.PUBLISHER_SEAL);
            wallet.grantSeal(SealType.MODERATOR_SEAL);
            assertTrue(wallet.hasSeal(SealType.PUBLISHER_SEAL));
            assertTrue(wallet.hasSeal(SealType.MODERATOR_SEAL));
            assertEquals(2, wallet.getSeals().size());
        }
    }

    @Nested
    @DisplayName("Tokens (Transferable)")
    class TokenTests {

        @Test
        @DisplayName("addTokens creates new token type")
        void addTokensCreatesNewTokenType() {
            wallet.addTokens("gold", 100);
            assertEquals(100, wallet.getTokenBalance("gold"));
        }

        @Test
        @DisplayName("addTokens accumulates for same type")
        void addTokensAccumulatesForSameType() {
            wallet.addTokens("gold", 50);
            wallet.addTokens("gold", 30);
            assertEquals(80, wallet.getTokenBalance("gold"));
        }

        @Test
        @DisplayName("spendTokens deducts balance")
        void spendTokensDeductsBalance() {
            wallet.addTokens("gold", 100);
            assertTrue(wallet.spendTokens("gold", 40));
            assertEquals(60, wallet.getTokenBalance("gold"));
        }

        @Test
        @DisplayName("spendTokens fails when insufficient")
        void spendTokensFailsOnInsufficient() {
            wallet.addTokens("gold", 50);
            assertFalse(wallet.spendTokens("gold", 100));
            assertEquals(50, wallet.getTokenBalance("gold"));
        }

        @Test
        @DisplayName("spendTokens removes empty token type")
        void spendTokensRemovesEmptyType() {
            wallet.addTokens("gold", 50);
            wallet.spendTokens("gold", 50);
            assertEquals(0, wallet.getTokenBalance("gold"));
            assertFalse(wallet.getTokens().containsKey("gold"));
        }

        @Test
        @DisplayName("transferTokens moves tokens between wallets")
        void transferTokensMovesTokens() {
            ResourceWallet recipient = new ResourceWallet(UUID.randomUUID());
            wallet.addTokens("gold", 100);

            assertTrue(wallet.transferTokens("gold", 40, recipient));

            assertEquals(60, wallet.getTokenBalance("gold"));
            assertEquals(40, recipient.getTokenBalance("gold"));
        }

        @Test
        @DisplayName("getTokens returns immutable copy")
        void getTokensReturnsImmutableCopy() {
            wallet.addTokens("gold", 100);
            Map<String, Long> tokens = wallet.getTokens();
            assertThrows(UnsupportedOperationException.class, () -> tokens.put("silver", 50L));
        }
    }

    @Nested
    @DisplayName("Cost Checking")
    class CostCheckingTests {

        @Test
        @DisplayName("canAfford returns true when resources sufficient")
        void canAffordReturnsTrueWhenSufficient() {
            wallet.addAsh(100);
            // Wallet starts with 10 sigils

            ResourceCost cost = new ResourceCost.Builder()
                    .ash(50)
                    .sigils(5)
                    .build();

            assertTrue(wallet.canAfford(cost));
        }

        @Test
        @DisplayName("canAfford returns false when ash insufficient")
        void canAffordReturnsFalseWhenAshInsufficient() {
            wallet.addAsh(30);

            ResourceCost cost = new ResourceCost.Builder()
                    .ash(50)
                    .build();

            assertFalse(wallet.canAfford(cost));
        }

        @Test
        @DisplayName("canAfford checks seal requirements")
        void canAffordChecksSealRequirements() {
            wallet.addAsh(100);

            ResourceCost cost = new ResourceCost.Builder()
                    .ash(10)
                    .requireSeal(SealType.PUBLISHER_SEAL)
                    .build();

            assertFalse(wallet.canAfford(cost));

            wallet.grantSeal(SealType.PUBLISHER_SEAL);
            assertTrue(wallet.canAfford(cost));
        }

        @Test
        @DisplayName("spend deducts all resources atomically")
        void spendDeductsAtomically() {
            wallet.addAsh(100);
            wallet.addTokens("gold", 50);

            ResourceCost cost = new ResourceCost.Builder()
                    .ash(30)
                    .sigils(2)
                    .token("gold", 20)
                    .build();

            assertTrue(wallet.spend(cost));

            assertEquals(70, wallet.getAsh());
            assertEquals(8, wallet.getSigils());
            assertEquals(30, wallet.getTokenBalance("gold"));
        }

        @Test
        @DisplayName("spend returns false and doesn't modify when insufficient")
        void spendReturnsFalseWhenInsufficient() {
            wallet.addAsh(10);

            ResourceCost cost = new ResourceCost.Builder()
                    .ash(100)
                    .build();

            assertFalse(wallet.spend(cost));
            assertEquals(10, wallet.getAsh()); // Unchanged
        }
    }

    @Nested
    @DisplayName("Serialization")
    class SerializationTests {

        @Test
        @DisplayName("snapshot captures current state")
        void snapshotCapturesState() {
            wallet.addAsh(500);
            wallet.addSigils(20);
            wallet.addWards(10);
            wallet.grantSeal(SealType.PUBLISHER_SEAL);
            wallet.addTokens("gold", 100);

            ResourceWallet.WalletSnapshot snapshot = wallet.snapshot();

            assertEquals(testPlayerId, snapshot.playerId());
            assertEquals(500, snapshot.ash());
            assertTrue(snapshot.sigils() > 0);
            assertTrue(snapshot.wards() > 0);
            assertTrue(snapshot.seals().contains(SealType.PUBLISHER_SEAL));
            assertEquals(100L, snapshot.tokens().get("gold"));
        }

        @Test
        @DisplayName("fromSnapshot restores wallet correctly")
        void fromSnapshotRestoresWallet() {
            wallet.addAsh(500);
            wallet.addWards(10);
            wallet.grantSeal(SealType.MODERATOR_SEAL);
            wallet.addTokens("silver", 200);

            ResourceWallet.WalletSnapshot snapshot = wallet.snapshot();
            ResourceWallet restored = ResourceWallet.fromSnapshot(snapshot);

            assertEquals(wallet.getPlayerId(), restored.getPlayerId());
            assertEquals(wallet.getAsh(), restored.getAsh());
            assertEquals(wallet.getWards(), restored.getWards());
            assertTrue(restored.hasSeal(SealType.MODERATOR_SEAL));
            assertEquals(200, restored.getTokenBalance("silver"));
        }
    }
}
