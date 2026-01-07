package com.farmcraft.client;

import com.farmcraft.config.FarmCraftConfig;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public class DocsClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(DocsClient.class);
    private static DocsClient instance;
    private final HttpClient httpClient;
    private final String baseUrl;

    private DocsClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        // Docs server runs on port 7424 by default
        this.baseUrl = "http://localhost:7424";
    }

    public static DocsClient getInstance() {
        if (instance == null) {
            instance = new DocsClient();
        }
        return instance;
    }

    /**
     * Ask a question to the LLM documentation assistant
     */
    public CompletableFuture<String> ask(String question) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                JsonObject requestBody = new JsonObject();
                requestBody.addProperty("question", question);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/ask"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                        .timeout(Duration.ofSeconds(10))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                    return json.get("answer").getAsString();
                } else {
                    LOGGER.error("Docs server returned error: {}", response.statusCode());
                    return "Failed to get answer from documentation server.";
                }
            } catch (Exception e) {
                LOGGER.error("Failed to query docs server", e);
                return "Documentation server is not available. Make sure it's running on port 7424.";
            }
        });
    }

    /**
     * Get documentation for a specific topic
     */
    public CompletableFuture<String> getTopic(String topic) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/docs/" + topic))
                        .GET()
                        .timeout(Duration.ofSeconds(5))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                    return json.get("content").getAsString();
                } else {
                    return "Topic '" + topic + "' not found.";
                }
            } catch (Exception e) {
                LOGGER.error("Failed to fetch topic", e);
                return "Failed to fetch documentation.";
            }
        });
    }

    /**
     * Get list of available topics
     */
    public CompletableFuture<String[]> getTopics() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/topics"))
                        .GET()
                        .timeout(Duration.ofSeconds(5))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                    return json.getAsJsonArray("topics").asList().stream()
                            .map(e -> e.getAsString())
                            .toArray(String[]::new);
                } else {
                    return new String[0];
                }
            } catch (Exception e) {
                LOGGER.error("Failed to fetch topics", e);
                return new String[0];
            }
        });
    }

    /**
     * Check if documentation server is available
     */
    public CompletableFuture<Boolean> healthCheck() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/health"))
                        .GET()
                        .timeout(Duration.ofSeconds(3))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                return response.statusCode() == 200;
            } catch (Exception e) {
                return false;
            }
        });
    }

    /**
     * Send a chat message to the player
     */
    public static void sendMessage(String message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal("§6[FarmCraft] §f" + message));
        }
    }

    /**
     * Send multi-line message to player
     */
    public static void sendMultilineMessage(String message) {
        for (String line : message.split("\n")) {
            if (!line.trim().isEmpty()) {
                sendMessage(line);
            }
        }
    }
}
