/**
 * Recipe Registry
 * Stores and manages all recipes in the system
 */

import type {
  Recipe,
  RecipeCategory,
  RecipeIngredient,
  WorkToken,
  FertilizerTier,
} from '@farmcraft/types';
import { verifyToken } from '@farmcraft/pow-core';

// ============================================================================
// Default Recipes
// ============================================================================

const DEFAULT_RECIPES: Recipe[] = [
  // ========== Basic Fertilizers ==========
  {
    id: 'fertilizer_stone_dust',
    name: 'Stone Dust Fertilizer',
    category: 'fertilizer',
    inputs: [
      { itemId: 'minecraft:diorite', count: 4 },
      { itemId: 'minecraft:bone_meal', count: 2 },
    ],
    output: { itemId: 'farmcraft:stone_dust_fertilizer', count: 4 },
    requiredTier: 'basic',
  },
  {
    id: 'fertilizer_calcium_mix',
    name: 'Calcium Mix Fertilizer',
    category: 'fertilizer',
    inputs: [
      { itemId: 'minecraft:calcite', count: 4 },
      { itemId: 'minecraft:bone_meal', count: 2 },
    ],
    output: { itemId: 'farmcraft:calcium_mix_fertilizer', count: 4 },
    requiredTier: 'basic',
  },
  {
    id: 'fertilizer_mineral_blend',
    name: 'Mineral Blend Fertilizer',
    category: 'fertilizer',
    inputs: [
      { itemId: 'minecraft:tuff', count: 4 },
      { itemId: 'minecraft:bone_meal', count: 2 },
    ],
    output: { itemId: 'farmcraft:mineral_blend_fertilizer', count: 4 },
    requiredTier: 'basic',
  },
  {
    id: 'fertilizer_gravel_grit',
    name: 'Gravel Grit Fertilizer',
    category: 'fertilizer',
    inputs: [
      { itemId: 'minecraft:gravel', count: 4 },
      { itemId: 'minecraft:bone_meal', count: 2 },
    ],
    output: { itemId: 'farmcraft:gravel_grit_fertilizer', count: 4 },
    requiredTier: 'basic',
  },

  // ========== Enhanced Fertilizers ==========
  {
    id: 'fertilizer_enhanced_stone',
    name: 'Enhanced Stone Fertilizer',
    category: 'fertilizer',
    inputs: [
      { itemId: 'farmcraft:stone_dust_fertilizer', count: 4 },
      { itemId: 'minecraft:glowstone_dust', count: 2 },
      { itemId: 'minecraft:redstone', count: 1 },
    ],
    output: { itemId: 'farmcraft:enhanced_stone_fertilizer', count: 4 },
    requiredTier: 'enhanced',
  },
  {
    id: 'fertilizer_enhanced_mineral',
    name: 'Enhanced Mineral Fertilizer',
    category: 'fertilizer',
    inputs: [
      { itemId: 'farmcraft:mineral_blend_fertilizer', count: 4 },
      { itemId: 'minecraft:amethyst_shard', count: 2 },
      { itemId: 'minecraft:lapis_lazuli', count: 1 },
    ],
    output: { itemId: 'farmcraft:enhanced_mineral_fertilizer', count: 4 },
    requiredTier: 'enhanced',
  },

  // ========== Superior Fertilizers ==========
  {
    id: 'fertilizer_superior_blend',
    name: 'Superior Blend Fertilizer',
    category: 'fertilizer',
    inputs: [
      { itemId: 'farmcraft:enhanced_stone_fertilizer', count: 2 },
      { itemId: 'farmcraft:enhanced_mineral_fertilizer', count: 2 },
      { itemId: 'minecraft:nether_wart', count: 1 },
      { itemId: 'minecraft:blaze_powder', count: 1 },
    ],
    output: { itemId: 'farmcraft:superior_blend_fertilizer', count: 4 },
    requiredTier: 'superior',
  },

  // ========== Power Foods ==========
  {
    id: 'power_food_speed_carrot',
    name: 'Speed Carrot',
    category: 'power_food',
    inputs: [
      { itemId: 'minecraft:carrot', count: 1, metadata: { grownWith: 'stone_dust_fertilizer' } },
    ],
    output: { itemId: 'farmcraft:speed_carrot', count: 1 },
    requiredTier: 'basic',
  },
  {
    id: 'power_food_strength_potato',
    name: 'Strength Potato',
    category: 'power_food',
    inputs: [
      { itemId: 'minecraft:potato', count: 1, metadata: { grownWith: 'gravel_grit_fertilizer' } },
    ],
    output: { itemId: 'farmcraft:strength_potato', count: 1 },
    requiredTier: 'basic',
  },
  {
    id: 'power_food_resistance_beet',
    name: 'Resistance Beetroot',
    category: 'power_food',
    inputs: [
      { itemId: 'minecraft:beetroot', count: 1, metadata: { grownWith: 'calcium_mix_fertilizer' } },
    ],
    output: { itemId: 'farmcraft:resistance_beet', count: 1 },
    requiredTier: 'basic',
  },
  {
    id: 'power_food_night_vision_wheat',
    name: 'Night Vision Bread',
    category: 'power_food',
    inputs: [
      { itemId: 'minecraft:wheat', count: 3, metadata: { grownWith: 'mineral_blend_fertilizer' } },
    ],
    output: { itemId: 'farmcraft:night_vision_bread', count: 1 },
    requiredTier: 'basic',
  },

  // ========== Enhanced Power Foods ==========
  {
    id: 'power_food_super_speed_carrot',
    name: 'Super Speed Carrot',
    category: 'power_food',
    inputs: [
      { itemId: 'minecraft:carrot', count: 1, metadata: { grownWith: 'enhanced_stone_fertilizer' } },
      { itemId: 'minecraft:sugar', count: 2 },
    ],
    output: { itemId: 'farmcraft:super_speed_carrot', count: 1 },
    requiredTier: 'enhanced',
  },
  {
    id: 'power_food_regeneration_apple',
    name: 'Regeneration Apple',
    category: 'power_food',
    inputs: [
      { itemId: 'minecraft:apple', count: 1 },
      { itemId: 'farmcraft:superior_blend_fertilizer', count: 1 },
      { itemId: 'minecraft:ghast_tear', count: 1 },
    ],
    output: { itemId: 'farmcraft:regeneration_apple', count: 1 },
    requiredTier: 'superior',
  },

  // ========== Tools ==========
  {
    id: 'tool_fertilizer_spreader',
    name: 'Fertilizer Spreader',
    category: 'tool',
    inputs: [
      { itemId: 'minecraft:iron_ingot', count: 3 },
      { itemId: 'minecraft:stick', count: 2 },
      { itemId: 'minecraft:hopper', count: 1 },
    ],
    output: { itemId: 'farmcraft:fertilizer_spreader', count: 1 },
    requiredTier: 'basic',
  },
  {
    id: 'tool_crop_analyzer',
    name: 'Crop Analyzer',
    category: 'tool',
    inputs: [
      { itemId: 'minecraft:glass', count: 2 },
      { itemId: 'minecraft:redstone', count: 3 },
      { itemId: 'minecraft:gold_ingot', count: 2 },
    ],
    output: { itemId: 'farmcraft:crop_analyzer', count: 1 },
    requiredTier: 'enhanced',
  },

  // ========== Upgrades ==========
  {
    id: 'upgrade_spreader_range',
    name: 'Spreader Range Upgrade',
    category: 'upgrade',
    inputs: [
      { itemId: 'minecraft:ender_pearl', count: 2 },
      { itemId: 'minecraft:redstone_block', count: 1 },
    ],
    output: { itemId: 'farmcraft:spreader_range_upgrade', count: 1 },
    requiredTier: 'enhanced',
  },
  {
    id: 'upgrade_analyzer_precision',
    name: 'Analyzer Precision Upgrade',
    category: 'upgrade',
    inputs: [
      { itemId: 'minecraft:diamond', count: 1 },
      { itemId: 'minecraft:spyglass', count: 1 },
    ],
    output: { itemId: 'farmcraft:analyzer_precision_upgrade', count: 1 },
    requiredTier: 'superior',
  },

  // ========== Special (Discovered through PoW) ==========
  {
    id: 'special_mutation_serum',
    name: 'Mutation Serum',
    category: 'special',
    inputs: [
      { itemId: 'farmcraft:superior_blend_fertilizer', count: 4 },
      { itemId: 'minecraft:dragon_breath', count: 1 },
      { itemId: 'minecraft:nether_star', count: 1 },
    ],
    output: { itemId: 'farmcraft:mutation_serum', count: 1 },
    requiredTier: 'legendary',
  },
];

