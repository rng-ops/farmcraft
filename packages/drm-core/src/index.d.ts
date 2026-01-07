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
    expectedOutputs: Map<string, string>;
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
/**
 * Known shader programs and their expected behaviors
 */
export declare const SHADER_REGISTRY: {
    hash_compute_v1: {
        version: string;
        testSeed: string;
        expectedTestOutput: string;
    };
    folding_energy_v1: {
        version: string;
        testSeed: string;
        expectedTestOutput: string;
    };
    entropy_v1: {
        version: string;
        testSeed: string;
        expectedTestOutput: string;
    };
    version_proof_v1: {
        version: string;
        testSeed: string;
        expectedTestOutput: string;
    };
};
/**
 * Simulates shader execution with deterministic outputs.
 * In the real mod, this would be actual GPU shader execution.
 */
export declare function executeShader(shaderId: string, inputSeed: string): string;
export declare class StateChain {
    private clientVersion;
    private links;
    private currentHash;
    constructor(clientVersion: string);
    /**
     * Add a new link to the chain by executing a shader
     */
    addLink(shaderId: string, inputSeed: string, workProof: string): StateChainLink;
    /**
     * Get the current chain hash (for embedding in next challenge)
     */
    getChainHash(): string;
    /**
     * Get all links for verification
     */
    getLinks(): StateChainLink[];
    /**
     * Export chain for transmission
     */
    export(): {
        links: StateChainLink[];
        chainHash: string;
        version: string;
    };
    /**
     * Import existing chain
     */
    static import(data: {
        links: StateChainLink[];
        chainHash: string;
        version: string;
    }): StateChain;
}
export declare class DRMChallengeGenerator {
    private activeManifest;
    constructor(manifest: VersionManifest);
    /**
     * Generate a challenge that requires specific shader execution
     */
    generateChallenge(clientState: ClientState, workType?: DRMChallenge['workType']): DRMChallenge;
    private selectRequiredShaders;
    /**
     * Get the expected outputs for a challenge (server-side)
     */
    getExpectedOutputs(challenge: DRMChallenge): Map<string, string>;
}
export declare class DRMVerifier {
    private manifest;
    private clientStates;
    constructor(manifest: VersionManifest);
    /**
     * Verify a DRM response from a client
     */
    verify(challenge: DRMChallenge, response: DRMResponse): VerificationResult;
    private verifyChainIntegrity;
    private verifyWorkProof;
    /**
     * Update client state after successful verification
     */
    updateClientState(clientId: string, response: DRMResponse, result: VerificationResult): void;
    private calculateTrustScore;
    /**
     * Get client state
     */
    getClientState(clientId: string): ClientState | undefined;
    /**
     * Initialize new client
     */
    initializeClient(clientId: string, version: string): ClientState;
}
export declare class DRMClient {
    private stateChain;
    private clientId;
    constructor(clientId: string, version: string);
    /**
     * Solve a DRM challenge
     */
    solveChallenge(challenge: DRMChallenge): DRMResponse;
    private computeWorkProof;
    private findValidNonce;
    private signResponse;
    /**
     * Get current chain state
     */
    getChainState(): {
        chainHash: string;
        chainLength: number;
    };
}
export declare function buildVersionManifest(version: string): VersionManifest;
export declare function runDRMDemo(): void;
export { runDRMDemo as demo };
