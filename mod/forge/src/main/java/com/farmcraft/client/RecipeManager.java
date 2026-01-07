package com.farmcraft.client;

import com.farmcraft.FarmCraft;
import com.farmcraft.network.RecipeResponsePacket;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Client-side manager for recipe data
 */
public class RecipeManager {
    
    private static RecipeManager instance;
    
    private final Map<String, RecipeResponsePacket.RecipeData> recipes = new HashMap<>();
    private final Map<String, List<RecipeResponsePacket.RecipeData>> recipesByCategory = new HashMap<>();
    private long lastUpdateTime = 0;
    
    private RecipeManager() {}
    
    public static RecipeManager getInstance() {
        if (instance == null) {
            instance = new RecipeManager();
        }
        return instance;
    }
    
    public void updateRecipes(List<RecipeResponsePacket.RecipeData> newRecipes) {
        recipes.clear();
        recipesByCategory.clear();
        
        for (RecipeResponsePacket.RecipeData recipe : newRecipes) {
            recipes.put(recipe.getId(), recipe);
            
            recipesByCategory
                .computeIfAbsent(recipe.getCategory(), k -> new ArrayList<>())
                .add(recipe);
        }
        
        lastUpdateTime = System.currentTimeMillis();
        FarmCraft.LOGGER.info("Recipe cache updated with {} recipes", recipes.size());
    }
    
    public RecipeResponsePacket.RecipeData getRecipe(String id) {
        return recipes.get(id);
    }
    
    public List<RecipeResponsePacket.RecipeData> getRecipesByCategory(String category) {
        return recipesByCategory.getOrDefault(category, List.of());
    }
    
    public List<RecipeResponsePacket.RecipeData> getAllRecipes() {
        return new ArrayList<>(recipes.values());
    }
    
    public int getRecipeCount() {
        return recipes.size();
    }
    
    public boolean hasRecipes() {
        return !recipes.isEmpty();
    }
    
    public long getLastUpdateTime() {
        return lastUpdateTime;
    }
    
    public void clearCache() {
        recipes.clear();
        recipesByCategory.clear();
        lastUpdateTime = 0;
    }
}
