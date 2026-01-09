package com.farmcraft.overlay;

import java.security.PublicKey;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Core types for the privacy-preserving overlay network.
 * 
 * DESIGN PRINCIPLES:
 * - No stable global identifiers
 * - Session-ephemeral keys for discovery
 * - Long-lived keys only for explicit friends
 * - All handles are scoped and expiring
 * - Correlation requires multi-party cooperation
 */
public class OverlayTypes {

    private OverlayTypes() {
    } // Static types only

    // ═══════════════════════════════════════════════════════════════════
    // Identity Types
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Session-ephemeral identity used for discovery and rendezvous.
     * NEVER persisted. Regenerated each session.
     * 
     * Privacy: Cannot be linked across sessions.
     */
    public record SessionIdentity(
            /** Ephemeral public key for this session */
            PublicKey sessionPublicKey,
            /** When this identity was created */
            Instant createdAt,
            /** Capabilities advertised (hashed) */
            String capabilitiesHash) {
    }

    /**
     * Long-lived identity used ONLY for mutual friends.
     * Stored encrypted locally. Never shared publicly.
     * 
     * Privacy: Only visible to explicit mutual friends.
     */
    public record FriendsIdentity(
            /** Stable public key for friends */
            PublicKey friendsPublicKey,
            /** Display name (optional, user-controlled) */
            Optional<String> displayName,
            /** When this identity was created */
            Instant createdAt) {
    }

    /**
     * Cooperative overlay handle derived through threshold OPRF.
     * Scoped to cohort + epoch. Requires multi-party cooperation.
     * 
     * Privacy: Same user produces same handle within cohort+epoch,
     * but requires t-of-n servers plus client cooperation.
     */
    public record OverlayHandle(
            /** The derived handle (base32 encoded) */
            String handle,
            /** Cohort this handle is valid for */
            String cohortId,
            /** Epoch bucket (weekly rotation) */
            long epochBucket,
            /** When this handle expires */
            Instant expiresAt,
            /** Proof-of-work nonce used */
            long powNonce) {
    }

    // ═══════════════════════════════════════════════════════════════════
    // Rendezvous Types
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Server hint identifier for rendezvous lookup.
     * Derived from server address + epoch to prevent permanent tracking.
     */
    public record ServerHintId(
            /** The derived hint ID */
            String hintId,
            /** Epoch bucket this hint is valid for */
            long epochBucket,
            /** Original server address (normalized) */
            String normalizedServerAddr) {
    }

    /**
     * Rendezvous endpoint information.
     */
    public record RendezvousEndpoint(
            /** WebSocket or HTTP endpoint URL */
            String endpointUrl,
            /** Public key of the rendezvous server */
            Optional<PublicKey> serverPublicKey,
            /** Delegation token if delegated */
            Optional<String> delegationToken,
            /** Whether this is a delegated third-party */
            boolean isDelegated) {
    }

    /**
     * Result of a rendezvous handshake.
     */
    public record RendezvousResult(
            /** Whether rendezvous succeeded */
            boolean success,
            /** Assigned session token */
            Optional<String> sessionToken,
            /** Connected peers in this rendezvous */
            int peerCount,
            /** Features supported by this rendezvous */
            Set<String> features,
            /** Error message if failed */
            Optional<String> error) {
    }

    // ═══════════════════════════════════════════════════════════════════
    // Fog-of-War Types
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Condition bucket for fog-of-war topic derivation.
     * Uses coarse categories to prevent precise tracking.
     */
    public record ConditionBucket(
            /** Coarse dimension (overworld/nether/end) */
            DimensionCategory dimension,
            /** Coarse biome category */
            BiomeCategory biome,
            /** Time-of-day bucket (day/evening/night) */
            TimeOfDayBucket timeOfDay,
            /** Hash of capabilities for compatibility matching */
            String capabilitiesHash) {
    }

    public enum DimensionCategory {
        OVERWORLD, NETHER, END, OTHER
    }

    public enum BiomeCategory {
        TEMPERATE, COLD, HOT, AQUATIC, OTHER
    }

    public enum TimeOfDayBucket {
        DAY, EVENING, NIGHT
    }

    /**
     * A discovered shard in the fog-of-war overlay.
     * Represents a PLACE/capability set, not a person.
     * 
     * Privacy: No usernames, no UUIDs, no coordinates.
     */
    public record FogShard(
            /** Ephemeral peer ID (session-scoped) */
            String peerId,
            /** Capabilities hash for compatibility */
            String capabilitiesHash,
            /** Manifest CID summary */
            String manifestCidSummary,
            /** Freshness bucket */
            FreshnessBucket freshness,
            /** Number of corroborations (for k-anonymity) */
            int corroborations,
            /** Trust tier based on verification */
            TrustTier trustTier,
            /** When this shard was first seen */
            Instant firstSeen,
            /** When this shard expires (decay) */
            Instant expiresAt) {
    }

    public enum FreshnessBucket {
        RECENT, // < 15 minutes
        TODAY, // < 24 hours
        THIS_WEEK // < 7 days
    }

    public enum TrustTier {
        UNVERIFIED, // No corroborations
        LOW, // 1-2 corroborations
        MEDIUM, // 3-5 corroborations (meets k-anonymity)
        HIGH // 6+ corroborations
    }

