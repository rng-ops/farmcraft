/**
 * FarmCraft Folding Client
 * Client-side protein folding simulation for proof-of-work
 */

import type { FoldingWorkUnit, FoldingResult } from '@farmcraft/types';

// ============================================================================
// Types
// ============================================================================

export interface AminoAcid {
  code: string;
  name: string;
  hydrophobicity: number;
  charge: number;
  size: number;
}

export interface Atom {
  x: number;
  y: number;
  z: number;
}

export interface FoldingState {
  positions: Atom[];
  energy: number;
  iteration: number;
}

export interface FoldingProgress {
  currentIteration: number;
  maxIterations: number;
  currentEnergy: number;
  bestEnergy: number;
  percentComplete: number;
}

export type ProgressCallback = (progress: FoldingProgress) => void;

// ============================================================================
// Amino Acid Properties
// ============================================================================

const AMINO_ACIDS: Record<string, AminoAcid> = {
  'A': { code: 'A', name: 'Alanine', hydrophobicity: 1.8, charge: 0, size: 1 },
  'C': { code: 'C', name: 'Cysteine', hydrophobicity: 2.5, charge: 0, size: 1 },
  'D': { code: 'D', name: 'Aspartic Acid', hydrophobicity: -3.5, charge: -1, size: 2 },
  'E': { code: 'E', name: 'Glutamic Acid', hydrophobicity: -3.5, charge: -1, size: 3 },
  'F': { code: 'F', name: 'Phenylalanine', hydrophobicity: 2.8, charge: 0, size: 3 },
  'G': { code: 'G', name: 'Glycine', hydrophobicity: -0.4, charge: 0, size: 0 },
  'H': { code: 'H', name: 'Histidine', hydrophobicity: -3.2, charge: 0.5, size: 3 },
  'I': { code: 'I', name: 'Isoleucine', hydrophobicity: 4.5, charge: 0, size: 2 },
  'K': { code: 'K', name: 'Lysine', hydrophobicity: -3.9, charge: 1, size: 3 },
  'L': { code: 'L', name: 'Leucine', hydrophobicity: 3.8, charge: 0, size: 2 },
  'M': { code: 'M', name: 'Methionine', hydrophobicity: 1.9, charge: 0, size: 3 },
  'N': { code: 'N', name: 'Asparagine', hydrophobicity: -3.5, charge: 0, size: 2 },
  'P': { code: 'P', name: 'Proline', hydrophobicity: -1.6, charge: 0, size: 1 },
  'Q': { code: 'Q', name: 'Glutamine', hydrophobicity: -3.5, charge: 0, size: 3 },
  'R': { code: 'R', name: 'Arginine', hydrophobicity: -4.5, charge: 1, size: 4 },
  'S': { code: 'S', name: 'Serine', hydrophobicity: -0.8, charge: 0, size: 1 },
  'T': { code: 'T', name: 'Threonine', hydrophobicity: -0.7, charge: 0, size: 1 },
  'V': { code: 'V', name: 'Valine', hydrophobicity: 4.2, charge: 0, size: 1 },
  'W': { code: 'W', name: 'Tryptophan', hydrophobicity: -0.9, charge: 0, size: 4 },
  'Y': { code: 'Y', name: 'Tyrosine', hydrophobicity: -1.3, charge: 0, size: 3 },
};

// ============================================================================
// Energy Calculation
// ============================================================================

const BOND_LENGTH = 3.8; // Å - typical Cα-Cα distance
const BOND_STIFFNESS = 100.0;
const LJ_EPSILON = 1.0;
const LJ_SIGMA = 3.4;
const ELECTROSTATIC_K = 332.0;

function distance(a: Atom, b: Atom): number {
  const dx = a.x - b.x;
  const dy = a.y - b.y;
  const dz = a.z - b.z;
  return Math.sqrt(dx * dx + dy * dy + dz * dz);
}

function calculateBondEnergy(positions: Atom[]): number {
  let energy = 0;
  for (let i = 0; i < positions.length - 1; i++) {
    const d = distance(positions[i], positions[i + 1]);
    const deviation = d - BOND_LENGTH;
    energy += BOND_STIFFNESS * deviation * deviation;
  }
  return energy;
}

