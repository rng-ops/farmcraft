package com.farmcraft.watermark;

import com.farmcraft.FarmCraft;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/**
 * Encodes game state into screenshot pixels using LSB steganography.
 * 
 * This allows screenshots to carry verifiable game state information
 * that can be extracted by ML models or other clients for consensus.
 */
public class WatermarkEncoder {

    private static final int VERSION = 1;
    private static final byte[] MAGIC_BYTES = { (byte) 0xFA, (byte) 0xC0, (byte) 0xDE };
    private static final Gson GSON = new GsonBuilder().create();

    /**
     * Encode watermark data into an image
     */
    public static BufferedImage encode(BufferedImage image, WatermarkData data) {
        int width = image.getWidth();
        int height = image.getHeight();

        // Clone image
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        result.getGraphics().drawImage(image, 0, 0, null);

        // Serialize data
        String json = GSON.toJson(data);
        byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);

        // Convert to bits
        List<Integer> bits = toBits(jsonBytes);

        // Embed magic bytes at fixed positions
        embedMagicBytes(result);

        // Embed data starting at pixel 100
        int bitIndex = 0;
        int dataStartOffset = 100;

        outer: for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixelIndex = y * width + x;
                if (pixelIndex < dataStartOffset)
                    continue;
                if (bitIndex >= bits.size())
                    break outer;

                int rgb = result.getRGB(x, y);
                int alpha = (rgb >> 24) & 0xFF;
                int red = (rgb >> 16) & 0xFF;
                int green = (rgb >> 8) & 0xFF;
                int blue = rgb & 0xFF;

                // Embed 2 bits per channel (R, G, B)
                if (bitIndex < bits.size()) {
                    int bit1 = bits.get(bitIndex++);
                    int bit2 = bitIndex < bits.size() ? bits.get(bitIndex++) : 0;
                    red = (red & 0xFC) | (bit1 << 1) | bit2;
                }

                if (bitIndex < bits.size()) {
                    int bit1 = bits.get(bitIndex++);
                    int bit2 = bitIndex < bits.size() ? bits.get(bitIndex++) : 0;
                    green = (green & 0xFC) | (bit1 << 1) | bit2;
                }

                if (bitIndex < bits.size()) {
                    int bit1 = bits.get(bitIndex++);
                    int bit2 = bitIndex < bits.size() ? bits.get(bitIndex++) : 0;
                    blue = (blue & 0xFC) | (bit1 << 1) | bit2;
                }

                result.setRGB(x, y, (alpha << 24) | (red << 16) | (green << 8) | blue);
            }
        }

        return result;
    }

    /**
     * Create watermark data from current game state
     */
    public static WatermarkData createFromGameState(String clientId, String sessionId, String chainHash,
            int eventSequence) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        Level level = mc.level;

        if (player == null || level == null) {
            return null;
        }

        // Build game state snapshot
        GameStateSnapshot gameState = new GameStateSnapshot();
        gameState.worldSeed = level.dimension().location().toString(); // Use dimension as seed proxy (client can't
                                                                       // access seed)
        gameState.playerPosition = new double[] {
                player.getX(),
                player.getY(),
                player.getZ()
        };
        gameState.playerInventoryHash = hashInventory(player);
        gameState.nearbyBlocksHash = hashNearbyBlocks(player, level);
        gameState.timeOfDay = level.getDayTime() % 24000;
        gameState.weather = level.isRaining() ? (level.isThundering() ? "thunder" : "rain") : "clear";
        gameState.activeFertilizers = new ArrayList<>();
        gameState.recentRecipesUsed = new ArrayList<>();

        // Build GPU fingerprint
        GPUFingerprint gpuFingerprint = GPUFingerprinter.generate();

        // Create watermark
        WatermarkData data = new WatermarkData();
        data.clientId = clientId;
        data.sessionId = sessionId;
        data.timestamp = System.currentTimeMillis();
        data.gameState = gameState;
        data.gpuFingerprint = gpuFingerprint;
        data.eventChainHash = chainHash;
        data.lastEventSequence = eventSequence;

        // Sign the data
        data.signature = signData(data);

        return data;
    }

    private static void embedMagicBytes(BufferedImage image) {
        int[] positions = { 0, 10, 20, 30, 40, 50, 60, 70, 80, 90 };
        int width = image.getWidth();

        for (int i = 0; i < positions.length; i++) {
            int pos = positions[i];
            int x = pos % width;
            int y = pos / width;

            byte magicByte = MAGIC_BYTES[i % MAGIC_BYTES.length];
            int xorValue = (magicByte & 0xFF) ^ (i * 17);
            int secondByte = ((magicByte >> 4) & 0x0F) | ((i & 0x0F) << 4);

            int rgb = image.getRGB(x, y);
            int alpha = (rgb >> 24) & 0xFF;
            int green = (rgb >> 8) & 0xFF;
            int blue = rgb & 0xFF;

            // Embed in red and green channels
            image.setRGB(x, y, (alpha << 24) | (xorValue << 16) | (secondByte << 8) | blue);
        }
    }

    private static List<Integer> toBits(byte[] data) {
        List<Integer> bits = new ArrayList<>();

        // Add length header (32 bits)
        int length = data.length;
        for (int i = 31; i >= 0; i--) {
            bits.add((length >> i) & 1);
        }

        // Add data bits
        for (byte b : data) {
            for (int i = 7; i >= 0; i--) {
                bits.add((b >> i) & 1);
            }
        }

        return bits;
    }

    private static String hashInventory(Player player) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            var stack = player.getInventory().getItem(i);
            if (!stack.isEmpty()) {
                sb.append(stack.getItem().toString()).append(":").append(stack.getCount()).append(",");
            }
        }
        return sha256(sb.toString()).substring(0, 16);
    }

    private static String hashNearbyBlocks(Player player, Level level) {
        StringBuilder sb = new StringBuilder();
        BlockPos pos = player.blockPosition();

        // Hash blocks in 5x5x5 cube around player
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos checkPos = pos.offset(dx, dy, dz);
                    sb.append(level.getBlockState(checkPos).getBlock().toString());
                }
            }
        }

        return sha256(sb.toString()).substring(0, 16);
    }

    private static String signData(WatermarkData data) {
        String content = data.clientId + data.timestamp + data.eventChainHash;
        return sha256(content + "farmcraft_watermark_key");
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
