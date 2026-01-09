package com.farmcraft.overlay.search;

import com.farmcraft.overlay.OverlayConfig.DiscoverabilityScope;
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
 * Search manager with mandatory view receipts.
 * 
 * DESIGN (LinkedIn-style):
 * - All profile views produce receipts
 * - Target is ALWAYS notified
 * - Anonymous viewing allowed, but receipt still generated
 * - Rate limiting per searcher
 * 
 * PRIVACY GUARANTEES:
 * - No silent profile viewing
 * - No last-seen oracle (coarse time buckets only)
 * - No third-party friend queries
 * - Rate limits prevent enumeration
 * 
 * SEARCH FLOW:
 * 1. Searcher submits query + PoW
 * 2. Server checks rate limit, finds target
 * 3. Server returns ProfileStub + receiptChallengeNonce
 * 4. Searcher MUST return ViewReceipt to complete search
 * 5. Target is notified of view (anonymous or identified)
 */
@OnlyIn(Dist.CLIENT)
public class SearchManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(SearchManager.class);

    // PoW difficulty for search (lighter than handle derivation)
    private static final int SEARCH_POW_DIFFICULTY = 14;

    // Rate limit: max searches per hour
    private static final int MAX_SEARCHES_PER_HOUR = 20;

    // Receipt timeout
    private static final Duration RECEIPT_TIMEOUT = Duration.ofMinutes(2);

    private final OverlayManager overlayManager;

    // Search history (for rate limiting)
    private final ConcurrentHashMap<Long, Integer> searchCountByHour = new ConcurrentHashMap<>();

    // Pending receipts we need to send
    private final ConcurrentHashMap<String, PendingReceipt> pendingReceipts = new ConcurrentHashMap<>();

    // Views of our profile (we were the target)
    private final List<ProfileView> profileViews = Collections.synchronizedList(new ArrayList<>());

    // Our searchable profile (if discoverable)
    private volatile SearchableProfile myProfile;

    public SearchManager(OverlayManager overlayManager) {
        this.overlayManager = overlayManager;
    }

    /**
     * Search for a user by overlay handle.
     * 
     * @param query     The overlay handle or partial
     * @param scope     Search scope
     * @param anonymous Whether to view anonymously
     * @return CompletableFuture<SearchResponse>
     */
    public CompletableFuture<SearchResponse> search(String query, SearchScope scope, boolean anonymous) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Check rate limit
                if (!checkRateLimit()) {
                    return new SearchResponse(
                            false,
                            Optional.empty(),
                            "",
                            Instant.now());
                }

                // Compute PoW for rate limiting
                long powNonce = computeSearchPoW(query);

                // Build search request
                SearchRequest request = new SearchRequest(
                        overlayManager.getSessionIdentity(),
                        query,
                        scope,
                        powNonce,
                        Instant.now(),
                        new byte[0] // Signature
                );

                // Execute search
                SearchResponse response = executeSearch(request);

                // If found, queue receipt
                if (response.found() && !response.receiptChallengeNonce().isEmpty()) {
                    queueReceipt(response.receiptChallengeNonce(), query, anonymous);
                }

                return response;

            } catch (Exception e) {
                LOGGER.error("Search failed for query: {}", query, e);
                return new SearchResponse(
                        false,
                        Optional.empty(),
                        "",
                        Instant.now());
            }
        });
    }

    /**
     * Check if we're within rate limits.
     */
    private boolean checkRateLimit() {
        long currentHour = System.currentTimeMillis() / (60 * 60 * 1000);
        int count = searchCountByHour.compute(currentHour, (k, v) -> v == null ? 1 : v + 1);

        // Clean old entries
        long oldHour = currentHour - 2;
        searchCountByHour.keySet().removeIf(h -> h < oldHour);

        if (count > MAX_SEARCHES_PER_HOUR) {
            LOGGER.warn("Search rate limit exceeded ({}/{})", count, MAX_SEARCHES_PER_HOUR);
            return false;
        }

        return true;
    }

    /**
     * Compute PoW for search rate limiting.
     */
    private long computeSearchPoW(String query) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] queryBytes = query.getBytes(StandardCharsets.UTF_8);
        byte[] sessionKey = overlayManager.getSessionIdentity().sessionPublicKey().getEncoded();

        long nonce = 0;
        long maxAttempts = 1_000_000L;

        while (nonce < maxAttempts) {
            digest.reset();
            digest.update(queryBytes);
            digest.update(sessionKey);
            digest.update(ByteBuffer.allocate(8).putLong(nonce).array());

            byte[] hash = digest.digest();

            if (hasLeadingZeroBits(hash, SEARCH_POW_DIFFICULTY)) {
                return nonce;
            }

            nonce++;
        }

        throw new RuntimeException("Failed to compute search PoW");
    }

    private boolean hasLeadingZeroBits(byte[] hash, int requiredZeros) {
        int zeroBits = 0;
        for (byte b : hash) {
            if (b == 0) {
                zeroBits += 8;
            } else {
                zeroBits += Integer.numberOfLeadingZeros(b & 0xFF) - 24;
                break;
            }
            if (zeroBits >= requiredZeros)
                break;
        }
        return zeroBits >= requiredZeros;
    }

    /**
     * Execute the search request.
     */
    private SearchResponse executeSearch(SearchRequest request) {
        // In production: send to rendezvous/federation server
        // For now: return mock response

        LOGGER.debug("Executing search for: {}", request.query());

        // Generate receipt challenge nonce
        byte[] nonceBytes = new byte[16];
        new SecureRandom().nextBytes(nonceBytes);
        String challengeNonce = OverlayManager.bytesToBase32(nonceBytes).substring(0, 24);

        // Mock: no results found
        return new SearchResponse(
                false,
                Optional.empty(),
                challengeNonce,
                Instant.now().plus(RECEIPT_TIMEOUT));
    }

    /**
     * Queue a view receipt to be sent.
     */
    private void queueReceipt(String challengeNonce, String query, boolean anonymous) {
        PendingReceipt receipt = new PendingReceipt(
                challengeNonce,
                query,
                anonymous,
                Instant.now(),
                Instant.now().plus(RECEIPT_TIMEOUT));

        pendingReceipts.put(challengeNonce, receipt);

        // Send receipt asynchronously
        sendReceiptAsync(receipt);
    }

    /**
     * Send view receipt.
     */
    private void sendReceiptAsync(PendingReceipt pending) {
        CompletableFuture.runAsync(() -> {
            try {
                // Determine viewer key
                PublicKey viewerKey;
                if (pending.anonymous) {
                    // Generate ephemeral key for anonymous view
                    KeyPairGenerator keyGen = KeyPairGenerator.getInstance("EC");
                    keyGen.initialize(256, new SecureRandom());
                    viewerKey = keyGen.generateKeyPair().getPublic();
                } else {
                    viewerKey = overlayManager.getSessionIdentity().sessionPublicKey();
                }

                // Create receipt
                ViewReceipt receipt = new ViewReceipt(
                        pending.challengeNonce,
                        viewerKey,
                        pending.anonymous,
                        Instant.now(),
                        new byte[0] // Signature
                );

                // In production: send to server
                LOGGER.debug("Sent view receipt for challenge: {}", pending.challengeNonce);

                // Remove from pending
                pendingReceipts.remove(pending.challengeNonce);

            } catch (Exception e) {
                LOGGER.error("Failed to send view receipt", e);
            }
        });
    }

    // ═══════════════════════════════════════════════════════════════════
    // Profile Management (Being Searchable)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Set our searchable profile.
     */
    public void setSearchableProfile(SearchableProfile profile) {
        this.myProfile = profile;
        LOGGER.info("Updated searchable profile with discoverability: {}", profile.discoverability);
    }

    /**
     * Handle incoming view notification.
     * Called when someone viewed our profile.
     */
    public void handleViewNotification(ViewNotification notification) {
        ProfileView view = new ProfileView(
                notification.viewerInfo(),
                notification.anonymous(),
                notification.viewedAt(),
                notification.query());

        profileViews.add(view);

        // Limit history
        while (profileViews.size() > 100) {
            profileViews.remove(0);
        }

        LOGGER.info("Profile viewed by {}{}",
                notification.anonymous() ? "anonymous user" : notification.viewerInfo(),
                notification.query().map(q -> " (query: " + q + ")").orElse(""));
    }

    /**
     * Get recent profile views.
     */
    public List<ProfileView> getProfileViews() {
        return new ArrayList<>(profileViews);
    }

    /**
     * Clear profile view history.
     */
    public void clearViewHistory() {
        profileViews.clear();
    }

    // ═══════════════════════════════════════════════════════════════════
    // Public API
    // ═══════════════════════════════════════════════════════════════════

    public int getSearchesThisHour() {
        long currentHour = System.currentTimeMillis() / (60 * 60 * 1000);
        return searchCountByHour.getOrDefault(currentHour, 0);
    }

    public int getRemainingSearches() {
        return Math.max(0, MAX_SEARCHES_PER_HOUR - getSearchesThisHour());
    }

    public int getPendingReceiptCount() {
        // Clean expired
        pendingReceipts.entrySet().removeIf(e -> e.getValue().expiresAt.isBefore(Instant.now()));
        return pendingReceipts.size();
    }

    public Optional<SearchableProfile> getMyProfile() {
        return Optional.ofNullable(myProfile);
    }

    // ═══════════════════════════════════════════════════════════════════
    // Internal Types
    // ═══════════════════════════════════════════════════════════════════

    private record PendingReceipt(
            String challengeNonce,
            String query,
            boolean anonymous,
            Instant createdAt,
            Instant expiresAt) {
    }

    /**
     * Our searchable profile configuration.
     */
    public record SearchableProfile(
            /** Our current overlay handle */
            OverlayHandle handle,
            /** Discoverability setting */
            DiscoverabilityScope discoverability,
            /** Capabilities summary to show */
            String capabilitiesSummary,
            /** Whether detailed profile is available */
            boolean detailedProfileEnabled) {
    }

    /**
     * View notification when someone viewed our profile.
     */
    public record ViewNotification(
            /** Viewer info (empty if anonymous) */
            String viewerInfo,
            /** Whether this was an anonymous view */
            boolean anonymous,
            /** When viewed */
            Instant viewedAt,
            /** The query used (if available) */
            Optional<String> query) {
    }

    /**
     * Record of someone viewing our profile.
     */
    public record ProfileView(
            /** Viewer info */
            String viewerInfo,
            /** Anonymous? */
            boolean anonymous,
            /** When */
            Instant viewedAt,
            /** Query used */
            Optional<String> query) {
    }
}
