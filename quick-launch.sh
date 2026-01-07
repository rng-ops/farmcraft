#!/bin/bash

# Quick launch - starts everything in parallel without waiting
set -e

GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo -e "${BLUE}=== FarmCraft Quick Launch ===${NC}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FORGE_DIR="$SCRIPT_DIR/mod/forge"
SERVER_DIR="$FORGE_DIR/run-server"

# Set Java
export JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || echo "/opt/homebrew/opt/openjdk@17")
export PATH="$JAVA_HOME/bin:$PATH"

# Setup server directory and config
mkdir -p "$SERVER_DIR"
cat > "$SERVER_DIR/server.properties" << EOF
online-mode=false
server-port=25565
gamemode=survival
difficulty=peaceful
spawn-protection=0
max-players=20
view-distance=10
enable-command-block=true
allow-flight=true
motd=FarmCraft Development Server
EOF
echo "eula=true" > "$SERVER_DIR/eula.txt"

# Copy mod to server
mkdir -p "$SERVER_DIR/mods"
cp "$FORGE_DIR/build/libs/farmcraft-1.0.0.jar" "$SERVER_DIR/mods/" 2>/dev/null || true

# Generate servers.dat for auto-populated server list
echo -e "${YELLOW}Adding server to multiplayer list...${NC}"
python3 "$SCRIPT_DIR/generate-server-list.py"

echo -e "${GREEN}✓ Server configured${NC}"

# Start all services in background
echo -e "${YELLOW}Starting Recipe Server...${NC}"
cd "$SCRIPT_DIR"
PORT=7420 WS_PORT=7421 npx ts-node server/recipe-server/src/index.ts > /tmp/recipe-server.log 2>&1 &
echo $! > /tmp/farmcraft-recipe.pid

echo -e "${YELLOW}Starting MCP Server...${NC}"
PORT=7422 DIST_PORT=7423 npx ts-node packages/mcp-recipe-server/src/http-server.ts > /tmp/mcp-server.log 2>&1 &
echo $! > /tmp/farmcraft-mcp.pid

echo -e "${YELLOW}Starting Documentation AI Server...${NC}"
PORT=7424 OLLAMA_URL=http://localhost:11434 OLLAMA_MODEL=gpt-oss npx ts-node packages/llm-docs/src/index.ts > /tmp/docs-server.log 2>&1 &
echo $! > /tmp/farmcraft-docs.pid

echo -e "${YELLOW}Starting Minecraft Server...${NC}"
cd "$FORGE_DIR"
./gradlew runServer --no-daemon > /tmp/minecraft-server.log 2>&1 &
echo $! > /tmp/farmcraft-mcserver.pid

echo -e "${GREEN}✓ Server is starting (check /tmp/minecraft-server.log)${NC}"
echo -e "${YELLOW}Waiting 5 seconds before launching client...${NC}"
sleep 5

echo -e "${YELLOW}Launching Client...${NC}"
./gradlew runClient --no-daemon 2>&1 | tee /tmp/minecraft-client.log

# When client exits, ask if we should stop services
echo -e "\n${YELLOW}Client closed. Stop all services? (y/N)${NC}"
read -t 10 -n 1 response || response="n"
echo

if [[ "$response" =~ ^[Yy]$ ]]; then
    echo -e "${YELLOW}Stopping services...${NC}"
    [ -f /tmp/farmcraft-recipe.pid ] && kill $(cat /tmp/farmcraft-recipe.pid) 2>/dev/null || true
    [ -f /tmp/farmcraft-mcp.pid ] && kill $(cat /tmp/farmcraft-mcp.pid) 2>/dev/null || true
    [ -f /tmp/farmcraft-docs.pid ] && kill $(cat /tmp/farmcraft-docs.pid) 2>/dev/null || true
    [ -f /tmp/farmcraft-mcserver.pid ] && kill $(cat /tmp/farmcraft-mcserver.pid) 2>/dev/null || true
    rm -f /tmp/farmcraft-*.pid
    echo -e "${GREEN}✓ Services stopped${NC}"
else
    echo -e "${BLUE}Services still running. To stop them manually:${NC}"
    echo "  kill \$(cat /tmp/farmcraft-*.pid)"
fi
