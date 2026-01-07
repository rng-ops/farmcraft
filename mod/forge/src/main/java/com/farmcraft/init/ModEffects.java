package com.farmcraft.init;

import com.farmcraft.FarmCraft;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Registry for custom mob effects
 */
public class ModEffects {
    public static final DeferredRegister<MobEffect> MOB_EFFECTS = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS,
            FarmCraft.MOD_ID);

    // Custom effect for enhanced farming
    public static final RegistryObject<MobEffect> FERTILE_AURA = MOB_EFFECTS.register(
            "fertile_aura",
            () -> new MobEffect(MobEffectCategory.BENEFICIAL, 0x00FF00) {
                public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
                    // Tick every second
                    return duration % 20 == 0;
                }
            });

    // Effect that increases crop yield when harvesting
    public static final RegistryObject<MobEffect> BOUNTIFUL_HARVEST = MOB_EFFECTS.register(
            "bountiful_harvest",
            () -> new MobEffect(MobEffectCategory.BENEFICIAL, 0xFFD700) {
                public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
                    return true;
                }
            });

    public static void register(IEventBus eventBus) {
        MOB_EFFECTS.register(eventBus);
    }
}
