/**
 * Demo: Shader Watermarking System
 * 
 * Demonstrates:
 * 1. Encoding game state into textures (LSB steganography)
 * 2. GPU fingerprinting
 * 3. Watermark extraction
 * 4. Consensus verification between screenshots
 * 5. ML feature extraction
 */

import {
  WatermarkEncoder,
  WatermarkDecoder,
  ConsensusVerifier,
  MLFeatureExtractor,
  GameStateSnapshot,
  WatermarkData,
  GPUFingerprint,
  FloatPrecisionInfo,
} from './index';

function createMockPixelData(width: number, height: number): Uint8ClampedArray {
  // Create synthetic image data (simulate a Minecraft screenshot)
  const data = new Uint8ClampedArray(width * height * 4);
  
  for (let y = 0; y < height; y++) {
    for (let x = 0; x < width; x++) {
      const i = (y * width + x) * 4;
      // Create gradient with some noise (like a game scene)
      const noise = Math.random() * 30;
      data[i] = Math.min(255, Math.floor((x / width) * 150 + noise)); // R
      data[i + 1] = Math.min(255, Math.floor((y / height) * 150 + noise)); // G
      data[i + 2] = Math.min(255, Math.floor(100 + noise)); // B
      data[i + 3] = 255; // A
    }
  }
  
  return data;
}

