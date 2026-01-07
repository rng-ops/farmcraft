/**
 * Natural Language Recipe Parser
 * 
 * Uses LangChain to parse English descriptions into
 * structured ingredient and recipe data.
 */

import { ChatOpenAI } from '@langchain/openai';
import { v4 as uuidv4 } from 'uuid';
import type { Ingredient, Recipe, IngredientEffect, RecipeInput } from './store';

export class RecipeParser {
  private llm: ChatOpenAI | null = null;
  
  constructor() {
    // Only initialize LLM if API key is available
    if (process.env.OPENAI_API_KEY) {
      this.llm = new ChatOpenAI({
        modelName: 'gpt-4',
        temperature: 0,
      });
    }
  }
  
  /**
   * Parse a natural language ingredient description
   */
  async parseIngredient(description: string, category: string): Promise<Ingredient> {
    if (this.llm) {
      try {
        const prompt = `
You are a Minecraft mod developer creating ingredients for a farming mod called FarmCraft.

Parse the following ingredient description into JSON format.

Category: ${category}
Description: ${description}

Return ONLY a JSON object with these fields:
{
  "name": "Display Name",
  "id": "snake_case_id",
  "description": "Brief description",
  "tier": "basic" | "enhanced" | "superior" | "legendary",
  "effects": [
    {
      "type": "effect_type (growth_speed, yield_bonus, speed, regeneration, etc.)",
      "value": 1.25,
      "duration": 30 (optional, for timed effects in seconds)
    }
  ]
}

For fertilizers, effects should be growth_speed (multiplier like 1.25), yield_bonus, fertility_duration.
For power foods, effects should be potion effects (speed, strength, regeneration, resistance).`;

        const response = await this.llm.invoke(prompt);
        const content = typeof response.content === 'string' 
          ? response.content 
          : JSON.stringify(response.content);
        
        // Extract JSON from response
        const jsonMatch = content.match(/\{[\s\S]*\}/);
        if (jsonMatch) {
          const parsed = JSON.parse(jsonMatch[0]);
          return {
            ...parsed,
            category: category as Ingredient['category'],
            createdAt: Date.now(),
          };
        }
      } catch (error) {
        console.log('LLM parsing failed, using fallback:', (error as Error).message);
      }
    }
    
    // Fallback to simple parsing
    return this.fallbackParseIngredient(description, category);
  }
  
  /**
   * Parse a natural language recipe description
   */
  async parseRecipe(description: string, type: string): Promise<Recipe> {
    if (this.llm) {
      try {
        const prompt = `
You are a Minecraft mod developer creating recipes for a farming mod called FarmCraft.

Parse the following recipe description into JSON format.

Recipe Type: ${type}
Description: ${description}

Return ONLY a JSON object with these fields:
{
  "name": "Recipe Name",
  "id": "snake_case_id",
  "inputs": [
    { "item": "minecraft:bone_meal", "count": 1 },
    { "item": "farmcraft:tuff_dust", "count": 4 }
  ],
  "output": { "item": "farmcraft:tuff_fertilizer", "count": 1 },
  "workRequired": 0,
  "trustRequired": 0
}

Important:
- Use minecraft: prefix for vanilla items (bone_meal, diamond, iron_ingot, etc.)
- Use farmcraft: prefix for mod items
- workRequired: 0-100 for basic, 100-500 for advanced, 500+ for legendary
- trustRequired: 0 for basic, 60 for advanced, 80+ for legendary`;

        const response = await this.llm.invoke(prompt);
        const content = typeof response.content === 'string' 
          ? response.content 
          : JSON.stringify(response.content);
        
        // Extract JSON from response
        const jsonMatch = content.match(/\{[\s\S]*\}/);
        if (jsonMatch) {
          const parsed = JSON.parse(jsonMatch[0]);
          return {
            ...parsed,
            type: type as Recipe['type'],
            createdAt: Date.now(),
          };
        }
      } catch (error) {
        console.log('LLM parsing failed, using fallback:', (error as Error).message);
      }
    }
    
    // Fallback to simple parsing
    return this.fallbackParseRecipe(description, type);
  }
  
