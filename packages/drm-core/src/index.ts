/**
 * @farmcraft/drm-core
 * 
 * DRM and version verification through shader state chains.
 * 
 * Concept:
 * - Each client runs shaders that produce deterministic outputs
 * - Shader outputs become seeds for subsequent challenges
 * - The chain of states proves the client has the correct shaders
 * - Tampered clients produce different state chains and fail verification
 */

import { createHash, randomBytes } from 'crypto';

// ============================================================================
// Types
// ============================================================================

export interface ShaderFingerprint {
  shaderId: string;
  version: string;
  inputSeed: string;
  outputHash: string;
  timestamp: number;
}

export interface StateChainLink {
  index: number;
  previousHash: string;
  shaderFingerprint: ShaderFingerprint;
  workProof: string;
  linkHash: string;
}

export interface VersionManifest {
  version: string;
  shaderHashes: Map<string, string>;
  expectedOutputs: Map<string, string>; // seed -> expected output hash
  buildTimestamp: number;
  signature: string;
}

export interface DRMChallenge {
  challengeId: string;
  requiredShaders: string[];
  inputSeeds: Map<string, string>;
  previousChainHash: string;
  difficulty: number;
  expiresAt: number;
  workType: 'shader_verify' | 'folding_chain' | 'entropy_chain';
}

export interface DRMResponse {
  challengeId: string;
  clientVersion: string;
  stateChain: StateChainLink[];
  workResult: string;
  nonce: number;
  clientSignature: string;
}

export interface VerificationResult {
  valid: boolean;
  versionMatch: boolean;
  chainIntegrity: boolean;
  workValid: boolean;
  shaderOutputsMatch: boolean;
  errors: string[];
}

export interface ClientState {
  clientId: string;
  version: string;
  lastChainHash: string;
  chainLength: number;
  totalWorkCompleted: number;
  lastVerifiedAt: number;
  trustScore: number;
}

// ============================================================================
// Shader State Registry
// ============================================================================

/**
 * Known shader programs and their expected behaviors
 */
export const SHADER_REGISTRY = {
  'hash_compute_v1': {
    version: '1.0.0',
    // Expected output for test seed - used to verify shader correctness
    testSeed: '0000000000000000000000000000000000000000000000000000000000000000',
    expectedTestOutput: 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855',
  },
  'folding_energy_v1': {
    version: '1.0.0',
    testSeed: 'ACDEFGHIKLMNPQRSTVWY', // amino acid sequence
    expectedTestOutput: '7d865e959b2466918c9863afca942d0fb89d7c9ac0c99bafc3749504ded97730',
  },
  'entropy_v1': {
    version: '1.0.0',
    testSeed: '1234567890abcdef',
    expectedTestOutput: 'a591a6d40bf420404a011733cfb7b190d62c65bf0bcda32b57b277d9ad9f146e',
  },
  'version_proof_v1': {
    version: '1.0.0',
    testSeed: 'version_check',
    expectedTestOutput: '2c26b46b68ffc68ff99b453c1d30413413422d706483bfa0f98a5e886266e7ae',
  },
};

// ============================================================================
// Shader Execution Simulator
// ============================================================================

/**
 * Simulates shader execution with deterministic outputs.
 * In the real mod, this would be actual GPU shader execution.
 */
export function executeShader(shaderId: string, inputSeed: string): string {
  // Each shader type has different computation characteristics
  switch (shaderId) {
    case 'hash_compute_v1':
      return computeHashShader(inputSeed);
    case 'folding_energy_v1':
      return computeFoldingShader(inputSeed);
    case 'entropy_v1':
      return computeEntropyShader(inputSeed);
    case 'version_proof_v1':
      return computeVersionProofShader(inputSeed);
    default:
      throw new Error(`Unknown shader: ${shaderId}`);
  }
}

function computeHashShader(input: string): string {
  // Simulates iterative SHA-256 like a GPU would compute
  let state = input;
  for (let i = 0; i < 1000; i++) {
    state = createHash('sha256').update(state + i.toString()).digest('hex');
  }
  return state;
}

