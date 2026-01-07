package com.farmcraft.commands;

import com.farmcraft.client.DocsClient;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;

public class FarmCraftCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("farmcraft")
                        .then(Commands.literal("guide")
                                .executes(FarmCraftCommand::showGuide))
                        .then(Commands.literal("status")
                                .executes(FarmCraftCommand::showStatus))
                        .then(Commands.literal("help")
                                .then(Commands.argument("topic", StringArgumentType.string())
                                        .executes(ctx -> showHelp(ctx, StringArgumentType.getString(ctx, "topic"))))
                                .executes(ctx -> showHelp(ctx, "overview")))
                        .then(Commands.literal("ask")
                                .then(Commands.argument("question", StringArgumentType.greedyString())
                                        .executes(ctx -> askQuestion(ctx,
                                                StringArgumentType.getString(ctx, "question")))))
                        .then(Commands.literal("topics")
                                .executes(FarmCraftCommand::listTopics))
                        .executes(FarmCraftCommand::showGuide));
    }

    /**
     * Send a message to the command executor
     */
    private static void sendMessage(CommandContext<CommandSourceStack> ctx, String message) {
        ctx.getSource().sendSuccess(() -> Component.literal(message), false);
    }

    private static int showGuide(CommandContext<CommandSourceStack> ctx) {
        sendMessage(ctx, "§e§lFarmCraft In-Game Guide");
        sendMessage(ctx, "§7Use these commands to learn about the mod:");
        sendMessage(ctx, "");
        sendMessage(ctx, "§6/farmcraft guide §f- Show this guide");
        sendMessage(ctx, "§6/farmcraft status §f- Check connection & progress");
        sendMessage(ctx, "§6/farmcraft help <topic> §f- Get help on a topic");
        sendMessage(ctx, "§6/farmcraft ask <question> §f- Ask the AI assistant");
        sendMessage(ctx, "§6/farmcraft topics §f- List all documentation topics");
        sendMessage(ctx, "");
        sendMessage(ctx, "§7Examples:");
        sendMessage(ctx, "§8/farmcraft help recipes");
        sendMessage(ctx, "§8/farmcraft ask how do I unlock recipes?");
        return 1;
    }

    private static int showStatus(CommandContext<CommandSourceStack> ctx) {
        sendMessage(ctx, "§e§lFarmCraft Status");

        // Only check docs server if on client side
        if (FMLEnvironment.dist == Dist.CLIENT) {
            sendMessage(ctx, "§7Checking servers...");

            try {
                DocsClient.getInstance().healthCheck().thenAccept(healthy -> {
                    sendMessage(ctx,
                            healthy ? "§aDocumentation AI: §2✓ Available" : "§cDocumentation AI: §4✗ Unavailable");
                    sendMessage(ctx, "");
                    sendMessage(ctx, "§7For troubleshooting, use: §6/farmcraft help troubleshooting");
                });
            } catch (Exception e) {
                sendMessage(ctx, "§cDocumentation AI: §4✗ Not available");
                sendMessage(ctx, "");
                sendMessage(ctx, "§7Start the docs server: §fcd packages/llm-docs && npm start");
            }
        } else {
            sendMessage(ctx, "§cThis command only works on the client side.");
            sendMessage(ctx, "§7Use it in single-player or on your local client.");
        }

        return 1;
    }

    private static int showHelp(CommandContext<CommandSourceStack> ctx, String topic) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            sendMessage(ctx, "§cThis command only works on the client side.");
            return 0;
        }

        sendMessage(ctx, "§7Loading documentation for: §e" + topic + "§7...");

        try {
            DocsClient.getInstance().getTopic(topic).thenAccept(content -> {
                sendMessage(ctx, "§e§l" + topic.toUpperCase());
                sendMessage(ctx, "§8" + "=".repeat(40));

                // Split content and send in chat-friendly format
                String[] lines = content.split("\n");
                int lineCount = 0;
                for (String line : lines) {
                    if (!line.trim().isEmpty()) {
                        // Format markdown-style headers
                        if (line.startsWith("# ")) {
                            sendMessage(ctx, "§e§l" + line.substring(2));
                        } else if (line.startsWith("## ")) {
                            sendMessage(ctx, "§6" + line.substring(3));
                        } else if (line.startsWith("### ")) {
                            sendMessage(ctx, "§e" + line.substring(4));
                        } else if (line.startsWith("- ")) {
                            sendMessage(ctx, "  §7• §f" + line.substring(2));
                        } else {
                            sendMessage(ctx, "§f" + line);
                        }

                        lineCount++;
                        if (lineCount > 20) {
                            sendMessage(ctx, "§7... (truncated, use web docs for full content)");
                            break;
                        }
                    }
                }
            }).exceptionally(ex -> {
                sendMessage(ctx, "§cFailed to load documentation. Is the docs server running?");
                sendMessage(ctx, "§7Start it: §fcd packages/llm-docs && npm start");
                return null;
            });
        } catch (Exception e) {
            sendMessage(ctx, "§cFailed to load documentation: " + e.getMessage());
        }

        return 1;
    }

    private static int askQuestion(CommandContext<CommandSourceStack> ctx, String question) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            sendMessage(ctx, "§cThis command only works on the client side.");
            return 0;
        }

        sendMessage(ctx, "§7Asking AI: §f" + question);
        sendMessage(ctx, "§8[§6●§8] §7FarmCraft AI is thinking...");

        try {
            DocsClient.getInstance().ask(question).thenAccept(answer -> {
                sendMessage(ctx, "§8┌─ §6FarmCraft AI §8─────────────");

                // Format answer for chat (max 60 chars per line)
                String[] lines = answer.split("\n");
                for (String line : lines) {
                    if (line.trim().isEmpty())
                        continue;

                    // Word wrap
                    if (line.length() > 60) {
                        String[] words = line.split(" ");
                        StringBuilder currentLine = new StringBuilder();

                        for (String word : words) {
                            if (currentLine.length() + word.length() + 1 > 60) {
                                sendMessage(ctx, "§8│ §f" + currentLine.toString());
                                currentLine = new StringBuilder(word);
                            } else {
                                if (currentLine.length() > 0)
                                    currentLine.append(" ");
                                currentLine.append(word);
                            }
                        }

                        if (currentLine.length() > 0) {
                            sendMessage(ctx, "§8│ §f" + currentLine.toString());
                        }
                    } else {
                        sendMessage(ctx, "§8│ §f" + line.trim());
                    }
                }

                sendMessage(ctx, "§8└────────────────────────────");
                sendMessage(ctx, "§8Tip: Use §6/farmcraft help §8for more info");
            }).exceptionally(ex -> {
                sendMessage(ctx, "§cFailed to get answer. Make sure:");
                sendMessage(ctx, "§71. Documentation server is running (port 7424)");
                sendMessage(ctx, "§72. Ollama is running: §follama serve");
                sendMessage(ctx, "§73. gpt-oss model is installed: §follama pull gpt-oss");
                return null;
            });
        } catch (Exception e) {
            sendMessage(ctx, "§cError: " + e.getMessage());
            sendMessage(ctx, "§7Make sure the docs server is running.");
        }

        return 1;
    }

    private static int listTopics(CommandContext<CommandSourceStack> ctx) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            sendMessage(ctx, "§cThis command only works on the client side.");
            return 0;
        }

        sendMessage(ctx, "§e§lAvailable Documentation Topics:");

        try {
            DocsClient.getInstance().getTopics().thenAccept(topics -> {
                for (String topic : topics) {
                    sendMessage(ctx, "  §7• §6" + topic + " §8- §7/farmcraft help " + topic);
                }
                sendMessage(ctx, "");
                sendMessage(ctx, "§7Or ask any question: §6/farmcraft ask <your question>");
            }).exceptionally(ex -> {
                sendMessage(ctx, "§cFailed to load topics. Is the docs server running?");
                return null;
            });
        } catch (Exception e) {
            sendMessage(ctx, "§cError: " + e.getMessage());
        }

        return 1;
    }
}