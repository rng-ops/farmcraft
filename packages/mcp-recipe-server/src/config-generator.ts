/**
 * Config Generator
 * 
 * Generates client configuration files from stored state.
 * Supports multiple formats and mod loaders.
 */

import type { FullState, Ingredient, Recipe, Effect } from './store';

export class ConfigGenerator {
  /**
   * Generate configuration in specified format
   */
  async generate(state: FullState, format: string, target: string): Promise<string> {
    switch (format) {
      case 'json':
        return this.generateJson(state, target);
      case 'toml':
        return this.generateToml(state, target);
      case 'yaml':
        return this.generateYaml(state, target);
      default:
        return this.generateJson(state, target);
    }
  }
  
  private generateJson(state: FullState, target: string): string {
    const config = {
      _meta: {
        format: 'farmcraft-config',
        version: '1.0.0',
        target,
        generatedAt: new Date().toISOString(),
      },
      ingredients: this.transformIngredients(state.ingredients, target),
      recipes: this.transformRecipes(state.recipes, target),
      effects: state.effects,
    };
    
    return JSON.stringify(config, null, 2);
  }
  
  private generateToml(state: FullState, target: string): string {
    const lines: string[] = [
      '# FarmCraft Configuration',
      `# Generated: ${new Date().toISOString()}`,
      `# Target: ${target}`,
      '',
    ];
    
    // Ingredients section
    lines.push('[ingredients]');
    for (const ing of state.ingredients) {
      lines.push('');
      lines.push(`[[ingredients.${ing.category}]]`);
      lines.push(`id = "${ing.id}"`);
      lines.push(`name = "${ing.name}"`);
      if (ing.tier) lines.push(`tier = "${ing.tier}"`);
      if (ing.effects && ing.effects.length > 0) {
        lines.push('effects = [');
        for (const eff of ing.effects) {
          lines.push(`  { type = "${eff.type}", value = ${eff.value}${eff.duration ? `, duration = ${eff.duration}` : ''} },`);
        }
        lines.push(']');
      }
    }
    
    lines.push('');
    lines.push('[recipes]');
    for (const recipe of state.recipes) {
      lines.push('');
      lines.push(`[[recipes.${recipe.type}]]`);
      lines.push(`id = "${recipe.id}"`);
      lines.push(`output = "${recipe.output.item}"`);
      lines.push(`output_count = ${recipe.output.count}`);
      lines.push('inputs = [');
      for (const input of recipe.inputs) {
        lines.push(`  { item = "${input.item}", count = ${input.count} },`);
      }
      lines.push(']');
      if (recipe.workRequired) lines.push(`work_required = ${recipe.workRequired}`);
      if (recipe.trustRequired) lines.push(`trust_required = ${recipe.trustRequired}`);
    }
    
    return lines.join('\n');
  }
  
  private generateYaml(state: FullState, target: string): string {
    const lines: string[] = [
      '# FarmCraft Configuration',
      `# Generated: ${new Date().toISOString()}`,
      `# Target: ${target}`,
      '',
      'ingredients:',
    ];
    
    for (const ing of state.ingredients) {
      lines.push(`  - id: ${ing.id}`);
      lines.push(`    name: "${ing.name}"`);
      lines.push(`    category: ${ing.category}`);
      if (ing.tier) lines.push(`    tier: ${ing.tier}`);
      if (ing.effects && ing.effects.length > 0) {
        lines.push('    effects:');
        for (const eff of ing.effects) {
          lines.push(`      - type: ${eff.type}`);
          lines.push(`        value: ${eff.value}`);
          if (eff.duration) lines.push(`        duration: ${eff.duration}`);
        }
      }
      lines.push('');
    }
    
    lines.push('recipes:');
    for (const recipe of state.recipes) {
      lines.push(`  - id: ${recipe.id}`);
      lines.push(`    name: "${recipe.name}"`);
      lines.push(`    type: ${recipe.type}`);
      lines.push('    inputs:');
      for (const input of recipe.inputs) {
        lines.push(`      - item: ${input.item}`);
        lines.push(`        count: ${input.count}`);
      }
      lines.push('    output:');
      lines.push(`      item: ${recipe.output.item}`);
      lines.push(`      count: ${recipe.output.count}`);
      if (recipe.workRequired) lines.push(`    workRequired: ${recipe.workRequired}`);
      if (recipe.trustRequired) lines.push(`    trustRequired: ${recipe.trustRequired}`);
      lines.push('');
    }
    
    return lines.join('\n');
  }
  
  private transformIngredients(ingredients: Ingredient[], target: string): unknown[] {
    return ingredients.map(ing => ({
      ...ing,
      registryName: `farmcraft:${ing.id}`,
      // Add loader-specific fields
      ...(target === 'forge' ? {
        forgeRegistryEntry: true,
        properties: ing.properties || {},
      } : {}),
      ...(target === 'fabric' ? {
        fabricEntry: true,
      } : {}),
    }));
  }
  
  private transformRecipes(recipes: Recipe[], target: string): unknown[] {
    return recipes.map(recipe => {
      // Transform to Minecraft recipe format
      if (target === 'forge' || target === 'fabric') {
        return {
          type: this.getRecipeType(recipe.type),
          ...this.getRecipeFormat(recipe),
        };
      }
      return recipe;
    });
  }
  
  private getRecipeType(type: string): string {
    switch (type) {
      case 'crafting': return 'minecraft:crafting_shaped';
      case 'smelting': return 'minecraft:smelting';
      case 'brewing': return 'minecraft:brewing';
      default: return 'farmcraft:fertilizing';
    }
  }
  
  private getRecipeFormat(recipe: Recipe): object {
    if (recipe.type === 'crafting') {
      // Convert to shaped crafting format
      const pattern: string[] = [];
      const key: Record<string, { item: string }> = {};
      const chars = 'ABCDEFGHI';
      
      recipe.inputs.forEach((input, i) => {
        key[chars[i]] = { item: input.item };
      });
      
      // Simple 3x3 pattern
      const inputChars = recipe.inputs.map((_, i) => chars[i]).join('');
      pattern.push(inputChars.slice(0, 3).padEnd(3, ' '));
      if (inputChars.length > 3) pattern.push(inputChars.slice(3, 6).padEnd(3, ' '));
      if (inputChars.length > 6) pattern.push(inputChars.slice(6, 9).padEnd(3, ' '));
      
      return {
        pattern,
        key,
        result: {
          item: recipe.output.item,
          count: recipe.output.count,
        },
      };
    }
    
    // Shapeless/other formats
    return {
      ingredients: recipe.inputs.map(i => ({ item: i.item, count: i.count })),
      result: recipe.output,
    };
  }
}
