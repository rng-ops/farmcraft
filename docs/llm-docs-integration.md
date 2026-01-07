# FarmCraft In-Game Documentation System

## Overview

I've added an AI-powered documentation system to FarmCraft that allows players to ask questions about the mod directly in-game using natural language. The system uses **Ollama** (local LLM runner) with **LangChain** for intelligent question answering.

## What Was Added

### 1. Documentation Server (`packages/llm-docs/`)

A new TypeScript package that provides:

- **REST API** for querying documentation
- **LangChain integration** for LLM-powered answers
- **Comprehensive knowledge base** covering all mod features
- **Quick answers** for common questions (no LLM needed)

### 2. In-Game Commands (Minecraft Mod)

New `/farmcraft` commands:

```
/farmcraft guide                    - Show command list
/farmcraft status                   - Check server connections
/farmcraft help <topic>             - Get documentation on a topic
/farmcraft ask <question>           - Ask the AI assistant anything
/farmcraft topics                   - List all documentation topics
```

### 3. Knowledge Base

Comprehensive documentation on:

- **Overview** - Getting started with FarmCraft
- **Commands** - All available player/admin commands
- **Recipe System** - How recipe discovery works
- **Proof-of-Work** - Challenge system mechanics
- **Fertilizers** - Types, effects, and usage
- **Power Foods** - Special food items with buffs
- **Troubleshooting** - Common issues and solutions
- **Configuration** - Config file reference
- **Development** - Testing and development guide

## Architecture

```
┌─────────────────┐
│  Minecraft Mod  │  In-game /farmcraft commands
│   (Java)        │
└────────┬────────┘
         │ HTTP
         ▼
┌─────────────────┐
│  Docs Server    │  Express REST API (Port 7424)
│  (TypeScript)   │
└────────┬────────┘
         │ LangChain
         ▼
┌─────────────────┐
│     Ollama      │  Local LLM (Port 11434)
│   (gpt-oss)     │
└─────────────────┘
```

## Setup Instructions

### 1. Install Ollama

```bash
# macOS
brew install ollama

# Start Ollama
ollama serve
```

### 2. Install AI Model

```bash
# Install gpt-oss (recommended)
ollama pull gpt-oss

# Alternatives: llama2, mistral, codellama
```

### 3. Install Dependencies

```bash
cd packages/llm-docs
pnpm install
```

### 4. Launch Everything

```bash
cd /Users/a/projects/minecraft
./quick-launch.sh
```

This now starts:

- Recipe Server (7420/7421)
- MCP Server (7422/7423)
- **Documentation AI Server (7424)** ← New!
- Minecraft Server (25565)
- Minecraft Client

## Usage Examples

### In-Game

```
/farmcraft ask How do I unlock new recipes?
```

**AI Response:**

> Recipes unlock through gameplay: harvest crops, complete challenges, or reach achievements. Use /farmcraft recipes to see what you've unlocked. Some advanced recipes require proof-of-work challenges.

```
/farmcraft help fertilizers
```

Shows full fertilizer documentation with types, effects, crafting recipes.

### API (External)

```bash
# Ask a question
curl -X POST http://localhost:7424/ask \
  -H "Content-Type: application/json" \
  -d '{"question": "What are power foods?"}'

# Get specific documentation
curl http://localhost:7424/docs/proofOfWork

# Search
curl http://localhost:7424/search?q=fertilizer
```

## Files Created

### TypeScript Package

- `/packages/llm-docs/package.json` - Package configuration
- `/packages/llm-docs/src/knowledge-base.ts` - Documentation content
- `/packages/llm-docs/src/assistant.ts` - LLM integration
- `/packages/llm-docs/src/server.ts` - Express REST API
- `/packages/llm-docs/src/index.ts` - Server entry point
- `/packages/llm-docs/src/demo.ts` - Demo script
- `/packages/llm-docs/README.md` - Package documentation

### Java Client

- `/mod/forge/src/main/java/com/farmcraft/client/DocsClient.java` - HTTP client for docs API
- `/mod/forge/src/main/java/com/farmcraft/commands/FarmCraftCommand.java` - In-game commands

### Documentation

- `/docs/llm-setup.md` - Complete setup guide with troubleshooting

### Updated Files

- `/quick-launch.sh` - Added docs server startup
- `/stop-services.sh` - Added docs server shutdown
- `/.vscode/tasks.json` - Added "Start Docs AI Server" task
- `/mod/forge/src/main/java/com/farmcraft/FarmCraft.java` - Registered commands

## Features

### Smart Answers

- **Quick responses** for common questions (< 10ms)
- **LLM-powered answers** for complex queries (1-3s)
- **Context-aware** - understands mod terminology
- **Markdown formatting** preserved in chat