function computeFoldingShader(aminoSequence: string): string {
  // Simulates energy calculation for protein folding
  // Real implementation would use Lennard-Jones potential
  let energy = 0;
  const positions: number[] = [];
  
  for (let i = 0; i < aminoSequence.length; i++) {
    const char = aminoSequence.charCodeAt(i);
    positions.push(Math.sin(char * 0.1) * 10);
    positions.push(Math.cos(char * 0.1) * 10);
    positions.push(Math.sin(char * 0.2) * 10);
  }
  
  // Calculate pairwise interactions
  for (let i = 0; i < positions.length; i += 3) {
    for (let j = i + 3; j < positions.length; j += 3) {
      const dx = positions[i] - positions[j];
      const dy = positions[i + 1] - positions[j + 1];
      const dz = positions[i + 2] - positions[j + 2];
      const r2 = dx * dx + dy * dy + dz * dz;
      const r6 = r2 * r2 * r2;
      energy += 1 / r6 - 2 / (r6 * r6);
    }
  }
  
  return createHash('sha256')
    .update(energy.toFixed(10) + aminoSequence)
    .digest('hex');
}

function computeEntropyShader(input: string): string {
  // Simulates entropy generation through chaotic mixing
  const state = new Uint32Array(16);
  const inputBytes = Buffer.from(input);
  
  // Initialize state
  for (let i = 0; i < 16; i++) {
    state[i] = inputBytes[i % inputBytes.length] * 0x01000193;
  }
  
  // Chaotic mixing rounds
  for (let round = 0; round < 100; round++) {
    for (let i = 0; i < 16; i++) {
      const a = state[i];
      const b = state[(i + 1) % 16];
      const c = state[(i + 5) % 16];
      state[i] = (a ^ (b << 7) ^ (c >>> 3)) >>> 0;
    }
  }
  
  return createHash('sha256')
    .update(Buffer.from(state.buffer))
    .digest('hex');
}

function computeVersionProofShader(input: string): string {
  // Version-specific computation that differs per build
  // This would be compiled differently in each version
  const versionSalt = 'farmcraft_v1.0.0_build_2026';
  return createHash('sha256')
    .update(input + versionSalt)
    .digest('hex');
}

// ============================================================================
// State Chain Management
// ============================================================================

export class StateChain {
  private links: StateChainLink[] = [];
  private currentHash: string = '0'.repeat(64); // Genesis hash
  
  constructor(private clientVersion: string) {}
  
  /**
   * Add a new link to the chain by executing a shader
   */
  addLink(shaderId: string, inputSeed: string, workProof: string): StateChainLink {
    const shaderOutput = executeShader(shaderId, inputSeed);
    
    const fingerprint: ShaderFingerprint = {
      shaderId,
      version: this.clientVersion,
      inputSeed,
      outputHash: shaderOutput,
      timestamp: Date.now(),
    };
    
    const linkData = JSON.stringify({
      index: this.links.length,
      previousHash: this.currentHash,
      fingerprint,
      workProof,
    });
    
    const linkHash = createHash('sha256').update(linkData).digest('hex');
    
    const link: StateChainLink = {
      index: this.links.length,
      previousHash: this.currentHash,
      shaderFingerprint: fingerprint,
      workProof,
      linkHash,
    };
    
    this.links.push(link);
    this.currentHash = linkHash;
    
    return link;
  }
  
  /**
   * Get the current chain hash (for embedding in next challenge)
   */
  getChainHash(): string {
    return this.currentHash;
  }
  
  /**
   * Get all links for verification
   */
  getLinks(): StateChainLink[] {
    return [...this.links];
  }
  
  /**
   * Export chain for transmission
   */
  export(): { links: StateChainLink[]; chainHash: string; version: string } {
    return {
      links: this.links,
      chainHash: this.currentHash,
      version: this.clientVersion,
    };
  }
  
  /**
   * Import existing chain
   */
  static import(data: { links: StateChainLink[]; chainHash: string; version: string }): StateChain {
    const chain = new StateChain(data.version);
    chain.links = data.links;
    chain.currentHash = data.chainHash;
    return chain;
  }
}

// ============================================================================
// DRM Challenge Generator
// ============================================================================

export class DRMChallengeGenerator {
  private activeManifest: VersionManifest;
  
  constructor(manifest: VersionManifest) {
    this.activeManifest = manifest;
  }
  
  /**
   * Generate a challenge that requires specific shader execution
   */
  generateChallenge(
    clientState: ClientState,
    workType: DRMChallenge['workType'] = 'shader_verify'
  ): DRMChallenge {
    const challengeId = randomBytes(16).toString('hex');
    const requiredShaders = this.selectRequiredShaders(workType);
    const inputSeeds = new Map<string, string>();
    
    // Generate seeds that chain from previous state
    for (const shaderId of requiredShaders) {
      // Seed includes previous chain hash to create dependency
      const seed = createHash('sha256')
        .update(clientState.lastChainHash + shaderId + challengeId)
        .digest('hex');
      inputSeeds.set(shaderId, seed);
    }
    
    // Difficulty scales with trust score (lower trust = harder challenges)
    const difficulty = Math.max(1, 5 - Math.floor(clientState.trustScore / 20));
    
    return {
      challengeId,
      requiredShaders,
      inputSeeds,
      previousChainHash: clientState.lastChainHash,
      difficulty,
      expiresAt: Date.now() + 60000, // 1 minute
      workType,
    };
  }
  
