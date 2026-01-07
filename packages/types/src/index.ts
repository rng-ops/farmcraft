/**
 * FarmCraft Types
 * Core type definitions for the entire monorepo
 */

// ============================================================================
// Minecraft Items & Blocks
// ============================================================================

export type MinecraftBlockId = 
  | 'minecraft:tuff'
  | 'minecraft:diorite'
  | 'minecraft:calcite'
  | 'minecraft:gravel'
  | 'minecraft:granite'
  | 'minecraft:andesite'
  | 'minecraft:deepslate'
  | 'minecraft:cobblestone'
  | 'minecraft:bone_block'
  | 'minecraft:clay';

export type FertilizerTier = 'basic' | 'enhanced' | 'superior' | 'legendary';

export interface FertilizerType {
  id: string;
  name: string;
  tier: FertilizerTier;
  baseIngredients: MinecraftBlockId[];
  effects: FertilizerEffect[];
  color: number; // RGB hex color
  particleType: string;
}

export interface FertilizerEffect {
  type: FertilizerEffectType;
  magnitude: number; // 0.0 - 1.0 scale
  duration?: number; // in ticks, for time-based effects
}

export type FertilizerEffectType =
  | 'growth_speed'
  | 'yield_bonus'
  | 'effect_duration'
  | 'effect_potency'
  | 'rare_drop_chance'
  | 'mutation_chance';

// ============================================================================
// Power Foods
// ============================================================================

export type PotionEffectId = 
  | 'minecraft:speed'
  | 'minecraft:strength'
  | 'minecraft:resistance'
  | 'minecraft:night_vision'
  | 'minecraft:jump_boost'
  | 'minecraft:regeneration'
  | 'minecraft:fire_resistance'
  | 'minecraft:water_breathing'
  | 'minecraft:haste'
  | 'minecraft:luck';

export interface PowerFoodEffect {
  effectId: PotionEffectId;
  amplifier: number; // 0 = Level I, 1 = Level II, etc.
  duration: number; // in ticks (20 ticks = 1 second)
}

export interface PowerFood {
  id: string;
  name: string;
  baseCrop: string;
  fertilizerUsed: string;
  effects: PowerFoodEffect[];
  hungerRestored: number;
  saturation: number;
  rarity: 'common' | 'uncommon' | 'rare' | 'epic';
}

// ============================================================================
// Recipes
// ============================================================================

export interface RecipeIngredient {
  itemId: string;
  count: number;
  metadata?: Record<string, unknown>;
}

export interface Recipe {
  id: string;
  name: string;
  category: RecipeCategory;
  inputs: RecipeIngredient[];
  output: RecipeIngredient;
  craftingTime?: number; // in ticks, for timed crafting
  requiredTier: FertilizerTier;
  discoveredAt?: number; // timestamp when discovered
  discoveredBy?: string; // player UUID who discovered it
}

export type RecipeCategory = 
  | 'fertilizer'
  | 'power_food'
  | 'tool'
  | 'upgrade'
  | 'special';

// ============================================================================
// Proof of Work
// ============================================================================

export interface WorkChallenge {
  id: string;
  type: WorkType;
  difficulty: number;
  payload: WorkPayload;
  issuedAt: number;
  expiresAt: number;
  rewardTokens: number;
}

export type WorkType = 
  | 'protein_folding'
  | 'hash_challenge'
  | 'entropy_generation'
  | 'shader_compute';

export interface WorkPayload {
  // For protein folding
  proteinSequence?: string;
  targetStructure?: string;
  
  // For hash challenges
  prefix?: string;
  targetDifficulty?: number;
  
  // For shader compute
  shaderProgram?: string;
  inputData?: ArrayBuffer | string;
  
  // Generic data
  data?: unknown;
}

export interface WorkSolution {
  challengeId: string;
  solution: unknown;
  computeTimeMs: number;
  clientVersion: string;
  gpuUsed?: boolean;
  shaderStats?: ShaderComputeStats;
}

export interface ShaderComputeStats {
  gpuVendor: string;
  gpuModel: string;
  shaderExecutionTimeMs: number;
  workgroupsDispatched: number;
}

export interface WorkToken {
  id: string;
  playerId: string;
  credits: number;
  issuedAt: number;
  expiresAt: number;
  signature: string;
}

// ============================================================================
// Protein Folding Specific
// ============================================================================

export interface FoldingWorkUnit {
  id: string;
  projectId: string;
  proteinName: string;
  sequence: string;
  startingConfiguration: Float32Array | number[];
  targetEnergy: number;
  maxIterations: number;
  checkpointInterval: number;
}

export interface FoldingResult {
  workUnitId: string;
  finalConfiguration: Float32Array | number[];
  finalEnergy: number;
  iterationsCompleted: number;
  convergenceReached: boolean;
  entropyGenerated: Uint8Array | number[];
}

// ============================================================================
// Server Communication
// ============================================================================

export interface RecipeRequest {
  playerId: string;
  token?: WorkToken;
  requestedCategories?: RecipeCategory[];
  clientVersion: string;
}

export interface RecipeResponse {
  success: boolean;
  recipes?: Recipe[];
  newChallenge?: WorkChallenge;
  tokensRemaining?: number;
  error?: string;
}

export interface ServerStatus {
  online: boolean;
  version: string;
  totalRecipes: number;
  activeChallenges: number;
  connectedClients: number;
  foldingStats: {
    workUnitsCompleted: number;
    contributingPlayers: number;
    totalComputeHours: number;
  };
}

// ============================================================================
// Events
// ============================================================================

export type GameEvent = 
  | { type: 'recipe_discovered'; recipe: Recipe; playerId: string }
  | { type: 'work_completed'; solution: WorkSolution; playerId: string }
  | { type: 'token_earned'; token: WorkToken; playerId: string }
  | { type: 'fertilizer_applied'; fertilizer: FertilizerType; blockPos: BlockPos }
  | { type: 'power_food_consumed'; food: PowerFood; playerId: string };

export interface BlockPos {
  x: number;
  y: number;
  z: number;
  dimension: string;
}
