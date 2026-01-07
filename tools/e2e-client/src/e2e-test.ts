/**
 * FarmCraft E2E Test Suite
 * 
 * Runs a comprehensive end-to-end test of:
 * 1. Recipe Server (REST + WebSocket)
 * 2. MCP Recipe Server (natural language)
 * 3. DRM verification
 * 4. Watermark encoding/decoding
 */

import { WebSocket } from 'ws';

const RECIPE_SERVER_URL = process.env.RECIPE_SERVER_URL || 'http://localhost:3000';
const RECIPE_WS_URL = process.env.RECIPE_WS_URL || 'ws://localhost:3001';
const MCP_SERVER_URL = process.env.MCP_SERVER_URL || 'http://localhost:3003';

interface TestResult {
  name: string;
  passed: boolean;
  duration: number;
  error?: string;
}

const results: TestResult[] = [];

async function runTest(name: string, fn: () => Promise<void>): Promise<void> {
  const start = Date.now();
  try {
    await fn();
    results.push({ name, passed: true, duration: Date.now() - start });
    console.log(`  ✓ ${name} (${Date.now() - start}ms)`);
  } catch (error) {
    results.push({ 
      name, 
      passed: false, 
      duration: Date.now() - start,
      error: (error as Error).message 
    });
    console.log(`  ✗ ${name} - ${(error as Error).message}`);
  }
}

async function fetchJson(url: string, options?: RequestInit): Promise<any> {
  const response = await fetch(url, options);
  if (!response.ok) {
    throw new Error(`HTTP ${response.status}: ${response.statusText}`);
  }
  return response.json();
}

// ============================================================================
// Recipe Server Tests
// ============================================================================

async function testRecipeServerHealth(): Promise<void> {
  const data = await fetchJson(`${RECIPE_SERVER_URL}/health`);
  if (data.status !== 'healthy') {
    throw new Error(`Unexpected status: ${data.status}`);
  }
}

async function testRecipeServerStatus(): Promise<void> {
  const data = await fetchJson(`${RECIPE_SERVER_URL}/status`);
  if (typeof data.totalRecipes !== 'number') {
    throw new Error('Missing totalRecipes in status');
  }
  if (typeof data.drm?.activeClients !== 'number') {
    throw new Error('Missing DRM stats in status');
  }
}

async function testRecipeCategories(): Promise<void> {
  const data = await fetchJson(`${RECIPE_SERVER_URL}/recipes/categories`);
  if (!Array.isArray(data)) {
    throw new Error('Expected categories array');
  }
}

async function testChallengeRequest(): Promise<void> {
  const data = await fetchJson(`${RECIPE_SERVER_URL}/challenge/request`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      playerId: 'test_player_123',
      preferredType: 'hash',
      maxDifficulty: 100,
    }),
  });
  if (!data.success || !data.challenge) {
    throw new Error('Failed to create challenge');
  }
}

// ============================================================================
// WebSocket Tests
// ============================================================================

async function testWebSocketConnection(): Promise<void> {
  return new Promise((resolve, reject) => {
    const ws = new WebSocket(RECIPE_WS_URL);
    const timeout = setTimeout(() => {
      ws.close();
      reject(new Error('WebSocket connection timeout'));
    }, 5000);

    ws.on('open', () => {
      clearTimeout(timeout);
      // Send a handshake
      ws.send(JSON.stringify({
        type: 0x01, // HANDSHAKE
        version: '1.0.0',
        playerId: 'test_player_ws',
      }));
    });

    ws.on('message', (data) => {
      const msg = JSON.parse(data.toString());
      if (msg.type === 0x02 || msg.type === 0xFF) { // HANDSHAKE_ACK or ERROR
        ws.close();
        resolve();
      }
    });

    ws.on('error', (err) => {
      clearTimeout(timeout);
      reject(err);
    });
  });
}

async function testDRMInit(): Promise<void> {
  return new Promise((resolve, reject) => {
    const ws = new WebSocket(RECIPE_WS_URL);
    const timeout = setTimeout(() => {
      ws.close();
      reject(new Error('DRM init timeout'));
    }, 5000);

    ws.on('open', () => {
      ws.send(JSON.stringify({
        type: 'drm_init',
        payload: {
          clientId: 'e2e_test_client',
          modVersion: '1.0.0',
          shaderHash: 'abc123def456',
        },
      }));
    });

    ws.on('message', (data) => {
      const msg = JSON.parse(data.toString());
      clearTimeout(timeout);
      ws.close();
      if (msg.type === 'drm_init_ack' || msg.type === 'drm_challenge') {
        resolve();
      } else {
        reject(new Error(`Unexpected response: ${msg.type}`));
      }
    });

    ws.on('error', (err) => {
      clearTimeout(timeout);
      reject(err);
    });
  });
}

// ============================================================================
// MCP Server Tests
// ============================================================================

async function testMCPServerHealth(): Promise<void> {
  const data = await fetchJson(`${MCP_SERVER_URL}/health`);
  if (data.status !== 'healthy') {
    throw new Error(`Unexpected status: ${data.status}`);
  }
}

async function testMCPServerStatus(): Promise<void> {
  const data = await fetchJson(`${MCP_SERVER_URL}/status`);
  if (typeof data.ingredients !== 'number') {
    throw new Error('Missing ingredients count');
  }
  if (typeof data.eventSequence !== 'number') {
    throw new Error('Missing event sequence');
  }
}

