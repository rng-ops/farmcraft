#!/usr/bin/env node

import { AITestController, TestScenario } from './index';

/**
 * CLI for running AI-controlled test scenarios
 */

const scenarios: TestScenario[] = [
  {
    id: 'basic-chat',
    description: 'Test basic chat communication',
    instructions: [
      'Say hello in chat',
      'Ask for help using the /farmcraft guide command',
      'Check your current status',
    ],
    expectedOutcomes: [
      'Bot sends messages successfully',
      'Help information is displayed',
      'Status command works',
    ],
    tags: ['basic', 'chat', 'commands'],
  },
  {
    id: 'farming-workflow',
    description: 'Complete farming workflow with FarmCraft',
    instructions: [
      'Look for nearby farmland or create some',
      'Plant wheat seeds if available in inventory',
      'Wait for crops to grow',
      'Harvest the wheat when ready',
      'Craft bread if possible',
    ],
    expectedOutcomes: [
      'Bot identifies farming area',
      'Seeds are planted',
      'Crops are harvested',
      'Items are crafted',
    ],
    tags: ['farming', 'crafting', 'gameplay'],
  },
  {
    id: 'fertilizer-test',
    description: 'Test FarmCraft fertilizer system',
    instructions: [
      'Check inventory for stone dust',
      'If no stone dust, craft it from cobblestone',
      'Find farmland with crops',
      'Apply stone dust fertilizer to crops',
      'Observe growth rate changes',
    ],
    expectedOutcomes: [
      'Fertilizer is crafted or obtained',
      'Fertilizer is applied successfully',
      'Crops show enhanced growth',
    ],
    tags: ['fertilizer', 'farmcraft', 'advanced'],
  },
  {
    id: 'recipe-unlock',
    description: 'Test recipe unlock system with proof-of-work',
    instructions: [
      'Ask about locked recipes using /farmcraft topics',
      'Identify a recipe to unlock',
      'Complete the proof-of-work challenge',
      'Verify the recipe is now unlocked',
      'Craft the newly unlocked item',
    ],
    expectedOutcomes: [
      'Recipe system responds',
      'PoW challenge is completed',
      'Recipe unlocks successfully',
      'Item can be crafted',
    ],
    tags: ['recipes', 'pow', 'unlock', 'advanced'],
  },
  {
    id: 'ai-assistant',
    description: 'Test AI assistant integration',
    instructions: [
      'Ask the AI assistant about farming tips',
      'Request information about crop growth rates',
      'Ask how to use fertilizers effectively',
      'Get help with a specific recipe',
    ],
    expectedOutcomes: [
      'AI responds to questions',
      'Information is accurate and helpful',
      'Assistant provides actionable guidance',
    ],
    tags: ['ai', 'assistant', 'documentation'],
  },
];

async function main() {
  const args = process.argv.slice(2);

  // Parse arguments
  const host = args[0] || process.env.MC_HOST || 'localhost';
  const port = parseInt(args[1] || process.env.MC_PORT || '25565');
  const llmEndpoint = args[2] || process.env.LLM_ENDPOINT || 'http://localhost:7424';
  const scenarioId = args[3] || process.env.SCENARIO_ID;
  const outputDir = args[4] || process.env.TEST_OUTPUT_DIR || './test-output';

  console.log('🤖 FarmCraft AI Test Controller');
  console.log('================================\n');
  console.log(`Server: ${host}:${port}`);
  console.log(`LLM: ${llmEndpoint}`);
  console.log(`Output: ${outputDir}\n`);

  const controller = new AITestController(host, port, 'AITestBot', llmEndpoint, outputDir);

  try {
    await controller.connect();

    // Wait for world to load
    await controller.sleep(3000);

    // Run scenarios
    const scenariosToRun = scenarioId ? scenarios.filter((s) => s.id === scenarioId) : scenarios;

    if (scenariosToRun.length === 0) {
      console.error(`❌ No scenario found with ID: ${scenarioId}`);
      console.log('\nAvailable scenarios:');
      scenarios.forEach((s) => console.log(`  - ${s.id}: ${s.description}`));
      process.exit(1);
    }

    const results = [];
    for (const scenario of scenariosToRun) {
      const result = await controller.runScenario(scenario);
      results.push(result);

      // Wait between scenarios
      if (scenariosToRun.length > 1) {
        await controller.sleep(5000);
      }
    }

    await controller.disconnect();

    // Print summary
    console.log('\n' + '='.repeat(70));
    console.log('📊 TEST SUMMARY');
    console.log('='.repeat(70));

    const passed = results.filter((r) => r.success).length;
    const failed = results.length - passed;

    console.log(`Total: ${results.length} scenarios`);
    console.log(`✅ Passed: ${passed}`);
    console.log(`❌ Failed: ${failed}`);
    console.log(`⏱️  Total time: ${results.reduce((sum, r) => sum + r.duration, 0)}ms`);

    results.forEach((r) => {
      const status = r.success ? '✅' : '❌';
      console.log(`  ${status} ${r.scenarioId} (${r.duration}ms)`);
    });

    console.log('='.repeat(70) + '\n');

    process.exit(failed > 0 ? 1 : 0);
  } catch (error) {
    console.error('❌ Fatal error:', error);
    process.exit(1);
  }
}

if (require.main === module) {
  main();
}

export { scenarios };
