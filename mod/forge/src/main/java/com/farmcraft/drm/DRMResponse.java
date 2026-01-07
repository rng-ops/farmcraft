package com.farmcraft.drm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Response to a DRM challenge
 */
public class DRMResponse {
    public String challengeId;
    public String clientVersion;
    public List<StateChainLink> stateChain;
    public String workResult;
    public int nonce;
    public String clientSignature;
    
    public DRMResponse() {
        this.stateChain = new ArrayList<>();
    }
    
    public Map<String, Object> toMessage() {
        Map<String, Object> message = new HashMap<>();
        message.put("type", "drm_response");
        
        Map<String, Object> payload = new HashMap<>();
        payload.put("challengeId", challengeId);
        payload.put("clientVersion", clientVersion);
        
        List<Map<String, Object>> chainData = new ArrayList<>();
        for (StateChainLink link : stateChain) {
            chainData.add(link.toMap());
        }
        payload.put("stateChain", chainData);
        
        payload.put("workResult", workResult);
        payload.put("nonce", nonce);
        payload.put("clientSignature", clientSignature);
        
        message.put("payload", payload);
        return message;
    }
}
