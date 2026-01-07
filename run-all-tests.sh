#!/bin/bash

# FarmCraft - Complete System Test
# Launches all servers and runs end-to-end tests including mod command tests

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "================================================"
echo "FarmCraft - Complete System Test"
echo "================================================"
echo ""

# Function to check if a port is in use
check_port() {
    local port=$1
    if lsof -Pi :$port -sTCP:LISTEN -t >/dev/null 2>&1; then
        return 0
    else
        return 1
    fi
}

# Check if servers are running
echo "Checking server status..."
echo ""

RECIPE_SERVER_RUNNING=false
MCP_SERVER_RUNNING=false
DOCS_SERVER_RUNNING=false

if check_port 7420; then
    echo "✅ Recipe Server (port 7420) - Running"
    RECIPE_SERVER_RUNNING=true
else
    echo "❌ Recipe Server (port 7420) - Not running"
fi

if check_port 7422; then
    echo "✅ MCP Server (port 7422) - Running"
    MCP_SERVER_RUNNING=true
else
    echo "❌ MCP Server (port 7422) - Not running"
fi

if check_port 7424; then
    echo "✅ Docs AI Server (port 7424) - Running"
    DOCS_SERVER_RUNNING=true
else
    echo "❌ Docs AI Server (port 7424) - Not running"
fi

echo ""

if ! $RECIPE_SERVER_RUNNING || ! $MCP_SERVER_RUNNING || ! $DOCS_SERVER_RUNNING; then
    echo "⚠️  Warning: Not all servers are running"
    echo "   Some tests may fail if servers are not available"
    echo ""
    read -p "Continue anyway? (y/N) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        echo "Aborted. Start servers with: pnpm run start:all"
        exit 1
    fi
fi

echo "================================================"
echo "1. Running Mod Command Tests"
echo "================================================"
echo ""

bash "$SCRIPT_DIR/run-mod-tests.sh"
MOD_TEST_EXIT=$?

echo ""
echo "================================================"
echo "2. Running E2E Server Tests"
echo "================================================"
echo ""

cd "$SCRIPT_DIR"
pnpm run test:e2e
E2E_TEST_EXIT=$?

echo ""
echo "================================================"
echo "Complete System Test Results"
echo "================================================"
echo ""

if [ $MOD_TEST_EXIT -eq 0 ] && [ $E2E_TEST_EXIT -eq 0 ]; then
    echo "✅ All system tests passed!"
    echo ""
    echo "Components tested:"
    echo "   ✅ Mod command registration"
    echo "   ✅ Mod command execution"
    echo "   ✅ Recipe server integration"
    echo "   ✅ MCP server integration"
    echo "   ✅ Docs AI server integration"
    echo ""
    exit 0
else
    echo "❌ System tests failed!"
    echo ""
    if [ $MOD_TEST_EXIT -ne 0 ]; then
        echo "   ❌ Mod command tests failed"
    else
        echo "   ✅ Mod command tests passed"
    fi
    
    if [ $E2E_TEST_EXIT -ne 0 ]; then
        echo "   ❌ E2E server tests failed"
    else
        echo "   ✅ E2E server tests passed"
    fi
    echo ""
    exit 1
fi
