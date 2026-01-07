/**
 * @farmcraft/peer-verify
 *
 * Peer-to-peer verification system where clients verify each other's
 * shader states to form a trustless network for resource sharing.
 */

import { createHash, randomBytes } from 'crypto';
import {
  StateChainLink,
  DRMChallenge,
  DRMResponse,
  executeShader,
  SHADER_REGISTRY,
} from '@farmcraft/drm-core';

// ============================================================================
// Types
// ============================================================================

export interface Peer {
  peerId: string;
  publicKey: string;
  version: string;
  lastSeen: number;
  reputation: number;
  chainHash: string;
  verifiedAt: number;
}

export interface PeerChallenge {
  challengerId: string;
  targetId: string;
  challengeId: string;
  shaderTests: ShaderTest[];
  timestamp: number;
  expiresAt: number;
}

export interface ShaderTest {
  shaderId: string;
  inputSeed: string;
  expectedOutput: string; // Challenger knows this
}

export interface PeerResponse {
  challengeId: string;
  responderId: string;
  outputs: Map<string, string>;
  stateProof: StateChainLink;
  signature: string;
}

export interface ResourceRequest {
  requesterId: string;
  resourceType: 'recipe' | 'texture' | 'config' | 'world_data';
  resourceId: string;
  stateProof: StateChainLink[];
  workToken: string;
}

export interface ResourceGrant {
  resourceId: string;
  data: unknown;
  granterId: string;
  validUntil: number;
  signature: string;
}

export interface PeerNetwork {
  peers: Map<string, Peer>;
  pendingChallenges: Map<string, PeerChallenge>;
  resourceHolders: Map<string, string[]>; // resourceId -> peerIds
}

// ============================================================================
// Peer Verification
// ============================================================================

export class PeerVerifier {
  private localPeerId: string;
  private localVersion: string;
  private peers: Map<string, Peer> = new Map();
  private pendingChallenges: Map<string, PeerChallenge> = new Map();

  constructor(peerId: string, version: string) {
    this.localPeerId = peerId;
    this.localVersion = version;
  }

  /**
   * Create a challenge to verify another peer
   */
  createChallenge(targetPeerId: string): PeerChallenge {
    const challengeId = randomBytes(16).toString('hex');
    const shaderTests: ShaderTest[] = [];

    // Select random shaders to test
    const shaderIds = Object.keys(SHADER_REGISTRY);
    const selectedShaders = this.selectRandomShaders(shaderIds, 2);

    for (const shaderId of selectedShaders) {
      // Generate random input
      const inputSeed = randomBytes(32).toString('hex');

      // Compute expected output (challenger runs locally)
      const expectedOutput = executeShader(shaderId, inputSeed);

      shaderTests.push({
        shaderId,
        inputSeed,
        expectedOutput,
      });
    }

    const challenge: PeerChallenge = {
      challengerId: this.localPeerId,
      targetId: targetPeerId,
      challengeId,
      shaderTests,
      timestamp: Date.now(),
      expiresAt: Date.now() + 30000, // 30 seconds
    };

    this.pendingChallenges.set(challengeId, challenge);
    return challenge;
  }

  /**
   * Respond to a peer challenge
   */
  respondToChallenge(challenge: PeerChallenge, stateChain: StateChainLink[]): PeerResponse {
    const outputs = new Map<string, string>();

    // Execute each shader test
    for (const test of challenge.shaderTests) {
      const output = executeShader(test.shaderId, test.inputSeed);
      outputs.set(test.shaderId, output);
    }

    // Include latest state proof
    const latestLink = stateChain[stateChain.length - 1] || this.createGenesisLink();

    const responseData = JSON.stringify({
      challengeId: challenge.challengeId,
      outputs: Object.fromEntries(outputs),
      linkHash: latestLink.linkHash,
    });

    const signature = createHash('sha256')
      .update(responseData + this.localPeerId)
      .digest('hex');

    return {
      challengeId: challenge.challengeId,
      responderId: this.localPeerId,
      outputs,
      stateProof: latestLink,
      signature,
    };
  }

