package com.farmcraft.network;

import com.farmcraft.FarmCraft;
import com.farmcraft.client.RecipeManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.event.network.CustomPayloadEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Packet containing recipes from the server
 */
public class RecipeResponsePacket {
    
    private final boolean success;
    private final List<RecipeData> recipes;
    private final int tokensRemaining;
    private final String error;
    
    public RecipeResponsePacket(boolean success, List<RecipeData> recipes, int tokensRemaining, String error) {
        this.success = success;
        this.recipes = recipes;
        this.tokensRemaining = tokensRemaining;
        this.error = error;
    }
    
    public static void encode(RecipeResponsePacket packet, FriendlyByteBuf buf) {
        buf.writeBoolean(packet.success);
        buf.writeInt(packet.recipes.size());
        for (RecipeData recipe : packet.recipes) {
            recipe.encode(buf);
        }
        buf.writeInt(packet.tokensRemaining);
        buf.writeUtf(packet.error != null ? packet.error : "");
    }
    
    public static RecipeResponsePacket decode(FriendlyByteBuf buf) {
        boolean success = buf.readBoolean();
        int size = buf.readInt();
        List<RecipeData> recipes = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            recipes.add(RecipeData.decode(buf));
        }
        int tokensRemaining = buf.readInt();
        String error = buf.readUtf();
        return new RecipeResponsePacket(success, recipes, tokensRemaining, error.isEmpty() ? null : error);
    }
    
    public static void handle(RecipeResponsePacket packet, CustomPayloadEvent.Context ctx) {
        ctx.enqueueWork(() -> {
            // Handle on client side
            if (packet.success) {
                FarmCraft.LOGGER.info("Received {} recipes from server", packet.recipes.size());
                RecipeManager.getInstance().updateRecipes(packet.recipes);
            } else {
                FarmCraft.LOGGER.warn("Recipe request failed: {}", packet.error);
            }
        });
        ctx.setPacketHandled(true);
    }
    
    /**
     * Simplified recipe data for network transfer
     */
    public static class RecipeData {
        private final String id;
        private final String name;
        private final String category;
        private final String tier;
        private final List<IngredientData> inputs;
        private final IngredientData output;
        
        public RecipeData(String id, String name, String category, String tier,
                          List<IngredientData> inputs, IngredientData output) {
            this.id = id;
            this.name = name;
            this.category = category;
            this.tier = tier;
            this.inputs = inputs;
            this.output = output;
        }
        
        public void encode(FriendlyByteBuf buf) {
            buf.writeUtf(id);
            buf.writeUtf(name);
            buf.writeUtf(category);
            buf.writeUtf(tier);
            buf.writeInt(inputs.size());
            for (IngredientData input : inputs) {
                input.encode(buf);
            }
            output.encode(buf);
        }
        
        public static RecipeData decode(FriendlyByteBuf buf) {
            String id = buf.readUtf();
            String name = buf.readUtf();
            String category = buf.readUtf();
            String tier = buf.readUtf();
            int inputSize = buf.readInt();
            List<IngredientData> inputs = new ArrayList<>(inputSize);
            for (int i = 0; i < inputSize; i++) {
                inputs.add(IngredientData.decode(buf));
            }
            IngredientData output = IngredientData.decode(buf);
            return new RecipeData(id, name, category, tier, inputs, output);
        }
        
        public String getId() { return id; }
        public String getName() { return name; }
        public String getCategory() { return category; }
        public String getTier() { return tier; }
        public List<IngredientData> getInputs() { return inputs; }
        public IngredientData getOutput() { return output; }
    }
    
    public static class IngredientData {
        private final String itemId;
        private final int count;
        
        public IngredientData(String itemId, int count) {
            this.itemId = itemId;
            this.count = count;
        }
        
        public void encode(FriendlyByteBuf buf) {
            buf.writeUtf(itemId);
            buf.writeInt(count);
        }
        
        public static IngredientData decode(FriendlyByteBuf buf) {
            return new IngredientData(buf.readUtf(), buf.readInt());
        }
        
        public String getItemId() { return itemId; }
        public int getCount() { return count; }
    }
}
