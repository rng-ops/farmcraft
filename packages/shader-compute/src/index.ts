/**
 * FarmCraft Shader Compute
 * GPU-accelerated computation using WebGPU shaders
 * 
 * This module provides WGSL shader programs that can be compiled and executed
 * on the GPU for proof-of-work challenges. These run in the background while
 * the game is being played, utilizing idle GPU cycles.
 */

// ============================================================================
// WGSL Shader Programs
// ============================================================================

/**
 * Hash computation shader for proof-of-work
 * Computes SHA-256 hashes in parallel on the GPU
 */
export const HASH_COMPUTE_SHADER = `
// SHA-256 constants
const K: array<u32, 64> = array<u32, 64>(
    0x428a2f98u, 0x71374491u, 0xb5c0fbcfu, 0xe9b5dba5u,
    0x3956c25bu, 0x59f111f1u, 0x923f82a4u, 0xab1c5ed5u,
    0xd807aa98u, 0x12835b01u, 0x243185beu, 0x550c7dc3u,
    0x72be5d74u, 0x80deb1feu, 0x9bdc06a7u, 0xc19bf174u,
    0xe49b69c1u, 0xefbe4786u, 0x0fc19dc6u, 0x240ca1ccu,
    0x2de92c6fu, 0x4a7484aau, 0x5cb0a9dcu, 0x76f988dau,
    0x983e5152u, 0xa831c66du, 0xb00327c8u, 0xbf597fc7u,
    0xc6e00bf3u, 0xd5a79147u, 0x06ca6351u, 0x14292967u,
    0x27b70a85u, 0x2e1b2138u, 0x4d2c6dfcu, 0x53380d13u,
    0x650a7354u, 0x766a0abbu, 0x81c2c92eu, 0x92722c85u,
    0xa2bfe8a1u, 0xa81a664bu, 0xc24b8b70u, 0xc76c51a3u,
    0xd192e819u, 0xd6990624u, 0xf40e3585u, 0x106aa070u,
    0x19a4c116u, 0x1e376c08u, 0x2748774cu, 0x34b0bcb5u,
    0x391c0cb3u, 0x4ed8aa4au, 0x5b9cca4fu, 0x682e6ff3u,
    0x748f82eeu, 0x78a5636fu, 0x84c87814u, 0x8cc70208u,
    0x90befffau, 0xa4506cebu, 0xbef9a3f7u, 0xc67178f2u
);

fn rotr(x: u32, n: u32) -> u32 {
    return (x >> n) | (x << (32u - n));
}

fn ch(x: u32, y: u32, z: u32) -> u32 {
    return (x & y) ^ (~x & z);
}

fn maj(x: u32, y: u32, z: u32) -> u32 {
    return (x & y) ^ (x & z) ^ (y & z);
}

fn sigma0(x: u32) -> u32 {
    return rotr(x, 2u) ^ rotr(x, 13u) ^ rotr(x, 22u);
}

fn sigma1(x: u32) -> u32 {
    return rotr(x, 6u) ^ rotr(x, 11u) ^ rotr(x, 25u);
}

fn gamma0(x: u32) -> u32 {
    return rotr(x, 7u) ^ rotr(x, 18u) ^ (x >> 3u);
}

fn gamma1(x: u32) -> u32 {
    return rotr(x, 17u) ^ rotr(x, 19u) ^ (x >> 10u);
}

struct Params {
    prefix_len: u32,
    target_zeros: u32,
    base_nonce: u32,
    padding: u32,
}

@group(0) @binding(0) var<uniform> params: Params;
@group(0) @binding(1) var<storage, read> prefix_data: array<u32>;
@group(0) @binding(2) var<storage, read_write> results: array<u32>;

@compute @workgroup_size(256)
fn main(@builtin(global_invocation_id) global_id: vec3<u32>) {
    let nonce = params.base_nonce + global_id.x;
    
    // Initialize hash state
    var h: array<u32, 8> = array<u32, 8>(
        0x6a09e667u, 0xbb67ae85u, 0x3c6ef372u, 0xa54ff53au,
        0x510e527fu, 0x9b05688cu, 0x1f83d9abu, 0x5be0cd19u
    );
    
    // Prepare message block (simplified - assumes small prefix)
    var w: array<u32, 64>;
    for (var i = 0u; i < 16u; i = i + 1u) {
        if (i < params.prefix_len / 4u) {
            w[i] = prefix_data[i];
        } else if (i == params.prefix_len / 4u) {
            w[i] = nonce;
        } else {
            w[i] = 0u;
        }
    }
    
    // Message schedule
    for (var i = 16u; i < 64u; i = i + 1u) {
        w[i] = gamma1(w[i - 2u]) + w[i - 7u] + gamma0(w[i - 15u]) + w[i - 16u];
    }
    
    // Compression
    var a = h[0]; var b = h[1]; var c = h[2]; var d = h[3];
    var e = h[4]; var f = h[5]; var g = h[6]; var hh = h[7];
    
    for (var i = 0u; i < 64u; i = i + 1u) {
        let t1 = hh + sigma1(e) + ch(e, f, g) + K[i] + w[i];
        let t2 = sigma0(a) + maj(a, b, c);
        hh = g; g = f; f = e; e = d + t1;
        d = c; c = b; b = a; a = t1 + t2;
    }
    
    let final_h0 = h[0] + a;
    
    // Check if hash meets target
    let leading_zeros = countLeadingZeros(final_h0);
    
    if (leading_zeros >= params.target_zeros) {
        results[0] = 1u;  // Found flag
        results[1] = nonce;
        results[2] = final_h0;
    }
}
`;

