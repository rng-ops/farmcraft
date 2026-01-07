package com.farmcraft.watermark;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Decodes watermark data from screenshot pixels.
 * 
 * Extracts the LSB-encoded game state for verification
 * and consensus checking between players.
 */
public class WatermarkDecoder {
    
    private static final byte[] MAGIC_BYTES = {(byte) 0xFA, (byte) 0xC0, (byte) 0xDE};
    private static final Gson GSON = new GsonBuilder().create();
    
    /**
     * Result of watermark decoding
     */
    public static class DecodeResult {
        public boolean success;
        public WatermarkData data;
        public float confidence;
        public String error;
        
        public static DecodeResult failure(String error) {
            DecodeResult r = new DecodeResult();
            r.success = false;
            r.error = error;
            r.confidence = 0;
            return r;
        }
        
        public static DecodeResult success(WatermarkData data, float confidence) {
            DecodeResult r = new DecodeResult();
            r.success = true;
            r.data = data;
            r.confidence = confidence;
            return r;
        }
    }
    
    /**
     * Decode watermark from image
     */
    public static DecodeResult decode(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        
        // Verify magic bytes
        if (!verifyMagicBytes(image)) {
            return DecodeResult.failure("Magic bytes not found - not a watermarked image");
        }
        
        try {
            // Extract bits from LSB
            List<Integer> bits = extractBits(image);
            
            // Parse length header (first 32 bits)
            int length = 0;
            for (int i = 0; i < 32; i++) {
                length = (length << 1) | bits.get(i);
            }
            
            if (length <= 0 || length > 1000000) {
                return DecodeResult.failure("Invalid data length: " + length);
            }
            
            // Extract bytes
            byte[] bytes = new byte[length];
            for (int i = 0; i < length; i++) {
                int b = 0;
                for (int j = 0; j < 8; j++) {
                    int bitIdx = 32 + (i * 8) + j;
                    if (bitIdx < bits.size()) {
                        b = (b << 1) | bits.get(bitIdx);
                    }
                }
                bytes[i] = (byte) b;
            }
            
            // Parse JSON
            String json = new String(bytes, StandardCharsets.UTF_8);
            WatermarkData data = GSON.fromJson(json, WatermarkData.class);
            
            // Calculate confidence
            float confidence = calculateConfidence(data);
            
            return DecodeResult.success(data, confidence);
            
        } catch (Exception e) {
            return DecodeResult.failure("Decode error: " + e.getMessage());
        }
    }
    
    /**
     * Quick check if image likely contains watermark
     */
    public static boolean detectWatermark(BufferedImage image) {
        return verifyMagicBytes(image);
    }
    
    /**
     * Compare two screenshots for consensus
     */
    public static ConsensusResult compareForConsensus(BufferedImage image1, BufferedImage image2) {
        DecodeResult result1 = decode(image1);
        DecodeResult result2 = decode(image2);
        
        ConsensusResult consensus = new ConsensusResult();
        
        if (!result1.success || !result2.success) {
            consensus.match = false;
            consensus.error = "One or both images failed to decode";
            return consensus;
        }
        
        WatermarkData data1 = result1.data;
        WatermarkData data2 = result2.data;
        
        // Compare game states
        consensus.worldSeedMatch = data1.gameState.worldSeed.equals(data2.gameState.worldSeed);
        consensus.chainHashMatch = data1.eventChainHash.equals(data2.eventChainHash);
        consensus.timestampDelta = Math.abs(data1.timestamp - data2.timestamp);
        consensus.weatherMatch = data1.gameState.weather.equals(data2.gameState.weather);
        
        // GPU fingerprints should differ (different machines)
        consensus.sameGPU = data1.gpuFingerprint.driverHash.equals(data2.gpuFingerprint.driverHash);
        
        // Overall match if core state agrees
        consensus.match = consensus.worldSeedMatch && 
                         consensus.chainHashMatch && 
                         consensus.timestampDelta < 60000; // Within 1 minute
        
        // Calculate similarity score
        float score = 0;
        if (consensus.worldSeedMatch) score += 0.3f;
        if (consensus.chainHashMatch) score += 0.3f;
        if (consensus.weatherMatch) score += 0.1f;
        if (consensus.timestampDelta < 10000) score += 0.3f;
        else if (consensus.timestampDelta < 60000) score += 0.15f;
        
        consensus.similarity = score;
        
        return consensus;
    }
    
    private static boolean verifyMagicBytes(BufferedImage image) {
        int[] positions = {0, 10, 20};
        int matches = 0;
        int width = image.getWidth();
        
        for (int i = 0; i < positions.length; i++) {
            int pos = positions[i];
            int x = pos % width;
            int y = pos / width;
            
            int rgb = image.getRGB(x, y);
            int red = (rgb >> 16) & 0xFF;
            
            int expected = (MAGIC_BYTES[i] & 0xFF) ^ (i * 17);
            if (red == expected) {
                matches++;
            }
        }
        
        return matches >= 2; // Allow some tolerance
    }
    
    private static List<Integer> extractBits(BufferedImage image) {
        List<Integer> bits = new ArrayList<>();
        int width = image.getWidth();
        int height = image.getHeight();
        int dataStartOffset = 100;
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixelIndex = y * width + x;
                if (pixelIndex < dataStartOffset) continue;
                
                int rgb = image.getRGB(x, y);
                int red = (rgb >> 16) & 0xFF;
                int green = (rgb >> 8) & 0xFF;
                int blue = rgb & 0xFF;
                
                // Extract 2 bits per channel
                bits.add((red >> 1) & 1);
                bits.add(red & 1);
                bits.add((green >> 1) & 1);
                bits.add(green & 1);
                bits.add((blue >> 1) & 1);
                bits.add(blue & 1);
            }
        }
        
        return bits;
    }
    
    private static float calculateConfidence(WatermarkData data) {
        float score = 0;
        
        if (data.clientId != null && data.clientId.length() > 10) score += 0.2f;
        if (data.timestamp > 1000000000000L) score += 0.1f;
        if (data.gameState != null) score += 0.3f;
        if (data.gpuFingerprint != null) score += 0.2f;
        if (data.signature != null && data.signature.length() == 64) score += 0.2f;
        
        return score;
    }
    
    /**
     * Result of comparing two screenshots
     */
    public static class ConsensusResult {
        public boolean match;
        public float similarity;
        public boolean worldSeedMatch;
        public boolean chainHashMatch;
        public boolean weatherMatch;
        public boolean sameGPU;
        public long timestampDelta;
        public String error;
    }
}
