#!/bin/bash

# Stop all FarmCraft services
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo -e "${YELLOW}Stopping FarmCraft services...${NC}"

# Stop services from PID files
if [ -f /tmp/farmcraft-recipe.pid ]; then
    kill $(cat /tmp/farmcraft-recipe.pid) 2>/dev/null && echo -e "${GREEN}✓ Recipe Server stopped${NC}" || true
    rm -f /tmp/farmcraft-recipe.pid
fi

if [ -f /tmp/farmcraft-mcp.pid ]; then
    kill $(cat /tmp/farmcraft-mcp.pid) 2>/dev/null && echo -e "${GREEN}✓ MCP Server stopped${NC}" || true
    rm -f /tmp/farmcraft-mcp.pid
fi

if [ -f /tmp/farmcraft-docs.pid ]; then
    kill $(cat /tmp/farmcraft-docs.pid) 2>/dev/null && echo -e "${GREEN}✓ Docs AI Server stopped${NC}" || true
    rm -f /tmp/farmcraft-docs.pid
fi

if [ -f /tmp/farmcraft-mcserver.pid ]; then
    kill $(cat /tmp/farmcraft-mcserver.pid) 2>/dev/null && echo -e "${GREEN}✓ Minecraft Server stopped${NC}" || true
    rm -f /tmp/farmcraft-mcserver.pid
fi

# Also kill any lingering Gradle/Java processes related to FarmCraft
pkill -f "runServer" 2>/dev/null || true
pkill -f "recipe-server" 2>/dev/null || true
pkill -f "mcp-recipe-server" 2>/dev/null || true
pkill -f "llm-docs" 2>/dev/null || true

echo -e "${GREEN}✓ All services stopped${NC}"
