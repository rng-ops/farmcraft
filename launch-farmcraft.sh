#!/bin/bash
# FarmCraft Game Launcher
# Starts servers and launches Minecraft with the correct profile

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MINECRAFT_VERSION="1.20.4"
FORGE_VERSION="49.0.30"

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${BLUE}"
echo "╔════════════════════════════════════════╗"
echo "║       🌾 FarmCraft Launcher 🌾         ║"
echo "╚════════════════════════════════════════╝"
echo -e "${NC}"

# Detect Minecraft directory
if [[ "$OSTYPE" == "darwin"* ]]; then
    MINECRAFT_DIR="$HOME/Library/Application Support/minecraft"
    LAUNCHER_CMD="open -a Minecraft"
elif [[ "$OSTYPE" == "linux-gnu"* ]]; then
    MINECRAFT_DIR="$HOME/.minecraft"
    LAUNCHER_CMD="minecraft-launcher"
else
    MINECRAFT_DIR="$APPDATA/.minecraft"
    LAUNCHER_CMD="MinecraftLauncher.exe"
fi

# Start backend servers in background
echo -e "${YELLOW}Starting FarmCraft servers...${NC}"

# Kill any existing servers
pkill -f "ts-node.*recipe-server" 2>/dev/null || true
pkill -f "ts-node.*mcp-recipe-server" 2>/dev/null || true
sleep 1

# Start Recipe Server
cd "$SCRIPT_DIR/server/recipe-server"
PORT=7420 WS_PORT=7421 npx ts-node src/index.ts > /tmp/farmcraft-recipe.log 2>&1 &
RECIPE_PID=$!

# Start MCP Server
cd "$SCRIPT_DIR/packages/mcp-recipe-server"
PORT=7422 DIST_PORT=7423 npx ts-node src/http-server.ts > /tmp/farmcraft-mcp.log 2>&1 &
MCP_PID=$!

cd "$SCRIPT_DIR"

# Wait for servers to initialize
echo "Waiting for servers to start..."
sleep 3

# Check server health
check_server() {
    curl -s -o /dev/null -w "%{http_code}" "http://localhost:$1" 2>/dev/null || echo "000"
}

RECIPE_STATUS=$(check_server 7420)
MCP_STATUS=$(check_server 7422)

if [ "$RECIPE_STATUS" = "200" ] || [ "$RECIPE_STATUS" = "404" ]; then
    echo -e "${GREEN}✓ Recipe Server running (port 7420/7421)${NC}"
else
    echo -e "${YELLOW}⚠ Recipe Server may still be starting...${NC}"
fi

if [ "$MCP_STATUS" = "200" ] || [ "$MCP_STATUS" = "404" ]; then
    echo -e "${GREEN}✓ MCP Server running (port 7422/7423)${NC}"
else
    echo -e "${YELLOW}⚠ MCP Server may still be starting...${NC}"
fi

# Create/update launcher profile for auto-select
PROFILES_FILE="$MINECRAFT_DIR/launcher_profiles.json"
if [ -f "$PROFILES_FILE" ]; then
    # Check if our profile exists
    if ! grep -q "farmcraft-forge" "$PROFILES_FILE" 2>/dev/null; then
        echo -e "${YELLOW}Note: Select 'Forge' profile manually in the launcher${NC}"
    fi
fi

echo ""
echo -e "${GREEN}════════════════════════════════════════════════${NC}"
echo -e "${GREEN}  Servers Ready! Starting Minecraft...${NC}"
echo -e "${GREEN}════════════════════════════════════════════════${NC}"
echo ""
echo -e "${BLUE}In the Minecraft Launcher:${NC}"
echo "  1. Select the 'Forge' or '${MINECRAFT_VERSION}-forge' profile"
echo "  2. Click 'Play'"
echo "  3. Create a new world or join existing"
echo ""
echo -e "${BLUE}In-Game Testing:${NC}"
echo "  • Press E and search for 'farmcraft'"
echo "  • Craft fertilizers with Diorite/Calcite/Tuff/Gravel + Bone Meal"
echo "  • Right-click farmland to apply fertilizer"
echo "  • Harvest crops for Power Foods!"
echo ""
echo -e "${YELLOW}Logs:${NC}"
echo "  Recipe Server: /tmp/farmcraft-recipe.log"
echo "  MCP Server:    /tmp/farmcraft-mcp.log"
echo ""
echo -e "${YELLOW}Press Ctrl+C to stop servers when done${NC}"
echo ""

# Launch Minecraft
if [[ "$OSTYPE" == "darwin"* ]]; then
    if [ -d "/Applications/Minecraft.app" ]; then
        open -a "Minecraft"
    else
        echo -e "${YELLOW}Minecraft.app not found. Please launch manually.${NC}"
        echo "Download from: https://www.minecraft.net/download"
    fi
elif command -v minecraft-launcher &> /dev/null; then
    minecraft-launcher &
fi

# Cleanup on exit
cleanup() {
    echo ""
    echo "Stopping servers..."
    kill $RECIPE_PID 2>/dev/null || true
    kill $MCP_PID 2>/dev/null || true
    echo "Goodbye!"
}
trap cleanup EXIT

# Keep script running to maintain servers
wait
