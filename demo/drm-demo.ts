#!/usr/bin/env npx ts-node

/**
 * DRM Demo - Demonstrates shader-state chain verification
 * 
 * This demo shows how the DRM system:
 * 1. Verifies client versions through shader execution
 * 2. Builds chains of proofs that are tamper-evident
 * 3. Gates resource access based on verification
 * 
 * Run: npx ts-node demo/drm-demo.ts
 */

import {
  DRMClient,
  DRMChallengeGenerator,
  DRMVerifier,
  buildVersionManifest,
  executeShader,
  SHADER_REGISTRY,
  runDRMDemo,
} from '../packages/drm-core/src';

import {
  GossipNetwork,
  ResourceSharing,
  runPeerDemo,
} from '../packages/peer-verify/src';

import { createHash } from 'crypto';

// ANSI colors for output
const colors = {
  reset: '\x1b[0m',
  bright: '\x1b[1m',
  dim: '\x1b[2m',
  red: '\x1b[31m',
  green: '\x1b[32m',
  yellow: '\x1b[33m',
  blue: '\x1b[34m',
  magenta: '\x1b[35m',
  cyan: '\x1b[36m',
};

function log(msg: string, color: string = colors.reset): void {
  console.log(color + msg + colors.reset);
}

function header(title: string): void {
  console.log();
  log('═'.repeat(60), colors.cyan);
  log(`  ${title}`, colors.bright + colors.cyan);
  log('═'.repeat(60), colors.cyan);
  console.log();
}

function section(title: string): void {
  console.log();
  log(`── ${title} ──`, colors.yellow);
}

async function sleep(ms: number): Promise<void> {
  return new Promise(resolve => setTimeout(resolve, ms));
}

// ============================================================================
// Demo: Shader Determinism
// ============================================================================

function demoShaderDeterminism(): void {
  header('Shader Determinism Demo');
  
  log('The DRM system relies on shaders producing identical outputs', colors.dim);
  log('for the same inputs. This allows the server to verify clients.\n', colors.dim);
  
  section('Testing each shader with known inputs');
  
  for (const [shaderId, config] of Object.entries(SHADER_REGISTRY)) {
    log(`\nShader: ${shaderId}`, colors.bright);
    log(`  Version: ${config.version}`, colors.dim);
    log(`  Test Seed: ${config.testSeed.substring(0, 30)}...`, colors.dim);
    
    // Execute multiple times to verify determinism
    const results: string[] = [];
    for (let i = 0; i < 3; i++) {
      results.push(executeShader(shaderId, config.testSeed));
    }
    
    const allSame = results.every(r => r === results[0]);
    
    if (allSame) {
      log(`  Output: ${results[0].substring(0, 32)}...`, colors.green);
      log(`  ✓ Deterministic (3 executions identical)`, colors.green);
    } else {
      log(`  ✗ Non-deterministic!`, colors.red);
    }
  }
}

// ============================================================================
// Demo: Version Verification
// ============================================================================

