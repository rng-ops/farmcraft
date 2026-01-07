# FarmCraft E2E Testing Guide

## Overview

This guide covers end-to-end testing for FarmCraft gameplay features, including both automated bot tests and manual QA test scenarios.

## Automated Testing

### Test Bot

The E2E bot (`tools/e2e-bot`) automatically tests gameplay features by simulating a player in-game.

**Setup:**
```bash
cd tools/e2e-bot
pnpm install
pnpm build
```

**Running Tests:**
```bash
# Test against local server
pnpm test:local

# Test against remote server
node dist/test-bot.js <host> <port>
```

### Minecraft GameTest Framework

In-game tests using Minecraft's GameTest framework are located in `mod/forge/src/test/java/com/farmcraft/tests/`.

**Running GameTests:**
```bash
cd mod/forge
./gradlew runGameTestServer
```

## Manual QA Test Scenarios

### Test Environment Setup

1. **Start Servers:**
   ```bash
   pnpm run start:all
   ```

2. **Launch Minecraft:**
   - Forge 1.20.4
   - Install FarmCraft mod
   - Create new world (Survival mode recommended)

3. **Verify Server Connection:**
   - Run `/farmcraft status`
   - Should show "Documentation AI: ✓ Available"

---

## Gameplay Test Scenarios

### Scenario 1: Fertilizer Crafting

**Objective:** Verify all fertilizers can be crafted

**Steps:**
1. Gather materials:
   - Diorite blocks
   - Calcite blocks
   - Tuff blocks
   - Gravel blocks

2. **Test: Stone Dust Fertilizer**
   - Open crafting table
   - Place diorite in crafting grid
   - ✅ **Expected:** Stone Dust Fertilizer appears in output
   - Take fertilizer
   - ✅ **Expected:** Item tooltip shows "Increases growth speed"

3. **Test: Calcium Fertilizer**
   - Place calcite in crafting grid
   - ✅ **Expected:** Calcium Mix fertilizer crafted
   - ✅ **Expected:** Tooltip shows "Increases yield"

4. **Test: Mineral Fertilizer**
   - Place tuff in crafting grid
   - ✅ **Expected:** Mineral Blend fertilizer crafted
   - ✅ **Expected:** Tooltip shows "Increases effect duration"

5. **Test: Gravel Fertilizer**
   - Place gravel in crafting grid
   - ✅ **Expected:** Gravel Grit fertilizer crafted
   - ✅ **Expected:** Tooltip shows "Increases effect potency"

**Pass Criteria:**
- [ ] All 4 fertilizers can be crafted
- [ ] Each has correct tooltip description
- [ ] Items stack correctly (up to 64)

---

### Scenario 2: Applying Fertilizers to Farmland

**Objective:** Verify fertilizers can be applied to farmland

**Steps:**
1. Create farmland:
   - Use hoe on dirt/grass
   - Place water source nearby

2. **Test: Apply Fertilizer**
   - Hold fertilizer in hand
   - Right-click on farmland
   - ✅ **Expected:** Fertilizer consumed from stack
   - ✅ **Expected:** Farmland shows particle effect
   - ✅ **Expected:** Farmland appears slightly different (color/texture)

3. **Test: Multiple Fertilizers**
   - Try applying a different fertilizer to the same farmland
   - ✅ **Expected:** Should not apply (or replace existing)

**Pass Criteria:**
- [ ] Fertilizer is consumed when applied
- [ ] Visual feedback is provided
- [ ] Each farmland block tracks its fertilizer type

---

### Scenario 3: Enhanced Crop Growth

**Objective:** Verify crops grow faster on fertilized farmland

**Setup:**
- Place two patches of farmland (3x3 each)
- Apply Stone Dust fertilizer to one patch
- Leave the other patch normal

**Steps:**
1. Plant carrots on both patches
2. Wait and observe growth
3. Use `/time add 100` to advance time
4. Compare growth stages

**Expected Results:**
- ✅ Fertilized crops reach next growth stage faster
- ✅ Difference visible after ~10-20 time additions
- ✅ Both eventually reach maturity

**Pass Criteria:**
- [ ] Fertilized crops grow measurably faster
- [ ] Growth rate difference is noticeable
- [ ] No crashes or errors during growth

