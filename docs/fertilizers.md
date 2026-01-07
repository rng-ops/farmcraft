# Fertilizer System

This document describes the fertilizer mechanics in FarmCraft.

## Fertilizer Types

### Basic Tier

| Fertilizer | Base Material | Primary Effect |
|------------|---------------|----------------|
| Stone Dust | Diorite | +25% Growth Speed |
| Calcium Mix | Calcite | +25% Yield |
| Mineral Blend | Tuff | +50% Effect Duration |
| Gravel Grit | Gravel | +25% Effect Potency |

### Enhanced Tier

| Fertilizer | Base Materials | Effects |
|------------|----------------|---------|
| Enhanced Stone | Stone Dust + Glowstone + Redstone | +50% Growth, +25% Yield |
| Enhanced Mineral | Mineral Blend + Amethyst + Lapis | +25% All, +75% Duration |

### Superior Tier

| Fertilizer | Base Materials | Effects |
|------------|----------------|---------|
| Superior Blend | Enhanced Stone + Enhanced Mineral + Nether Materials | +75% Growth, +50% Yield, +100% Duration |

## How It Works

### Application

1. Craft the fertilizer using base materials
2. Right-click on farmland with fertilizer
3. Farmland converts to fertilized variant
4. Plant crops as normal

### Effects on Crops

When crops grow on fertilized farmland:

1. **Growth Speed**: Random tick bonus increases growth rate
2. **Yield Bonus**: Extra drops when harvested
3. **Effect Duration**: Power food effects last longer
4. **Effect Potency**: Power food effects are stronger

### Power Foods

Crops grown on fertilized farmland become "power foods":

| Crop | Fertilizer | Power Food | Effect |
|------|------------|------------|--------|
| Carrot | Stone Dust | Speed Carrot | Speed I (30s) |
| Potato | Gravel Grit | Strength Potato | Strength I (30s) |
| Beetroot | Calcium Mix | Resistance Beet | Resistance I (30s) |
| Wheat | Mineral Blend | Night Vision Bread | Night Vision (60s) |

### Enhanced Power Foods

Using higher tier fertilizers creates enhanced versions:

| Power Food | Fertilizer Tier | Effect |
|------------|-----------------|--------|
| Super Speed Carrot | Enhanced | Speed II (60s) |
| Regeneration Apple | Superior | Regeneration II (20s) |

## Fertilized Farmland Properties

- More resistant to trampling
- Emits subtle particles
- Enhanced tiers emit light
- Maintains moisture better
- Can be detected with Crop Analyzer

## Tools

### Fertilizer Spreader

- Applies fertilizer in a 5x5 area
- Consumes fertilizer from inventory
- Upgradeable range

### Crop Analyzer

- Shows crop growth percentage
- Displays fertilizer type and effects
- Estimates time to harvest

## Recipes

### Stone Dust Fertilizer
```
[Diorite] [Diorite]
[Diorite] [Diorite]
[Bone Meal] [Bone Meal]
→ 4x Stone Dust Fertilizer
```

### Enhanced Stone Fertilizer
```
[Stone Dust] [Glowstone Dust]
[Stone Dust] [Redstone]
[Stone Dust] [Stone Dust]
→ 4x Enhanced Stone Fertilizer
```

### Superior Blend Fertilizer
```
[Enhanced Stone] [Enhanced Mineral]
[Nether Wart] [Blaze Powder]
[Enhanced Stone] [Enhanced Mineral]
→ 4x Superior Blend Fertilizer
```

## Advanced Mechanics

### Mutation System

With high-tier fertilizers and the Mutation Serum, crops can mutate:

- Rare chance per growth tick
- Produces unique variants
- Stacks with other bonuses

### Cross-Fertilization

Different fertilized farmland blocks adjacent to each other can combine effects (reduced magnitude).

### Weather Effects

- Rain: +25% fertilizer efficiency
- Thunderstorm: Small chance of super-growth
- Clear weather: Normal operation
