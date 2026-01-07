package com.farmcraft.drm;

import java.util.HashMap;
import java.util.Map;

/**
 * Shader fingerprint proving shader execution
 */
public class ShaderFingerprint {
    public String shaderId;
    public String version;
    public String inputSeed;
    public String outputHash;
    public long timestamp;
    
    public ShaderFingerprint(String shaderId, String version, String inputSeed, String outputHash) {
        this.shaderId = shaderId;
        this.version = version;
        this.inputSeed = inputSeed;
        this.outputHash = outputHash;
        this.timestamp = System.currentTimeMillis();
    }
    
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("shaderId", shaderId);
        map.put("version", version);
        map.put("inputSeed", inputSeed);
        map.put("outputHash", outputHash);
        map.put("timestamp", timestamp);
        return map;
    }
}