---

### Scenario 4: Speed Carrot (Enhanced Food)

**Objective:** Verify enhanced carrots grant Speed effect

**Steps:**
1. Setup:
   - Apply Stone Dust fertilizer to farmland
   - Plant carrots
   - Wait for growth to completion

2. **Test: Harvest Enhanced Carrot**
   - Break fully-grown carrot
   - ✅ **Expected:** Carrot drops with special tooltip
   - ✅ **Expected:** Tooltip says "Speed Carrot" or "Enhanced"

3. **Test: Eat Enhanced Carrot**
   - Have hunger below max
   - Eat the carrot (right-click)
   - ✅ **Expected:** Speed effect applied (see icon in HUD)
   - ✅ **Expected:** Effect lasts 30-60 seconds
   - Check F3 screen or inventory
   - ✅ **Expected:** "Speed I" or higher shows in effects

4. **Test: Speed Effect Behavior**
   - Walk/sprint around
   - ✅ **Expected:** Movement speed noticeably increased
   - Jump and move
   - ✅ **Expected:** Speed maintained

**Pass Criteria:**
- [ ] Enhanced carrot has distinct appearance/tooltip
- [ ] Eating grants Speed effect
- [ ] Effect duration is appropriate
- [ ] Speed increase is noticeable

---

### Scenario 5: Strength Potato

**Objective:** Verify enhanced potatoes grant Strength effect

**Setup:**
- Apply Calcium fertilizer to farmland
- Plant potatoes

**Steps:**
1. Harvest enhanced potato
2. Eat potato
3. ✅ **Expected:** Strength effect applied
4. Attack mob or break block
5. ✅ **Expected:** Increased damage/mining speed

**Pass Criteria:**
- [ ] Enhanced potato crafted correctly
- [ ] Strength effect applied
- [ ] Effect impacts gameplay appropriately

---

### Scenario 6: Resistance Beetroot

**Objective:** Verify enhanced beetroot grants Resistance effect

**Setup:**
- Apply Mineral Blend fertilizer to farmland
- Plant beetroot

**Steps:**
1. Harvest enhanced beetroot
2. Eat beetroot
3. ✅ **Expected:** Resistance effect applied
4. Take damage from mob or fall
5. ✅ **Expected:** Damage reduced

**Pass Criteria:**
- [ ] Enhanced beetroot works
- [ ] Resistance effect applied
- [ ] Damage reduction is noticeable

---

### Scenario 7: Night Vision Wheat

**Objective:** Verify enhanced wheat grants Night Vision effect

**Setup:**
- Apply Gravel Grit fertilizer to farmland
- Plant wheat

**Steps:**
1. Harvest enhanced wheat
2. Craft into bread (if needed)
3. Eat the bread/wheat
4. ✅ **Expected:** Night Vision effect applied
5. Go into dark area or night time
6. ✅ **Expected:** Can see clearly without torches

**Pass Criteria:**
- [ ] Enhanced wheat harvested
- [ ] Night Vision effect applied
- [ ] Visibility improved in darkness

---

### Scenario 8: Recipe Discovery System

**Objective:** Test proof-of-work recipe unlock system

**Steps:**
1. **Test: Initial Recipe State**
   - Check recipe book
   - ✅ **Expected:** Some recipes are locked/hidden

2. **Test: Unlock Recipe**
   - (Depends on DRM system implementation)
   - Open recipe interface
   - Request unlock for fertilizer recipe
   - ✅ **Expected:** PoW challenge initiated
   - ✅ **Expected:** Progress indicator shown

3. **Test: After Unlock**
   - Complete PoW challenge
   - ✅ **Expected:** Recipe appears in book
   - ✅ **Expected:** Can now craft the item

**Pass Criteria:**
- [ ] Recipe unlock system is accessible
- [ ] PoW challenge completes
- [ ] Unlocked recipes become available

---

### Scenario 9: In-Game Documentation Commands

**Objective:** Test all /farmcraft commands

**Commands to Test:**

1. **`/farmcraft guide`**
   - Execute command
   - ✅ **Expected:** Guide message appears in chat
   - ✅ **Expected:** Lists all available commands

