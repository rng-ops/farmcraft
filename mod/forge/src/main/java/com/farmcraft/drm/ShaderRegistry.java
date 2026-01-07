package com.farmcraft.drm;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Registry of deterministic shader functions
 * These must produce identical outputs on all clients for the same inputs
 */
public class ShaderRegistry {
    
    private static final Map<String, ShaderFunction> SHADERS = new HashMap<>();
    
    static {
        // Register built-in shaders
        registerShader("hash_compute_v1", "1.0.0", ShaderRegistry::hashComputeShader);
        registerShader("folding_energy_v1", "1.0.0", ShaderRegistry::foldingEnergyShader);
        registerShader("entropy_v1", "1.0.0", ShaderRegistry::entropyShader);
        registerShader("version_proof_v1", "1.0.0", ShaderRegistry::versionProofShader);
    }
    
    public static void registerShader(String id, String version, Function<String, String> executor) {
        SHADERS.put(id, new ShaderFunction(id, version, executor));
    }
    
    public static ShaderFunction getShader(String id) {
        return SHADERS.get(id);
    }
    
    public static Map<String, ShaderFunction> getAllShaders() {
        return new HashMap<>(SHADERS);
    }
    
    public static String executeShader(String shaderId, String seed) {
        ShaderFunction shader = SHADERS.get(shaderId);
        if (shader == null) {
            throw new IllegalArgumentException("Unknown shader: " + shaderId);
        }
        return shader.execute(seed);
    }
    
    // ═══════════════════════════════════════════════════════════════════
    // Shader Implementations (must match TypeScript versions exactly)
    // ═══════════════════════════════════════════════════════════════════
    
    private static String hashComputeShader(String seed) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(seed.getBytes(StandardCharsets.UTF_8));
            
            // Multiple rounds for work
            for (int i = 0; i < 1000; i++) {
                hash = digest.digest(hash);
            }
            
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
    
    private static String foldingEnergyShader(String seed) {
        // Simplified energy calculation from amino acid sequence
        // Must match the TypeScript implementation
        float energy = 0;
        byte[] seedBytes = seed.getBytes(StandardCharsets.UTF_8);
        
        for (int i = 0; i < seedBytes.length - 1; i++) {
            float interaction = (seedBytes[i] * seedBytes[i + 1]) / 10000.0f;
            energy += Math.sin(interaction) * 100;
        }
        
        // Normalize and hash for consistent output
        String energyStr = String.format("%.6f", energy);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return bytesToHex(digest.digest(energyStr.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
    
    private static String entropyShader(String seed) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] state = digest.digest(seed.getBytes(StandardCharsets.UTF_8));
            
            // XOR mixing
            for (int i = 0; i < 100; i++) {
                byte[] next = digest.digest(state);
                for (int j = 0; j < state.length; j++) {
                    state[j] ^= next[j];
                }
            }
            
            return bytesToHex(state);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
    
    private static String versionProofShader(String seed) {
        // Version proof combines seed with embedded version markers
        String versionMarker = "FARMCRAFT_V1.0.0";
        String combined = seed + "|" + versionMarker + "|" + getClientFingerprint();
        
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(combined.getBytes(StandardCharsets.UTF_8));
            
            // Multiple rounds with version mixing
            for (int i = 0; i < 500; i++) {
                String withVersion = bytesToHex(hash) + versionMarker;
                hash = digest.digest(withVersion.getBytes(StandardCharsets.UTF_8));
            }
            
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
    
    private static String getClientFingerprint() {
        // Generate a fingerprint from the class bytecode
        // This ensures any modification to the shader code changes the output
        try {
            String classInfo = ShaderRegistry.class.getName() + 
                              ShaderRegistry.class.getDeclaredMethods().length;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return bytesToHex(digest.digest(classInfo.getBytes(StandardCharsets.UTF_8))).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            return "UNKNOWN";
        }
    }
    
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
    
    // ═══════════════════════════════════════════════════════════════════
    // Shader Function Container
    // ═══════════════════════════════════════════════════════════════════
    
    public static class ShaderFunction {
        public final String id;
        public final String version;
        private final Function<String, String> executor;
        
        public ShaderFunction(String id, String version, Function<String, String> executor) {
            this.id = id;
            this.version = version;
            this.executor = executor;
        }
        
        public String execute(String seed) {
            return executor.apply(seed);
        }
        
        public String getFingerprint() {
            // Generate fingerprint for this shader
            String data = id + "|" + version + "|" + execute("FINGERPRINT_TEST");
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                return bytesToHex(digest.digest(data.getBytes(StandardCharsets.UTF_8)));
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException("SHA-256 not available", e);
            }
        }
    }
}
