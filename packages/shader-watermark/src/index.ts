/**
 * @farmcraft/shader-watermark
 * 
 * Shader-based watermarking system for encoding game state into textures.
 * 
 * Features:
 * - Encode state into pixel data (steganography)
 * - GPU architecture fingerprinting
 * - Floating point precision detection
 * - ML-readable watermarks in screenshots
 * - Consensus verification through visual analysis
 */

import { createHash } from 'crypto';

// Type declarations for WebGL (when running in browser/game context)
declare global {
  interface WebGLRenderingContext {
    VERTEX_SHADER: number;
    FRAGMENT_SHADER: number;
    HIGH_FLOAT: number;
    VENDOR: number;
    RENDERER: number;
    MAX_TEXTURE_SIZE: number;
    getParameter(pname: number): any;
    getExtension(name: string): any;
    getSupportedExtensions(): string[] | null;
    getShaderPrecisionFormat(shaderType: number, precisionType: number): {
      rangeMin: number;
      rangeMax: number;
      precision: number;
    } | null;
  }
  interface WebGL2RenderingContext extends WebGLRenderingContext {}
}

// ============================================================================
// Types
// ============================================================================

export interface WatermarkData {
  // Core identifiers
  clientId: string;
  sessionId: string;
  timestamp: number;
  
  // Game state
  gameState: GameStateSnapshot;
  
  // Hardware fingerprint
  gpuFingerprint: GPUFingerprint;
  
  // Chain reference
  eventChainHash: string;
  lastEventSequence: number;
  
  // Verification
  signature: string;
}

export interface GameStateSnapshot {
  worldSeed: string;
  playerPosition: [number, number, number];
  playerInventoryHash: string;
  nearbyBlocksHash: string;
  timeOfDay: number;
  weather: string;
  activeFertilizers: string[];
  recentRecipesUsed: string[];
}

export interface GPUFingerprint {
  vendor: string;
  renderer: string;
  floatPrecision: FloatPrecisionInfo;
  maxTextureSize: number;
  extensions: string[];
  driverHash: string;
}

export interface FloatPrecisionInfo {
  vertexFloat: { rangeMin: number; rangeMax: number; precision: number };
  fragmentFloat: { rangeMin: number; rangeMax: number; precision: number };
  detectedQuirks: string[];
}

export interface EncodedWatermark {
  // Encoded as RGBA pixel data
  pixelData: Uint8ClampedArray;
  width: number;
  height: number;
  
  // Metadata for extraction
  version: number;
  encoding: 'lsb' | 'dct' | 'hybrid';
  checksum: string;
}

export interface DecodedWatermark {
  success: boolean;
  data?: WatermarkData;
  confidence: number;
  extractionMethod: string;
  errors?: string[];
}

// ============================================================================
// Watermark Encoder
// ============================================================================

export class WatermarkEncoder {
  private readonly VERSION = 1;
  private readonly MAGIC_BYTES = [0xFA, 0xC0, 0xDE]; // "FACADE" marker
  
  /**
   * Encode watermark data into pixel array using LSB steganography
   */
  encode(data: WatermarkData, width: number, height: number): EncodedWatermark {
    const jsonData = JSON.stringify(data);
    const compressedData = this.compress(jsonData);
    const binaryData = this.toBinary(compressedData);
    
    // Create RGBA pixel array
    const pixelCount = width * height;
    const pixelData = new Uint8ClampedArray(pixelCount * 4);
    
    // Initialize with noise pattern (looks natural)
    for (let i = 0; i < pixelData.length; i++) {
      pixelData[i] = Math.floor(Math.random() * 256);
    }
    
    // Embed magic bytes at known positions
    this.embedMagicBytes(pixelData);
    
    // Embed data using LSB (Least Significant Bit)
    let bitIndex = 0;
    const dataStartOffset = 100; // Skip first 100 pixels for magic bytes
    
    for (let pixelIdx = dataStartOffset; pixelIdx < pixelCount && bitIndex < binaryData.length; pixelIdx++) {
      const baseIdx = pixelIdx * 4;
      
      // Embed 2 bits per channel (R, G, B), skip Alpha for visibility
      for (let channel = 0; channel < 3 && bitIndex < binaryData.length; channel++) {
        const bit1 = binaryData[bitIndex++] || 0;
        const bit2 = bitIndex < binaryData.length ? binaryData[bitIndex++] : 0;
        
        // Clear last 2 bits and set new ones
        pixelData[baseIdx + channel] = (pixelData[baseIdx + channel] & 0xFC) | (bit1 << 1) | bit2;
      }
    }
    
    // Compute checksum
    const checksum = createHash('sha256').update(Buffer.from(pixelData)).digest('hex').substring(0, 16);
    
    return {
      pixelData,
      width,
      height,
      version: this.VERSION,
      encoding: 'lsb',
      checksum,
    };
  }
  
