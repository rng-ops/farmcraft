package com.farmcraft.watermark;

import com.farmcraft.FarmCraft;
import com.farmcraft.drm.DRMManager;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ScreenshotEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.UUID;

/**
 * Integrates watermarking with Minecraft's screenshot system.
 * 
 * When a screenshot is taken:
 * 1. Captures current game state
 * 2. Encodes state into screenshot pixels
 * 3. Saves watermarked screenshot
 * 
 * This allows screenshots to carry verifiable game state
 * for consensus verification between players.
 */
@Mod.EventBusSubscriber(modid = FarmCraft.MOD_ID, value = Dist.CLIENT)
@OnlyIn(Dist.CLIENT)
public class WatermarkIntegration {

    private static boolean enabled = true;
    private static String sessionId = UUID.randomUUID().toString();

    /**
     * Handle screenshot event to embed watermark
     */
    @SubscribeEvent
    public static void onScreenshot(ScreenshotEvent event) {
        if (!enabled)
            return;

        try {
            NativeImage nativeImage = event.getImage();

            // Convert NativeImage to BufferedImage
            BufferedImage bufferedImage = nativeImageToBuffered(nativeImage);

            // Get DRM state
            DRMManager drm = DRMManager.getInstance();
            String chainHash = "0".repeat(64); // Would get from DRM client
            int eventSequence = 0;

            // Create watermark data
            WatermarkData data = WatermarkEncoder.createFromGameState(
                    getClientId(),
                    sessionId,
                    chainHash,
                    eventSequence);

            if (data != null) {
                // Encode watermark
                BufferedImage watermarked = WatermarkEncoder.encode(bufferedImage, data);

                // Convert back to NativeImage
                NativeImage watermarkedNative = bufferedToNativeImage(watermarked);

                // Replace the event's image
                // Note: This requires reflection or mixin in actual implementation
                // For now, we'll save separately
                saveWatermarkedScreenshot(watermarked, event.getScreenshotFile());

                FarmCraft.LOGGER.info("Screenshot watermarked with state: world={}, chain={}",
                        data.gameState.worldSeed.substring(0, 8),
                        data.eventChainHash.substring(0, 8));
            }

        } catch (Exception e) {
            FarmCraft.LOGGER.error("Failed to watermark screenshot", e);
        }
    }

    /**
     * Verify a screenshot's watermark
     */
    public static WatermarkDecoder.DecodeResult verifyScreenshot(File screenshotFile) {
        try {
            BufferedImage image = ImageIO.read(screenshotFile);
            return WatermarkDecoder.decode(image);
        } catch (IOException e) {
            return WatermarkDecoder.DecodeResult.failure("Failed to read image: " + e.getMessage());
        }
    }

    /**
     * Compare two screenshots for consensus
     */
    public static WatermarkDecoder.ConsensusResult compareScreenshots(File file1, File file2) {
        try {
            BufferedImage image1 = ImageIO.read(file1);
            BufferedImage image2 = ImageIO.read(file2);
            return WatermarkDecoder.compareForConsensus(image1, image2);
        } catch (IOException e) {
            WatermarkDecoder.ConsensusResult result = new WatermarkDecoder.ConsensusResult();
            result.match = false;
            result.error = "Failed to read images: " + e.getMessage();
            return result;
        }
    }

    /**
     * Enable or disable watermarking
     */
    public static void setEnabled(boolean enabled) {
        WatermarkIntegration.enabled = enabled;
    }

    /**
     * Check if watermarking is enabled
     */
    public static boolean isEnabled() {
        return enabled;
    }

    private static String getClientId() {
        // Get from DRM or generate
        return Minecraft.getInstance().getUser().getProfileId().toString();
    }

    private static BufferedImage nativeImageToBuffered(NativeImage nativeImage) {
        int width = nativeImage.getWidth();
        int height = nativeImage.getHeight();
        BufferedImage buffered = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = nativeImage.getPixelRGBA(x, y);
                // NativeImage uses ABGR, convert to ARGB
                int a = (pixel >> 24) & 0xFF;
                int b = (pixel >> 16) & 0xFF;
                int g = (pixel >> 8) & 0xFF;
                int r = pixel & 0xFF;
                buffered.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
            }
        }

        return buffered;
    }

    private static NativeImage bufferedToNativeImage(BufferedImage buffered) {
        int width = buffered.getWidth();
        int height = buffered.getHeight();
        NativeImage nativeImage = new NativeImage(width, height, false);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = buffered.getRGB(x, y);
                // Convert ARGB to ABGR
                int a = (argb >> 24) & 0xFF;
                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;
                nativeImage.setPixelRGBA(x, y, (a << 24) | (b << 16) | (g << 8) | r);
            }
        }

        return nativeImage;
    }

    private static void saveWatermarkedScreenshot(BufferedImage image, File originalFile) {
        try {
            // Save alongside original with _verified suffix
            String name = originalFile.getName();
            String baseName = name.substring(0, name.lastIndexOf('.'));
            String extension = name.substring(name.lastIndexOf('.'));
            File watermarkedFile = new File(originalFile.getParent(), baseName + "_verified" + extension);

            ImageIO.write(image, "PNG", watermarkedFile);

        } catch (IOException e) {
            FarmCraft.LOGGER.error("Failed to save watermarked screenshot", e);
        }
    }
}