function calculateLennardJonesEnergy(
  positions: Atom[],
  sequence: string
): number {
  let energy = 0;
  
  for (let i = 0; i < positions.length - 2; i++) {
    for (let j = i + 2; j < positions.length; j++) {
      const d = distance(positions[i], positions[j]);
      if (d < 0.1) continue; // Avoid singularity
      
      const aa1 = AMINO_ACIDS[sequence[i]];
      const aa2 = AMINO_ACIDS[sequence[j]];
      
      // Hydrophobic interaction modifier
      const hydrophobicMod = (aa1.hydrophobicity + aa2.hydrophobicity) / 10;
      const effectiveEpsilon = LJ_EPSILON * (1 + hydrophobicMod);
      
      const sigma6 = Math.pow(LJ_SIGMA / d, 6);
      const sigma12 = sigma6 * sigma6;
      
      energy += 4 * effectiveEpsilon * (sigma12 - sigma6);
    }
  }
  
  return energy;
}

function calculateElectrostaticEnergy(
  positions: Atom[],
  sequence: string
): number {
  let energy = 0;
  
  for (let i = 0; i < positions.length - 2; i++) {
    for (let j = i + 2; j < positions.length; j++) {
      const aa1 = AMINO_ACIDS[sequence[i]];
      const aa2 = AMINO_ACIDS[sequence[j]];
      
      if (aa1.charge === 0 || aa2.charge === 0) continue;
      
      const d = distance(positions[i], positions[j]);
      if (d < 0.1) continue;
      
      energy += ELECTROSTATIC_K * aa1.charge * aa2.charge / d;
    }
  }
  
  return energy;
}

export function calculateTotalEnergy(positions: Atom[], sequence: string): number {
  const bondEnergy = calculateBondEnergy(positions);
  const ljEnergy = calculateLennardJonesEnergy(positions, sequence);
  const electrostaticEnergy = calculateElectrostaticEnergy(positions, sequence);
  
  return bondEnergy + ljEnergy + electrostaticEnergy;
}

// ============================================================================
// Initialization
// ============================================================================

export function initializeLinearChain(sequence: string): Atom[] {
  const positions: Atom[] = [];
  
  for (let i = 0; i < sequence.length; i++) {
    positions.push({
      x: i * BOND_LENGTH,
      y: 0,
      z: 0,
    });
  }
  
  return positions;
}

export function initializeRandomCoil(sequence: string): Atom[] {
  const positions: Atom[] = [{ x: 0, y: 0, z: 0 }];
  
  for (let i = 1; i < sequence.length; i++) {
    // Random direction on unit sphere
    const theta = Math.random() * 2 * Math.PI;
    const phi = Math.acos(2 * Math.random() - 1);
    
    const prev = positions[i - 1];
    positions.push({
      x: prev.x + BOND_LENGTH * Math.sin(phi) * Math.cos(theta),
      y: prev.y + BOND_LENGTH * Math.sin(phi) * Math.sin(theta),
      z: prev.z + BOND_LENGTH * Math.cos(phi),
    });
  }
  
  return positions;
}

// ============================================================================
// Optimization
// ============================================================================

function gradientDescent(
  positions: Atom[],
  sequence: string,
  stepSize: number = 0.01
): Atom[] {
  const newPositions = positions.map(p => ({ ...p }));
  const epsilon = 0.001;
  
  for (let i = 0; i < positions.length; i++) {
    // Numerical gradient for each coordinate
    for (const coord of ['x', 'y', 'z'] as const) {
      const original = newPositions[i][coord];
      
      newPositions[i][coord] = original + epsilon;
      const energyPlus = calculateTotalEnergy(newPositions, sequence);
      
      newPositions[i][coord] = original - epsilon;
      const energyMinus = calculateTotalEnergy(newPositions, sequence);
      
      newPositions[i][coord] = original;
      
      const gradient = (energyPlus - energyMinus) / (2 * epsilon);
      newPositions[i][coord] -= stepSize * gradient;
    }
  }
  
  return newPositions;
}

function simulatedAnnealing(
  positions: Atom[],
  sequence: string,
  temperature: number
): Atom[] {
  const newPositions = positions.map(p => ({ ...p }));
  
  // Random perturbation
  const idx = Math.floor(Math.random() * positions.length);
  const perturbation = 0.5;
  
  newPositions[idx].x += (Math.random() - 0.5) * perturbation;
  newPositions[idx].y += (Math.random() - 0.5) * perturbation;
  newPositions[idx].z += (Math.random() - 0.5) * perturbation;
  
  const oldEnergy = calculateTotalEnergy(positions, sequence);
  const newEnergy = calculateTotalEnergy(newPositions, sequence);
  
  const deltaE = newEnergy - oldEnergy;
  
  // Metropolis criterion
  if (deltaE < 0 || Math.random() < Math.exp(-deltaE / temperature)) {
    return newPositions;
  }
  
  return positions;
}

