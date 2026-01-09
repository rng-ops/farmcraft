package com.farmcraft.client.workshop;

import com.farmcraft.FarmCraft;
import com.farmcraft.overlay.OverlayManager;
import com.farmcraft.overlay.registry.DefinitionEntry;
import com.farmcraft.overlay.registry.DefinitionRegistry;
import com.farmcraft.overlay.registry.DefinitionStatus;
import com.farmcraft.overlay.registry.DefinitionType;
import com.farmcraft.overlay.resources.ResourceManager;
import com.farmcraft.overlay.resources.ResourceWallet;
import com.farmcraft.overlay.sync.SyncService;

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
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * In-game Workshop UI for browsing and managing overlay definitions.
 * 
 * This screen provides:
 * - Browse Items/Spells from local registry
 * - View definition details (CID, status, signers)
 * - Create new drafts (redirects to web UI)
 * - Resource wallet overview
 * - Sync status with distribution server
 * 
 * Privacy: All definition data is local-first. Only published definitions
 * with player consent are shared via manifests.
 */
public class WorkshopScreen extends Screen {

    /**
     * Workshop navigation tabs
     */
    public enum WorkshopTab {
        ITEMS("§6📦 Items"),
        SPELLS("§d✨ Spells"),
        WALLET("§e💰 Wallet"),
        SYNC("§a🔄 Sync");

        private final String label;

