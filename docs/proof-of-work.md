# Proof-of-Work System

This document describes the proof-of-work (PoW) system used in FarmCraft for recipe discovery and distributed computation.

## Overview

The PoW system serves multiple purposes:

1. **Rate Limiting**: Prevents abuse of the recipe distribution system
2. **Scientific Contribution**: Contributes to protein folding research
3. **Entropy Generation**: Creates high-quality random numbers for game mechanics
4. **Token Economy**: Creates a merit-based access system

## Work Types

### Hash Challenge

Simple SHA-256 hash-based proof-of-work.

```
Challenge: Find nonce N such that SHA256(prefix + N) starts with D zeros
Difficulty: D = 1-8 (number of required leading zeros)
Reward: 10 * 1.5^(D-1) tokens
```

**Use Case**: Quick challenges, fallback when more complex work isn't needed.

### Protein Folding

Simplified molecular dynamics simulation.

```
Challenge: Minimize energy of a protein configuration
Input: Amino acid sequence (20-100 residues)
Output: 3D coordinates with minimized energy
Difficulty: Affects sequence length and required energy threshold
```

**Scientific Value**: Results contribute to understanding protein structure.

### Entropy Generation

Cryptographic entropy through iterated hashing.

```
Challenge: Generate entropy by iterating hash function
Input: Seed value
Output: High-quality random bytes
Difficulty: Number of iterations (1000 * difficulty)
```

**Use Case**: Generating randomness for game mechanics, loot tables, etc.

### Shader Compute

GPU-accelerated computation using WGSL shaders.

```
Challenge: Execute compute shader on provided data
Input: Shader program + input data
Output: Computation result
Difficulty: Complexity of computation
```

**Benefit**: Utilizes idle GPU cycles while playing.

## Token Economy

### Earning Tokens

| Action | Tokens Earned |
|--------|---------------|
| Hash challenge (D=1) | 10 |
| Hash challenge (D=3) | 23 |
| Hash challenge (D=5) | 51 |
| Protein folding (easy) | 50 |
| Protein folding (medium) | 150 |
| Protein folding (hard) | 500 |
| Shader compute | Varies |

### Spending Tokens

| Action | Tokens Required |
|--------|-----------------|
| Request basic recipes | 1 |
| Request enhanced recipes | 5 |
| Request superior recipes | 25 |
| Request legendary recipes | 100 |
| Unlock new ingredient | 50 |

## Implementation

### Client-Side

```java
// Request a challenge
ChallengeRequestPacket request = new ChallengeRequestPacket("protein_folding", 5);
ModNetworking.sendToServer(request);

// Solve challenge in background
ProofOfWorkClient.getInstance().startChallenge(
    challengeId,
    type,
    difficulty,
    payload,
    expiresAt,
    rewardTokens
);

// Solution is automatically submitted when complete
```

### Server-Side

```typescript
// Generate challenge
const challenge = generateChallenge({
  type: 'protein_folding',
  difficulty: 5,
});

// Verify solution
const result = verifySolution(challenge, solution);

if (result.valid) {
  const token = generateToken(playerId, challenge.rewardTokens, 24);
  // Send token to client
}
```

## Shader Integration

The mod can utilize GPU shaders for computation:

1. **Rendering Shaders**: Normal game rendering continues
2. **Compute Shaders**: Background computation during idle GPU time
3. **Result Extraction**: Entropy/results are read back asynchronously

```wgsl
@compute @workgroup_size(256)
fn main(@builtin(global_invocation_id) global_id: vec3<u32>) {
    // Computation happens here
    // Results written to output buffer
}
```

## Security Considerations

1. **Challenge Expiration**: Challenges expire after 30 minutes
2. **Solution Verification**: Server validates all solutions
3. **Token Signatures**: Tokens are cryptographically signed
4. **Rate Limiting**: Challenges are rate-limited per player
5. **Difficulty Scaling**: Automatic difficulty adjustment

## Future Improvements

- Integration with Folding@home
- Multi-GPU support
- Persistent work units across sessions
- Leaderboards and achievements
- Cross-server token exchange