  /**
   * Verify a peer's response
   */
  verifyResponse(response: PeerResponse): { valid: boolean; errors: string[] } {
    const errors: string[] = [];

    const challenge = this.pendingChallenges.get(response.challengeId);
    if (!challenge) {
      errors.push('Unknown challenge');
      return { valid: false, errors };
    }

    if (Date.now() > challenge.expiresAt) {
      errors.push('Challenge expired');
      return { valid: false, errors };
    }

    // Verify each shader output matches expected
    for (const test of challenge.shaderTests) {
      const actualOutput = response.outputs.get(test.shaderId);
      if (actualOutput !== test.expectedOutput) {
        errors.push(`Shader ${test.shaderId} output mismatch`);
      }
    }

    // Clean up challenge
    this.pendingChallenges.delete(response.challengeId);

    const valid = errors.length === 0;

    // Update peer reputation
    if (valid) {
      this.updatePeerReputation(response.responderId, 10);
    } else {
      this.updatePeerReputation(response.responderId, -30);
    }

    return { valid, errors };
  }

  /**
   * Add or update a peer
   */
  addPeer(peer: Peer): void {
    this.peers.set(peer.peerId, peer);
  }

  /**
   * Get verified peers with minimum reputation
   */
  getVerifiedPeers(minReputation: number = 50): Peer[] {
    return Array.from(this.peers.values()).filter((p) => p.reputation >= minReputation);
  }

  private selectRandomShaders(shaderIds: string[], count: number): string[] {
    const shuffled = [...shaderIds].sort(() => Math.random() - 0.5);
    return shuffled.slice(0, count);
  }

  private updatePeerReputation(peerId: string, delta: number): void {
    const peer = this.peers.get(peerId);
    if (peer) {
      peer.reputation = Math.max(0, Math.min(100, peer.reputation + delta));
      peer.verifiedAt = Date.now();
    }
  }

  private createGenesisLink(): StateChainLink {
    return {
      index: 0,
      previousHash: '0'.repeat(64),
      shaderFingerprint: {
        shaderId: 'genesis',
        version: this.localVersion,
        inputSeed: 'genesis',
        outputHash: '0'.repeat(64),
        timestamp: Date.now(),
      },
      workProof: '0'.repeat(64),
      linkHash: '0'.repeat(64),
    };
  }
}

// ============================================================================
// Resource Sharing with DRM
// ============================================================================

export class ResourceSharing {
  private verifier: PeerVerifier;
  private localResources: Map<string, unknown> = new Map();
  private resourceAccess: Map<string, Set<string>> = new Map(); // resourceId -> allowed peerIds

  constructor(verifier: PeerVerifier) {
    this.verifier = verifier;
  }

  /**
   * Register a local resource for sharing
   */
  registerResource(resourceId: string, data: unknown): void {
    this.localResources.set(resourceId, data);
  }

  /**
   * Request a resource from the network
   */
  createResourceRequest(
    resourceId: string,
    stateProof: StateChainLink[],
    workToken: string,
    requesterId: string
  ): ResourceRequest {
    return {
      requesterId,
      resourceType: this.inferResourceType(resourceId),
      resourceId,
      stateProof,
      workToken,
    };
  }

  /**
   * Handle incoming resource request
   */
  handleResourceRequest(
    request: ResourceRequest,
    localPeerId: string
  ): ResourceGrant | { error: string } {
    // Verify requester has valid state proof
    if (request.stateProof.length === 0) {
      return { error: 'No state proof provided' };
    }

    // Verify work token
    if (!this.verifyWorkToken(request.workToken)) {
      return { error: 'Invalid work token' };
    }

    // Check if we have the resource
    const resource = this.localResources.get(request.resourceId);
    if (!resource) {
      return { error: 'Resource not found' };
    }

    // Verify state chain is valid (simplified check)
    const lastLink = request.stateProof[request.stateProof.length - 1];
    if (!this.verifyStateLink(lastLink)) {
      return { error: 'Invalid state proof' };
    }

    // Grant access
    const grant: ResourceGrant = {
      resourceId: request.resourceId,
      data: resource,
      granterId: localPeerId,
      validUntil: Date.now() + 3600000, // 1 hour
      signature: this.signGrant(request.resourceId, localPeerId),
    };

    // Track access
    const accessSet = this.resourceAccess.get(request.resourceId) || new Set();
    accessSet.add(request.requesterId);
    this.resourceAccess.set(request.resourceId, accessSet);

    return grant;
  }

