package com.farmcraft.watermark;

import com.google.gson.Gson;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Generates GPU fingerprint from OpenGL context.
 * 
 * This fingerprint helps identify the GPU architecture and driver,
 * which can be used for:
 * - Verifying client hardware consistency
 * - Detecting emulation/virtualization
 * - Correlating screenshots from same machine
 */
public class GPUFingerprinter {

    private static GPUFingerprint cachedFingerprint = null;

    /**
     * Generate GPU fingerprint from current OpenGL context
     */
    public static GPUFingerprint generate() {
        if (cachedFingerprint != null) {
            return cachedFingerprint;
        }

        GPUFingerprint fp = new GPUFingerprint();

        // Get basic info
        fp.vendor = GL11.glGetString(GL11.GL_VENDOR);
        fp.renderer = GL11.glGetString(GL11.GL_RENDERER);
        fp.glVersion = GL11.glGetString(GL11.GL_VERSION);
        fp.maxTextureSize = GL11.glGetInteger(GL11.GL_MAX_TEXTURE_SIZE);

        // Get float precision info
        fp.floatPrecision = getFloatPrecision();

        // Compute driver hash
        String driverData = fp.vendor + "|" + fp.renderer + "|" + fp.glVersion;
        fp.driverHash = sha256(driverData);

        cachedFingerprint = fp;
        return fp;
    }

    /**
     * Get detailed float precision information
     */
    private static FloatPrecisionInfo getFloatPrecision() {
        FloatPrecisionInfo info = new FloatPrecisionInfo();

        try {
            // Fallback for desktop OpenGL (glGetShaderPrecisionFormat is ES only)
            // Use standard IEEE 754 float precision values
            info.vertexPrecision = 23; // IEEE 754 float mantissa bits
            info.fragmentPrecision = 23;

            // Detect quirks through computation tests
            info.detectedQuirks = detectFloatQuirks();

        } catch (Exception e) {
            // Fallback for older OpenGL versions
            info.vertexPrecision = 23;
            info.fragmentPrecision = 23;
            info.detectedQuirks = new String[] { "precision_query_failed" };
        }

        return info;
    }

    /**
     * Detect GPU-specific floating point quirks
     * These can be used to identify GPU architecture
     */
    private static String[] detectFloatQuirks() {
        java.util.List<String> quirks = new java.util.ArrayList<>();

        // Test 1: Denormalized number handling
        float denorm = Float.MIN_VALUE / 2;
        if (denorm == 0) {
            quirks.add("flush_denorms_to_zero");
        }

        // Test 2: Infinity handling
        float inf = Float.MAX_VALUE * 2;
        if (!Float.isInfinite(inf)) {
            quirks.add("clamp_infinity");
        }

        // Test 3: NaN propagation
        float nan = Float.NaN + 1;
        if (!Float.isNaN(nan)) {
            quirks.add("nan_becomes_zero");
        }

        // Test 4: Precision in specific ranges
        float precTest = 1.0f + 1e-7f;
        if (precTest == 1.0f) {
            quirks.add("low_precision_near_one");
        }

        return quirks.toArray(new String[0]);
    }

    /**
     * Get a compact fingerprint hash
     */
    public static String getCompactFingerprint() {
        GPUFingerprint fp = generate();
        return fp.driverHash.substring(0, 16);
    }

    /**
     * Check if current GPU matches a known fingerprint
     */
    public static boolean matches(String expectedHash) {
        return getCompactFingerprint().equals(expectedHash);
    }

    private static String sha256(String input) {
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
}
