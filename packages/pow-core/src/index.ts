/**
 * FarmCraft Proof-of-Work Core
 * Core algorithms for proof-of-work challenges and validation
 */

import { createHash, randomBytes } from 'crypto';
import type {
  WorkChallenge,
  WorkSolution,
  WorkToken,
  WorkType,
  WorkPayload,
} from '@farmcraft/types';

// ============================================================================
// Challenge Generation
// ============================================================================

export interface ChallengeOptions {
  type: WorkType;
  difficulty: number;
  expirationMinutes?: number;
  rewardTokens?: number;
}

export function generateChallenge(options: ChallengeOptions): WorkChallenge {
  const {
    type,
    difficulty,
    expirationMinutes = 30,
    rewardTokens = calculateReward(difficulty),
  } = options;

  const id = generateChallengeId();
  const now = Date.now();

  const payload = generatePayload(type, difficulty);

  return {
    id,
    type,
    difficulty,
    payload,
    issuedAt: now,
    expiresAt: now + expirationMinutes * 60 * 1000,
    rewardTokens,
  };
}

function generateChallengeId(): string {
  return `challenge_${randomBytes(16).toString('hex')}`;
}

function generatePayload(type: WorkType, difficulty: number): WorkPayload {
  switch (type) {
    case 'hash_challenge':
      return generateHashChallengePayload(difficulty);
    case 'protein_folding':
      return generateFoldingPayload(difficulty);
    case 'entropy_generation':
      return generateEntropyPayload(difficulty);
    case 'shader_compute':
      return generateShaderPayload(difficulty);
    default:
      throw new Error(`Unknown work type: ${type}`);
  }
}

function generateHashChallengePayload(difficulty: number): WorkPayload {
  const prefix = randomBytes(8).toString('hex');
  // Target difficulty: number of leading zeros required
  const targetDifficulty = Math.min(Math.floor(difficulty / 10) + 1, 8);
  
  return {
    prefix,
    targetDifficulty,
  };
}

function generateFoldingPayload(difficulty: number): WorkPayload {
  // Generate a simple protein sequence for the challenge
  const aminoAcids = 'ACDEFGHIKLMNPQRSTVWY';
  const length = 20 + difficulty * 5;
  let sequence = '';
  for (let i = 0; i < length; i++) {
    sequence += aminoAcids[Math.floor(Math.random() * aminoAcids.length)];
  }
  
  return {
    proteinSequence: sequence,
    targetStructure: 'minimize_energy',
  };
}

function generateEntropyPayload(difficulty: number): WorkPayload {
  const seed = randomBytes(32).toString('hex');
  return {
    data: {
      seed,
      iterations: 1000 * difficulty,
      outputSize: 64,
    },
  };
}

function generateShaderPayload(difficulty: number): WorkPayload {
  // WGSL shader program for compute
  const shaderProgram = `
@group(0) @binding(0) var<storage, read> input: array<u32>;
@group(0) @binding(1) var<storage, read_write> output: array<u32>;

@compute @workgroup_size(64)
fn main(@builtin(global_invocation_id) global_id: vec3<u32>) {
    let idx = global_id.x;
    if (idx >= arrayLength(&input)) {
        return;
    }
    
    var value = input[idx];
    for (var i = 0u; i < ${100 * difficulty}u; i = i + 1u) {
        value = value ^ (value << 13u);
        value = value ^ (value >> 17u);
        value = value ^ (value << 5u);
    }
    output[idx] = value;
}`;

  const inputData = randomBytes(1024 * difficulty).toString('base64');
  
  return {
    shaderProgram,
    inputData,
  };
}

function calculateReward(difficulty: number): number {
  // Base reward is 10, scaled by difficulty
  return Math.floor(10 * Math.pow(1.5, difficulty - 1));
}

// ============================================================================
// Solution Verification
// ============================================================================

export interface VerificationResult {
  valid: boolean;
  reason?: string;
  entropyGenerated?: Uint8Array;
}

