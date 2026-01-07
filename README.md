# FarmCraft Monorepo

A comprehensive Minecraft mod for enhanced farming mechanics with proof-of-work recipe discovery and distributed protein folding computation.

## Overview

This mod introduces:
- **Enhanced Fertilizers**: Craft fertilizers from Tuff, Diorite, Calcite, Gravel, and other materials
- **Power Foods**: Crops grown with special fertilizers grant unique effects
- **Proof-of-Work Recipes**: Discover new recipes by contributing to distributed computation
- **Protein Folding Integration**: Help solve real scientific problems while playing
- **Shader-Based Computation**: GPU-accelerated work units running in the background

## Architecture

```
minecraft/
├── packages/           # Shared TypeScript packages
│   ├── protocol/       # Communication protocol definitions
│   ├── pow-core/       # Proof-of-work core algorithms
│   ├── folding-client/ # Protein folding work unit client
│   └── types/          # Shared type definitions
├── mod/
│   ├── forge/          # Forge mod (1.20.x)
│   └── fabric/         # Fabric mod (1.20.x)
├── server/
│   ├── recipe-server/  # Recipe distribution server
│   └── pow-validator/  # Proof-of-work validation service
└── tools/
    └── dev-utils/      # Development utilities
```

## Quick Start

```bash
# Install dependencies
pnpm install

# Build everything
pnpm build

# Build just the Forge mod
pnpm build:mod

# Start the recipe server
pnpm dev:server
```

## Mod Features

### Fertilizers

| Fertilizer | Base Material | Effect on Crops |
|------------|---------------|-----------------|
| Stone Dust | Diorite | +Growth Speed |
| Calcium Mix | Calcite | +Yield |
| Mineral Blend | Tuff | +Effect Duration |
| Gravel Grit | Gravel | +Effect Potency |

### Power Foods

Crops grown with enhanced fertilizers gain special properties:
- **Speed Carrots**: Grant Speed effect
- **Strength Potatoes**: Grant Strength effect
- **Resistance Beets**: Grant Resistance effect
- **Night Vision Wheat**: Grant Night Vision effect

### Proof-of-Work System

1. Client requests recipe list from server
2. Server issues a computational challenge
3. Client solves challenge (protein folding work unit)
4. Server validates solution and returns recipes
5. Tokens can be pre-earned for future requests

## Development

### Prerequisites

- Java 17+ (for Minecraft mod)
- Node.js 20+ (for server and tools)
- pnpm 8+ (package manager)
- Gradle 8+ (mod building)

### Building the Mod

```bash
cd mod/forge
./gradlew build
```

The built JAR will be in `mod/forge/build/libs/`.

### Running Tests

```bash
# Run all tests (mod + server)
pnpm run test:all

# Run only mod command tests
pnpm run test:mod

# Run only server integration tests
pnpm run test:e2e

# Validate command registration
pnpm run test:mod:validate
```

See [docs/TESTING.md](docs/TESTING.md) for complete testing documentation.

### Available Commands

The mod exposes the following in-game commands:
- `/farmcraft` or `/farmcraft guide` - Show in-game guide
- `/farmcraft status` - Check server connection status
- `/farmcraft help <topic>` - Get help on specific topics
- `/farmcraft ask <question>` - Ask the AI assistant
- `/farmcraft topics` - List all available documentation topics

All commands are automatically tested in CI/CD to ensure they remain functional.

## CI/CD

Tests run automatically on:
- Push to `main` or `develop` branches
- Pull requests

The build will fail if:
- Any command tests fail
- Command registration is incomplete
- Integration tests fail
- Build errors occur

See `.github/workflows/mod-ci.yml` for workflow configuration.

## License

MIT License - See LICENSE file
