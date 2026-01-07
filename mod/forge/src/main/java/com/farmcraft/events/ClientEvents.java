package com.farmcraft.events;

import com.farmcraft.client.ChatAssistant;
import com.farmcraft.config.FarmCraftConfig;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client-side event handlers
 */
@Mod.EventBusSubscriber(modid = "farmcraft", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ClientEvents {

    @SubscribeEvent
    public static void onClientChatReceived(ClientChatReceivedEvent event) {
        // Disabled chat AI feature - use /farmcraft commands instead
        // This was causing crashes due to config issues

        /*
         * TODO: Re-enable when config system is fixed
         * if (FarmCraftConfig.ENABLE_AI_CHAT == null ||
         * !FarmCraftConfig.ENABLE_AI_CHAT.get()) {
         * return;
         * }
         * 
         * String message = event.getMessage().getString();
         * 
         * // Extract player name and message content
         * // Format is usually: "<PlayerName> message"
         * if (message.startsWith("<") && message.contains(">")) {
         * int endBracket = message.indexOf(">");
         * String playerName = message.substring(1, endBracket);
         * String chatMessage = message.substring(endBracket + 2); // Skip "> "
         * 
         * // Pass to chat assistant for AI processing
         * ChatAssistant.handleChatMessage(chatMessage, playerName);
         * }
         */
    }
}
