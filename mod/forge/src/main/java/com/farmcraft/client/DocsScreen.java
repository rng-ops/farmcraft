package com.farmcraft.client;

import com.farmcraft.FarmCraft;
import com.farmcraft.client.workshop.WorkshopScreen;
import com.farmcraft.overlay.OverlayConfig.DiscoverabilityScope;
import com.farmcraft.overlay.OverlayConfig.PresenceVisibility;
import com.farmcraft.overlay.OverlayManager;
import com.farmcraft.overlay.OverlayTypes.*;
import com.farmcraft.overlay.friends.FriendsManager;
import com.farmcraft.overlay.search.SearchManager;

import javax.annotation.Nonnull;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Main menu screen for FarmCraft - provides About, Ask AI, Atlas, Friends, and
 * Settings
 */
public class DocsScreen extends Screen {

    private enum MenuState {
        MAIN_MENU,
        ABOUT,
        ASK_AI,
        ATLAS,
        FRIENDS,
        SEARCH,
        SETTINGS,
        WORKSHOP
    }

    private MenuState currentState = MenuState.MAIN_MENU;
    private EditBox questionBox;
    private EditBox searchBox;
    private EditBox inviteCodeBox;
    private String aiResponseText = "";
    private boolean isLoading = false;
    private int scrollOffset = 0;
    private static final int LINE_HEIGHT = 12;

    // Chat history for AI conversation
    private List<ChatMessage> chatHistory = new ArrayList<>();

    // Search results
    private List<SearchResult> searchResults = new ArrayList<>();
    private String lastSearchQuery = "";

    // Friends list cache
    private List<FriendDisplay> friendsCache = new ArrayList<>();
    private String pendingInviteCode = "";

    // Settings state
    private DiscoverabilityScope currentDiscoverability = DiscoverabilityScope.FRIENDS_ONLY;
    private PresenceVisibility currentPresenceVisibility = PresenceVisibility.COARSE;

    private static class ChatMessage {
        final boolean isUser;
        final String text;

        ChatMessage(boolean isUser, String text) {
            this.isUser = isUser;
            this.text = text;
        }
    }

    private static class SearchResult {
        final String handle;
        final String summary;
        final String freshness;
        final boolean anonymous;

        SearchResult(String handle, String summary, String freshness, boolean anonymous) {
            this.handle = handle;
            this.summary = summary;
            this.freshness = freshness;
            this.anonymous = anonymous;
        }
    }

    private static class FriendDisplay {
        final String friendId;
        final String displayName;
        final String status;
        final String lastSeen;

        FriendDisplay(String friendId, String displayName, String status, String lastSeen) {
            this.friendId = friendId;
            this.displayName = displayName;
            this.status = status;
            this.lastSeen = lastSeen;
        }
    }

    public DocsScreen() {
        super(Component.literal("FarmCraft"));
    }

    @Override
    protected void init() {
        super.init();
        rebuildUI();
    }

    private void rebuildUI() {
        this.clearWidgets();

        switch (currentState) {
            case MAIN_MENU -> buildMainMenu();
            case ABOUT -> buildAboutScreen();
            case ASK_AI -> buildAskAIScreen();
            case ATLAS -> buildAtlasScreen();
            case FRIENDS -> buildFriendsScreen();
            case SEARCH -> buildSearchScreen();
            case SETTINGS -> buildSettingsScreen();
            case WORKSHOP -> {
            } // Workshop opens separate screen
        }
    }

    private void buildMainMenu() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int buttonWidth = 200;
        int buttonHeight = 26;
        int spacing = 6;
        int startY = centerY - 100;

        // About button
        this.addRenderableWidget(Button.builder(Component.literal("§e✦ §fAbout FarmCraft §e✦"), button -> {
            currentState = MenuState.ABOUT;
            scrollOffset = 0;
            rebuildUI();
        }).bounds(centerX - buttonWidth / 2, startY, buttonWidth, buttonHeight).build());

        // Ask AI button
        this.addRenderableWidget(Button.builder(Component.literal("§b✧ §fAsk AI §b✧"), button -> {
            currentState = MenuState.ASK_AI;
            scrollOffset = 0;
            rebuildUI();
        }).bounds(centerX - buttonWidth / 2, startY + buttonHeight + spacing, buttonWidth, buttonHeight).build());

        // Workshop button (NEW!)
        this.addRenderableWidget(Button.builder(Component.literal("§6🔨 §fWorkshop §6🔨"), button -> {
            // Open Workshop screen
            Minecraft.getInstance().setScreen(new WorkshopScreen(this));
        }).bounds(centerX - buttonWidth / 2, startY + 2 * (buttonHeight + spacing), buttonWidth, buttonHeight).build());

        // Atlas button
        this.addRenderableWidget(Button.builder(Component.literal("§a🗺 §fAtlas §a🗺"), button -> {
            currentState = MenuState.ATLAS;
            scrollOffset = 0;
            rebuildUI();
        }).bounds(centerX - buttonWidth / 2, startY + 3 * (buttonHeight + spacing), buttonWidth, buttonHeight).build());

