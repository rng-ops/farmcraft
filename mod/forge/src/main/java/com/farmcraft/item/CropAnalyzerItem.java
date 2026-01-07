package com.farmcraft.item;

import com.farmcraft.block.FertilizedFarmlandBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Tool for analyzing crops and farmland to show detailed information
 */
public class CropAnalyzerItem extends Item {
    
    public CropAnalyzerItem(Properties properties) {
        super(properties);
    }
    
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        
        if (player == null) return InteractionResult.PASS;
        
        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();
        
        if (level.isClientSide) {
            // Display information to the player
            if (block instanceof CropBlock crop) {
                displayCropInfo(player, crop, state, pos, level);
            } else if (block instanceof FertilizedFarmlandBlock farmland) {
                displayFarmlandInfo(player, farmland, state, pos);
            } else {
                player.displayClientMessage(
                    Component.translatable("message.farmcraft.analyzer.not_analyzable")
                        .withStyle(ChatFormatting.YELLOW),
                    true
                );
                return InteractionResult.PASS;
            }
            
            // Damage the analyzer
            context.getItemInHand().hurtAndBreak(1, player, (p) -> 
                p.broadcastBreakEvent(context.getHand()));
        }
        
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    
    private void displayCropInfo(Player player, CropBlock crop, BlockState state, BlockPos pos, Level level) {
        int age = crop.getAge(state);
        int maxAge = crop.getMaxAge();
        float growthPercent = (float) age / maxAge * 100;
        
        player.displayClientMessage(Component.literal("=== Crop Analysis ===")
            .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), false);
        
        player.displayClientMessage(Component.translatable("message.farmcraft.analyzer.crop_type", 
            crop.getName().getString())
            .withStyle(ChatFormatting.WHITE), false);
        
        player.displayClientMessage(Component.translatable("message.farmcraft.analyzer.growth",
            String.format("%.1f%%", growthPercent), age, maxAge)
            .withStyle(ChatFormatting.YELLOW), false);
        
        // Check farmland below
        BlockState farmlandState = level.getBlockState(pos.below());
        if (farmlandState.getBlock() instanceof FertilizedFarmlandBlock fertilizedFarmland) {
            FertilizedFarmlandBlock.FertilizerType type = fertilizedFarmland.getFertilizerType();
            player.displayClientMessage(Component.translatable("message.farmcraft.analyzer.fertilizer",
                type.getName())
                .withStyle(ChatFormatting.AQUA), false);
            
            player.displayClientMessage(Component.translatable("message.farmcraft.analyzer.effect",
                type.getEffectDescription())
                .withStyle(ChatFormatting.GRAY), false);
        }
        
        // Estimated time to harvest
        int ticksRemaining = (maxAge - age) * 1200; // Rough estimate
        int minutesRemaining = ticksRemaining / 20 / 60;
        player.displayClientMessage(Component.translatable("message.farmcraft.analyzer.time_remaining",
            minutesRemaining)
            .withStyle(ChatFormatting.GRAY), false);
    }
    
    private void displayFarmlandInfo(Player player, FertilizedFarmlandBlock farmland, BlockState state, BlockPos pos) {
        FertilizedFarmlandBlock.FertilizerType type = farmland.getFertilizerType();
        
        player.displayClientMessage(Component.literal("=== Farmland Analysis ===")
            .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), false);
        
        player.displayClientMessage(Component.translatable("message.farmcraft.analyzer.fertilizer_type",
            type.getName())
            .withStyle(ChatFormatting.AQUA), false);
        
        player.displayClientMessage(Component.translatable("message.farmcraft.analyzer.growth_bonus",
            String.format("+%.0f%%", (type.getGrowthSpeedMultiplier() - 1) * 100))
            .withStyle(ChatFormatting.YELLOW), false);
        
        player.displayClientMessage(Component.translatable("message.farmcraft.analyzer.yield_bonus",
            String.format("+%.0f%%", (type.getYieldMultiplier() - 1) * 100))
            .withStyle(ChatFormatting.GOLD), false);
        
        player.displayClientMessage(Component.translatable("message.farmcraft.analyzer.effect_info",
            type.getEffectDescription())
            .withStyle(ChatFormatting.GRAY), false);
    }
    
    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.farmcraft.analyzer.description")
            .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.farmcraft.analyzer.usage")
            .withStyle(ChatFormatting.DARK_GRAY));
    }
}