  /**
   * Embed watermark into existing texture (overlay mode)
   */
  embedInTexture(
    existingPixels: Uint8ClampedArray,
    width: number,
    height: number,
    data: WatermarkData
  ): EncodedWatermark {
    const jsonData = JSON.stringify(data);
    const binaryData = this.toBinary(this.compress(jsonData));
    
    // Clone existing pixels
    const pixelData = new Uint8ClampedArray(existingPixels);
    
    // Embed magic bytes
    this.embedMagicBytes(pixelData);
    
    // Use DCT-like embedding for better invisibility on real textures
    let bitIndex = 0;
    const step = Math.floor((width * height) / binaryData.length);
    
    for (let i = 0; i < binaryData.length; i++) {
      const pixelIdx = 100 + (i * step);
      if (pixelIdx >= width * height) break;
      
      const baseIdx = pixelIdx * 4;
      const bit = binaryData[i];
      
      // Modify blue channel (least perceptible to human eye)
      pixelData[baseIdx + 2] = (pixelData[baseIdx + 2] & 0xFE) | bit;
    }
    
    const checksum = createHash('sha256').update(Buffer.from(pixelData)).digest('hex').substring(0, 16);
    
    return {
      pixelData,
      width,
      height,
      version: this.VERSION,
      encoding: 'hybrid',
      checksum,
    };
  }
  
  private embedMagicBytes(pixelData: Uint8ClampedArray): void {
    // Embed at fixed positions for easy detection
    const positions = [0, 10, 20, 30, 40, 50, 60, 70, 80, 90];
    
    positions.forEach((pos, idx) => {
      const magicByte = this.MAGIC_BYTES[idx % this.MAGIC_BYTES.length];
      const baseIdx = pos * 4;
      
      // XOR with position for uniqueness
      pixelData[baseIdx] = magicByte ^ (idx * 17);
      pixelData[baseIdx + 1] = (magicByte >> 4) | ((idx & 0x0F) << 4);
    });
  }
  
  private compress(data: string): Uint8Array {
    // Simple RLE-like compression
    const bytes = new TextEncoder().encode(data);
    return bytes; // In production, use proper compression
  }
  
  private toBinary(data: Uint8Array): number[] {
    const bits: number[] = [];
    
    // Add length header (32 bits)
    const length = data.length;
    for (let i = 31; i >= 0; i--) {
      bits.push((length >> i) & 1);
    }
    
    // Add data bits
    for (const byte of data) {
      for (let i = 7; i >= 0; i--) {
        bits.push((byte >> i) & 1);
      }
    }
    
    return bits;
  }
}

// ============================================================================
// Watermark Decoder
// ============================================================================

export class WatermarkDecoder {
  private readonly MAGIC_BYTES = [0xFA, 0xC0, 0xDE];
  
