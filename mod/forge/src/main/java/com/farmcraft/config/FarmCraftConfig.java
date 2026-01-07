package com.farmcraft.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Configuration for FarmCraft mod
 */
public class FarmCraftConfig {

    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    // Server Connection
    public static final ForgeConfigSpec.ConfigValue<String> RECIPE_SERVER_URL;
    public static final ForgeConfigSpec.IntValue RECIPE_SERVER_PORT;
    public static final ForgeConfigSpec.BooleanValue AUTO_CONNECT;

    // Proof of Work
    public static final ForgeConfigSpec.BooleanValue ENABLE_POW;
    public static final ForgeConfigSpec.IntValue MAX_DIFFICULTY;
    public static final ForgeConfigSpec.BooleanValue USE_GPU_COMPUTE;
    public static final ForgeConfigSpec.IntValue POW_THREAD_PRIORITY;

    // Farming
    public static final ForgeConfigSpec.DoubleValue BASE_GROWTH_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue BASE_YIELD_MULTIPLIER;
    public static final ForgeConfigSpec.BooleanValue ENABLE_POWER_FOODS;
    public static final ForgeConfigSpec.IntValue POWER_FOOD_EFFECT_DURATION_MODIFIER;

    // Fertilizers
    public static final ForgeConfigSpec.BooleanValue FERTILIZER_PARTICLES;
    public static final ForgeConfigSpec.BooleanValue FERTILIZED_FARMLAND_GLOW;

    // AI Assistant
    public static final ForgeConfigSpec.BooleanValue ENABLE_AI_CHAT;
    public static final ForgeConfigSpec.IntValue AI_MIN_QUESTION_LENGTH;
    public static final ForgeConfigSpec.BooleanValue AI_SHOW_THINKING_ANIMATION;

    // Debug
    public static final ForgeConfigSpec.BooleanValue DEBUG_MODE;
    public static final ForgeConfigSpec.BooleanValue LOG_NETWORK_PACKETS;

    static {
        BUILDER.comment("FarmCraft Configuration").push("farmcraft");

        // Server Connection
        BUILDER.comment("Recipe Server Connection Settings").push("server");

        RECIPE_SERVER_URL = BUILDER
                .comment("URL of the recipe distribution server")
                .define("recipeServerUrl", "localhost");

        RECIPE_SERVER_PORT = BUILDER
                .comment("Port of the recipe distribution server (HTTP port, WebSocket is port+1)")
                .defineInRange("recipeServerPort", 7420, 1, 65535);

        AUTO_CONNECT = BUILDER
                .comment("Automatically connect to recipe server on game start")
                .define("autoConnect", true);

        BUILDER.pop();

        // Proof of Work
        BUILDER.comment("Proof of Work Settings").push("pow");

        ENABLE_POW = BUILDER
                .comment("Enable proof-of-work challenges for recipe discovery")
                .define("enablePoW", true);

        MAX_DIFFICULTY = BUILDER
                .comment("Maximum difficulty level for challenges (1-20)")
                .defineInRange("maxDifficulty", 10, 1, 20);

        USE_GPU_COMPUTE = BUILDER
                .comment("Use GPU for computing challenges (requires compatible hardware)")
                .define("useGpuCompute", false);

        POW_THREAD_PRIORITY = BUILDER
                .comment("Thread priority for PoW computation (1=lowest, 10=highest)")
                .defineInRange("threadPriority", 1, 1, 10);

        BUILDER.pop();

        // Farming
        BUILDER.comment("Farming Mechanics Settings").push("farming");

        BASE_GROWTH_MULTIPLIER = BUILDER
                .comment("Base multiplier for crop growth speed")
                .defineInRange("baseGrowthMultiplier", 1.0, 0.1, 10.0);

        BASE_YIELD_MULTIPLIER = BUILDER
                .comment("Base multiplier for crop yields")
                .defineInRange("baseYieldMultiplier", 1.0, 0.1, 10.0);

        ENABLE_POWER_FOODS = BUILDER
                .comment("Enable power foods that grant effects")
                .define("enablePowerFoods", true);

        POWER_FOOD_EFFECT_DURATION_MODIFIER = BUILDER
                .comment("Modifier for power food effect duration (percentage)")
                .defineInRange("effectDurationModifier", 100, 10, 500);

        BUILDER.pop();

        // Fertilizers
        BUILDER.comment("Fertilizer Visual Settings").push("fertilizers");

        FERTILIZER_PARTICLES = BUILDER
                .comment("Show particles when fertilizers are applied")
                .define("showParticles", true);

        FERTILIZED_FARMLAND_GLOW = BUILDER
                .comment("Enhanced fertilized farmland emits light")
                .define("farmlandGlow", true);

        BUILDER.pop();

        // AI Assistant
        BUILDER.comment("AI Documentation Assistant Settings").push("ai");

        ENABLE_AI_CHAT = BUILDER
                .comment("Enable AI assistant to respond to questions in chat")
                .define("enableAiChat", true);

        AI_MIN_QUESTION_LENGTH = BUILDER
                .comment("Minimum message length to trigger AI response")
                .defineInRange("minQuestionLength", 10, 5, 100);

        AI_SHOW_THINKING_ANIMATION = BUILDER
                .comment("Show animated thinking indicator when AI is processing")
                .define("showThinkingAnimation", true);

        BUILDER.pop();

        // Debug
        BUILDER.comment("Debug Settings").push("debug");

        DEBUG_MODE = BUILDER
                .comment("Enable debug mode for additional logging")
                .define("debugMode", false);

        LOG_NETWORK_PACKETS = BUILDER
                .comment("Log all network packets (very verbose)")
                .define("logNetworkPackets", false);

        BUILDER.pop();

        BUILDER.pop();
        SPEC = BUILDER.build();
    }
}
