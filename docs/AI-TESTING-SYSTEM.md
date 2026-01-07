# AI-Controlled Testing with Cryptographic Attestation

## Overview

This document describes the complete AI-controlled testing system with cryptographic attestation, packet capture, and video recording for ML training pipelines.

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                         GitHub Actions CI/CD                     │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌────────────────┐         ┌──────────────────────────────┐   │
│  │  Build Stage   │────────▶│   Cryptographic Hashing      │   │
│  │  - Mod JAR     │         │   - SHA-256 of mod           │   │
│  │  - TypeScript  │         │   - SHA-256 of packages      │   │
│  │  - Packages    │         │   - Build manifest           │   │
│  └────────────────┘         └──────────────────────────────┘   │
│         │                              │                        │
│         │                              │                        │
│         ▼                              ▼                        │
│  ┌────────────────────────────────────────────────────────┐    │
│  │          Self-Hosted macOS M2 Runner                   │    │
│  ├────────────────────────────────────────────────────────┤    │
│  │                                                         │    │
│  │  ┌──────────────┐    ┌─────────────┐   ┌───────────┐ │    │
│  │  │  Minecraft   │◀───│  AI Test    │◀──│  GPT-OSS  │ │    │
│  │  │  Server      │    │  Controller │   │  (LLM)    │ │    │
│  │  │  + FarmCraft │───▶│             │   └───────────┘ │    │
│  │  └──────────────┘    └─────────────┘                  │    │
│  │        │                    │                          │    │
│  │        │                    │                          │    │
│  │        ▼                    ▼                          │    │
│  │  ┌──────────┐         ┌────────────┐                  │    │
│  │  │  Packet  │         │   Video    │                  │    │
│  │  │  Capture │         │  Recording │                  │    │
│  │  └──────────┘         └────────────┘                  │    │
│  │        │                    │                          │    │
│  └────────┼────────────────────┼──────────────────────────┘    │
│           │                    │                               │
│           ▼                    ▼                               │
│  ┌─────────────────────────────────────────────────────┐      │
│  │              ML Training Artifacts                   │      │
│  │  - Videos (.mp4)                                     │      │
│  │  - Packet captures (.json)                           │      │
│  │  - Cryptographic attestations (.json)                │      │
│  │  - Full transcripts (.txt)                           │      │
│  │  - System profiling data (.json)                     │      │
│  └─────────────────────────────────────────────────────┘      │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

## Components

### 1. AI Test Controller

**Package:** `@farmcraft/ai-test-controller`

Natural language controlled Minecraft bot that:

- Accepts test scenarios in plain English
- Queries GPT-OSS for action planning
- Executes bot commands based on LLM reasoning
- Captures all network packets
- Generates cryptographic attestations
- Creates full transcripts of every action

**Key Features:**

- Natural language to Minecraft commands
- Intelligent decision making via GPT-OSS
- Complete observability and auditability
- ML training pipeline ready

### 2. Cryptographic Attestation

Every test run generates SHA-256 hashes of:

- Mod JAR file
- TypeScript packages
- Test scenarios executed
- Actions taken during tests
- Final outcomes

**Attestation Format:**

```json
{
  "sha256": "abc123...",
  "timestamp": "2026-01-07T12:00:00.000Z",
  "scenario": "farming-workflow",
  "gitCommit": "abc123",
  "buildVersion": "42",
  "data": {
    "actions": [...],
    "outcomes": [...]
  }
}
```

### 3. Packet Capture

All Minecraft network traffic is captured in JSON format:

- Packet name (e.g., "chat", "move", "dig")
- Timestamp
- Complete packet data

**Use Cases:**

- Replay attacks for testing
- Network behavior analysis
- Protocol verification
- ML training data

### 4. Video Recording

Headless video recording on M2 Mac:

- H.264/MP4 format
- 30 FPS
- Full gameplay capture
- Synchronized with transcripts

### 5. System Profiling

Real-time collection of:

- CPU usage (per-core and total)
- Memory (active, wired, compressed)
- GPU usage (M2-specific)
- Network I/O
- Disk I/O

## Natural Language Test Control

### How It Works

1. **Test Scenario Defined in English:**

   ```typescript
   {
     id: 'farming-workflow',
     instructions: [
       'Look for nearby farmland or create some',
       'Plant wheat seeds if available in inventory',
       'Wait for crops to grow',
       'Harvest the wheat when ready',
       'Craft bread if possible'
     ]
   }
   ```