  private inferResourceType(resourceId: string): ResourceRequest['resourceType'] {
    if (resourceId.startsWith('recipe:')) return 'recipe';
    if (resourceId.startsWith('texture:')) return 'texture';
    if (resourceId.startsWith('config:')) return 'config';
    return 'world_data';
  }

  private verifyWorkToken(token: string): boolean {
    // Simplified verification - check token format
    return token.length === 64 && /^[a-f0-9]+$/.test(token);
  }

  private verifyStateLink(link: StateChainLink): boolean {
    // Verify link hash matches computed hash
    const linkData = JSON.stringify({
      index: link.index,
      previousHash: link.previousHash,
      fingerprint: link.shaderFingerprint,
      workProof: link.workProof,
    });
    const expectedHash = createHash('sha256').update(linkData).digest('hex');
    return link.linkHash === expectedHash;
  }

  private signGrant(resourceId: string, granterId: string): string {
    return createHash('sha256')
      .update(resourceId + granterId + Date.now())
      .digest('hex');
  }
}

// ============================================================================
// Gossip Protocol for Peer Discovery
// ============================================================================

export interface GossipMessage {
  type: 'announce' | 'query' | 'response' | 'challenge' | 'verify';
  senderId: string;
  payload: unknown;
  ttl: number;
  signature: string;
}

export class GossipNetwork {
  private peerId: string;
  private version: string;
  private verifier: PeerVerifier;
  private messageHandlers: Map<string, (msg: GossipMessage) => void> = new Map();
  private seenMessages: Set<string> = new Set();

  constructor(peerId: string, version: string) {
    this.peerId = peerId;
    this.version = version;
    this.verifier = new PeerVerifier(peerId, version);
  }

  /**
   * Create an announcement message
   */
  createAnnouncement(chainHash: string): GossipMessage {
    const payload = {
      peerId: this.peerId,
      version: this.version,
      chainHash,
      capabilities: ['recipes', 'textures', 'configs'],
      timestamp: Date.now(),
    };

    return {
      type: 'announce',
      senderId: this.peerId,
      payload,
      ttl: 5,
      signature: this.signMessage(payload),
    };
  }

  /**
   * Process incoming gossip message
   */
  processMessage(message: GossipMessage): GossipMessage | null {
    // Check if we've seen this message
    const messageId = this.computeMessageId(message);
    if (this.seenMessages.has(messageId)) {
      return null;
    }
    this.seenMessages.add(messageId);

    // Verify signature
    if (!this.verifySignature(message)) {
      console.log(`Invalid signature from ${message.senderId}`);
      return null;
    }

    switch (message.type) {
      case 'announce':
        return this.handleAnnouncement(message);
      case 'query':
        return this.handleQuery(message);
      case 'challenge':
        return this.handleChallenge(message);
      default:
        return null;
    }
  }

  private handleAnnouncement(message: GossipMessage): GossipMessage | null {
    const payload = message.payload as {
      peerId: string;
      version: string;
      chainHash: string;
      timestamp: number;
    };

    // Add/update peer
    this.verifier.addPeer({
      peerId: payload.peerId,
      publicKey: '', // Would be included in real implementation
      version: payload.version,
      lastSeen: Date.now(),
      reputation: 50, // Start neutral
      chainHash: payload.chainHash,
      verifiedAt: 0,
    });

    // Maybe challenge new peer
    if (payload.version !== this.version) {
      // Version mismatch - issue challenge
      const challenge = this.verifier.createChallenge(payload.peerId);
      return {
        type: 'challenge',
        senderId: this.peerId,
        payload: challenge,
        ttl: 1, // Direct message
        signature: this.signMessage(challenge),
      };
    }

    // Decrement TTL and forward
    if (message.ttl > 1) {
      return { ...message, ttl: message.ttl - 1 };
    }

    return null;
  }

  private handleQuery(message: GossipMessage): GossipMessage | null {
    const payload = message.payload as { resourceId: string };

    // Check if we have the resource
    // Return response if we do
    return {
      type: 'response',
      senderId: this.peerId,
      payload: {
        resourceId: payload.resourceId,
        available: true,
        chainHash: this.version,
      },
      ttl: 1,
      signature: this.signMessage({ resourceId: payload.resourceId }),
    };
  }