export function verifySolution(
  challenge: WorkChallenge,
  solution: WorkSolution
): VerificationResult {
  // Check if challenge has expired
  if (Date.now() > challenge.expiresAt) {
    return { valid: false, reason: 'Challenge has expired' };
  }

  // Check challenge ID matches
  if (solution.challengeId !== challenge.id) {
    return { valid: false, reason: 'Challenge ID mismatch' };
  }

  switch (challenge.type) {
    case 'hash_challenge':
      return verifyHashChallenge(challenge, solution);
    case 'protein_folding':
      return verifyFoldingChallenge(challenge, solution);
    case 'entropy_generation':
      return verifyEntropyChallenge(challenge, solution);
    case 'shader_compute':
      return verifyShaderChallenge(challenge, solution);
    default:
      return { valid: false, reason: 'Unknown challenge type' };
  }
}

function verifyHashChallenge(
  challenge: WorkChallenge,
  solution: WorkSolution
): VerificationResult {
  const { prefix, targetDifficulty } = challenge.payload;
  const nonce = solution.solution as string;
  
  if (!prefix || !targetDifficulty || !nonce) {
    return { valid: false, reason: 'Invalid challenge or solution format' };
  }

  const hash = createHash('sha256')
    .update(prefix + nonce)
    .digest('hex');

  const requiredPrefix = '0'.repeat(targetDifficulty);
  
  if (!hash.startsWith(requiredPrefix)) {
    return { valid: false, reason: 'Hash does not meet difficulty requirement' };
  }

  // Extract entropy from the hash
  const entropyGenerated = new Uint8Array(Buffer.from(hash, 'hex'));

  return { valid: true, entropyGenerated };
}

function verifyFoldingChallenge(
  challenge: WorkChallenge,
  solution: WorkSolution
): VerificationResult {
  const solutionData = solution.solution as {
    configuration: number[];
    energy: number;
    iterations: number;
  };

  if (!solutionData || !solutionData.configuration || typeof solutionData.energy !== 'number') {
    return { valid: false, reason: 'Invalid folding solution format' };
  }

  // Verify the configuration is reasonable
  const sequence = challenge.payload.proteinSequence;
  if (!sequence) {
    return { valid: false, reason: 'No protein sequence in challenge' };
  }

  // Each amino acid needs 3 coordinates (x, y, z)
  const expectedLength = sequence.length * 3;
  if (solutionData.configuration.length !== expectedLength) {
    return { valid: false, reason: 'Configuration length mismatch' };
  }

  // Verify energy is below threshold (simplified validation)
  const maxEnergy = sequence.length * 10; // Simplified threshold
  if (solutionData.energy > maxEnergy) {
    return { valid: false, reason: 'Energy too high - solution not optimal enough' };
  }

  // Generate entropy from the solution
  const configBuffer = Buffer.alloc(solutionData.configuration.length * 4);
  solutionData.configuration.forEach((val, i) => {
    configBuffer.writeFloatLE(val, i * 4);
  });
  const entropyHash = createHash('sha256').update(configBuffer).digest();
  
  return { valid: true, entropyGenerated: new Uint8Array(entropyHash) };
}

function verifyEntropyChallenge(
  challenge: WorkChallenge,
  solution: WorkSolution
): VerificationResult {
  const data = challenge.payload.data as {
    seed: string;
    iterations: number;
    outputSize: number;
  };

  const solutionData = solution.solution as { entropy: string };
  
  if (!solutionData || !solutionData.entropy) {
    return { valid: false, reason: 'Invalid entropy solution format' };
  }

  // Verify the entropy was generated correctly
  let hash = createHash('sha256').update(data.seed).digest();
  
  for (let i = 1; i < data.iterations; i++) {
    hash = createHash('sha256').update(hash).digest();
  }

  const expectedEntropy = hash.toString('hex').substring(0, data.outputSize * 2);
  
  if (solutionData.entropy !== expectedEntropy) {
    return { valid: false, reason: 'Entropy verification failed' };
  }

  return { valid: true, entropyGenerated: new Uint8Array(hash) };
}

