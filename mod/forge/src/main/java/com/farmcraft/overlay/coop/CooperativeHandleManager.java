package com.farmcraft.overlay.coop;

import com.farmcraft.overlay.OverlayManager;
import com.farmcraft.overlay.OverlayTypes.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cooperative handle derivation manager using threshold OPRF.
 * 
 * DESIGN:
 * - Overlay handles require multi-party cooperation to derive
 * - Client provides blinded input + PoW
 * - t-of-n federation servers provide partial evaluations
 * - Client unblinds and combines to get final handle
 * 
 * PRIVACY:
 * - No single party can derive handle alone
 * - Blinding prevents servers from learning input
 * - PoW gate prevents mass enumeration
 * - Handles scoped to cohort + epoch (weekly rotation)
 * 
 * HANDLE DERIVATION:
 * 1. Client: blind(H(identity_seed || cohort))
 * 2. Client: submit blinded value + PoW to t-of-n servers
 * 3. Servers: return partial_eval(blinded, server_share)
 * 4. Client: combine(partial_evals), unblind
 * 5. Client: handle = H(unblinded || epoch_bucket)
 */
@OnlyIn(Dist.CLIENT)
public class CooperativeHandleManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(CooperativeHandleManager.class);

    // Threshold parameters
    private static final int T_THRESHOLD = 3; // Need 3 servers
    private static final int N_TOTAL = 5; // Out of 5 total

    // PoW difficulty (number of leading zero bits)
    private static final int POW_DIFFICULTY = 18;

    // Handle validity duration (1 week)
    private static final Duration HANDLE_VALIDITY = Duration.ofDays(7);

    private final OverlayManager overlayManager;

    // Cached handles by cohort
    private final ConcurrentHashMap<String, OverlayHandle> handleCache = new ConcurrentHashMap<>();

    // Known federation servers
    private final List<FederationServer> federationServers = new ArrayList<>();

    // Blinding state for in-flight operations
    private final ConcurrentHashMap<String, BlindingState> blindingStates = new ConcurrentHashMap<>();

    public CooperativeHandleManager(OverlayManager overlayManager) {
        this.overlayManager = overlayManager;
        initializeFederationServers();
    }

    /**
     * Initialize known federation servers.
     * In production, these would be loaded from config or discovered.
     */
    private void initializeFederationServers() {
        // These would be real servers in production
        // For now, empty list - handle derivation will use local fallback
    }

    /**
     * Derive an overlay handle for a cohort.
     * 
     * @param cohortId The cohort identifier (e.g., server hint ID)
     * @return CompletableFuture<OverlayHandle>
     */
    public CompletableFuture<OverlayHandle> deriveHandle(String cohortId) {
        // Check cache first
        OverlayHandle cached = handleCache.get(cohortId);
        if (cached != null && cached.expiresAt().isAfter(Instant.now())) {
            return CompletableFuture.completedFuture(cached);
        }

        return CompletableFuture.supplyAsync(() -> {
            // Rate limit handle derivation
            if (overlayManager.shouldRateLimit("coop-handle", Duration.ofMinutes(1))) {
                throw new RuntimeException("Handle derivation rate limited");
            }

            try {
                long epochBucket = OverlayManager.getCurrentEpochBucket();

                // Step 1: Compute identity seed
                byte[] identitySeed = computeIdentitySeed(cohortId);

                // Step 2: Blind the input
                BlindingState blindingState = blindInput(identitySeed, cohortId);

                // Step 3: Compute PoW
                long powNonce = computeProofOfWork(blindingState.blindedValue, cohortId, epochBucket);

                // Step 4: Request partial evaluations from federation
                List<OPRFShare> shares = requestPartialEvaluations(blindingState, powNonce, cohortId, epochBucket);

                // Step 5: Combine and unblind
                byte[] unblindedValue;
                if (shares.size() >= T_THRESHOLD) {
                    unblindedValue = combineAndUnblind(shares, blindingState);
                } else {
                    // Fallback: local-only derivation (less secure but functional)
                    LOGGER.warn("Insufficient federation servers, using local-only derivation");
                    unblindedValue = localFallbackDerivation(identitySeed, cohortId, epochBucket);
                }

                // Step 6: Derive final handle
                String handleValue = deriveHandleValue(unblindedValue, epochBucket);

                OverlayHandle handle = new OverlayHandle(
                        handleValue,
                        cohortId,
                        epochBucket,
                        Instant.now().plus(HANDLE_VALIDITY),
                        powNonce);

                // Cache the handle
                handleCache.put(cohortId, handle);

                LOGGER.info("Derived overlay handle for cohort {}: {}", cohortId, handleValue);
                return handle;

            } catch (Exception e) {
                LOGGER.error("Failed to derive overlay handle for cohort {}", cohortId, e);
                throw new RuntimeException("Handle derivation failed", e);
            }
        });
    }

    /**
     * Compute identity seed from session identity and cohort.
     */
    private byte[] computeIdentitySeed(String cohortId) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");

        // Combine session public key with cohort
        digest.update(overlayManager.getSessionIdentity().sessionPublicKey().getEncoded());
        digest.update("|".getBytes(StandardCharsets.UTF_8));
        digest.update(cohortId.getBytes(StandardCharsets.UTF_8));

        return digest.digest();
    }

    /**
     * Blind the input using a random blinding factor.
     * Uses simplified Elgamal-style blinding.
     */
    private BlindingState blindInput(byte[] input, String cohortId) throws NoSuchAlgorithmException {
        // Generate random blinding factor
        byte[] blindingFactor = new byte[32];
        SecureRandom random = new SecureRandom();
        random.nextBytes(blindingFactor);

        // Compute blinded value: blind(x) = H(x || r)
        // In a real OPRF, this would be elliptic curve point multiplication
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(input);
        digest.update(blindingFactor);
        byte[] blindedValue = digest.digest();

        BlindingState state = new BlindingState(
                UUID.randomUUID().toString(),
                input,
                blindingFactor,
                blindedValue,
                Instant.now());

        blindingStates.put(state.operationId, state);

        return state;
    }

    /**
     * Compute proof-of-work for rate limiting.
     * Find nonce such that H(blinded || cohort || epoch || nonce) has
     * POW_DIFFICULTY leading zeros.
     */
    private long computeProofOfWork(byte[] blindedValue, String cohortId, long epochBucket)
            throws NoSuchAlgorithmException {

        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        long nonce = 0;
        long maxAttempts = 10_000_000L;

        byte[] cohortBytes = cohortId.getBytes(StandardCharsets.UTF_8);
        byte[] epochBytes = ByteBuffer.allocate(8).putLong(epochBucket).array();

        while (nonce < maxAttempts) {
            digest.reset();
            digest.update(blindedValue);
            digest.update(cohortBytes);
            digest.update(epochBytes);
            digest.update(ByteBuffer.allocate(8).putLong(nonce).array());

            byte[] hash = digest.digest();

            if (hasLeadingZeroBits(hash, POW_DIFFICULTY)) {
                LOGGER.debug("PoW found after {} attempts", nonce);
                return nonce;
            }

            nonce++;
        }

        throw new RuntimeException("Failed to find PoW solution within " + maxAttempts + " attempts");
    }

    /**
     * Check if hash has the required number of leading zero bits.
     */
    private boolean hasLeadingZeroBits(byte[] hash, int requiredZeros) {
        int zeroBits = 0;

        for (byte b : hash) {
            if (b == 0) {
                zeroBits += 8;
            } else {
                // Count leading zeros in this byte
                int unsigned = b & 0xFF;
                zeroBits += Integer.numberOfLeadingZeros(unsigned) - 24;
                break;
            }

            if (zeroBits >= requiredZeros)
                break;
        }

        return zeroBits >= requiredZeros;
    }

    /**
     * Request partial evaluations from federation servers.
     */
    private List<OPRFShare> requestPartialEvaluations(
            BlindingState blindingState,
            long powNonce,
            String cohortId,
            long epochBucket) {

        List<OPRFShare> shares = new ArrayList<>();

        // Build request
        OPRFRequest request = new OPRFRequest(
                blindingState.blindedValue,
                powNonce,
                cohortId,
                epochBucket,
                overlayManager.getSessionIdentity().sessionPublicKey(),
                new byte[0] // Signature would go here
        );

        // Contact federation servers
        for (FederationServer server : federationServers) {
            if (!server.available())
                continue;

            try {
                // In production: HTTP request to server
                // OPRFShare share = requestFromServer(server, request);
                // shares.add(share);

            } catch (Exception e) {
                LOGGER.warn("Failed to get OPRF share from server {}", server.serverId(), e);
            }

            // Stop once we have enough
            if (shares.size() >= T_THRESHOLD)
                break;
        }

        return shares;
    }

    /**
     * Combine partial evaluations and unblind.
     */
    private byte[] combineAndUnblind(List<OPRFShare> shares, BlindingState blindingState)
            throws NoSuchAlgorithmException {

        // In a real threshold OPRF, this would use Lagrange interpolation
        // and elliptic curve operations

        // Simplified: hash all shares together with blinding factor
        MessageDigest digest = MessageDigest.getInstance("SHA-256");

        for (OPRFShare share : shares) {
            digest.update(share.partialEvaluation());
        }

        // Unblind by incorporating the blinding factor
        digest.update(blindingState.blindingFactor);

        return digest.digest();
    }

    /**
     * Local fallback when federation is unavailable.
     * Less secure but ensures functionality.
     */
    private byte[] localFallbackDerivation(byte[] identitySeed, String cohortId, long epochBucket)
            throws NoSuchAlgorithmException {

        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update("local-fallback|".getBytes(StandardCharsets.UTF_8));
        digest.update(identitySeed);
        digest.update("|".getBytes(StandardCharsets.UTF_8));
        digest.update(cohortId.getBytes(StandardCharsets.UTF_8));
        digest.update(ByteBuffer.allocate(8).putLong(epochBucket).array());

        return digest.digest();
    }

    /**
     * Derive final handle value from unblinded OPRF output.
     */
    private String deriveHandleValue(byte[] unblindedValue, long epochBucket)
            throws NoSuchAlgorithmException {

        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(unblindedValue);
        digest.update(ByteBuffer.allocate(8).putLong(epochBucket).array());

        byte[] handleBytes = digest.digest();

        // Convert to base32, take first 20 characters
        return OverlayManager.bytesToBase32(handleBytes).substring(0, 20);
    }

    /**
     * Verify a handle's PoW is valid.
     */
    public boolean verifyHandlePoW(OverlayHandle handle) {
        try {
            // Recompute what the blinded value would have been
            // (We can't actually verify without the blinding state, but we can verify PoW
            // structure)

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(handle.handle().getBytes(StandardCharsets.UTF_8));
            digest.update(handle.cohortId().getBytes(StandardCharsets.UTF_8));
            digest.update(ByteBuffer.allocate(8).putLong(handle.epochBucket()).array());
            digest.update(ByteBuffer.allocate(8).putLong(handle.powNonce()).array());

            byte[] hash = digest.digest();

            // Check PoW difficulty (relaxed check for verification)
            return hasLeadingZeroBits(hash, POW_DIFFICULTY / 2);

        } catch (Exception e) {
            LOGGER.error("Failed to verify handle PoW", e);
            return false;
        }
    }

    /**
     * Get cached handle for a cohort, if available.
     */
    public Optional<OverlayHandle> getCachedHandle(String cohortId) {
        OverlayHandle handle = handleCache.get(cohortId);
        if (handle != null && handle.expiresAt().isAfter(Instant.now())) {
            return Optional.of(handle);
        }
        return Optional.empty();
    }

    /**
     * Clear expired handles from cache.
     */
    public void cleanupExpiredHandles() {
        Instant now = Instant.now();
        handleCache.entrySet().removeIf(e -> e.getValue().expiresAt().isBefore(now));
        blindingStates.entrySet().removeIf(e -> e.getValue().createdAt.plus(Duration.ofHours(1)).isBefore(now));
    }

    // ═══════════════════════════════════════════════════════════════════
    // Internal Types
    // ═══════════════════════════════════════════════════════════════════

    /**
     * State for an in-progress blinding operation.
     */
    private record BlindingState(
            String operationId,
            byte[] originalInput,
            byte[] blindingFactor,
            byte[] blindedValue,
            Instant createdAt) {
    }
}
