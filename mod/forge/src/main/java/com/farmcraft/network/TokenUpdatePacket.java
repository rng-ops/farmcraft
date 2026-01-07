package com.farmcraft.network;

import com.farmcraft.FarmCraft;
import com.farmcraft.client.TokenManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.event.network.CustomPayloadEvent;

/**
 * Packet for updating the client's token balance
 */
public class TokenUpdatePacket {
    
    private final String tokenId;
    private final int credits;
    private final long expiresAt;
    private final boolean solutionAccepted;
    private final String message;
    
    public TokenUpdatePacket(String tokenId, int credits, long expiresAt, 
                             boolean solutionAccepted, String message) {
        this.tokenId = tokenId;
        this.credits = credits;
        this.expiresAt = expiresAt;
        this.solutionAccepted = solutionAccepted;
        this.message = message;
    }
    
    public static void encode(TokenUpdatePacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.tokenId);
        buf.writeInt(packet.credits);
        buf.writeLong(packet.expiresAt);
        buf.writeBoolean(packet.solutionAccepted);
        buf.writeUtf(packet.message != null ? packet.message : "");
    }
    
    public static TokenUpdatePacket decode(FriendlyByteBuf buf) {
        return new TokenUpdatePacket(
            buf.readUtf(),
            buf.readInt(),
            buf.readLong(),
            buf.readBoolean(),
            buf.readUtf()
        );
    }
    
    public static void handle(TokenUpdatePacket packet, CustomPayloadEvent.Context ctx) {
        ctx.enqueueWork(() -> {
            FarmCraft.LOGGER.info("Token update: {} credits, accepted={}", 
                packet.credits, packet.solutionAccepted);
            
            TokenManager.getInstance().updateToken(
                packet.tokenId,
                packet.credits,
                packet.expiresAt
            );
            
            if (!packet.message.isEmpty()) {
                // Display message to player
                FarmCraft.LOGGER.info("Server message: {}", packet.message);
            }
        });
        ctx.setPacketHandled(true);
    }
    
    public String getTokenId() { return tokenId; }
    public int getCredits() { return credits; }
    public long getExpiresAt() { return expiresAt; }
    public boolean isSolutionAccepted() { return solutionAccepted; }
    public String getMessage() { return message; }
}
