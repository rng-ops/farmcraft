/**
 * Demo: MCP Recipe Server with Append-Only Log
 * 
 * Demonstrates:
 * 1. Adding ingredients via natural language
 * 2. Creating recipes
 * 3. Cryptographically signed event log
 * 4. Snapshot versioning
 * 5. Config distribution
 */

import { RecipeStore } from './store';
import { EventLog } from './event-log';
import { RecipeParser } from './parser';
import { ConfigGenerator } from './config-generator';

async function runDemo() {
  console.log(`
  ███╗   ███╗ ██████╗██████╗     ██████╗ ███████╗ ██████╗██╗██████╗ ███████╗
  ████╗ ████║██╔════╝██╔══██╗    ██╔══██╗██╔════╝██╔════╝██║██╔══██╗██╔════╝
  ██╔████╔██║██║     ██████╔╝    ██████╔╝█████╗  ██║     ██║██████╔╝█████╗  
  ██║╚██╔╝██║██║     ██╔═══╝     ██╔══██╗██╔══╝  ██║     ██║██╔═══╝ ██╔══╝  
  ██║ ╚═╝ ██║╚██████╗██║         ██║  ██║███████╗╚██████╗██║██║     ███████╗
  ╚═╝     ╚═╝ ╚═════╝╚═╝         ╚═╝  ╚═╝╚══════╝ ╚═════╝╚═╝╚═╝     ╚══════╝
                                                                            
                    Append-Only Event Log Demo
  `);

  // Initialize components (use temp directory for demo)
  const store = new RecipeStore('./demo-data/recipes');
  const eventLog = new EventLog('./demo-data/events');
  const parser = new RecipeParser();
  const configGen = new ConfigGenerator();

  await store.initialize();
  await eventLog.initialize();

  console.log('═══════════════════════════════════════════════════════════════');
  console.log('  Adding Ingredients via Natural Language');
  console.log('═══════════════════════════════════════════════════════════════\n');

  // Add some ingredients
  const ingredientDescriptions = [
    {
      description: 'Tuff dust fertilizer that increases crop growth by 25% and lasts for 3 harvests',
      category: 'fertilizer',
    },
    {
      description: 'Calcite powder fertilizer, a superior tier that doubles growth speed and yield',
      category: 'fertilizer',
    },
    {
      description: 'Power carrot that gives the player speed 2 for 30 seconds when eaten',
      category: 'power_food',
    },
    {
      description: 'Legendary golden beetroot providing regeneration 2 and resistance 1 for 60 seconds',
      category: 'power_food',
    },
  ];

  for (const { description, category } of ingredientDescriptions) {
    console.log(`Adding: "${description.substring(0, 50)}..."`);
    
    const ingredient = await parser.parseIngredient(description, category);
    await store.addIngredient(ingredient);
    
    const event = await eventLog.appendEvent({
      type: 'ingredient_added',
      data: ingredient,
      timestamp: Date.now(),
    });
    
    console.log(`  → Created: ${ingredient.name} (${ingredient.tier})`);
    console.log(`  → Event #${event.sequence}: ${event.hash.substring(0, 16)}...`);
    console.log(`  → Signature: ${event.signature.substring(0, 16)}...`);
    console.log(`  → PoW nonce: ${event.powNonce}\n`);
  }

  console.log('═══════════════════════════════════════════════════════════════');
  console.log('  Creating Recipes');
  console.log('═══════════════════════════════════════════════════════════════\n');

  const recipeDescriptions = [
    'Combine 4 tuff and 1 bone meal to create tuff dust fertilizer',
    'Mix 2 calcite and 2 bone meal with a diamond to make calcite powder fertilizer',
    'Craft a power carrot from 1 golden carrot and 1 tuff dust fertilizer',
  ];

  for (const description of recipeDescriptions) {
    console.log(`Creating: "${description}"`);
    
    const recipe = await parser.parseRecipe(description, 'crafting');
    await store.addRecipe(recipe);
    
    const event = await eventLog.appendEvent({
      type: 'recipe_created',
      data: recipe,
      timestamp: Date.now(),
    });
    
    console.log(`  → Recipe: ${recipe.name}`);
    console.log(`  → Inputs: ${recipe.inputs.map(i => `${i.count}x ${i.item}`).join(', ')}`);
    console.log(`  → Output: ${recipe.output.count}x ${recipe.output.item}`);
    console.log(`  → Event #${event.sequence}: ${event.hash.substring(0, 16)}...\n`);
  }

  console.log('═══════════════════════════════════════════════════════════════');
  console.log('  Event Log Chain Verification');
  console.log('═══════════════════════════════════════════════════════════════\n');

  const chainResult = await eventLog.verifyChain();
  console.log(`Chain valid: ${chainResult.valid ? '✓' : '✗'}`);
  console.log(`Events verified: ${chainResult.verifiedCount}`);
  console.log(`Current chain hash: ${eventLog.getState().lastHash.substring(0, 32)}...`);
  
  if (chainResult.errors.length > 0) {
    console.log('Errors:', chainResult.errors);
  }

  console.log('\n── Verifying Individual Events ──\n');
  
  const events = await eventLog.query({ limit: 3 });
  for (const event of events) {
    const result = await eventLog.verifyEvent(event.id);
    console.log(`Event #${event.sequence} (${event.type}):`);
    console.log(`  Hash valid: ${result.hashValid ? '✓' : '✗'}`);
    console.log(`  Signature valid: ${result.signatureValid ? '✓' : '✗'}`);
    console.log(`  Chain valid: ${result.chainValid ? '✓' : '✗'}`);
    console.log(`  PoW valid: ${result.powValid ? '✓' : '✗'}`);
  }

  console.log('\n═══════════════════════════════════════════════════════════════');
  console.log('  Creating Snapshot');
  console.log('═══════════════════════════════════════════════════════════════\n');

  const snapshot = await store.createSnapshot('1.0.0', 'Initial release with fertilizers and power foods');
  
  const snapshotEvent = await eventLog.appendEvent({
    type: 'snapshot_created',
    data: {
      version: '1.0.0',
      hash: snapshot.hash,
      ingredientCount: snapshot.ingredients.length,
      recipeCount: snapshot.recipes.length,
    },
    timestamp: Date.now(),
  });

  console.log(`Snapshot created: v${snapshot.version}`);
  console.log(`  Hash: ${snapshot.hash.substring(0, 32)}...`);
  console.log(`  Ingredients: ${snapshot.ingredients.length}`);
  console.log(`  Recipes: ${snapshot.recipes.length}`);
  console.log(`  Event signature: ${snapshotEvent.signature.substring(0, 32)}...`);

  console.log('\n═══════════════════════════════════════════════════════════════');
  console.log('  Config Generation');
  console.log('═══════════════════════════════════════════════════════════════\n');

  const state = await store.getFullState();
  const jsonConfig = await configGen.generate(state, 'json', 'forge');
  
  console.log('Generated JSON config (excerpt):');
  const configObj = JSON.parse(jsonConfig);
  console.log(JSON.stringify({
    _meta: configObj._meta,
    ingredientCount: configObj.ingredients.length,
    recipeCount: configObj.recipes.length,
  }, null, 2));

  console.log('\n═══════════════════════════════════════════════════════════════');
  console.log('  Export for Distribution');
  console.log('═══════════════════════════════════════════════════════════════\n');

  const distribution = await eventLog.exportForDistribution(0);
  console.log(`Events to distribute: ${distribution.events.length}`);
  console.log(`Chain hash: ${distribution.chainHash.substring(0, 32)}...`);
  console.log(`Server signature: ${distribution.serverSignature.substring(0, 32)}...`);
  
  console.log('\n── Distribution Package ──');
  console.log('This package can be verified by clients:');
  console.log('1. Check server signature');
  console.log('2. Verify each event hash');
  console.log('3. Verify chain continuity');
  console.log('4. Compare final hash with DRM challenge');

  console.log('\n═══════════════════════════════════════════════════════════════');
  console.log('  Demo Complete');
  console.log('═══════════════════════════════════════════════════════════════\n');

  console.log('The MCP Recipe Server provides:');
  console.log('  • Natural language ingredient/recipe creation');
  console.log('  • Cryptographically signed append-only event log');
  console.log('  • PoW-verified events (prevents tampering)');
  console.log('  • Snapshot versioning for releases');
  console.log('  • Config generation for Forge/Fabric');
  console.log('  • Signed distribution packages for DRM');

  // Cleanup
  const fs = await import('fs/promises');
  try {
    await fs.rm('./demo-data', { recursive: true, force: true });
  } catch {}
}

runDemo().catch(console.error);
