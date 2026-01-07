/**
 * Recipe Store
 *
 * Persistent storage for ingredients, recipes, and snapshots.
 */

import { createHash } from 'crypto';
import { Level } from 'level';
import { v4 as uuidv4 } from 'uuid';

export interface Ingredient {
  id: string;
  name: string;
  category: 'fertilizer' | 'power_food' | 'material' | 'crop' | 'tool';
  description: string;
  effects?: IngredientEffect[];
  properties?: Record<string, unknown>;
  tier?: 'basic' | 'enhanced' | 'superior' | 'legendary';
  createdAt: number;
}

export interface IngredientEffect {
  type: string;
  value: number;
  duration?: number;
}

export interface Recipe {
  id: string;
  name: string;
  type: 'crafting' | 'smelting' | 'brewing' | 'fertilizing';
  inputs: RecipeInput[];
  output: RecipeOutput;
  workRequired?: number;
  trustRequired?: number;
  createdAt: number;
}

export interface RecipeInput {
  item: string;
  count: number;
  tag?: string;
}

export interface RecipeOutput {
  item: string;
  count: number;
}

export interface Effect {
  id: string;
  name: string;
  description: string;
  duration: number;
  amplifier: number;
}

export interface Snapshot {
  version: string;
  hash: string;
  timestamp: number;
  notes: string;
  ingredients: Ingredient[];
  recipes: Recipe[];
  effects: Effect[];
}

export interface FullState {
  ingredients: Ingredient[];
  recipes: Recipe[];
  effects: Effect[];
}

export class RecipeStore {
  private db!: Level<string, string>;

  constructor(private dbPath: string) {}

  async initialize(): Promise<void> {
    this.db = new Level(this.dbPath, { valueEncoding: 'json' });
    await this.db.open();
  }

  // ═══════════════════════════════════════════════════════════════════
  // Ingredients
  // ═══════════════════════════════════════════════════════════════════

  async addIngredient(ingredient: Ingredient): Promise<void> {
    await this.db.put(`ingredient:${ingredient.id}`, JSON.stringify(ingredient));
  }

  async getIngredient(id: string): Promise<Ingredient | null> {
    try {
      const data = await this.db.get(`ingredient:${id}`);
      return JSON.parse(data);
    } catch {
      return null;
    }
  }

  async getIngredients(category?: string): Promise<Ingredient[]> {
    const ingredients: Ingredient[] = [];

    for await (const [key, value] of this.db.iterator()) {
      if (key.startsWith('ingredient:')) {
        const ingredient = JSON.parse(value) as Ingredient;
        if (!category || ingredient.category === category) {
          ingredients.push(ingredient);
        }
      }
    }

    return ingredients.sort((a, b) => a.createdAt - b.createdAt);
  }

  // ═══════════════════════════════════════════════════════════════════
  // Recipes
  // ═══════════════════════════════════════════════════════════════════

  async addRecipe(recipe: Recipe): Promise<void> {
    await this.db.put(`recipe:${recipe.id}`, JSON.stringify(recipe));
  }

  async getRecipe(id: string): Promise<Recipe | null> {
    try {
      const data = await this.db.get(`recipe:${id}`);
      return JSON.parse(data);
    } catch {
      return null;
    }
  }

  async getRecipes(outputItem?: string): Promise<Recipe[]> {
    const recipes: Recipe[] = [];

    for await (const [key, value] of this.db.iterator()) {
      if (key.startsWith('recipe:')) {
        const recipe = JSON.parse(value) as Recipe;
        if (!outputItem || recipe.output.item.includes(outputItem)) {
          recipes.push(recipe);
        }
      }
    }

    return recipes.sort((a, b) => a.createdAt - b.createdAt);
  }

  async validateRecipe(recipe: Recipe): Promise<{ valid: boolean; missing: string[] }> {
    const missing: string[] = [];

    for (const input of recipe.inputs) {
      // Check if it's a Minecraft item or a farmcraft ingredient
      if (input.item.startsWith('minecraft:')) continue;
      if (input.item.startsWith('farmcraft:')) {
        const ingredientId = input.item.replace('farmcraft:', '');
        const exists = await this.getIngredient(ingredientId);
        if (!exists) {
          missing.push(input.item);
        }
      }
    }

    return {
      valid: missing.length === 0,
      missing,
    };
  }

  // ═══════════════════════════════════════════════════════════════════
  // Effects
  // ═══════════════════════════════════════════════════════════════════

  async addEffect(effect: Effect): Promise<void> {
    await this.db.put(`effect:${effect.id}`, JSON.stringify(effect));
  }

  async getEffects(): Promise<Effect[]> {
    const effects: Effect[] = [];

    for await (const [key, value] of this.db.iterator()) {
      if (key.startsWith('effect:')) {
        effects.push(JSON.parse(value) as Effect);
      }
    }

    return effects;
  }

  // ═══════════════════════════════════════════════════════════════════
  // Snapshots
  // ═══════════════════════════════════════════════════════════════════

  async createSnapshot(version: string, notes: string): Promise<Snapshot> {
    const state = await this.getFullState();

    // Compute hash of entire state
    const stateData = JSON.stringify({
      version,
      ingredients: state.ingredients,
      recipes: state.recipes,
      effects: state.effects,
    });

    const hash = createHash('sha256').update(stateData).digest('hex');

    const snapshot: Snapshot = {
      version,
      hash,
      timestamp: Date.now(),
      notes,
      ...state,
    };

    await this.db.put(`snapshot:${version}`, JSON.stringify(snapshot));

    return snapshot;
  }

  async getSnapshot(version: string): Promise<Snapshot | null> {
    try {
      const data = await this.db.get(`snapshot:${version}`);
      return JSON.parse(data);
    } catch {
      return null;
    }
  }

  async listSnapshots(): Promise<
    { version: string; timestamp: number; notes: string; hash: string }[]
  > {
    const snapshots: { version: string; timestamp: number; notes: string; hash: string }[] = [];

    for await (const [key, value] of this.db.iterator()) {
      if (key.startsWith('snapshot:')) {
        const snapshot = JSON.parse(value) as Snapshot;
        snapshots.push({
          version: snapshot.version,
          timestamp: snapshot.timestamp,
          notes: snapshot.notes,
          hash: snapshot.hash,
        });
      }
    }

    return snapshots.sort((a, b) => b.timestamp - a.timestamp);
  }

  // ═══════════════════════════════════════════════════════════════════
  // Full State
  // ═══════════════════════════════════════════════════════════════════

  async getFullState(): Promise<FullState> {
    const [ingredients, recipes, effects] = await Promise.all([
      this.getIngredients(),
      this.getRecipes(),
      this.getEffects(),
    ]);

    return { ingredients, recipes, effects };
  }
}
