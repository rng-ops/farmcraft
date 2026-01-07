/**
 * Challenge Manager
 * Manages proof-of-work challenges and solutions
 */

import type { WorkChallenge, WorkSolution, WorkToken, Recipe } from '@farmcraft/types';
import {
  generateChallenge,
  verifySolution,
  generateToken,
  ChallengeOptions,
} from '@farmcraft/pow-core';

export interface ChallengeRequest {
  playerId: string;
  preferredType?: string;
  maxDifficulty?: number;
}

export interface VerificationResult {
  valid: boolean;
  reason?: string;
  canRetry?: boolean;
  tokensEarned?: number;
  token?: WorkToken;
  bonusRecipes?: Recipe[];
}

export class ChallengeManager {
  private activeChallenges: Map<string, WorkChallenge> = new Map();
  private playerChallenges: Map<string, string[]> = new Map(); // playerId -> challengeIds
  private completedWorkUnits: number = 0;
  private totalComputeMs: number = 0;

  createChallenge(request: ChallengeRequest): WorkChallenge {
    const { playerId, preferredType, maxDifficulty } = request;

    // Determine difficulty based on player history
    const playerChallengeCount = this.getPlayerChallengeCount(playerId);
    const difficulty = Math.min(
      maxDifficulty || 20,
      Math.floor(playerChallengeCount / 5) + 1
    );

    const options: ChallengeOptions = {
      type: (preferredType as any) || this.selectWorkType(),
      difficulty,
      expirationMinutes: 30,
    };

    const challenge = generateChallenge(options);
    
    this.activeChallenges.set(challenge.id, challenge);
    
    // Track player's challenges
    const playerChallenges = this.playerChallenges.get(playerId) || [];
    playerChallenges.push(challenge.id);
    this.playerChallenges.set(playerId, playerChallenges);

    return challenge;
  }

  private selectWorkType(): 'hash_challenge' | 'protein_folding' | 'entropy_generation' {
    const types: Array<'hash_challenge' | 'protein_folding' | 'entropy_generation'> = [
      'hash_challenge',
      'protein_folding',
      'entropy_generation',
    ];
    
    // Weighted selection - prefer protein folding for scientific contribution
    const weights = [0.2, 0.6, 0.2];
    const random = Math.random();
    let cumulative = 0;
    
    for (let i = 0; i < types.length; i++) {
      cumulative += weights[i];
      if (random < cumulative) {
        return types[i];
      }
    }
    
    return 'protein_folding';
  }

  getChallenge(challengeId: string): WorkChallenge | undefined {
    return this.activeChallenges.get(challengeId);
  }

  verifySolution(solution: WorkSolution): VerificationResult {
    const challenge = this.activeChallenges.get(solution.challengeId);
    
    if (!challenge) {
      return {
        valid: false,
        reason: 'Challenge not found or expired',
        canRetry: false,
      };
    }

    const result = verifySolution(challenge, solution);

    if (!result.valid) {
      return {
        valid: false,
        reason: result.reason,
        canRetry: true,
      };
    }

    // Remove the challenge (it's been solved)
    this.activeChallenges.delete(solution.challengeId);
    
    // Update statistics
    this.completedWorkUnits++;
    this.totalComputeMs += solution.computeTimeMs;

    // Generate token reward
    const token = generateToken('player', challenge.rewardTokens, 24);

    // Check for bonus recipes (rare chance based on difficulty)
    const bonusRecipes: Recipe[] = [];
    if (Math.random() < challenge.difficulty * 0.01) {
      // Could add a discovered recipe here
    }

    return {
      valid: true,
      tokensEarned: challenge.rewardTokens,
      token,
      bonusRecipes,
    };
  }

  getActiveChallengeCount(): number {
    return this.activeChallenges.size;
  }

  getPlayerChallengeCount(playerId: string): number {
    return this.playerChallenges.get(playerId)?.length || 0;
  }

  getCompletedWorkUnits(): number {
    return this.completedWorkUnits;
  }

  getTotalComputeHours(): number {
    return this.totalComputeMs / (1000 * 60 * 60);
  }

  // Cleanup expired challenges
  cleanupExpiredChallenges(): number {
    const now = Date.now();
    let cleaned = 0;

    for (const [id, challenge] of this.activeChallenges) {
      if (now > challenge.expiresAt) {
        this.activeChallenges.delete(id);
        cleaned++;
      }
    }

    return cleaned;
  }
}
