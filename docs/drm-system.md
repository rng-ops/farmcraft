# DRM & Version Verification System

FarmCraft uses a novel DRM approach where clients prove they're running unmodified
code by executing shader computations and building verifiable state chains.

## Overview

Traditional DRM relies on code signing and obfuscation. Our approach:

1. **Shader Fingerprinting**: Shaders produce deterministic outputs for given inputs
2. **State Chains**: Each verification builds on previous ones, creating tamper-evident history
3. **Useful Work**: Verification computation contributes to protein folding research
4. **Progressive Trust**: Verified clients earn trust scores unlocking premium content

```
┌─────────────────────────────────────────────────────────────────────┐
│                        DRM Flow                                     │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  Client                           Server                            │
│    │                                │                               │
│    │──── drm_init (version) ───────>│                               │
│    │                                │ Check version                 │
│    │<─── challenge (shaders, seeds)─│                               │
│    │                                │                               │
│    │  Execute shaders               │                               │
│    │  Build state chain             │                               │
│    │  Compute work proof            │                               │
│    │                                │                               │
│    │──── response (chain, proof) ──>│                               │
│    │                                │ Verify outputs                │
│    │                                │ Check chain integrity         │
│    │<─── verify result + token ─────│                               │
│    │                                │                               │
│    │──── resource request ─────────>│                               │
│    │                                │ Check token & trust           │
│    │<─── resource data ─────────────│                               │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

## Components

### 1. Shader Registry

Each shader is registered with:
- **ID**: Unique identifier (e.g., `hash_compute_v1`)
- **Version**: Shader version for compatibility
- **Test Seed**: Known input for verification
- **Expected Output**: Server knows what correct shaders produce

```typescript
const SHADER_REGISTRY = {
  'hash_compute_v1': {
    version: '1.0.0',
    testSeed: '000000...',
    expectedTestOutput: 'e3b0c4...',
  },
  'folding_energy_v1': { ... },
  'entropy_v1': { ... },
  'version_proof_v1': { ... },
};
```

### 2. Shader Execution

Shaders perform deterministic computations:

| Shader | Purpose | Computation |
|--------|---------|-------------|
| `hash_compute_v1` | General hashing | 1000 iterations of SHA-256 |
| `folding_energy_v1` | Protein folding | Lennard-Jones energy calculation |
| `entropy_v1` | Entropy generation | Chaotic mixing function |
| `version_proof_v1` | Version proof | Hash with build-specific salt |

The `version_proof_v1` shader includes a salt that changes with each build,
so modified clients produce different outputs.

### 3. State Chain

Each verification creates a link containing:

```typescript
interface StateChainLink {
  index: number;           // Position in chain
  previousHash: string;    // Hash of previous link
  shaderFingerprint: {
    shaderId: string;      // Which shader was run
    version: string;       // Client version
    inputSeed: string;     // Input from challenge
    outputHash: string;    // Result of shader execution
    timestamp: number;     // When executed
  };
  workProof: string;       // PoW for this link
  linkHash: string;        // SHA-256 of this link's data
}
```

Properties:
- **Immutable**: Any modification breaks subsequent hashes
- **Ordered**: Links reference previous link hash
- **Verifiable**: Server can recompute and compare

### 4. DRM Challenge

Server generates challenges containing:

```typescript
interface DRMChallenge {
  challengeId: string;          // Unique ID
  requiredShaders: string[];    // Which shaders to execute
  inputSeeds: Map<string, string>; // Seed per shader
  previousChainHash: string;    // Must match client's chain
  difficulty: number;           // Work proof difficulty
  expiresAt: number;           // Challenge timeout
}
```

Challenges:
- Reference previous chain state (prevents replays)
- Include random seeds (different every time)
- Have difficulty based on trust level

### 5. Trust Score

Trust increases with successful verifications:

| Trust Level | Unlocks |
|-------------|---------|
| 0-49 | Basic recipes only |
| 50-59 | Standard recipes |
| 60-69 | Advanced recipes |
| 70-79 | Premium recipes |
| 80-100 | Legendary content |

Trust decreases on:
- Version mismatch (-30)
- Invalid shader outputs (-50)
- Chain integrity failure (-20)

## Verification Process

### Client Side (Java)

```java
// 1. Initialize DRM client
DRMManager drm = DRMManager.getInstance();
drm.connect().thenAccept(connected -> {
    if (connected) {
        // 2. Await challenge from server
    }
});

// 3. When challenge arrives, solve it
DRMChallenge challenge = drm.getPendingChallenge();
DRMResponse response = drmClient.solveChallenge(challenge);

// 4. Response automatically sent to server
// 5. If valid, access token granted
if (drm.isVerified()) {
    drm.requestResource("recipe:legendary_fertilizer");
}
```

### Server Side (TypeScript)

```typescript
// 1. On client init, create challenge
const clientState = verifier.initializeClient(sessionId, clientVersion);
const challenge = challengeGenerator.generateChallenge(clientState);

// 2. On response, verify
const result = verifier.verify(challenge, response);

if (result.valid) {
  // 3. Update trust and grant token
  verifier.updateClientState(sessionId, response, result);
  const token = generateAccessToken(sessionId);
}

// 4. On resource request, check trust
if (clientState.trustScore >= requiredTrust) {
  grantResource(resourceId);
}
```

## Peer-to-Peer Verification

Clients can verify each other without a central server:

```typescript
// Peer 1 challenges Peer 2
const challenge = peer1.createChallenge('peer2_id');

// Peer 2 responds with shader outputs
const response = peer2.respondToChallenge(challenge);

// Peer 1 verifies (has expected outputs since same version)
const result = peer1.verifyResponse(response);

if (result.valid) {
  // Same version confirmed, can share resources
  shareResource('recipe:power_carrot', 'peer2_id');
}
```

## Security Properties

### Tamper Detection

Modified clients produce different shader outputs because:
1. `version_proof_v1` includes build-specific salt
2. Any code change affects computation
3. Server computes expected outputs independently

### Replay Prevention

Challenges can't be replayed because:
1. Each challenge includes previous chain hash
2. Seeds are randomly generated
3. Challenges expire after 60 seconds

### Chain Integrity

Chain tampering is detected because:
1. Each link hashes the previous link
2. Fingerprints are hashed into links
3. Server verifies entire chain

## Running the Demo

```bash
# Full interactive demo
pnpm demo:drm

# Core DRM demo
pnpm demo:core

# Peer verification demo  
pnpm demo:peer
```

## Integration with Minecraft

The DRM system integrates with the mod through:

1. **DRMManager**: Singleton managing WebSocket connection
2. **ShaderExecutor**: Runs shader computations
3. **StateChain**: Maintains verification history

When the player loads a world:
1. DRMManager connects to server
2. Initial challenge is solved
3. Recipes become available based on trust

Background challenges maintain trust over time.

## Configuration

In `farmcraft-common.toml`:

```toml
[drm]
# Enable DRM verification
enable_drm = true

# Minimum trust for gated recipes
min_trust_for_advanced = 60
min_trust_for_legendary = 80

# Challenge frequency (minutes)
challenge_interval = 5
```