  private selectRequiredShaders(workType: DRMChallenge['workType']): string[] {
    switch (workType) {
      case 'shader_verify':
        return ['version_proof_v1', 'hash_compute_v1'];
      case 'folding_chain':
        return ['folding_energy_v1', 'version_proof_v1'];
      case 'entropy_chain':
        return ['entropy_v1', 'hash_compute_v1', 'version_proof_v1'];
      default:
        return ['version_proof_v1'];
    }
  }
  
  /**
   * Get the expected outputs for a challenge (server-side)
   */
  getExpectedOutputs(challenge: DRMChallenge): Map<string, string> {
    const expected = new Map<string, string>();
    
    for (const [shaderId, seed] of challenge.inputSeeds) {
      // Server computes expected output using canonical shader
      const output = executeShader(shaderId, seed);
      expected.set(shaderId, output);
    }
    
    return expected;
  }
}

// ============================================================================
// DRM Verifier
// ============================================================================

export class DRMVerifier {
  private manifest: VersionManifest;
  private clientStates: Map<string, ClientState> = new Map();
  
  constructor(manifest: VersionManifest) {
    this.manifest = manifest;
  }
  
  /**
   * Verify a DRM response from a client
   */
  verify(challenge: DRMChallenge, response: DRMResponse): VerificationResult {
    const errors: string[] = [];
    let versionMatch = true;
    let chainIntegrity = true;
    let workValid = true;
    let shaderOutputsMatch = true;
    
    // Check challenge hasn't expired
    if (Date.now() > challenge.expiresAt) {
      errors.push('Challenge expired');
      return { valid: false, versionMatch, chainIntegrity, workValid, shaderOutputsMatch, errors };
    }
    
    // Verify challenge ID matches
    if (response.challengeId !== challenge.challengeId) {
      errors.push('Challenge ID mismatch');
      return { valid: false, versionMatch, chainIntegrity, workValid, shaderOutputsMatch, errors };
    }
    
    // Verify version matches manifest
    if (response.clientVersion !== this.manifest.version) {
      versionMatch = false;
      errors.push(`Version mismatch: expected ${this.manifest.version}, got ${response.clientVersion}`);
    }
    
    // Verify state chain integrity
    chainIntegrity = this.verifyChainIntegrity(response.stateChain, challenge.previousChainHash);
    if (!chainIntegrity) {
      errors.push('State chain integrity check failed');
    }
    
    // Verify shader outputs match expected
    const expectedOutputs = new Map<string, string>();
    for (const [shaderId, seed] of challenge.inputSeeds) {
      expectedOutputs.set(shaderId, executeShader(shaderId, seed));
    }
    
    for (const link of response.stateChain) {
      const expected = expectedOutputs.get(link.shaderFingerprint.shaderId);
      if (expected && link.shaderFingerprint.outputHash !== expected) {
        shaderOutputsMatch = false;
        errors.push(`Shader output mismatch for ${link.shaderFingerprint.shaderId}`);
      }
    }
    
    // Verify work proof meets difficulty
    workValid = this.verifyWorkProof(response.workResult, response.nonce, challenge.difficulty);
    if (!workValid) {
      errors.push('Work proof invalid or insufficient difficulty');
    }
    
    const valid = versionMatch && chainIntegrity && workValid && shaderOutputsMatch;
    
    return {
      valid,
      versionMatch,
      chainIntegrity,
      workValid,
      shaderOutputsMatch,
      errors,
    };
  }
  
  private verifyChainIntegrity(chain: StateChainLink[], expectedPreviousHash: string): boolean {
    if (chain.length === 0) return false;
    
    // First link should reference the expected previous hash
    if (chain[0].previousHash !== expectedPreviousHash) {
      return false;
    }
    
    // Verify each link
    for (let i = 0; i < chain.length; i++) {
      const link = chain[i];
      
      // Verify link hash - note: indices are cumulative in the full chain
      const linkData = JSON.stringify({
        index: link.index,
        previousHash: link.previousHash,
        fingerprint: link.shaderFingerprint,
        workProof: link.workProof,
      });
      const expectedHash = createHash('sha256').update(linkData).digest('hex');
      if (link.linkHash !== expectedHash) return false;
      
      // Verify chain continuity within this response
      if (i > 0 && link.previousHash !== chain[i - 1].linkHash) {
        return false;
      }
    }
    
    return true;
  }
  