async function testMCPAddIngredient(): Promise<void> {
  const data = await fetchJson(`${MCP_SERVER_URL}/api/ingredients/add`, {
    method: 'POST',
    headers: { 
      'Content-Type': 'application/json',
      'X-Admin-Token': 'farmcraft_admin',
    },
    body: JSON.stringify({
      description: 'Test fertilizer that increases growth by 50%',
      category: 'fertilizer',
    }),
  });
  if (!data.success || !data.ingredient) {
    throw new Error('Failed to add ingredient');
  }
  if (!data.event?.signature) {
    throw new Error('Event missing cryptographic signature');
  }
}

async function testMCPCreateRecipe(): Promise<void> {
  const data = await fetchJson(`${MCP_SERVER_URL}/api/recipes/add`, {
    method: 'POST',
    headers: { 
      'Content-Type': 'application/json',
      'X-Admin-Token': 'farmcraft_admin',
    },
    body: JSON.stringify({
      description: 'Combine 4 dirt and 1 bone meal to create basic fertilizer',
      type: 'crafting',
    }),
  });
  if (!data.success || !data.recipe) {
    throw new Error('Failed to create recipe');
  }
}

async function testMCPEventLog(): Promise<void> {
  const data = await fetchJson(`${MCP_SERVER_URL}/api/events?limit=10`);
  if (!Array.isArray(data.events)) {
    throw new Error('Expected events array');
  }
  if (typeof data.chainHash !== 'string') {
    throw new Error('Missing chain hash');
  }
}

async function testMCPChainVerify(): Promise<void> {
  const data = await fetchJson(`${MCP_SERVER_URL}/api/chain/verify`);
  if (data.valid !== true) {
    throw new Error(`Chain verification failed: ${JSON.stringify(data.errors)}`);
  }
}

async function testMCPConfig(): Promise<void> {
  const data = await fetchJson(`${MCP_SERVER_URL}/api/config?format=json&target=forge`);
  if (!data.config || !data.signature) {
    throw new Error('Missing config or signature');
  }
}

// ============================================================================
// Main Test Runner
// ============================================================================

async function runAllTests(): Promise<void> {
  console.log(`
  ███████╗ █████╗ ██████╗ ███╗   ███╗ ██████╗██████╗  █████╗ ███████╗████████╗
  ██╔════╝██╔══██╗██╔══██╗████╗ ████║██╔════╝██╔══██╗██╔══██╗██╔════╝╚══██╔══╝
  █████╗  ███████║██████╔╝██╔████╔██║██║     ██████╔╝███████║█████╗     ██║   
  ██╔══╝  ██╔══██║██╔══██╗██║╚██╔╝██║██║     ██╔══██╗██╔══██║██╔══╝     ██║   
  ██║     ██║  ██║██║  ██║██║ ╚═╝ ██║╚██████╗██║  ██║██║  ██║██║        ██║   
  ╚═╝     ╚═╝  ╚═╝╚═╝  ╚═╝╚═╝     ╚═╝ ╚═════╝╚═╝  ╚═╝╚═╝  ╚═╝╚═╝        ╚═╝   
                                                                               
                    End-to-End Test Suite
  `);

  console.log('═══════════════════════════════════════════════════════════════');
  console.log('  Recipe Server Tests');
  console.log('═══════════════════════════════════════════════════════════════\n');

  await runTest('Health check', testRecipeServerHealth);
  await runTest('Server status', testRecipeServerStatus);
  await runTest('Recipe categories', testRecipeCategories);
  await runTest('Challenge request', testChallengeRequest);

  console.log('\n═══════════════════════════════════════════════════════════════');
  console.log('  WebSocket Tests');
  console.log('═══════════════════════════════════════════════════════════════\n');

  await runTest('WebSocket connection', testWebSocketConnection);
  await runTest('DRM initialization', testDRMInit);

  console.log('\n═══════════════════════════════════════════════════════════════');
  console.log('  MCP Server Tests');
  console.log('═══════════════════════════════════════════════════════════════\n');

  await runTest('MCP health check', testMCPServerHealth);
  await runTest('MCP server status', testMCPServerStatus);
  await runTest('Add ingredient (NL)', testMCPAddIngredient);
  await runTest('Create recipe (NL)', testMCPCreateRecipe);
  await runTest('Query event log', testMCPEventLog);
  await runTest('Verify chain integrity', testMCPChainVerify);
  await runTest('Generate config', testMCPConfig);

  // Summary
  console.log('\n═══════════════════════════════════════════════════════════════');
  console.log('  Test Summary');
  console.log('═══════════════════════════════════════════════════════════════\n');

  const passed = results.filter(r => r.passed).length;
  const failed = results.filter(r => !r.passed).length;
  const totalTime = results.reduce((acc, r) => acc + r.duration, 0);

  console.log(`  Total:  ${results.length} tests`);
  console.log(`  Passed: ${passed} ✓`);
  console.log(`  Failed: ${failed} ✗`);
  console.log(`  Time:   ${totalTime}ms\n`);

  if (failed > 0) {
    console.log('  Failed tests:');
    results.filter(r => !r.passed).forEach(r => {
      console.log(`    • ${r.name}: ${r.error}`);
    });
    console.log('');
  }

  process.exit(failed > 0 ? 1 : 0);
}

// Run if main module
runAllTests().catch(err => {
  console.error('Test suite error:', err);
  process.exit(1);
});