2. **AI Controller Queries GPT-OSS:**

   ```
   Human instruction: "Plant wheat seeds if available in inventory"

   Bot capabilities:
   - bot.chat(message)
   - bot.pathfinder.goto(goal)
   - bot.dig(block)
   - bot.equip(item)
   - bot.activateItem()

   Current state:
   - Position: (100, 64, 200)
   - Inventory: [wheat_seeds, stone, dirt]

   What should the bot do?
   ```

3. **LLM Responds with Plan:**

   ```json
   {
     "reasoning": "Bot has wheat seeds. Need to find farmland and right-click with seeds.",
     "commands": ["bot.equip(wheat_seeds)", "bot.lookAt(farmlandPosition)", "bot.activateItem()"],
     "expectedOutcome": "Seeds are planted on farmland"
   }
   ```

4. **Bot Executes and Verifies:**
   - Executes commands
   - Logs all actions
   - Captures packets
   - Records video
   - Generates attestation

## CI/CD Integration

### Workflow: `ai-attestation-tests.yml`

**Stage 1: Build with Attestation**

- Build mod and packages on standard runner
- Generate SHA-256 hashes
- Create build manifest
- Upload signed attestations

**Stage 2: AI-Controlled Tests (M2 Runner)**

- Download build artifacts
- Start Minecraft server with FarmCraft mod
- Start Recipe Server (port 7420)
- Start LLM Docs Server / GPT-OSS (port 7424)
- Start video recording (headless)
- Start system profiling
- Run AI-controlled test scenarios
- Capture all packets
- Generate test attestations
- Upload all artifacts

### Artifacts Generated

| Artifact        | Retention | Size        | Purpose                           |
| --------------- | --------- | ----------- | --------------------------------- |
| Videos          | 90 days   | ~100MB/test | Visual verification, ML training  |
| Packet Captures | 90 days   | ~10MB/test  | Protocol analysis, replay attacks |
| Attestations    | 365 days  | ~1KB/test   | Cryptographic verification        |
| Transcripts     | 90 days   | ~100KB/test | Natural language logs             |
| System Profiles | 90 days   | ~5MB/test   | Performance analysis              |
| ML Manifest     | 365 days  | ~1KB        | Training pipeline metadata        |

## Test Scenarios

### Built-in Scenarios

1. **basic-chat**: Test chat system and basic commands
2. **farming-workflow**: Complete farming cycle (plant → grow → harvest → craft)
3. **fertilizer-test**: Test FarmCraft's stone dust fertilizer system
4. **recipe-unlock**: Test proof-of-work recipe system
5. **ai-assistant**: Test AI documentation helper integration

### Creating Custom Scenarios

```typescript
const customScenario: TestScenario = {
  id: 'my-custom-test',
  description: 'Test a specific gameplay feature',
  instructions: ['Do the first thing', 'Then do the second thing', 'Verify the result'],
  expectedOutcomes: ['Feature works as expected', 'No errors occurred'],
  tags: ['custom', 'gameplay'],
};
```

## ML Training Pipeline Integration

### Data Structure

```
ml-training-data/
├── run-{id}/
│   ├── video.mp4              # Visual gameplay
│   ├── packets.json           # Network data
│   ├── attestation.json       # Ground truth labels
│   ├── transcript.txt         # Natural language descriptions
│   ├── profile.json           # System metrics
│   └── manifest.json          # Metadata
```

### Manifest Format

```json
{
  "build": {
    "commit": "abc123",
    "mod_hash": "def456",
    "packages_hash": "ghi789"
  },
  "test_run": {
    "timestamp": "2026-01-07T12:00:00.000Z",
    "platform": "macOS 14.1",
    "architecture": "arm64",
    "scenario": "farming-workflow"
  },
  "artifacts": {
    "videos": 1,
    "packets": 1,
    "attestations": 5,
    "transcripts": 1
  }
}
```

### ML Use Cases

1. **Gameplay Prediction**: Train models to predict player actions
2. **Bot Behavior Learning**: Learn optimal strategies from human-like tests
3. **Anomaly Detection**: Identify unusual patterns or bugs
4. **Test Case Generation**: Generate new test scenarios from existing data
5. **Visual Recognition**: Train computer vision models on Minecraft gameplay
6. **Natural Language Understanding**: Link English instructions to game actions