  private verifyWorkProof(result: string, nonce: number, difficulty: number): boolean {
    // The result already contains the hash that was found with the nonce
    // Verify it has the required leading zeros
    for (let i = 0; i < difficulty; i++) {
      if (result[i] !== '0') return false;
    }
    
    return true;
  }
  
  /**
   * Update client state after successful verification
   */
  updateClientState(clientId: string, response: DRMResponse, result: VerificationResult): void {
    const existing = this.clientStates.get(clientId);
    const lastLink = response.stateChain[response.stateChain.length - 1];
    
    const newState: ClientState = {
      clientId,
      version: response.clientVersion,
      lastChainHash: lastLink?.linkHash ?? '0'.repeat(64),
      chainLength: (existing?.chainLength ?? 0) + response.stateChain.length,
      totalWorkCompleted: (existing?.totalWorkCompleted ?? 0) + 1,
      lastVerifiedAt: Date.now(),
      trustScore: this.calculateTrustScore(existing, result),
    };
    
    this.clientStates.set(clientId, newState);
  }
  
  private calculateTrustScore(existing: ClientState | undefined, result: VerificationResult): number {
    let score = existing?.trustScore ?? 50;
    
    if (result.valid) {
      score = Math.min(100, score + 5);
    } else {
      score = Math.max(0, score - 20);
    }
    
    if (!result.versionMatch) score = Math.max(0, score - 30);
    if (!result.shaderOutputsMatch) score = Math.max(0, score - 50);
    
    return score;
  }
  
  /**
   * Get client state
   */
  getClientState(clientId: string): ClientState | undefined {
    return this.clientStates.get(clientId);
  }
  
  /**
   * Initialize new client
   */
  initializeClient(clientId: string, version: string): ClientState {
    const state: ClientState = {
      clientId,
      version,
      lastChainHash: '0'.repeat(64),
      chainLength: 0,
      totalWorkCompleted: 0,
      lastVerifiedAt: Date.now(),
      trustScore: 50,
    };
    this.clientStates.set(clientId, state);
    return state;
  }
}

// ============================================================================
// Client-side DRM Handler
// ============================================================================

export class DRMClient {
  private stateChain: StateChain;
  private clientId: string;
  
  constructor(clientId: string, version: string) {
    this.clientId = clientId;
    this.stateChain = new StateChain(version);
  }
  
  /**
   * Solve a DRM challenge
   */
  solveChallenge(challenge: DRMChallenge): DRMResponse {
    const newLinks: StateChainLink[] = [];
    
    // Execute each required shader and add to chain
    for (const shaderId of challenge.requiredShaders) {
      const seed = challenge.inputSeeds.get(shaderId);
      if (!seed) throw new Error(`Missing seed for shader ${shaderId}`);
      
      // Do work proof for each link
      const workProof = this.computeWorkProof(shaderId, seed, challenge.difficulty);
      const link = this.stateChain.addLink(shaderId, seed, workProof);
      newLinks.push(link);
    }
    
    // Compute final work result with nonce
    const { result, nonce } = this.findValidNonce(
      this.stateChain.getChainHash(),
      challenge.difficulty
    );
    
    return {
      challengeId: challenge.challengeId,
      clientVersion: this.stateChain.export().version,
      stateChain: newLinks,
      workResult: result,
      nonce,
      clientSignature: this.signResponse(result),
    };
  }
  
  private computeWorkProof(shaderId: string, seed: string, difficulty: number): string {
    // Simple work proof - hash iterations
    let proof = seed;
    const iterations = Math.pow(10, difficulty);
    for (let i = 0; i < iterations; i++) {
      proof = createHash('sha256').update(proof + shaderId).digest('hex');
    }
    return proof;
  }
  
  private findValidNonce(data: string, difficulty: number): { result: string; nonce: number } {
    let nonce = 0;
    while (true) {
      const result = createHash('sha256')
        .update(data + nonce.toString())
        .digest('hex');
      
      let valid = true;
      for (let i = 0; i < difficulty; i++) {
        if (result[i] !== '0') {
          valid = false;
          break;
        }
      }
      
      if (valid) {
        return { result, nonce };
      }
      nonce++;
    }
  }
  
  private signResponse(data: string): string {
    // In real implementation, use proper cryptographic signing
    return createHash('sha256')
      .update(this.clientId + data + Date.now())
      .digest('hex');
  }
  
