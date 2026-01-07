/**
 * DRM Server Handlers
 * 
 * Handles version verification and resource gating through
 * shader-state proof-of-work chains.
 */

import { WebSocket } from 'ws';
import {
  DRMChallengeGenerator,
  DRMVerifier,
  DRMChallenge,
  DRMResponse,
  ClientState,
  buildVersionManifest,
  VersionManifest,
} from '@farmcraft/drm-core';
import { getSession, updateSession } from './sessions';

// Build version manifest for current server version
const VERSION = '1.0.0';
const manifest: VersionManifest = buildVersionManifest(VERSION);
const verifier = new DRMVerifier(manifest);
const challengeGenerator = new DRMChallengeGenerator(manifest);

// Active challenges
const activeChallenges: Map<string, DRMChallenge> = new Map();

// Gated resources - require DRM verification
const GATED_RESOURCES = new Set([
  'recipe:legendary_fertilizer',
  'recipe:power_food_supreme',
  'config:advanced_farming',
  'texture:rare_crops',
]);

export interface DRMMessage {
  type: 'drm_init' | 'drm_challenge' | 'drm_response' | 'drm_verify' | 'drm_resource_request';
  sessionId: string;
  payload: unknown;
}

/**
 * Handle DRM initialization - client announces version
 */
export function handleDRMInit(
  ws: WebSocket,
  sessionId: string,
  payload: { clientVersion: string }
): void {
  const session = getSession(sessionId);
  if (!session) {
    ws.send(JSON.stringify({
      type: 'drm_error',
      error: 'Session not found',
    }));
    return;
  }
  
  // Initialize or get client state
  let clientState = verifier.getClientState(sessionId);
  if (!clientState) {
    clientState = verifier.initializeClient(sessionId, payload.clientVersion);
  }
  
  // Check version compatibility
  const versionMatch = payload.clientVersion === VERSION;
  
  ws.send(JSON.stringify({
    type: 'drm_init_response',
    serverVersion: VERSION,
    versionMatch,
    clientState: {
      trustScore: clientState.trustScore,
      chainLength: clientState.chainLength,
      totalWorkCompleted: clientState.totalWorkCompleted,
    },
    // If version matches, issue initial challenge
    initialChallenge: versionMatch ? generateChallenge(sessionId, clientState) : null,
  }));
}

/**
 * Generate and send a DRM challenge
 */
function generateChallenge(sessionId: string, clientState: ClientState): DRMChallenge {
  const challenge = challengeGenerator.generateChallenge(clientState, 'shader_verify');
  
  // Convert Map to object for JSON serialization
  const serializedChallenge = {
    ...challenge,
    inputSeeds: Object.fromEntries(challenge.inputSeeds),
  };
  
  activeChallenges.set(challenge.challengeId, challenge);
  
  // Clean up expired challenges periodically
  setTimeout(() => {
    activeChallenges.delete(challenge.challengeId);
  }, 65000);
  
  return serializedChallenge as unknown as DRMChallenge;
}

/**
 * Handle DRM challenge request
 */
export function handleDRMChallengeRequest(
  ws: WebSocket,
  sessionId: string,
  payload: { workType?: 'shader_verify' | 'folding_chain' | 'entropy_chain' }
): void {
  const clientState = verifier.getClientState(sessionId);
  if (!clientState) {
    ws.send(JSON.stringify({
      type: 'drm_error',
      error: 'Client not initialized. Send drm_init first.',
    }));
    return;
  }
  
  const challenge = challengeGenerator.generateChallenge(
    clientState,
    payload.workType || 'shader_verify'
  );
  
  activeChallenges.set(challenge.challengeId, challenge);
  
  ws.send(JSON.stringify({
    type: 'drm_challenge',
    challenge: {
      ...challenge,
      inputSeeds: Object.fromEntries(challenge.inputSeeds),
    },
  }));
}

/**
 * Handle DRM response verification
 */
export function handleDRMResponse(
  ws: WebSocket,
  sessionId: string,
  payload: DRMResponse
): void {
  const challenge = activeChallenges.get(payload.challengeId);
  if (!challenge) {
    ws.send(JSON.stringify({
      type: 'drm_verify_result',
      valid: false,
      error: 'Challenge not found or expired',
    }));
    return;
  }
  
  // Verify the response
  const result = verifier.verify(challenge, payload);
  
  // Update client state
  if (result.valid) {
    verifier.updateClientState(sessionId, payload, result);
  }
  
  const clientState = verifier.getClientState(sessionId);
  
  ws.send(JSON.stringify({
    type: 'drm_verify_result',
    valid: result.valid,
    versionMatch: result.versionMatch,
    chainIntegrity: result.chainIntegrity,
    shaderOutputsMatch: result.shaderOutputsMatch,
    workValid: result.workValid,
    errors: result.errors,
    updatedState: clientState ? {
      trustScore: clientState.trustScore,
      chainLength: clientState.chainLength,
    } : null,
    // Grant access token if valid
    accessToken: result.valid ? generateAccessToken(sessionId, clientState!) : null,
  }));
  
  // Clean up challenge
  activeChallenges.delete(payload.challengeId);
}

