# FarmCraft Quick Start Guide

## One-Command Install

```bash
./install.sh
```

This will:

- Check/install Java 17 and Node.js
- Build all packages
- Install Minecraft Forge
- Install the FarmCraft mod
- Configure auto-connect to servers

## Launch the Game

```bash
./launch-farmcraft.sh
```

This starts the servers and opens Minecraft. Select the **Forge** profile!

---

## Manual Setup

### Prerequisites

- **Node.js 20+** - For the server and TypeScript packages
- **pnpm 8.15+** - Package manager
- **Java 17+** - For the Minecraft mod
- **Minecraft 1.20.4** - With Forge loader

### 1. Install Dependencies

```bash
pnpm install
```

### 2. Build All Packages

```bash
pnpm build
```

### 3. Start the Servers

```bash
./start-servers.sh
```

Servers:

- Recipe API: http://localhost:7420
- WebSocket: ws://localhost:7421
- MCP Server: http://localhost:7422

### 4. Build the Forge Mod

```bash
cd mod/forge
./gradlew build
```

The mod JAR: `mod/forge/build/libs/farmcraft-1.0.0.jar`

### 5. Install the Mod

Copy to your Minecraft mods folder:

```bash
cp mod/forge/build/libs/farmcraft-1.0.0.jar ~/.minecraft/mods/
```

### 6. Run Minecraft

Launch Minecraft with the Forge 1.20.4 profile.

## Project Structure

```
minecraft/
├── packages/           # Shared TypeScript packages
│   ├── types/          # Core type definitions
│   ├── protocol/       # Wire protocol
│   ├── pow-core/       # Proof-of-work algorithms
│   ├── folding-client/ # Protein folding simulation
│   └── shader-compute/ # GPU shader programs
├── server/
│   └── recipe-server/  # Recipe distribution server
├── mod/
│   └── forge/          # Minecraft Forge mod
├── tools/
│   └── dev-utils/      # Development CLI
└── docs/               # Documentation
```

## Key Commands

| Command           | Description                    |
| ----------------- | ------------------------------ |
| `pnpm install`    | Install all dependencies       |
| `pnpm build`      | Build all packages             |
| `pnpm dev:server` | Start recipe server (dev mode) |
| `pnpm test`       | Run all tests                  |
| `pnpm dev:cli`    | Run dev CLI                    |

## Mod Features

### Fertilizers

- **Tuff Fertilizer** - Basic tier, 1.25x growth speed
- **Diorite Fertilizer** - Enhanced tier, 1.5x growth speed
- **Calcite Fertilizer** - Superior tier, 2.0x growth speed
- **Gravel Fertilizer** - Enhanced tier, 1.5x growth speed

### Power Foods

- **Power Carrot** - Speed boost
- **Power Potato** - Strength boost
- **Power Beetroot** - Regeneration
- **Power Wheat** - Resistance

### Tools

- **Fertilizer Spreader** - Apply fertilizers in 3x3 area
- **Crop Analyzer** - View crop status and fertilizer info

## Proof-of-Work System

The mod includes a unique proof-of-work system where:

1. Players contribute to scientific computing (protein folding)
2. Completed work earns tokens
3. Tokens unlock advanced recipes
4. GPU shaders run in background for computation

See [docs/proof-of-work.md](docs/proof-of-work.md) for details.

## Configuration

Edit `mod/forge/src/main/resources/farmcraft-common.toml`:

```toml
[server]
server_host = "localhost"
server_port = 7420
enable_pow = true

[gameplay]
base_growth_multiplier = 1.0
max_fertilizer_stack = 64
```

## Troubleshooting

### Server won't start

- Check Node.js version: `node --version` (must be 20+)
- Check port availability: `lsof -i :7420`

### Mod won't build

- Check Java version: `java --version` (must be 17+)
- Clean and rebuild: `./gradlew clean build`

### Can't connect to server from mod

- Ensure server is running
- Check firewall settings
- Verify host/port in config

## Development

### Adding New Recipes

Edit `server/recipe-server/src/recipes.ts`:

```typescript
recipes.set('my_recipe', {
  id: 'my_recipe',
  inputs: [{ item: 'minecraft:diamond', count: 1 }],
  output: { item: 'farmcraft:power_diamond', count: 1 },
  workRequired: 200,
  discoverable: true,
});
```

### Adding New Items

1. Add item class in `mod/forge/src/main/java/com/farmcraft/item/`
2. Register in `ModItems.java`
3. Add recipe JSON in `resources/data/farmcraft/recipes/`
4. Add translation in `resources/assets/farmcraft/lang/en_us.json`