  /**
   * Attempt to decode watermark from pixel data
   */
  decode(pixelData: Uint8ClampedArray, width: number, height: number): DecodedWatermark {
    try {
      // Verify magic bytes
      if (!this.verifyMagicBytes(pixelData)) {
        return {
          success: false,
          confidence: 0,
          extractionMethod: 'none',
          errors: ['Magic bytes not found - not a watermarked image'],
        };
      }
      
      // Extract binary data
      const binaryData = this.extractBinary(pixelData, width, height);
      
      // Parse length header
      let length = 0;
      for (let i = 0; i < 32; i++) {
        length = (length << 1) | binaryData[i];
      }
      
      if (length <= 0 || length > 1000000) {
        return {
          success: false,
          confidence: 0.2,
          extractionMethod: 'lsb',
          errors: ['Invalid data length'],
        };
      }
      
      // Extract bytes
      const bytes = new Uint8Array(length);
      for (let i = 0; i < length; i++) {
        let byte = 0;
        for (let j = 0; j < 8; j++) {
          const bitIdx = 32 + (i * 8) + j;
          byte = (byte << 1) | (binaryData[bitIdx] || 0);
        }
        bytes[i] = byte;
      }
      
      // Decompress and parse
      const jsonStr = new TextDecoder().decode(bytes);
      const data = JSON.parse(jsonStr) as WatermarkData;
      
      // Calculate confidence based on data validity
      const confidence = this.calculateConfidence(data);
      
      return {
        success: true,
        data,
        confidence,
        extractionMethod: 'lsb',
      };
    } catch (error) {
      return {
        success: false,
        confidence: 0,
        extractionMethod: 'lsb',
        errors: [(error as Error).message],
      };
    }
  }
  
  /**
   * Detect if image likely contains watermark (fast check)
   */
  detectWatermark(pixelData: Uint8ClampedArray): { likely: boolean; confidence: number } {
    const hasMagic = this.verifyMagicBytes(pixelData);
    
    if (!hasMagic) {
      return { likely: false, confidence: 0 };
    }
    
    // Check LSB distribution (watermarked images have specific patterns)
    let lsbSum = 0;
    const sampleSize = Math.min(10000, pixelData.length / 4);
    
    for (let i = 0; i < sampleSize; i++) {
      const idx = i * 4;
      lsbSum += (pixelData[idx] & 1) + (pixelData[idx + 1] & 1) + (pixelData[idx + 2] & 1);
    }
    
    const lsbRatio = lsbSum / (sampleSize * 3);
    const deviation = Math.abs(lsbRatio - 0.5);
    
    // Watermarked images have slightly skewed LSB distribution
    const confidence = hasMagic ? 0.8 + (deviation * 0.4) : deviation * 0.5;
    
    return {
      likely: hasMagic || confidence > 0.6,
      confidence: Math.min(1, confidence),
    };
  }
  
  private verifyMagicBytes(pixelData: Uint8ClampedArray): boolean {
    const positions = [0, 10, 20];
    let matches = 0;
    
    positions.forEach((pos, idx) => {
      const baseIdx = pos * 4;
      const expected = this.MAGIC_BYTES[idx] ^ (idx * 17);
      
      if (pixelData[baseIdx] === expected) {
        matches++;
      }
    });
    
    return matches >= 2; // Allow some tolerance
  }
  
  private extractBinary(pixelData: Uint8ClampedArray, width: number, height: number): number[] {
    const bits: number[] = [];
    const pixelCount = width * height;
    const dataStartOffset = 100;
    
    for (let pixelIdx = dataStartOffset; pixelIdx < pixelCount; pixelIdx++) {
      const baseIdx = pixelIdx * 4;
      
      for (let channel = 0; channel < 3; channel++) {
        const value = pixelData[baseIdx + channel];
        bits.push((value >> 1) & 1);
        bits.push(value & 1);
      }
    }
    
    return bits;
  }
  
  private calculateConfidence(data: WatermarkData): number {
    let score = 0;
    
    if (data.clientId && data.clientId.length > 10) score += 0.2;
    if (data.timestamp && data.timestamp > 1000000000000) score += 0.1;
    if (data.gameState) score += 0.3;
    if (data.gpuFingerprint) score += 0.2;
    if (data.signature && data.signature.length === 64) score += 0.2;
    
    return score;
  }
}

// ============================================================================
// GPU Fingerprinting
// ============================================================================

