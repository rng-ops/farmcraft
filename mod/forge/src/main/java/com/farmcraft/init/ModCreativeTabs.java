package com.farmcraft.init;

import com.farmcraft.FarmCraft;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Registry for FarmCraft creative tabs
 */
@Mod.EventBusSubscriber(modid = FarmCraft.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ModCreativeTabs {

    @SubscribeEvent
    public static void buildContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            // Fertilizers
            event.accept(ModItems.STONE_DUST_FERTILIZER.get());
            event.accept(ModItems.CALCIUM_MIX_FERTILIZER.get());
            event.accept(ModItems.MINERAL_BLEND_FERTILIZER.get());
            event.accept(ModItems.GRAVEL_GRIT_FERTILIZER.get());
            event.accept(ModItems.ENHANCED_STONE_FERTILIZER.get());
            event.accept(ModItems.ENHANCED_MINERAL_FERTILIZER.get());
            event.accept(ModItems.SUPERIOR_BLEND_FERTILIZER.get());
        }

        /*
         * Power Foods temporarily disabled - will re-enable after fixing FoodProperties
         * API
         * if (event.getTabKey() == CreativeModeTabs.FOOD_AND_DRINKS) {
         * event.accept(ModItems.SPEED_CARROT.get());
         * event.accept(ModItems.STRENGTH_POTATO.get());
         * event.accept(ModItems.RESISTANCE_BEET.get());
         * event.accept(ModItems.NIGHT_VISION_BREAD.get());
         * event.accept(ModItems.SUPER_SPEED_CARROT.get());
         * event.accept(ModItems.REGENERATION_APPLE.get());
         * }
         */

        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            // Tools
            event.accept(ModItems.FERTILIZER_SPREADER.get());
            event.accept(ModItems.CROP_ANALYZER.get());

            // Upgrades
            event.accept(ModItems.SPREADER_RANGE_UPGRADE.get());
            event.accept(ModItems.ANALYZER_PRECISION_UPGRADE.get());

            // Special
            event.accept(ModItems.MUTATION_SERUM.get());
        }

        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            // Blocks
            event.accept(ModBlocks.STONE_FERTILIZED_FARMLAND.get());
            event.accept(ModBlocks.CALCIUM_FERTILIZED_FARMLAND.get());
            event.accept(ModBlocks.MINERAL_FERTILIZED_FARMLAND.get());
            event.accept(ModBlocks.GRAVEL_FERTILIZED_FARMLAND.get());
            event.accept(ModBlocks.ENHANCED_STONE_FARMLAND.get());
            event.accept(ModBlocks.ENHANCED_MINERAL_FARMLAND.get());
            event.accept(ModBlocks.SUPERIOR_BLEND_FARMLAND.get());
        }
    }

    public static void register(IEventBus eventBus) {
        // No deferred register needed - using event subscriber
    }
}
