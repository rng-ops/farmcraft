# Setting Up FarmCraft LLM Documentation

This guide will help you set up the AI-powered documentation system for FarmCraft.

## Step 1: Install Ollama

### macOS

```bash
brew install ollama
```

### Linux

```bash
curl -fsSL https://ollama.com/install.sh | sh
```

### Windows

Download from [ollama.com](https://ollama.com/download)

## Step 2: Start Ollama

```bash
# Start Ollama server in background
ollama serve &

# Or use nohup for persistent background
nohup ollama serve > /tmp/ollama.log 2>&1 &
```

## Step 3: Pull the AI Model

```bash
# Install gpt-oss (recommended, fast and accurate)
ollama pull gpt-oss

# Alternative models:
# ollama pull llama2        # Llama 2 (Meta)
# ollama pull mistral       # Mistral 7B
# ollama pull codellama     # Code-focused
```

## Step 4: Verify Installation

```bash
# Check Ollama is running
curl http://localhost:11434/api/version

# Test the model
ollama run gpt-oss "What is Minecraft?"
```

## Step 5: Install Node Dependencies

```bash
cd packages/llm-docs
pnpm install
```

## Step 6: Test the Documentation Server

```bash
# Run the demo
pnpm demo

# Or start the server
pnpm dev
```

Expected output:

```
Starting FarmCraft LLM Documentation Server...
FarmCraft Docs Server running on port 7424
Ollama: http://localhost:11434
Model: gpt-oss
```

## Step 7: Launch FarmCraft

```bash
cd ../..
./quick-launch.sh
```

This will start:

- Recipe Server (7420/7421)
- MCP Server (7422/7423)
- **Documentation AI Server (7424)** ← New!
- Minecraft Server (25565)
- Minecraft Client

## Step 8: Test In-Game

Once in Minecraft:

```
/farmcraft guide
/farmcraft status
/farmcraft ask How do I unlock recipes?
/farmcraft help fertilizers
```

## Troubleshooting

### Ollama Not Starting

```bash
# Check if already running
ps aux | grep ollama

# Kill existing process
pkill ollama

# Restart
ollama serve
```

### Model Not Found

```bash
# List installed models
ollama list

# If gpt-oss not listed, install it
ollama pull gpt-oss
```

### Port 11434 Already in Use

```bash
# Find what's using it
lsof -i :11434

# Start Ollama on different port
OLLAMA_HOST=0.0.0.0:11435 ollama serve

# Update docs server config
OLLAMA_URL=http://localhost:11435 pnpm dev
```

### Documentation Server Won't Start

```bash
# Check if port 7424 is available
lsof -i :7424

# Try different port
PORT=8080 pnpm dev
```

### Commands Not Working In-Game

1. **Check server status:**

   ```
   /farmcraft status
   ```

2. **Check logs:**

   ```bash
   tail -f /tmp/docs-server.log
   ```

3. **Verify connection:**

   ```bash
   curl http://localhost:7424/health
   ```

4. **Rebuild mod:**
   ```bash
   cd mod/forge
   ./gradlew clean jar
   cp build/libs/farmcraft-1.0.0.jar run/mods/
   ```

## Performance Considerations

### Model Speed

- **gpt-oss**: Fast, good for real-time chat (1-2s)
- **llama2**: Moderate speed (2-4s)
- **mistral**: Fast, good accuracy (1-3s)

### GPU Acceleration

If you have an NVIDIA GPU:

```bash
# Check if GPU is detected
nvidia-smi

# Ollama automatically uses GPU if available
```

For AMD GPUs, check [Ollama documentation](https://github.com/ollama/ollama).

### Memory Usage

Models require RAM:

- **gpt-oss**: ~4GB
- **llama2**: ~4GB
- **mistral**: ~4GB

If low on memory, use smaller models or close other applications.

## Advanced Configuration

### Custom Model

Train or fine-tune your own model:

```bash
# Create Modelfile
cat > Modelfile << 'EOF'
FROM llama2
PARAMETER temperature 0.7
SYSTEM You are an expert on Minecraft modding and the FarmCraft mod.
EOF

# Create custom model
ollama create farmcraft-expert -f Modelfile

# Use it
OLLAMA_MODEL=farmcraft-expert pnpm dev
```

### Multiple Models

Run multiple models simultaneously:

```bash
# Pull multiple models
ollama pull gpt-oss
ollama pull codellama

# Switch models at runtime (via API)
curl -X POST http://localhost:7424/ask \
  -H "Content-Type: application/json" \
  -d '{"question": "How does PoW work?", "model": "codellama"}'
```

### API Integration

Use the REST API from other tools:

```python
import requests

def ask_farmcraft(question):
    response = requests.post('http://localhost:7424/ask',
        json={'question': question})
    return response.json()['answer']

print(ask_farmcraft("What are power foods?"))
```

## Next Steps

1. **Explore in-game commands**: Try different questions
2. **Check the knowledge base**: See what documentation is available
3. **Contribute**: Add more documentation in `src/knowledge-base.ts`
4. **Customize**: Adjust the LLM temperature, model, or prompts

## Resources

- [Ollama Documentation](https://github.com/ollama/ollama)
- [LangChain Documentation](https://js.langchain.com/)
- [FarmCraft Mod README](../../README.md)
- [Development Guide](../../docs/development.md)
