package com.farmcraft;

import com.farmcraft.commands.FarmCraftCommand;
import com.farmcraft.config.FarmCraftConfig;
import com.mojang.logging.LogUtils;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

@Mod(FarmCraft.MOD_ID)
public class FarmCraft {
    public static final String MOD_ID = "farmcraft";
    public static final Logger LOGGER = LogUtils.getLogger();

    public FarmCraft() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::commonSetup);
        MinecraftForge.EVENT_BUS.register(this);

        // Register config
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, FarmCraftConfig.SPEC);

        LOGGER.info("FarmCraft initialized!");
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("FarmCraft common setup starting...");

        // Initialize DRM system (client-side only)
        if (FMLEnvironment.dist == Dist.CLIENT) {
            event.enqueueWork(() -> {
                if (FarmCraftConfig.AUTO_CONNECT.get()) {
                    LOGGER.info("Auto-connecting to recipe server at {}:{}",
                            FarmCraftConfig.RECIPE_SERVER_URL.get(),
                            FarmCraftConfig.RECIPE_SERVER_PORT.get());

                    try {
                        // Import DRMManager here to avoid class loading on server
                        com.farmcraft.drm.DRMManager.getInstance().connect().thenAccept(success -> {
                            if (success) {
                                LOGGER.info("Successfully connected to recipe server!");
                            } else {
                                LOGGER.warn("Failed to connect to recipe server");
                            }
                        });
                    } catch (Exception e) {
                        LOGGER.error("Failed to initialize DRM system", e);
                    }
                }
            });
        }

        LOGGER.info("FarmCraft common setup complete");
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        LOGGER.info("Registering FarmCraft commands...");
        // Register commands for both client and integrated server
        FarmCraftCommand.register(event.getDispatcher());
    }
}
