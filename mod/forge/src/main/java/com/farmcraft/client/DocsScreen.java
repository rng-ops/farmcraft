package com.farmcraft.client;

import com.farmcraft.FarmCraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class DocsScreen extends Screen {
    private EditBox questionBox;
    private String responseText = "";
    private int scrollOffset = 0;
    private static final int LINE_HEIGHT = 12;
    private static final int MAX_VISIBLE_LINES = 15;

    public DocsScreen() {
        super(Component.literal("FarmCraft Documentation"));
    }

    @Override
    protected void init() {
        super.init();

        // Question input box
        this.questionBox = new EditBox(this.font, this.width / 2 - 150, 40, 300, 20,
                Component.literal("Ask a question..."));
        this.questionBox.setMaxLength(256);
        this.addRenderableWidget(this.questionBox);

        // Ask button
        this.addRenderableWidget(Button.builder(Component.literal("Ask AI"), button -> {
            String question = questionBox.getValue();
            if (!question.isEmpty()) {
                askQuestion(question);
            }
        }).bounds(this.width / 2 - 150, 70, 145, 20).build());

        // Clear button
        this.addRenderableWidget(Button.builder(Component.literal("Clear"), button -> {
            responseText = "";
            scrollOffset = 0;
        }).bounds(this.width / 2 + 5, 70, 145, 20).build());

        // Quick topic buttons
        int buttonY = 100;
        int buttonWidth = 145;

        this.addRenderableWidget(Button.builder(Component.literal("Recipes"), button -> {
            loadTopic("recipes");
        }).bounds(this.width / 2 - 150, buttonY, buttonWidth, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Fertilizers"), button -> {
            loadTopic("fertilizers");
        }).bounds(this.width / 2 + 5, buttonY, buttonWidth, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Power Foods"), button -> {
            loadTopic("power-foods");
        }).bounds(this.width / 2 - 150, buttonY + 25, buttonWidth, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Getting Started"), button -> {
            loadTopic("getting-started");
        }).bounds(this.width / 2 + 5, buttonY + 25, buttonWidth, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Troubleshooting"), button -> {
            loadTopic("troubleshooting");
        }).bounds(this.width / 2 - 150, buttonY + 50, buttonWidth, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Status"), button -> {
            checkStatus();
        }).bounds(this.width / 2 + 5, buttonY + 50, buttonWidth, 20).build());

        // Close button
        this.addRenderableWidget(Button.builder(Component.literal("Close"), button -> {
            this.onClose();
        }).bounds(this.width / 2 - 75, this.height - 30, 150, 20).build());
    }

    private void askQuestion(String question) {
        responseText = "§7Asking: §f" + question + "\n§8[§6●§8] Thinking...\n\n";

        DocsClient.getInstance().ask(question).thenAccept(answer -> {
            responseText = "§e§lQuestion: §f" + question + "\n\n" +
                    "§6§lAnswer:\n§f" + answer;
            scrollOffset = 0;
        }).exceptionally(ex -> {
            responseText = "§cFailed to get answer.\n\n" +
                    "§7Make sure:\n" +
                    "§71. Docs server is running (port 7424)\n" +
                    "§72. Ollama is running: §follama serve\n" +
                    "§73. gpt-oss model installed: §follama pull gpt-oss";
            FarmCraft.LOGGER.error("AI query failed", ex);
            return null;
        });
    }

    private void loadTopic(String topic) {
        responseText = "§7Loading: §e" + topic + "§7...\n";

        DocsClient.getInstance().getTopic(topic).thenAccept(content -> {
            responseText = "§e§l" + topic.toUpperCase() + "\n\n§f" + content;
            scrollOffset = 0;
        }).exceptionally(ex -> {
            responseText = "§cFailed to load documentation.\n" +
                    "§7Start the docs server:\n" +
                    "§fcd packages/llm-docs && npm start";
            return null;
        });
    }

    private void checkStatus() {
        responseText = "§e§lFarmCraft Status\n\n§7Checking servers...\n";

        DocsClient.getInstance().healthCheck().thenAccept(healthy -> {
            if (healthy) {
                responseText = "§e§lFarmCraft Status\n\n" +
                        "§aDocumentation AI: §2✓ Available\n\n" +
                        "§7The AI assistant is ready!\n" +
                        "§7Ask questions or browse topics above.";
            } else {
                responseText = "§e§lFarmCraft Status\n\n" +
                        "§cDocumentation AI: §4✗ Unavailable\n\n" +
                        "§7Start the server:\n" +
                        "§fcd packages/llm-docs\n" +
                        "§fnpm start";
            }
        }).exceptionally(ex -> {
            responseText = "§e§lFarmCraft Status\n\n" +
                    "§cDocumentation AI: §4✗ Not responding\n\n" +
                    "§7Error: " + ex.getMessage();
            return null;
        });
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);

        // Title
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFF);

        super.render(graphics, mouseX, mouseY, partialTick);

        // Response area background
        int responseY = 180;
        int responseHeight = this.height - responseY - 45;
        graphics.fill(this.width / 2 - 150, responseY, this.width / 2 + 150, responseY + responseHeight, 0x88000000);

        // Render response text with scrolling
        if (!responseText.isEmpty()) {
            String[] lines = responseText.split("\n");
            int y = responseY + 5 - (scrollOffset * LINE_HEIGHT);

            for (String line : lines) {
                if (y > responseY && y < responseY + responseHeight - 10) {
                    // Parse color codes
                    graphics.drawString(this.font, line, this.width / 2 - 145, y, 0xFFFFFF);
                }
                y += LINE_HEIGHT;
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // Scroll the response text
        if (!responseText.isEmpty()) {
            int totalLines = responseText.split("\n").length;
            int maxScroll = Math.max(0, totalLines - MAX_VISIBLE_LINES);

            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) scrollY));
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
