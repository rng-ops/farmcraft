# Quick Test Guide: FarmCraft AI Documentation

## Prerequisites Check

```bash
# 1. Check Ollama is installed
which ollama

# 2. Check if Ollama is running
ps aux | grep ollama

# 3. If not running, start it
ollama serve &

# 4. Check gpt-oss model is installed
ollama list | grep gpt-oss

# 5. If not installed, pull it
ollama pull gpt-oss
```

## Quick Launch

```bash
cd /Users/a/projects/minecraft
./quick-launch.sh
```

Wait for all services to start. You should see:

```
✓ Server configured
Starting Recipe Server...
Starting MCP Server...
Starting Documentation AI Server...  ← New!
Starting Minecraft Server...
Launching Client...
```

## In-Game Testing

### 1. Basic Commands

Join the server and try:

```
/farmcraft guide
```

Expected output:

```
[FarmCraft] FarmCraft In-Game Guide
[FarmCraft] Use these commands to learn about the mod:
[FarmCraft]
[FarmCraft] /farmcraft guide - Show this guide
[FarmCraft] /farmcraft status - Check connection & progress
[FarmCraft] /farmcraft help <topic> - Get help on a topic
[FarmCraft] /farmcraft ask <question> - Ask the AI assistant
[FarmCraft] /farmcraft topics - List all documentation topics
```

### 2. Check Status

```
/farmcraft status
```

Expected output:

```
[FarmCraft] FarmCraft Status
[FarmCraft] Recipe Server: Checking...
[FarmCraft] Documentation AI: ✓ Available
```

### 3. List Topics

```
/farmcraft topics
```

Expected output:

```
[FarmCraft] Available Documentation Topics:
[FarmCraft]   • overview - /farmcraft help overview
[FarmCraft]   • commands - /farmcraft help commands
[FarmCraft]   • recipeSystem - /farmcraft help recipeSystem
[FarmCraft]   • proofOfWork - /farmcraft help proofOfWork
[FarmCraft]   • fertilizers - /farmcraft help fertilizers
[FarmCraft]   • powerFoods - /farmcraft help powerFoods
[FarmCraft]   • troubleshooting - /farmcraft help troubleshooting
[FarmCraft]   • configuration - /farmcraft help configuration
[FarmCraft]   • development - /farmcraft help development
```

### 4. Get Specific Documentation

```
/farmcraft help fertilizers
```

Expected: Full fertilizer documentation with types, effects, crafting.

### 5. Ask Natural Language Questions

```
/farmcraft ask How do I unlock new recipes?
```

Expected: AI-generated answer explaining recipe discovery system.

```
/farmcraft ask What are power foods?
```

Expected: AI-generated answer about power food items.

```
/farmcraft ask Why isn't my fertilizer working?
```

Expected: AI-generated troubleshooting advice.

## API Testing (Outside Game)

### Test Server Health

```bash
curl http://localhost:7424/health
```

Expected:

```json
{
  "status": "ok",
  "ollama": "connected",
  "timestamp": "2026-01-07T..."
}
```

### Ask a Question

```bash
curl -X POST http://localhost:7424/ask \
  -H "Content-Type: application/json" \
  -d '{"question": "How do I unlock recipes?"}'
```

Expected:

```json
{
  "question": "How do I unlock recipes?",
  "answer": "Recipes unlock through gameplay: harvest crops, complete challenges, or reach achievements. Use /farmcraft recipes to see what you've unlocked."
}
```

### Get Documentation

```bash
curl http://localhost:7424/docs/fertilizers
```

Expected: Full markdown documentation for fertilizers.

### Search

```bash
curl http://localhost:7424/search?q=fertilizer
```

Expected:

```json
{
  "query": "fertilizer",
  "results": ["fertilizers", "troubleshooting", "recipeSystem"]
}
```

## Troubleshooting

### ❌ Commands Don't Exist

**Symptoms**: "Unknown command" when typing `/farmcraft`

**Solutions**:

1. Check mod is loaded:

   ```bash
   grep "FarmCraft initialized" mod/forge/run/logs/latest.log
   ```

2. Rebuild mod:

   ```bash
   cd mod/forge
   ./gradlew clean jar
   cp build/libs/farmcraft-1.0.0.jar run/mods/
   ```

3. Restart server and client

### ❌ "Documentation AI: ✗ Unavailable"

**Symptoms**: `/farmcraft status` shows docs server unavailable

**Solutions**:

1. Check docs server is running:

   ```bash
   lsof -i :7424
   ```

2. Check logs:

   ```bash
   tail -f /tmp/docs-server.log
   ```

3. Restart docs server:
   ```bash
   cd packages/llm-docs
   PORT=7424 pnpm dev
   ```

### ❌ AI Doesn't Answer Questions

**Symptoms**: `/farmcraft ask` returns error message

**Solutions**:

1. Check Ollama is running:

   ```bash
   ps aux | grep ollama
   ```

2. Test Ollama directly:

   ```bash
   curl http://localhost:11434/api/version
   ```

3. Check model is installed:

   ```bash
   ollama list | grep gpt-oss
   ```

4. Restart Ollama:
   ```bash
   pkill ollama
   ollama serve &
   ```

### ❌ Slow AI Responses

**Symptoms**: `/farmcraft ask` takes > 10 seconds

**Solutions**:

1. Check system resources (Ollama uses CPU/GPU)
2. Use a faster model:
   ```bash
   OLLAMA_MODEL=mistral ./quick-launch.sh
   ```
3. Check if GPU acceleration is working (NVIDIA/AMD)

## Success Indicators

✅ All services start without errors
✅ `/farmcraft guide` shows command list
✅ `/farmcraft status` shows "Documentation AI: ✓ Available"
✅ `/farmcraft topics` lists all 9 topics
✅ `/farmcraft help fertilizers` shows documentation
✅ `/farmcraft ask` questions get relevant answers
✅ `curl http://localhost:7424/health` returns 200 OK

## Performance Benchmarks

Expected response times:

- `/farmcraft guide`: Instant (< 10ms)
- `/farmcraft status`: < 100ms
- `/farmcraft topics`: < 50ms
- `/farmcraft help <topic>`: < 100ms
- `/farmcraft ask <question>`: 1-3 seconds (LLM processing)

## Next Steps

Once everything is working:

1. **Explore topics**: Try `/farmcraft help` with different topics
2. **Ask diverse questions**: Test the AI's understanding
3. **Check accuracy**: Verify answers match mod behavior
4. **Customize**: Edit `packages/llm-docs/src/knowledge-base.ts` to add more docs
5. **Extend**: Add new commands in `FarmCraftCommand.java`

## Demo Video Script

1. Start services: `./quick-launch.sh`
2. Join server
3. Type: `/farmcraft guide`
4. Type: `/farmcraft status`
5. Type: `/farmcraft ask How does this mod work?`
6. Type: `/farmcraft help recipes`
7. Show: Working AI responses in real-time
8. Exit and show: `./stop-services.sh`

## Log Locations

- Client: `.minecraft/logs/latest.log`
- Server: `mod/forge/run-server/logs/latest.log`
- Recipe Server: `/tmp/recipe-server.log`
- MCP Server: `/tmp/mcp-server.log`
- Docs Server: `/tmp/docs-server.log`
- Ollama: Check with `ps aux | grep ollama`