function demoVersionVerification(): void {
  header('Version Verification Demo');
  
  log('Clients prove they run the correct version by producing', colors.dim);
  log('the same shader outputs as the server expects.\n', colors.dim);
  
  // Create server components
  const manifest = buildVersionManifest('1.0.0');
  const verifier = new DRMVerifier(manifest);
  const challengeGenerator = new DRMChallengeGenerator(manifest);
  
  log(`Server version: 1.0.0`, colors.cyan);
  log(`Registered shaders: ${manifest.shaderHashes.size}\n`, colors.cyan);
  
  section('Legitimate Client');
  
  // Create legitimate client
  const legitimateClient = new DRMClient('player_legit', '1.0.0');
  const legitState = verifier.initializeClient('player_legit', '1.0.0');
  
  // Generate and solve challenge
  const challenge1 = challengeGenerator.generateChallenge(legitState, 'shader_verify');
  log(`Challenge ID: ${challenge1.challengeId.substring(0, 16)}...`);
  log(`Required shaders: ${challenge1.requiredShaders.join(', ')}`);
  log(`Difficulty: ${challenge1.difficulty}`);
  
  const response1 = legitimateClient.solveChallenge(challenge1);
  const result1 = verifier.verify(challenge1, response1);
  
  console.log();
  log(`Verification result:`, colors.bright);
  log(`  Valid: ${result1.valid}`, result1.valid ? colors.green : colors.red);
  log(`  Version Match: ${result1.versionMatch}`, result1.versionMatch ? colors.green : colors.red);
  log(`  Shader Outputs Match: ${result1.shaderOutputsMatch}`, result1.shaderOutputsMatch ? colors.green : colors.red);
  log(`  Chain Integrity: ${result1.chainIntegrity}`, result1.chainIntegrity ? colors.green : colors.red);
  
  section('Tampered Client (Wrong Version)');
  
  // Create tampered client with different version
  const tamperedClient = new DRMClient('player_hacker', '1.0.1-modded');
  const hackerState = verifier.initializeClient('player_hacker', '1.0.1-modded');
  
  const challenge2 = challengeGenerator.generateChallenge(hackerState, 'shader_verify');
  const response2 = tamperedClient.solveChallenge(challenge2);
  const result2 = verifier.verify(challenge2, response2);
  
  log(`Verification result:`, colors.bright);
  log(`  Valid: ${result2.valid}`, result2.valid ? colors.green : colors.red);
  log(`  Version Match: ${result2.versionMatch}`, result2.versionMatch ? colors.green : colors.red);
  if (result2.errors.length > 0) {
    log(`  Errors: ${result2.errors.join(', ')}`, colors.red);
  }
}

// ============================================================================
// Demo: State Chain Integrity
// ============================================================================

function demoStateChain(): void {
  header('State Chain Demo');
  
  log('Each verification builds on previous ones, creating', colors.dim);
  log('a chain of proofs that detects any tampering.\n', colors.dim);
  
  const manifest = buildVersionManifest('1.0.0');
  const verifier = new DRMVerifier(manifest);
  const challengeGenerator = new DRMChallengeGenerator(manifest);
  
  const client = new DRMClient('player_chain', '1.0.0');
  let clientState = verifier.initializeClient('player_chain', '1.0.0');
  
  log(`Initial chain hash: ${'0'.repeat(16)}...`, colors.dim);
  log(`Initial trust score: ${clientState.trustScore}\n`);
  
  // Build chain over multiple rounds
  for (let round = 1; round <= 5; round++) {
    section(`Round ${round}`);
    
    const challenge = challengeGenerator.generateChallenge(clientState, 'shader_verify');
    log(`Previous chain hash embedded: ${challenge.previousChainHash.substring(0, 16)}...`);
    
    const response = client.solveChallenge(challenge);
    const result = verifier.verify(challenge, response);
    verifier.updateClientState('player_chain', response, result);
    clientState = verifier.getClientState('player_chain')!;
    
    log(`New chain hash: ${response.stateChain[response.stateChain.length - 1].linkHash.substring(0, 16)}...`);
    log(`Chain length: ${clientState.chainLength}`);
    log(`Trust score: ${clientState.trustScore}`, 
      clientState.trustScore >= 75 ? colors.green : 
      clientState.trustScore >= 50 ? colors.yellow : colors.red);
  }
  
  section('Chain Properties');
  log(`✓ Each link contains hash of previous link`, colors.green);
  log(`✓ Shader outputs prove correct code execution`, colors.green);
  log(`✓ Work proofs prevent replay attacks`, colors.green);
  log(`✓ Any modification breaks chain integrity`, colors.green);
}

// ============================================================================
// Demo: Resource Gating
// ============================================================================

function demoResourceGating(): void {
  header('Resource Gating Demo');
  
  log('Gated resources require minimum trust scores.', colors.dim);
  log('Trust is earned through successful verifications.\n', colors.dim);
  
  const resources = [
    { id: 'recipe:basic_fertilizer', minTrust: 0, name: 'Basic Fertilizer' },
    { id: 'recipe:advanced_farming', minTrust: 60, name: 'Advanced Farming' },
    { id: 'recipe:power_food_supreme', minTrust: 70, name: 'Supreme Power Food' },
    { id: 'recipe:legendary_fertilizer', minTrust: 80, name: 'Legendary Fertilizer' },
  ];
  
  log('Resource Requirements:', colors.bright);
  for (const resource of resources) {
    log(`  ${resource.name}: Trust ≥ ${resource.minTrust}`);
  }
  
  section('Access Simulation');
  
  const trustLevels = [30, 50, 65, 75, 90];
  
  for (const trust of trustLevels) {
    console.log();
    log(`Client with trust score: ${trust}`, colors.cyan);
    
    for (const resource of resources) {
      const hasAccess = trust >= resource.minTrust;
      const icon = hasAccess ? '✓' : '✗';
      const color = hasAccess ? colors.green : colors.red;
      log(`  ${icon} ${resource.name}`, color);
    }
  }
}

