#!/bin/bash

# FarmCraft Mod - Test Runner Script
# This script runs all integration tests for the mod commands

set -e  # Exit on error

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MOD_DIR="$SCRIPT_DIR/mod/forge"

echo "================================================"
echo "FarmCraft Mod - Integration Test Suite"
echo "================================================"
echo ""

# Check for Java
if ! command -v java &> /dev/null; then
    echo "❌ Error: Java is not installed or not in PATH"
    echo "   Please install Java 17 or higher"
    echo "   Visit: https://adoptium.net/"
    exit 1
fi

JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
if [ "$JAVA_VERSION" -lt 17 ]; then
    echo "❌ Error: Java 17 or higher is required (found Java $JAVA_VERSION)"
    exit 1
fi

echo "✅ Java version: $(java -version 2>&1 | head -n 1)"
echo ""

# Navigate to mod directory
cd "$MOD_DIR"

echo "================================================"
echo "Running Command Integration Tests"
echo "================================================"
echo ""

# Run tests with detailed output
./gradlew test --info --console=plain 2>&1 | tee test-output.log

TEST_EXIT_CODE=$?

echo ""
echo "================================================"
echo "Running Command Validation Tests"
echo "================================================"
echo ""

./gradlew validateCommands --console=plain 2>&1 | tee -a test-output.log

VALIDATE_EXIT_CODE=$?

echo ""
echo "================================================"
echo "Test Results Summary"
echo "================================================"
echo ""

if [ $TEST_EXIT_CODE -eq 0 ] && [ $VALIDATE_EXIT_CODE -eq 0 ]; then
    echo "✅ All tests passed successfully!"
    echo ""
    echo "Test reports available at:"
    echo "   HTML: file://$MOD_DIR/build/reports/tests/test/index.html"
    echo "   XML:  $MOD_DIR/build/test-results/test/"
    echo ""
    exit 0
else
    echo "❌ Tests failed!"
    echo ""
    echo "Check the following for details:"
    echo "   Test log:    $MOD_DIR/test-output.log"
    echo "   HTML report: file://$MOD_DIR/build/reports/tests/test/index.html"
    echo "   XML results: $MOD_DIR/build/test-results/test/"
    echo ""
    exit 1
fi
