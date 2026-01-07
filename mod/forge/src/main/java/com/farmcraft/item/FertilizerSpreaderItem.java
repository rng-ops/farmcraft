package com.farmcraft.item;

import com.farmcraft.FarmCraft;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmBlock;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Tool for spreading fertilizer over multiple farmland blocks
 */
public class FertilizerSpreaderItem extends Item {
    
    private static final int BASE_RANGE = 2; // 5x5 area
    
    public FertilizerSpreaderItem(Properties properties) {
        super(properties);
    }
    
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        
        if (player == null) return InteractionResult.PASS;
        
        // Find fertilizer in player's inventory
        ItemStack fertilizerStack = findFertilizerInInventory(player);
        if (fertilizerStack.isEmpty()) {
            if (level.isClientSide) {
                player.displayClientMessage(
                    Component.translatable("message.farmcraft.no_fertilizer")
                        .withStyle(ChatFormatting.RED), 
                    true
                );
            }
            return InteractionResult.FAIL;
        }
        
        if (!level.isClientSide) {
            int range = getRange(context.getItemInHand());
            int applied = 0;
            
            // Apply fertilizer to all farmland in range
            for (int x = -range; x <= range; x++) {
                for (int z = -range; z <= range; z++) {
                    BlockPos targetPos = pos.offset(x, 0, z);
                    
                    if (level.getBlockState(targetPos).is(Blocks.FARMLAND)) {
                        if (fertilizerStack.getItem() instanceof FertilizerItem fertilizerItem) {
                            // Apply fertilizer effect
                            applyFertilizer(level, targetPos, fertilizerItem);
                            applied++;
                            
                            fertilizerStack.shrink(1);
                            if (fertilizerStack.isEmpty()) {
                                fertilizerStack = findFertilizerInInventory(player);
                                if (fertilizerStack.isEmpty()) break;
                            }
                        }
                    }
                }
                if (fertilizerStack.isEmpty()) break;
            }
            
            if (applied > 0) {
                // Damage the spreader
                context.getItemInHand().hurtAndBreak(1, player, (p) -> 
                    p.broadcastBreakEvent(context.getHand()));
                
                // Play sound
                level.playSound(null, pos, SoundEvents.BONE_MEAL_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
                
                player.displayClientMessage(
                    Component.translatable("message.farmcraft.fertilizer_applied", applied)
                        .withStyle(ChatFormatting.GREEN),
                    true
                );
                
                FarmCraft.LOGGER.debug("Spreader applied fertilizer to {} blocks", applied);
            }
        }
        
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    
    private ItemStack findFertilizerInInventory(Player player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() instanceof FertilizerItem) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }
    
    private void applyFertilizer(Level level, BlockPos pos, FertilizerItem fertilizer) {
        // This would convert farmland to fertilized farmland
        // For now, we'll just trigger a growth tick
        if (level instanceof ServerLevel serverLevel) {
            Block block = level.getBlockState(pos.above()).getBlock();
            if (block instanceof net.minecraft.world.level.block.CropBlock crop) {
                // Boost growth
                crop.randomTick(level.getBlockState(pos.above()), serverLevel, pos.above(), level.random);
            }
        }
    }
    
    private int getRange(ItemStack stack) {
        // Check for range upgrade
        // This would be stored in NBT
        return BASE_RANGE;
    }
    
    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        int range = getRange(stack);
        int diameter = range * 2 + 1;
        
        tooltip.add(Component.translatable("tooltip.farmcraft.spreader.range", diameter, diameter)
            .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.farmcraft.spreader.usage")
            .withStyle(ChatFormatting.DARK_GRAY));
    }
}
