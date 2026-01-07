package com.farmcraft.network;

import com.farmcraft.FarmCraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.SimpleChannel;

/**
 * Network handler for FarmCraft mod-server communication
 */
public class ModNetworking {
    
    private static final int PROTOCOL_VERSION = 1;
    
    public static final SimpleChannel CHANNEL = ChannelBuilder
        .named(new ResourceLocation(FarmCraft.MOD_ID, "main"))
        .networkProtocolVersion(PROTOCOL_VERSION)
        .clientAcceptedVersions((status, version) -> true)
        .serverAcceptedVersions((status, version) -> true)
        .simpleChannel();
    
    private static int packetId = 0;
    
    private static int nextId() {
        return packetId++;
    }
    
    public static void register() {
        // Register packets
        CHANNEL.messageBuilder(RecipeRequestPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .decoder(RecipeRequestPacket::decode)
            .encoder(RecipeRequestPacket::encode)
            .consumerMainThread(RecipeRequestPacket::handle)
            .add();
        
        CHANNEL.messageBuilder(RecipeResponsePacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
            .decoder(RecipeResponsePacket::decode)
            .encoder(RecipeResponsePacket::encode)
            .consumerMainThread(RecipeResponsePacket::handle)
            .add();
        
        CHANNEL.messageBuilder(ChallengeRequestPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .decoder(ChallengeRequestPacket::decode)
            .encoder(ChallengeRequestPacket::encode)
            .consumerMainThread(ChallengeRequestPacket::handle)
            .add();
        
        CHANNEL.messageBuilder(ChallengeResponsePacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
            .decoder(ChallengeResponsePacket::decode)
            .encoder(ChallengeResponsePacket::encode)
            .consumerMainThread(ChallengeResponsePacket::handle)
            .add();
        
        CHANNEL.messageBuilder(SolutionSubmitPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .decoder(SolutionSubmitPacket::decode)
            .encoder(SolutionSubmitPacket::encode)
            .consumerMainThread(SolutionSubmitPacket::handle)
            .add();
        
        CHANNEL.messageBuilder(TokenUpdatePacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
            .decoder(TokenUpdatePacket::decode)
            .encoder(TokenUpdatePacket::encode)
            .consumerMainThread(TokenUpdatePacket::handle)
            .add();
        
        FarmCraft.LOGGER.info("FarmCraft networking registered {} packets", packetId);
    }
    
    public static <T> void sendToServer(T packet) {
        CHANNEL.send(packet, PacketDistributor.SERVER.noArg());
    }
    
    public static <T> void sendToPlayer(T packet, net.minecraft.server.level.ServerPlayer player) {
        CHANNEL.send(packet, PacketDistributor.PLAYER.with(player));
    }
    
    public static <T> void sendToAllPlayers(T packet) {
        CHANNEL.send(packet, PacketDistributor.ALL.noArg());
    }
}
