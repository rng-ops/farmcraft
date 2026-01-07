package com.farmcraft.network;

import com.farmcraft.FarmCraft;
import com.farmcraft.client.ProofOfWorkClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.event.network.CustomPayloadEvent;

/**
 * Packet containing a proof-of-work challenge from the server
 */
public class ChallengeResponsePacket {
    
    private final String challengeId;
    private final String type;
    private final int difficulty;
    private final String payload;
    private final long expiresAt;
    private final int rewardTokens;
    
    public ChallengeResponsePacket(String challengeId, String type, int difficulty,
                                    String payload, long expiresAt, int rewardTokens) {
        this.challengeId = challengeId;
        this.type = type;
        this.difficulty = difficulty;
        this.payload = payload;
        this.expiresAt = expiresAt;
        this.rewardTokens = rewardTokens;
    }
    
    public static void encode(ChallengeResponsePacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.challengeId);
        buf.writeUtf(packet.type);
        buf.writeInt(packet.difficulty);
        buf.writeUtf(packet.payload);
        buf.writeLong(packet.expiresAt);
        buf.writeInt(packet.rewardTokens);
    }
    
    public static ChallengeResponsePacket decode(FriendlyByteBuf buf) {
        return new ChallengeResponsePacket(
            buf.readUtf(),
            buf.readUtf(),
            buf.readInt(),
            buf.readUtf(),
            buf.readLong(),
            buf.readInt()
        );
    }
    
    public static void handle(ChallengeResponsePacket packet, CustomPayloadEvent.Context ctx) {
        ctx.enqueueWork(() -> {
            FarmCraft.LOGGER.info("Received challenge {} (type={}, difficulty={})", 
                packet.challengeId, packet.type, packet.difficulty);
            
            // Start solving the challenge
            ProofOfWorkClient.getInstance().startChallenge(
                packet.challengeId,
                packet.type,
                packet.difficulty,
                packet.payload,
                packet.expiresAt,
                packet.rewardTokens
            );
        });
        ctx.setPacketHandled(true);
    }
    
    public String getChallengeId() { return challengeId; }
    public String getType() { return type; }
    public int getDifficulty() { return difficulty; }
    public String getPayload() { return payload; }
    public long getExpiresAt() { return expiresAt; }
    public int getRewardTokens() { return rewardTokens; }
}
