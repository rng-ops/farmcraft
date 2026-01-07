"use strict";
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
Object.defineProperty(exports, "__esModule", { value: true });
exports.DRMClient = exports.DRMVerifier = exports.DRMChallengeGenerator = exports.StateChain = exports.SHADER_REGISTRY = void 0;
exports.executeShader = executeShader;
exports.buildVersionManifest = buildVersionManifest;
exports.runDRMDemo = runDRMDemo;
exports.demo = runDRMDemo;
const crypto_1 = require("crypto");
// ============================================================================
// Shader State Registry
// ============================================================================
/**
 * Known shader programs and their expected behaviors
 */
exports.SHADER_REGISTRY = {
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
function executeShader(shaderId, inputSeed) {
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
function computeHashShader(input) {
    // Simulates iterative SHA-256 like a GPU would compute
    let state = input;
    for (let i = 0; i < 1000; i++) {
        state = (0, crypto_1.createHash)('sha256').update(state + i.toString()).digest('hex');
    }
    return state;
}
function computeFoldingShader(aminoSequence) {
    // Simulates energy calculation for protein folding
    // Real implementation would use Lennard-Jones potential
    let energy = 0;
    const positions = [];
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
    return (0, crypto_1.createHash)('sha256')
        .update(energy.toFixed(10) + aminoSequence)
        .digest('hex');
}
function computeEntropyShader(input) {
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
    return (0, crypto_1.createHash)('sha256')
        .update(Buffer.from(state.buffer))
        .digest('hex');
}
function computeVersionProofShader(input) {
    // Version-specific computation that differs per build
    // This would be compiled differently in each version
    const versionSalt = 'farmcraft_v1.0.0_build_2026';
    return (0, crypto_1.createHash)('sha256')
        .update(input + versionSalt)
        .digest('hex');
}
// ============================================================================
// State Chain Management
// ============================================================================
class StateChain {
    clientVersion;
    links = [];
    currentHash = '0'.repeat(64); // Genesis hash
    constructor(clientVersion) {
        this.clientVersion = clientVersion;
    }
    /**
     * Add a new link to the chain by executing a shader
     */
    addLink(shaderId, inputSeed, workProof) {
        const shaderOutput = executeShader(shaderId, inputSeed);
        const fingerprint = {
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
        const linkHash = (0, crypto_1.createHash)('sha256').update(linkData).digest('hex');
        const link = {
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
    getChainHash() {
        return this.currentHash;
    }
    /**
     * Get all links for verification
     */
    getLinks() {
        return [...this.links];
    }
    /**
     * Export chain for transmission
     */
    export() {
        return {
            links: this.links,
            chainHash: this.currentHash,
            version: this.clientVersion,
        };
    }
    /**
     * Import existing chain
     */
    static import(data) {
        const chain = new StateChain(data.version);
        chain.links = data.links;
        chain.currentHash = data.chainHash;
        return chain;
    }
}
exports.StateChain = StateChain;
// ============================================================================
// DRM Challenge Generator
// ============================================================================
class DRMChallengeGenerator {
    activeManifest;
    constructor(manifest) {
        this.activeManifest = manifest;
    }
    /**
     * Generate a challenge that requires specific shader execution
     */
    generateChallenge(clientState, workType = 'shader_verify') {
        const challengeId = (0, crypto_1.randomBytes)(16).toString('hex');
        const requiredShaders = this.selectRequiredShaders(workType);
        const inputSeeds = new Map();
        // Generate seeds that chain from previous state
        for (const shaderId of requiredShaders) {
            // Seed includes previous chain hash to create dependency
            const seed = (0, crypto_1.createHash)('sha256')
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
    selectRequiredShaders(workType) {
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
    getExpectedOutputs(challenge) {
        const expected = new Map();
        for (const [shaderId, seed] of challenge.inputSeeds) {
            // Server computes expected output using canonical shader
            const output = executeShader(shaderId, seed);
            expected.set(shaderId, output);
        }
        return expected;
    }
}
exports.DRMChallengeGenerator = DRMChallengeGenerator;
// ============================================================================
// DRM Verifier
// ============================================================================
class DRMVerifier {
    manifest;
    clientStates = new Map();
    constructor(manifest) {
        this.manifest = manifest;
    }
    /**
     * Verify a DRM response from a client
     */
    verify(challenge, response) {
        const errors = [];
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
        const expectedOutputs = new Map();
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
    verifyChainIntegrity(chain, expectedPreviousHash) {
        if (chain.length === 0)
            return false;
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
            const expectedHash = (0, crypto_1.createHash)('sha256').update(linkData).digest('hex');
            if (link.linkHash !== expectedHash)
                return false;
            // Verify chain continuity within this response
            if (i > 0 && link.previousHash !== chain[i - 1].linkHash) {
                return false;
            }
        }
        return true;
    }
    verifyWorkProof(result, nonce, difficulty) {
        // The result already contains the hash that was found with the nonce
        // Verify it has the required leading zeros
        for (let i = 0; i < difficulty; i++) {
            if (result[i] !== '0')
                return false;
        }
        return true;
    }
    /**
     * Update client state after successful verification
     */
    updateClientState(clientId, response, result) {
        const existing = this.clientStates.get(clientId);
        const lastLink = response.stateChain[response.stateChain.length - 1];
        const newState = {
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
    calculateTrustScore(existing, result) {
        let score = existing?.trustScore ?? 50;
        if (result.valid) {
            score = Math.min(100, score + 5);
        }
        else {
            score = Math.max(0, score - 20);
        }
        if (!result.versionMatch)
            score = Math.max(0, score - 30);
        if (!result.shaderOutputsMatch)
            score = Math.max(0, score - 50);
        return score;
    }
    /**
     * Get client state
     */
    getClientState(clientId) {
        return this.clientStates.get(clientId);
    }
    /**
     * Initialize new client
     */
    initializeClient(clientId, version) {
        const state = {
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
exports.DRMVerifier = DRMVerifier;
// ============================================================================
// Client-side DRM Handler
// ============================================================================
class DRMClient {
    stateChain;
    clientId;
    constructor(clientId, version) {
        this.clientId = clientId;
        this.stateChain = new StateChain(version);
    }
    /**
     * Solve a DRM challenge
     */
    solveChallenge(challenge) {
        const newLinks = [];
        // Execute each required shader and add to chain
        for (const shaderId of challenge.requiredShaders) {
            const seed = challenge.inputSeeds.get(shaderId);
            if (!seed)
                throw new Error(`Missing seed for shader ${shaderId}`);
            // Do work proof for each link
            const workProof = this.computeWorkProof(shaderId, seed, challenge.difficulty);
            const link = this.stateChain.addLink(shaderId, seed, workProof);
            newLinks.push(link);
        }
        // Compute final work result with nonce
        const { result, nonce } = this.findValidNonce(this.stateChain.getChainHash(), challenge.difficulty);
        return {
            challengeId: challenge.challengeId,
            clientVersion: this.stateChain.export().version,
            stateChain: newLinks,
            workResult: result,
            nonce,
            clientSignature: this.signResponse(result),
        };
    }
    computeWorkProof(shaderId, seed, difficulty) {
        // Simple work proof - hash iterations
        let proof = seed;
        const iterations = Math.pow(10, difficulty);
        for (let i = 0; i < iterations; i++) {
            proof = (0, crypto_1.createHash)('sha256').update(proof + shaderId).digest('hex');
        }
        return proof;
    }
    findValidNonce(data, difficulty) {
        let nonce = 0;
        while (true) {
            const result = (0, crypto_1.createHash)('sha256')
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
    signResponse(data) {
        // In real implementation, use proper cryptographic signing
        return (0, crypto_1.createHash)('sha256')
            .update(this.clientId + data + Date.now())
            .digest('hex');
    }
    /**
     * Get current chain state
     */
    getChainState() {
        return {
            chainHash: this.stateChain.getChainHash(),
            chainLength: this.stateChain.getLinks().length,
        };
    }
}
exports.DRMClient = DRMClient;
// ============================================================================
// Version Manifest Builder
// ============================================================================
function buildVersionManifest(version) {
    const shaderHashes = new Map();
    const expectedOutputs = new Map();
    for (const [shaderId, config] of Object.entries(exports.SHADER_REGISTRY)) {
        // Hash the shader "code" (simulated as config)
        const shaderHash = (0, crypto_1.createHash)('sha256')
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
    const signature = (0, crypto_1.createHash)('sha256')
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
function runDRMDemo() {
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
        clientState = verifier.getClientState(clientId);
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
//# sourceMappingURL=data:application/json;base64,eyJ2ZXJzaW9uIjozLCJmaWxlIjoiaW5kZXguanMiLCJzb3VyY2VSb290IjoiIiwic291cmNlcyI6WyJpbmRleC50cyJdLCJuYW1lcyI6W10sIm1hcHBpbmdzIjoiO0FBQUE7Ozs7Ozs7Ozs7R0FVRzs7O0FBNkdILHNDQWNDO0FBK2ZELG9EQWtDQztBQU1ELGdDQXdFQztBQUdzQiwwQkFBSTtBQTN1QjNCLG1DQUFpRDtBQW9FakQsK0VBQStFO0FBQy9FLHdCQUF3QjtBQUN4QiwrRUFBK0U7QUFFL0U7O0dBRUc7QUFDVSxRQUFBLGVBQWUsR0FBRztJQUM3QixpQkFBaUIsRUFBRTtRQUNqQixPQUFPLEVBQUUsT0FBTztRQUNoQixvRUFBb0U7UUFDcEUsUUFBUSxFQUFFLGtFQUFrRTtRQUM1RSxrQkFBa0IsRUFBRSxrRUFBa0U7S0FDdkY7SUFDRCxtQkFBbUIsRUFBRTtRQUNuQixPQUFPLEVBQUUsT0FBTztRQUNoQixRQUFRLEVBQUUsc0JBQXNCLEVBQUUsc0JBQXNCO1FBQ3hELGtCQUFrQixFQUFFLGtFQUFrRTtLQUN2RjtJQUNELFlBQVksRUFBRTtRQUNaLE9BQU8sRUFBRSxPQUFPO1FBQ2hCLFFBQVEsRUFBRSxrQkFBa0I7UUFDNUIsa0JBQWtCLEVBQUUsa0VBQWtFO0tBQ3ZGO0lBQ0Qsa0JBQWtCLEVBQUU7UUFDbEIsT0FBTyxFQUFFLE9BQU87UUFDaEIsUUFBUSxFQUFFLGVBQWU7UUFDekIsa0JBQWtCLEVBQUUsa0VBQWtFO0tBQ3ZGO0NBQ0YsQ0FBQztBQUVGLCtFQUErRTtBQUMvRSw2QkFBNkI7QUFDN0IsK0VBQStFO0FBRS9FOzs7R0FHRztBQUNILFNBQWdCLGFBQWEsQ0FBQyxRQUFnQixFQUFFLFNBQWlCO0lBQy9ELDZEQUE2RDtJQUM3RCxRQUFRLFFBQVEsRUFBRSxDQUFDO1FBQ2pCLEtBQUssaUJBQWlCO1lBQ3BCLE9BQU8saUJBQWlCLENBQUMsU0FBUyxDQUFDLENBQUM7UUFDdEMsS0FBSyxtQkFBbUI7WUFDdEIsT0FBTyxvQkFBb0IsQ0FBQyxTQUFTLENBQUMsQ0FBQztRQUN6QyxLQUFL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QVksRUFBRSxDQUFDLENBQUM7SUFDdkQsT0FBTyxDQUFDLEdBQUcsQ0FBQyxhQUFhLE1BQU0sQ0FBQyxNQUFNLENBQUMsSUFBSSxDQUFDLElBQUksQ0FBQyxFQUFFLENBQUMsQ0FBQztJQUVyRCxPQUFPLENBQUMsR0FBRyxDQUFDLHlCQUF5QixDQUFDLENBQUM7QUFDekMsQ0FBQyIsInNvdXJjZXNDb250ZW50IjpbIi8qKlxuICogQGZhcm1jcmFmdC9kcm0tY29yZVxuICogXG4gKiBEUk0gYW5kIHZlcnNpb24gdmVyaWZpY2F0aW9uIHRocm91Z2ggc2hhZGVyIHN0YXRlIGNoYWlucy5cbiAqIFxuICogQ29uY2VwdDpcbiAqIC0gRWFjaCBjbGllbnQgcnVucyBzaGFkZXJzIHRoYXQgcHJvZHVjZSBkZXRlcm1pbmlzdGljIG91dHB1dHNcbiAqIC0gU2hhZGVyIG91dHB1dHMgYmVjb21lIHNlZWRzIGZvciBzdWJzZXF1ZW50IGNoYWxsZW5nZXNcbiAqIC0gVGhlIGNoYWluIG9mIHN0YXRlcyBwcm92ZXMgdGhlIGNsaWVudCBoYXMgdGhlIGNvcnJlY3Qgc2hhZGVyc1xuICogLSBUYW1wZXJlZCBjbGllbnRzIHByb2R1Y2UgZGlmZmVyZW50IHN0YXRlIGNoYWlucyBhbmQgZmFpbCB2ZXJpZmljYXRpb25cbiAqL1xuXG5pbXBvcnQgeyBjcmVhdGVIYXNoLCByYW5kb21CeXRlcyB9IGZyb20gJ2NyeXB0byc7XG5cbi8vID09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT1cbi8vIFR5cGVzXG4vLyA9PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09XG5cbmV4cG9ydCBpbnRlcmZhY2UgU2hhZGVyRmluZ2VycHJpbnQge1xuICBzaGFkZXJJZDogc3RyaW5nO1xuICB2ZXJzaW9uOiBzdHJpbmc7XG4gIGlucHV0U2VlZDogc3RyaW5nO1xuICBvdXRwdXRIYXNoOiBzdHJpbmc7XG4gIHRpbWVzdGFtcDogbnVtYmVyO1xufVxuXG5leHBvcnQgaW50ZXJmYWNlIFN0YXRlQ2hhaW5MaW5rIHtcbiAgaW5kZXg6IG51bWJlcjtcbiAgcHJldmlvdXNIYXNoOiBzdHJpbmc7XG4gIHNoYWRlckZpbmdlcnByaW50OiBTaGFkZXJGaW5nZXJwcmludDtcbiAgd29ya1Byb29mOiBzdHJpbmc7XG4gIGxpbmtIYXNoOiBzdHJpbmc7XG59XG5cbmV4cG9ydCBpbnRlcmZhY2UgVmVyc2lvbk1hbmlmZXN0IHtcbiAgdmVyc2lvbjogc3RyaW5nO1xuICBzaGFkZXJIYXNoZXM6IE1hcDxzdHJpbmcsIHN0cmluZz47XG4gIGV4cGVjdGVkT3V0cHV0czogTWFwPHN0cmluZywgc3RyaW5nPjsgLy8gc2VlZCAtPiBleHBlY3RlZCBvdXRwdXQgaGFzaFxuICBidWlsZFRpbWVzdGFtcDogbnVtYmVyO1xuICBzaWduYXR1cmU6IHN0cmluZztcbn1cblxuZXhwb3J0IGludGVyZmFjZSBEUk1DaGFsbGVuZ2Uge1xuICBjaGFsbGVuZ2VJZDogc3RyaW5nO1xuICByZXF1aXJlZFNoYWRlcnM6IHN0cmluZ1tdO1xuICBpbnB1dFNlZWRzOiBNYXA8c3RyaW5nLCBzdHJpbmc+O1xuICBwcmV2aW91c0NoYWluSGFzaDogc3RyaW5nO1xuICBkaWZmaWN1bHR5OiBudW1iZXI7XG4gIGV4cGlyZXNBdDogbnVtYmVyO1xuICB3b3JrVHlwZTogJ3NoYWRlcl92ZXJpZnknIHwgJ2ZvbGRpbmdfY2hhaW4nIHwgJ2VudHJvcHlfY2hhaW4nO1xufVxuXG5leHBvcnQgaW50ZXJmYWNlIERSTVJlc3BvbnNlIHtcbiAgY2hhbGxlbmdlSWQ6IHN0cmluZztcbiAgY2xpZW50VmVyc2lvbjogc3RyaW5nO1xuICBzdGF0ZUNoYWluOiBTdGF0ZUNoYWluTGlua1tdO1xuICB3b3JrUmVzdWx0OiBzdHJpbmc7XG4gIG5vbmNlOiBudW1iZXI7XG4gIGNsaWVudFNpZ25hdHVyZTogc3RyaW5nO1xufVxuXG5leHBvcnQgaW50ZXJmYWNlIFZlcmlmaWNhdGlvblJlc3VsdCB7XG4gIHZhbGlkOiBib29sZWFuO1xuICB2ZXJzaW9uTWF0Y2g6IGJvb2xlYW47XG4gIGNoYWluSW50ZWdyaXR5OiBib29sZWFuO1xuICB3b3JrVmFsaWQ6IGJvb2xlYW47XG4gIHNoYWRlck91dHB1dHNNYXRjaDogYm9vbGVhbjtcbiAgZXJyb3JzOiBzdHJpbmdbXTtcbn1cblxuZXhwb3J0IGludGVyZmFjZSBDbGllbnRTdGF0ZSB7XG4gIGNsaWVudElkOiBzdHJpbmc7XG4gIHZlcnNpb246IHN0cmluZztcbiAgbGFzdENoYWluSGFzaDogc3RyaW5nO1xuICBjaGFpbkxlbmd0aDogbnVtYmVyO1xuICB0b3RhbFdvcmtDb21wbGV0ZWQ6IG51bWJlcjtcbiAgbGFzdFZlcmlmaWVkQXQ6IG51bWJlcjtcbiAgdHJ1c3RTY29yZTogbnVtYmVyO1xufVxuXG4vLyA9PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09XG4vLyBTaGFkZXIgU3RhdGUgUmVnaXN0cnlcbi8vID09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT1cblxuLyoqXG4gKiBLbm93biBzaGFkZXIgcHJvZ3JhbXMgYW5kIHRoZWlyIGV4cGVjdGVkIGJlaGF2aW9yc1xuICovXG5leHBvcnQgY29uc3QgU0hBREVSX1JFR0lTVFJZID0ge1xuICAnaGFzaF9jb21wdXRlX3YxJzoge1xuICAgIHZlcnNpb246ICcxLjAuMCcsXG4gICAgLy8gRXhwZWN0ZWQgb3V0cHV0IGZvciB0ZXN0IHNlZWQgLSB1c2VkIHRvIHZlcmlmeSBzaGFkZXIgY29ycmVjdG5lc3NcbiAgICB0ZXN0U2VlZDogJzAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAnLFxuICAgIGV4cGVjdGVkVGVzdE91dHB1dDogJ2UzYjBjNDQyOThmYzFjMTQ5YWZiZjRjODk5NmZiOTI0MjdhZTQxZTQ2NDliOTM0Y2E0OTU5OTFiNzg1MmI4NTUnLFxuICB9LFxuICAnZm9sZGluZ19lbmVyZ3lfdjEnOiB7XG4gICAgdmVyc2lvbjogJzEuMC4wJyxcbiAgICB0ZXN0U2VlZDogJ0FDREVGR0hJS0xNTlBRUlNUVldZJywgLy8gYW1pbm8gYWNpZCBzZXF1ZW5jZVxuICAgIGV4cGVjdGVkVGVzdE91dHB1dDogJzdkODY1ZTk1OWIyNDY2OTE4Yzk4NjNhZmNhOTQyZDBmYjg5ZDdjOWFjMGM5OWJhZmMzNzQ5NTA0ZGVkOTc3MzAnLFxuICB9LFxuICAnZW50cm9weV92MSc6IHtcbiAgICB2ZXJzaW9uOiAnMS4wLjAnLFxuICAgIHRlc3RTZWVkOiAnMTIzNDU2Nzg5MGFiY2RlZicsXG4gICAgZXhwZWN0ZWRUZXN0T3V0cHV0OiAnYTU5MWE2ZDQwYmY0MjA0MDRhMDExNzMzY2ZiN2IxOTBkNjJjNjViZjBiY2RhMzJiNTdiMjc3ZDlhZDlmMTQ2ZScsXG4gIH0sXG4gICd2ZXJzaW9uX3Byb29mX3YxJzoge1xuICAgIHZlcnNpb246ICcxLjAuMCcsXG4gICAgdGVzdFNlZWQ6ICd2ZXJzaW9uX2NoZWNrJyxcbiAgICBleHBlY3RlZFRlc3RPdXRwdXQ6ICcyYzI2YjQ2YjY4ZmZjNjhmZjk5YjQ1M2MxZDMwNDEzNDEzNDIyZDcwNjQ4M2JmYTBmOThhNWU4ODYyNjZlN2FlJyxcbiAgfSxcbn07XG5cbi8vID09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT1cbi8vIFNoYWRlciBFeGVjdXRpb24gU2ltdWxhdG9yXG4vLyA9PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09XG5cbi8qKlxuICogU2ltdWxhdGVzIHNoYWRlciBleGVjdXRpb24gd2l0aCBkZXRlcm1pbmlzdGljIG91dHB1dHMuXG4gKiBJbiB0aGUgcmVhbCBtb2QsIHRoaXMgd291bGQgYmUgYWN0dWFsIEdQVSBzaGFkZXIgZXhlY3V0aW9uLlxuICovXG5leHBvcnQgZnVuY3Rpb24gZXhlY3V0ZVNoYWRlcihzaGFkZXJJZDogc3RyaW5nLCBpbnB1dFNlZWQ6IHN0cmluZyk6IHN0cmluZyB7XG4gIC8vIEVhY2ggc2hhZGVyIHR5cGUgaGFzIGRpZmZlcmVudCBjb21wdXRhdGlvbiBjaGFyYWN0ZXJpc3RpY3NcbiAgc3dpdGNoIChzaGFkZXJJZCkge1xuICAgIGNhc2UgJ2hhc2hfY29tcHV0ZV92MSc6XG4gICAgICByZXR1cm4gY29tcHV0ZUhhc2hTaGFkZXIoaW5wdXRTZWVkKTtcbiAgICBjYXNlICdmb2xkaW5nX2VuZXJneV92MSc6XG4gICAgICByZXR1cm4gY29tcHV0ZUZvbGRpbmdTaGFkZXIoaW5wdXRTZWVkKTtcbiAgICBjYXNlICdlbnRyb3B5X3YxJzpcbiAgICAgIHJldHVybiBjb21wdXRlRW50cm9weVNoYWRlcihpbnB1dFNlZWQpO1xuICAgIGNhc2UgJ3ZlcnNpb25fcHJvb2ZfdjEnOlxuICAgICAgcmV0dXJuIGNvbXB1dGVWZXJzaW9uUHJvb2ZTaGFkZXIoaW5wdXRTZWVkKTtcbiAgICBkZWZhdWx0OlxuICAgICAgdGhyb3cgbmV3IEVycm9yKGBVbmtub3duIHNoYWRlcjogJHtzaGFkZXJJZH1gKTtcbiAgfVxufVxuXG5mdW5jdGlvbiBjb21wdXRlSGFzaFNoYWRlcihpbnB1dDogc3RyaW5nKTogc3RyaW5nIHtcbiAgLy8gU2ltdWxhdGVzIGl0ZXJhdGl2ZSBTSEEtMjU2IGxpa2UgYSBHUFUgd291bGQgY29tcHV0ZVxuICBsZXQgc3RhdGUgPSBpbnB1dDtcbiAgZm9yIChsZXQgaSA9IDA7IGkgPCAxMDAwOyBpKyspIHtcbiAgICBzdGF0ZSA9IGNyZWF0ZUhhc2goJ3NoYTI1NicpLnVwZGF0ZShzdGF0ZSArIGkudG9TdHJpbmcoKSkuZGlnZXN0KCdoZXgnKTtcbiAgfVxuICByZXR1cm4gc3RhdGU7XG59XG5cbmZ1bmN0aW9uIGNvbXB1dGVGb2xkaW5nU2hhZGVyKGFtaW5vU2VxdWVuY2U6IHN0cmluZyk6IHN0cmluZyB7XG4gIC8vIFNpbXVsYXRlcyBlbmVyZ3kgY2FsY3VsYXRpb24gZm9yIHByb3RlaW4gZm9sZGluZ1xuICAvLyBSZWFsIGltcGxlbWVudGF0aW9uIHdvdWxkIHVzZSBMZW5uYXJkLUpvbmVzIHBvdGVudGlhbFxuICBsZXQgZW5lcmd5ID0gMDtcbiAgY29uc3QgcG9zaXRpb25zOiBudW1iZXJbXSA9IFtdO1xuICBcbiAgZm9yIChsZXQgaSA9IDA7IGkgPCBhbWlub1NlcXVlbmNlLmxlbmd0aDsgaSsrKSB7XG4gICAgY29uc3QgY2hhciA9IGFtaW5vU2VxdWVuY2UuY2hhckNvZGVBdChpKTtcbiAgICBwb3NpdGlvbnMucHVzaChNYXRoLnNpbihjaGFyICogMC4xKSAqIDEwKTtcbiAgICBwb3NpdGlvbnMucHVzaChNYXRoLmNvcyhjaGFyICogMC4xKSAqIDEwKTtcbiAgICBwb3NpdGlvbnMucHVzaChNYXRoLnNpbihjaGFyICogMC4yKSAqIDEwKTtcbiAgfVxuICBcbiAgLy8gQ2FsY3VsYXRlIHBhaXJ3aXNlIGludGVyYWN0aW9uc1xuICBmb3IgKGxldCBpID0gMDsgaSA8IHBvc2l0aW9ucy5sZW5ndGg7IGkgKz0gMykge1xuICAgIGZvciAobGV0IGogPSBpICsgMzsgaiA8IHBvc2l0aW9ucy5sZW5ndGg7IGogKz0gMykge1xuICAgICAgY29uc3QgZHggPSBwb3NpdGlvbnNbaV0gLSBwb3NpdGlvbnNbal07XG4gICAgICBjb25zdCBkeSA9IHBvc2l0aW9uc1tpICsgMV0gLSBwb3NpdGlvbnNbaiArIDFdO1xuICAgICAgY29uc3QgZHogPSBwb3NpdGlvbnNbaSArIDJdIC0gcG9zaXRpb25zW2ogKyAyXTtcbiAgICAgIGNvbnN0IHIyID0gZHggKiBkeCArIGR5ICogZHkgKyBkeiAqIGR6O1xuICAgICAgY29uc3QgcjYgPSByMiAqIHIyICogcjI7XG4gICAgICBlbmVyZ3kgKz0gMSAvIHI2IC0gMiAvIChyNiAqIHI2KTtcbiAgICB9XG4gIH1cbiAgXG4gIHJldHVybiBjcmVhdGVIYXNoKCdzaGEyNTYnKVxuICAgIC51cGRhdGUoZW5lcmd5LnRvRml4ZWQoMTApICsgYW1pbm9TZXF1ZW5jZSlcbiAgICAuZGlnZXN0KCdoZXgnKTtcbn1cblxuZnVuY3Rpb24gY29tcHV0ZUVudHJvcHlTaGFkZXIoaW5wdXQ6IHN0cmluZyk6IHN0cmluZyB7XG4gIC8vIFNpbXVsYXRlcyBlbnRyb3B5IGdlbmVyYXRpb24gdGhyb3VnaCBjaGFvdGljIG1peGluZ1xuICBjb25zdCBzdGF0ZSA9IG5ldyBVaW50MzJBcnJheSgxNik7XG4gIGNvbnN0IGlucHV0Qnl0ZXMgPSBCdWZmZXIuZnJvbShpbnB1dCk7XG4gIFxuICAvLyBJbml0aWFsaXplIHN0YXRlXG4gIGZvciAobGV0IGkgPSAwOyBpIDwgMTY7IGkrKykge1xuICAgIHN0YXRlW2ldID0gaW5wdXRCeXRlc1tpICUgaW5wdXRCeXRlcy5sZW5ndGhdICogMHgwMTAwMDE5MztcbiAgfVxuICBcbiAgLy8gQ2hhb3RpYyBtaXhpbmcgcm91bmRzXG4gIGZvciAobGV0IHJvdW5kID0gMDsgcm91bmQgPCAxMDA7IHJvdW5kKyspIHtcbiAgICBmb3IgKGxldCBpID0gMDsgaSA8IDE2OyBpKyspIHtcbiAgICAgIGNvbnN0IGEgPSBzdGF0ZVtpXTtcbiAgICAgIGNvbnN0IGIgPSBzdGF0ZVsoaSArIDEpICUgMTZdO1xuICAgICAgY29uc3QgYyA9IHN0YXRlWyhpICsgNSkgJSAxNl07XG4gICAgICBzdGF0ZVtpXSA9IChhIF4gKGIgPDwgNykgXiAoYyA+Pj4gMykpID4+PiAwO1xuICAgIH1cbiAgfVxuICBcbiAgcmV0dXJuIGNyZWF0ZUhhc2goJ3NoYTI1NicpXG4gICAgLnVwZGF0ZShCdWZmZXIuZnJvbShzdGF0ZS5idWZmZXIpKVxuICAgIC5kaWdlc3QoJ2hleCcpO1xufVxuXG5mdW5jdGlvbiBjb21wdXRlVmVyc2lvblByb29mU2hhZGVyKGlucHV0OiBzdHJpbmcpOiBzdHJpbmcge1xuICAvLyBWZXJzaW9uLXNwZWNpZmljIGNvbXB1dGF0aW9uIHRoYXQgZGlmZmVycyBwZXIgYnVpbGRcbiAgLy8gVGhpcyB3b3VsZCBiZSBjb21waWxlZCBkaWZmZXJlbnRseSBpbiBlYWNoIHZlcnNpb25cbiAgY29uc3QgdmVyc2lvblNhbHQgPSAnZmFybWNyYWZ0X3YxLjAuMF9idWlsZF8yMDI2JztcbiAgcmV0dXJuIGNyZWF0ZUhhc2goJ3NoYTI1NicpXG4gICAgLnVwZGF0ZShpbnB1dCArIHZlcnNpb25TYWx0KVxuICAgIC5kaWdlc3QoJ2hleCcpO1xufVxuXG4vLyA9PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09XG4vLyBTdGF0ZSBDaGFpbiBNYW5hZ2VtZW50XG4vLyA9PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09XG5cbmV4cG9ydCBjbGFzcyBTdGF0ZUNoYWluIHtcbiAgcHJpdmF0ZSBsaW5rczogU3RhdGVDaGFpbkxpbmtbXSA9IFtdO1xuICBwcml2YXRlIGN1cnJlbnRIYXNoOiBzdHJpbmcgPSAnMCcucmVwZWF0KDY0KTsgLy8gR2VuZXNpcyBoYXNoXG4gIFxuICBjb25zdHJ1Y3Rvcihwcml2YXRlIGNsaWVudFZlcnNpb246IHN0cmluZykge31cbiAgXG4gIC8qKlxuICAgKiBBZGQgYSBuZXcgbGluayB0byB0aGUgY2hhaW4gYnkgZXhlY3V0aW5nIGEgc2hhZGVyXG4gICAqL1xuICBhZGRMaW5rKHNoYWRlcklkOiBzdHJpbmcsIGlucHV0U2VlZDogc3RyaW5nLCB3b3JrUHJvb2Y6IHN0cmluZyk6IFN0YXRlQ2hhaW5MaW5rIHtcbiAgICBjb25zdCBzaGFkZXJPdXRwdXQgPSBleGVjdXRlU2hhZGVyKHNoYWRlcklkLCBpbnB1dFNlZWQpO1xuICAgIFxuICAgIGNvbnN0IGZpbmdlcnByaW50OiBTaGFkZXJGaW5nZXJwcmludCA9IHtcbiAgICAgIHNoYWRlcklkLFxuICAgICAgdmVyc2lvbjogdGhpcy5jbGllbnRWZXJzaW9uLFxuICAgICAgaW5wdXRTZWVkLFxuICAgICAgb3V0cHV0SGFzaDogc2hhZGVyT3V0cHV0LFxuICAgICAgdGltZXN0YW1wOiBEYXRlLm5vdygpLFxuICAgIH07XG4gICAgXG4gICAgY29uc3QgbGlua0RhdGEgPSBKU09OLnN0cmluZ2lmeSh7XG4gICAgICBpbmRleDogdGhpcy5saW5rcy5sZW5ndGgsXG4gICAgICBwcmV2aW91c0hhc2g6IHRoaXMuY3VycmVudEhhc2gsXG4gICAgICBmaW5nZXJwcmludCxcbiAgICAgIHdvcmtQcm9vZixcbiAgICB9KTtcbiAgICBcbiAgICBjb25zdCBsaW5rSGFzaCA9IGNyZWF0ZUhhc2goJ3NoYTI1NicpLnVwZGF0ZShsaW5rRGF0YSkuZGlnZXN0KCdoZXgnKTtcbiAgICBcbiAgICBjb25zdCBsaW5rOiBTdGF0ZUNoYWluTGluayA9IHtcbiAgICAgIGluZGV4OiB0aGlzLmxpbmtzLmxlbmd0aCxcbiAgICAgIHByZXZpb3VzSGFzaDogdGhpcy5jdXJyZW50SGFzaCxcbiAgICAgIHNoYWRlckZpbmdlcnByaW50OiBmaW5nZXJwcmludCxcbiAgICAgIHdvcmtQcm9vZixcbiAgICAgIGxpbmtIYXNoLFxuICAgIH07XG4gICAgXG4gICAgdGhpcy5saW5rcy5wdXNoKGxpbmspO1xuICAgIHRoaXMuY3VycmVudEhhc2ggPSBsaW5rSGFzaDtcbiAgICBcbiAgICByZXR1cm4gbGluaztcbiAgfVxuICBcbiAgLyoqXG4gICAqIEdldCB0aGUgY3VycmVudCBjaGFpbiBoYXNoIChmb3IgZW1iZWRkaW5nIGluIG5leHQgY2hhbGxlbmdlKVxuICAgKi9cbiAgZ2V0Q2hhaW5IYXNoKCk6IHN0cmluZyB7XG4gICAgcmV0dXJuIHRoaXMuY3VycmVudEhhc2g7XG4gIH1cbiAgXG4gIC8qKlxuICAgKiBHZXQgYWxsIGxpbmtzIGZvciB2ZXJpZmljYXRpb25cbiAgICovXG4gIGdldExpbmtzKCk6IFN0YXRlQ2hhaW5MaW5rW10ge1xuICAgIHJldHVybiBbLi4udGhpcy5saW5rc107XG4gIH1cbiAgXG4gIC8qKlxuICAgKiBFeHBvcnQgY2hhaW4gZm9yIHRyYW5zbWlzc2lvblxuICAgKi9cbiAgZXhwb3J0KCk6IHsgbGlua3M6IFN0YXRlQ2hhaW5MaW5rW107IGNoYWluSGFzaDogc3RyaW5nOyB2ZXJzaW9uOiBzdHJpbmcgfSB7XG4gICAgcmV0dXJuIHtcbiAgICAgIGxpbmtzOiB0aGlzLmxpbmtzLFxuICAgICAgY2hhaW5IYXNoOiB0aGlzLmN1cnJlbnRIYXNoLFxuICAgICAgdmVyc2lvbjogdGhpcy5jbGllbnRWZXJzaW9uLFxuICAgIH07XG4gIH1cbiAgXG4gIC8qKlxuICAgKiBJbXBvcnQgZXhpc3RpbmcgY2hhaW5cbiAgICovXG4gIHN0YXRpYyBpbXBvcnQoZGF0YTogeyBsaW5rczogU3RhdGVDaGFpbkxpbmtbXTsgY2hhaW5IYXNoOiBzdHJpbmc7IHZlcnNpb246IHN0cmluZyB9KTogU3RhdGVDaGFpbiB7XG4gICAgY29uc3QgY2hhaW4gPSBuZXcgU3RhdGVDaGFpbihkYXRhLnZlcnNpb24pO1xuICAgIGNoYWluLmxpbmtzID0gZGF0YS5saW5rcztcbiAgICBjaGFpbi5jdXJyZW50SGFzaCA9IGRhdGEuY2hhaW5IYXNoO1xuICAgIHJldHVybiBjaGFpbjtcbiAgfVxufVxuXG4vLyA9PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09XG4vLyBEUk0gQ2hhbGxlbmdlIEdlbmVyYXRvclxuLy8gPT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PVxuXG5leHBvcnQgY2xhc3MgRFJNQ2hhbGxlbmdlR2VuZXJhdG9yIHtcbiAgcHJpdmF0ZSBhY3RpdmVNYW5pZmVzdDogVmVyc2lvbk1hbmlmZXN0O1xuICBcbiAgY29uc3RydWN0b3IobWFuaWZlc3Q6IFZlcnNpb25NYW5pZmVzdCkge1xuICAgIHRoaXMuYWN0aXZlTWFuaWZlc3QgPSBtYW5pZmVzdDtcbiAgfVxuICBcbiAgLyoqXG4gICAqIEdlbmVyYXRlIGEgY2hhbGxlbmdlIHRoYXQgcmVxdWlyZXMgc3BlY2lmaWMgc2hhZGVyIGV4ZWN1dGlvblxuICAgKi9cbiAgZ2VuZXJhdGVDaGFsbGVuZ2UoXG4gICAgY2xpZW50U3RhdGU6IENsaWVudFN0YXRlLFxuICAgIHdvcmtUeXBlOiBEUk1DaGFsbGVuZ2VbJ3dvcmtUeXBlJ10gPSAnc2hhZGVyX3ZlcmlmeSdcbiAgKTogRFJNQ2hhbGxlbmdlIHtcbiAgICBjb25zdCBjaGFsbGVuZ2VJZCA9IHJhbmRvbUJ5dGVzKDE2KS50b1N0cmluZygnaGV4Jyk7XG4gICAgY29uc3QgcmVxdWlyZWRTaGFkZXJzID0gdGhpcy5zZWxlY3RSZXF1aXJlZFNoYWRlcnMod29ya1R5cGUpO1xuICAgIGNvbnN0IGlucHV0U2VlZHMgPSBuZXcgTWFwPHN0cmluZywgc3RyaW5nPigpO1xuICAgIFxuICAgIC8vIEdlbmVyYXRlIHNlZWRzIHRoYXQgY2hhaW4gZnJvbSBwcmV2aW91cyBzdGF0ZVxuICAgIGZvciAoY29uc3Qgc2hhZGVySWQgb2YgcmVxdWlyZWRTaGFkZXJzKSB7XG4gICAgICAvLyBTZWVkIGluY2x1ZGVzIHByZXZpb3VzIGNoYWluIGhhc2ggdG8gY3JlYXRlIGRlcGVuZGVuY3lcbiAgICAgIGNvbnN0IHNlZWQgPSBjcmVhdGVIYXNoKCdzaGEyNTYnKVxuICAgICAgICAudXBkYXRlKGNsaWVudFN0YXRlLmxhc3RDaGFpbkhhc2ggKyBzaGFkZXJJZCArIGNoYWxsZW5nZUlkKVxuICAgICAgICAuZGlnZXN0KCdoZXgnKTtcbiAgICAgIGlucHV0U2VlZHMuc2V0KHNoYWRlcklkLCBzZWVkKTtcbiAgICB9XG4gICAgXG4gICAgLy8gRGlmZmljdWx0eSBzY2FsZXMgd2l0aCB0cnVzdCBzY29yZSAobG93ZXIgdHJ1c3QgPSBoYXJkZXIgY2hhbGxlbmdlcylcbiAgICBjb25zdCBkaWZmaWN1bHR5ID0gTWF0aC5tYXgoMSwgNSAtIE1hdGguZmxvb3IoY2xpZW50U3RhdGUudHJ1c3RTY29yZSAvIDIwKSk7XG4gICAgXG4gICAgcmV0dXJuIHtcbiAgICAgIGNoYWxsZW5nZUlkLFxuICAgICAgcmVxdWlyZWRTaGFkZXJzLFxuICAgICAgaW5wdXRTZWVkcyxcbiAgICAgIHByZXZpb3VzQ2hhaW5IYXNoOiBjbGllbnRTdGF0ZS5sYXN0Q2hhaW5IYXNoLFxuICAgICAgZGlmZmljdWx0eSxcbiAgICAgIGV4cGlyZXNBdDogRGF0ZS5ub3coKSArIDYwMDAwLCAvLyAxIG1pbnV0ZVxuICAgICAgd29ya1R5cGUsXG4gICAgfTtcbiAgfVxuICBcbiAgcHJpdmF0ZSBzZWxlY3RSZXF1aXJlZFNoYWRlcnMod29ya1R5cGU6IERSTUNoYWxsZW5nZVsnd29ya1R5cGUnXSk6IHN0cmluZ1tdIHtcbiAgICBzd2l0Y2ggKHdvcmtUeXBlKSB7XG4gICAgICBjYXNlICdzaGFkZXJfdmVyaWZ5JzpcbiAgICAgICAgcmV0dXJuIFsndmVyc2lvbl9wcm9vZl92MScsICdoYXNoX2NvbXB1dGVfdjEnXTtcbiAgICAgIGNhc2UgJ2ZvbGRpbmdfY2hhaW4nOlxuICAgICAgICByZXR1cm4gWydmb2xkaW5nX2VuZXJneV92MScsICd2ZXJzaW9uX3Byb29mX3YxJ107XG4gICAgICBjYXNlICdlbnRyb3B5X2NoYWluJzpcbiAgICAgICAgcmV0dXJuIFsnZW50cm9weV92MScsICdoYXNoX2NvbXB1dGVfdjEnLCAndmVyc2lvbl9wcm9vZl92MSddO1xuICAgICAgZGVmYXVsdDpcbiAgICAgICAgcmV0dXJuIFsndmVyc2lvbl9wcm9vZl92MSddO1xuICAgIH1cbiAgfVxuICBcbiAgLyoqXG4gICAqIEdldCB0aGUgZXhwZWN0ZWQgb3V0cHV0cyBmb3IgYSBjaGFsbGVuZ2UgKHNlcnZlci1zaWRlKVxuICAgKi9cbiAgZ2V0RXhwZWN0ZWRPdXRwdXRzKGNoYWxsZW5nZTogRFJNQ2hhbGxlbmdlKTogTWFwPHN0cmluZywgc3RyaW5nPiB7XG4gICAgY29uc3QgZXhwZWN0ZWQgPSBuZXcgTWFwPHN0cmluZywgc3RyaW5nPigpO1xuICAgIFxuICAgIGZvciAoY29uc3QgW3NoYWRlcklkLCBzZWVkXSBvZiBjaGFsbGVuZ2UuaW5wdXRTZWVkcykge1xuICAgICAgLy8gU2VydmVyIGNvbXB1dGVzIGV4cGVjdGVkIG91dHB1dCB1c2luZyBjYW5vbmljYWwgc2hhZGVyXG4gICAgICBjb25zdCBvdXRwdXQgPSBleGVjdXRlU2hhZGVyKHNoYWRlcklkLCBzZWVkKTtcbiAgICAgIGV4cGVjdGVkLnNldChzaGFkZXJJZCwgb3V0cHV0KTtcbiAgICB9XG4gICAgXG4gICAgcmV0dXJuIGV4cGVjdGVkO1xuICB9XG59XG5cbi8vID09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT1cbi8vIERSTSBWZXJpZmllclxuLy8gPT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PVxuXG5leHBvcnQgY2xhc3MgRFJNVmVyaWZpZXIge1xuICBwcml2YXRlIG1hbmlmZXN0OiBWZXJzaW9uTWFuaWZlc3Q7XG4gIHByaXZhdGUgY2xpZW50U3RhdGVzOiBNYXA8c3RyaW5nLCBDbGllbnRTdGF0ZT4gPSBuZXcgTWFwKCk7XG4gIFxuICBjb25zdHJ1Y3RvcihtYW5pZmVzdDogVmVyc2lvbk1hbmlmZXN0KSB7XG4gICAgdGhpcy5tYW5pZmVzdCA9IG1hbmlmZXN0O1xuICB9XG4gIFxuICAvKipcbiAgICogVmVyaWZ5IGEgRFJNIHJlc3BvbnNlIGZyb20gYSBjbGllbnRcbiAgICovXG4gIHZlcmlmeShjaGFsbGVuZ2U6IERSTUNoYWxsZW5nZSwgcmVzcG9uc2U6IERSTVJlc3BvbnNlKTogVmVyaWZpY2F0aW9uUmVzdWx0IHtcbiAgICBjb25zdCBlcnJvcnM6IHN0cmluZ1tdID0gW107XG4gICAgbGV0IHZlcnNpb25NYXRjaCA9IHRydWU7XG4gICAgbGV0IGNoYWluSW50ZWdyaXR5ID0gdHJ1ZTtcbiAgICBsZXQgd29ya1ZhbGlkID0gdHJ1ZTtcbiAgICBsZXQgc2hhZGVyT3V0cHV0c01hdGNoID0gdHJ1ZTtcbiAgICBcbiAgICAvLyBDaGVjayBjaGFsbGVuZ2UgaGFzbid0IGV4cGlyZWRcbiAgICBpZiAoRGF0ZS5ub3coKSA+IGNoYWxsZW5nZS5leHBpcmVzQXQpIHtcbiAgICAgIGVycm9ycy5wdXNoKCdDaGFsbGVuZ2UgZXhwaXJlZCcpO1xuICAgICAgcmV0dXJuIHsgdmFsaWQ6IGZhbHNlLCB2ZXJzaW9uTWF0Y2gsIGNoYWluSW50ZWdyaXR5LCB3b3JrVmFsaWQsIHNoYWRlck91dHB1dHNNYXRjaCwgZXJyb3JzIH07XG4gICAgfVxuICAgIFxuICAgIC8vIFZlcmlmeSBjaGFsbGVuZ2UgSUQgbWF0Y2hlc1xuICAgIGlmIChyZXNwb25zZS5jaGFsbGVuZ2VJZCAhPT0gY2hhbGxlbmdlLmNoYWxsZW5nZUlkKSB7XG4gICAgICBlcnJvcnMucHVzaCgnQ2hhbGxlbmdlIElEIG1pc21hdGNoJyk7XG4gICAgICByZXR1cm4geyB2YWxpZDogZmFsc2UsIHZlcnNpb25NYXRjaCwgY2hhaW5JbnRlZ3JpdHksIHdvcmtWYWxpZCwgc2hhZGVyT3V0cHV0c01hdGNoLCBlcnJvcnMgfTtcbiAgICB9XG4gICAgXG4gICAgLy8gVmVyaWZ5IHZlcnNpb24gbWF0Y2hlcyBtYW5pZmVzdFxuICAgIGlmIChyZXNwb25zZS5jbGllbnRWZXJzaW9uICE9PSB0aGlzLm1hbmlmZXN0LnZlcnNpb24pIHtcbiAgICAgIHZlcnNpb25NYXRjaCA9IGZhbHNlO1xuICAgICAgZXJyb3JzLnB1c2goYFZlcnNpb24gbWlzbWF0Y2g6IGV4cGVjdGVkICR7dGhpcy5tYW5pZmVzdC52ZXJzaW9ufSwgZ290ICR7cmVzcG9uc2UuY2xpZW50VmVyc2lvbn1gKTtcbiAgICB9XG4gICAgXG4gICAgLy8gVmVyaWZ5IHN0YXRlIGNoYWluIGludGVncml0eVxuICAgIGNoYWluSW50ZWdyaXR5ID0gdGhpcy52ZXJpZnlDaGFpbkludGVncml0eShyZXNwb25zZS5zdGF0ZUNoYWluLCBjaGFsbGVuZ2UucHJldmlvdXNDaGFpbkhhc2gpO1xuICAgIGlmICghY2hhaW5JbnRlZ3JpdHkpIHtcbiAgICAgIGVycm9ycy5wdXNoKCdTdGF0ZSBjaGFpbiBpbnRlZ3JpdHkgY2hlY2sgZmFpbGVkJyk7XG4gICAgfVxuICAgIFxuICAgIC8vIFZlcmlmeSBzaGFkZXIgb3V0cHV0cyBtYXRjaCBleHBlY3RlZFxuICAgIGNvbnN0IGV4cGVjdGVkT3V0cHV0cyA9IG5ldyBNYXA8c3RyaW5nLCBzdHJpbmc+KCk7XG4gICAgZm9yIChjb25zdCBbc2hhZGVySWQsIHNlZWRdIG9mIGNoYWxsZW5nZS5pbnB1dFNlZWRzKSB7XG4gICAgICBleHBlY3RlZE91dHB1dHMuc2V0KHNoYWRlcklkLCBleGVjdXRlU2hhZGVyKHNoYWRlcklkLCBzZWVkKSk7XG4gICAgfVxuICAgIFxuICAgIGZvciAoY29uc3QgbGluayBvZiByZXNwb25zZS5zdGF0ZUNoYWluKSB7XG4gICAgICBjb25zdCBleHBlY3RlZCA9IGV4cGVjdGVkT3V0cHV0cy5nZXQobGluay5zaGFkZXJGaW5nZXJwcmludC5zaGFkZXJJZCk7XG4gICAgICBpZiAoZXhwZWN0ZWQgJiYgbGluay5zaGFkZXJGaW5nZXJwcmludC5vdXRwdXRIYXNoICE9PSBleHBlY3RlZCkge1xuICAgICAgICBzaGFkZXJPdXRwdXRzTWF0Y2ggPSBmYWxzZTtcbiAgICAgICAgZXJyb3JzLnB1c2goYFNoYWRlciBvdXRwdXQgbWlzbWF0Y2ggZm9yICR7bGluay5zaGFkZXJGaW5nZXJwcmludC5zaGFkZXJJZH1gKTtcbiAgICAgIH1cbiAgICB9XG4gICAgXG4gICAgLy8gVmVyaWZ5IHdvcmsgcHJvb2YgbWVldHMgZGlmZmljdWx0eVxuICAgIHdvcmtWYWxpZCA9IHRoaXMudmVyaWZ5V29ya1Byb29mKHJlc3BvbnNlLndvcmtSZXN1bHQsIHJlc3BvbnNlLm5vbmNlLCBjaGFsbGVuZ2UuZGlmZmljdWx0eSk7XG4gICAgaWYgKCF3b3JrVmFsaWQpIHtcbiAgICAgIGVycm9ycy5wdXNoKCdXb3JrIHByb29mIGludmFsaWQgb3IgaW5zdWZmaWNpZW50IGRpZmZpY3VsdHknKTtcbiAgICB9XG4gICAgXG4gICAgY29uc3QgdmFsaWQgPSB2ZXJzaW9uTWF0Y2ggJiYgY2hhaW5JbnRlZ3JpdHkgJiYgd29ya1ZhbGlkICYmIHNoYWRlck91dHB1dHNNYXRjaDtcbiAgICBcbiAgICByZXR1cm4ge1xuICAgICAgdmFsaWQsXG4gICAgICB2ZXJzaW9uTWF0Y2gsXG4gICAgICBjaGFpbkludGVncml0eSxcbiAgICAgIHdvcmtWYWxpZCxcbiAgICAgIHNoYWRlck91dHB1dHNNYXRjaCxcbiAgICAgIGVycm9ycyxcbiAgICB9O1xuICB9XG4gIFxuICBwcml2YXRlIHZlcmlmeUNoYWluSW50ZWdyaXR5KGNoYWluOiBTdGF0ZUNoYWluTGlua1tdLCBleHBlY3RlZFByZXZpb3VzSGFzaDogc3RyaW5nKTogYm9vbGVhbiB7XG4gICAgaWYgKGNoYWluLmxlbmd0aCA9PT0gMCkgcmV0dXJuIGZhbHNlO1xuICAgIFxuICAgIC8vIEZpcnN0IGxpbmsgc2hvdWxkIHJlZmVyZW5jZSB0aGUgZXhwZWN0ZWQgcHJldmlvdXMgaGFzaFxuICAgIGlmIChjaGFpblswXS5wcmV2aW91c0hhc2ggIT09IGV4cGVjdGVkUHJldmlvdXNIYXNoKSB7XG4gICAgICByZXR1cm4gZmFsc2U7XG4gICAgfVxuICAgIFxuICAgIC8vIFZlcmlmeSBlYWNoIGxpbmtcbiAgICBmb3IgKGxldCBpID0gMDsgaSA8IGNoYWluLmxlbmd0aDsgaSsrKSB7XG4gICAgICBjb25zdCBsaW5rID0gY2hhaW5baV07XG4gICAgICBcbiAgICAgIC8vIFZlcmlmeSBsaW5rIGhhc2ggLSBub3RlOiBpbmRpY2VzIGFyZSBjdW11bGF0aXZlIGluIHRoZSBmdWxsIGNoYWluXG4gICAgICBjb25zdCBsaW5rRGF0YSA9IEpTT04uc3RyaW5naWZ5KHtcbiAgICAgICAgaW5kZXg6IGxpbmsuaW5kZXgsXG4gICAgICAgIHByZXZpb3VzSGFzaDogbGluay5wcmV2aW91c0hhc2gsXG4gICAgICAgIGZpbmdlcnByaW50OiBsaW5rLnNoYWRlckZpbmdlcnByaW50LFxuICAgICAgICB3b3JrUHJvb2Y6IGxpbmsud29ya1Byb29mLFxuICAgICAgfSk7XG4gICAgICBjb25zdCBleHBlY3RlZEhhc2ggPSBjcmVhdGVIYXNoKCdzaGEyNTYnKS51cGRhdGUobGlua0RhdGEpLmRpZ2VzdCgnaGV4Jyk7XG4gICAgICBpZiAobGluay5saW5rSGFzaCAhPT0gZXhwZWN0ZWRIYXNoKSByZXR1cm4gZmFsc2U7XG4gICAgICBcbiAgICAgIC8vIFZlcmlmeSBjaGFpbiBjb250aW51aXR5IHdpdGhpbiB0aGlzIHJlc3BvbnNlXG4gICAgICBpZiAoaSA+IDAgJiYgbGluay5wcmV2aW91c0hhc2ggIT09IGNoYWluW2kgLSAxXS5saW5rSGFzaCkge1xuICAgICAgICByZXR1cm4gZmFsc2U7XG4gICAgICB9XG4gICAgfVxuICAgIFxuICAgIHJldHVybiB0cnVlO1xuICB9XG4gIFxuICBwcml2YXRlIHZlcmlmeVdvcmtQcm9vZihyZXN1bHQ6IHN0cmluZywgbm9uY2U6IG51bWJlciwgZGlmZmljdWx0eTogbnVtYmVyKTogYm9vbGVhbiB7XG4gICAgLy8gVGhlIHJlc3VsdCBhbHJlYWR5IGNvbnRhaW5zIHRoZSBoYXNoIHRoYXQgd2FzIGZvdW5kIHdpdGggdGhlIG5vbmNlXG4gICAgLy8gVmVyaWZ5IGl0IGhhcyB0aGUgcmVxdWlyZWQgbGVhZGluZyB6ZXJvc1xuICAgIGZvciAobGV0IGkgPSAwOyBpIDwgZGlmZmljdWx0eTsgaSsrKSB7XG4gICAgICBpZiAocmVzdWx0W2ldICE9PSAnMCcpIHJldHVybiBmYWxzZTtcbiAgICB9XG4gICAgXG4gICAgcmV0dXJuIHRydWU7XG4gIH1cbiAgXG4gIC8qKlxuICAgKiBVcGRhdGUgY2xpZW50IHN0YXRlIGFmdGVyIHN1Y2Nlc3NmdWwgdmVyaWZpY2F0aW9uXG4gICAqL1xuICB1cGRhdGVDbGllbnRTdGF0ZShjbGllbnRJZDogc3RyaW5nLCByZXNwb25zZTogRFJNUmVzcG9uc2UsIHJlc3VsdDogVmVyaWZpY2F0aW9uUmVzdWx0KTogdm9pZCB7XG4gICAgY29uc3QgZXhpc3RpbmcgPSB0aGlzLmNsaWVudFN0YXRlcy5nZXQoY2xpZW50SWQpO1xuICAgIGNvbnN0IGxhc3RMaW5rID0gcmVzcG9uc2Uuc3RhdGVDaGFpbltyZXNwb25zZS5zdGF0ZUNoYWluLmxlbmd0aCAtIDFdO1xuICAgIFxuICAgIGNvbnN0IG5ld1N0YXRlOiBDbGllbnRTdGF0ZSA9IHtcbiAgICAgIGNsaWVudElkLFxuICAgICAgdmVyc2lvbjogcmVzcG9uc2UuY2xpZW50VmVyc2lvbixcbiAgICAgIGxhc3RDaGFpbkhhc2g6IGxhc3RMaW5rPy5saW5rSGFzaCA/PyAnMCcucmVwZWF0KDY0KSxcbiAgICAgIGNoYWluTGVuZ3RoOiAoZXhpc3Rpbmc/LmNoYWluTGVuZ3RoID8/IDApICsgcmVzcG9uc2Uuc3RhdGVDaGFpbi5sZW5ndGgsXG4gICAgICB0b3RhbFdvcmtDb21wbGV0ZWQ6IChleGlzdGluZz8udG90YWxXb3JrQ29tcGxldGVkID8/IDApICsgMSxcbiAgICAgIGxhc3RWZXJpZmllZEF0OiBEYXRlLm5vdygpLFxuICAgICAgdHJ1c3RTY29yZTogdGhpcy5jYWxjdWxhdGVUcnVzdFNjb3JlKGV4aXN0aW5nLCByZXN1bHQpLFxuICAgIH07XG4gICAgXG4gICAgdGhpcy5jbGllbnRTdGF0ZXMuc2V0KGNsaWVudElkLCBuZXdTdGF0ZSk7XG4gIH1cbiAgXG4gIHByaXZhdGUgY2FsY3VsYXRlVHJ1c3RTY29yZShleGlzdGluZzogQ2xpZW50U3RhdGUgfCB1bmRlZmluZWQsIHJlc3VsdDogVmVyaWZpY2F0aW9uUmVzdWx0KTogbnVtYmVyIHtcbiAgICBsZXQgc2NvcmUgPSBleGlzdGluZz8udHJ1c3RTY29yZSA/PyA1MDtcbiAgICBcbiAgICBpZiAocmVzdWx0LnZhbGlkKSB7XG4gICAgICBzY29yZSA9IE1hdGgubWluKDEwMCwgc2NvcmUgKyA1KTtcbiAgICB9IGVsc2Uge1xuICAgICAgc2NvcmUgPSBNYXRoLm1heCgwLCBzY29yZSAtIDIwKTtcbiAgICB9XG4gICAgXG4gICAgaWYgKCFyZXN1bHQudmVyc2lvbk1hdGNoKSBzY29yZSA9IE1hdGgubWF4KDAsIHNjb3JlIC0gMzApO1xuICAgIGlmICghcmVzdWx0LnNoYWRlck91dHB1dHNNYXRjaCkgc2NvcmUgPSBNYXRoLm1heCgwLCBzY29yZSAtIDUwKTtcbiAgICBcbiAgICByZXR1cm4gc2NvcmU7XG4gIH1cbiAgXG4gIC8qKlxuICAgKiBHZXQgY2xpZW50IHN0YXRlXG4gICAqL1xuICBnZXRDbGllbnRTdGF0ZShjbGllbnRJZDogc3RyaW5nKTogQ2xpZW50U3RhdGUgfCB1bmRlZmluZWQge1xuICAgIHJldHVybiB0aGlzLmNsaWVudFN0YXRlcy5nZXQoY2xpZW50SWQpO1xuICB9XG4gIFxuICAvKipcbiAgICogSW5pdGlhbGl6ZSBuZXcgY2xpZW50XG4gICAqL1xuICBpbml0aWFsaXplQ2xpZW50KGNsaWVudElkOiBzdHJpbmcsIHZlcnNpb246IHN0cmluZyk6IENsaWVudFN0YXRlIHtcbiAgICBjb25zdCBzdGF0ZTogQ2xpZW50U3RhdGUgPSB7XG4gICAgICBjbGllbnRJZCxcbiAgICAgIHZlcnNpb24sXG4gICAgICBsYXN0Q2hhaW5IYXNoOiAnMCcucmVwZWF0KDY0KSxcbiAgICAgIGNoYWluTGVuZ3RoOiAwLFxuICAgICAgdG90YWxXb3JrQ29tcGxldGVkOiAwLFxuICAgICAgbGFzdFZlcmlmaWVkQXQ6IERhdGUubm93KCksXG4gICAgICB0cnVzdFNjb3JlOiA1MCxcbiAgICB9O1xuICAgIHRoaXMuY2xpZW50U3RhdGVzLnNldChjbGllbnRJZCwgc3RhdGUpO1xuICAgIHJldHVybiBzdGF0ZTtcbiAgfVxufVxuXG4vLyA9PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09XG4vLyBDbGllbnQtc2lkZSBEUk0gSGFuZGxlclxuLy8gPT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PVxuXG5leHBvcnQgY2xhc3MgRFJNQ2xpZW50IHtcbiAgcHJpdmF0ZSBzdGF0ZUNoYWluOiBTdGF0ZUNoYWluO1xuICBwcml2YXRlIGNsaWVudElkOiBzdHJpbmc7XG4gIFxuICBjb25zdHJ1Y3RvcihjbGllbnRJZDogc3RyaW5nLCB2ZXJzaW9uOiBzdHJpbmcpIHtcbiAgICB0aGlzLmNsaWVudElkID0gY2xpZW50SWQ7XG4gICAgdGhpcy5zdGF0ZUNoYWluID0gbmV3IFN0YXRlQ2hhaW4odmVyc2lvbik7XG4gIH1cbiAgXG4gIC8qKlxuICAgKiBTb2x2ZSBhIERSTSBjaGFsbGVuZ2VcbiAgICovXG4gIHNvbHZlQ2hhbGxlbmdlKGNoYWxsZW5nZTogRFJNQ2hhbGxlbmdlKTogRFJNUmVzcG9uc2Uge1xuICAgIGNvbnN0IG5ld0xpbmtzOiBTdGF0ZUNoYWluTGlua1tdID0gW107XG4gICAgXG4gICAgLy8gRXhlY3V0ZSBlYWNoIHJlcXVpcmVkIHNoYWRlciBhbmQgYWRkIHRvIGNoYWluXG4gICAgZm9yIChjb25zdCBzaGFkZXJJZCBvZiBjaGFsbGVuZ2UucmVxdWlyZWRTaGFkZXJzKSB7XG4gICAgICBjb25zdCBzZWVkID0gY2hhbGxlbmdlLmlucHV0U2VlZHMuZ2V0KHNoYWRlcklkKTtcbiAgICAgIGlmICghc2VlZCkgdGhyb3cgbmV3IEVycm9yKGBNaXNzaW5nIHNlZWQgZm9yIHNoYWRlciAke3NoYWRlcklkfWApO1xuICAgICAgXG4gICAgICAvLyBEbyB3b3JrIHByb29mIGZvciBlYWNoIGxpbmtcbiAgICAgIGNvbnN0IHdvcmtQcm9vZiA9IHRoaXMuY29tcHV0ZVdvcmtQcm9vZihzaGFkZXJJZCwgc2VlZCwgY2hhbGxlbmdlLmRpZmZpY3VsdHkpO1xuICAgICAgY29uc3QgbGluayA9IHRoaXMuc3RhdGVDaGFpbi5hZGRMaW5rKHNoYWRlcklkLCBzZWVkLCB3b3JrUHJvb2YpO1xuICAgICAgbmV3TGlua3MucHVzaChsaW5rKTtcbiAgICB9XG4gICAgXG4gICAgLy8gQ29tcHV0ZSBmaW5hbCB3b3JrIHJlc3VsdCB3aXRoIG5vbmNlXG4gICAgY29uc3QgeyByZXN1bHQsIG5vbmNlIH0gPSB0aGlzLmZpbmRWYWxpZE5vbmNlKFxuICAgICAgdGhpcy5zdGF0ZUNoYWluLmdldENoYWluSGFzaCgpLFxuICAgICAgY2hhbGxlbmdlLmRpZmZpY3VsdHlcbiAgICApO1xuICAgIFxuICAgIHJldHVybiB7XG4gICAgICBjaGFsbGVuZ2VJZDogY2hhbGxlbmdlLmNoYWxsZW5nZUlkLFxuICAgICAgY2xpZW50VmVyc2lvbjogdGhpcy5zdGF0ZUNoYWluLmV4cG9ydCgpLnZlcnNpb24sXG4gICAgICBzdGF0ZUNoYWluOiBuZXdMaW5rcyxcbiAgICAgIHdvcmtSZXN1bHQ6IHJlc3VsdCxcbiAgICAgIG5vbmNlLFxuICAgICAgY2xpZW50U2lnbmF0dXJlOiB0aGlzLnNpZ25SZXNwb25zZShyZXN1bHQpLFxuICAgIH07XG4gIH1cbiAgXG4gIHByaXZhdGUgY29tcHV0ZVdvcmtQcm9vZihzaGFkZXJJZDogc3RyaW5nLCBzZWVkOiBzdHJpbmcsIGRpZmZpY3VsdHk6IG51bWJlcik6IHN0cmluZyB7XG4gICAgLy8gU2ltcGxlIHdvcmsgcHJvb2YgLSBoYXNoIGl0ZXJhdGlvbnNcbiAgICBsZXQgcHJvb2YgPSBzZWVkO1xuICAgIGNvbnN0IGl0ZXJhdGlvbnMgPSBNYXRoLnBvdygxMCwgZGlmZmljdWx0eSk7XG4gICAgZm9yIChsZXQgaSA9IDA7IGkgPCBpdGVyYXRpb25zOyBpKyspIHtcbiAgICAgIHByb29mID0gY3JlYXRlSGFzaCgnc2hhMjU2JykudXBkYXRlKHByb29mICsgc2hhZGVySWQpLmRpZ2VzdCgnaGV4Jyk7XG4gICAgfVxuICAgIHJldHVybiBwcm9vZjtcbiAgfVxuICBcbiAgcHJpdmF0ZSBmaW5kVmFsaWROb25jZShkYXRhOiBzdHJpbmcsIGRpZmZpY3VsdHk6IG51bWJlcik6IHsgcmVzdWx0OiBzdHJpbmc7IG5vbmNlOiBudW1iZXIgfSB7XG4gICAgbGV0IG5vbmNlID0gMDtcbiAgICB3aGlsZSAodHJ1ZSkge1xuICAgICAgY29uc3QgcmVzdWx0ID0gY3JlYXRlSGFzaCgnc2hhMjU2JylcbiAgICAgICAgLnVwZGF0ZShkYXRhICsgbm9uY2UudG9TdHJpbmcoKSlcbiAgICAgICAgLmRpZ2VzdCgnaGV4Jyk7XG4gICAgICBcbiAgICAgIGxldCB2YWxpZCA9IHRydWU7XG4gICAgICBmb3IgKGxldCBpID0gMDsgaSA8IGRpZmZpY3VsdHk7IGkrKykge1xuICAgICAgICBpZiAocmVzdWx0W2ldICE9PSAnMCcpIHtcbiAgICAgICAgICB2YWxpZCA9IGZhbHNlO1xuICAgICAgICAgIGJyZWFrO1xuICAgICAgICB9XG4gICAgICB9XG4gICAgICBcbiAgICAgIGlmICh2YWxpZCkge1xuICAgICAgICByZXR1cm4geyByZXN1bHQsIG5vbmNlIH07XG4gICAgICB9XG4gICAgICBub25jZSsrO1xuICAgIH1cbiAgfVxuICBcbiAgcHJpdmF0ZSBzaWduUmVzcG9uc2UoZGF0YTogc3RyaW5nKTogc3RyaW5nIHtcbiAgICAvLyBJbiByZWFsIGltcGxlbWVudGF0aW9uLCB1c2UgcHJvcGVyIGNyeXB0b2dyYXBoaWMgc2lnbmluZ1xuICAgIHJldHVybiBjcmVhdGVIYXNoKCdzaGEyNTYnKVxuICAgICAgLnVwZGF0ZSh0aGlzLmNsaWVudElkICsgZGF0YSArIERhdGUubm93KCkpXG4gICAgICAuZGlnZXN0KCdoZXgnKTtcbiAgfVxuICBcbiAgLyoqXG4gICAqIEdldCBjdXJyZW50IGNoYWluIHN0YXRlXG4gICAqL1xuICBnZXRDaGFpblN0YXRlKCk6IHsgY2hhaW5IYXNoOiBzdHJpbmc7IGNoYWluTGVuZ3RoOiBudW1iZXIgfSB7XG4gICAgcmV0dXJuIHtcbiAgICAgIGNoYWluSGFzaDogdGhpcy5zdGF0ZUNoYWluLmdldENoYWluSGFzaCgpLFxuICAgICAgY2hhaW5MZW5ndGg6IHRoaXMuc3RhdGVDaGFpbi5nZXRMaW5rcygpLmxlbmd0aCxcbiAgICB9O1xuICB9XG59XG5cbi8vID09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT1cbi8vIFZlcnNpb24gTWFuaWZlc3QgQnVpbGRlclxuLy8gPT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PVxuXG5leHBvcnQgZnVuY3Rpb24gYnVpbGRWZXJzaW9uTWFuaWZlc3QodmVyc2lvbjogc3RyaW5nKTogVmVyc2lvbk1hbmlmZXN0IHtcbiAgY29uc3Qgc2hhZGVySGFzaGVzID0gbmV3IE1hcDxzdHJpbmcsIHN0cmluZz4oKTtcbiAgY29uc3QgZXhwZWN0ZWRPdXRwdXRzID0gbmV3IE1hcDxzdHJpbmcsIHN0cmluZz4oKTtcbiAgXG4gIGZvciAoY29uc3QgW3NoYWRlcklkLCBjb25maWddIG9mIE9iamVjdC5lbnRyaWVzKFNIQURFUl9SRUdJU1RSWSkpIHtcbiAgICAvLyBIYXNoIHRoZSBzaGFkZXIgXCJjb2RlXCIgKHNpbXVsYXRlZCBhcyBjb25maWcpXG4gICAgY29uc3Qgc2hhZGVySGFzaCA9IGNyZWF0ZUhhc2goJ3NoYTI1NicpXG4gICAgICAudXBkYXRlKEpTT04uc3RyaW5naWZ5KGNvbmZpZykpXG4gICAgICAuZGlnZXN0KCdoZXgnKTtcbiAgICBzaGFkZXJIYXNoZXMuc2V0KHNoYWRlcklkLCBzaGFkZXJIYXNoKTtcbiAgICBcbiAgICAvLyBDb21wdXRlIGV4cGVjdGVkIG91dHB1dCBmb3IgdGVzdCBzZWVkXG4gICAgY29uc3Qgb3V0cHV0ID0gZXhlY3V0ZVNoYWRlcihzaGFkZXJJZCwgY29uZmlnLnRlc3RTZWVkKTtcbiAgICBleHBlY3RlZE91dHB1dHMuc2V0KGNvbmZpZy50ZXN0U2VlZCwgb3V0cHV0KTtcbiAgfVxuICBcbiAgY29uc3QgbWFuaWZlc3REYXRhID0gSlNPTi5zdHJpbmdpZnkoe1xuICAgIHZlcnNpb24sXG4gICAgc2hhZGVySGFzaGVzOiBPYmplY3QuZnJvbUVudHJpZXMoc2hhZGVySGFzaGVzKSxcbiAgICBleHBlY3RlZE91dHB1dHM6IE9iamVjdC5mcm9tRW50cmllcyhleHBlY3RlZE91dHB1dHMpLFxuICAgIGJ1aWxkVGltZXN0YW1wOiBEYXRlLm5vdygpLFxuICB9KTtcbiAgXG4gIGNvbnN0IHNpZ25hdHVyZSA9IGNyZWF0ZUhhc2goJ3NoYTI1NicpXG4gICAgLnVwZGF0ZShtYW5pZmVzdERhdGEgKyAnZmFybWNyYWZ0X3NpZ25pbmdfa2V5JylcbiAgICAuZGlnZXN0KCdoZXgnKTtcbiAgXG4gIHJldHVybiB7XG4gICAgdmVyc2lvbixcbiAgICBzaGFkZXJIYXNoZXMsXG4gICAgZXhwZWN0ZWRPdXRwdXRzLFxuICAgIGJ1aWxkVGltZXN0YW1wOiBEYXRlLm5vdygpLFxuICAgIHNpZ25hdHVyZSxcbiAgfTtcbn1cblxuLy8gPT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PVxuLy8gRGVtbyAvIFRlc3QgRnVuY3Rpb25zXG4vLyA9PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09XG5cbmV4cG9ydCBmdW5jdGlvbiBydW5EUk1EZW1vKCk6IHZvaWQge1xuICBjb25zb2xlLmxvZygnPT09IEZhcm1DcmFmdCBEUk0gRGVtbyA9PT1cXG4nKTtcbiAgXG4gIC8vIEJ1aWxkIHZlcnNpb24gbWFuaWZlc3RcbiAgY29uc3QgbWFuaWZlc3QgPSBidWlsZFZlcnNpb25NYW5pZmVzdCgnMS4wLjAnKTtcbiAgY29uc29sZS5sb2coYFZlcnNpb24gbWFuaWZlc3QgY3JlYXRlZCBmb3IgdiR7bWFuaWZlc3QudmVyc2lvbn1gKTtcbiAgY29uc29sZS5sb2coYFNoYWRlcnMgcmVnaXN0ZXJlZDogJHttYW5pZmVzdC5zaGFkZXJIYXNoZXMuc2l6ZX1gKTtcbiAgXG4gIC8vIENyZWF0ZSB2ZXJpZmllciAoc2VydmVyLXNpZGUpXG4gIGNvbnN0IHZlcmlmaWVyID0gbmV3IERSTVZlcmlmaWVyKG1hbmlmZXN0KTtcbiAgY29uc3QgY2hhbGxlbmdlR2VuZXJhdG9yID0gbmV3IERSTUNoYWxsZW5nZUdlbmVyYXRvcihtYW5pZmVzdCk7XG4gIFxuICAvLyBDcmVhdGUgY2xpZW50XG4gIGNvbnN0IGNsaWVudElkID0gJ3BsYXllcl8xMjM0NSc7XG4gIGNvbnN0IGNsaWVudCA9IG5ldyBEUk1DbGllbnQoY2xpZW50SWQsICcxLjAuMCcpO1xuICBcbiAgLy8gSW5pdGlhbGl6ZSBjbGllbnQgb24gc2VydmVyXG4gIGxldCBjbGllbnRTdGF0ZSA9IHZlcmlmaWVyLmluaXRpYWxpemVDbGllbnQoY2xpZW50SWQsICcxLjAuMCcpO1xuICBjb25zb2xlLmxvZyhgXFxuQ2xpZW50ICR7Y2xpZW50SWR9IGluaXRpYWxpemVkIHdpdGggdHJ1c3Qgc2NvcmU6ICR7Y2xpZW50U3RhdGUudHJ1c3RTY29yZX1gKTtcbiAgXG4gIC8vIFJ1biBzZXZlcmFsIGNoYWxsZW5nZSByb3VuZHNcbiAgZm9yIChsZXQgcm91bmQgPSAxOyByb3VuZCA8PSAzOyByb3VuZCsrKSB7XG4gICAgY29uc29sZS5sb2coYFxcbi0tLSBSb3VuZCAke3JvdW5kfSAtLS1gKTtcbiAgICBcbiAgICAvLyBHZW5lcmF0ZSBjaGFsbGVuZ2VcbiAgICBjb25zdCBjaGFsbGVuZ2UgPSBjaGFsbGVuZ2VHZW5lcmF0b3IuZ2VuZXJhdGVDaGFsbGVuZ2UoY2xpZW50U3RhdGUsICdzaGFkZXJfdmVyaWZ5Jyk7XG4gICAgY29uc29sZS5sb2coYENoYWxsZW5nZSBnZW5lcmF0ZWQ6ICR7Y2hhbGxlbmdlLmNoYWxsZW5nZUlkLnN1YnN0cmluZygwLCAxNil9Li4uYCk7XG4gICAgY29uc29sZS5sb2coYFJlcXVpcmVkIHNoYWRlcnM6ICR7Y2hhbGxlbmdlLnJlcXVpcmVkU2hhZGVycy5qb2luKCcsICcpfWApO1xuICAgIGNvbnNvbGUubG9nKGBEaWZmaWN1bHR5OiAke2NoYWxsZW5nZS5kaWZmaWN1bHR5fWApO1xuICAgIFxuICAgIC8vIENsaWVudCBzb2x2ZXMgY2hhbGxlbmdlXG4gICAgY29uc3Qgc3RhcnRUaW1lID0gRGF0ZS5ub3coKTtcbiAgICBjb25zdCByZXNwb25zZSA9IGNsaWVudC5zb2x2ZUNoYWxsZW5nZShjaGFsbGVuZ2UpO1xuICAgIGNvbnN0IHNvbHZlVGltZSA9IERhdGUubm93KCkgLSBzdGFydFRpbWU7XG4gICAgY29uc29sZS5sb2coYENoYWxsZW5nZSBzb2x2ZWQgaW4gJHtzb2x2ZVRpbWV9bXNgKTtcbiAgICBjb25zb2xlLmxvZyhgV29yayBub25jZSBmb3VuZDogJHtyZXNwb25zZS5ub25jZX1gKTtcbiAgICBcbiAgICAvLyBTZXJ2ZXIgdmVyaWZpZXNcbiAgICBjb25zdCByZXN1bHQgPSB2ZXJpZmllci52ZXJpZnkoY2hhbGxlbmdlLCByZXNwb25zZSk7XG4gICAgY29uc29sZS5sb2coYFxcblZlcmlmaWNhdGlvbiByZXN1bHQ6YCk7XG4gICAgY29uc29sZS5sb2coYCAgVmFsaWQ6ICR7cmVzdWx0LnZhbGlkfWApO1xuICAgIGNvbnNvbGUubG9nKGAgIFZlcnNpb24gbWF0Y2g6ICR7cmVzdWx0LnZlcnNpb25NYXRjaH1gKTtcbiAgICBjb25zb2xlLmxvZyhgICBDaGFpbiBpbnRlZ3JpdHk6ICR7cmVzdWx0LmNoYWluSW50ZWdyaXR5fWApO1xuICAgIGNvbnNvbGUubG9nKGAgIFNoYWRlciBvdXRwdXRzIG1hdGNoOiAke3Jlc3VsdC5zaGFkZXJPdXRwdXRzTWF0Y2h9YCk7XG4gICAgY29uc29sZS5sb2coYCAgV29yayB2YWxpZDogJHtyZXN1bHQud29ya1ZhbGlkfWApO1xuICAgIFxuICAgIGlmIChyZXN1bHQuZXJyb3JzLmxlbmd0aCA+IDApIHtcbiAgICAgIGNvbnNvbGUubG9nKGAgIEVycm9yczogJHtyZXN1bHQuZXJyb3JzLmpvaW4oJywgJyl9YCk7XG4gICAgfVxuICAgIFxuICAgIC8vIFVwZGF0ZSBjbGllbnQgc3RhdGVcbiAgICB2ZXJpZmllci51cGRhdGVDbGllbnRTdGF0ZShjbGllbnRJZCwgcmVzcG9uc2UsIHJlc3VsdCk7XG4gICAgY2xpZW50U3RhdGUgPSB2ZXJpZmllci5nZXRDbGllbnRTdGF0ZShjbGllbnRJZCkhO1xuICAgIGNvbnNvbGUubG9nKGBcXG5VcGRhdGVkIHRydXN0IHNjb3JlOiAke2NsaWVudFN0YXRlLnRydXN0U2NvcmV9YCk7XG4gICAgY29uc29sZS5sb2coYENoYWluIGxlbmd0aDogJHtjbGllbnRTdGF0ZS5jaGFpbkxlbmd0aH1gKTtcbiAgfVxuICBcbiAgLy8gRGVtbyB0YW1wZXJlZCBjbGllbnRcbiAgY29uc29sZS5sb2coJ1xcblxcbj09PSBUYW1wZXJlZCBDbGllbnQgRGVtbyA9PT0nKTtcbiAgY29uc3QgdGFtcGVyZWRDbGllbnQgPSBuZXcgRFJNQ2xpZW50KCdoYWNrZXJfOTk5JywgJzEuMC4xJyk7IC8vIFdyb25nIHZlcnNpb25cbiAgY29uc3QgaGFja2VyU3RhdGUgPSB2ZXJpZmllci5pbml0aWFsaXplQ2xpZW50KCdoYWNrZXJfOTk5JywgJzEuMC4xJyk7XG4gIFxuICBjb25zdCBjaGFsbGVuZ2UgPSBjaGFsbGVuZ2VHZW5lcmF0b3IuZ2VuZXJhdGVDaGFsbGVuZ2UoaGFja2VyU3RhdGUsICdzaGFkZXJfdmVyaWZ5Jyk7XG4gIGNvbnN0IHJlc3BvbnNlID0gdGFtcGVyZWRDbGllbnQuc29sdmVDaGFsbGVuZ2UoY2hhbGxlbmdlKTtcbiAgY29uc3QgcmVzdWx0ID0gdmVyaWZpZXIudmVyaWZ5KGNoYWxsZW5nZSwgcmVzcG9uc2UpO1xuICBcbiAgY29uc29sZS5sb2coYFxcblRhbXBlcmVkIGNsaWVudCB2ZXJpZmljYXRpb246YCk7XG4gIGNvbnNvbGUubG9nKGAgIFZhbGlkOiAke3Jlc3VsdC52YWxpZH1gKTtcbiAgY29uc29sZS5sb2coYCAgVmVyc2lvbiBtYXRjaDogJHtyZXN1bHQudmVyc2lvbk1hdGNofWApO1xuICBjb25zb2xlLmxvZyhgICBFcnJvcnM6ICR7cmVzdWx0LmVycm9ycy5qb2luKCcsICcpfWApO1xuICBcbiAgY29uc29sZS5sb2coJ1xcbj09PSBEZW1vIENvbXBsZXRlID09PScpO1xufVxuXG4vLyBFeHBvcnQgZm9yIENMSVxuZXhwb3J0IHsgcnVuRFJNRGVtbyBhcyBkZW1vIH07XG4iXX0=