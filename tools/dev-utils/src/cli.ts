#!/usr/bin/env node
/**
 * FarmCraft Development CLI
 * Utilities for testing and development
 */

import { Command } from 'commander';
import chalk from 'chalk';
import { WebSocket } from 'ws';
import { generateChallenge, verifySolution, generateToken } from '@farmcraft/pow-core';
import { MessageType, PROTOCOL_VERSION } from '@farmcraft/protocol';

const program = new Command();

program
  .name('farmcraft-dev')
  .description('FarmCraft development utilities')
  .version('1.0.0');

// ============================================================================
// Challenge Commands
// ============================================================================

program
  .command('challenge')
  .description('Generate and test proof-of-work challenges')
  .option('-t, --type <type>', 'Challenge type (hash_challenge, protein_folding, entropy_generation)', 'hash_challenge')
  .option('-d, --difficulty <number>', 'Difficulty level (1-20)', '1')
  .action((options) => {
    console.log(chalk.blue('Generating challenge...'));
    
    const challenge = generateChallenge({
      type: options.type as any,
      difficulty: parseInt(options.difficulty),
    });

    console.log(chalk.green('Challenge generated:'));
    console.log(JSON.stringify(challenge, null, 2));
  });

// ============================================================================
// Token Commands
// ============================================================================

program
  .command('token')
  .description('Generate and verify work tokens')
  .option('-p, --player <id>', 'Player ID', 'test-player')
  .option('-c, --credits <number>', 'Token credits', '100')
  .option('-h, --hours <number>', 'Validity in hours', '24')
  .action((options) => {
    console.log(chalk.blue('Generating token...'));
    
    const token = generateToken(
      options.player,
      parseInt(options.credits),
      parseInt(options.hours)
    );

    console.log(chalk.green('Token generated:'));
    console.log(JSON.stringify(token, null, 2));
    
    // Base64 encode for transport
    const encoded = Buffer.from(JSON.stringify(token)).toString('base64');
    console.log(chalk.yellow('\nEncoded token for HTTP header:'));
    console.log(encoded);
  });

// ============================================================================
// Server Test Commands
// ============================================================================

program
  .command('test-server')
  .description('Test connection to recipe server')
  .option('-u, --url <url>', 'Server WebSocket URL', 'ws://localhost:3001')
  .action(async (options) => {
    console.log(chalk.blue(`Connecting to ${options.url}...`));
    
    const ws = new WebSocket(options.url);
    
    ws.on('open', () => {
      console.log(chalk.green('Connected!'));
      
      // Send hello message
      const hello = {
        type: MessageType.HELLO,
        protocolVersion: PROTOCOL_VERSION,
        clientVersion: '1.0.0-dev',
        playerId: 'test-cli',
        playerName: 'Test CLI',
        capabilities: {
          supportsShaderCompute: false,
          supportedWorkTypes: ['hash_challenge', 'protein_folding', 'entropy_generation'],
        },
      };
      
      ws.send(JSON.stringify(hello));
      console.log(chalk.gray('Sent HELLO'));
    });

    ws.on('message', (data) => {
      const message = JSON.parse(data.toString());
      console.log(chalk.cyan('Received:'), JSON.stringify(message, null, 2));
      
      if (message.type === MessageType.HELLO_ACK) {
        console.log(chalk.green('Handshake complete!'));
        
        // Request a challenge
        ws.send(JSON.stringify({
          type: MessageType.REQUEST_CHALLENGE,
          sessionId: message.sessionId,
        }));
        console.log(chalk.gray('Requested challenge'));
      }
    });

    ws.on('error', (error) => {
      console.log(chalk.red('Error:'), error.message);
    });

    ws.on('close', () => {
      console.log(chalk.yellow('Connection closed'));
    });

    // Keep alive for 30 seconds
    await new Promise(resolve => setTimeout(resolve, 30000));
    ws.close();
  });

// ============================================================================
// Benchmark Commands
// ============================================================================

