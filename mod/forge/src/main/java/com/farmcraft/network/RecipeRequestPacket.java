package com.farmcraft.network;

import com.farmcraft.FarmCraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.event.network.CustomPayloadEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Packet for requesting recipes from the server
 */
public class RecipeRequestPacket {
    
    private final List<String> categories;
    private final String tokenId;
    
    public RecipeRequestPacket(List<String> categories, String tokenId) {
        this.categories = categories;
        this.tokenId = tokenId;
    }
    
    public static void encode(RecipeRequestPacket packet, FriendlyByteBuf buf) {
        buf.writeInt(packet.categories.size());
        for (String category : packet.categories) {
            buf.writeUtf(category);
        }
        buf.writeUtf(packet.tokenId != null ? packet.tokenId : "");
    }
    
    public static RecipeRequestPacket decode(FriendlyByteBuf buf) {
        int size = buf.readInt();
        List<String> categories = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            categories.add(buf.readUtf());
        }
        String tokenId = buf.readUtf();
        return new RecipeRequestPacket(categories, tokenId.isEmpty() ? null : tokenId);
    }
    
    public static void handle(RecipeRequestPacket packet, CustomPayloadEvent.Context ctx) {
        ctx.enqueueWork(() -> {
            // Handle on server side
            var player = ctx.getSender();
            if (player != null) {
                FarmCraft.LOGGER.debug("Received recipe request from {}", player.getName().getString());
                // TODO: Process recipe request and send response
            }
        });
        ctx.setPacketHandled(true);
    }
    
    public List<String> getCategories() {
        return categories;
    }
    
    public String getTokenId() {
        return tokenId;
    }
}