export class GPUFingerprinter {
  /**
   * Generate GPU fingerprint from WebGL context info
   * This would run in browser/game context
   */
  static generateFingerprint(gl: WebGLRenderingContext | WebGL2RenderingContext): GPUFingerprint {
    const debugInfo = gl.getExtension('WEBGL_debug_renderer_info');
    
    const vendor = debugInfo 
      ? gl.getParameter(debugInfo.UNMASKED_VENDOR_WEBGL) 
      : gl.getParameter(gl.VENDOR);
    
    const renderer = debugInfo
      ? gl.getParameter(debugInfo.UNMASKED_RENDERER_WEBGL)
      : gl.getParameter(gl.RENDERER);
    
    // Get float precision
    const floatPrecision = this.getFloatPrecision(gl);
    
    // Get extensions
    const extensions = gl.getSupportedExtensions() || [];
    
    // Create driver hash
    const driverData = `${vendor}|${renderer}|${JSON.stringify(floatPrecision)}`;
    const driverHash = createHash('sha256').update(driverData).digest('hex');
    
    return {
      vendor,
      renderer,
      floatPrecision,
      maxTextureSize: gl.getParameter(gl.MAX_TEXTURE_SIZE),
      extensions: extensions.slice(0, 20), // Limit for size
      driverHash,
    };
  }
  
  private static getFloatPrecision(gl: WebGLRenderingContext | WebGL2RenderingContext): FloatPrecisionInfo {
    const getRange = (shaderType: number, precisionType: number) => {
      const format = gl.getShaderPrecisionFormat(shaderType, precisionType);
      return format ? {
        rangeMin: format.rangeMin,
        rangeMax: format.rangeMax,
        precision: format.precision,
      } : { rangeMin: 0, rangeMax: 0, precision: 0 };
    };
    
    const vertexFloat = getRange(gl.VERTEX_SHADER, gl.HIGH_FLOAT);
    const fragmentFloat = getRange(gl.FRAGMENT_SHADER, gl.HIGH_FLOAT);
    
    // Detect quirks through computation
    const quirks = this.detectFloatQuirks(gl);
    
    return {
      vertexFloat,
      fragmentFloat,
      detectedQuirks: quirks,
    };
  }
  
  private static detectFloatQuirks(gl: WebGLRenderingContext | WebGL2RenderingContext): string[] {
    const quirks: string[] = [];
    
    // These would be detected by running specific shaders
    // and checking for known GPU-specific behaviors
    
    return quirks;
  }
}

// ============================================================================
// Consensus Verification
// ============================================================================

export interface ScreenshotComparison {
  match: boolean;
  similarity: number;
  stateMatch: boolean;
  gpuMatch: boolean;
  timestampDelta: number;
  discrepancies: string[];
}

export class ConsensusVerifier {
  /**
   * Compare two screenshots for consensus
   */
  static compare(
    screenshot1: DecodedWatermark,
    screenshot2: DecodedWatermark
  ): ScreenshotComparison {
    if (!screenshot1.success || !screenshot2.success) {
      return {
        match: false,
        similarity: 0,
        stateMatch: false,
        gpuMatch: false,
        timestampDelta: 0,
        discrepancies: ['One or both screenshots failed to decode'],
      };
    }
    
    const data1 = screenshot1.data!;
    const data2 = screenshot2.data!;
    const discrepancies: string[] = [];
    
    // Compare game state
    const stateMatch = this.compareGameState(data1.gameState, data2.gameState, discrepancies);
    
    // Compare GPU fingerprints (should differ between users)
    const gpuMatch = data1.gpuFingerprint.driverHash === data2.gpuFingerprint.driverHash;
    
    // Check event chain references
    const chainMatch = data1.eventChainHash === data2.eventChainHash;
    if (!chainMatch) {
      discrepancies.push('Event chain hash mismatch');
    }
    
    // Timestamp delta
    const timestampDelta = Math.abs(data1.timestamp - data2.timestamp);
    if (timestampDelta > 60000) { // More than 1 minute
      discrepancies.push(`Timestamps differ by ${timestampDelta}ms`);
    }
    
    // Calculate overall similarity
    let similarity = 0;
    if (stateMatch) similarity += 0.5;
    if (chainMatch) similarity += 0.3;
    if (timestampDelta < 10000) similarity += 0.2;
    
    return {
      match: stateMatch && chainMatch && timestampDelta < 60000,
      similarity,
      stateMatch,
      gpuMatch,
      timestampDelta,
      discrepancies,
    };
  }
  