async function runDemo() {
  console.log(`
  ██╗    ██╗ █████╗ ████████╗███████╗██████╗ ███╗   ███╗ █████╗ ██████╗ ██╗  ██╗
  ██║    ██║██╔══██╗╚══██╔══╝██╔════╝██╔══██╗████╗ ████║██╔══██╗██╔══██╗██║ ██╔╝
  ██║ █╗ ██║███████║   ██║   █████╗  ██████╔╝██╔████╔██║███████║██████╔╝█████╔╝ 
  ██║███╗██║██╔══██║   ██║   ██╔══╝  ██╔══██╗██║╚██╔╝██║██╔══██║██╔══██╗██╔═██╗ 
  ╚███╔███╔╝██║  ██║   ██║   ███████╗██║  ██║██║ ╚═╝ ██║██║  ██║██║  ██║██║  ██╗
   ╚══╝╚══╝ ╚═╝  ╚═╝   ╚═╝   ╚══════╝╚═╝  ╚═╝╚═╝     ╚═╝╚═╝  ╚═╝╚═╝  ╚═╝╚═╝  ╚═╝
                                                                                 
               Shader-Based Steganography Demo
  `);

  console.log('═══════════════════════════════════════════════════════════════');
  console.log('  Creating Game State Snapshot');
  console.log('═══════════════════════════════════════════════════════════════\n');

  // Create sample game state
  const gameState: GameStateSnapshot = {
    worldSeed: '12345678',
    playerPosition: [100.5, 64.0, -200.25],
    playerInventoryHash: 'inv_abc123',
    nearbyBlocksHash: 'blocks_def456',
    timeOfDay: 6000,
    weather: 'clear',
    activeFertilizers: ['tuff_fertilizer', 'calcite_fertilizer'],
    recentRecipesUsed: ['power_carrot', 'golden_beetroot'],
  };

  console.log('Game State:');
  console.log(`  World Seed: ${gameState.worldSeed}`);
  console.log(`  Player Position: (${gameState.playerPosition.join(', ')})`);
  console.log(`  Time of Day: ${gameState.timeOfDay}`);
  console.log(`  Weather: ${gameState.weather}`);
  console.log(`  Active Fertilizers: ${gameState.activeFertilizers.join(', ')}`);

  console.log('\n═══════════════════════════════════════════════════════════════');
  console.log('  GPU Fingerprinting');
  console.log('═══════════════════════════════════════════════════════════════\n');

  // Note: In Node.js we don't have WebGL, so we'll use mock data
  console.log('GPU Fingerprint (simulated for demo):');
  
  const floatPrecision: FloatPrecisionInfo = {
    vertexFloat: { rangeMin: 127, rangeMax: 127, precision: 23 },
    fragmentFloat: { rangeMin: 127, rangeMax: 127, precision: 23 },
    detectedQuirks: ['denorm_flush', 'precision_loss_at_large_values'],
  };
  
  const gpuFingerprint: GPUFingerprint = {
    vendor: 'NVIDIA Corporation',
    renderer: 'NVIDIA GeForce RTX 3080',
    floatPrecision,
    maxTextureSize: 16384,
    extensions: ['OES_texture_float', 'WEBGL_depth_texture', 'EXT_shader_texture_lod'],
    driverHash: 'gpu_abc123def456789',
  };
  
  console.log(`  Vendor: ${gpuFingerprint.vendor}`);
  console.log(`  Renderer: ${gpuFingerprint.renderer}`);
  console.log(`  Float Precision (fragment): ${floatPrecision.fragmentFloat.precision} bits`);
  console.log(`  Max Texture Size: ${gpuFingerprint.maxTextureSize}`);
  console.log(`  Detected Quirks: ${floatPrecision.detectedQuirks.join(', ')}`);
  console.log(`  Driver Hash: ${gpuFingerprint.driverHash}`);

  console.log('\n═══════════════════════════════════════════════════════════════');
  console.log('  Encoding Watermark into Texture');
  console.log('═══════════════════════════════════════════════════════════════\n');

  const encoder = new WatermarkEncoder();
  const decoder = new WatermarkDecoder();

  // Create watermark data
  const watermarkData: WatermarkData = {
    clientId: 'client_demo_12345678901234567890',
    sessionId: 'session_demo_abc123',
    timestamp: Date.now(),
    gameState,
    gpuFingerprint,
    eventChainHash: 'chain_abcdef123456789abcdef123456789',
    lastEventSequence: 42,
    signature: 'sig_0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef',
  };

  // Create mock image
  const width = 256;
  const height = 256;
  
  console.log(`Original image: ${width}x${height} pixels`);
  
  // Encode watermark (creates new pixel data with embedded watermark)
  const encoded = encoder.encode(watermarkData, width, height);
  
  console.log('\nEncoding result:');
  console.log(`  Encoding method: ${encoded.encoding}`);
  console.log(`  Version: ${encoded.version}`);
  console.log(`  Checksum: ${encoded.checksum}`);
  console.log(`  Pixel data size: ${encoded.pixelData.length} bytes`);
  
  // Test embedding into existing texture
  const existingPixels = createMockPixelData(width, height);
  const embedded = encoder.embedInTexture(existingPixels, width, height, watermarkData);
  
  console.log('\nEmbed into existing texture:');
  console.log(`  Encoding method: ${embedded.encoding}`);
  console.log(`  Checksum: ${embedded.checksum}`);

  console.log('\n═══════════════════════════════════════════════════════════════');
  console.log('  Decoding Watermark from Texture');
  console.log('═══════════════════════════════════════════════════════════════\n');

  // First detect if watermark exists
  const detected = decoder.detectWatermark(encoded.pixelData);
  console.log(`Watermark detected: ${detected.likely ? '✓' : '✗'}`);
  console.log(`Detection confidence: ${(detected.confidence * 100).toFixed(1)}%`);
  
  if (detected.likely) {
    // Decode the watermark
    const decoded = decoder.decode(encoded.pixelData, width, height);
    
    console.log('\nDecoded data:');
    console.log(`  Success: ${decoded.success ? '✓' : '✗'}`);
    console.log(`  Confidence: ${(decoded.confidence * 100).toFixed(1)}%`);
    console.log(`  Extraction method: ${decoded.extractionMethod}`);
    
    if (decoded.success && decoded.data) {
      console.log(`  Client ID: ${decoded.data.clientId}`);
      console.log(`  Session ID: ${decoded.data.sessionId}`);
      console.log(`  World Seed: ${decoded.data.gameState.worldSeed}`);
      console.log(`  Player Position: (${decoded.data.gameState.playerPosition.join(', ')})`);
      console.log(`  Event Sequence: ${decoded.data.lastEventSequence}`);
      console.log(`  GPU Vendor: ${decoded.data.gpuFingerprint.vendor}`);
    }
    
    if (decoded.errors && decoded.errors.length > 0) {
      console.log(`  Errors: ${decoded.errors.join(', ')}`);
    }
  }

  console.log('\n═══════════════════════════════════════════════════════════════');
  console.log('  Consensus Verification');
  console.log('═══════════════════════════════════════════════════════════════\n');

  // Simulate two players seeing the same event
  const player1State: GameStateSnapshot = {
    ...gameState,
    playerPosition: [100.5, 64.0, -200.25],
  };
  const player2State: GameStateSnapshot = {
    ...gameState,
    playerPosition: [105.0, 64.0, -198.0], // Slightly different position (different player)
  };
  
  const player1Watermark: WatermarkData = {
    ...watermarkData,
    clientId: 'player_1_client_id',
    gameState: player1State,
    gpuFingerprint: { ...gpuFingerprint, driverHash: 'player1_gpu_hash' },
  };
  
  const player2Watermark: WatermarkData = {
    ...watermarkData,
    clientId: 'player_2_client_id',
    gameState: player2State,
    gpuFingerprint: { ...gpuFingerprint, renderer: 'AMD Radeon RX 6800', driverHash: 'player2_gpu_hash' },
  };
  
  const player1Encoded = encoder.encode(player1Watermark, width, height);
  const player2Encoded = encoder.encode(player2Watermark, width, height);
  
  console.log('Comparing screenshots from two players:');
  console.log(`  Player 1 position: (${player1State.playerPosition.join(', ')})`);
  console.log(`  Player 2 position: (${player2State.playerPosition.join(', ')})`);
  
  const player1Decoded = decoder.decode(player1Encoded.pixelData, width, height);
  const player2Decoded = decoder.decode(player2Encoded.pixelData, width, height);
  
  const comparison = ConsensusVerifier.compare(player1Decoded, player2Decoded);
  
  console.log(`\nComparison results:`);
  console.log(`  Overall match: ${comparison.match ? '✓' : '✗'}`);
  console.log(`  Similarity: ${(comparison.similarity * 100).toFixed(1)}%`);
  console.log(`  State match: ${comparison.stateMatch ? '✓' : '✗'}`);
  console.log(`  GPU match: ${comparison.gpuMatch ? '✓ (same GPU)' : '✗ (different GPUs)'}`);
  console.log(`  Timestamp delta: ${comparison.timestampDelta}ms`);
  
  if (comparison.discrepancies.length > 0) {
    console.log(`  Discrepancies: ${comparison.discrepancies.join(', ')}`);
  }

  // Multi-player consensus
  console.log('\n── Multi-Player Consensus ──\n');
  
  // Create more player screenshots
  const screenshots = [player1Decoded, player2Decoded];
  
  const consensusResult = ConsensusVerifier.verifyConsensus(screenshots, 0.66);
  
  console.log(`Verifying consensus across ${screenshots.length} players:`);
  console.log(`  Threshold: 66% agreement required`);
  console.log(`  Consensus reached: ${consensusResult.consensus ? '✓' : '✗'}`);
  console.log(`  Agreement ratio: ${(consensusResult.agreementRatio * 100).toFixed(1)}%`);
  console.log(`  Clusters found: ${consensusResult.clusters.length}`);
  console.log(`  Cluster sizes: ${consensusResult.clusters.map(c => c.length).join(', ')}`);

  console.log('\n═══════════════════════════════════════════════════════════════');
  console.log('  ML Feature Extraction');
  console.log('═══════════════════════════════════════════════════════════════\n');

  console.log('Extracting ML-readable features from watermarked image:');
  
  const decoded = decoder.decode(encoded.pixelData, width, height);
  const features = MLFeatureExtractor.extract(encoded.pixelData, width, height, decoded);
  
  console.log('\nCore Features:');
  console.log(`  Watermark present: ${features.watermarkPresent ? '✓' : '✗'}`);
  console.log(`  Confidence: ${(features.confidence * 100).toFixed(1)}%`);
  
  console.log('\nGPU Features:');
  console.log(`  Vendor: ${features.gpuVendor}`);
  console.log(`  Renderer: ${features.gpuRenderer}`);
  console.log(`  Float precision hash: ${features.floatPrecisionHash}`);
  
  console.log('\nGame State Features:');
  console.log(`  World seed hash: ${features.worldSeedHash}`);
  console.log(`  Player position bucket: (${features.playerPositionBucket.join(', ')})`);
  console.log(`  Time of day (normalized): ${features.timeOfDayNormalized.toFixed(4)}`);
  console.log(`  Weather category: ${features.weatherCategory}`);
  
  console.log('\nChain Features:');
  console.log(`  Event sequence: ${features.eventSequence}`);
  console.log(`  Chain hash prefix: ${features.chainHashPrefix}`);
  
  console.log('\nImage Analysis Features:');
  console.log(`  LSB entropy: ${features.lsbEntropy.toFixed(4)}`);
  console.log(`  Edge complexity: ${features.edgeComplexity.toFixed(4)}`);
  console.log(`  Color distribution (first 8 bins): [${features.colorDistribution.slice(0, 8).map(v => v.toFixed(3)).join(', ')}]`);

  console.log('\n═══════════════════════════════════════════════════════════════');
  console.log('  Demo Complete');
  console.log('═══════════════════════════════════════════════════════════════\n');

  console.log('The Shader Watermarking System provides:');
  console.log('  • LSB steganography for invisible state encoding');
  console.log('  • GPU fingerprinting for hardware identification');
  console.log('  • Float precision analysis for determinism verification');
  console.log('  • Multi-player consensus verification');
  console.log('  • ML-readable feature extraction');
  console.log('  • Cryptographic integrity verification');
  console.log('\nUse cases:');
  console.log('  • Verify players saw the same game events');
  console.log('  • Detect modified clients by GPU behavior');
  console.log('  • Create verifiable gameplay records');
  console.log('  • Enable screenshot-based consensus mechanisms');
}

runDemo().catch(console.error);