## GPT-OSS Integration

### Requirements

- Ollama running on port 11434
- GPT-OSS model installed: `ollama pull gpt-oss`
- LLM Docs server running on port 7424

### Configuration

```bash
# In packages/llm-docs/.env
PORT=7424
OLLAMA_URL=http://localhost:11434
OLLAMA_MODEL=gpt-oss
```

### API Endpoints

**Query LLM:**

```bash
POST http://localhost:7424/query
{
  "prompt": "How do I plant wheat seeds?",
  "context": "Current inventory: wheat_seeds, stone, dirt",
  "model": "gpt-oss"
}
```

**Response:**

```json
{
  "response": "To plant wheat seeds, you need to find or create farmland..."
}
```

## Security & Verification

### Cryptographic Chain of Trust

1. **Build Attestation**: SHA-256 of all built artifacts
2. **Test Attestation**: SHA-256 of test execution data
3. **Artifact Verification**: All artifacts linked to build hashes
4. **Tamper Detection**: Any modification breaks the hash chain

### Verifying Attestations

```bash
# Download attestation
gh run download RUN_ID -n test-attestations-RUN_NUMBER

# Verify hash
cat attestation.json | jq -r '.data' | sha256sum
# Should match the "sha256" field in attestation.json
```

## Local Development

### Setup

```bash
# Install dependencies
pnpm install

# Build all packages
pnpm run build

# Start servers
pnpm run start:all

# Run AI tests locally
cd packages/ai-test-controller
pnpm test:scenario localhost 25565 http://localhost:7424 basic-chat
```

### Testing Individual Components

```bash
# Test packet capture
cd packages/ai-test-controller
node dist/cli.js localhost 25565 http://localhost:7424 basic-chat

# Test video recording
~/record-minecraft-test.sh

# Test profiling
~/profile-minecraft-test.sh ./output

# Test LLM
curl -X POST http://localhost:7424/query \
  -H "Content-Type: application/json" \
  -d '{"prompt":"Test query"}'
```

## Troubleshooting

### AI Controller Not Connecting

```bash
# Check Minecraft server
tail -f mod/forge/run-server/logs/latest.log

# Check LLM server
curl http://localhost:7424/health

# Check Recipe server
curl http://localhost:7420/health
```

### LLM Not Responding

```bash
# Check Ollama
ollama list
ollama run gpt-oss "test"

# Check llm-docs logs
cd packages/llm-docs
pnpm start
```

### Video Recording Issues

```bash
# Check ffmpeg
ffmpeg -version

# Check screen recording permissions
# System Settings → Privacy & Security → Screen Recording

# Test recording
ffmpeg -f avfoundation -list_devices true -i ""
```

### Packet Capture Empty

```bash
# Ensure bot is connecting
# Packets are only captured after bot spawns
# Check bot connection logs in transcript
```

## Performance Optimization

### Reduce Video Size

- Lower resolution: `-vf scale=1280:720`
- Lower bitrate: `-b:v 1000k`
- Higher compression: `-preset slow`

### Reduce Packet Capture Size

- Filter specific packets only
- Compress JSON output
- Sample packets instead of capturing all

### Speed Up Tests

- Run specific scenarios instead of all
- Reduce wait times between actions
- Use multiple runners in parallel

## Future Enhancements

1. **Real Client Rendering**: Use actual Minecraft client instead of server-only
2. **Advanced AI Plugins**: Integrate Baritone or other advanced AI mods
3. **Multi-Agent Testing**: Multiple bots working together
4. **Automated Scenario Generation**: LLM generates test scenarios
5. **Real-Time Streaming**: Stream gameplay to monitoring dashboard
6. **Cloud Storage Integration**: Auto-sync to S3/GCS for long-term archival
7. **ML Model Integration**: Train models directly from test data
8. **Performance Regression Detection**: Automated performance comparisons

## References

- [Mineflayer Documentation](https://github.com/PrismarineJS/mineflayer)
- [GameTest Framework](https://learn.microsoft.com/en-us/minecraft/creator/documents/gametestgettingstarted)
- [Ollama](https://ollama.ai/)
- [GitHub Actions Artifacts](https://docs.github.com/en/actions/using-workflows/storing-workflow-data-as-artifacts)
- [Cryptographic Attestation](https://en.wikipedia.org/wiki/Trusted_Computing)