  private static compareGameState(
    state1: GameStateSnapshot,
    state2: GameStateSnapshot,
    discrepancies: string[]
  ): boolean {
    let matches = 0;
    let total = 0;
    
    // World seed should match
    total++;
    if (state1.worldSeed === state2.worldSeed) {
      matches++;
    } else {
      discrepancies.push('World seed mismatch');
    }
    
    // Time of day should be close
    total++;
    if (Math.abs(state1.timeOfDay - state2.timeOfDay) < 1000) {
      matches++;
    } else {
      discrepancies.push('Time of day differs significantly');
    }
    
    // Weather should match
    total++;
    if (state1.weather === state2.weather) {
      matches++;
    } else {
      discrepancies.push('Weather mismatch');
    }
    
    // Player positions can differ (they're different players)
    // But nearby blocks hash should be similar if same location
    
    return matches >= total * 0.7;
  }
  
  /**
   * Verify a set of screenshots reach consensus
   */
  static verifyConsensus(
    screenshots: DecodedWatermark[],
    threshold = 0.66
  ): { consensus: boolean; agreementRatio: number; clusters: number[][] } {
    if (screenshots.length < 2) {
      return { consensus: true, agreementRatio: 1, clusters: [[0]] };
    }
    
    // Build agreement matrix
    const agreements: boolean[][] = [];
    for (let i = 0; i < screenshots.length; i++) {
      agreements[i] = [];
      for (let j = 0; j < screenshots.length; j++) {
        if (i === j) {
          agreements[i][j] = true;
        } else if (j < i) {
          agreements[i][j] = agreements[j][i];
        } else {
          const comparison = this.compare(screenshots[i], screenshots[j]);
          agreements[i][j] = comparison.match;
        }
      }
    }
    
    // Find largest agreeing cluster
    const clusters = this.findClusters(agreements);
    const largestCluster = clusters.reduce((a, b) => a.length > b.length ? a : b, []);
    
    const agreementRatio = largestCluster.length / screenshots.length;
    
    return {
      consensus: agreementRatio >= threshold,
      agreementRatio,
      clusters,
    };
  }
  
  private static findClusters(agreements: boolean[][]): number[][] {
    const n = agreements.length;
    const visited = new Set<number>();
    const clusters: number[][] = [];
    
    for (let i = 0; i < n; i++) {
      if (visited.has(i)) continue;
      
      const cluster: number[] = [i];
      visited.add(i);
      
      for (let j = i + 1; j < n; j++) {
        if (visited.has(j)) continue;
        
        // Check if j agrees with all members of cluster
        let agreesWithAll = true;
        for (const member of cluster) {
          if (!agreements[member][j]) {
            agreesWithAll = false;
            break;
          }
        }
        
        if (agreesWithAll) {
          cluster.push(j);
          visited.add(j);
        }
      }
      
      clusters.push(cluster);
    }
    
    return clusters;
  }
}

// ============================================================================
// ML Feature Extraction
// ============================================================================

export interface MLFeatures {
  // Structured data for ML models
  watermarkPresent: boolean;
  confidence: number;
  
  // Extracted features
  gpuVendor: string;
  gpuRenderer: string;
  floatPrecisionHash: string;
  
  // Game state features
  worldSeedHash: string;
  playerPositionBucket: [number, number, number];
  timeOfDayNormalized: number;
  weatherCategory: number;
  
  // Chain verification
  eventSequence: number;
  chainHashPrefix: string;
  
  // Image analysis features (for CV models)
  lsbEntropy: number;
  colorDistribution: number[];
  edgeComplexity: number;
}

