package com.farmcraft.network;

import com.farmcraft.FarmCraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.event.network.CustomPayloadEvent;

/**
 * Packet for submitting a proof-of-work solution
 */
public class SolutionSubmitPacket {
    
    private final String challengeId;
    private final String solution;
    private final long computeTimeMs;
    private final boolean gpuUsed;
    
    public SolutionSubmitPacket(String challengeId, String solution, long computeTimeMs, boolean gpuUsed) {
        this.challengeId = challengeId;
        this.solution = solution;
        this.computeTimeMs = computeTimeMs;
        this.gpuUsed = gpuUsed;
    }
    
    public static void encode(SolutionSubmitPacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.challengeId);
        buf.writeUtf(packet.solution);
        buf.writeLong(packet.computeTimeMs);
        buf.writeBoolean(packet.gpuUsed);
    }
    
    public static SolutionSubmitPacket decode(FriendlyByteBuf buf) {
        return new SolutionSubmitPacket(
            buf.readUtf(),
            buf.readUtf(),
            buf.readLong(),
            buf.readBoolean()
        );
    }
    
    public static void handle(SolutionSubmitPacket packet, CustomPayloadEvent.Context ctx) {
        ctx.enqueueWork(() -> {
            var player = ctx.getSender();
            if (player != null) {
                FarmCraft.LOGGER.info("Received solution for challenge {} from {} ({}ms, gpu={})",
                    packet.challengeId, 
                    player.getName().getString(),
                    packet.computeTimeMs,
                    packet.gpuUsed);
                // TODO: Validate solution and send token update
            }
        });
        ctx.setPacketHandled(true);
    }
    
    public String getChallengeId() { return challengeId; }
    public String getSolution() { return solution; }
    public long getComputeTimeMs() { return computeTimeMs; }
    public boolean isGpuUsed() { return gpuUsed; }
}
