# FarmCraft LLM Documentation System

AI-powered in-game documentation using Ollama and LangChain.

## Features

- **In-Game Commands**: Ask questions directly in Minecraft chat
- **LLM Integration**: Uses Ollama with gpt-oss model for natural language queries
- **Comprehensive Docs**: Covers recipes, proof-of-work, fertilizers, power foods, troubleshooting
- **REST API**: Query documentation from any client
- **Smart Caching**: Quick answers for common questions

## Prerequisites

### Install Ollama

```bash
# macOS
brew install ollama

# Start Ollama server
ollama serve
```

### Install gpt-oss Model

```bash
ollama pull gpt-oss
```

> **Note**: You can use other models like `llama2`, `mistral`, or `codellama` by changing the `OLLAMA_MODEL` environment variable.

## Quick Start

### 1. Install Dependencies

```bash
cd packages/llm-docs
pnpm install
```

### 2. Start the Documentation Server

```bash
# Standalone
pnpm dev

# Or as part of full launch
cd ../..
./quick-launch.sh
```

The server will start on port **7424** by default.

### 3. Test the API

```bash
# Health check
curl http://localhost:7424/health

# Ask a question
curl -X POST http://localhost:7424/ask \
  -H "Content-Type: application/json" \
  -d '{"question": "How do I unlock recipes?"}'

# Get a specific topic
curl http://localhost:7424/docs/fertilizers

# Search documentation
curl http://localhost:7424/search?q=proof+of+work
```

### 4. Use In-Game Commands

Once in Minecraft with FarmCraft mod loaded:

```
/farmcraft guide          - Show command list
/farmcraft status         - Check connection status
/farmcraft help recipes   - Get documentation on recipes
/farmcraft ask How do I unlock new recipes?
/farmcraft topics         - List all documentation topics
```

## API Endpoints

### POST /ask

Ask a question to the LLM assistant.

**Request:**

```json
{
  "question": "How do I use fertilizers?"
}
```

**Response:**

```json
{
  "question": "How do I use fertilizers?",
  "answer": "Right-click on farmland with fertilizer in hand. The farmland will show green particles and crops will grow 1.5-3x faster depending on fertilizer type..."
}
```

### GET /docs/:topic

Get full documentation for a specific topic.

**Topics:**

- `overview` - Mod overview and getting started
- `commands` - All available commands
- `recipeSystem` - How recipe discovery works
- `proofOfWork` - PoW challenge system
- `fertilizers` - Fertilizer types and usage
- `powerFoods` - Power food items and effects
- `troubleshooting` - Common issues and solutions
- `configuration` - Config file reference
- `development` - Development and testing guide

### GET /search?q=query

Search documentation for relevant topics.

**Response:**

```json
{
  "query": "fertilizer",
  "results": ["fertilizers", "troubleshooting", "recipeSystem"]
}
```

### GET /topics

List all available documentation topics.

### GET /health

Check server health and Ollama connection status.

## Configuration

### Environment Variables

- `PORT` - Server port (default: 7424)
- `OLLAMA_URL` - Ollama server URL (default: http://localhost:11434)
- `OLLAMA_MODEL` - Model to use (default: gpt-oss)

### Example

```bash
PORT=8080 OLLAMA_MODEL=llama2 pnpm dev
```

## Architecture

```
┌─────────────────┐
│  Minecraft Mod  │
│   (FarmCraft)   │
└────────┬────────┘
         │ HTTP Requests
         │ /farmcraft commands
         ▼
┌─────────────────┐
│   Docs Server   │
│   (Express)     │
│   Port 7424     │
└────────┬────────┘
         │ LangChain
         ▼
┌─────────────────┐
│     Ollama      │
│   (gpt-oss)     │
│   Port 11434    │
└─────────────────┘
```

### Knowledge Base

The knowledge base is defined in [src/knowledge-base.ts](./src/knowledge-base.ts) and includes:

- Detailed documentation for all mod features
- Quick answers for common questions
- Troubleshooting guides
- Configuration examples
- Development/testing instructions

### LLM Integration

Uses LangChain's `ChatOllama` integration for:

- Natural language question answering
- Context-aware responses
- Markdown formatting preservation
- Fallback to quick answers for performance

## Development

### Run Tests

```bash
pnpm demo
```

This will test:

- Ollama connection
- Sample questions and answers
- Topic retrieval
- Search functionality

### Add New Documentation

Edit `src/knowledge-base.ts`:

```typescript
export const FARMCRAFT_DOCS = {
  // Add new topic
  myNewTopic: `
# My New Topic

Content goes here...
  `,

  // Existing topics...
};
```

### Add Quick Answers

For common questions that don't need LLM:

```typescript
export const QUICK_ANSWERS = {
  'my keyword': 'Quick answer for this topic',
  // ...
};
```

## Troubleshooting

### "Ollama not available"

1. Check if Ollama is running: `ps aux | grep ollama`
2. Start Ollama: `ollama serve`
3. Test: `curl http://localhost:11434/api/version`

### "Model not found"

Install the model:

```bash
ollama pull gpt-oss
# or
ollama pull llama2
```

### "Documentation server not responding"

1. Check if port 7424 is in use: `lsof -i :7424`
2. Check logs: `tail -f /tmp/docs-server.log`
3. Restart: `./stop-services.sh && ./quick-launch.sh`

### "In-game commands not working"

1. Check connection: `/farmcraft status`
2. Verify server is running: `curl http://localhost:7424/health`
3. Check mod logs for errors

## Performance

- **Quick Answers**: <10ms (cached responses)
- **LLM Queries**: 1-3 seconds (depends on model and question complexity)
- **Topic Retrieval**: <50ms (static content)

## License

Same as parent FarmCraft project.