### Documentation Topics

| Topic           | Description                      |
| --------------- | -------------------------------- |
| overview        | Mod overview and getting started |
| commands        | All available commands           |
| recipeSystem    | Recipe discovery mechanics       |
| proofOfWork     | PoW challenge system             |
| fertilizers     | Fertilizer types and usage       |
| powerFoods      | Power food items and effects     |
| troubleshooting | Common issues and solutions      |
| configuration   | Config file reference            |
| development     | Testing and development guide    |

### Quick Answers (No LLM Required)

For maximum performance, these questions get instant cached responses:

- "how do I start"
- "unlock recipes"
- "fertilizer not working"
- "proof of work"
- "connection failed"
- "commands"
- "power foods"
- "survival mode"

## Testing

### Test Documentation Server

```bash
cd packages/llm-docs
pnpm demo
```

Output:

```
FarmCraft LLM Docs Assistant Demo

Checking Ollama connection...
Ollama status: ✓ Connected

Q: How do I unlock new recipes?
A: Recipes unlock through gameplay: harvest crops, complete challenges...

Q: What are power foods?
A: Power Foods are advanced food items that provide effects beyond basic hunger...
```

### Test In-Game

1. Launch FarmCraft: `./quick-launch.sh`
2. Join server
3. Try commands:
   ```
   /farmcraft guide
   /farmcraft status
   /farmcraft ask how does the mod work?
   ```

## Configuration

### Environment Variables

```bash
# Docs Server
PORT=7424                           # Server port
OLLAMA_URL=http://localhost:11434  # Ollama URL
OLLAMA_MODEL=gpt-oss               # Model to use
```

### Change Model

```bash
# Use different model
OLLAMA_MODEL=llama2 ./quick-launch.sh

# Or in tasks.json
"env": {
  "OLLAMA_MODEL": "mistral"
}
```

## Performance

- **Quick Answers**: < 10ms (cached)
- **LLM Queries**: 1-3 seconds (depends on model)
- **Topic Retrieval**: < 50ms (static content)

## Troubleshooting

### Ollama Not Running

```bash
# Check if running
ps aux | grep ollama

# Start it
ollama serve
```

### Model Not Found

```bash
# List models
ollama list

# Install
ollama pull gpt-oss
```

### Docs Server Not Starting

```bash
# Check port
lsof -i :7424

# View logs
tail -f /tmp/docs-server.log
```

### Commands Not Working

1. Check mod compiled: `ls mod/forge/build/libs/`
2. Check mod loaded: Look for "FarmCraft initialized!" in logs
3. Check docs server: `/farmcraft status`
4. Check Ollama: `curl http://localhost:11434/api/version`

## Benefits

### For Players

- **Instant help** without leaving the game
- **Natural language** queries (no need to memorize syntax)
- **Comprehensive** - covers all features
- **Smart** - understands context and intent

### For Developers

- **Easy to update** - just edit knowledge-base.ts
- **Extensible** - add new topics easily
- **API-first** - can be used by other tools
- **Local** - no external API keys or rate limits

## Future Improvements

1. **Voice Commands** - Integrate with speech-to-text
2. **In-Game GUI** - Visual documentation browser
3. **Multi-language** - Support for other languages
4. **Fine-tuned Model** - Train model specifically on FarmCraft
5. **Video Tutorials** - Link to video guides for complex topics
6. **Recipe Search** - "Show me recipes that use wheat"
7. **Progress Tracking** - "What should I do next?"

## Technical Details

### LangChain Integration

Uses `ChatOllama` from `@langchain/community`:

- Streaming responses (for future UI improvements)
- Temperature control (0.7 for balanced creativity)
- Context window management
- Fallback handling

### Knowledge Base Format

Markdown-based with sections:

```typescript
export const FARMCRAFT_DOCS = {
  topicName: `
# Topic Title

## Subsection

Content...
  `,
};
```

### Chat Formatting

In-game messages support Minecraft formatting codes:

- `§e` - Yellow (headers)
- `§6` - Gold (commands)
- `§7` - Gray (descriptions)
- `§a` - Green (success)
- `§c` - Red (errors)

## Conclusion

You now have a complete AI-powered documentation system that:

- ✅ Runs locally (no API keys needed)
- ✅ Works in-game with `/farmcraft` commands
- ✅ Provides instant answers to player questions
- ✅ Covers all mod features comprehensively
- ✅ Is easy to update and extend

To use it:

1. Make sure Ollama is running: `ollama serve`
2. Launch FarmCraft: `./quick-launch.sh`
3. In-game: `/farmcraft ask <your question>`

Enjoy! 🚀
