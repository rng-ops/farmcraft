package com.farmcraft.watermark;

import java.util.List;

/**
 * Data structure for watermark content
 */
public class WatermarkData {
    // Core identifiers
    public String clientId;
    public String sessionId;
    public long timestamp;
    
    // Game state
    public GameStateSnapshot gameState;
    
    // Hardware fingerprint
    public GPUFingerprint gpuFingerprint;
    
    // Chain reference
    public String eventChainHash;
    public int lastEventSequence;
    
    // Verification
    public String signature;
}

class GameStateSnapshot {
    public String worldSeed;
    public double[] playerPosition;
    public String playerInventoryHash;
    public String nearbyBlocksHash;
    public long timeOfDay;
    public String weather;
    public List<String> activeFertilizers;
    public List<String> recentRecipesUsed;
}

class GPUFingerprint {
    public String vendor;
    public String renderer;
    public String glVersion;
    public int maxTextureSize;
    public FloatPrecisionInfo floatPrecision;
    public String driverHash;
}

class FloatPrecisionInfo {
    public int vertexPrecision;
    public int fragmentPrecision;
    public String[] detectedQuirks;
}
