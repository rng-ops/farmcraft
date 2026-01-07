package com.farmcraft.drm;

import com.farmcraft.FarmCraft;
import com.farmcraft.config.FarmCraftConfig;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Manages DRM verification lifecycle for the client.
 * 
 * Flow:
 * 1. Client connects and sends drm_init with version
 * 2. Server responds with challenge if version matches
 * 3. Client solves challenge (shader execution + PoW)
 * 4. Server verifies and grants access token
 * 5. Client uses token to request gated resources
 */
@OnlyIn(Dist.CLIENT)
public class DRMManager {

    private static DRMManager instance;

    private final DRMClient drmClient;
    private final Gson gson = new Gson();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    private WebSocket webSocket;
    private boolean connected = false;
    private boolean verified = false;

    // Callbacks for UI updates
    private Consumer<String> statusCallback;
    private Consumer<Boolean> verificationCallback;

    private DRMManager() {
        String clientId = getOrCreateClientId();
        this.drmClient = new DRMClient(clientId);
    }

    public static DRMManager getInstance() {
        if (instance == null) {
            instance = new DRMManager();
        }
        return instance;
    }

    /**
     * Connect to the DRM server
     */
    public CompletableFuture<Boolean> connect() {
        CompletableFuture<Boolean> future = new CompletableFuture<>();

        executor.submit(() -> {
            try {
                String wsUrl = String.format("ws://%s:%d",
                        FarmCraftConfig.RECIPE_SERVER_URL.get(),
                        FarmCraftConfig.RECIPE_SERVER_PORT.get() + 1); // WebSocket on port + 1

                HttpClient client = HttpClient.newHttpClient();

                webSocket = client.newWebSocketBuilder()
                        .buildAsync(URI.create(wsUrl), new WebSocketListener())
                        .join();

                connected = true;
                updateStatus("Connected to DRM server");

                // Send init message
                sendMessage(drmClient.createInitMessage());

                future.complete(true);

            } catch (Exception e) {
                FarmCraft.LOGGER.error("Failed to connect to DRM server", e);
                updateStatus("Connection failed: " + e.getMessage());
                future.complete(false);
            }
        });

        return future;
    }

    /**
     * Disconnect from DRM server
     */
    public void disconnect() {
        if (webSocket != null) {
            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Client closing");
            webSocket = null;
        }
        connected = false;
        verified = false;
        executor.shutdown();
        scheduler.shutdown();
    }

    /**
     * Request a new challenge from server
     */
    public void requestChallenge(String workType) {
        if (!connected) {
            updateStatus("Not connected to server");
            return;
        }

        Map<String, Object> message = Map.of(
                "type", "drm_challenge_request",
                "payload", Map.of("workType", workType));

        sendMessage(message);
    }

    /**
     * Request a gated resource
     */
    public CompletableFuture<Object> requestResource(String resourceId) {
        CompletableFuture<Object> future = new CompletableFuture<>();

        if (!verified || !drmClient.hasValidToken()) {
            future.completeExceptionally(new IllegalStateException("Not verified"));
            return future;
        }

        // Store callback for response
        // In real implementation, track pending requests
        sendMessage(drmClient.createResourceRequest(resourceId));

        // Timeout after 30 seconds
        scheduler.schedule(() -> {
            if (!future.isDone()) {
                future.completeExceptionally(new TimeoutException("Resource request timed out"));
            }
        }, 30, TimeUnit.SECONDS);

        return future;
    }

    /**
     * Check if client is verified
     */
    public boolean isVerified() {
        return verified;
    }

    /**
     * Get current trust score
     */
    public int getTrustScore() {
        return drmClient.getTrustScore();
    }

    /**
     * Set status callback
     */
    public void setStatusCallback(Consumer<String> callback) {
        this.statusCallback = callback;
    }

    /**
     * Set verification callback
     */
    public void setVerificationCallback(Consumer<Boolean> callback) {
        this.verificationCallback = callback;
    }

    // Private methods

    private void sendMessage(Map<String, Object> message) {
        if (webSocket != null) {
            String json = gson.toJson(message);
            webSocket.sendText(json, true);
        }
    }

    private void handleMessage(String text) {
        try {
            JsonObject json = JsonParser.parseString(text).getAsJsonObject();
            String type = json.get("type").getAsString();

            switch (type) {
                case "drm_init_response":
                    handleInitResponse(json);
                    break;
                case "drm_challenge":
                    handleChallenge(json);
                    break;
                case "drm_verify_result":
                    handleVerifyResult(json);
                    break;
                case "drm_resource_response":
                    handleResourceResponse(json);
                    break;
                case "drm_error":
                    handleError(json);
                    break;
            }
        } catch (Exception e) {
            FarmCraft.LOGGER.error("Error handling DRM message", e);
        }
    }