/**
 * Protein folding energy minimization shader
 * Performs parallel energy calculations for protein configurations
 */
export const FOLDING_ENERGY_SHADER = `
struct FoldingParams {
    sequence_length: u32,
    iteration: u32,
    temperature: f32,
    padding: u32,
}

@group(0) @binding(0) var<uniform> params: FoldingParams;
@group(0) @binding(1) var<storage, read> positions: array<vec3<f32>>;
@group(0) @binding(2) var<storage, read> hydrophobicity: array<f32>;
@group(0) @binding(3) var<storage, read_write> energies: array<f32>;
@group(0) @binding(4) var<storage, read_write> gradients: array<vec3<f32>>;

const BOND_LENGTH: f32 = 3.8;
const BOND_STIFFNESS: f32 = 100.0;
const LJ_EPSILON: f32 = 1.0;
const LJ_SIGMA: f32 = 3.4;

@compute @workgroup_size(64)
fn main(@builtin(global_invocation_id) global_id: vec3<u32>) {
    let idx = global_id.x;
    if (idx >= params.sequence_length) {
        return;
    }
    
    var energy: f32 = 0.0;
    var grad: vec3<f32> = vec3<f32>(0.0, 0.0, 0.0);
    let pos_i = positions[idx];
    
    // Bond energy with neighbors
    if (idx > 0u) {
        let pos_prev = positions[idx - 1u];
        let diff = pos_i - pos_prev;
        let dist = length(diff);
        let deviation = dist - BOND_LENGTH;
        energy += BOND_STIFFNESS * deviation * deviation;
        
        if (dist > 0.01) {
            grad += 2.0 * BOND_STIFFNESS * deviation * (diff / dist);
        }
    }
    
    if (idx < params.sequence_length - 1u) {
        let pos_next = positions[idx + 1u];
        let diff = pos_i - pos_next;
        let dist = length(diff);
        let deviation = dist - BOND_LENGTH;
        energy += BOND_STIFFNESS * deviation * deviation;
        
        if (dist > 0.01) {
            grad += 2.0 * BOND_STIFFNESS * deviation * (diff / dist);
        }
    }
    
    // Non-bonded interactions (LJ potential)
    for (var j = 0u; j < params.sequence_length; j = j + 1u) {
        if (j == idx || j == idx - 1u || j == idx + 1u) {
            continue;
        }
        
        let pos_j = positions[j];
        let diff = pos_i - pos_j;
        let dist = length(diff);
        
        if (dist < 0.1) {
            continue;
        }
        
        // Hydrophobic interaction modifier
        let hydro_mod = (hydrophobicity[idx] + hydrophobicity[j]) / 10.0;
        let eff_epsilon = LJ_EPSILON * (1.0 + hydro_mod);
        
        let sigma_r = LJ_SIGMA / dist;
        let sigma6 = pow(sigma_r, 6.0);
        let sigma12 = sigma6 * sigma6;
        
        energy += 4.0 * eff_epsilon * (sigma12 - sigma6);
        
        // Gradient
        let force_mag = 24.0 * eff_epsilon * (2.0 * sigma12 - sigma6) / dist;
        grad += force_mag * (diff / dist);
    }
    
    energies[idx] = energy;
    gradients[idx] = grad;
}
`;

/**
 * Entropy generation shader
 * Generates cryptographically useful random numbers using GPU parallelism
 */
