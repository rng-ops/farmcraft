#!/bin/bash

set -e

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${BLUE}=== FarmCraft Integrated Launcher ===${NC}"

# Set up paths
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FORGE_DIR="$SCRIPT_DIR/mod/forge"
SERVER_DIR="$FORGE_DIR/run-server"

# Set Java home
export JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || echo "/opt/homebrew/opt/openjdk@17")
export PATH="$JAVA_HOME/bin:$PATH"

echo -e "${GREEN}✓ Using Java: $JAVA_HOME${NC}"

# Create server directory if it doesn't exist
mkdir -p "$SERVER_DIR"

# Configure server properties
echo -e "${YELLOW}Configuring server...${NC}"
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

# Accept EULA
echo "eula=true" > "$SERVER_DIR/eula.txt"

# Generate servers.dat for client
echo -e "${YELLOW}Adding server to multiplayer list...${NC}"
python3 "$SCRIPT_DIR/generate-server-list.py"

echo -e "${GREEN}✓ Server configured (online-mode=false)${NC}"

# Start Recipe Server
echo -e "${YELLOW}Starting Recipe Server...${NC}"
cd "$SCRIPT_DIR"
PORT=7420 WS_PORT=7421 npx ts-node server/recipe-server/src/index.ts > /tmp/recipe-server.log 2>&1 &
RECIPE_PID=$!
echo -e "${GREEN}✓ Recipe Server started (PID: $RECIPE_PID)${NC}"

# Start MCP Server
echo -e "${YELLOW}Starting MCP Server...${NC}"
PORT=7422 DIST_PORT=7423 npx ts-node packages/mcp-recipe-server/src/http-server.ts > /tmp/mcp-server.log 2>&1 &
MCP_PID=$!
echo -e "${GREEN}✓ MCP Server started (PID: $MCP_PID)${NC}"

# Wait for servers to initialize
sleep 2

# Start Minecraft Server
echo -e "${YELLOW}Starting Minecraft Server...${NC}"
cd "$FORGE_DIR"
./gradlew runServer --no-daemon > /tmp/minecraft-server.log 2>&1 &
MC_SERVER_PID=$!
echo -e "${GREEN}✓ Minecraft Server starting (PID: $MC_SERVER_PID)${NC}"
echo -e "${BLUE}Server log: /tmp/minecraft-server.log${NC}"

# Wait for server to start (check for "Done" message)
echo -e "${YELLOW}Waiting for server to start...${NC}"
timeout=120
elapsed=0
while [ $elapsed -lt $timeout ]; do
    if grep -q "Done" /tmp/minecraft-server.log 2>/dev/null; then
        echo -e "${GREEN}✓ Server is ready!${NC}"
        break
    fi
    sleep 1
    elapsed=$((elapsed + 1))
    if [ $((elapsed % 10)) -eq 0 ]; then
        echo -e "${BLUE}Still waiting... ($elapsed seconds)${NC}"
    fi
done

if [ $elapsed -ge $timeout ]; then
    echo -e "${YELLOW}Warning: Server may still be starting. Launching client anyway...${NC}"
fi

# Launch Client with server in list
echo -e "${YELLOW}Launching Minecraft Client...${NC}"
sleep 2

# Use Gradle to launch client
cd "$FORGE_DIR"
./gradlew runClient --no-daemon 2>&1 | tee /tmp/minecraft-client.log &
MC_CLIENT_PID=$!

echo -e "${GREEN}✓ Client launched (PID: $MC_CLIENT_PID)${NC}"
echo -e "${BLUE}=== All services running ===${NC}"
echo -e "Recipe Server:    http://localhost:7420 (PID: $RECIPE_PID)"
echo -e "MCP Server:       http://localhost:7422 (PID: $MCP_PID)"
echo -e "Minecraft Server: localhost:25565     (PID: $MC_SERVER_PID)"
echo -e "Minecraft Client:                      (PID: $MC_CLIENT_PID)"
echo ""
echo -e "${YELLOW}Press Ctrl+C to stop all services${NC}"

# Cleanup function
cleanup() {
    echo -e "\n${YELLOW}Shutting down services...${NC}"
    kill $RECIPE_PID 2>/dev/null || true
    kill $MCP_PID 2>/dev/null || true
    kill $MC_SERVER_PID 2>/dev/null || true
    kill $MC_CLIENT_PID 2>/dev/null || true
    echo -e "${GREEN}✓ All services stopped${NC}"
    exit 0
}

trap cleanup INT TERM

# Wait for client to finish
wait $MC_CLIENT_PID