    @SuppressWarnings("unchecked")
    private void handleInitResponse(JsonObject json) {
        Map<String, Object> response = gson.fromJson(json, Map.class);
        drmClient.handleInitResponse(response);

        boolean versionMatch = json.get("versionMatch").getAsBoolean();
        if (versionMatch) {
            updateStatus("Version verified, solving initial challenge...");

            // Process initial challenge if present
            if (json.has("initialChallenge") && !json.get("initialChallenge").isJsonNull()) {
                DRMChallenge challenge = DRMChallenge.fromMap(
                        gson.fromJson(json.get("initialChallenge"), Map.class));
                solveChallengeAsync(challenge);
            }
        } else {
            String serverVersion = json.get("serverVersion").getAsString();
            updateStatus("Version mismatch! Server: " + serverVersion +
                    ", Client: " + DRMClient.CLIENT_VERSION);
        }
    }

    @SuppressWarnings("unchecked")
    private void handleChallenge(JsonObject json) {
        updateStatus("Challenge received, solving...");

        DRMChallenge challenge = DRMChallenge.fromMap(
                gson.fromJson(json.get("challenge"), Map.class));

        solveChallengeAsync(challenge);
    }

    private void solveChallengeAsync(DRMChallenge challenge) {
        executor.submit(() -> {
            try {
                long startTime = System.currentTimeMillis();

                updateStatus("Executing shaders: " + String.join(", ", challenge.requiredShaders));

                DRMResponse response = drmClient.solveChallenge(challenge);

                long elapsed = System.currentTimeMillis() - startTime;
                updateStatus("Challenge solved in " + elapsed + "ms, submitting...");

                sendMessage(response.toMessage());

            } catch (Exception e) {
                FarmCraft.LOGGER.error("Error solving challenge", e);
                updateStatus("Challenge failed: " + e.getMessage());
            }
        });
    }

    @SuppressWarnings("unchecked")
    private void handleVerifyResult(JsonObject json) {
        Map<String, Object> result = gson.fromJson(json, Map.class);
        drmClient.handleVerifyResult(result);

        boolean valid = json.get("valid").getAsBoolean();
        this.verified = valid;

        if (valid) {
            updateStatus("Verified! Trust score: " + drmClient.getTrustScore());

            if (verificationCallback != null) {
                verificationCallback.accept(true);
            }

            // Schedule periodic challenge solving to maintain trust
            scheduleMaintenance();

        } else {
            String errors = json.has("errors") ? json.get("errors").toString() : "Unknown error";
            updateStatus("Verification failed: " + errors);

            if (verificationCallback != null) {
                verificationCallback.accept(false);
            }
        }
    }

    private void handleResourceResponse(JsonObject json) {
        boolean granted = json.get("granted").getAsBoolean();

        if (granted) {
            String resourceId = json.get("resourceId").getAsString();
            updateStatus("Resource granted: " + resourceId);
            // Deliver resource to requesting code
        } else {
            String error = json.has("error") ? json.get("error").getAsString() : "Access denied";
            updateStatus("Resource denied: " + error);
        }
    }

    private void handleError(JsonObject json) {
        String error = json.has("error") ? json.get("error").getAsString() : "Unknown error";
        updateStatus("DRM Error: " + error);
    }

    private void updateStatus(String status) {
        FarmCraft.LOGGER.info("[DRM] " + status);
        if (statusCallback != null) {
            // Run on main thread for UI updates
            Minecraft.getInstance().execute(() -> statusCallback.accept(status));
        }
    }

    private void scheduleMaintenance() {
        // Solve a new challenge every 5 minutes to maintain trust score
        scheduler.scheduleAtFixedRate(() -> {
            if (connected && verified) {
                requestChallenge("shader_verify");
            }
        }, 5, 5, TimeUnit.MINUTES);
    }

    private String getOrCreateClientId() {
        // In real implementation, store this in a config file
        // For now, generate from player UUID or random
        return UUID.randomUUID().toString();
    }

    /**
     * WebSocket listener for DRM messages
     */
    private class WebSocketListener implements WebSocket.Listener {
        private StringBuilder textBuffer = new StringBuilder();

        @Override
        public void onOpen(WebSocket webSocket) {
            updateStatus("WebSocket opened");
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            textBuffer.append(data);
            if (last) {
                handleMessage(textBuffer.toString());
                textBuffer = new StringBuilder();
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            connected = false;
            verified = false;
            updateStatus("Connection closed: " + reason);
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            FarmCraft.LOGGER.error("WebSocket error", error);
            connected = false;
            updateStatus("WebSocket error: " + error.getMessage());
        }
    }
}