function verifyShaderChallenge(
  challenge: WorkChallenge,
  solution: WorkSolution
): VerificationResult {
  // For shader challenges, we verify the output hash
  const solutionData = solution.solution as {
    outputHash: string;
    executionProof: string;
  };

  if (!solutionData || !solutionData.outputHash) {
    return { valid: false, reason: 'Invalid shader solution format' };
  }

  // GPU stats provide additional validation
  if (solution.gpuUsed && solution.shaderStats) {
    // Verify execution time is reasonable
    const minExpectedTime = challenge.difficulty * 10; // ms
    if (solution.shaderStats.shaderExecutionTimeMs < minExpectedTime) {
      return { valid: false, reason: 'Execution time too fast - possible spoofing' };
    }
  }

  // Extract entropy from output hash
  const entropyGenerated = new Uint8Array(
    Buffer.from(solutionData.outputHash, 'hex').subarray(0, 32)
  );

  return { valid: true, entropyGenerated };
}

// ============================================================================
// Token Management
// ============================================================================

const TOKEN_SECRET = process.env.TOKEN_SECRET || 'farmcraft-dev-secret';

export function generateToken(
  playerId: string,
  credits: number,
  validityHours: number = 24
): WorkToken {
  const id = `token_${randomBytes(16).toString('hex')}`;
  const now = Date.now();
  const expiresAt = now + validityHours * 60 * 60 * 1000;

  const signatureData = `${id}:${playerId}:${credits}:${expiresAt}`;
  const signature = createHash('sha256')
    .update(signatureData + TOKEN_SECRET)
    .digest('hex');

  return {
    id,
    playerId,
    credits,
    issuedAt: now,
    expiresAt,
    signature,
  };
}

export function verifyToken(token: WorkToken): boolean {
  if (Date.now() > token.expiresAt) {
    return false;
  }

  const signatureData = `${token.id}:${token.playerId}:${token.credits}:${token.expiresAt}`;
  const expectedSignature = createHash('sha256')
    .update(signatureData + TOKEN_SECRET)
    .digest('hex');

  return token.signature === expectedSignature;
}

export function consumeTokenCredits(
  token: WorkToken,
  amount: number
): WorkToken | null {
  if (!verifyToken(token)) {
    return null;
  }

  if (token.credits < amount) {
    return null;
  }

  // Create a new token with reduced credits
  return generateToken(
    token.playerId,
    token.credits - amount,
    Math.floor((token.expiresAt - Date.now()) / (60 * 60 * 1000))
  );
}

// ============================================================================
// Difficulty Scaling
// ============================================================================

export interface DifficultyConfig {
  baseDifficulty: number;
  maxDifficulty: number;
  scaleFactor: number;
}

export function calculateDifficulty(
  completedChallenges: number,
  averageComputeTimeMs: number,
  config: DifficultyConfig = {
    baseDifficulty: 1,
    maxDifficulty: 20,
    scaleFactor: 0.1,
  }
): number {
  const { baseDifficulty, maxDifficulty, scaleFactor } = config;

  // Increase difficulty based on completed challenges
  const completionBonus = Math.floor(completedChallenges * scaleFactor);

  // Adjust based on compute time (if they're solving too fast, increase difficulty)
  const targetTimeMs = 5000; // 5 seconds target
  const timeRatio = targetTimeMs / Math.max(averageComputeTimeMs, 100);
  const timeAdjustment = Math.floor(Math.log2(timeRatio));

  const difficulty = baseDifficulty + completionBonus + timeAdjustment;

  return Math.max(1, Math.min(maxDifficulty, difficulty));
}

// ============================================================================
// Exports
// ============================================================================

export {
  WorkChallenge,
  WorkSolution,
  WorkToken,
  WorkType,
} from '@farmcraft/types';