  /**
   * Get current chain state
   */
  getChainState(): { chainHash: string; chainLength: number } {
    return {
      chainHash: this.stateChain.getChainHash(),
      chainLength: this.stateChain.getLinks().length,
    };
  }
}

// ============================================================================
// Version Manifest Builder
// ============================================================================

export function buildVersionManifest(version: string): VersionManifest {
  const shaderHashes = new Map<string, string>();
  const expectedOutputs = new Map<string, string>();
  
  for (const [shaderId, config] of Object.entries(SHADER_REGISTRY)) {
    // Hash the shader "code" (simulated as config)
    const shaderHash = createHash('sha256')
      .update(JSON.stringify(config))
      .digest('hex');
    shaderHashes.set(shaderId, shaderHash);
    
    // Compute expected output for test seed
    const output = executeShader(shaderId, config.testSeed);
    expectedOutputs.set(config.testSeed, output);
  }
  
  const manifestData = JSON.stringify({
    version,
    shaderHashes: Object.fromEntries(shaderHashes),
    expectedOutputs: Object.fromEntries(expectedOutputs),
    buildTimestamp: Date.now(),
  });
  
  const signature = createHash('sha256')
    .update(manifestData + 'farmcraft_signing_key')
    .digest('hex');
  
  return {
    version,
    shaderHashes,
    expectedOutputs,
    buildTimestamp: Date.now(),
    signature,
  };
}

// ============================================================================
// Demo / Test Functions
// ============================================================================

export function runDRMDemo(): void {
  console.log('=== FarmCraft DRM Demo ===\n');
  
  // Build version manifest
  const manifest = buildVersionManifest('1.0.0');
  console.log(`Version manifest created for v${manifest.version}`);
  console.log(`Shaders registered: ${manifest.shaderHashes.size}`);
  
  // Create verifier (server-side)
  const verifier = new DRMVerifier(manifest);
  const challengeGenerator = new DRMChallengeGenerator(manifest);
  
  // Create client
  const clientId = 'player_12345';
  const client = new DRMClient(clientId, '1.0.0');
  
  // Initialize client on server
  let clientState = verifier.initializeClient(clientId, '1.0.0');
  console.log(`\nClient ${clientId} initialized with trust score: ${clientState.trustScore}`);
  
  // Run several challenge rounds
  for (let round = 1; round <= 3; round++) {
    console.log(`\n--- Round ${round} ---`);
    
    // Generate challenge
    const challenge = challengeGenerator.generateChallenge(clientState, 'shader_verify');
    console.log(`Challenge generated: ${challenge.challengeId.substring(0, 16)}...`);
    console.log(`Required shaders: ${challenge.requiredShaders.join(', ')}`);
    console.log(`Difficulty: ${challenge.difficulty}`);
    
    // Client solves challenge
    const startTime = Date.now();
    const response = client.solveChallenge(challenge);
    const solveTime = Date.now() - startTime;
    console.log(`Challenge solved in ${solveTime}ms`);
    console.log(`Work nonce found: ${response.nonce}`);
    
    // Server verifies
    const result = verifier.verify(challenge, response);
    console.log(`\nVerification result:`);
    console.log(`  Valid: ${result.valid}`);
    console.log(`  Version match: ${result.versionMatch}`);
    console.log(`  Chain integrity: ${result.chainIntegrity}`);
    console.log(`  Shader outputs match: ${result.shaderOutputsMatch}`);
    console.log(`  Work valid: ${result.workValid}`);
    
    if (result.errors.length > 0) {
      console.log(`  Errors: ${result.errors.join(', ')}`);
    }
    
    // Update client state
    verifier.updateClientState(clientId, response, result);
    clientState = verifier.getClientState(clientId)!;
    console.log(`\nUpdated trust score: ${clientState.trustScore}`);
    console.log(`Chain length: ${clientState.chainLength}`);
  }
  
  // Demo tampered client
  console.log('\n\n=== Tampered Client Demo ===');
  const tamperedClient = new DRMClient('hacker_999', '1.0.1'); // Wrong version
  const hackerState = verifier.initializeClient('hacker_999', '1.0.1');
  
  const challenge = challengeGenerator.generateChallenge(hackerState, 'shader_verify');
  const response = tamperedClient.solveChallenge(challenge);
  const result = verifier.verify(challenge, response);
  
  console.log(`\nTampered client verification:`);
  console.log(`  Valid: ${result.valid}`);
  console.log(`  Version match: ${result.versionMatch}`);
  console.log(`  Errors: ${result.errors.join(', ')}`);
  
  console.log('\n=== Demo Complete ===');
}

// Export for CLI
export { runDRMDemo as demo };
