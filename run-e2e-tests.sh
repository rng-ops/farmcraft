#!/bin/bash

# FarmCraft E2E Test Runner
# Starts a test server and runs all E2E tests

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$SCRIPT_DIR"

echo "================================================"
echo "FarmCraft E2E Test Runner"
echo "================================================"
echo ""

# Check if servers are running
echo "📡 Checking server status..."

if ! lsof -Pi :7420 -sTCP:LISTEN -t >/dev/null 2>&1; then
    echo "⚠️  Recipe Server not running on port 7420"
    echo "   Starting servers..."
    cd "$PROJECT_DIR"
    pnpm run start:all &
    SERVER_PID=$!
    echo "   Waiting for servers to start..."
    sleep 10
else
    echo "✅ Recipe Server is running"
    SERVER_PID=""
fi

# Check if Minecraft server is running
MINECRAFT_RUNNING=false
if lsof -Pi :25565 -sTCP:LISTEN -t >/dev/null 2>&1; then
    echo "✅ Minecraft server is running on port 25565"
    MINECRAFT_RUNNING=true
else
    echo "⚠️  No Minecraft server detected on port 25565"
    echo "   You need to start a Minecraft server with FarmCraft mod"
    echo ""
    read -p "Continue with server tests only? (y/N) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        echo "Aborted"
        exit 1
    fi
fi

echo ""
echo "================================================"
echo "Running E2E Tests"
echo "================================================"
echo ""

# 1. Test Recipe Server
echo "1️⃣  Testing Recipe Server..."
cd "$PROJECT_DIR"
curl -s http://localhost:7420/health > /dev/null 2>&1
if [ $? -eq 0 ]; then
    echo "   ✅ Recipe Server health check passed"
else
    echo "   ❌ Recipe Server health check failed"
fi

# 2. Test MCP Server
echo "2️⃣  Testing MCP Server..."
curl -s http://localhost:7422/health > /dev/null 2>&1
if [ $? -eq 0 ]; then
    echo "   ✅ MCP Server health check passed"
else
    echo "   ❌ MCP Server health check failed"
fi

# 3. Test Docs AI Server
echo "3️⃣  Testing Docs AI Server..."
curl -s http://localhost:7424/health > /dev/null 2>&1
if [ $? -eq 0 ]; then
    echo "   ✅ Docs AI Server health check passed"
else
    echo "   ❌ Docs AI Server health check failed"
fi

# 4. Run E2E Client Tests
echo "4️⃣  Running E2E Client Tests..."
cd "$PROJECT_DIR/tools/e2e-client"
if [ -f "dist/e2e-test.js" ]; then
    node dist/e2e-test.js || echo "   ⚠️  Some E2E tests failed"
else
    echo "   ⚠️  E2E client not built, skipping"
fi

# 5. Run Test Bot (if Minecraft server is running)
if [ "$MINECRAFT_RUNNING" = true ]; then
    echo "5️⃣  Running Test Bot..."
    cd "$PROJECT_DIR/tools/e2e-bot"
    
    if [ ! -d "node_modules" ]; then
        echo "   Installing bot dependencies..."
        pnpm install
    fi
    
    if [ ! -d "dist" ]; then
        echo "   Building bot..."
        pnpm build
    fi
    
    echo "   Connecting to Minecraft server..."
    node dist/test-bot.js localhost 25565 || echo "   ⚠️  Bot tests encountered errors"
else
    echo "5️⃣  Skipping Test Bot (no Minecraft server)"
fi

echo ""
echo "================================================"
echo "Test Summary"
echo "================================================"
echo ""

# Cleanup
if [ -n "$SERVER_PID" ]; then
    echo "🧹 Stopping test servers..."
    kill $SERVER_PID 2>/dev/null || true
fi

echo "✅ E2E test run complete"
echo ""
echo "For detailed results, check:"
echo "  - Server logs in terminal output above"
echo "  - Test reports in tools/*/build/reports/"
echo ""
