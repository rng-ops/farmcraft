#!/bin/bash
# FarmCraft Server Launcher
# Starts both the Recipe Server and MCP Server

# Get absolute path to script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "Starting FarmCraft Servers..."

# Kill any existing servers on our ports
lsof -ti:7420 | xargs kill -9 2>/dev/null
lsof -ti:7421 | xargs kill -9 2>/dev/null
lsof -ti:7422 | xargs kill -9 2>/dev/null
lsof -ti:7423 | xargs kill -9 2>/dev/null

# Start Recipe Server (HTTP on 7420, WebSocket on 7421)
echo "Starting Recipe Server on ports 7420/7421..."
cd "$SCRIPT_DIR/server/recipe-server"
PORT=7420 WS_PORT=7421 npx ts-node src/index.ts &
RECIPE_PID=$!

# Give it a moment to start
sleep 2

# Start MCP Server (HTTP on 7422, Distribution on 7423)
echo "Starting MCP Server on ports 7422/7423..."
cd "$SCRIPT_DIR/packages/mcp-recipe-server"
PORT=7422 DIST_PORT=7423 npx ts-node src/http-server.ts &
MCP_PID=$!

echo ""
echo "==================================="
echo "FarmCraft Servers Started!"
echo "==================================="
echo "Recipe Server:  http://localhost:7420"
echo "WebSocket:      ws://localhost:7421"
echo "MCP Server:     http://localhost:7422"
echo "Distribution:   http://localhost:7423"
echo ""
echo "Press Ctrl+C to stop all servers"
echo "==================================="

# Wait for both processes
trap "kill $RECIPE_PID $MCP_PID 2>/dev/null" EXIT
wait
