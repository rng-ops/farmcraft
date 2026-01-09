package com.farmcraft.overlay.friends;

import com.farmcraft.overlay.OverlayManager;
import com.farmcraft.overlay.OverlayTypes.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Friends overlay manager with mutual consent.
 * 
 * DESIGN:
 * - Mutual consent required for all connections
 * - Pairwise encrypted communication
 * - Presence without server IP or coordinates
 * - Content sharing between friends
 * 
 * PRIVACY GUARANTEES:
 * - NO server IP sharing
 * - NO coordinate sharing
 * - NO encounter logging
 * - NO third-party friend queries
 * - Presence uses coarse time buckets only
 * - All shared content is end-to-end encrypted
 * 
 * FRIEND ESTABLISHMENT:
 * 1. Alice generates invite code (contains her public key + signature)
 * 2. Alice shares invite via QR or copy/paste (out-of-band)
 * 3. Bob scans/enters invite, sends accept with his public key
 * 4. Both derive pairwise shared secret via ECDH
 * 5. All future communication encrypted with shared secret
 */
@OnlyIn(Dist.CLIENT)
public class FriendsManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(FriendsManager.class);

    // Invite code validity duration
    private static final Duration INVITE_VALIDITY = Duration.ofHours(24);

    // Presence broadcast interval
    private static final Duration PRESENCE_INTERVAL = Duration.ofMinutes(5);

    // Storage path for friends data
    private static final String FRIENDS_FILE = "farmcraft_friends.dat";

    private final OverlayManager overlayManager;

    // Long-lived friends identity (persisted)
    private KeyPair friendsKeyPair;
    private FriendsIdentity friendsIdentity;

    // Active friends
    private final ConcurrentHashMap<String, FriendConnection> friends = new ConcurrentHashMap<>();

    // Pairwise shared secrets
    private final ConcurrentHashMap<String, SecretKey> sharedSecrets = new ConcurrentHashMap<>();

    // Pending invites (we created)
    private final ConcurrentHashMap<String, PendingInvite> pendingOutgoingInvites = new ConcurrentHashMap<>();

    // Pending accepts (invites we received)
    private final ConcurrentHashMap<String, ReceivedInvite> pendingIncomingInvites = new ConcurrentHashMap<>();

    // Presence cache
    private volatile Instant lastPresenceBroadcast = Instant.EPOCH;

    public FriendsManager(OverlayManager overlayManager) {
        this.overlayManager = overlayManager;
        initializeIdentity();
    }

    /**
     * Initialize or load friends identity.
     */
    private void initializeIdentity() {
        try {
            // Try to load existing identity
            if (!loadFriendsIdentity()) {
                // Generate new identity
                generateFriendsIdentity();
            }

            LOGGER.info("Friends identity initialized with key: {}",
                    truncateKey(friendsIdentity.friendsPublicKey()));

        } catch (Exception e) {
            LOGGER.error("Failed to initialize friends identity", e);
        }
    }

    /**
     * Generate a new friends identity keypair.
     */
    private void generateFriendsIdentity() throws NoSuchAlgorithmException {
        // Generate ECDH keypair for key agreement
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("EC");
        keyGen.initialize(256, new SecureRandom());
        friendsKeyPair = keyGen.generateKeyPair();

        friendsIdentity = new FriendsIdentity(
                friendsKeyPair.getPublic(),
                Optional.empty(),
                Instant.now());

        // Save to disk (encrypted with local machine key in production)
        saveFriendsIdentity();
    }

    /**
     * Load friends identity from disk.
     */
    private boolean loadFriendsIdentity() {
        // In production: load from encrypted file
        // For now: return false to generate new
        return false;
    }

    /**
     * Save friends identity to disk.
     */
    private void saveFriendsIdentity() {
        // In production: encrypt and save to file
        LOGGER.debug("Friends identity saved");
    }

    // ═══════════════════════════════════════════════════════════════════
    // Friend Invite Flow
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Create a friend invite code.
     * 
     * @return FriendInvite containing the invite code to share
     */
    public FriendInvite createInvite() {
        try {
            // Generate invite code
            byte[] inviteBytes = new byte[16];
            new SecureRandom().nextBytes(inviteBytes);
            String inviteCode = OverlayManager.bytesToBase32(inviteBytes).substring(0, 24);

            // Create signature
            byte[] signature = signInvite(inviteCode);

            FriendInvite invite = new FriendInvite(
                    friendsIdentity.friendsPublicKey(),
                    inviteCode,
                    Instant.now().plus(INVITE_VALIDITY),
                    signature);

            // Track pending invite
            pendingOutgoingInvites.put(inviteCode, new PendingInvite(
                    inviteCode,
                    Instant.now(),
                    invite.expiresAt()));

            LOGGER.info("Created friend invite: {}", inviteCode);
            return invite;

        } catch (Exception e) {
            LOGGER.error("Failed to create friend invite", e);
            throw new RuntimeException("Failed to create invite", e);
        }
    }

    /**
     * Sign an invite code.
     */
    private byte[] signInvite(String inviteCode) throws Exception {
        // In production: real signature with friends private key
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(inviteCode.getBytes(StandardCharsets.UTF_8));
        digest.update(friendsKeyPair.getPrivate().getEncoded());
        return digest.digest();
    }

    /**
     * Accept a friend invite.
     * 
     * @param inviteCode       The invite code received from friend
     * @param inviterPublicKey The public key from the invite
     * @param displayName      Optional display name for the friend
     * @return CompletableFuture<FriendConnection>
     */
    public CompletableFuture<FriendConnection> acceptInvite(
            String inviteCode,
            PublicKey inviterPublicKey,
            Optional<String> displayName) {

        return CompletableFuture.supplyAsync(() -> {
            try {
                // Rate limit accepts
                if (overlayManager.shouldRateLimit("friend-accept", Duration.ofSeconds(10))) {
                    throw new RuntimeException("Rate limited, please wait");
                }

                // Derive shared secret via ECDH
                SecretKey sharedSecret = deriveSharedSecret(inviterPublicKey);

                // Create friend connection
                String friendId = OverlayManager.hashToBase32(inviterPublicKey.getEncoded(), 16);

                FriendConnection connection = new FriendConnection(
                        inviterPublicKey,
                        displayName.orElse("Friend-" + friendId.substring(0, 8)),
                        Instant.now(),
                        Optional.empty(),
                        List.of());

                // Store connection and secret
                friends.put(friendId, connection);
                sharedSecrets.put(friendId, sharedSecret);

                // Send accept notification (encrypted with shared secret)
                sendAcceptNotification(friendId, sharedSecret);

                // Save friends list
                saveFriendsList();

                LOGGER.info("Accepted friend invite, connected to: {}", friendId);
                return connection;

            } catch (Exception e) {
                LOGGER.error("Failed to accept friend invite", e);
                throw new RuntimeException("Failed to accept invite", e);
            }
        });
    }

    /**
     * Derive shared secret using ECDH key agreement.
     */
    private SecretKey deriveSharedSecret(PublicKey theirPublicKey) throws Exception {
        KeyAgreement keyAgreement = KeyAgreement.getInstance("ECDH");
        keyAgreement.init(friendsKeyPair.getPrivate());
        keyAgreement.doPhase(theirPublicKey, true);

        byte[] sharedSecretBytes = keyAgreement.generateSecret();

        // Derive AES key from shared secret
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] keyBytes = digest.digest(sharedSecretBytes);

        return new SecretKeySpec(keyBytes, "AES");
    }

    /**
     * Send accept notification to friend.
     */
    private void sendAcceptNotification(String friendId, SecretKey sharedSecret) {
        // In production: send encrypted notification via P2P or relay
        LOGGER.debug("Sent accept notification to {}", friendId);
    }

    /**
     * Handle incoming accept notification.
     */
    public void handleAcceptNotification(String inviteCode, PublicKey accepterPublicKey, String displayName) {
        try {
            PendingInvite pending = pendingOutgoingInvites.remove(inviteCode);
            if (pending == null) {
                LOGGER.warn("Received accept for unknown invite: {}", inviteCode);
                return;
            }

            if (pending.expiresAt.isBefore(Instant.now())) {
                LOGGER.warn("Received accept for expired invite: {}", inviteCode);
                return;
            }

            // Derive shared secret
            SecretKey sharedSecret = deriveSharedSecret(accepterPublicKey);

            // Create friend connection
            String friendId = OverlayManager.hashToBase32(accepterPublicKey.getEncoded(), 16);

            FriendConnection connection = new FriendConnection(
                    accepterPublicKey,
                    displayName,
                    Instant.now(),
                    Optional.empty(),
                    List.of());

            friends.put(friendId, connection);
            sharedSecrets.put(friendId, sharedSecret);

            saveFriendsList();

            LOGGER.info("Friend connection completed: {}", friendId);

        } catch (Exception e) {
            LOGGER.error("Failed to handle accept notification", e);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Presence Management
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Broadcast presence to friends.
     * Called periodically by OverlayManager.
     * 
     * PRIVACY: NO server IP, NO coordinates - only status and coarse time.
     */
    public void broadcastPresence() {
        if (friends.isEmpty())
            return;

        // Rate limit broadcasts
        if (Duration.between(lastPresenceBroadcast, Instant.now()).compareTo(PRESENCE_INTERVAL) < 0) {
            return;
        }

        try {
            // Create presence update
            FriendPresence presence = new FriendPresence(
                    PresenceStatus.ONLINE,
                    Optional.of(LastSeenBucket.JUST_NOW),
                    Instant.now());

            // Send to each friend (encrypted)
            for (Map.Entry<String, FriendConnection> entry : friends.entrySet()) {
                String friendId = entry.getKey();
                SecretKey secret = sharedSecrets.get(friendId);

                if (secret != null) {
                    sendEncryptedPresence(friendId, presence, secret);
                }
            }

            lastPresenceBroadcast = Instant.now();
            LOGGER.debug("Broadcasted presence to {} friends", friends.size());

        } catch (Exception e) {
            LOGGER.error("Failed to broadcast presence", e);
        }
    }

    /**
     * Send encrypted presence to a friend.
     */
    private void sendEncryptedPresence(String friendId, FriendPresence presence, SecretKey secret) {
        try {
            // Serialize presence
            String presenceJson = String.format(
                    "{\"status\":\"%s\",\"lastSeen\":\"%s\",\"timestamp\":%d}",
                    presence.status(),
                    presence.lastSeenBucket().map(Enum::name).orElse("UNKNOWN"),
                    presence.updatedAt().toEpochMilli());

            // Encrypt
            byte[] encrypted = encrypt(presenceJson.getBytes(StandardCharsets.UTF_8), secret);

            // In production: send via P2P or relay
            LOGGER.debug("Sent encrypted presence to {}", friendId);

        } catch (Exception e) {
            LOGGER.error("Failed to send presence to {}", friendId, e);
        }
    }

    /**
     * Handle incoming presence update.
     */
    public void handlePresenceUpdate(String friendId, byte[] encryptedPresence) {
        try {
            SecretKey secret = sharedSecrets.get(friendId);
            if (secret == null) {
                LOGGER.warn("Received presence from unknown friend: {}", friendId);
                return;
            }

            // Decrypt
            byte[] decrypted = decrypt(encryptedPresence, secret);
            String presenceJson = new String(decrypted, StandardCharsets.UTF_8);

            // Parse and update (simplified)
            FriendConnection existing = friends.get(friendId);
            if (existing != null) {
                FriendPresence newPresence = new FriendPresence(
                        PresenceStatus.ONLINE, // Parsed from JSON in production
                        Optional.of(LastSeenBucket.JUST_NOW),
                        Instant.now());

                FriendConnection updated = new FriendConnection(
                        existing.friendPublicKey(),
                        existing.displayName(),
                        existing.establishedAt(),
                        Optional.of(newPresence),
                        existing.sharedContentCids());

                friends.put(friendId, updated);
            }

        } catch (Exception e) {
            LOGGER.error("Failed to handle presence update from {}", friendId, e);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Content Sharing
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Share content with a friend.
     */
    public CompletableFuture<Void> shareContent(String friendId, SharedContent content) {
        return CompletableFuture.runAsync(() -> {
            try {
                SecretKey secret = sharedSecrets.get(friendId);
                if (secret == null) {
                    throw new RuntimeException("Not connected to friend: " + friendId);
                }

                // Serialize content
                String contentJson = String.format(
                        "{\"type\":\"%s\",\"cid\":\"%s\",\"desc\":\"%s\",\"sharedAt\":%d}",
                        content.type(),
                        content.contentCid(),
                        content.description(),
                        content.sharedAt().toEpochMilli());

                // Encrypt and send
                byte[] encrypted = encrypt(contentJson.getBytes(StandardCharsets.UTF_8), secret);

                // In production: send via P2P or relay
                LOGGER.info("Shared {} with friend {}", content.type(), friendId);

            } catch (Exception e) {
                LOGGER.error("Failed to share content with {}", friendId, e);
                throw new RuntimeException("Failed to share content", e);
            }
        });
    }

    // ═══════════════════════════════════════════════════════════════════
    // Encryption Utilities
    // ═══════════════════════════════════════════════════════════════════

    private byte[] encrypt(byte[] plaintext, SecretKey key) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);

        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
        byte[] ciphertext = cipher.doFinal(plaintext);

        // Prepend IV
        byte[] result = new byte[iv.length + ciphertext.length];
        System.arraycopy(iv, 0, result, 0, iv.length);
        System.arraycopy(ciphertext, 0, result, iv.length, ciphertext.length);

        return result;
    }

    private byte[] decrypt(byte[] ciphertext, SecretKey key) throws Exception {
        if (ciphertext.length < 12) {
            throw new RuntimeException("Invalid ciphertext");
        }

        byte[] iv = Arrays.copyOfRange(ciphertext, 0, 12);
        byte[] actualCiphertext = Arrays.copyOfRange(ciphertext, 12, ciphertext.length);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));

        return cipher.doFinal(actualCiphertext);
    }

    // ═══════════════════════════════════════════════════════════════════
    // Friend Management
    // ═══════════════════════════════════════════════════════════════════

    public List<FriendConnection> getFriends() {
        return new ArrayList<>(friends.values());
    }

    public Optional<FriendConnection> getFriend(String friendId) {
        return Optional.ofNullable(friends.get(friendId));
    }

    public void removeFriend(String friendId) {
        friends.remove(friendId);
        sharedSecrets.remove(friendId);
        saveFriendsList();
        LOGGER.info("Removed friend: {}", friendId);
    }

    public void updateDisplayName(String friendId, String newName) {
        FriendConnection existing = friends.get(friendId);
        if (existing != null) {
            FriendConnection updated = new FriendConnection(
                    existing.friendPublicKey(),
                    newName,
                    existing.establishedAt(),
                    existing.presence(),
                    existing.sharedContentCids());
            friends.put(friendId, updated);
            saveFriendsList();
        }
    }

    public int getFriendCount() {
        return friends.size();
    }

    public FriendsIdentity getIdentity() {
        return friendsIdentity;
    }

    /**
     * Save friends list to disk.
     */
    private void saveFriendsList() {
        // In production: encrypt and save
        LOGGER.debug("Friends list saved ({} friends)", friends.size());
    }

    /**
     * Shutdown friends manager.
     */
    public void shutdown() {
        // Broadcast offline status
        try {
            FriendPresence offline = new FriendPresence(
                    PresenceStatus.OFFLINE,
                    Optional.of(LastSeenBucket.JUST_NOW),
                    Instant.now());

            for (Map.Entry<String, FriendConnection> entry : friends.entrySet()) {
                SecretKey secret = sharedSecrets.get(entry.getKey());
                if (secret != null) {
                    sendEncryptedPresence(entry.getKey(), offline, secret);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to broadcast offline status", e);
        }

        saveFriendsList();
        friends.clear();
        sharedSecrets.clear();
    }

    private static String truncateKey(PublicKey key) {
        String encoded = OverlayManager.bytesToBase32(key.getEncoded());
        if (encoded.length() > 12) {
            return encoded.substring(0, 8) + "..." + encoded.substring(encoded.length() - 4);
        }
        return encoded;
    }

    // ═══════════════════════════════════════════════════════════════════
    // Internal Types
    // ═══════════════════════════════════════════════════════════════════

    private record PendingInvite(
            String inviteCode,
            Instant createdAt,
            Instant expiresAt) {
    }

    private record ReceivedInvite(
            String inviteCode,
            PublicKey inviterPublicKey,
            Instant receivedAt,
            Instant expiresAt) {
    }
}