  /**
   * Fallback ingredient parser (no LLM required)
   */
  private fallbackParseIngredient(description: string, category: string): Ingredient {
    // Extract name from first part of description
    const words = description.split(' ');
    const name = words.slice(0, 3).join(' ');
    const id = name.toLowerCase().replace(/\s+/g, '_').replace(/[^a-z0-9_]/g, '');
    
    // Detect tier from keywords
    let tier: Ingredient['tier'] = 'basic';
    if (description.match(/legendary|ultimate|supreme/i)) tier = 'legendary';
    else if (description.match(/superior|advanced|powerful/i)) tier = 'superior';
    else if (description.match(/enhanced|improved|better/i)) tier = 'enhanced';
    
    // Detect effects from keywords
    const effects: IngredientEffect[] = [];
    
    if (category === 'fertilizer') {
      const growthMatch = description.match(/(\d+(?:\.\d+)?)[x%]?\s*(?:faster|growth|speed)/i);
      if (growthMatch) {
        const value = parseFloat(growthMatch[1]);
        effects.push({ type: 'growth_speed', value: value > 10 ? value / 100 + 1 : value });
      } else {
        // Default growth speed based on tier
        const tierMultipliers = { basic: 1.25, enhanced: 1.5, superior: 2.0, legendary: 3.0 };
        effects.push({ type: 'growth_speed', value: tierMultipliers[tier] });
      }
    }
    
    if (category === 'power_food') {
      const effectMatches = description.match(/(speed|strength|regeneration|resistance|jump|haste)/gi);
      if (effectMatches) {
        effectMatches.forEach(effect => {
          effects.push({ type: effect.toLowerCase(), value: 0, duration: 30 });
        });
      }
    }
    
    return {
      id,
      name,
      category: category as Ingredient['category'],
      description,
      tier,
      effects,
      createdAt: Date.now(),
    };
  }
  
  /**
   * Fallback recipe parser (no LLM required)
   */
  private fallbackParseRecipe(description: string, type: string): Recipe {
    // Simple pattern matching for common recipe formats
    // "combine X with Y to make Z"
    // "4 tuff dust + bone meal = enhanced tuff fertilizer"
    
    const inputs: RecipeInput[] = [];
    let outputItem = 'farmcraft:unknown_item';
    let outputCount = 1;
    
    // Try to find output
    const outputMatch = description.match(/(?:make|create|produces?|=|->|to get)\s+(?:(\d+)\s+)?([a-z_\s]+)/i);
    if (outputMatch) {
      outputCount = outputMatch[1] ? parseInt(outputMatch[1]) : 1;
      outputItem = 'farmcraft:' + outputMatch[2].trim().toLowerCase().replace(/\s+/g, '_');
    }
    
    // Try to find inputs
    const inputPattern = /(\d+)\s+([a-z_\s]+?)(?:\s+(?:and|with|\+|,)|$)/gi;
    let match;
    while ((match = inputPattern.exec(description)) !== null) {
      const count = parseInt(match[1]);
      let item = match[2].trim().toLowerCase().replace(/\s+/g, '_');
      
      // Determine namespace
      const vanillaItems = ['bone_meal', 'diamond', 'iron', 'gold', 'coal', 'redstone', 'lapis'];
      const namespace = vanillaItems.some(v => item.includes(v)) ? 'minecraft' : 'farmcraft';
      
      inputs.push({ item: `${namespace}:${item}`, count });
    }
    
    // If no inputs found, add placeholder
    if (inputs.length === 0) {
      inputs.push({ item: 'minecraft:unknown', count: 1 });
    }
    
    const name = outputItem.replace('farmcraft:', '').replace(/_/g, ' ');
    const id = outputItem.replace('farmcraft:', '');
    
    return {
      id,
      name,
      type: type as Recipe['type'],
      inputs,
      output: { item: outputItem, count: outputCount },
      workRequired: 0,
      trustRequired: 0,
      createdAt: Date.now(),
    };
  }
}
