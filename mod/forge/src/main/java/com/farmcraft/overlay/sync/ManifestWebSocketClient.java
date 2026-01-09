package com.farmcraft.overlay.sync;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.farmcraft.overlay.registry.DefinitionRegistry;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * WebSocket client for receiving real-time manifest updates.
 * 
 * Connects to the manifest distribution server and receives
 * push notifications when definitions are added, updated, or removed.
 */
public class ManifestWebSocketClient implements WebSocket.Listener {

    private static final Logger LOGGER = LoggerFactory.getLogger(ManifestWebSocketClient.class);

    private final String wsUrl;
    private final Consumer<ManifestUpdate> updateHandler;
    private final Set<String> subscribedManifests;

    private WebSocket webSocket;
    private StringBuilder messageBuffer;
    private volatile boolean connected;
    private volatile boolean shouldReconnect;

    private static final int RECONNECT_DELAY_MS = 5000;

    public ManifestWebSocketClient(String wsUrl, Consumer<ManifestUpdate> updateHandler) {
        this.wsUrl = wsUrl;
        this.updateHandler = updateHandler;
        this.subscribedManifests = new HashSet<>();
        this.messageBuffer = new StringBuilder();
        this.connected = false;
        this.shouldReconnect = true;
    }

    /**
     * Connect to the WebSocket server.
     */
    public CompletableFuture<Void> connect() {
        LOGGER.info("Connecting to WebSocket: {}", wsUrl);

        return HttpClient.newHttpClient()
                .newWebSocketBuilder()
                .buildAsync(URI.create(wsUrl), this)
                .thenAccept(ws -> {
                    this.webSocket = ws;
                    this.connected = true;
                    LOGGER.info("WebSocket connected");

                    // Resubscribe to manifests
                    for (String manifestId : subscribedManifests) {
                        sendSubscribe(manifestId);
                    }
                })
                .exceptionally(e -> {
                    LOGGER.error("WebSocket connection failed", e);
                    scheduleReconnect();
                    return null;
                });
    }

    /**
     * Disconnect from the WebSocket server.
     */
    public void disconnect() {
        shouldReconnect = false;
        if (webSocket != null) {
            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Client disconnect");
        }
        connected = false;
    }

    /**
     * Subscribe to updates for a manifest.
     */
    public void subscribe(String manifestId) {
        subscribedManifests.add(manifestId);
        if (connected) {
            sendSubscribe(manifestId);
        }
    }

    /**
     * Unsubscribe from updates for a manifest.
     */
    public void unsubscribe(String manifestId) {
        subscribedManifests.remove(manifestId);
        if (connected) {
            sendUnsubscribe(manifestId);
        }
    }

    private void sendSubscribe(String manifestId) {
        JsonObject message = new JsonObject();
        message.addProperty("type", "subscribe");
        message.addProperty("manifestId", manifestId);
        send(message.toString());
    }

    private void sendUnsubscribe(String manifestId) {
        JsonObject message = new JsonObject();
        message.addProperty("type", "unsubscribe");
        message.addProperty("manifestId", manifestId);
        send(message.toString());
    }

    private void send(String message) {
        if (webSocket != null && connected) {
            webSocket.sendText(message, true);
        }
    }

    // ========== WebSocket.Listener Implementation ==========

    @Override
    public void onOpen(WebSocket webSocket) {
        LOGGER.debug("WebSocket opened");
        webSocket.request(1);
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        messageBuffer.append(data);

        if (last) {
            String message = messageBuffer.toString();
            messageBuffer = new StringBuilder();
            handleMessage(message);
        }

        webSocket.request(1);
        return null;
    }

    @Override
    public CompletionStage<?> onPing(WebSocket webSocket, ByteBuffer message) {
        webSocket.sendPong(message);
        webSocket.request(1);
        return null;
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        LOGGER.info("WebSocket closed: {} {}", statusCode, reason);
        connected = false;

        if (shouldReconnect) {
            scheduleReconnect();
        }

        return null;
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        LOGGER.error("WebSocket error", error);
        connected = false;

        if (shouldReconnect) {
            scheduleReconnect();
        }
    }

    // ========== Message Handling ==========

    private void handleMessage(String message) {
        try {
            JsonObject json = JsonParser.parseString(message).getAsJsonObject();
            String type = json.get("type").getAsString();

            ManifestUpdate update = switch (type) {
                case "entry_added" -> new ManifestUpdate(
                        UpdateType.ENTRY_ADDED,
                        json.get("manifestId").getAsString(),
                        json.has("cid") ? json.get("cid").getAsString() : null,
                        json.has("entry") ? json.getAsJsonObject("entry") : null);
                case "entry_updated" -> new ManifestUpdate(
                        UpdateType.ENTRY_UPDATED,
                        json.get("manifestId").getAsString(),
                        json.has("cid") ? json.get("cid").getAsString() : null,
                        json.has("entry") ? json.getAsJsonObject("entry") : null);
                case "entry_removed" -> new ManifestUpdate(
                        UpdateType.ENTRY_REMOVED,
                        json.get("manifestId").getAsString(),
                        json.has("cid") ? json.get("cid").getAsString() : null,
                        null);
                case "manifest_updated" -> new ManifestUpdate(
                        UpdateType.MANIFEST_UPDATED,
                        json.get("manifestId").getAsString(),
                        null,
                        json.has("manifest") ? json.getAsJsonObject("manifest") : null);
                case "subscribed" -> {
                    LOGGER.debug("Subscribed to manifest: {}", json.get("manifestId").getAsString());
                    yield null;
                }
                case "error" -> {
                    LOGGER.error("Server error: {}", json.get("message").getAsString());
                    yield null;
                }
                default -> {
                    LOGGER.warn("Unknown message type: {}", type);
                    yield null;
                }
            };

            if (update != null && updateHandler != null) {
                updateHandler.accept(update);
            }

        } catch (Exception e) {
            LOGGER.error("Failed to parse WebSocket message: {}", message, e);
        }
    }

    // ========== Reconnection ==========

    private void scheduleReconnect() {
        if (!shouldReconnect)
            return;

        LOGGER.info("Scheduling reconnect in {}ms", RECONNECT_DELAY_MS);
        CompletableFuture.delayedExecutor(RECONNECT_DELAY_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
                .execute(() -> {
                    if (shouldReconnect && !connected) {
                        connect();
                    }
                });
    }

    // ========== Status ==========

    public boolean isConnected() {
        return connected;
    }

    public Set<String> getSubscribedManifests() {
        return Set.copyOf(subscribedManifests);
    }

    // ========== Data Classes ==========

    public enum UpdateType {
        ENTRY_ADDED,
        ENTRY_UPDATED,
        ENTRY_REMOVED,
        MANIFEST_UPDATED
    }

    public record ManifestUpdate(UpdateType type, String manifestId, String cid, JsonObject data) {
    }
}
