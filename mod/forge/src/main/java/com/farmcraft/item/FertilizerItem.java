package com.farmcraft.item;

import com.farmcraft.FarmCraft;
import com.farmcraft.init.ModBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Fertilizer item that can be applied to farmland to create fertilized farmland
 */
public class FertilizerItem extends Item {
    
    public enum FertilizerTier {
        BASIC("basic", 1.0f),
        ENHANCED("enhanced", 1.5f),
        SUPERIOR("superior", 2.0f),
        LEGENDARY("legendary", 3.0f);
        
        private final String name;
        private final float effectMultiplier;
        
        FertilizerTier(String name, float effectMultiplier) {
            this.name = name;
            this.effectMultiplier = effectMultiplier;
        }
        
        public String getName() {
            return name;
        }
        
        public float getEffectMultiplier() {
            return effectMultiplier;
        }
    }
    
    private final FertilizerTier tier;
    private final int particleColor;
    
    public FertilizerItem(Properties properties, FertilizerTier tier, int particleColor) {
        super(properties);
        this.tier = tier;
        this.particleColor = particleColor;
    }
    
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        
        // Check if clicking on farmland
        if (state.is(Blocks.FARMLAND)) {
            if (!level.isClientSide) {
                // Convert to fertilized farmland based on fertilizer type
                BlockState newState = getFertilizedFarmland();
                level.setBlockAndUpdate(pos, newState);
                
                // Play sound
                level.playSound(null, pos, SoundEvents.BONE_MEAL_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
                
                // Spawn particles
                if (level instanceof ServerLevel serverLevel) {
                    double x = pos.getX() + 0.5;
                    double y = pos.getY() + 1.0;
                    double z = pos.getZ() + 0.5;
                    
                    for (int i = 0; i < 10; i++) {
                        double offsetX = (level.random.nextDouble() - 0.5) * 0.5;
                        double offsetZ = (level.random.nextDouble() - 0.5) * 0.5;
                        serverLevel.sendParticles(
                            ParticleTypes.HAPPY_VILLAGER,
                            x + offsetX, y, z + offsetZ,
                            1, 0, 0.1, 0, 0.1
                        );
                    }
                }
                
                // Consume item
                context.getItemInHand().shrink(1);
                
                FarmCraft.LOGGER.debug("Applied {} fertilizer at {}", tier.getName(), pos);
            }
            
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        
        return InteractionResult.PASS;
    }
    
    private BlockState getFertilizedFarmland() {
        return switch (this.getDescriptionId()) {
            case "item.farmcraft.stone_dust_fertilizer" -> ModBlocks.STONE_FERTILIZED_FARMLAND.get().defaultBlockState();
            case "item.farmcraft.calcium_mix_fertilizer" -> ModBlocks.CALCIUM_FERTILIZED_FARMLAND.get().defaultBlockState();
            case "item.farmcraft.mineral_blend_fertilizer" -> ModBlocks.MINERAL_FERTILIZED_FARMLAND.get().defaultBlockState();
            case "item.farmcraft.gravel_grit_fertilizer" -> ModBlocks.GRAVEL_FERTILIZED_FARMLAND.get().defaultBlockState();
            case "item.farmcraft.enhanced_stone_fertilizer" -> ModBlocks.ENHANCED_STONE_FARMLAND.get().defaultBlockState();
            case "item.farmcraft.enhanced_mineral_fertilizer" -> ModBlocks.ENHANCED_MINERAL_FARMLAND.get().defaultBlockState();
            case "item.farmcraft.superior_blend_fertilizer" -> ModBlocks.SUPERIOR_BLEND_FARMLAND.get().defaultBlockState();
            default -> ModBlocks.STONE_FERTILIZED_FARMLAND.get().defaultBlockState();
        };
    }
    
    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.farmcraft.fertilizer.tier", tier.getName())
            .withStyle(getTierColor()));
        tooltip.add(Component.translatable("tooltip.farmcraft.fertilizer.effect", 
            String.format("%.1fx", tier.getEffectMultiplier()))
            .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.farmcraft.fertilizer.usage")
            .withStyle(ChatFormatting.DARK_GRAY));
    }
    
    private ChatFormatting getTierColor() {
        return switch (tier) {
            case BASIC -> ChatFormatting.WHITE;
            case ENHANCED -> ChatFormatting.YELLOW;
            case SUPERIOR -> ChatFormatting.GOLD;
            case LEGENDARY -> ChatFormatting.LIGHT_PURPLE;
        };
    }
    
    public FertilizerTier getTier() {
        return tier;
    }
    
    public int getParticleColor() {
        return particleColor;
    }
}