        WorkshopTab(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    private WorkshopTab currentTab = WorkshopTab.ITEMS;
    private String searchQuery = "";
    private int scrollOffset = 0;
    private DefinitionEntry selectedEntry = null;

    // UI Components
    private EditBox searchBox;

    // Cached data
    private List<DefinitionEntry> filteredItems = new ArrayList<>();
    private List<DefinitionEntry> filteredSpells = new ArrayList<>();

    // Layout constants
    private static final int TAB_HEIGHT = 25;
    private static final int SIDEBAR_WIDTH = 200;
    private static final int ITEM_HEIGHT = 36;
    private static final int LINE_HEIGHT = 12;

    private final Screen previousScreen;

    public WorkshopScreen(Screen previousScreen) {
        super(Component.literal("Workshop"));
        this.previousScreen = previousScreen;
    }

    public WorkshopScreen() {
        this(null);
    }

    @Override
    protected void init() {
        super.init();
        refreshCache();
        rebuildUI();
    }

    private void refreshCache() {
        DefinitionRegistry registry = DefinitionRegistry.getInstance();

        filteredItems = registry.getByType(DefinitionType.ITEM).stream()
                .filter(e -> matchesSearch(e))
                .collect(Collectors.toList());

        filteredSpells = registry.getByType(DefinitionType.SPELL).stream()
                .filter(e -> matchesSearch(e))
                .collect(Collectors.toList());
    }

    private boolean matchesSearch(DefinitionEntry entry) {
        if (searchQuery.isEmpty())
            return true;
        String query = searchQuery.toLowerCase();
        return entry.name().toLowerCase().contains(query) ||
                entry.cid().toLowerCase().contains(query) ||
                (entry.description() != null && entry.description().toLowerCase().contains(query));
    }

    private void rebuildUI() {
        this.clearWidgets();

        int centerX = this.width / 2;

        // Tab buttons at top
        int tabWidth = 80;
        int tabStartX = centerX - (WorkshopTab.values().length * tabWidth) / 2;
        int tabY = 35;

        for (int i = 0; i < WorkshopTab.values().length; i++) {
            WorkshopTab tab = WorkshopTab.values()[i];
            boolean isActive = tab == currentTab;

            this.addRenderableWidget(Button.builder(
                    Component.literal(isActive ? "§l" + tab.getLabel() : "§7" + tab.getLabel().replaceAll("§.", "")),
                    button -> {
                        currentTab = tab;
                        scrollOffset = 0;
                        selectedEntry = null;
                        refreshCache();
                        rebuildUI();
                    }).bounds(tabStartX + i * tabWidth, tabY, tabWidth - 4, TAB_HEIGHT).build());
        }

        // Content area based on tab
        switch (currentTab) {
            case ITEMS, SPELLS -> buildBrowseUI();
            case WALLET -> buildWalletUI();
            case SYNC -> buildSyncUI();
        }

        // Back button
        this.addRenderableWidget(Button.builder(Component.literal("§c◄ Back"), button -> {
            if (previousScreen != null) {
                Minecraft.getInstance().setScreen(previousScreen);
            } else {
                this.onClose();
            }
        }).bounds(10, 10, 60, 20).build());

        // Close button
        this.addRenderableWidget(Button.builder(Component.literal("Close"), button -> {
            this.onClose();
        }).bounds(centerX - 40, this.height - 30, 80, 20).build());
    }

    private void buildBrowseUI() {
        int searchY = 65;
        int searchWidth = Math.min(300, this.width - 40);
        int centerX = this.width / 2;

        // Search box
        this.searchBox = new EditBox(this.font, centerX - searchWidth / 2, searchY, searchWidth - 80, 18,
                Component.literal("Search..."));
        this.searchBox.setMaxLength(50);
        this.searchBox.setHint(Component.literal("§7Search by name or CID..."));
        this.searchBox.setValue(searchQuery);
        this.searchBox.setResponder(query -> {
            searchQuery = query;
            refreshCache();
        });
        this.addRenderableWidget(this.searchBox);

        // Search button
        this.addRenderableWidget(Button.builder(Component.literal("§6🔍"), button -> {
            refreshCache();
        }).bounds(centerX + searchWidth / 2 - 75, searchY, 35, 18).build());

        // Refresh button
        this.addRenderableWidget(Button.builder(Component.literal("§a↻"), button -> {
            refreshCache();
            rebuildUI();
        }).bounds(centerX + searchWidth / 2 - 35, searchY, 35, 18).build());

        // Open Web Editor button
        this.addRenderableWidget(Button.builder(Component.literal("§b+ New (Web)"), button -> {
            openWebEditor();
        }).bounds(this.width - 100, searchY, 90, 18).build());
    }

    private void buildWalletUI() {
        // Wallet refresh button
        this.addRenderableWidget(Button.builder(Component.literal("§a↻ Refresh"), button -> {
            rebuildUI();
        }).bounds(this.width - 100, 65, 90, 18).build());
    }

    private void buildSyncUI() {
        int buttonY = 65;
        int buttonWidth = 100;
        int centerX = this.width / 2;

        // Sync now button
        this.addRenderableWidget(Button.builder(Component.literal("§a🔄 Sync Now"), button -> {
            triggerSync();
        }).bounds(centerX - buttonWidth - 5, buttonY, buttonWidth, 20).build());

        // Force full sync button
        this.addRenderableWidget(Button.builder(Component.literal("§e⟳ Full Sync"), button -> {
            triggerFullSync();
        }).bounds(centerX + 5, buttonY, buttonWidth, 20).build());
    }

    private void openWebEditor() {
        // Copy the web editor URL to chat/clipboard hint
        // In production this would open a browser or show the URL
        Minecraft.getInstance().player.sendSystemMessage(
                Component.literal("§b[Workshop] §7Open web editor at: §fhttp://localhost:7432"));
    }

    private void triggerSync() {
        SyncService syncService = SyncService.getInstance();
        syncService.doSync().thenAccept(result -> {
            Minecraft.getInstance().execute(() -> {
                if (result.success) {
                    Minecraft.getInstance().player.sendSystemMessage(
                            Component.literal("§a[Workshop] §7Sync complete: " + result.toString()));
                } else {
                    Minecraft.getInstance().player.sendSystemMessage(
                            Component.literal("§c[Workshop] §7Sync failed: " + result.error));
                }
                rebuildUI();
            });
        });
    }

    private void triggerFullSync() {
        SyncService syncService = SyncService.getInstance();
        syncService.forceFullSync().thenAccept(result -> {
            Minecraft.getInstance().execute(() -> {
                if (result.success) {
                    Minecraft.getInstance().player.sendSystemMessage(
                            Component.literal("§a[Workshop] §7Full sync complete: " + result.toString()));
                } else {
                    Minecraft.getInstance().player.sendSystemMessage(
                            Component.literal("§c[Workshop] §7Full sync failed: " + result.error));
                }
                refreshCache();
                rebuildUI();
            });
        });
    }

    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);

