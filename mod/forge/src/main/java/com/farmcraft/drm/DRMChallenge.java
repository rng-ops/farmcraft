package com.farmcraft.drm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a DRM challenge from the server
 */
public class DRMChallenge {
    public String challengeId;
    public List<String> requiredShaders;
    public Map<String, String> inputSeeds;
    public String previousChainHash;
    public int difficulty;
    public long expiresAt;
    public String workType;
    
    public DRMChallenge() {
        this.requiredShaders = new ArrayList<>();
        this.inputSeeds = new HashMap<>();
    }
    
    public boolean isExpired() {
        return System.currentTimeMillis() > expiresAt;
    }
    
    @SuppressWarnings("unchecked")
    public static DRMChallenge fromMap(Map<String, Object> map) {
        DRMChallenge challenge = new DRMChallenge();
        
        challenge.challengeId = (String) map.get("challengeId");
        challenge.requiredShaders = (List<String>) map.get("requiredShaders");
        challenge.previousChainHash = (String) map.get("previousChainHash");
        challenge.difficulty = ((Number) map.get("difficulty")).intValue();
        challenge.expiresAt = ((Number) map.get("expiresAt")).longValue();
        challenge.workType = (String) map.get("workType");
        
        // Input seeds might be a Map or need conversion
        Object seeds = map.get("inputSeeds");
        if (seeds instanceof Map) {
            challenge.inputSeeds = (Map<String, String>) seeds;
        }
        
        return challenge;
    }
    
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("challengeId", challengeId);
        map.put("requiredShaders", requiredShaders);
        map.put("inputSeeds", inputSeeds);
        map.put("previousChainHash", previousChainHash);
        map.put("difficulty", difficulty);
        map.put("expiresAt", expiresAt);
        map.put("workType", workType);
        return map;
    }
}