    /**
     * Fog-of-war announcement message.
     */
    public record FogAnnouncement(
            /** Session-ephemeral peer ID */
            String peerId,
            /** Capabilities hash */
            String capabilitiesHash,
            /** Manifest CID summary */
            String manifestCidSummary,
            /** Timestamp bucket (coarse) */
            long timeBucket,
            /** Signature with session key */
            byte[] signature) {
    }

    // ═══════════════════════════════════════════════════════════════════
    // Search and View Receipt Types
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Search request with rate limiting and scope.
     */
    public record SearchRequest(
            /** Searcher's session identity */
            SessionIdentity searcher,
            /** Query (overlay handle or partial) */
            String query,
            /** Search scope */
            SearchScope scope,
            /** Proof-of-work for rate limiting */
            long powNonce,
            /** Request timestamp */
            Instant timestamp,
            /** Signature */
            byte[] signature) {
    }

    public enum SearchScope {
        COHORT, // Same cohort only
        FEDERATION, // All federated servers
        FRIENDS // Friends only (uses different path)
    }

    /**
     * Search response with mandatory receipt challenge.
     * 
     * Privacy: Target is ALWAYS notified of views.
     */
    public record SearchResponse(
            /** Whether the target was found */
            boolean found,
            /** Minimal profile stub (if discoverable) */
            Optional<ProfileStub> profileStub,
            /** Receipt challenge nonce - MUST be returned */
            String receiptChallengeNonce,
            /** When this response expires without receipt */
            Instant expiresAt) {
    }

    /**
     * Minimal profile stub - limited information.
     */
    public record ProfileStub(
            /** Overlay handle (scoped, expiring) */
            String overlayHandle,
            /** Capabilities summary */
            String capabilitiesSummary,
            /** Freshness bucket */
            FreshnessBucket freshness,
            /** Whether detailed profile is available */
            boolean detailedProfileAvailable) {
    }

    /**
     * View receipt - proof that searcher viewed the profile.
     * MANDATORY for search to complete.
     * 
     * Privacy: Anonymous viewing allowed, but receipt still produced.
     */
    public record ViewReceipt(
            /** The receipt challenge nonce */
            String challengeNonce,
            /** Searcher's public key (session or anonymous) */
            PublicKey viewerKey,
            /** Whether this is an anonymous view */
            boolean anonymous,
            /** Timestamp */
            Instant timestamp,
            /** Signature */
            byte[] signature) {
    }

    // ═══════════════════════════════════════════════════════════════════
    // Friends Overlay Types
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Friend connection with mutual consent.
     */
    public record FriendConnection(
            /** Friend's public key */
            PublicKey friendPublicKey,
            /** Display name */
            String displayName,
            /** When the friendship was established */
            Instant establishedAt,
            /** Current presence (if shared) */
            Optional<FriendPresence> presence,
            /** Shared content CIDs */
            List<String> sharedContentCids) {
    }

    /**
     * Friend presence information.
     * 
     * Privacy: NO server IP, NO coordinates, NO encounter logs.
     */
    public record FriendPresence(
            /** Presence status */
            PresenceStatus status,
            /** Coarse last-seen bucket */
            Optional<LastSeenBucket> lastSeenBucket,
            /** When this presence was last updated */
            Instant updatedAt) {
    }

    public enum PresenceStatus {
        ONLINE, AWAY, OFFLINE
    }

    public enum LastSeenBucket {
        JUST_NOW, // < 15 minutes
        TODAY, // < 24 hours
        THIS_WEEK, // < 7 days
        OLDER // > 7 days
    }

    /**
     * Friend invite for establishing mutual consent.
     */
    public record FriendInvite(
            /** Inviter's friends public key */
            PublicKey inviterPublicKey,
            /** Invite code (for QR or manual entry) */
            String inviteCode,
            /** When this invite expires */
            Instant expiresAt,
            /** Signature */
            byte[] signature) {
    }

    /**
     * Shared content between friends.
     */
    public record SharedContent(
            /** Content type */
            SharedContentType type,
            /** Content identifier (CID) */
            String contentCid,
            /** Description */
            String description,
            /** When shared */
            Instant sharedAt) {
    }

    public enum SharedContentType {
        MANIFEST, // Mod manifest
        RECIPE_SET, // Recipe collection
        ASSET_PACK, // Asset package
        SHADER_PREFERENCES // Coarse, noisy, rotating shader prefs
    }

    // ═══════════════════════════════════════════════════════════════════
    // Cooperative Handle Types
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Request for threshold OPRF evaluation.
     */
    public record OPRFRequest(
            /** Blinded input x */
            byte[] blindedInput,
            /** Proof-of-work nonce */
            long powNonce,
            /** Cohort ID */
            String cohortId,
            /** Epoch bucket */
            long epochBucket,
            /** Requester's session key */
            PublicKey requesterKey,
            /** Signature */
            byte[] signature) {
    }

    /**
     * Partial evaluation from a federation server.
     */
    public record OPRFShare(
            /** Server's share index */
            int shareIndex,
            /** Partial evaluation */
            byte[] partialEvaluation,
            /** Server's public key */
            PublicKey serverPublicKey,
            /** Signature */
            byte[] signature) {
    }

    /**
     * Federation server info for OPRF.
     */
    public record FederationServer(
            /** Server ID */
            String serverId,
            /** Server endpoint */
            String endpoint,
            /** Server's public key */
            PublicKey publicKey,
            /** Share index in t-of-n scheme */
            int shareIndex,
            /** Whether this server is currently available */
            boolean available) {
    }
}
