package com.farmcraft.drm;

import java.util.HashMap;
import java.util.Map;

/**
 * A single link in the state chain
 */
public class StateChainLink {
    public int index;
    public String previousHash;
    public ShaderFingerprint shaderFingerprint;
    public String workProof;
    public String linkHash;
    
    public StateChainLink(int index, String previousHash, ShaderFingerprint fingerprint, 
                          String workProof, String linkHash) {
        this.index = index;
        this.previousHash = previousHash;
        this.shaderFingerprint = fingerprint;
        this.workProof = workProof;
        this.linkHash = linkHash;
    }
    
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("index", index);
        map.put("previousHash", previousHash);
        map.put("shaderFingerprint", shaderFingerprint.toMap());
        map.put("workProof", workProof);
        map.put("linkHash", linkHash);
        return map;
    }
}