        // Title
        graphics.drawCenteredString(this.font, "§6§l✦ WORKSHOP ✦", this.width / 2, 12, 0xFFAA00);
        graphics.drawCenteredString(this.font, "§7Definition Browser & Manager", this.width / 2, 24, 0xAAAAAA);

        // Render content based on tab
        switch (currentTab) {
            case ITEMS -> renderItemsList(graphics, mouseX, mouseY);
            case SPELLS -> renderSpellsList(graphics, mouseX, mouseY);
            case WALLET -> renderWalletView(graphics);
            case SYNC -> renderSyncView(graphics);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderItemsList(GuiGraphics graphics, int mouseX, int mouseY) {
        renderDefinitionList(graphics, filteredItems, "Items", mouseX, mouseY);
    }

    private void renderSpellsList(GuiGraphics graphics, int mouseX, int mouseY) {
        renderDefinitionList(graphics, filteredSpells, "Spells", mouseX, mouseY);
    }

    private void renderDefinitionList(GuiGraphics graphics, List<DefinitionEntry> entries,
            String title, int mouseX, int mouseY) {
        int listX = 20;
        int listY = 90;
        int listWidth = selectedEntry != null ? this.width - SIDEBAR_WIDTH - 40 : this.width - 40;
        int listHeight = this.height - 140;

        // List panel
        drawPanel(graphics, listX, listY, listWidth, listHeight);

        // Header
        graphics.drawString(this.font, "§6" + title + " §7(" + entries.size() + ")",
                listX + 10, listY + 8, 0xFFAA00);

        if (entries.isEmpty()) {
            graphics.drawCenteredString(this.font, "§7No " + title.toLowerCase() + " found",
                    listX + listWidth / 2, listY + listHeight / 2, 0x888888);
            graphics.drawCenteredString(this.font, "§8Sync with server or create new",
                    listX + listWidth / 2, listY + listHeight / 2 + 15, 0x555555);
            return;
        }

        // Render entries
        int entryY = listY + 25;
        int maxVisible = (listHeight - 30) / ITEM_HEIGHT;
        int endIndex = Math.min(scrollOffset + maxVisible, entries.size());

        for (int i = scrollOffset; i < endIndex; i++) {
            DefinitionEntry entry = entries.get(i);
            boolean isSelected = entry.equals(selectedEntry);
            boolean isHovered = mouseX >= listX + 5 && mouseX <= listX + listWidth - 5 &&
                    mouseY >= entryY && mouseY <= entryY + ITEM_HEIGHT - 2;

            // Entry background
            int bgColor = isSelected ? 0x66447799 : (isHovered ? 0x44444444 : 0x22222222);
            graphics.fill(listX + 5, entryY, listX + listWidth - 5, entryY + ITEM_HEIGHT - 2, bgColor);

            // Status indicator
            String statusIcon = getStatusIcon(entry.status());
            graphics.drawString(this.font, statusIcon, listX + 10, entryY + 4, 0xFFFFFF);

            // Name and version
            String nameText = "§f" + entry.name();
            if (entry.version() > 0) {
                nameText += " §8v" + entry.version();
            }
            graphics.drawString(this.font, nameText, listX + 25, entryY + 4, 0xFFFFFF);

            // CID (truncated)
            String cidShort = entry.cid().length() > 16 ? entry.cid().substring(0, 16) + "..." : entry.cid();
            graphics.drawString(this.font, "§8" + cidShort, listX + 25, entryY + 16, 0x666666);

            // Signature count
            int sigCount = entry.signatures() != null ? entry.signatures().size() : 0;
            if (sigCount > 0) {
                graphics.drawString(this.font, "§a✓" + sigCount,
                        listX + listWidth - 35, entryY + 10, 0x55FF55);
            }

            entryY += ITEM_HEIGHT;
        }

        // Scroll indicators
        if (scrollOffset > 0) {
            graphics.drawCenteredString(this.font, "§7▲", listX + listWidth / 2, listY + 18, 0x888888);
        }
        if (endIndex < entries.size()) {
            graphics.drawCenteredString(this.font, "§7▼", listX + listWidth / 2, listY + listHeight - 10, 0x888888);
        }

        // Detail sidebar if entry selected
        if (selectedEntry != null) {
            renderDetailSidebar(graphics, selectedEntry);
        }
    }

    private void renderDetailSidebar(GuiGraphics graphics, DefinitionEntry entry) {
        int sidebarX = this.width - SIDEBAR_WIDTH - 10;
        int sidebarY = 90;
        int sidebarHeight = this.height - 140;

        drawPanel(graphics, sidebarX, sidebarY, SIDEBAR_WIDTH, sidebarHeight);

        int textX = sidebarX + 10;
        int textY = sidebarY + 10;

        // Name
        graphics.drawString(this.font, "§6§l" + entry.name(), textX, textY, 0xFFAA00);
        textY += 15;

        // Status
        graphics.drawString(this.font, "§7Status: " + getStatusIcon(entry.status()) + " §f" + entry.status().name(),
                textX, textY, 0xAAAAAA);
        textY += 12;

        // Type
        graphics.drawString(this.font, "§7Type: §f" + entry.type().name(), textX, textY, 0xAAAAAA);
        textY += 12;

        // Version
        if (entry.version() > 0) {
            graphics.drawString(this.font, "§7Version: §f" + entry.version(), textX, textY, 0xAAAAAA);
            textY += 12;
        }

        // CID
        textY += 5;
        graphics.drawString(this.font, "§7CID:", textX, textY, 0xAAAAAA);
        textY += 10;
        String cid = entry.cid();
        // Split CID into readable chunks
        for (int i = 0; i < cid.length(); i += 24) {
            String chunk = cid.substring(i, Math.min(i + 24, cid.length()));
            graphics.drawString(this.font, "§8" + chunk, textX + 5, textY, 0x666666);
            textY += 10;
            if (textY > sidebarY + sidebarHeight - 60)
                break;
        }

        // Description
        textY += 5;
        if (entry.description() != null && !entry.description().isEmpty()) {
            graphics.drawString(this.font, "§7Description:", textX, textY, 0xAAAAAA);
            textY += 10;

            List<FormattedCharSequence> lines = this.font.split(
                    Component.literal("§f" + entry.description()), SIDEBAR_WIDTH - 20);
            for (FormattedCharSequence line : lines) {
                if (textY > sidebarY + sidebarHeight - 40)
                    break;
                graphics.drawString(this.font, line, textX + 5, textY, 0xFFFFFF);
                textY += 10;
            }
        }

        // Signatures
        textY += 5;
        int sigCount = entry.signatures() != null ? entry.signatures().size() : 0;
        graphics.drawString(this.font, "§7Signatures: §a" + sigCount, textX, textY, 0xAAAAAA);

        // Timestamps
        textY += 15;
        if (entry.createdAt() != null) {
            graphics.drawString(this.font, "§8Created: " + formatTimestamp(entry.createdAt().toEpochMilli()),
                    textX, textY, 0x555555);
            textY += 10;
        }
        if (entry.updatedAt() != null) {
            graphics.drawString(this.font, "§8Updated: " + formatTimestamp(entry.updatedAt().toEpochMilli()),
                    textX, textY, 0x555555);
        }
    }

    private void renderWalletView(GuiGraphics graphics) {
        int panelX = 20;
        int panelY = 90;
        int panelWidth = this.width - 40;
        int panelHeight = this.height - 140;

        drawPanel(graphics, panelX, panelY, panelWidth, panelHeight);

        // Get wallet
        UUID playerId = Minecraft.getInstance().player != null ? Minecraft.getInstance().player.getUUID()
                : new UUID(0, 0);
        ResourceWallet wallet = ResourceManager.getInstance().getWallet(playerId);

        int textX = panelX + 20;
        int textY = panelY + 15;
        int colWidth = (panelWidth - 40) / 2;

        // Title
        graphics.drawString(this.font, "§6§lResource Wallet", textX, textY, 0xFFAA00);
        textY += 20;

        // Ash (Proof of Work)
        graphics.drawString(this.font, "§c🔥 Ash (PoW)", textX, textY, 0xFF5555);
        textY += 12;
        graphics.drawString(this.font, "§f" + wallet.getAsh() + " §8accumulated", textX + 10, textY, 0xFFFFFF);
        textY += 10;
        graphics.drawString(this.font, "§7Earned by contributing compute", textX + 10, textY, 0x888888);
        textY += 20;

        // Sigils (Time-based)
        graphics.drawString(this.font, "§b✦ Sigils (Regenerating)", textX, textY, 0x55FFFF);
        textY += 12;
        graphics.drawString(this.font, "§f" + wallet.getSigils() + " / " + ResourceWallet.MAX_SIGILS + " §8max",
                textX + 10, textY, 0xFFFFFF);
        textY += 10;
        graphics.drawString(this.font, "§7Regenerates over time, used for actions", textX + 10, textY, 0x888888);
        textY += 20;

        // Seals (Non-transferable)
        graphics.drawString(this.font, "§e🔱 Seals (Privileges)", textX, textY, 0xFFFF55);
        textY += 12;
        var seals = wallet.getSeals();
        if (seals.isEmpty()) {
            graphics.drawString(this.font, "§8No seals earned yet", textX + 10, textY, 0x666666);
        } else {
            for (var seal : seals) {
                graphics.drawString(this.font, "§f• " + seal.getDisplayName(), textX + 10, textY, 0xFFFFFF);
                textY += 10;
            }
        }
        textY += 10;
        graphics.drawString(this.font, "§7Non-transferable role credentials", textX + 10, textY, 0x888888);
        textY += 20;

        // Wards (Protection)
        graphics.drawString(this.font, "§a🛡 Wards (Protection)", textX, textY, 0x55FF55);
        textY += 12;
        graphics.drawString(this.font, "§f" + wallet.getWards() + " / " + ResourceWallet.MAX_WARDS + " §8max",
                textX + 10, textY, 0xFFFFFF);
        textY += 10;
        graphics.drawString(this.font, "§7Protects definitions from tampering", textX + 10, textY, 0x888888);
        textY += 20;

        // Tokens (Transferable)
        graphics.drawString(this.font, "§d💎 Tokens (Tradeable)", textX, textY, 0xFF55FF);
        textY += 12;
        graphics.drawString(this.font, "§f" + wallet.getTokens() + " §8available", textX + 10, textY, 0xFFFFFF);
        textY += 10;
        graphics.drawString(this.font, "§7Can be transferred between players", textX + 10, textY, 0x888888);

        // Right column - Economy info
        textX = panelX + colWidth + 20;
        textY = panelY + 35;

        graphics.drawString(this.font, "§6§lEconomy Overview", textX, textY, 0xFFAA00);
        textY += 15;

        String[] economyInfo = {
                "§7Publishing an item: §c-5 Ash §7+ §b-2 Sigils",
                "§7Publishing a spell: §c-10 Ash §7+ §b-3 Sigils",
                "§7Adding a signature: §b-1 Sigil",
                "§7Creating manifest: §b-5 Sigils",
                "",
                "§e§lHow to earn resources:",
                "§7• Ash: Complete proof-of-work tasks",
                "§7• Sigils: Wait (regenerates every 5 min)",
                "§7• Seals: Earn through governance",
                "§7• Wards: Purchase with Ash or earn",
                "§7• Tokens: Trade with other players",
                "",
                "§a§lSybil Resistance:",
                "§7Resources prevent spam publishing.",
                "§7Ash requires real compute work.",
                "§7Sigils rate-limit actions.",
        };

        for (String line : economyInfo) {
            graphics.drawString(this.font, line, textX, textY, 0xFFFFFF);
            textY += 11;
        }
    }

    private void renderSyncView(GuiGraphics graphics) {
        int panelX = 20;
        int panelY = 90;
        int panelWidth = this.width - 40;
        int panelHeight = this.height - 140;

        drawPanel(graphics, panelX, panelY, panelWidth, panelHeight);

        SyncService syncService = SyncService.getInstance();
        SyncService.SyncStatus status = syncService.getStatus();

        int textX = panelX + 20;
        int textY = panelY + 15;

        // Title
        graphics.drawString(this.font, "§6§lSync Status", textX, textY, 0xFFAA00);
        textY += 20;

        // Connection status
        boolean wsConnected = status.wsConnected();
        String connStatus = wsConnected ? "§a● Connected" : "§c● Disconnected";
        graphics.drawString(this.font, "§7WebSocket: " + connStatus, textX, textY, 0xAAAAAA);
        textY += 15;

        // Server URLs
        graphics.drawString(this.font, "§7HTTP Server: §f" + syncService.getManifestServerUrl(), textX, textY,
                0xAAAAAA);
        textY += 12;
        graphics.drawString(this.font, "§7Manifest: §f" + syncService.getPrimaryManifestId(), textX, textY, 0xAAAAAA);
        textY += 20;

        // Sync status info
        String statusIcon = status.running() ? "§a✓" : "§e○";
        graphics.drawString(this.font, "§7Status: " + statusIcon + " §f" + status.format(),
                textX, textY, 0xAAAAAA);
        textY += 12;

        if (status.lastSyncTime() > 0) {
            graphics.drawString(this.font, "§8" + formatTimestamp(status.lastSyncTime()),
                    textX + 10, textY, 0x666666);
            textY += 12;
        } else {
            graphics.drawString(this.font, "§7Last Sync: §8Never synced", textX, textY, 0xAAAAAA);
            textY += 12;
        }
        textY += 10;

        // Registry stats
        DefinitionRegistry registry = DefinitionRegistry.getInstance();
        graphics.drawString(this.font, "§6§lLocal Registry", textX, textY, 0xFFAA00);
        textY += 15;

        int itemCount = registry.getByType(DefinitionType.ITEM).size();
        int spellCount = registry.getByType(DefinitionType.SPELL).size();
        int activeItems = registry.getActiveItems().size();
        int activeSpells = registry.getActiveSpells().size();

        graphics.drawString(this.font, "§7Items: §f" + itemCount + " §8(" + activeItems + " active)",
                textX, textY, 0xAAAAAA);
        textY += 12;
        graphics.drawString(this.font, "§7Spells: §f" + spellCount + " §8(" + activeSpells + " active)",
                textX, textY, 0xAAAAAA);
        textY += 12;

        // Current manifest
        textY += 10;
        graphics.drawString(this.font, "§6§lActive Manifest", textX, textY, 0xFFAA00);
        textY += 15;

        String currentManifest = syncService.getPrimaryManifestId();
        graphics.drawString(this.font, "§7• §f" + currentManifest, textX, textY, 0xAAAAAA);
        textY += 11;

        // Right side - sync details
        int rightX = panelX + panelWidth / 2 + 20;
        int rightY = panelY + 15;

        graphics.drawString(this.font, "§6§lSync Details", rightX, rightY, 0xFFAA00);
        rightY += 15;

        graphics.drawString(this.font, "§7Sync Version: §f" + status.syncVersion(), rightX, rightY, 0xAAAAAA);
        rightY += 12;
        graphics.drawString(this.font, "§7Registry Size: §f" + status.registrySize() + " entries", rightX, rightY,
                0xAAAAAA);
        rightY += 12;
        graphics.drawString(this.font, "§7Service: " + (status.running() ? "§aRunning" : "§cStopped"), rightX, rightY,
                0xAAAAAA);
        rightY += 12;
    }

    private String getStatusIcon(DefinitionStatus status) {
        return switch (status) {
            case DRAFT -> "§8◌";
            case PENDING -> "§e◐";
            case ACTIVE -> "§a●";
            case DEPRECATED -> "§c○";
        };
    }

    private String formatTimestamp(long timestamp) {
        long ago = System.currentTimeMillis() - timestamp;
        if (ago < 60000)
            return "just now";
        if (ago < 3600000)
            return (ago / 60000) + "m ago";
        if (ago < 86400000)
            return (ago / 3600000) + "h ago";
        return (ago / 86400000) + "d ago";
    }

    private void drawPanel(GuiGraphics graphics, int x, int y, int width, int height) {
        // Outer border
        graphics.fill(x - 2, y - 2, x + width + 2, y + height + 2, 0xFF3A3A3A);
        // Inner background
        graphics.fill(x, y, x + width, y + height, 0xEE1A1A1A);
        // Subtle highlight
        graphics.fill(x + 1, y + 1, x + width - 1, y + 2, 0x22FFFFFF);
        graphics.fill(x + 1, y + 1, x + 2, y + height - 1, 0x22FFFFFF);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Handle clicking on list items
        if ((currentTab == WorkshopTab.ITEMS || currentTab == WorkshopTab.SPELLS) && button == 0) {
            List<DefinitionEntry> entries = currentTab == WorkshopTab.ITEMS ? filteredItems : filteredSpells;

            int listX = 20;
            int listY = 90;
            int listWidth = selectedEntry != null ? this.width - SIDEBAR_WIDTH - 40 : this.width - 40;
            int listHeight = this.height - 140;

            int entryY = listY + 25;
            int maxVisible = (listHeight - 30) / ITEM_HEIGHT;
            int endIndex = Math.min(scrollOffset + maxVisible, entries.size());

            for (int i = scrollOffset; i < endIndex; i++) {
                if (mouseX >= listX + 5 && mouseX <= listX + listWidth - 5 &&
                        mouseY >= entryY && mouseY <= entryY + ITEM_HEIGHT - 2) {

                    DefinitionEntry clicked = entries.get(i);
                    if (clicked.equals(selectedEntry)) {
                        selectedEntry = null; // Deselect
                    } else {
                        selectedEntry = clicked;
                    }
                    rebuildUI();
                    return true;
                }
                entryY += ITEM_HEIGHT;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (currentTab == WorkshopTab.ITEMS || currentTab == WorkshopTab.SPELLS) {
            List<DefinitionEntry> entries = currentTab == WorkshopTab.ITEMS ? filteredItems : filteredSpells;
            int listHeight = this.height - 140;
            int maxVisible = (listHeight - 30) / ITEM_HEIGHT;
            int maxScroll = Math.max(0, entries.size() - maxVisible);

            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) scrollY * 2));
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { // Escape
            if (selectedEntry != null) {
                selectedEntry = null;
                rebuildUI();
                return true;
            }
            if (previousScreen != null) {
                Minecraft.getInstance().setScreen(previousScreen);
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
