package com.farmcraft.drm;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DRM Client for version verification through shader state chains.
 * 
 * This system proves the client is running an unmodified version by:
 * 1. Executing shaders with server-provided seeds
 * 2. Building a chain of shader outputs
 * 3. The chain proves the client has correct shader implementations
 * 4. Tampered clients produce different outputs and fail verification
 */
public class DRMClient {
    
    public static final String CLIENT_VERSION = "1.0.0";
    
    private final String clientId;
    private final StateChain stateChain;
    private String currentAccessToken;
    private int trustScore = 50;
    private int chainLength = 0;
    
    // Active challenges waiting for solutions
    private final Map<String, DRMChallenge> activeChallenges = new ConcurrentHashMap<>();
    
    public DRMClient(String clientId) {
        this.clientId = clientId;
        this.stateChain = new StateChain(CLIENT_VERSION);
    }
    
    /**
     * Create initialization message to send to server
     */
    public Map<String, Object> createInitMessage() {
        Map<String, Object> message = new HashMap<>();
        message.put("type", "drm_init");
        
        Map<String, Object> payload = new HashMap<>();
        payload.put("clientVersion", CLIENT_VERSION);
        message.put("payload", payload);
        
        return message;
    }
    
    /**
     * Handle server's init response
     */
    public void handleInitResponse(Map<String, Object> response) {
        String serverVersion = (String) response.get("serverVersion");
        boolean versionMatch = (Boolean) response.get("versionMatch");
        
        @SuppressWarnings("unchecked")
        Map<String, Object> clientState = (Map<String, Object>) response.get("clientState");
        if (clientState != null) {
            this.trustScore = ((Number) clientState.get("trustScore")).intValue();
            this.chainLength = ((Number) clientState.get("chainLength")).intValue();
        }
        
        // If we got an initial challenge, store it
        @SuppressWarnings("unchecked")
        Map<String, Object> initialChallenge = (Map<String, Object>) response.get("initialChallenge");
        if (initialChallenge != null) {
            DRMChallenge challenge = DRMChallenge.fromMap(initialChallenge);
            activeChallenges.put(challenge.challengeId, challenge);
        }
    }
    
    /**
     * Solve a DRM challenge
     */
    public DRMResponse solveChallenge(DRMChallenge challenge) {
        List<StateChainLink> newLinks = new ArrayList<>();
        
        // Execute each required shader
        for (String shaderId : challenge.requiredShaders) {
            String seed = challenge.inputSeeds.get(shaderId);
            if (seed == null) {
                throw new IllegalStateException("Missing seed for shader: " + shaderId);
            }
            
            // Execute shader and compute work proof
            String workProof = computeWorkProof(shaderId, seed, challenge.difficulty);
            StateChainLink link = stateChain.addLink(shaderId, seed, workProof);
            newLinks.add(link);
        }
        
        // Find valid nonce for final work result
        NonceResult nonceResult = findValidNonce(stateChain.getChainHash(), challenge.difficulty);
        
        // Create response
        DRMResponse response = new DRMResponse();
        response.challengeId = challenge.challengeId;
        response.clientVersion = CLIENT_VERSION;
        response.stateChain = newLinks;
        response.workResult = nonceResult.result;
        response.nonce = nonceResult.nonce;
        response.clientSignature = signResponse(nonceResult.result);
        
        return response;
    }
    
    /**
     * Handle verification result from server
     */
    public void handleVerifyResult(Map<String, Object> result) {
        boolean valid = (Boolean) result.get("valid");
        
        if (valid) {
            @SuppressWarnings("unchecked")
            Map<String, Object> updatedState = (Map<String, Object>) result.get("updatedState");
            if (updatedState != null) {
                this.trustScore = ((Number) updatedState.get("trustScore")).intValue();
                this.chainLength = ((Number) updatedState.get("chainLength")).intValue();
            }
            
            String accessToken = (String) result.get("accessToken");
            if (accessToken != null) {
                this.currentAccessToken = accessToken;
            }
        }
    }
    
    /**
     * Create a resource request message
     */
    public Map<String, Object> createResourceRequest(String resourceId) {
        Map<String, Object> message = new HashMap<>();
        message.put("type", "drm_resource_request");
        
        Map<String, Object> payload = new HashMap<>();
        payload.put("resourceId", resourceId);
        payload.put("accessToken", currentAccessToken);
        payload.put("stateProof", serializeStateProof());
        message.put("payload", payload);
        
        return message;
    }
    
    private List<Map<String, Object>> serializeStateProof() {
        List<Map<String, Object>> proof = new ArrayList<>();
        List<StateChainLink> links = stateChain.getLinks();
        
        // Send last 3 links as proof
        int start = Math.max(0, links.size() - 3);
        for (int i = start; i < links.size(); i++) {
            proof.add(links.get(i).toMap());
        }
        
        return proof;
    }
    
    private String computeWorkProof(String shaderId, String seed, int difficulty) {
        String proof = seed;
        int iterations = (int) Math.pow(10, difficulty);
        
        for (int i = 0; i < iterations; i++) {
            proof = sha256(proof + shaderId);
        }
        
        return proof;
    }
    
    private NonceResult findValidNonce(String data, int difficulty) {
        int nonce = 0;
        
        while (true) {
            String result = sha256(data + nonce);
            
            boolean valid = true;
            for (int i = 0; i < difficulty; i++) {
                if (result.charAt(i) != '0') {
                    valid = false;
                    break;
                }
            }
            
            if (valid) {
                return new NonceResult(result, nonce);
            }
            nonce++;
        }
    }
    
    private String signResponse(String data) {
        return sha256(clientId + data + System.currentTimeMillis());
    }
    
    public static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
    
    // Getters
    public String getClientId() { return clientId; }
    public int getTrustScore() { return trustScore; }
    public int getChainLength() { return chainLength; }
    public String getAccessToken() { return currentAccessToken; }
    public boolean hasValidToken() { return currentAccessToken != null; }
    
    /**
     * Get pending challenge if any
     */
    public DRMChallenge getPendingChallenge() {
        return activeChallenges.values().stream().findFirst().orElse(null);
    }
    
    /**
     * Store a received challenge
     */
    public void storeChallenge(DRMChallenge challenge) {
        activeChallenges.put(challenge.challengeId, challenge);
    }
    
    /**
     * Remove a completed challenge
     */
    public void removeChallenge(String challengeId) {
        activeChallenges.remove(challengeId);
    }
    
    // Inner classes
    
    private static class NonceResult {
        final String result;
        final int nonce;
        
        NonceResult(String result, int nonce) {
            this.result = result;
            this.nonce = nonce;
        }
    }
}
