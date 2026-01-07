package com.farmcraft.drm;

import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Maintains a chain of shader execution proofs
 */
public class StateChain {
    
    private static final String GENESIS_HASH = "0000000000000000000000000000000000000000000000000000000000000000";
    private static final Gson GSON = new Gson();
    
    private final List<StateChainLink> links;
    private final String clientVersion;
    private String currentHash;
    
    public StateChain(String clientVersion) {
        this.clientVersion = clientVersion;
        this.links = new ArrayList<>();
        this.currentHash = GENESIS_HASH;
    }
    
    /**
     * Add a new link to the chain
     */
    public StateChainLink addLink(String shaderId, String inputSeed, String workProof) {
        // Execute shader to get output
        String shaderOutput = ShaderExecutor.execute(shaderId, inputSeed);
        
        // Create fingerprint
        ShaderFingerprint fingerprint = new ShaderFingerprint(
            shaderId,
            clientVersion,
            inputSeed,
            shaderOutput
        );
        
        // Create link data for hashing
        Map<String, Object> linkData = new HashMap<>();
        linkData.put("index", links.size());
        linkData.put("previousHash", currentHash);
        linkData.put("fingerprint", fingerprint.toMap());
        linkData.put("workProof", workProof);
        
        String linkHash = DRMClient.sha256(GSON.toJson(linkData));
        
        // Create and add link
        StateChainLink link = new StateChainLink(
            links.size(),
            currentHash,
            fingerprint,
            workProof,
            linkHash
        );
        
        links.add(link);
        currentHash = linkHash;
        
        return link;
    }
    
    /**
     * Get current chain hash
     */
    public String getChainHash() {
        return currentHash;
    }
    
    /**
     * Get all links
     */
    public List<StateChainLink> getLinks() {
        return new ArrayList<>(links);
    }
    
    /**
     * Get chain length
     */
    public int getLength() {
        return links.size();
    }
    
    /**
     * Verify chain integrity
     */
    public boolean verifyIntegrity() {
        String expectedPrev = GENESIS_HASH;
        
        for (StateChainLink link : links) {
            // Check previous hash
            if (!link.previousHash.equals(expectedPrev)) {
                return false;
            }
            
            // Recompute link hash
            Map<String, Object> linkData = new HashMap<>();
            linkData.put("index", link.index);
            linkData.put("previousHash", link.previousHash);
            linkData.put("fingerprint", link.shaderFingerprint.toMap());
            linkData.put("workProof", link.workProof);
            
            String computed = DRMClient.sha256(GSON.toJson(linkData));
            if (!computed.equals(link.linkHash)) {
                return false;
            }
            
            expectedPrev = link.linkHash;
        }
        
        return true;
    }
}