// ============================================================================
// Recipe Registry Class
// ============================================================================

export class RecipeRegistry {
  private recipes: Map<string, Recipe> = new Map();
  private discoveredRecipes: Map<string, { playerId: string; timestamp: number }> = new Map();

  constructor() {
    this.loadDefaultRecipes();
  }

  private loadDefaultRecipes(): void {
    for (const recipe of DEFAULT_RECIPES) {
      this.recipes.set(recipe.id, recipe);
    }
    console.log(`Loaded ${this.recipes.size} default recipes`);
  }

  getRecipeCount(): number {
    return this.recipes.size;
  }

  getCategories(): RecipeCategory[] {
    const categories = new Set<RecipeCategory>();
    for (const recipe of this.recipes.values()) {
      categories.add(recipe.category);
    }
    return Array.from(categories);
  }

  getRecipeById(id: string): Recipe | undefined {
    return this.recipes.get(id);
  }

  getRecipesByCategory(category: RecipeCategory): Recipe[] {
    return Array.from(this.recipes.values()).filter(r => r.category === category);
  }

  getRecipesByTier(tier: FertilizerTier): Recipe[] {
    return Array.from(this.recipes.values()).filter(r => r.requiredTier === tier);
  }

  getAllRecipes(): Recipe[] {
    return Array.from(this.recipes.values());
  }

  getRecipesWithToken(token: WorkToken): Recipe[] {
    if (!verifyToken(token)) {
      throw new Error('Invalid token');
    }

    // Return recipes based on token credits
    // More credits = access to higher tier recipes
    const tierAccess: FertilizerTier[] = ['basic'];
    
    if (token.credits >= 10) tierAccess.push('enhanced');
    if (token.credits >= 50) tierAccess.push('superior');
    if (token.credits >= 200) tierAccess.push('legendary');

    return Array.from(this.recipes.values()).filter(r => 
      tierAccess.includes(r.requiredTier)
    );
  }

  addDiscoveredRecipe(recipe: Recipe, playerId: string): void {
    recipe.discoveredAt = Date.now();
    recipe.discoveredBy = playerId;
    this.recipes.set(recipe.id, recipe);
    this.discoveredRecipes.set(recipe.id, {
      playerId,
      timestamp: Date.now(),
    });
  }

  getDiscoveredRecipes(): Recipe[] {
    return Array.from(this.recipes.values()).filter(r => r.discoveredAt !== undefined);
  }

  isRecipeDiscovered(recipeId: string): boolean {
    return this.discoveredRecipes.has(recipeId);
  }
}