  private handleChallenge(message: GossipMessage): GossipMessage | null {
    const challenge = message.payload as PeerChallenge;

    // Respond to challenge
    const response = this.verifier.respondToChallenge(challenge, []);

    return {
      type: 'verify',
      senderId: this.peerId,
      payload: response,
      ttl: 1,
      signature: this.signMessage(response),
    };
  }

  private signMessage(payload: unknown): string {
    return createHash('sha256')
      .update(JSON.stringify(payload) + this.peerId)
      .digest('hex');
  }

  private verifySignature(message: GossipMessage): boolean {
    const expected = createHash('sha256')
      .update(JSON.stringify(message.payload) + message.senderId)
      .digest('hex');
    return message.signature === expected;
  }

  private computeMessageId(message: GossipMessage): string {
    return createHash('sha256').update(JSON.stringify(message)).digest('hex');
  }

  /**
   * Get the peer verifier
   */
  getVerifier(): PeerVerifier {
    return this.verifier;
  }
}

// ============================================================================
// Demo
// ============================================================================

export function runPeerDemo(): void {
  console.log('=== Peer Verification Demo ===\n');

  // Create two peers
  const peer1 = new GossipNetwork('peer_1', '1.0.0');
  const peer2 = new GossipNetwork('peer_2', '1.0.0');
  const maliciousPeer = new GossipNetwork('peer_evil', '1.0.1'); // Different version

  // Peer 1 announces
  const announcement1 = peer1.createAnnouncement('abc123');
  console.log('Peer 1 announces presence');

  // Peer 2 receives and processes
  const response = peer2.processMessage(announcement1);
  console.log('Peer 2 processes announcement');
  console.log(`Response: ${response?.type || 'none (same version)'}`);

  // Malicious peer announces
  console.log('\nMalicious peer announces with wrong version...');
  const maliciousAnn = maliciousPeer.createAnnouncement('xyz789');
  const challengeMsg = peer1.processMessage(maliciousAnn);

  if (challengeMsg?.type === 'challenge') {
    console.log('Peer 1 challenges malicious peer!');

    // Malicious peer responds
    const verifyMsg = maliciousPeer.processMessage(challengeMsg);
    console.log('Malicious peer responds to challenge');

    // This would fail verification due to different shader outputs
    // (In demo, outputs match since we use same code, but version differs)
  }

  // Resource sharing demo
  console.log('\n--- Resource Sharing Demo ---');
  const sharing = new ResourceSharing(peer1.getVerifier());

  // Register a resource
  sharing.registerResource('recipe:power_carrot', {
    inputs: [{ item: 'minecraft:carrot', count: 1 }],
    output: { item: 'farmcraft:power_carrot', count: 1 },
  });
  console.log('Registered recipe resource');

  // Create mock state proof
  const mockStateProof: StateChainLink[] = [
    {
      index: 0,
      previousHash: '0'.repeat(64),
      shaderFingerprint: {
        shaderId: 'version_proof_v1',
        version: '1.0.0',
        inputSeed: 'test',
        outputHash: executeShader('version_proof_v1', 'test'),
        timestamp: Date.now(),
      },
      workProof: '0'.repeat(64),
      linkHash: '', // Will be computed
    },
  ];

  // Compute link hash
  const linkData = JSON.stringify({
    index: mockStateProof[0].index,
    previousHash: mockStateProof[0].previousHash,
    fingerprint: mockStateProof[0].shaderFingerprint,
    workProof: mockStateProof[0].workProof,
  });
  mockStateProof[0].linkHash = createHash('sha256').update(linkData).digest('hex');

  // Request resource
  const request = sharing.createResourceRequest(
    'recipe:power_carrot',
    mockStateProof,
    'a'.repeat(64), // Mock work token
    'peer_2'
  );
  console.log('Peer 2 requests resource with state proof');

  const grant = sharing.handleResourceRequest(request, 'peer_1');
  if ('error' in grant) {
    console.log(`Request denied: ${grant.error}`);
  } else {
    console.log(`Resource granted! Valid until: ${new Date(grant.validUntil).toISOString()}`);
  }

  console.log('\n=== Demo Complete ===');
}

export { runPeerDemo as demo };
