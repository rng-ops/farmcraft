package com.farmcraft.client;

import com.farmcraft.config.FarmCraftConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Listens for chat messages and responds to questions using the AI assistant
 */
@Mod.EventBusSubscriber(modid = "farmcraft", value = Dist.CLIENT)
public class ChatAssistant {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChatAssistant.class);
    private static final String[] THINKING_ANIMATION = { "⣾", "⣽", "⣻", "⢿", "⡿", "⣟", "⣯", "⣷" };
    private static final AtomicInteger animationFrame = new AtomicInteger(0);
    private static CompletableFuture<?> currentThinkingTask = null;

    /**
     * Detect questions in chat and respond with AI
     */
    public static void handleChatMessage(String message, String playerName) {
        // Skip if disabled in config
        if (!FarmCraftConfig.ENABLE_AI_CHAT.get()) {
            return;
        }

        // Detect questions (ends with ?, contains farmcraft keywords, or starts with
        // trigger words)
        String lowerMessage = message.toLowerCase();
        boolean isQuestion = message.endsWith("?") ||
                lowerMessage.contains("farmcraft") ||
                lowerMessage.contains("fertilizer") ||
                lowerMessage.contains("recipe") ||
                lowerMessage.contains("power food") ||
                lowerMessage.contains("how do") ||
                lowerMessage.contains("how to") ||
                lowerMessage.contains("what is") ||
                lowerMessage.contains("what are") ||
                lowerMessage.contains("why") ||
                lowerMessage.contains("where");

        if (!isQuestion || message.length() < 10) {
            return;
        }

        LOGGER.info("Detected potential question in chat: {}", message);
        askAI(message, playerName);
    }

    /**
     * Ask the AI assistant and show thinking animation
     */
    private static void askAI(String question, String askedBy) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null)
            return;

        // Show thinking indicator
        sendThinkingMessage("🤔 FarmCraft AI is thinking...");

        // Start thinking animation
        startThinkingAnimation();

        // Query the AI
        DocsClient.getInstance().ask(question).thenAccept(answer -> {
            // Stop animation
            stopThinkingAnimation();

            // Send answer
            sendAIResponse(answer, question);

        }).exceptionally(ex -> {
            stopThinkingAnimation();
            sendErrorMessage("Failed to get AI response. Use /farmcraft help instead.");
            LOGGER.error("AI query failed", ex);
            return null;
        });
    }

    /**
     * Start animated thinking indicator
     */
    private static void startThinkingAnimation() {
        if (currentThinkingTask != null && !currentThinkingTask.isDone()) {
            return; // Already animating
        }

        animationFrame.set(0);
        currentThinkingTask = CompletableFuture.runAsync(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    int frame = animationFrame.getAndIncrement() % THINKING_ANIMATION.length;
                    String spinner = THINKING_ANIMATION[frame];

                    Minecraft.getInstance().execute(() -> {
                        if (Minecraft.getInstance().player != null) {
                            // Update action bar with thinking animation
                            Minecraft.getInstance().player.displayClientMessage(
                                    Component.literal("§6" + spinner + " §7FarmCraft AI is thinking..."),
                                    true // actionBar
                            );
                        }
                    });

                    Thread.sleep(100); // 10 FPS animation
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    /**
     * Stop thinking animation
     */
    private static void stopThinkingAnimation() {
        if (currentThinkingTask != null) {
            currentThinkingTask.cancel(true);
            currentThinkingTask = null;
        }

        // Clear action bar
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal(""), true);
        }
    }

    /**
     * Send thinking message to chat
     */
    private static void sendThinkingMessage(String message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal("§8[§6FarmCraft AI§8] §7" + message));
        }
    }

    /**
     * Send AI response with nice formatting
     */
    private static void sendAIResponse(String answer, String originalQuestion) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null)
            return;

        // Header
        mc.player.sendSystemMessage(Component.literal("§8┌─ §6FarmCraft AI §8─────────────"));

        // Split answer into chat-friendly lines (max 60 chars per line)
        String[] lines = answer.split("\n");
        for (String line : lines) {
            if (line.trim().isEmpty())
                continue;

            // Word wrap long lines
            if (line.length() > 60) {
                String[] words = line.split(" ");
                StringBuilder currentLine = new StringBuilder();

                for (String word : words) {
                    if (currentLine.length() + word.length() + 1 > 60) {
                        mc.player.sendSystemMessage(Component.literal("§8│ §f" + currentLine.toString()));
                        currentLine = new StringBuilder(word);
                    } else {
                        if (currentLine.length() > 0)
                            currentLine.append(" ");
                        currentLine.append(word);
                    }
                }

                if (currentLine.length() > 0) {
                    mc.player.sendSystemMessage(Component.literal("§8│ §f" + currentLine.toString()));
                }
            } else {
                mc.player.sendSystemMessage(Component.literal("§8│ §f" + line));
            }
        }

        // Footer
        mc.player.sendSystemMessage(Component.literal("§8└────────────────────────────"));
        mc.player.sendSystemMessage(Component.literal("§8Tip: Use §6/farmcraft help §8for more info"));
    }

    /**
     * Send error message
     */
    private static void sendErrorMessage(String message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal("§8[§cFarmCraft AI§8] §c" + message));
        }
    }
}