program
  .command('benchmark')
  .description('Benchmark proof-of-work solvers')
  .option('-t, --type <type>', 'Challenge type', 'hash_challenge')
  .option('-d, --difficulty <number>', 'Difficulty level', '3')
  .option('-i, --iterations <number>', 'Number of challenges to solve', '5')
  .action(async (options) => {
    const { type, difficulty, iterations } = options;
    const numIterations = parseInt(iterations);
    const difficultyLevel = parseInt(difficulty);

    console.log(chalk.blue(`Benchmarking ${type} at difficulty ${difficultyLevel}...`));
    console.log(chalk.gray(`Running ${numIterations} iterations\n`));

    const times: number[] = [];

    for (let i = 0; i < numIterations; i++) {
      const challenge = generateChallenge({
        type: type as any,
        difficulty: difficultyLevel,
      });

      const startTime = Date.now();
      
      // Simulate solving (simplified)
      let solution: string | null = null;
      
      if (type === 'hash_challenge') {
        const prefix = challenge.payload.prefix as string;
        const target = challenge.payload.targetDifficulty as number;
        const targetStr = '0'.repeat(target);
        
        const crypto = await import('crypto');
        let nonce = 0;
        
        while (nonce < 10000000) {
          const hash = crypto.createHash('sha256')
            .update(prefix + nonce)
            .digest('hex');
          
          if (hash.startsWith(targetStr)) {
            solution = String(nonce);
            break;
          }
          nonce++;
        }
      }

      const elapsed = Date.now() - startTime;
      times.push(elapsed);

      if (solution) {
        console.log(chalk.green(`  Iteration ${i + 1}: ${elapsed}ms (solved)`));
      } else {
        console.log(chalk.red(`  Iteration ${i + 1}: ${elapsed}ms (failed)`));
      }
    }

    const avg = times.reduce((a, b) => a + b, 0) / times.length;
    const min = Math.min(...times);
    const max = Math.max(...times);

    console.log(chalk.yellow('\nResults:'));
    console.log(`  Average: ${avg.toFixed(2)}ms`);
    console.log(`  Min: ${min}ms`);
    console.log(`  Max: ${max}ms`);
  });

// ============================================================================
// Recipe Commands
// ============================================================================

program
  .command('list-recipes')
  .description('List all default recipes')
  .action(() => {
    const recipes = [
      { id: 'stone_dust_fertilizer', name: 'Stone Dust Fertilizer', tier: 'basic' },
      { id: 'calcium_mix_fertilizer', name: 'Calcium Mix Fertilizer', tier: 'basic' },
      { id: 'mineral_blend_fertilizer', name: 'Mineral Blend Fertilizer', tier: 'basic' },
      { id: 'gravel_grit_fertilizer', name: 'Gravel Grit Fertilizer', tier: 'basic' },
      { id: 'enhanced_stone_fertilizer', name: 'Enhanced Stone Fertilizer', tier: 'enhanced' },
      { id: 'enhanced_mineral_fertilizer', name: 'Enhanced Mineral Fertilizer', tier: 'enhanced' },
      { id: 'superior_blend_fertilizer', name: 'Superior Blend Fertilizer', tier: 'superior' },
      { id: 'speed_carrot', name: 'Speed Carrot', tier: 'basic' },
      { id: 'strength_potato', name: 'Strength Potato', tier: 'basic' },
      { id: 'resistance_beet', name: 'Resistance Beetroot', tier: 'basic' },
      { id: 'night_vision_bread', name: 'Night Vision Bread', tier: 'basic' },
      { id: 'super_speed_carrot', name: 'Super Speed Carrot', tier: 'enhanced' },
      { id: 'regeneration_apple', name: 'Regeneration Apple', tier: 'superior' },
    ];

    console.log(chalk.blue('FarmCraft Recipes:\n'));
    
    const tiers = ['basic', 'enhanced', 'superior', 'legendary'];
    for (const tier of tiers) {
      const tierRecipes = recipes.filter(r => r.tier === tier);
      if (tierRecipes.length > 0) {
        console.log(chalk.yellow(`${tier.toUpperCase()}:`));
        for (const recipe of tierRecipes) {
          console.log(`  - ${recipe.name} (${recipe.id})`);
        }
        console.log();
      }
    }
  });

program.parse();