/**
 * Handle gated resource request
 */
export function handleDRMResourceRequest(
  ws: WebSocket,
  sessionId: string,
  payload: { resourceId: string; accessToken: string; stateProof: unknown[] }
): void {
  const clientState = verifier.getClientState(sessionId);
  if (!clientState) {
    ws.send(JSON.stringify({
      type: 'drm_resource_response',
      granted: false,
      error: 'Client not verified',
    }));
    return;
  }
  
  // Check if resource is gated
  if (!GATED_RESOURCES.has(payload.resourceId)) {
    // Non-gated resource - grant immediately
    ws.send(JSON.stringify({
      type: 'drm_resource_response',
      granted: true,
      resourceId: payload.resourceId,
      data: getResource(payload.resourceId),
    }));
    return;
  }
  
  // Verify access token
  if (!verifyAccessToken(payload.accessToken, sessionId)) {
    ws.send(JSON.stringify({
      type: 'drm_resource_response',
      granted: false,
      error: 'Invalid access token',
    }));
    return;
  }
  
  // Check trust score threshold
  const requiredTrust = getResourceTrustThreshold(payload.resourceId);
  if (clientState.trustScore < requiredTrust) {
    ws.send(JSON.stringify({
      type: 'drm_resource_response',
      granted: false,
      error: `Insufficient trust score. Required: ${requiredTrust}, Current: ${clientState.trustScore}`,
      hint: 'Complete more shader verification challenges to increase trust.',
    }));
    return;
  }
  
  // Grant resource
  ws.send(JSON.stringify({
    type: 'drm_resource_response',
    granted: true,
    resourceId: payload.resourceId,
    data: getResource(payload.resourceId),
    validUntil: Date.now() + 3600000, // 1 hour
  }));
}

/**
 * Get DRM statistics
 */
export function getDRMStats(): {
  activeClients: number;
  totalVerifications: number;
  averageTrustScore: number;
  activeChallenges: number;
} {
  // This would aggregate stats from verifier
  return {
    activeClients: 0, // Would count from verifier
    totalVerifications: 0,
    averageTrustScore: 50,
    activeChallenges: activeChallenges.size,
  };
}

// Helper functions

function generateAccessToken(sessionId: string, clientState: ClientState): string {
  const tokenData = {
    sessionId,
    chainHash: clientState.lastChainHash,
    trustScore: clientState.trustScore,
    issuedAt: Date.now(),
    expiresAt: Date.now() + 300000, // 5 minutes
  };
  
  // In production, use proper JWT or similar
  const crypto = require('crypto');
  return crypto.createHash('sha256')
    .update(JSON.stringify(tokenData) + 'server_secret')
    .digest('hex');
}

function verifyAccessToken(token: string, sessionId: string): boolean {
  // Simplified verification
  return Boolean(token && token.length === 64);
}

function getResourceTrustThreshold(resourceId: string): number {
  if (resourceId.includes('legendary')) return 80;
  if (resourceId.includes('supreme')) return 70;
  if (resourceId.includes('advanced')) return 60;
  return 50;
}

function getResource(resourceId: string): unknown {
  // Mock resource data
  const resources: Record<string, unknown> = {
    'recipe:legendary_fertilizer': {
      id: 'legendary_fertilizer',
      inputs: [
        { item: 'farmcraft:superior_blend', count: 4 },
        { item: 'minecraft:nether_star', count: 1 },
      ],
      output: { item: 'farmcraft:legendary_fertilizer', count: 1 },
    },
    'recipe:power_food_supreme': {
      id: 'power_food_supreme',
      inputs: [
        { item: 'farmcraft:power_carrot', count: 1 },
        { item: 'farmcraft:power_potato', count: 1 },
        { item: 'farmcraft:power_beetroot', count: 1 },
        { item: 'minecraft:golden_apple', count: 1 },
      ],
      output: { item: 'farmcraft:supreme_meal', count: 1 },
    },
    'config:advanced_farming': {
      maxGrowthMultiplier: 5.0,
      enableAutoHarvest: true,
      enableCropMutation: true,
    },
    'texture:rare_crops': {
      format: 'png',
      size: '16x16',
      url: '/textures/rare_crops.png',
    },
  };
  
  return resources[resourceId] || null;
}
