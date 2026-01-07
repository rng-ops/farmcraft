package com.farmcraft.client;

import com.farmcraft.FarmCraft;
import com.farmcraft.network.ModNetworking;
import com.farmcraft.network.SolutionSubmitPacket;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Client-side proof-of-work solver
 */
public class ProofOfWorkClient {
    
    private static ProofOfWorkClient instance;
    private static final Gson GSON = new Gson();
    
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "FarmCraft-PoW-Worker");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY); // Low priority to not affect gameplay
        return t;
    });
    
    private String currentChallengeId;
    private boolean isWorking;
    private float progress;
    
    private ProofOfWorkClient() {}
    
    public static ProofOfWorkClient getInstance() {
        if (instance == null) {
            instance = new ProofOfWorkClient();
        }
        return instance;
    }
    
    public void startChallenge(String challengeId, String type, int difficulty,
                                String payloadJson, long expiresAt, int rewardTokens) {
        if (isWorking) {
            FarmCraft.LOGGER.warn("Already working on a challenge, ignoring new one");
            return;
        }
        
        if (System.currentTimeMillis() > expiresAt) {
            FarmCraft.LOGGER.warn("Challenge already expired");
            return;
        }
        
        this.currentChallengeId = challengeId;
        this.isWorking = true;
        this.progress = 0;
        
        JsonObject payload = GSON.fromJson(payloadJson, JsonObject.class);
        
        CompletableFuture.runAsync(() -> {
            try {
                long startTime = System.currentTimeMillis();
                String solution = null;
                
                switch (type) {
                    case "hash_challenge":
                        solution = solveHashChallenge(payload, difficulty);
                        break;
                    case "protein_folding":
                        solution = solveFoldingChallenge(payload, difficulty);
                        break;
                    case "entropy_generation":
                        solution = solveEntropyChallenge(payload, difficulty);
                        break;
                    default:
                        FarmCraft.LOGGER.error("Unknown challenge type: {}", type);
                        return;
                }
                
                long computeTime = System.currentTimeMillis() - startTime;
                
                if (solution != null) {
                    FarmCraft.LOGGER.info("Challenge solved in {}ms", computeTime);
                    
                    // Send solution to server
                    ModNetworking.sendToServer(new SolutionSubmitPacket(
                        challengeId,
                        solution,
                        computeTime,
                        false // GPU not used in this implementation
                    ));
                }
            } catch (Exception e) {
                FarmCraft.LOGGER.error("Error solving challenge", e);
            } finally {
                isWorking = false;
                currentChallengeId = null;
                progress = 0;
            }
        }, executor);
    }
    
    private String solveHashChallenge(JsonObject payload, int difficulty) {
        String prefix = payload.get("prefix").getAsString();
        int targetDifficulty = payload.get("targetDifficulty").getAsInt();
        String target = "0".repeat(targetDifficulty);
        
        long nonce = 0;
        long maxAttempts = 100_000_000L;
        
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            
            while (nonce < maxAttempts) {
                String input = prefix + nonce;
                byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
                String hashHex = bytesToHex(hash);
                
                if (hashHex.startsWith(target)) {
                    return String.valueOf(nonce);
                }
                
                nonce++;
                
                if (nonce % 100000 == 0) {
                    progress = (float) nonce / maxAttempts;
                }
            }
        } catch (NoSuchAlgorithmException e) {
            FarmCraft.LOGGER.error("SHA-256 not available", e);
        }
        
        return null;
    }
    
    private String solveFoldingChallenge(JsonObject payload, int difficulty) {
        String sequence = payload.get("proteinSequence").getAsString();
        
        // Simplified protein folding simulation
        int length = sequence.length();
        float[] configuration = new float[length * 3];
        
        // Initialize linear chain
        for (int i = 0; i < length; i++) {
            configuration[i * 3] = i * 3.8f;
            configuration[i * 3 + 1] = 0;
            configuration[i * 3 + 2] = 0;
        }
        
        // Optimize with gradient descent
        int maxIterations = 1000 * difficulty;
        float temperature = 100.0f;
        float coolingRate = 0.995f;
        
        float bestEnergy = calculateEnergy(configuration, sequence);
        float[] bestConfig = configuration.clone();
        
        for (int iter = 0; iter < maxIterations; iter++) {
            // Perturb random position
            int idx = (int) (Math.random() * length);
            float[] newConfig = configuration.clone();
            newConfig[idx * 3] += (float) (Math.random() - 0.5) * 0.5f;
            newConfig[idx * 3 + 1] += (float) (Math.random() - 0.5) * 0.5f;
            newConfig[idx * 3 + 2] += (float) (Math.random() - 0.5) * 0.5f;
            
            float newEnergy = calculateEnergy(newConfig, sequence);
            float deltaE = newEnergy - bestEnergy;
            
            if (deltaE < 0 || Math.random() < Math.exp(-deltaE / temperature)) {
                configuration = newConfig;
                if (newEnergy < bestEnergy) {
                    bestEnergy = newEnergy;
                    bestConfig = configuration.clone();
                }
            }
            
            temperature *= coolingRate;
            
            if (iter % 1000 == 0) {
                progress = (float) iter / maxIterations;
            }
        }
        
        // Build solution JSON
        JsonObject solution = new JsonObject();
        solution.addProperty("energy", bestEnergy);
        solution.addProperty("iterations", maxIterations);
        
        StringBuilder configStr = new StringBuilder();
        for (float v : bestConfig) {
            if (configStr.length() > 0) configStr.append(",");
            configStr.append(String.format("%.4f", v));
        }
        solution.addProperty("configuration", configStr.toString());
        
        return GSON.toJson(solution);
    }
    
    private float calculateEnergy(float[] config, String sequence) {
        float energy = 0;
        int length = sequence.length();
        
        // Bond energy
        for (int i = 0; i < length - 1; i++) {
            float dx = config[(i + 1) * 3] - config[i * 3];
            float dy = config[(i + 1) * 3 + 1] - config[i * 3 + 1];
            float dz = config[(i + 1) * 3 + 2] - config[i * 3 + 2];
            float d = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            float deviation = d - 3.8f;
            energy += 100 * deviation * deviation;
        }
        
        // Lennard-Jones potential (simplified)
        for (int i = 0; i < length - 2; i++) {
            for (int j = i + 2; j < length; j++) {
                float dx = config[j * 3] - config[i * 3];
                float dy = config[j * 3 + 1] - config[i * 3 + 1];
                float dz = config[j * 3 + 2] - config[i * 3 + 2];
                float d = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                
                if (d > 0.1f) {
                    float sigma = 3.4f;
                    float sigma6 = (float) Math.pow(sigma / d, 6);
                    energy += 4 * (sigma6 * sigma6 - sigma6);
                }
            }
        }
        
        return energy;
    }
    
    private String solveEntropyChallenge(JsonObject payload, int difficulty) {
        String seed = payload.get("data").getAsJsonObject().get("seed").getAsString();
        int iterations = payload.get("data").getAsJsonObject().get("iterations").getAsInt();
        int outputSize = payload.get("data").getAsJsonObject().get("outputSize").getAsInt();
        
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(seed.getBytes(StandardCharsets.UTF_8));
            
            for (int i = 1; i < iterations; i++) {
                hash = digest.digest(hash);
                
                if (i % 10000 == 0) {
                    progress = (float) i / iterations;
                }
            }
            
            String entropy = bytesToHex(hash).substring(0, outputSize * 2);
            
            JsonObject solution = new JsonObject();
            solution.addProperty("entropy", entropy);
            
            return GSON.toJson(solution);
        } catch (NoSuchAlgorithmException e) {
            FarmCraft.LOGGER.error("SHA-256 not available", e);
            return null;
        }
    }
    
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
    
    public boolean isWorking() {
        return isWorking;
    }
    
    public float getProgress() {
        return progress;
    }
    
    public String getCurrentChallengeId() {
        return currentChallengeId;
    }
    
    public void shutdown() {
        executor.shutdownNow();
    }
}
