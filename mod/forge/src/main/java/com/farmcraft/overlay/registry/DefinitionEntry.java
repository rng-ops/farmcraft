package com.farmcraft.overlay.registry;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.google.gson.JsonObject;

/**
 * A definition entry in the local registry.
 * 
 * Definitions are content-addressed using CIDs (Content Identifiers).
 * Immutable once created - updates create new entries with new CIDs.
 */
public record DefinitionEntry(
        String cid,
        DefinitionType type,
        String name,
        String description,
        String author,
        int version,
        DefinitionStatus status,
        Instant createdAt,
        Instant updatedAt,
        JsonObject content,
        List<String> signatures) {

    private static final String CID_PREFIX = "fc_";

    /**
     * Generate a CID for content.
     */
    public static String generateCid(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return CID_PREFIX + bytesToHex(hash).substring(0, 40);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    /**
     * Verify that the content matches the CID.
     */
    public boolean verifyCid() {
        String expectedCid = generateCid(content.toString());
        return cid.equals(expectedCid);
    }

    /**
     * Check if this entry is signed.
     */
    public boolean isSigned() {
        return signatures != null && !signatures.isEmpty();
    }

    /**
     * Get the number of signatures.
     */
    public int getSignatureCount() {
        return signatures != null ? signatures.size() : 0;
    }

    /**
     * Check if this entry has the required number of signatures.
     */
    public boolean hasMinimumSignatures(int required) {
        return getSignatureCount() >= required;
    }

    /**
     * Get a summary for display.
     */
    public String getSummary() {
        return String.format("%s %s [%s] by %s (v%d)",
                type.getIcon(), name, status.getDisplayName(), author, version);
    }

    /**
     * Get a short CID for display.
     */
    public String getShortCid() {
        return cid.length() > 12 ? cid.substring(0, 12) + "..." : cid;
    }

    /**
     * Convert bytes to hex string.
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    // ========== Builder ==========

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String cid;
        private DefinitionType type;
        private String name;
        private String description;
        private String author;
        private int version = 1;
        private DefinitionStatus status = DefinitionStatus.DRAFT;
        private Instant createdAt;
        private Instant updatedAt;
        private JsonObject content;
        private List<String> signatures = List.of();

        public Builder cid(String cid) {
            this.cid = cid;
            return this;
        }

        public Builder type(DefinitionType type) {
            this.type = type;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder author(String author) {
            this.author = author;
            return this;
        }

        public Builder version(int version) {
            this.version = version;
            return this;
        }

        public Builder status(DefinitionStatus status) {
            this.status = status;
            return this;
        }

        public Builder content(JsonObject content) {
            this.content = content;
            return this;
        }

        public Builder signatures(List<String> signatures) {
            this.signatures = signatures;
            return this;
        }

        public DefinitionEntry build() {
            Instant now = Instant.now();
            if (createdAt == null)
                createdAt = now;
            if (updatedAt == null)
                updatedAt = now;

            // Generate CID from content if not provided
            if (cid == null && content != null) {
                cid = generateCid(content.toString());
            }

            return new DefinitionEntry(
                    cid, type, name, description, author, version, status,
                    createdAt, updatedAt, content, signatures);
        }
    }

    // ========== Factory Methods ==========

    /**
     * Create a new draft item definition.
     */
    public static DefinitionEntry newItemDraft(String name, String description, String author, JsonObject content) {
        return builder()
                .type(DefinitionType.ITEM)
                .name(name)
                .description(description)
                .author(author)
                .status(DefinitionStatus.DRAFT)
                .content(content)
                .build();
    }

    /**
     * Create a new draft spell definition.
     */
    public static DefinitionEntry newSpellDraft(String name, String description, String author, JsonObject content) {
        return builder()
                .type(DefinitionType.SPELL)
                .name(name)
                .description(description)
                .author(author)
                .status(DefinitionStatus.DRAFT)
                .content(content)
                .build();
    }

    /**
     * Create an updated version of this entry.
     */
    public DefinitionEntry withUpdatedContent(JsonObject newContent) {
        return new DefinitionEntry(
                generateCid(newContent.toString()),
                type, name, description, author,
                version + 1,
                DefinitionStatus.DRAFT,
                createdAt,
                Instant.now(),
                newContent,
                List.of() // Clear signatures on update
        );
    }

    /**
     * Create a version with new status.
     */
    public DefinitionEntry withStatus(DefinitionStatus newStatus) {
        return new DefinitionEntry(
                cid, type, name, description, author, version, newStatus,
                createdAt, Instant.now(), content, signatures);
    }

    /**
     * Create a version with an additional signature.
     */
    public DefinitionEntry withSignature(String signature) {
        List<String> newSignatures = new java.util.ArrayList<>(signatures);
        newSignatures.add(signature);
        return new DefinitionEntry(
                cid, type, name, description, author, version, status,
                createdAt, Instant.now(), content, List.copyOf(newSignatures));
    }
}
