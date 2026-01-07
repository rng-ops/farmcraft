# AI Test Controller

Natural language controlled Minecraft bot for automated testing using GPT-OSS.

## Features

- 🤖 **Natural Language Control**: Describe tests in plain English
- 🧠 **GPT-OSS Integration**: Uses local LLM for intelligent decision making
- 📦 **Packet Capture**: Records all network traffic for analysis
- 🔐 **Cryptographic Attestation**: SHA-256 hashes of test runs
- 📝 **Full Transcripts**: Detailed logs of every action
- 🎬 **ML Training Ready**: Outputs suitable for ML pipeline ingestion

## Usage

### Basic Usage

```bash
# Run all scenarios
pnpm test:scenario

# Run specific scenario
pnpm test:scenario localhost 25565 http://localhost:7424 basic-chat

# With environment variables
MC_HOST=localhost MC_PORT=25565 LLM_ENDPOINT=http://localhost:7424 pnpm test:scenario
```

### Programmatic Usage

```typescript
import { AITestController, TestScenario } from '@farmcraft/ai-test-controller';

const controller = new AITestController(
  'localhost',
  25565,
  'AIBot',
  'http://localhost:7424',
  './test-output'
);

await controller.connect();

const scenario: TestScenario = {
  id: 'my-test',
  description: 'Test something',
  instructions: ['Say hello in chat', 'Move forward 10 blocks', 'Break a block'],
  expectedOutcomes: ['All actions completed'],
  tags: ['custom'],
};

const result = await controller.runScenario(scenario);
console.log(`Test ${result.success ? 'passed' : 'failed'}`);

await controller.disconnect();
```

## Test Scenarios

### Built-in Scenarios

1. **basic-chat**: Test basic communication
2. **farming-workflow**: Complete farming cycle
3. **fertilizer-test**: Test FarmCraft fertilizers
4. **recipe-unlock**: Test proof-of-work recipe system
5. **ai-assistant**: Test AI documentation helper

### Creating Custom Scenarios

```typescript
const myScenario: TestScenario = {
  id: 'unique-id',
  description: 'Human-readable description',
  instructions: ['First thing to do', 'Second thing to do', 'Third thing to do'],
  expectedOutcomes: ['Expected result 1', 'Expected result 2'],
  tags: ['category1', 'category2'],
};
```

## Output Structure

```
test-output/
├── videos/              # Screen recordings (from runner)
├── packets/             # Network packet captures
│   └── scenario-timestamp.json
├── attestations/        # Cryptographic attestations
│   └── scenario-timestamp.json
└── transcripts/         # Full execution logs
    └── scenario-timestamp.txt
```

## Attestation Format

```json
{
  "sha256": "abc123...",
  "timestamp": "2026-01-07T12:00:00.000Z",
  "scenario": "basic-chat",
  "gitCommit": "abc123",
  "buildVersion": "1.0.0",
  "data": {
    "scenario": "basic-chat",
    "description": "Test basic chat communication",
    "actions": [...]
  }
}
```

## Packet Capture Format

```json
[
  {
    "timestamp": 1704628800000,
    "name": "chat",
    "data": "{\"message\":\"Hello\"}"
  },
  ...
]
```

## Integration with CI/CD

The AI Test Controller is designed to work seamlessly in CI/CD pipelines:

```yaml
- name: Run AI-controlled tests
  run: |
    cd packages/ai-test-controller
    pnpm test:scenario localhost 25565 http://localhost:7424 all ./ci-output

- name: Upload attestations
  uses: actions/upload-artifact@v4
  with:
    name: test-attestations
    path: ci-output/attestations/

- name: Upload packet captures
  uses: actions/upload-artifact@v4
  with:
    name: packet-captures
    path: ci-output/packets/
```

## GPT-OSS Integration

The controller queries GPT-OSS (via llm-docs server) to:

1. Interpret natural language instructions
2. Generate appropriate bot commands
3. Reason about game state
4. Verify test outcomes

### Example LLM Interaction

**Input**: "Move to the nearest tree and break it"

**LLM Response**:

```json
{
  "reasoning": "Need to locate nearest tree block and path to it",
  "commands": [
    "bot.pathfinder.goto(goals.Goal(treePosition))",
    "bot.lookAt(treeBlock)",
    "bot.dig(treeBlock)"
  ],
  "expectedOutcome": "Tree block is broken and wood is collected"
}
```

## ML Training Pipeline

Outputs are structured for ML training:

- **Attestations**: Ground truth labels with cryptographic verification
- **Packets**: Network-level gameplay data
- **Transcripts**: Natural language descriptions of actions
- **Videos**: Visual recordings (when paired with runner)

These can be fed into ML models for:

- Gameplay prediction
- Bot behavior learning
- Test case generation
- Anomaly detection

## Requirements

- Running Minecraft server (1.20.4 + Forge + FarmCraft mod)
- LLM server (llm-docs or compatible) on port 7424
- Node.js 20+
- pnpm 8+

## Environment Variables

- `MC_HOST`: Minecraft server host (default: localhost)
- `MC_PORT`: Minecraft server port (default: 25565)
- `LLM_ENDPOINT`: LLM API endpoint (default: http://localhost:7424)
- `SCENARIO_ID`: Specific scenario to run (default: all)
- `TEST_OUTPUT_DIR`: Output directory (default: ./test-output)
- `GITHUB_SHA`: Git commit hash (for attestations)
- `BUILD_VERSION`: Build version (for attestations)