// ============================================================================
// Demo: Peer Verification
// ============================================================================

function demoPeerVerification(): void {
  header('Peer-to-Peer Verification Demo');
  
  log('Peers can verify each other without a central server,', colors.dim);
  log('enabling trustless resource sharing.\n', colors.dim);
  
  // Create peers
  const peer1 = new GossipNetwork('peer_alice', '1.0.0');
  const peer2 = new GossipNetwork('peer_bob', '1.0.0');
  const malicious = new GossipNetwork('peer_eve', '1.0.1-hacked');
  
  section('Network Discovery');
  
  // Peer 1 announces
  const announce1 = peer1.createAnnouncement('chain_hash_abc');
  log(`Alice announces: version 1.0.0`, colors.blue);
  
  // Peer 2 processes
  const response = peer2.processMessage(announce1);
  log(`Bob processes: ${response ? 'responds with ' + response.type : 'accepts (same version)'}`, colors.blue);
  
  // Malicious peer announces
  const announceEvil = malicious.createAnnouncement('chain_hash_xyz');
  log(`Eve announces: version 1.0.1-hacked`, colors.magenta);
  
  const challengeMsg = peer1.processMessage(announceEvil);
  log(`Alice: ${challengeMsg?.type === 'challenge' ? 'CHALLENGES Eve!' : 'accepts'}`, 
    challengeMsg?.type === 'challenge' ? colors.yellow : colors.red);
  
  section('Mutual Verification');
  
  // Peer 1 challenges Peer 2
  const verifier1 = peer1.getVerifier();
  const challenge = verifier1.createChallenge('peer_bob');
  
  log(`Alice challenges Bob with ${challenge.shaderTests.length} shader tests`);
  
  // Peer 2 responds
  const verifier2 = peer2.getVerifier();
  const peerResponse = verifier2.respondToChallenge(challenge, []);
  
  log(`Bob executes shaders and responds`);
  
  // Verify
  const verifyResult = verifier1.verifyResponse(peerResponse);
  log(`Alice verifies: ${verifyResult.valid ? 'VALID' : 'INVALID'}`, 
    verifyResult.valid ? colors.green : colors.red);
  
  if (verifyResult.errors.length > 0) {
    log(`  Errors: ${verifyResult.errors.join(', ')}`, colors.red);
  }
}

// ============================================================================
// Demo: Full Workflow
// ============================================================================