export class MLFeatureExtractor {
  /**
   * Extract features suitable for ML analysis
   */
  static extract(
    pixelData: Uint8ClampedArray,
    width: number,
    height: number,
    decoded?: DecodedWatermark
  ): MLFeatures {
    const features: MLFeatures = {
      watermarkPresent: false,
      confidence: 0,
      gpuVendor: '',
      gpuRenderer: '',
      floatPrecisionHash: '',
      worldSeedHash: '',
      playerPositionBucket: [0, 0, 0],
      timeOfDayNormalized: 0,
      weatherCategory: 0,
      eventSequence: 0,
      chainHashPrefix: '',
      lsbEntropy: this.calculateLSBEntropy(pixelData),
      colorDistribution: this.calculateColorDistribution(pixelData),
      edgeComplexity: this.calculateEdgeComplexity(pixelData, width, height),
    };
    
    if (decoded?.success && decoded.data) {
      const data = decoded.data;
      
      features.watermarkPresent = true;
      features.confidence = decoded.confidence;
      features.gpuVendor = data.gpuFingerprint.vendor;
      features.gpuRenderer = data.gpuFingerprint.renderer;
      features.floatPrecisionHash = createHash('sha256')
        .update(JSON.stringify(data.gpuFingerprint.floatPrecision))
        .digest('hex')
        .substring(0, 16);
      
      features.worldSeedHash = createHash('sha256')
        .update(data.gameState.worldSeed)
        .digest('hex')
        .substring(0, 16);
      
      // Bucket position to 16-chunk regions
      features.playerPositionBucket = data.gameState.playerPosition.map(
        p => Math.floor(p / 256)
      ) as [number, number, number];
      
      features.timeOfDayNormalized = data.gameState.timeOfDay / 24000;
      features.weatherCategory = ['clear', 'rain', 'thunder'].indexOf(data.gameState.weather);
      
      features.eventSequence = data.lastEventSequence;
      features.chainHashPrefix = data.eventChainHash.substring(0, 8);
    }
    
    return features;
  }
  
  private static calculateLSBEntropy(pixelData: Uint8ClampedArray): number {
    const counts = [0, 0];
    
    for (let i = 0; i < pixelData.length; i += 4) {
      counts[pixelData[i] & 1]++;
      counts[pixelData[i + 1] & 1]++;
      counts[pixelData[i + 2] & 1]++;
    }
    
    const total = counts[0] + counts[1];
    const p0 = counts[0] / total;
    const p1 = counts[1] / total;
    
    // Shannon entropy
    return -(p0 * Math.log2(p0 || 1) + p1 * Math.log2(p1 || 1));
  }
  
  private static calculateColorDistribution(pixelData: Uint8ClampedArray): number[] {
    // 8-bin histogram for each channel
    const bins = new Array(24).fill(0);
    const pixelCount = pixelData.length / 4;
    
    for (let i = 0; i < pixelData.length; i += 4) {
      bins[Math.floor(pixelData[i] / 32)]++; // R
      bins[8 + Math.floor(pixelData[i + 1] / 32)]++; // G
      bins[16 + Math.floor(pixelData[i + 2] / 32)]++; // B
    }
    
    // Normalize
    return bins.map(b => b / pixelCount);
  }
  
  private static calculateEdgeComplexity(
    pixelData: Uint8ClampedArray,
    width: number,
    height: number
  ): number {
    let edgeCount = 0;
    const threshold = 30;
    
    for (let y = 1; y < height - 1; y++) {
      for (let x = 1; x < width - 1; x++) {
        const idx = (y * width + x) * 4;
        const idxRight = idx + 4;
        const idxDown = idx + width * 4;
        
        // Simple gradient magnitude
        const gx = Math.abs(pixelData[idx] - pixelData[idxRight]);
        const gy = Math.abs(pixelData[idx] - pixelData[idxDown]);
        
        if (gx + gy > threshold) {
          edgeCount++;
        }
      }
    }
    
    return edgeCount / ((width - 2) * (height - 2));
  }
}