2. **`/farmcraft status`**
   - Execute command
   - ✅ **Expected:** Shows server connection status
   - ✅ **Expected:** Lists Recipe Server and Docs AI status

3. **`/farmcraft help fertilizers`**
   - Execute command
   - ✅ **Expected:** Shows help about fertilizers
   - ✅ **Expected:** Lists all fertilizer types

4. **`/farmcraft ask how do I craft fertilizers?`**
   - Execute command
   - ✅ **Expected:** AI responds with crafting info
   - ✅ **Expected:** Response is relevant and helpful

5. **`/farmcraft topics`**
   - Execute command
   - ✅ **Expected:** Lists all documentation topics
   - ✅ **Expected:** Topics are clickable/usable

**Pass Criteria:**
- [ ] All commands execute without errors
- [ ] Responses are formatted correctly
- [ ] Information provided is accurate

---

### Scenario 10: Server Integration

**Objective:** Verify multiplayer functionality

**Setup:**
- Run dedicated server with FarmCraft
- Connect with 2+ players

**Steps:**
1. **Test: Multiple Players**
   - Both players apply fertilizers
   - ✅ **Expected:** Effects work independently
   - ✅ **Expected:** No conflicts or crashes

2. **Test: Recipe Sync**
   - Player 1 unlocks a recipe
   - Player 2 checks their recipes
   - ✅ **Expected:** Recipes sync per-player
   - ✅ **Expected:** No cross-contamination

3. **Test: Enhanced Crops**
   - Player 1 plants on fertilized farmland
   - Player 2 harvests (if permissions allow)
   - ✅ **Expected:** Effects transfer correctly

**Pass Criteria:**
- [ ] Multi-player works without issues
- [ ] Player-specific data maintained
- [ ] No duplication bugs

---

## QA Testing Checklist

### Pre-Flight Checks
- [ ] Servers running (Recipe, MCP, Docs AI)
- [ ] Minecraft 1.20.4 with Forge
- [ ] FarmCraft mod installed
- [ ] Creative/Survival world ready
- [ ] Commands accessible

### Core Features
- [ ] All 4 fertilizers craftable
- [ ] Fertilizers apply to farmland
- [ ] Enhanced crops grow correctly
- [ ] All 4 food effects work
- [ ] Recipe unlock system functional

### Commands
- [ ] `/farmcraft guide` works
- [ ] `/farmcraft status` works
- [ ] `/farmcraft help` works
- [ ] `/farmcraft ask` works
- [ ] `/farmcraft topics` works

### Edge Cases
- [ ] Apply fertilizer to non-farmland (should fail)
- [ ] Eat normal crops (should not give effects)
- [ ] Break fertilized farmland (fertilizer lost?)
- [ ] Multiple fertilizers on same spot
- [ ] Disconnect during PoW challenge

### Performance
- [ ] No lag when growing crops
- [ ] No memory leaks over time
- [ ] Commands respond quickly
- [ ] Server connection stable

---

## Reporting Issues

When reporting bugs, include:
1. **Scenario:** Which test scenario failed
2. **Expected:** What should have happened
3. **Actual:** What actually happened
4. **Steps:** How to reproduce
5. **Logs:** Relevant log files
6. **Environment:** Minecraft version, mod version, etc.

**Example Report:**
```
Scenario: Speed Carrot
Expected: Eating enhanced carrot grants Speed effect
Actual: No effect applied, carrot consumed normally
Steps:
  1. Applied Stone Dust fertilizer to farmland
  2. Planted and grew carrot
  3. Harvested carrot (looked normal)
  4. Ate carrot - no speed buff

Logs: [Attach latest.log]
Environment: MC 1.20.4, Forge 49.0.30, FarmCraft v1.0.0
```

---

## Automation Integration

### CI/CD Testing

Tests run automatically on:
- Pull requests
- Main branch pushes
- Release tags

**Commands:**
```bash
# Run all E2E tests
pnpm run test:e2e

# Run with bot
pnpm run test:bot

# Run GameTests
./gradlew runGameTestServer
```

### Test Reports

After tests complete:
- Check GitHub Actions for results
- View HTML reports in artifacts
- Check `build/reports/tests/` directory