async function demoFullWorkflow(): Promise<void> {
  header('Complete DRM Workflow');
  
  log('This simulates a full client session:\n', colors.dim);
  
  const steps = [
    '1. Client connects to server',
    '2. Server sends DRM challenge',
    '3. Client executes shaders with seeds',
    '4. Client computes work proof',
    '5. Client submits response with state chain',
    '6. Server verifies outputs match expected',
    '7. Server grants access token',
    '8. Client requests gated recipe',
    '9. Server validates token and trust',
    '10. Server grants recipe access',
  ];
  
  const manifest = buildVersionManifest('1.0.0');
  const verifier = new DRMVerifier(manifest);
  const challengeGenerator = new DRMChallengeGenerator(manifest);
  const client = new DRMClient('player_demo', '1.0.0');
  
  for (let i = 0; i < steps.length; i++) {
    await sleep(300);
    
    const step = steps[i];
    
    // Simulate step
    switch (i) {
      case 0:
        log(`${step}`, colors.cyan);
        log(`  → Connecting to ws://server:3001...`, colors.dim);
        break;
        
      case 1:
        log(`${step}`, colors.cyan);
        const state = verifier.initializeClient('player_demo', '1.0.0');
        const challenge = challengeGenerator.generateChallenge(state, 'shader_verify');
        log(`  → Challenge ID: ${challenge.challengeId.substring(0, 16)}...`, colors.dim);
        log(`  → Shaders: ${challenge.requiredShaders.join(', ')}`, colors.dim);
        (client as any).currentChallenge = challenge;
        break;
        
      case 2:
        log(`${step}`, colors.cyan);
        for (const shader of ['version_proof_v1', 'hash_compute_v1']) {
          log(`  → Executing ${shader}...`, colors.dim);
        }
        break;
        
      case 3:
        log(`${step}`, colors.cyan);
        log(`  → Finding nonce with ${1} leading zeros...`, colors.dim);
        break;
        
      case 4:
        log(`${step}`, colors.cyan);
        log(`  → Sending state chain with 2 links...`, colors.dim);
        break;
        
      case 5:
        log(`${step}`, colors.cyan);
        log(`  → Comparing outputs: `, colors.dim);
        log(`    version_proof_v1: ✓ match`, colors.green);
        log(`    hash_compute_v1: ✓ match`, colors.green);
        break;
        
      case 6:
        log(`${step}`, colors.green);
        log(`  → Token: 7f4a9c2e...`, colors.dim);
        log(`  → Trust score: 55`, colors.dim);
        break;
        
      case 7:
        log(`${step}`, colors.cyan);
        log(`  → Requesting: recipe:advanced_farming`, colors.dim);
        log(`  → Token attached: 7f4a9c2e...`, colors.dim);
        break;
        
      case 8:
        log(`${step}`, colors.cyan);
        log(`  → Token valid: ✓`, colors.green);
        log(`  → Trust (55) >= Required (60): ✗`, colors.red);
        log(`  → Completing more challenges...`, colors.yellow);
        break;
        
      case 9:
        log(`${step}`, colors.green);
        log(`  → After 2 more challenges, trust: 65`, colors.dim);
        log(`  → Trust (65) >= Required (60): ✓`, colors.green);
        log(`  → Recipe data sent to client!`, colors.green);
        break;
    }
  }
  
  section('Summary');
  log(`✓ Version verified through shader execution`, colors.green);
  log(`✓ State chain built and validated`, colors.green);
  log(`✓ Work proof completed (useful computation)`, colors.green);
  log(`✓ Access token granted for resource requests`, colors.green);
  log(`✓ Trust score gates premium content`, colors.green);
}

// ============================================================================
// Main
// ============================================================================

async function main(): Promise<void> {
  console.clear();
  log(`
  ███████╗ █████╗ ██████╗ ███╗   ███╗ ██████╗██████╗  █████╗ ███████╗████████╗
  ██╔════╝██╔══██╗██╔══██╗████╗ ████║██╔════╝██╔══██╗██╔══██╗██╔════╝╚══██╔══╝
  █████╗  ███████║██████╔╝██╔████╔██║██║     ██████╔╝███████║█████╗     ██║   
  ██╔══╝  ██╔══██║██╔══██╗██║╚██╔╝██║██║     ██╔══██╗██╔══██║██╔══╝     ██║   
  ██║     ██║  ██║██║  ██║██║ ╚═╝ ██║╚██████╗██║  ██║██║  ██║██║        ██║   
  ╚═╝     ╚═╝  ╚═╝╚═╝  ╚═╝╚═╝     ╚═╝ ╚═════╝╚═╝  ╚═╝╚═╝  ╚═╝╚═╝        ╚═╝   
                                                                              
                    DRM & Shader Verification Demo
  `, colors.magenta);
  
  const demos = [
    { name: 'Shader Determinism', fn: demoShaderDeterminism },
    { name: 'Version Verification', fn: demoVersionVerification },
    { name: 'State Chain', fn: demoStateChain },
    { name: 'Resource Gating', fn: demoResourceGating },
    { name: 'Peer Verification', fn: demoPeerVerification },
    { name: 'Full Workflow', fn: demoFullWorkflow },
  ];
  
  for (const demo of demos) {
    await demo.fn();
    await sleep(500);
  }
  
  header('Demo Complete');
  log('The DRM system provides:', colors.bright);
  log('  • Version verification without code signing', colors.green);
  log('  • Tamper-evident state chains', colors.green);
  log('  • Useful work during verification (protein folding)', colors.green);
  log('  • Peer-to-peer verification for decentralization', colors.green);
  log('  • Progressive trust for premium content access', colors.green);
  
  console.log();
}

main().catch(console.error);
