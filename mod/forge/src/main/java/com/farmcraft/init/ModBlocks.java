package com.farmcraft.init;

import com.farmcraft.FarmCraft;
import com.farmcraft.block.FertilizedFarmlandBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Supplier;

/**
 * Registry for all FarmCraft blocks
 */
public class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = 
        DeferredRegister.create(ForgeRegistries.BLOCKS, FarmCraft.MOD_ID);

    // ========== Fertilized Farmland Variants ==========
    
    public static final RegistryObject<Block> STONE_FERTILIZED_FARMLAND = registerBlock(
        "stone_fertilized_farmland",
        () -> new FertilizedFarmlandBlock(
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.DIRT)
                .strength(0.6F)
                .randomTicks(),
            FertilizedFarmlandBlock.FertilizerType.STONE_DUST
        )
    );
    
    public static final RegistryObject<Block> CALCIUM_FERTILIZED_FARMLAND = registerBlock(
        "calcium_fertilized_farmland",
        () -> new FertilizedFarmlandBlock(
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.DIRT)
                .strength(0.6F)
                .randomTicks(),
            FertilizedFarmlandBlock.FertilizerType.CALCIUM_MIX
        )
    );
    
    public static final RegistryObject<Block> MINERAL_FERTILIZED_FARMLAND = registerBlock(
        "mineral_fertilized_farmland",
        () -> new FertilizedFarmlandBlock(
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.DIRT)
                .strength(0.6F)
                .randomTicks(),
            FertilizedFarmlandBlock.FertilizerType.MINERAL_BLEND
        )
    );
    
    public static final RegistryObject<Block> GRAVEL_FERTILIZED_FARMLAND = registerBlock(
        "gravel_fertilized_farmland",
        () -> new FertilizedFarmlandBlock(
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.DIRT)
                .strength(0.6F)
                .randomTicks(),
            FertilizedFarmlandBlock.FertilizerType.GRAVEL_GRIT
        )
    );

    // ========== Enhanced Fertilized Farmland ==========
    
    public static final RegistryObject<Block> ENHANCED_STONE_FARMLAND = registerBlock(
        "enhanced_stone_farmland",
        () -> new FertilizedFarmlandBlock(
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.DIRT)
                .strength(0.6F)
                .randomTicks()
                .lightLevel(state -> 3),
            FertilizedFarmlandBlock.FertilizerType.ENHANCED_STONE
        )
    );
    
    public static final RegistryObject<Block> ENHANCED_MINERAL_FARMLAND = registerBlock(
        "enhanced_mineral_farmland",
        () -> new FertilizedFarmlandBlock(
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.DIRT)
                .strength(0.6F)
                .randomTicks()
                .lightLevel(state -> 5),
            FertilizedFarmlandBlock.FertilizerType.ENHANCED_MINERAL
        )
    );

    // ========== Superior Fertilized Farmland ==========
    
    public static final RegistryObject<Block> SUPERIOR_BLEND_FARMLAND = registerBlock(
        "superior_blend_farmland",
        () -> new FertilizedFarmlandBlock(
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.DIRT)
                .strength(0.6F)
                .randomTicks()
                .lightLevel(state -> 7),
            FertilizedFarmlandBlock.FertilizerType.SUPERIOR_BLEND
        )
    );

    // Helper method to register block with item
    private static <T extends Block> RegistryObject<T> registerBlock(String name, Supplier<T> block) {
        RegistryObject<T> registeredBlock = BLOCKS.register(name, block);
        registerBlockItem(name, registeredBlock);
        return registeredBlock;
    }

    private static <T extends Block> void registerBlockItem(String name, RegistryObject<T> block) {
        ModItems.ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
    }

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }
}
