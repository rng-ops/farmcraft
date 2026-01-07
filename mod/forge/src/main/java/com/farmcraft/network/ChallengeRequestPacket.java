package com.farmcraft.network;

import com.farmcraft.FarmCraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.event.network.CustomPayloadEvent;

/**
 * Packet for requesting a proof-of-work challenge
 */
public class ChallengeRequestPacket {
    
    private final String preferredType;
    private final int maxDifficulty;
    
    public ChallengeRequestPacket(String preferredType, int maxDifficulty) {
        this.preferredType = preferredType;
        this.maxDifficulty = maxDifficulty;
    }
    
    public static void encode(ChallengeRequestPacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.preferredType != null ? packet.preferredType : "");
        buf.writeInt(packet.maxDifficulty);
    }
    
    public static ChallengeRequestPacket decode(FriendlyByteBuf buf) {
        String type = buf.readUtf();
        int difficulty = buf.readInt();
        return new ChallengeRequestPacket(type.isEmpty() ? null : type, difficulty);
    }
    
    public static void handle(ChallengeRequestPacket packet, CustomPayloadEvent.Context ctx) {
        ctx.enqueueWork(() -> {
            var player = ctx.getSender();
            if (player != null) {
                FarmCraft.LOGGER.debug("Received challenge request from {}", player.getName().getString());
                // TODO: Generate and send challenge
            }
        });
        ctx.setPacketHandled(true);
    }
}