        // Friends button
        this.addRenderableWidget(Button.builder(Component.literal("§d👥 §fFriends §d👥"), button -> {
            currentState = MenuState.FRIENDS;
            scrollOffset = 0;
            refreshFriendsList();
            rebuildUI();
        }).bounds(centerX - buttonWidth / 2, startY + 4 * (buttonHeight + spacing), buttonWidth, buttonHeight).build());

        // Search button
        this.addRenderableWidget(Button.builder(Component.literal("§6🔍 §fSearch Players §6🔍"), button -> {
            currentState = MenuState.SEARCH;
            scrollOffset = 0;
            rebuildUI();
        }).bounds(centerX - buttonWidth / 2, startY + 5 * (buttonHeight + spacing), buttonWidth, buttonHeight).build());

        // Settings button
        this.addRenderableWidget(Button.builder(Component.literal("§7⚙ §fPrivacy Settings §7⚙"), button -> {
            currentState = MenuState.SETTINGS;
            scrollOffset = 0;
            rebuildUI();
        }).bounds(centerX - buttonWidth / 2, startY + 6 * (buttonHeight + spacing), buttonWidth, buttonHeight).build());

        // Close button at bottom
        this.addRenderableWidget(Button.builder(Component.literal("Close"), button -> {
            this.onClose();
        }).bounds(centerX - 50, this.height - 35, 100, 20).build());
    }

    private void buildAboutScreen() {
        int centerX = this.width / 2;

        // Back button
        this.addRenderableWidget(Button.builder(Component.literal("§c◄ §fBack"), button -> {
            currentState = MenuState.MAIN_MENU;
            rebuildUI();
        }).bounds(10, 10, 60, 20).build());

        // Close button at bottom
        this.addRenderableWidget(Button.builder(Component.literal("Close"), button -> {
            this.onClose();
        }).bounds(centerX - 50, this.height - 35, 100, 20).build());
    }

    private void buildAskAIScreen() {
        int centerX = this.width / 2;
        int inputWidth = Math.min(400, this.width - 40);

        // Back button
        this.addRenderableWidget(Button.builder(Component.literal("§c◄ §fBack"), button -> {
            currentState = MenuState.MAIN_MENU;
            rebuildUI();
        }).bounds(10, 10, 60, 20).build());

        // Question input box at bottom
        int inputY = this.height - 60;
        this.questionBox = new EditBox(this.font, centerX - inputWidth / 2, inputY, inputWidth - 70, 20,
                Component.literal("Type your question..."));
        this.questionBox.setMaxLength(500);
        this.questionBox.setHint(Component.literal("§7Ask anything about FarmCraft..."));
        this.addRenderableWidget(this.questionBox);

        // Send button
        this.addRenderableWidget(Button.builder(Component.literal("§a➤"), button -> {
            sendQuestion();
        }).bounds(centerX + inputWidth / 2 - 65, inputY, 30, 20).build());

        // Clear chat button
        this.addRenderableWidget(Button.builder(Component.literal("§c✖"), button -> {
            chatHistory.clear();
            aiResponseText = "";
            scrollOffset = 0;
        }).bounds(centerX + inputWidth / 2 - 30, inputY, 30, 20).build());

        // Close button
        this.addRenderableWidget(Button.builder(Component.literal("Close"), button -> {
            this.onClose();
        }).bounds(centerX - 50, this.height - 30, 100, 20).build());
    }

    // ═══════════════════════════════════════════════════════════════════
    // ATLAS SCREEN - Privacy-preserving player discovery
    // ═══════════════════════════════════════════════════════════════════

    private void buildAtlasScreen() {
        int centerX = this.width / 2;

        // Back button
        this.addRenderableWidget(Button.builder(Component.literal("§c◄ §fBack"), button -> {
            currentState = MenuState.MAIN_MENU;
            rebuildUI();
        }).bounds(10, 10, 60, 20).build());

        // Refresh button
        this.addRenderableWidget(Button.builder(Component.literal("§a↻ Refresh"), button -> {
            refreshAtlas();
        }).bounds(this.width - 90, 10, 80, 20).build());

        // Close button
        this.addRenderableWidget(Button.builder(Component.literal("Close"), button -> {
            this.onClose();
        }).bounds(centerX - 50, this.height - 30, 100, 20).build());
    }

    private void refreshAtlas() {
        // Query fog-of-war for nearby players with compatible mods
        OverlayManager overlay = OverlayManager.getInstance();
        if (overlay.isInitialized()) {
            overlay.getFogManager().query().thenAccept(shards -> {
                // Update UI on main thread
                Minecraft.getInstance().execute(() -> {
                    // Would update atlas display
                });
            });
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // FRIENDS SCREEN - Mutual consent friend management
    // ═══════════════════════════════════════════════════════════════════

    private void buildFriendsScreen() {
        int centerX = this.width / 2;
        int inputWidth = Math.min(300, this.width - 100);

        // Back button
        this.addRenderableWidget(Button.builder(Component.literal("§c◄ §fBack"), button -> {
            currentState = MenuState.MAIN_MENU;
            rebuildUI();
        }).bounds(10, 10, 60, 20).build());

        // Create invite button
        this.addRenderableWidget(Button.builder(Component.literal("§a+ Create Invite"), button -> {
            createFriendInvite();
        }).bounds(this.width - 120, 10, 110, 20).build());

        // Invite code input
        int inputY = this.height - 60;
        this.inviteCodeBox = new EditBox(this.font, centerX - inputWidth / 2, inputY, inputWidth - 80, 20,
                Component.literal("Enter invite code..."));
        this.inviteCodeBox.setMaxLength(30);
        this.inviteCodeBox.setHint(Component.literal("§7Paste friend's invite code..."));
        this.addRenderableWidget(this.inviteCodeBox);

        // Accept invite button
        this.addRenderableWidget(Button.builder(Component.literal("§a✓ Accept"), button -> {
            acceptFriendInvite();
        }).bounds(centerX + inputWidth / 2 - 75, inputY, 75, 20).build());

        // Close button
        this.addRenderableWidget(Button.builder(Component.literal("Close"), button -> {
            this.onClose();
        }).bounds(centerX - 50, this.height - 30, 100, 20).build());
    }

    private void createFriendInvite() {
        OverlayManager overlay = OverlayManager.getInstance();
        if (overlay.isInitialized()) {
            FriendInvite invite = overlay.getFriendsManager().createInvite();
            pendingInviteCode = invite.inviteCode();
            // Would show popup with invite code to copy
        }
    }

    private void acceptFriendInvite() {
        if (inviteCodeBox == null)
            return;
        String code = inviteCodeBox.getValue().trim();
        if (code.isEmpty())
            return;

        // In production: parse invite code, extract public key, call acceptInvite
        inviteCodeBox.setValue("");
    }

    private void refreshFriendsList() {
        friendsCache.clear();
        OverlayManager overlay = OverlayManager.getInstance();
        if (overlay.isInitialized()) {
            for (FriendConnection friend : overlay.getFriendsManager().getFriends()) {
                String status = friend.presence()
                        .map(p -> p.status().name())
                        .orElse("UNKNOWN");
                String lastSeen = friend.presence()
                        .flatMap(FriendPresence::lastSeenBucket)
                        .map(Enum::name)
                        .orElse("UNKNOWN");
                friendsCache.add(new FriendDisplay(
                        OverlayManager.hashToBase32(friend.friendPublicKey().getEncoded(), 16),
                        friend.displayName(),
                        status,
                        lastSeen));
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // SEARCH SCREEN - LinkedIn-style search with view receipts
    // ═══════════════════════════════════════════════════════════════════

    private void buildSearchScreen() {
        int centerX = this.width / 2;
        int inputWidth = Math.min(400, this.width - 40);

        // Back button
        this.addRenderableWidget(Button.builder(Component.literal("§c◄ §fBack"), button -> {
            currentState = MenuState.MAIN_MENU;
            rebuildUI();
        }).bounds(10, 10, 60, 20).build());

        // Search input
        int inputY = 45;
        this.searchBox = new EditBox(this.font, centerX - inputWidth / 2, inputY, inputWidth - 80, 20,
                Component.literal("Search..."));
        this.searchBox.setMaxLength(50);
        this.searchBox.setHint(Component.literal("§7Enter overlay handle..."));
        this.addRenderableWidget(this.searchBox);

        // Search button
        this.addRenderableWidget(Button.builder(Component.literal("§6🔍"), button -> {
            executeSearch(false);
        }).bounds(centerX + inputWidth / 2 - 75, inputY, 35, 20).build());

        // Anonymous search button
        this.addRenderableWidget(Button.builder(Component.literal("§7👁"), button -> {
            executeSearch(true);
        }).bounds(centerX + inputWidth / 2 - 35, inputY, 35, 20).build());

        // Close button
        this.addRenderableWidget(Button.builder(Component.literal("Close"), button -> {
            this.onClose();
        }).bounds(centerX - 50, this.height - 30, 100, 20).build());
    }

    private void executeSearch(boolean anonymous) {
        if (searchBox == null)
            return;
        String query = searchBox.getValue().trim();
        if (query.isEmpty() || isLoading)
            return;

        lastSearchQuery = query;
        isLoading = true;
        searchResults.clear();

        OverlayManager overlay = OverlayManager.getInstance();
        if (overlay.isInitialized()) {
            // Note: SearchManager needs to be added to OverlayManager
            // For now, simulate search
            isLoading = false;
        } else {
            isLoading = false;
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // SETTINGS SCREEN - Privacy configuration
    // ═══════════════════════════════════════════════════════════════════

    private void buildSettingsScreen() {
        int centerX = this.width / 2;
        int buttonWidth = 180;
        int buttonHeight = 20;
        int startY = 70;
        int spacing = 30;

        // Back button
        this.addRenderableWidget(Button.builder(Component.literal("§c◄ §fBack"), button -> {
            currentState = MenuState.MAIN_MENU;
            rebuildUI();
        }).bounds(10, 10, 60, 20).build());

        // Discoverability setting
        String discoverLabel = switch (currentDiscoverability) {
            case NONE -> "§c✗ Hidden";
            case FRIENDS_ONLY -> "§a👥 Friends Only";
            case COHORT -> "§e🏠 Same Server";
            case PUBLIC -> "§b🌐 Public";
        };
        this.addRenderableWidget(Button.builder(Component.literal(discoverLabel), button -> {
            // Cycle through options
            currentDiscoverability = switch (currentDiscoverability) {
                case NONE -> DiscoverabilityScope.FRIENDS_ONLY;
                case FRIENDS_ONLY -> DiscoverabilityScope.COHORT;
                case COHORT -> DiscoverabilityScope.PUBLIC;
                case PUBLIC -> DiscoverabilityScope.NONE;
            };
            rebuildUI();
        }).bounds(centerX - buttonWidth / 2, startY, buttonWidth, buttonHeight).build());

        // Presence visibility setting
        String presenceLabel = switch (currentPresenceVisibility) {
            case FULL -> "§a⚡ Full Presence";
            case COARSE -> "§e📍 Coarse (Recommended)";
            case HIDDEN -> "§c👻 Hidden";
        };
        this.addRenderableWidget(Button.builder(Component.literal(presenceLabel), button -> {
            currentPresenceVisibility = switch (currentPresenceVisibility) {
                case FULL -> PresenceVisibility.COARSE;
                case COARSE -> PresenceVisibility.HIDDEN;
                case HIDDEN -> PresenceVisibility.FULL;
            };
            rebuildUI();
        }).bounds(centerX - buttonWidth / 2, startY + spacing, buttonWidth, buttonHeight).build());

        // Close button
        this.addRenderableWidget(Button.builder(Component.literal("Close"), button -> {
            this.onClose();
        }).bounds(centerX - 50, this.height - 30, 100, 20).build());
    }

    private void sendQuestion() {
        if (questionBox == null)
            return;

        String question = questionBox.getValue().trim();
        if (question.isEmpty() || isLoading)
            return;

        // Add user message to history
        chatHistory.add(new ChatMessage(true, question));
        questionBox.setValue("");
        isLoading = true;
        scrollOffset = 0;

        // Query AI
        DocsClient.getInstance().ask(question).thenAccept(answer -> {
            chatHistory.add(new ChatMessage(false, answer));
            isLoading = false;
        }).exceptionally(ex -> {
            chatHistory.add(new ChatMessage(false,
                    "§cSorry, I couldn't connect to the AI server. Please make sure the docs server is running on port 7424."));
            isLoading = false;
            FarmCraft.LOGGER.error("AI query failed", ex);
            return null;
        });
    }

    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);

        switch (currentState) {
            case MAIN_MENU -> renderMainMenu(graphics);
            case ABOUT -> renderAboutScreen(graphics);
            case ASK_AI -> renderAskAIScreen(graphics);
            case ATLAS -> renderAtlasScreen(graphics);
            case FRIENDS -> renderFriendsScreen(graphics);
            case SEARCH -> renderSearchScreen(graphics);
            case SETTINGS -> renderSettingsScreen(graphics);
            case WORKSHOP -> {
            } // Workshop opens separate screen
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderMainMenu(GuiGraphics graphics) {
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        // Draw decorative background panel
        int panelWidth = 280;
        int panelHeight = 310;
        drawPanel(graphics, centerX - panelWidth / 2, centerY - panelHeight / 2 - 20, panelWidth, panelHeight);

        // Title with decorative elements
        graphics.drawCenteredString(this.font, "§6§l✦ FARMCRAFT ✦", centerX, centerY - 120, 0xFFAA00);
        graphics.drawCenteredString(this.font, "§7Enhanced Farming for Minecraft", centerX, centerY - 105, 0xAAAAAA);

        // Decorative separator
        graphics.drawCenteredString(this.font, "§8═══════════════════════", centerX, centerY - 92, 0x555555);

        // Overlay status indicator
        OverlayManager overlay = OverlayManager.getInstance();
        if (overlay.isInitialized()) {
            int friendCount = overlay.getFriendsManager().getFriendCount();
            graphics.drawCenteredString(this.font, "§a● §7Overlay Active §8| §7" + friendCount + " friends", centerX,
                    this.height - 55, 0x55FF55);
        } else {
            graphics.drawCenteredString(this.font, "§c● §7Overlay Inactive", centerX, this.height - 55, 0xFF5555);
        }

        // Footer
        graphics.drawCenteredString(this.font, "§8Press H anytime to open this menu", centerX, this.height - 42,
                0x555555);
    }

    private void renderAboutScreen(GuiGraphics graphics) {
        int centerX = this.width / 2;
        int panelWidth = Math.min(450, this.width - 40);
        int panelX = centerX - panelWidth / 2;
        int panelY = 40;
        int panelHeight = this.height - 90;

        // Draw main panel
        drawPanel(graphics, panelX, panelY, panelWidth, panelHeight);

        // Title
        graphics.drawCenteredString(this.font, "§6§l✦ ABOUT FARMCRAFT ✦", centerX, panelY + 10, 0xFFAA00);
        graphics.drawCenteredString(this.font, "§8═══════════════════════════════", centerX, panelY + 25, 0x555555);

        // Content
        int textX = panelX + 15;
        int textY = panelY + 45;
        int lineSpacing = 14;

        String[] aboutContent = {
                "§e§lVersion §f1.0.0 §7| §e§lMinecraft §f1.20.4",
                "",
                "§6§l━━━ WHAT FARMCRAFT ADDS ━━━",
                "",
                "§a§l◆ ENHANCED FERTILIZERS",
                "§7  Craft special fertilizers from stone materials",
                "§7  to boost your crop growth speed by 50%!",
                "",
                "§7  §f• Stone Dust §7- Made from Diorite",
                "§7  §f• Calcium Mix §7- Made from Calcite",
                "§7  §f• Mineral Blend §7- Made from Tuff",
                "§7  §f• Gravel Grit §7- Made from Gravel",
                "",
                "§b§l◆ POWER FOODS",
                "§7  Grow special crops on fertilized farmland",
                "§7  that give you potion effects when eaten!",
                "",
                "§7  §e⚡ Speed Carrot §7- Speed I (30s)",
                "§7  §6💪 Strength Potato §7- Strength I (30s)",
                "§7  §c🛡 Resistance Beet §7- Resistance I (30s)",
                "§7  §9👁 Night Vision Bread §7- Night Vision (60s)",
                "",
                "§d§l◆ RECIPE DISCOVERY",
                "§7  Unlock new recipes as you play! Harvest",
                "§7  crops and complete challenges to discover",
                "§7  advanced crafting recipes.",
                "",
                "§6§l━━━ HOW TO USE ━━━",
                "",
                "§f1. §7Craft a fertilizer using stone materials",
                "§f2. §7Right-click on farmland to apply it",
                "§f3. §7Look for §agreen particles §7(active effect)",
                "§f4. §7Plant matching crops for Power Foods!",
                "",
                "§6§l━━━ COMMANDS ━━━",
                "",
                "§e/farmcraft guide §7- Open this documentation",
                "§e/farmcraft status §7- Check server connection",
                "§e/farmcraft recipes §7- List your unlocked recipes",
                "",
                "§6§l━━━ KEYBINDS ━━━",
                "",
                "§fH §7- Open FarmCraft menu (this screen)",
                "",
                "§8━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━",
                "§7Made with §c♥ §7for the Minecraft community"
        };

        // Calculate visible area
        int visibleHeight = panelHeight - 60;
        int maxVisibleLines = visibleHeight / lineSpacing;
        int totalLines = aboutContent.length;
        int maxScroll = Math.max(0, totalLines - maxVisibleLines);
        scrollOffset = Math.min(scrollOffset, maxScroll);

        // Render visible lines with clipping
        int clipTop = panelY + 40;
        int clipBottom = panelY + panelHeight - 15;

        for (int i = scrollOffset; i < aboutContent.length && textY < clipBottom; i++) {
            if (textY >= clipTop) {
                graphics.drawString(this.font, aboutContent[i], textX, textY, 0xFFFFFF);
            }
            textY += lineSpacing;
        }

        // Scroll indicators
        if (scrollOffset > 0) {
            graphics.drawCenteredString(this.font, "§7▲ Scroll up for more", centerX, panelY + 38, 0x888888);
        }
        if (scrollOffset < maxScroll) {
            graphics.drawCenteredString(this.font, "§7▼ Scroll down for more", centerX, panelY + panelHeight - 12,
                    0x888888);
        }
    }

    private void renderAskAIScreen(GuiGraphics graphics) {
        int centerX = this.width / 2;
        int panelWidth = Math.min(500, this.width - 40);
        int panelX = centerX - panelWidth / 2;
        int panelY = 40;
        int panelHeight = this.height - 110;

        // Draw main panel
        drawPanel(graphics, panelX, panelY, panelWidth, panelHeight);

        // Title
        graphics.drawCenteredString(this.font, "§b§l✧ ASK AI ✧", centerX, panelY + 10, 0x55FFFF);
        graphics.drawCenteredString(this.font, "§7Ask anything about FarmCraft mechanics!", centerX, panelY + 25,
                0xAAAAAA);
        graphics.drawCenteredString(this.font, "§8═══════════════════════════════════", centerX, panelY + 38, 0x555555);

        // Chat area
        int chatY = panelY + 55;
        int chatHeight = panelHeight - 70;
        int chatEndY = chatY + chatHeight;

        if (chatHistory.isEmpty() && !isLoading) {
            // Show helpful suggestions when empty
            int suggestY = chatY + 20;
            graphics.drawCenteredString(this.font, "§7Try asking:", centerX, suggestY, 0xAAAAAA);
            suggestY += 20;
            graphics.drawCenteredString(this.font, "§e\"How do fertilizers work?\"", centerX, suggestY, 0xFFFF55);
            suggestY += 15;
            graphics.drawCenteredString(this.font, "§e\"What are Power Foods?\"", centerX, suggestY, 0xFFFF55);
            suggestY += 15;
            graphics.drawCenteredString(this.font, "§e\"How do I craft Stone Dust?\"", centerX, suggestY, 0xFFFF55);
            suggestY += 15;
            graphics.drawCenteredString(this.font, "§e\"Tell me about recipe discovery\"", centerX, suggestY, 0xFFFF55);
        } else {
            // Render chat messages
            int msgY = chatY;
            int textWidth = panelWidth - 30;

            for (ChatMessage msg : chatHistory) {
                if (msgY >= chatEndY - 10)
                    break;

                if (msg.isUser) {
                    // User message - right aligned with blue background
                    List<FormattedCharSequence> lines = wrapText("§f" + msg.text, textWidth - 20);
                    int msgHeight = lines.size() * LINE_HEIGHT + 8;
                    int msgWidth = Math.min(textWidth - 40, getMaxLineWidth(lines) + 16);

                    int msgX = panelX + panelWidth - 15 - msgWidth;
                    graphics.fill(msgX, msgY, msgX + msgWidth, msgY + msgHeight, 0xCC1E5799);

                    int lineY = msgY + 4;
                    for (FormattedCharSequence line : lines) {
                        if (lineY < chatEndY) {
                            graphics.drawString(this.font, line, msgX + 8, lineY, 0xFFFFFF);
                        }
                        lineY += LINE_HEIGHT;
                    }
                    msgY += msgHeight + 8;
                } else {
                    // AI message - left aligned with dark background
                    List<FormattedCharSequence> lines = wrapText("§f" + msg.text, textWidth - 20);
                    int msgHeight = lines.size() * LINE_HEIGHT + 8;
                    int msgWidth = Math.min(textWidth - 40, getMaxLineWidth(lines) + 16);

                    int msgX = panelX + 15;
                    graphics.fill(msgX, msgY, msgX + msgWidth, msgY + msgHeight, 0xCC333333);

                    // AI indicator
                    graphics.drawString(this.font, "§b§lAI", msgX + 4, msgY - 10, 0x55FFFF);

                    int lineY = msgY + 4;
                    for (FormattedCharSequence line : lines) {
                        if (lineY < chatEndY) {
                            graphics.drawString(this.font, line, msgX + 8, lineY, 0xFFFFFF);
                        }
                        lineY += LINE_HEIGHT;
                    }
                    msgY += msgHeight + 8;
                }
            }

            // Loading indicator
            if (isLoading) {
                String dots = ".".repeat((int) ((System.currentTimeMillis() / 300) % 4));
                graphics.drawString(this.font, "§7§oThinking" + dots, panelX + 15, msgY, 0xAAAAAA);
            }
        }

        // Input hint
        graphics.drawCenteredString(this.font, "§8Type your question below and press Enter or click ➤", centerX,
                this.height - 80, 0x555555);
    }

    private void renderAtlasScreen(GuiGraphics graphics) {
        int centerX = this.width / 2;
        int panelWidth = Math.min(450, this.width - 40);
        int panelX = centerX - panelWidth / 2;
        int panelY = 40;
        int panelHeight = this.height - 90;

        // Draw main panel
        drawPanel(graphics, panelX, panelY, panelWidth, panelHeight);

        // Title
        graphics.drawCenteredString(this.font, "§a§l🗺 ATLAS 🗺", centerX, panelY + 10, 0x55FF55);
        graphics.drawCenteredString(this.font, "§7Discover players with compatible mods", centerX, panelY + 25,
                0xAAAAAA);
        graphics.drawCenteredString(this.font, "§8═══════════════════════════════════", centerX, panelY + 38, 0x555555);

        // Privacy notice
        int textY = panelY + 55;
        graphics.drawCenteredString(this.font, "§e⚠ §7Privacy-preserving discovery", centerX, textY, 0xFFFF55);
        textY += 15;
        graphics.drawCenteredString(this.font, "§8No coordinates, no usernames shared", centerX, textY, 0x666666);
        textY += 25;

        // Fog status
        OverlayManager overlay = OverlayManager.getInstance();
        if (overlay.isInitialized()) {
            var fogManager = overlay.getFogManager();
            String topic = fogManager.getCurrentTopic();
            int shardCount = fogManager.getShardCount();

            if (topic != null) {
                graphics.drawString(this.font,
                        "§7Current topic: §f" + topic.substring(0, Math.min(16, topic.length())) + "...", panelX + 15,
                        textY, 0xAAAAAA);
                textY += 15;
                graphics.drawString(this.font, "§7Discovered shards: §a" + shardCount, panelX + 15, textY, 0xAAAAAA);
                textY += 15;

                var condition = fogManager.getCurrentCondition();
                if (condition != null) {
                    graphics.drawString(this.font, "§7Dimension: §f" + condition.dimension(), panelX + 15, textY,
                            0xAAAAAA);
                    textY += 12;
                    graphics.drawString(this.font, "§7Biome: §f" + condition.biome(), panelX + 15, textY, 0xAAAAAA);
                    textY += 12;
                    graphics.drawString(this.font, "§7Time: §f" + condition.timeOfDay(), panelX + 15, textY, 0xAAAAAA);
                }
            } else {
                graphics.drawCenteredString(this.font, "§7Fog-of-war not active", centerX, textY, 0x888888);
            }
        } else {
            graphics.drawCenteredString(this.font, "§cOverlay not initialized", centerX, textY, 0xFF5555);
            textY += 15;
            graphics.drawCenteredString(this.font, "§7Connect to a server to discover players", centerX, textY,
                    0x888888);
        }
    }

    private void renderFriendsScreen(GuiGraphics graphics) {
        int centerX = this.width / 2;
        int panelWidth = Math.min(450, this.width - 40);
        int panelX = centerX - panelWidth / 2;
        int panelY = 40;
        int panelHeight = this.height - 110;

        // Draw main panel
        drawPanel(graphics, panelX, panelY, panelWidth, panelHeight);

        // Title
        graphics.drawCenteredString(this.font, "§d§l👥 FRIENDS 👥", centerX, panelY + 10, 0xFF55FF);
        graphics.drawCenteredString(this.font, "§7Mutual consent connections", centerX, panelY + 25, 0xAAAAAA);
        graphics.drawCenteredString(this.font, "§8═══════════════════════════════════", centerX, panelY + 38, 0x555555);

        // Privacy notice
        int textY = panelY + 55;
        graphics.drawCenteredString(this.font, "§a✓ §7No server IP sharing §a✓ §7No coordinates", centerX, textY,
                0x55FF55);
        textY += 20;

        // Pending invite code
        if (!pendingInviteCode.isEmpty()) {
            graphics.drawString(this.font, "§eYour invite code:", panelX + 15, textY, 0xFFFF55);
            textY += 12;
            graphics.drawString(this.font, "§f§l" + pendingInviteCode, panelX + 15, textY, 0xFFFFFF);
            textY += 12;
            graphics.drawString(this.font, "§7Share this with your friend", panelX + 15, textY, 0x888888);
            textY += 20;
        }

        // Friends list
        graphics.drawString(this.font, "§6§lFriends (" + friendsCache.size() + ")", panelX + 15, textY, 0xFFAA00);
        textY += 15;

        if (friendsCache.isEmpty()) {
            graphics.drawString(this.font, "§7No friends yet. Create an invite to get started!", panelX + 15, textY,
                    0x888888);
        } else {
            for (FriendDisplay friend : friendsCache) {
                if (textY > panelY + panelHeight - 30)
                    break;

                String statusIcon = switch (friend.status) {
                    case "ONLINE" -> "§a●";
                    case "AWAY" -> "§e●";
                    default -> "§8●";
                };
                graphics.drawString(this.font, statusIcon + " §f" + friend.displayName, panelX + 20, textY, 0xFFFFFF);
                graphics.drawString(this.font, "§7" + friend.lastSeen, panelX + panelWidth - 80, textY, 0x888888);
                textY += 14;
            }
        }

        // Input hint
        graphics.drawCenteredString(this.font, "§8Enter a friend's invite code below to connect", centerX,
                this.height - 80, 0x555555);
    }

    private void renderSearchScreen(GuiGraphics graphics) {
        int centerX = this.width / 2;
        int panelWidth = Math.min(450, this.width - 40);
        int panelX = centerX - panelWidth / 2;
        int panelY = 75;
        int panelHeight = this.height - 125;

        // Draw main panel
        drawPanel(graphics, panelX, panelY, panelWidth, panelHeight);

        // Title area (above panel)
        graphics.drawCenteredString(this.font, "§6§l🔍 SEARCH PLAYERS 🔍", centerX, 15, 0xFFAA00);
        graphics.drawCenteredString(this.font, "§7Search by overlay handle", centerX, 28, 0xAAAAAA);

        // Privacy notice
        int textY = panelY + 10;
        graphics.drawCenteredString(this.font, "§e⚠ §7Views produce receipts (LinkedIn-style)", centerX, textY,
                0xFFFF55);
        textY += 15;
        graphics.drawCenteredString(this.font, "§8Targets are always notified of profile views", centerX, textY,
                0x666666);
        textY += 20;

        // Search stats
        OverlayManager overlay = OverlayManager.getInstance();
        if (overlay.isInitialized()) {
            int remaining = 20; // Would get from SearchManager
            graphics.drawString(this.font, "§7Searches remaining: §a" + remaining + "/20", panelX + 15, textY,
                    0xAAAAAA);
        }
        textY += 20;

        // Search results
        if (!lastSearchQuery.isEmpty()) {
            graphics.drawString(this.font, "§6Results for: §f" + lastSearchQuery, panelX + 15, textY, 0xFFAA00);
            textY += 15;

            if (searchResults.isEmpty()) {
                graphics.drawString(this.font, "§7No results found", panelX + 15, textY, 0x888888);
            } else {
                for (SearchResult result : searchResults) {
                    if (textY > panelY + panelHeight - 20)
                        break;

                    String anonymousTag = result.anonymous ? " §8[anon]" : "";
                    graphics.drawString(this.font, "§f" + result.handle + anonymousTag, panelX + 20, textY, 0xFFFFFF);
                    textY += 12;
                    graphics.drawString(this.font, "§7" + result.summary + " §8| " + result.freshness, panelX + 25,
                            textY, 0x888888);
                    textY += 16;
                }
            }
        } else {
            graphics.drawCenteredString(this.font, "§7Enter an overlay handle to search", centerX, textY + 20,
                    0x888888);
        }

        // Button hints
        graphics.drawString(this.font, "§6🔍 §7= Normal search", panelX + 15, panelY + panelHeight - 30, 0x888888);
        graphics.drawString(this.font, "§7👁 §8= Anonymous search", panelX + 15, panelY + panelHeight - 18, 0x666666);
    }

    private void renderSettingsScreen(GuiGraphics graphics) {
        int centerX = this.width / 2;
        int panelWidth = Math.min(400, this.width - 40);
        int panelX = centerX - panelWidth / 2;
        int panelY = 40;
        int panelHeight = this.height - 90;

        // Draw main panel
        drawPanel(graphics, panelX, panelY, panelWidth, panelHeight);

        // Title
        graphics.drawCenteredString(this.font, "§7§l⚙ PRIVACY SETTINGS ⚙", centerX, panelY + 10, 0xAAAAAA);
        graphics.drawCenteredString(this.font, "§8═══════════════════════════════════", centerX, panelY + 25, 0x555555);

        // Discoverability section
        int textY = panelY + 45;
        graphics.drawString(this.font, "§6§lDiscoverability", panelX + 15, textY, 0xFFAA00);
        textY += 12;
        graphics.drawString(this.font, "§7Who can find you in search?", panelX + 15, textY, 0x888888);
        textY += 30;

        // Presence section
        textY += 20;
        graphics.drawString(this.font, "§6§lPresence Visibility", panelX + 15, textY, 0xFFAA00);
        textY += 12;
        graphics.drawString(this.font, "§7How much do friends see?", panelX + 15, textY, 0x888888);
        textY += 40;

        // Safety guarantees
        textY += 10;
        graphics.drawString(this.font, "§a§lPrivacy Guarantees:", panelX + 15, textY, 0x55FF55);
        textY += 14;
        graphics.drawString(this.font, "§7• §fNo server IP sharing", panelX + 20, textY, 0xAAAAAA);
        textY += 12;
        graphics.drawString(this.font, "§7• §fNo coordinate sharing", panelX + 20, textY, 0xAAAAAA);
        textY += 12;
        graphics.drawString(this.font, "§7• §fNo silent profile views", panelX + 20, textY, 0xAAAAAA);
        textY += 12;
        graphics.drawString(this.font, "§7• §fNo third-party queries", panelX + 20, textY, 0xAAAAAA);
        textY += 12;
        graphics.drawString(this.font, "§7• §fSession keys regenerated", panelX + 20, textY, 0xAAAAAA);
        textY += 12;
        graphics.drawString(this.font, "§7• §fRate limiting everywhere", panelX + 20, textY, 0xAAAAAA);
    }

    private void drawPanel(GuiGraphics graphics, int x, int y, int width, int height) {
        // Outer border (gold/orange accent)
        graphics.fill(x - 2, y - 2, x + width + 2, y + height + 2, 0xFF8B4513);
        // Inner dark background
        graphics.fill(x, y, x + width, y + height, 0xEE1A1A1A);
        // Subtle inner border
        graphics.fill(x + 1, y + 1, x + width - 1, y + 2, 0x44FFFFFF);
        graphics.fill(x + 1, y + 1, x + 2, y + height - 1, 0x44FFFFFF);
    }

    private List<FormattedCharSequence> wrapText(String text, int maxWidth) {
        return this.font.split(Component.literal(text), maxWidth);
    }

    private int getMaxLineWidth(List<FormattedCharSequence> lines) {
        int max = 0;
        for (FormattedCharSequence line : lines) {
            max = Math.max(max, this.font.width(line));
        }
        return max;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (currentState == MenuState.ABOUT) {
            scrollOffset = Math.max(0, scrollOffset - (int) scrollY * 2);
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Handle Enter key in Ask AI screen
        if (currentState == MenuState.ASK_AI && keyCode == 257) { // Enter key
            sendQuestion();
            return true;
        }

        // Handle Escape to go back
        if (keyCode == 256) { // Escape
            if (currentState != MenuState.MAIN_MENU) {
                currentState = MenuState.MAIN_MENU;
                rebuildUI();
                return true;
            }
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