// ============================================================================
// Main Folding Function
// ============================================================================

export interface FoldingOptions {
  maxIterations?: number;
  initialTemperature?: number;
  coolingRate?: number;
  checkpointInterval?: number;
  useGradientDescent?: boolean;
  progressCallback?: ProgressCallback;
}

export async function foldProtein(
  workUnit: FoldingWorkUnit,
  options: FoldingOptions = {}
): Promise<FoldingResult> {
  const {
    maxIterations = workUnit.maxIterations || 10000,
    initialTemperature = 100.0,
    coolingRate = 0.995,
    checkpointInterval = workUnit.checkpointInterval || 100,
    useGradientDescent = true,
    progressCallback,
  } = options;

  const sequence = workUnit.sequence;
  
  // Initialize from starting configuration or random coil
  let positions: Atom[];
  if (workUnit.startingConfiguration && workUnit.startingConfiguration.length > 0) {
    positions = [];
    const config = Array.from(workUnit.startingConfiguration);
    for (let i = 0; i < config.length; i += 3) {
      positions.push({ x: config[i], y: config[i + 1], z: config[i + 2] });
    }
  } else {
    positions = initializeRandomCoil(sequence);
  }

  let bestPositions = positions.map(p => ({ ...p }));
  let bestEnergy = calculateTotalEnergy(positions, sequence);
  let temperature = initialTemperature;

  const startTime = Date.now();
  let iteration = 0;

  for (iteration = 0; iteration < maxIterations; iteration++) {
    // Alternate between gradient descent and simulated annealing
    if (useGradientDescent && iteration % 10 === 0) {
      positions = gradientDescent(positions, sequence, 0.01 / (1 + iteration / 1000));
    } else {
      positions = simulatedAnnealing(positions, sequence, temperature);
    }

    const currentEnergy = calculateTotalEnergy(positions, sequence);
    
    if (currentEnergy < bestEnergy) {
      bestEnergy = currentEnergy;
      bestPositions = positions.map(p => ({ ...p }));
    }

    // Cool down
    temperature *= coolingRate;

    // Progress callback
    if (progressCallback && iteration % checkpointInterval === 0) {
      progressCallback({
        currentIteration: iteration,
        maxIterations,
        currentEnergy,
        bestEnergy,
        percentComplete: (iteration / maxIterations) * 100,
      });
    }

    // Check for convergence
    if (bestEnergy <= workUnit.targetEnergy) {
      break;
    }

    // Yield to event loop periodically
    if (iteration % 100 === 0) {
      await new Promise(resolve => setImmediate(resolve));
    }
  }

  // Convert positions to flat array
  const finalConfiguration = new Float32Array(bestPositions.length * 3);
  bestPositions.forEach((pos, i) => {
    finalConfiguration[i * 3] = pos.x;
    finalConfiguration[i * 3 + 1] = pos.y;
    finalConfiguration[i * 3 + 2] = pos.z;
  });

  // Generate entropy from the folding process
  const entropyGenerated = generateEntropy(bestPositions, iteration, Date.now() - startTime);

  return {
    workUnitId: workUnit.id,
    finalConfiguration: Array.from(finalConfiguration),
    finalEnergy: bestEnergy,
    iterationsCompleted: iteration,
    convergenceReached: bestEnergy <= workUnit.targetEnergy,
    entropyGenerated: Array.from(entropyGenerated),
  };
}

function generateEntropy(
  positions: Atom[],
  iterations: number,
  timeMs: number
): Uint8Array {
  // Generate entropy from the folding result
  const entropy = new Uint8Array(32);
  
  let hash = 0;
  for (let i = 0; i < positions.length; i++) {
    hash = ((hash << 5) - hash + Math.floor(positions[i].x * 1000)) | 0;
    hash = ((hash << 5) - hash + Math.floor(positions[i].y * 1000)) | 0;
    hash = ((hash << 5) - hash + Math.floor(positions[i].z * 1000)) | 0;
  }

  // Mix in timing information
  hash = ((hash << 5) - hash + iterations) | 0;
  hash = ((hash << 5) - hash + timeMs) | 0;

  // Fill entropy array
  for (let i = 0; i < 32; i++) {
    hash = ((hash << 5) - hash + i) | 0;
    entropy[i] = Math.abs(hash) % 256;
  }

  return entropy;
}

// ============================================================================
// Exports
// ============================================================================

export type { FoldingWorkUnit, FoldingResult } from '@farmcraft/types';
