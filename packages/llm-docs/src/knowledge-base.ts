/**
 * FarmCraft In-Game Documentation Knowledge Base
 * This data is used by LLM to answer questions about the mod
 */

// Mod Metadata
export const MOD_METADATA = {
  modId: 'farmcraft',
  modName: 'FarmCraft',
  version: '1.0.0',
  minecraftVersion: '1.20.4',
  forgeVersion: '49.0.30',
  description:
    'Enhanced farming with proof-of-work recipe discovery and distributed protein folding computation',
  authors: 'FarmCraft Team',

  features: [
    'Enhanced Fertilizers (Stone Dust, Calcium Mix, Mineral Blend, Gravel Grit)',
    'Power Foods (Speed Carrots, Strength Potatoes, Resistance Beets, Night Vision Wheat)',
    'Proof-of-Work Recipe Discovery',
    'Protein Folding Integration for scientific computation',
    'Shader-Based GPU Computation',
    'DRM & Version Verification System',
    'Recipe Server Integration (ports 7420/7421)',
    'MCP Server for recipe distribution (ports 7422/7423)',
    'AI Documentation Assistant (port 7424)',
  ],

  fertilizers: {
    basic: [
      'Stone Dust (Diorite)',
      'Calcium Mix (Calcite)',
      'Mineral Blend (Tuff)',
      'Gravel Grit (Gravel)',
    ],
    enhanced: ['Enhanced Stone', 'Enhanced Mineral'],
    superior: ['Superior Blend'],
  },

  powerFoods: {
    'Speed Carrot': 'Speed I (30s) - grown on Stone Dust fertilized farmland',
    'Strength Potato': 'Strength I (30s) - grown on Gravel Grit fertilized farmland',
    'Resistance Beet': 'Resistance I (30s) - grown on Calcium Mix fertilized farmland',
    'Night Vision Bread': 'Night Vision (60s) - grown on Mineral Blend fertilized farmland',
  },

  tools: ['Fertilizer Spreader', 'Crop Analyzer'],

  servers: {
    recipeServer: 'HTTP: 7420, WebSocket: 7421',
    mcpServer: 'HTTP: 7422, Distribution: 7423',
    docsServer: 'AI Assistant: 7424',
    minecraftServer: 'Game: 25565',
  },
};