export const ENTROPY_SHADER = `
struct EntropyParams {
    seed: u32,
    iterations: u32,
    output_offset: u32,
    padding: u32,
}

@group(0) @binding(0) var<uniform> params: EntropyParams;
@group(0) @binding(1) var<storage, read_write> output: array<u32>;

// xorshift128+ RNG
fn xorshift128plus(s0: ptr<function, u32>, s1: ptr<function, u32>) -> u32 {
    var x = *s0;
    let y = *s1;
    *s0 = y;
    x ^= x << 23u;
    x ^= x >> 17u;
    x ^= y;
    x ^= y >> 26u;
    *s1 = x;
    return x + y;
}

@compute @workgroup_size(256)
fn main(@builtin(global_invocation_id) global_id: vec3<u32>) {
    let idx = global_id.x;
    
    // Initialize state with seed and thread ID
    var s0 = params.seed ^ idx;
    var s1 = params.seed ^ (idx << 16u) ^ 0xDEADBEEFu;
    
    // Warm up the RNG
    for (var i = 0u; i < 16u; i = i + 1u) {
        let _ = xorshift128plus(&s0, &s1);
    }
    
    // Generate entropy
    var result = 0u;
    for (var i = 0u; i < params.iterations; i = i + 1u) {
        result ^= xorshift128plus(&s0, &s1);
    }
    
    output[params.output_offset + idx] = result;
}
`;

// ============================================================================
// Shader Manager Interface
// ============================================================================

export interface ShaderCapabilities {
  supported: boolean;
  vendor: string;
  device: string;
  maxWorkgroupSize: number;
  maxStorageBufferSize: number;
}

export interface ShaderComputeResult {
  success: boolean;
  output: ArrayBuffer;
  executionTimeMs: number;
  workgroupsDispatched: number;
}

/**
 * Abstract interface for shader compute operations
 * This would be implemented differently for:
 * - WebGPU in browsers
 * - Native GPU compute in Java/mod
 * - Fallback CPU implementation
 */
export interface ShaderCompute {
  initialize(): Promise<ShaderCapabilities>;
  
  compileShader(wgsl: string): Promise<boolean>;
  
  execute(
    shaderName: string,
    inputBuffers: Map<number, ArrayBuffer>,
    outputBufferSize: number,
    workgroupCount: [number, number, number]
  ): Promise<ShaderComputeResult>;
  
  dispose(): void;
}

/**
 * CPU fallback implementation of shader compute
 */
export class CPUShaderCompute implements ShaderCompute {
  async initialize(): Promise<ShaderCapabilities> {
    return {
      supported: true,
      vendor: 'CPU Fallback',
      device: 'N/A',
      maxWorkgroupSize: 256,
      maxStorageBufferSize: 128 * 1024 * 1024,
    };
  }

  async compileShader(_wgsl: string): Promise<boolean> {
    // CPU implementation doesn't need compilation
    return true;
  }

  async execute(
    shaderName: string,
    inputBuffers: Map<number, ArrayBuffer>,
    outputBufferSize: number,
    workgroupCount: [number, number, number]
  ): Promise<ShaderComputeResult> {
    const startTime = performance.now();
    const output = new ArrayBuffer(outputBufferSize);
    const outputView = new Uint32Array(output);

    const totalWorkgroups = workgroupCount[0] * workgroupCount[1] * workgroupCount[2];

    // Simulate compute work
    if (shaderName === 'entropy') {
      const paramsBuffer = inputBuffers.get(0);
      if (paramsBuffer) {
        const params = new Uint32Array(paramsBuffer);
        const seed = params[0];
        const iterations = params[1];

        for (let i = 0; i < outputView.length; i++) {
          let s0 = seed ^ i;
          let s1 = (seed ^ (i << 16)) >>> 0;

          for (let j = 0; j < iterations; j++) {
            const x = s0 ^ (s0 << 23);
            s0 = s1;
            s1 = (x ^ (x >>> 17) ^ s1 ^ (s1 >>> 26)) >>> 0;
          }

          outputView[i] = (s0 + s1) >>> 0;
        }
      }
    }

    return {
      success: true,
      output,
      executionTimeMs: performance.now() - startTime,
      workgroupsDispatched: totalWorkgroups,
    };
  }

  dispose(): void {
    // Nothing to dispose in CPU implementation
  }
}

// ============================================================================
// Exports
// ============================================================================

export const SHADERS = {
  HASH_COMPUTE: HASH_COMPUTE_SHADER,
  FOLDING_ENERGY: FOLDING_ENERGY_SHADER,
  ENTROPY: ENTROPY_SHADER,
};
