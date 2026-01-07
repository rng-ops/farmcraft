package com.farmcraft.init;

import com.farmcraft.FarmCraft;
import com.farmcraft.item.FertilizerItem;
import com.farmcraft.item.PowerFoodItem;
import com.farmcraft.item.FertilizerSpreaderItem;
import com.farmcraft.item.CropAnalyzerItem;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Registry for all FarmCraft items
 */
public class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, FarmCraft.MOD_ID);

    // ========== Base Fertilizers ==========

    public static final RegistryObject<Item> STONE_DUST_FERTILIZER = ITEMS.register(
            "stone_dust_fertilizer",
            () -> new FertilizerItem(new Item.Properties(), FertilizerItem.FertilizerTier.BASIC, 0x999999));

    public static final RegistryObject<Item> CALCIUM_MIX_FERTILIZER = ITEMS.register(
            "calcium_mix_fertilizer",
            () -> new FertilizerItem(new Item.Properties(), FertilizerItem.FertilizerTier.BASIC, 0xE8E8E8));

    public static final RegistryObject<Item> MINERAL_BLEND_FERTILIZER = ITEMS.register(
            "mineral_blend_fertilizer",
            () -> new FertilizerItem(new Item.Properties(), FertilizerItem.FertilizerTier.BASIC, 0x6B5B4F));

    public static final RegistryObject<Item> GRAVEL_GRIT_FERTILIZER = ITEMS.register(
            "gravel_grit_fertilizer",
            () -> new FertilizerItem(new Item.Properties(), FertilizerItem.FertilizerTier.BASIC, 0x7D7D7D));

    // ========== Enhanced Fertilizers ==========

    public static final RegistryObject<Item> ENHANCED_STONE_FERTILIZER = ITEMS.register(
            "enhanced_stone_fertilizer",
            () -> new FertilizerItem(new Item.Properties(), FertilizerItem.FertilizerTier.ENHANCED, 0xCCCC00));

    public static final RegistryObject<Item> ENHANCED_MINERAL_FERTILIZER = ITEMS.register(
            "enhanced_mineral_fertilizer",
            () -> new FertilizerItem(new Item.Properties(), FertilizerItem.FertilizerTier.ENHANCED, 0x9966FF));

    // ========== Superior Fertilizers ==========

    public static final RegistryObject<Item> SUPERIOR_BLEND_FERTILIZER = ITEMS.register(
            "superior_blend_fertilizer",
            () -> new FertilizerItem(new Item.Properties(), FertilizerItem.FertilizerTier.SUPERIOR, 0xFF6600));

    // ========== Power Foods ========== (Temporarily disabled - FoodProperties API
    // needs fixing for 1.20.4)

    /*
     * TODO: Fix FoodProperties.Builder API for 1.20.4
     * public static final RegistryObject<Item> SPEED_CARROT = ITEMS.register(
     * "speed_carrot",
     * () -> new Item(new Item.Properties())
     * );
     * 
     * public static final RegistryObject<Item> STRENGTH_POTATO = ITEMS.register(
     * "strength_potato",
     * () -> new Item(new Item.Properties())
     * );
     * 
     * public static final RegistryObject<Item> RESISTANCE_BEET = ITEMS.register(
     * "resistance_beet",
     * () -> new Item(new Item.Properties())
     * );
     * 
     * public static final RegistryObject<Item> NIGHT_VISION_BREAD = ITEMS.register(
     * "night_vision_bread",
     * () -> new Item(new Item.Properties())
     * );
     * 
     * public static final RegistryObject<Item> SUPER_SPEED_CARROT = ITEMS.register(
     * "super_speed_carrot",
     * () -> new Item(new Item.Properties())
     * );
     * 
     * public static final RegistryObject<Item> REGENERATION_APPLE = ITEMS.register(
     * "regeneration_apple",
     * () -> new Item(new Item.Properties())
     * );
     */

    // ========== Tools ==========

    public static final RegistryObject<Item> FERTILIZER_SPREADER = ITEMS.register(
            "fertilizer_spreader",
            () -> new FertilizerSpreaderItem(new Item.Properties()));

    public static final RegistryObject<Item> CROP_ANALYZER = ITEMS.register(
            "crop_analyzer",
            () -> new CropAnalyzerItem(new Item.Properties()));

    // ========== Upgrades ==========

    public static final RegistryObject<Item> SPREADER_RANGE_UPGRADE = ITEMS.register(
            "spreader_range_upgrade",
            () -> new Item(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> ANALYZER_PRECISION_UPGRADE = ITEMS.register(
            "analyzer_precision_upgrade",
            () -> new Item(new Item.Properties().stacksTo(1)));

    // ========== Special ==========

    public static final RegistryObject<Item> MUTATION_SERUM = ITEMS.register(
            "mutation_serum",
            () -> new Item(new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.EPIC)));

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
