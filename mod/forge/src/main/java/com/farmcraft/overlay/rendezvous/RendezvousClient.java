package com.farmcraft.overlay.rendezvous;

import com.farmcraft.overlay.OverlayManager;
import com.farmcraft.overlay.OverlayTypes.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-associated rendezvous client.
 * 
 * DESIGN:
 * - Each Minecraft server MAY provide rendezvous hints
 * - Client checks hints against configured federation
 * - Supports delegation for servers without rendezvous
 * - All communications use session-ephemeral keys
 * 
 * PRIVACY:
 * - Server hints are hashed with epoch to prevent tracking
 * - No permanent identifiers in rendezvous
 * - Delegated rendezvous respects origin server's config
 */
@OnlyIn(Dist.CLIENT)
public class RendezvousClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(RendezvousClient.class);

    // Hardcoded trusted federation servers (would be configurable in production)
    private static final List<FederationServer> DEFAULT_FEDERATION = List.of(
    // These would be real endpoints in production
    );

    private final OverlayManager overlayManager;

    // Current rendezvous state
    private volatile RendezvousEndpoint currentEndpoint;
    private volatile String currentSessionToken;
    private volatile boolean connected = false;

    // Cache of server hints
    private final ConcurrentHashMap<String, ServerHintId> hintCache = new ConcurrentHashMap<>();

    // Discovered peers in current rendezvous
    private final ConcurrentHashMap<String, DiscoveredPeer> discoveredPeers = new ConcurrentHashMap<>();

    public RendezvousClient(OverlayManager overlayManager) {
        this.overlayManager = overlayManager;
    }

    /**
     * Connect to rendezvous for a Minecraft server.
     * 
     * @param serverAddress The Minecraft server address (e.g.,
     *                      "play.example.com:25565")
     * @param serverHints   Optional hints from server's MOTD or handshake
     * @return CompletableFuture<RendezvousResult>
     */
    public CompletableFuture<RendezvousResult> connect(String serverAddress,
            Optional<ServerRendezvousHints> serverHints) {
        // Rate limit connections
        if (overlayManager.shouldRateLimit("rendezvous-connect", Duration.ofSeconds(10))) {
            return CompletableFuture.completedFuture(new RendezvousResult(
                    false, Optional.empty(), 0, Set.of(),
                    Optional.of("Rate limited, please wait")));
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                // Disconnect any existing rendezvous
                if (connected) {
                    disconnect();
                }

                // Derive server hint ID
                ServerHintId hintId = deriveServerHintId(serverAddress);
                hintCache.put(serverAddress, hintId);

                // Resolve rendezvous endpoint
                RendezvousEndpoint endpoint = resolveEndpoint(hintId, serverHints);
                if (endpoint == null) {
                    return new RendezvousResult(
                            false, Optional.empty(), 0, Set.of(),
                            Optional.of("No rendezvous endpoint available for this server"));
                }

                // Perform handshake
                RendezvousResult result = performHandshake(endpoint);

                if (result.success()) {
                    currentEndpoint = endpoint;
                    currentSessionToken = result.sessionToken().orElse(null);
                    connected = true;

                    LOGGER.info("Connected to rendezvous for {} ({} peers)",
                            serverAddress, result.peerCount());
                }

                return result;

            } catch (Exception e) {
                LOGGER.error("Failed to connect to rendezvous for {}", serverAddress, e);
                return new RendezvousResult(
                        false, Optional.empty(), 0, Set.of(),
                        Optional.of("Connection failed: " + e.getMessage()));
            }
        });
    }

    /**
     * Derive a server hint ID from the server address.
     * Includes epoch to prevent long-term tracking.
     */
    private ServerHintId deriveServerHintId(String serverAddress) {
        long epochBucket = OverlayManager.getCurrentEpochBucket();
        String normalized = normalizeServerAddress(serverAddress);

        // Hash: H("farmcraft-server-hint" || normalized_addr || epoch_bucket)
        String input = "farmcraft-server-hint|" + normalized + "|" + epochBucket;
        String hintId = OverlayManager.hashToBase32(input.getBytes(StandardCharsets.UTF_8), 20);

        return new ServerHintId(hintId, epochBucket, normalized);
    }

    /**
     * Normalize server address for consistent hashing.
     */
    private String normalizeServerAddress(String address) {
        // Lowercase, strip trailing dots, add default port if missing
        String normalized = address.toLowerCase(Locale.ROOT).trim();
        if (normalized.endsWith(".")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (!normalized.contains(":")) {
            normalized += ":25565";
        }
        return normalized;
    }

    /**
     * Resolve the rendezvous endpoint for a server.
     */
    private RendezvousEndpoint resolveEndpoint(ServerHintId hintId, Optional<ServerRendezvousHints> serverHints) {
        // Priority 1: Server-provided direct endpoint
        if (serverHints.isPresent() && serverHints.get().directEndpoint() != null) {
            String endpoint = serverHints.get().directEndpoint();
            if (isEndpointTrusted(endpoint)) {
                return new RendezvousEndpoint(
                        endpoint,
                        serverHints.get().serverPublicKey(),
                        Optional.empty(),
                        false);
            }
            LOGGER.warn("Server-provided endpoint {} is not trusted", endpoint);
        }

        // Priority 2: Server-provided delegation
        if (serverHints.isPresent() && serverHints.get().delegationEndpoint() != null) {
            String delegation = serverHints.get().delegationEndpoint();
            if (isEndpointTrusted(delegation)) {
                return new RendezvousEndpoint(
                        delegation,
                        Optional.empty(),
                        serverHints.flatMap(ServerRendezvousHints::delegationToken),
                        true);
            }
        }

        // Priority 3: Federation lookup by hint ID
        return lookupInFederation(hintId);
    }

    /**
     * Check if an endpoint is in our trusted list.
     */
    private boolean isEndpointTrusted(String endpoint) {
        // For now, only trust localhost and configured federation servers
        try {
            URI uri = new URI(endpoint);
            String host = uri.getHost();
            return host != null && (host.equals("localhost") ||
                    host.equals("127.0.0.1") ||
                    host.endsWith(".farmcraft.local") ||
                    DEFAULT_FEDERATION.stream().anyMatch(s -> s.endpoint().contains(host)));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Look up rendezvous in federation servers.
     */
    private RendezvousEndpoint lookupInFederation(ServerHintId hintId) {
        // Query federation servers to find one that handles this server
        for (FederationServer server : DEFAULT_FEDERATION) {
            if (!server.available())
                continue;

            // In production, this would be an HTTP call
            // For now, return null to indicate no federation match
        }

        return null;
    }

    /**
     * Perform rendezvous handshake.
     */
    private RendezvousResult performHandshake(RendezvousEndpoint endpoint) {
        // This would be a real WebSocket/HTTP handshake in production
        // For now, simulate a successful handshake

        LOGGER.debug("Performing handshake with {}", endpoint.endpointUrl());

        // Build handshake message
        RendezvousHandshake handshake = new RendezvousHandshake(
                overlayManager.getSessionIdentity(),
                endpoint.delegationToken(),
                Instant.now());

        // In production: send handshake, receive response
        // For now: return mock success
        return new RendezvousResult(
                true,
                Optional.of(UUID.randomUUID().toString()),
                0,
                Set.of("basic-discovery", "ephemeral-messaging"),
                Optional.empty());
    }

    /**
     * Announce presence to current rendezvous.
     */
    public CompletableFuture<Void> announcePresence(String capabilitiesHash) {
        if (!connected || currentEndpoint == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Not connected to rendezvous"));
        }

        return CompletableFuture.runAsync(() -> {
            // Rate limit announcements
            if (overlayManager.shouldRateLimit("rendezvous-announce", Duration.ofMinutes(1))) {
                LOGGER.debug("Presence announcement rate limited");
                return;
            }

            // Build announcement
            RendezvousAnnouncement announcement = new RendezvousAnnouncement(
                    overlayManager.getSessionIdentity().sessionPublicKey(),
                    capabilitiesHash,
                    Instant.now());

            // In production: send to rendezvous server
            LOGGER.debug("Announced presence to rendezvous");
        });
    }

    /**
     * Query peers in current rendezvous.
     */
    public CompletableFuture<List<DiscoveredPeer>> queryPeers(Optional<String> capabilitiesFilter) {
        if (!connected) {
            return CompletableFuture.completedFuture(List.of());
        }

        return CompletableFuture.supplyAsync(() -> {
            // Rate limit queries
            if (overlayManager.shouldRateLimit("rendezvous-query", Duration.ofSeconds(30))) {
                LOGGER.debug("Peer query rate limited");
                return new ArrayList<>(discoveredPeers.values());
            }

            // In production: query rendezvous server
            // For now: return cached peers
            List<DiscoveredPeer> peers = new ArrayList<>(discoveredPeers.values());

            if (capabilitiesFilter.isPresent()) {
                peers = peers.stream()
                        .filter(p -> p.capabilitiesHash().contains(capabilitiesFilter.get()))
                        .toList();
            }

            return peers;
        });
    }

    /**
     * Disconnect from current rendezvous.
     */
    public void disconnect() {
        if (connected) {
            LOGGER.info("Disconnecting from rendezvous");

            // In production: send disconnect message

            currentEndpoint = null;
            currentSessionToken = null;
            connected = false;
            discoveredPeers.clear();
        }
    }

    public boolean isConnected() {
        return connected;
    }

    public Optional<RendezvousEndpoint> getCurrentEndpoint() {
        return Optional.ofNullable(currentEndpoint);
    }

    public int getPeerCount() {
        return discoveredPeers.size();
    }

    // ═══════════════════════════════════════════════════════════════════
    // Internal Types
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Server-provided rendezvous hints from MOTD or handshake.
     */
    public record ServerRendezvousHints(
            String directEndpoint,
            String delegationEndpoint,
            Optional<java.security.PublicKey> serverPublicKey,
            Optional<String> delegationToken) {
    }

    /**
     * Rendezvous handshake message.
     */
    private record RendezvousHandshake(
            SessionIdentity sessionIdentity,
            Optional<String> delegationToken,
            Instant timestamp) {
    }

    /**
     * Presence announcement.
     */
    private record RendezvousAnnouncement(
            java.security.PublicKey sessionKey,
            String capabilitiesHash,
            Instant timestamp) {
    }

    /**
     * A discovered peer in the rendezvous.
     */
    public record DiscoveredPeer(
            String peerId,
            String capabilitiesHash,
            Instant lastSeen,
            boolean supportsDirectConnect) {
    }
}