export const FARMCRAFT_DOCS = {
  overview: `
# FarmCraft Mod Overview

FarmCraft is an advanced farming mod for Minecraft 1.20.4 that combines traditional farming with blockchain-inspired recipe discovery, proof-of-work mechanics, and distributed computing.

## Core Concepts

1. **Recipe Discovery System**: Recipes are unlocked through a server-based discovery system, not through crafting table patterns
2. **Proof-of-Work**: Crafting certain items requires solving computational challenges
3. **DRM & Authentication**: Client authenticates with recipe server to access advanced features
4. **Power Foods**: Special food items with enhanced effects
5. **Fertilizers**: Advanced farming boosters for crop growth

## Getting Started

When you first join a FarmCraft server:
1. The mod auto-connects to the recipe server (localhost:7420 in dev mode)
2. Check connection status in logs: "Successfully connected to recipe server!"
3. Use the in-game guide (/farmcraft guide) to learn about features
4. Start with basic farming and work towards advanced recipes
`,

  commands: `
# FarmCraft Commands

## Player Commands

### /farmcraft guide
Opens the in-game documentation UI showing all features and how to use them.

### /farmcraft status
Shows your current connection status to the recipe server, unlocked recipes count, and active proof-of-work challenges.

### /farmcraft recipes
Lists all recipes you have discovered so far.

### /farmcraft help [topic]
Get help on a specific topic. Available topics:
- farming
- recipes
- pow (proof-of-work)
- fertilizers
- powerfoods
- troubleshooting

### /farmcraft ask [question]
Ask the LLM documentation assistant any question about the mod. Examples:
- "/farmcraft ask how do I unlock new recipes?"
- "/farmcraft ask what are power foods?"
- "/farmcraft ask why isn't my crop growing faster?"

## Admin Commands (OP required)

### /farmcraft reload
Reloads configuration from server

### /farmcraft debug
Enables debug logging for troubleshooting

### /farmcraft reset [player]
Resets a player's recipe progress
`,

  recipeSystem: `
# Recipe Discovery System

## How It Works

1. **Server-Based Discovery**: Unlike vanilla Minecraft, recipes are managed by a central server
2. **Progressive Unlocking**: Start with basic recipes, unlock advanced ones through gameplay
3. **Challenge System**: Some recipes require completing proof-of-work challenges
4. **WebSocket Sync**: Your client stays in sync with the server via WebSocket (port 7421)

## Unlocking Recipes

Recipes can be unlocked by:
- **Harvesting crops**: Discover basic fertilizer recipes
- **Completing challenges**: Solve proof-of-work puzzles for advanced items
- **Trading**: Some NPCs may grant recipe knowledge
- **Achievements**: Reaching milestones unlocks new recipes

## Recipe Categories

1. **Basic Fertilizers**: Boost crop growth by 1.5x
2. **Advanced Fertilizers**: Boost crop growth by 2x-3x
3. **Power Foods**: Provide special effects beyond hunger restoration
4. **Special Tools**: Enhanced durability or efficiency
5. **Decorative Blocks**: Farming-themed building blocks
`,

  proofOfWork: `
# Proof-of-Work System

## What is Proof-of-Work?

FarmCraft uses computational challenges to gate certain powerful recipes. This creates interesting economy dynamics and prevents instant access to end-game items.

## How It Works

1. **Challenge Request**: When crafting a PoW-gated item, server sends a challenge
2. **GPU Computation**: Your client solves the challenge using shader-based computation
3. **Verification**: Server validates your solution
4. **Crafting Unlocked**: On success, the crafting operation completes

## Challenge Types

- **Hash Challenge**: Find a nonce that produces a hash below target difficulty
- **Matrix Challenge**: Solve linear algebra problems
- **Protein Folding**: Contribute to distributed folding@home-like calculations

## Difficulty Scaling

- Difficulty adjusts based on server load
- More players = lower individual difficulty
- Rare items have higher base difficulty
- Your GPU performance matters!

## Configuration

In mod config:
- \`enableProofOfWork\`: Toggle PoW system (default: true)
- \`powTimeout\`: Max time to solve challenge (default: 30s)
- \`useGPU\`: Enable GPU acceleration (default: true)
`,

  fertilizers: `
# Fertilizer System

## Fertilizer Types

### Basic Fertilizer
- **Effect**: 1.5x crop growth speed
- **Duration**: 5 minutes
- **Crafting**: 4x Wheat + 2x Bone Meal + 2x Seeds + 1x Water Bucket

### Advanced Fertilizer
- **Effect**: 2x crop growth speed
- **Duration**: 10 minutes
- **Crafting**: 4x Basic Fertilizer + 2x Diamond + 1x Nether Star + 1x Golden Apple

### Super Fertilizer
- **Effect**: 3x crop growth speed
- **Duration**: 15 minutes
- **Crafting**: Requires proof-of-work challenge completion

## Using Fertilizers

1. Right-click on farmland with fertilizer in hand
2. Farmland will have visual particles effect
3. Crops planted on fertilized farmland grow faster
4. Effect persists through crop harvesting (reusable land)

## Fertilized Farmland

- Retains water longer (never dries out when fertilized)
- Visual indicator: Green particles
- Effect stacks with other growth boosters
- Removed if farmland is broken
`,

  powerFoods: `
# Power Foods System

## What are Power Foods?

Power Foods are advanced food items that provide effects beyond basic hunger restoration. They combine nutrition with potion-like buffs.

## Available Power Foods

### Energy Bread
- **Hunger**: 8 (4 drumsticks)
- **Saturation**: 12.0
- **Effect**: Speed II for 60 seconds
- **Ingredients**: Wheat, Sugar, Redstone Dust

### Strength Stew
- **Hunger**: 10 (5 drumsticks)
- **Saturation**: 14.0
- **Effect**: Strength II for 120 seconds
- **Ingredients**: Beef, Carrot, Potato, Nether Wart

### Regeneration Cake
- **Hunger**: 12 (6 drumsticks)
- **Saturation**: 18.0
- **Effect**: Regeneration II for 30 seconds
- **Ingredients**: Milk, Egg, Sugar, Golden Carrot

### Mystic Elixir
- **Hunger**: 6 (3 drumsticks)
- **Saturation**: 20.0
- **Effect**: Night Vision, Water Breathing for 300 seconds
- **Ingredients**: Glass Bottle, Glowstone Dust, Prismarine Crystal

## Crafting Power Foods

Most power foods require:
1. Recipe unlocked via discovery system
2. Proof-of-work challenge completion
3. Special crafting station (Power Food Station block)
`,

  troubleshooting: `
# Troubleshooting Guide

## Connection Issues

### "Failed to connect to recipe server"
**Symptoms**: Mod loads but can't access recipes
**Solutions**:
1. Check if Recipe Server is running on port 7420
2. Verify \`recipeServerUrl\` in config matches your setup
3. Check firewall settings
4. Try \`/farmcraft status\` to see detailed error

### "WebSocket connection lost"
**Symptoms**: Recipes stop syncing mid-game
**Solutions**:
1. Check WebSocket port 7421 is accessible
2. Ensure server hasn't crashed
3. Try reconnecting: \`/farmcraft reconnect\`

## Gameplay Issues

### "Recipe not unlocking"
**Symptoms**: Completed requirement but recipe still locked
**Solutions**:
1. Wait 5-10 seconds for server sync
2. Check recipe requirements with \`/farmcraft recipes info <name>\`
3. Verify you completed the correct challenge type
4. Try \`/farmcraft sync\` to force re-sync

### "Proof-of-work taking too long"
**Symptoms**: Challenge doesn't complete within timeout
**Solutions**:
1. Check GPU is being used: \`/farmcraft debug gpu\`
2. Lower difficulty in server config
3. Increase \`powTimeout\` in client config
4. Close other GPU-intensive programs

### "Fertilizer not working"
**Symptoms**: Crops not growing faster on fertilized land
**Solutions**:
1. Verify farmland has green particles (effect active)
2. Ensure crop is supported by fertilizer type
3. Check if effect duration expired
4. Try breaking and replacing farmland

## Performance Issues

### "Lag when crafting"
**Symptoms**: Game freezes during PoW challenges
**Solutions**:
1. Disable GPU acceleration: set \`useGPU=false\`
2. Lower PoW difficulty on server
3. Allocate more RAM to Minecraft (-Xmx4G or higher)

### "High memory usage"
**Symptoms**: Minecraft using excessive RAM
**Solutions**:
1. Disable recipe caching: \`enableRecipeCache=false\`
2. Clear recipe cache: \`/farmcraft cache clear\`
3. Restart client periodically

## Debug Information

To get detailed logs for bug reports:
1. Enable debug mode: \`/farmcraft debug\`
2. Reproduce the issue
3. Check logs at: \`.minecraft/logs/latest.log\`
4. Share relevant log sections with mod developer
`,

  configuration: `
# Configuration Guide

## Client Configuration

Location: \`.minecraft/config/farmcraft-common.toml\`

### Network Settings
\`\`\`toml
[network]
recipeServerUrl = "localhost"
recipeServerPort = 7420
webSocketPort = 7421
autoConnect = true
reconnectDelay = 5000
\`\`\`

### Proof-of-Work Settings
\`\`\`toml
[proofOfWork]
enableProofOfWork = true
powTimeout = 30
useGPU = true
maxConcurrentChallenges = 3
\`\`\`

### UI Settings
\`\`\`toml
[ui]
showRecipeNotifications = true
showConnectionStatus = true
guideKeyBind = "G"
\`\`\`

## Server Configuration

Location: \`server/config/farmcraft-server.toml\`

### Recipe Server Settings
\`\`\`toml
[recipeServer]
port = 7420
wsPort = 7421
enableDRM = true
requireAuthentication = false
\`\`\`

### Difficulty Settings
\`\`\`toml
[difficulty]
basePoWDifficulty = 4
scalingFactor = 1.5
maxDifficulty = 10
\`\`\`

### Feature Toggles
\`\`\`toml
[features]
enableFertilizers = true
enablePowerFoods = true
enableProofOfWork = true
enableDistributedFolding = false
\`\`\`
`,

  development: `
# Development & Testing Guide

## Testing the Mod Locally

### 1. Start Services
\`\`\`bash
cd /Users/a/projects/minecraft
./quick-launch.sh
\`\`\`

This starts:
- Recipe Server (HTTP: 7420, WebSocket: 7421)
- MCP Server (HTTP: 7422, Distribution: 7423)
- Minecraft Server (25565, online-mode=false, survival mode)
- Minecraft Client (auto-connects to server)

### 2. Verify Connection
In-game, type:
\`\`\`
/farmcraft status
\`\`\`

Should show:
- ✓ Recipe Server: Connected
- ✓ WebSocket: Active
- ✓ Recipes Loaded: [count]

### 3. Test Recipe Discovery
\`\`\`
/farmcraft recipes
\`\`\`

### 4. Test Proof-of-Work
Attempt to craft a PoW-gated item and monitor logs for:
- Challenge request sent
- GPU computation starting
- Solution found
- Server verification

### 5. Test Fertilizers
1. Craft basic fertilizer
2. Right-click on farmland
3. Plant seeds
4. Observe growth speed (should be 1.5x)

## Stopping Services
\`\`\`bash
./stop-services.sh
\`\`\`

## Rebuilding Mod
\`\`\`bash
cd mod/forge
./gradlew clean jar
cp build/libs/farmcraft-1.0.0.jar run/mods/
\`\`\`

## Viewing Logs
- Client: \`.minecraft/logs/latest.log\`
- Server: \`mod/forge/run-server/logs/latest.log\`
- Recipe Server: Check terminal output
- MCP Server: Check terminal output
`,
};

export const QUICK_ANSWERS = {
  'how do I start':
    'Use /farmcraft guide to open the in-game documentation. Your mod auto-connects to the recipe server on join.',
  'unlock recipes':
    "Recipes unlock through gameplay: harvest crops, complete challenges, or reach achievements. Use /farmcraft recipes to see what you've unlocked.",
  'fertilizer not working':
    'Make sure farmland shows green particles. Right-click farmland with fertilizer, then plant crops. Effect lasts 5-15 minutes depending on fertilizer type.',
  'proof of work':
    'PoW challenges gate powerful recipes. When crafting, your GPU solves a computational puzzle. Check /farmcraft status to see active challenges.',
  'connection failed':
    'Ensure Recipe Server is running on port 7420. In dev mode, run ./quick-launch.sh from project root.',
  commands:
    'Main commands: /farmcraft guide, /farmcraft status, /farmcraft recipes, /farmcraft ask [question], /farmcraft help [topic]',
  'power foods':
    'Power Foods provide hunger + buffs. Requires unlocked recipes and proof-of-work. Examples: Energy Bread (Speed), Strength Stew (Strength).',
  'survival mode':
    'The server runs in survival mode by default for proper item drops and gameplay mechanics.',
};
