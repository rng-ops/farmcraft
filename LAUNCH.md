# FarmCraft Launch Scripts

Three scripts to manage the complete FarmCraft development environment.

## Quick Start

```bash
# Launch everything (recommended)
./quick-launch.sh

# Or use the full launcher with server wait
./launch-game.sh

# Stop all services
./stop-services.sh
```

## Scripts

### `quick-launch.sh` (Recommended)

Launches all services in parallel and auto-connects the client to the server:

1. **Recipe Server** → http://localhost:7420 (WebSocket: 7421)
2. **MCP Server** → http://localhost:7422 (Distribution: 7423)
3. **Minecraft Server** → localhost:25565 (online-mode=false)
4. **Minecraft Client** → Auto-connects to server

**Features:**

- Server configured with authentication off
- Creative mode, peaceful difficulty
- Flight enabled
- Client auto-connects after 5 seconds
- All logs saved to `/tmp/`

**Logs:**

- Recipe Server: `/tmp/recipe-server.log`
- MCP Server: `/tmp/mcp-server.log`
- Minecraft Server: `/tmp/minecraft-server.log`
- Minecraft Client: `/tmp/minecraft-client.log`

### `launch-game.sh`

Full launcher that waits for the server to be ready before launching the client. Takes longer but ensures the server is up.

### `stop-services.sh`

Stops all running FarmCraft services (Recipe Server, MCP Server, Minecraft Server).

## Server Configuration

The scripts automatically configure the Minecraft server with:

```properties
online-mode=false          # No authentication required
server-port=25565         # Default Minecraft port
gamemode=creative         # Creative mode
difficulty=peaceful       # Peaceful difficulty
spawn-protection=0        # No spawn protection
max-players=20           # Up to 20 players
enable-command-block=true # Command blocks enabled
allow-flight=true        # Flight allowed
```

## Manual Launch

If you prefer to launch components separately:

```bash
# 1. Start backend servers
cd /Users/a/projects/minecraft
PORT=7420 WS_PORT=7421 npx ts-node server/recipe-server/src/index.ts &
PORT=7422 DIST_PORT=7423 npx ts-node packages/mcp-recipe-server/src/http-server.ts &

# 2. Start Minecraft server
cd mod/forge
./gradlew runServer --no-daemon

# 3. In another terminal, start client with auto-connect
cd mod/forge
./gradlew runClient --no-daemon -Pmc.server=localhost:25565
```

## Troubleshooting

**Server won't start:**

- Check `/tmp/minecraft-server.log` for errors
- Ensure port 25565 is not in use: `lsof -i :25565`

**Client won't connect:**

- Verify server is running: `tail -f /tmp/minecraft-server.log`
- Look for "Done" message indicating server is ready
- Check client connected to correct address in multiplayer menu

**Services still running after client closes:**

- Run `./stop-services.sh` to stop all services
- Or manually: `kill $(cat /tmp/farmcraft-*.pid)`

**Recipe/MCP servers not responding:**

- Check logs in `/tmp/recipe-server.log` and `/tmp/mcp-server.log`
- Verify ports are free: `lsof -i :7420,7421,7422,7423`
