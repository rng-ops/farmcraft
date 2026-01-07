package com.farmcraft.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Power food item that grants effects when consumed
 */
public class PowerFoodItem extends Item {
    
    private final MobEffect effect;
    private final int amplifier;
    private final int duration;
    
    public PowerFoodItem(Properties properties, MobEffect effect, int amplifier, int duration) {
        super(properties);
        this.effect = effect;
        this.amplifier = amplifier;
        this.duration = duration;
    }
    
    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity livingEntity) {
        if (!level.isClientSide) {
            // Apply the effect
            livingEntity.addEffect(new MobEffectInstance(effect, duration, amplifier));
        }
        return super.finishUsingItem(stack, level, livingEntity);
    }
    
    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        // Show effect info
        String effectName = effect.getDisplayName().getString();
        int levelDisplay = amplifier + 1;
        int durationSeconds = duration / 20;
        
        tooltip.add(Component.translatable("tooltip.farmcraft.power_food.effect", 
            effectName, levelDisplay)
            .withStyle(ChatFormatting.BLUE));
        tooltip.add(Component.translatable("tooltip.farmcraft.power_food.duration", 
            durationSeconds)
            .withStyle(ChatFormatting.GRAY));
        
        // Show food properties
        FoodProperties food = this.getFoodProperties();
        if (food != null) {
            tooltip.add(Component.translatable("tooltip.farmcraft.power_food.nutrition", 
                food.getNutrition())
                .withStyle(ChatFormatting.DARK_GREEN));
        }
    }
    
    public MobEffect getEffect() {
        return effect;
    }
    
    public int getAmplifier() {
        return amplifier;
    }
    
    public int getEffectDuration() {
        return duration;
    }
}
