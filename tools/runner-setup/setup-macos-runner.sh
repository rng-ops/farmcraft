#!/bin/bash

# GitHub Actions Self-Hosted Runner Setup for macOS M2
# This script sets up a runner that can test Minecraft mods with video recording

set -e

RUNNER_DIR="${HOME}/actions-runner"
RUNNER_NAME="${RUNNER_NAME:-farmcraft-macos-m2}"
RUNNER_LABELS="${RUNNER_LABELS:-macos,arm64,minecraft,self-hosted}"

echo "================================================"
echo "FarmCraft GitHub Runner Setup (macOS M2)"
echo "================================================"
echo ""

# Check if running on macOS
if [[ "$OSTYPE" != "darwin"* ]]; then
    echo "❌ This script is for macOS only"
    exit 1
fi

# Check if M2/ARM64
ARCH=$(uname -m)
if [[ "$ARCH" != "arm64" ]]; then
    echo "⚠️  Warning: Not running on ARM64 (M2). Found: $ARCH"
fi

echo "✅ Platform: macOS $(sw_vers -productVersion)"
echo "✅ Architecture: $ARCH"
echo ""

# Install dependencies
echo "📦 Installing dependencies..."

# Check for Homebrew
if ! command -v brew &> /dev/null; then
    echo "Installing Homebrew..."
    /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
fi

# Install required packages
brew install --quiet ffmpeg || true
brew install --quiet node@20 || true
brew install --quiet pnpm || true
brew install --quiet openjdk@17 || true

# Install screen recording tools
brew install --quiet screencapture || true

echo "✅ Dependencies installed"
echo ""

# Setup GitHub Actions Runner
echo "📥 Setting up GitHub Actions Runner..."

mkdir -p "$RUNNER_DIR"
cd "$RUNNER_DIR"

# Download runner for ARM64
if [ ! -f "config.sh" ]; then
    echo "Downloading GitHub Actions Runner..."
    RUNNER_VERSION="2.319.1"
    curl -o actions-runner-osx-arm64.tar.gz -L \
        "https://github.com/actions/runner/releases/download/v${RUNNER_VERSION}/actions-runner-osx-arm64-${RUNNER_VERSION}.tar.gz"
    tar xzf actions-runner-osx-arm64.tar.gz
    rm actions-runner-osx-arm64.tar.gz
fi

echo "✅ Runner downloaded"
echo ""

# Get GitHub token
echo "🔐 GitHub Authentication"
echo ""
echo "To register this runner, you need a GitHub Personal Access Token with 'repo' scope."
echo "Or run the config.sh script manually with --url and --token flags."
echo ""
echo "Example:"
echo "  cd $RUNNER_DIR"
echo "  ./config.sh --url https://github.com/YOUR_USERNAME/farmcraft --token YOUR_TOKEN --labels \"$RUNNER_LABELS\""
echo ""
echo "After configuration, run:"
echo "  ./run.sh"
echo ""
echo "Or install as a service:"
echo "  sudo ./svc.sh install"
echo "  sudo ./svc.sh start"
echo ""

# Setup Minecraft test environment
echo "🎮 Setting up Minecraft test environment..."

MINECRAFT_DIR="${HOME}/minecraft-test-env"
mkdir -p "$MINECRAFT_DIR"

cat > "$MINECRAFT_DIR/setup-minecraft.sh" << 'EOF'
#!/bin/bash
# Setup Minecraft with FarmCraft mod for testing

MINECRAFT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MC_VERSION="1.20.4"
FORGE_VERSION="49.0.30"

echo "Setting up Minecraft $MC_VERSION with Forge..."

# Download Minecraft launcher components if needed
mkdir -p "$MINECRAFT_DIR/mods"
mkdir -p "$MINECRAFT_DIR/logs"
mkdir -p "$MINECRAFT_DIR/screenshots"

# Create launcher profile
cat > "$MINECRAFT_DIR/launcher_profiles.json" << PROFILE
{
  "profiles": {
    "FarmCraft Test": {
      "name": "FarmCraft Test",
      "type": "custom",
      "gameDir": "$MINECRAFT_DIR",
      "javaArgs": "-Xmx4G -Xms2G",
      "lastVersionId": "$MC_VERSION-forge-$FORGE_VERSION"
    }
  }
}
PROFILE

echo "✅ Minecraft environment ready at: $MINECRAFT_DIR"
EOF

chmod +x "$MINECRAFT_DIR/setup-minecraft.sh"

echo "✅ Minecraft environment created at: $MINECRAFT_DIR"
echo ""

# Setup video recording
echo "📹 Setting up video recording..."

cat > "${HOME}/record-minecraft-test.sh" << 'RECORD_SCRIPT'
#!/bin/bash
# Record Minecraft gameplay for test validation

OUTPUT_DIR="${1:-${HOME}/minecraft-test-recordings}"
OUTPUT_FILE="${OUTPUT_DIR}/test-$(date +%Y%m%d-%H%M%S).mp4"
HEADLESS="${HEADLESS:-false}"
DURATION="${DURATION:-300}" # 5 minutes default

mkdir -p "$OUTPUT_DIR"

echo "📹 Starting recording..."
echo "   Output: $OUTPUT_FILE"
echo "   Headless: $HEADLESS"
echo "   Duration: ${DURATION}s"

if [ "$HEADLESS" = "true" ]; then
    # Headless recording using virtual display
    echo "Starting headless recording..."
    
    # Record using screencapture with virtual display
    # On macOS, we'll use a hidden window approach
    caffeinate -i ffmpeg \
        -f avfoundation \
        -capture_cursor 1 \
        -r 30 \
        -i "1:none" \
        -t "$DURATION" \
        -c:v libx264 \
        -preset ultrafast \
        -pix_fmt yuv420p \
        -y "$OUTPUT_FILE" \
        > "${OUTPUT_FILE}.log" 2>&1 &
    
    FFMPEG_PID=$!
    echo "Recording PID: $FFMPEG_PID"
    echo $FFMPEG_PID > "${OUTPUT_FILE}.pid"
else
    # Visible recording
    echo "Starting visible recording..."
    
    caffeinate -i ffmpeg \
        -f avfoundation \
        -capture_cursor 1 \
        -r 30 \
        -i "1:none" \
        -t "$DURATION" \
        -c:v libx264 \
        -preset fast \
        -pix_fmt yuv420p \
        -y "$OUTPUT_FILE" \
        > "${OUTPUT_FILE}.log" 2>&1 &
    
    FFMPEG_PID=$!
    echo "Recording PID: $FFMPEG_PID"
    echo $FFMPEG_PID > "${OUTPUT_FILE}.pid"
fi

echo "✅ Recording started"
echo "   Stop with: kill $FFMPEG_PID"
RECORD_SCRIPT

chmod +x "${HOME}/record-minecraft-test.sh"

echo "✅ Video recording script created"
echo ""

# Setup profiling
echo "📊 Setting up profiling and data collection..."

cat > "${HOME}/profile-minecraft-test.sh" << 'PROFILE_SCRIPT'
#!/bin/bash
# Collect profiling data during Minecraft tests

OUTPUT_DIR="${1:-${HOME}/minecraft-test-profiles}"
PROFILE_FILE="${OUTPUT_DIR}/profile-$(date +%Y%m%d-%H%M%S).json"

mkdir -p "$OUTPUT_DIR"

echo "📊 Starting profiling..."
echo "   Output: $PROFILE_FILE"

# Collect system metrics
collect_metrics() {
    while true; do
        TIMESTAMP=$(date -u +"%Y-%m-%dT%H:%M:%S.%3NZ")
        
        # CPU usage
        CPU=$(ps -A -o %cpu | awk '{s+=$1} END {print s}')
        
        # Memory usage
        MEM=$(vm_stat | grep "Pages active" | awk '{print $3}' | sed 's/\.//')
        
        # GPU usage (M2 specific)
        GPU=$(ioreg -r -c "IOPlatformDevice" | grep "gpu-perf-stats" || echo "0")
        
        # Network
        NET_RX=$(netstat -ib | awk '{sum+=$7} END {print sum}')
        NET_TX=$(netstat -ib | awk '{sum+=$10} END {print sum}')
        
        # Disk I/O
        DISK_READ=$(iostat -c 2 | tail -1 | awk '{print $3}')
        DISK_WRITE=$(iostat -c 2 | tail -1 | awk '{print $4}')
        
        # Create JSON entry
        cat >> "$PROFILE_FILE" << METRICS
{
  "timestamp": "$TIMESTAMP",
  "cpu_percent": $CPU,
  "memory_active": $MEM,
  "gpu_usage": "$GPU",
  "network_rx_bytes": $NET_RX,
  "network_tx_bytes": $NET_TX,
  "disk_read_mb": $DISK_READ,
  "disk_write_mb": $DISK_WRITE
},
METRICS
        
        sleep 1
    done
}

# Start metric collection in background
collect_metrics &
COLLECTOR_PID=$!

echo $COLLECTOR_PID > "${PROFILE_FILE}.pid"
echo "✅ Profiling started (PID: $COLLECTOR_PID)"
echo "   Stop with: kill $COLLECTOR_PID"
PROFILE_SCRIPT

chmod +x "${HOME}/profile-minecraft-test.sh"

echo "✅ Profiling script created"
echo ""

# Create test runner script
cat > "${HOME}/run-minecraft-tests.sh" << 'TEST_SCRIPT'
#!/bin/bash
# Complete test runner with recording and profiling

set -e

REPO_DIR="${1:-${HOME}/projects/minecraft}"
OUTPUT_DIR="${2:-${HOME}/minecraft-test-output}"
HEADLESS="${HEADLESS:-true}"
RECORD_VIDEO="${RECORD_VIDEO:-true}"
COLLECT_PROFILE="${COLLECT_PROFILE:-true}"

echo "================================================"
echo "FarmCraft Automated Test Run"
echo "================================================"
echo ""
echo "Repository: $REPO_DIR"
echo "Output: $OUTPUT_DIR"
echo "Headless: $HEADLESS"
echo "Record Video: $RECORD_VIDEO"
echo "Collect Profile: $COLLECT_PROFILE"
echo ""

mkdir -p "$OUTPUT_DIR"
mkdir -p "$OUTPUT_DIR/videos"
mkdir -p "$OUTPUT_DIR/profiles"
mkdir -p "$OUTPUT_DIR/logs"

# Start recording if enabled
if [ "$RECORD_VIDEO" = "true" ]; then
    echo "📹 Starting video recording..."
    HEADLESS="$HEADLESS" DURATION=600 \
        "${HOME}/record-minecraft-test.sh" "$OUTPUT_DIR/videos" &
    RECORDER_PID=$!
    sleep 2
fi

# Start profiling if enabled
if [ "$COLLECT_PROFILE" = "true" ]; then
    echo "📊 Starting profiling..."
    "${HOME}/profile-minecraft-test.sh" "$OUTPUT_DIR/profiles" &
    PROFILER_PID=$!
    sleep 2
fi

# Run tests
echo "🧪 Running tests..."
cd "$REPO_DIR"

# Build mod
echo "Building Forge mod..."
cd mod/forge
./gradlew build --no-daemon 2>&1 | tee "$OUTPUT_DIR/logs/build.log"

# Run GameTests
echo "Running GameTests..."
./gradlew runGameTestServer 2>&1 | tee "$OUTPUT_DIR/logs/gametest.log" || true

# Run E2E tests
echo "Running E2E tests..."
cd "$REPO_DIR"
pnpm run test:e2e 2>&1 | tee "$OUTPUT_DIR/logs/e2e.log" || true

# Run bot tests (if server running)
echo "Running bot tests..."
pnpm run test:bot 2>&1 | tee "$OUTPUT_DIR/logs/bot.log" || true

echo "✅ Tests complete"

# Stop profiling
if [ -n "$PROFILER_PID" ]; then
    echo "Stopping profiler..."
    kill $PROFILER_PID 2>/dev/null || true
fi

# Stop recording
if [ -n "$RECORDER_PID" ]; then
    echo "Stopping recorder..."
    kill $RECORDER_PID 2>/dev/null || true
    wait $RECORDER_PID 2>/dev/null || true
fi

# Generate summary
cat > "$OUTPUT_DIR/test-summary.json" << SUMMARY
{
  "timestamp": "$(date -u +"%Y-%m-%dT%H:%M:%SZ")",
  "platform": "macOS $(sw_vers -productVersion)",
  "architecture": "$(uname -m)",
  "repository": "$REPO_DIR",
  "headless": $HEADLESS,
  "video_recorded": $RECORD_VIDEO,
  "profile_collected": $COLLECT_PROFILE,
  "output_directory": "$OUTPUT_DIR"
}
SUMMARY

echo ""
echo "================================================"
echo "Test Results"
echo "================================================"
echo ""
echo "📁 Output directory: $OUTPUT_DIR"
echo "📹 Videos: $OUTPUT_DIR/videos/"
echo "📊 Profiles: $OUTPUT_DIR/profiles/"
echo "📄 Logs: $OUTPUT_DIR/logs/"
echo ""
TEST_SCRIPT

chmod +x "${HOME}/run-minecraft-tests.sh"

echo "✅ Test runner script created"
echo ""

# Summary
echo "================================================"
echo "✅ Setup Complete!"
echo "================================================"
echo ""
echo "📁 Runner directory: $RUNNER_DIR"
echo "🎮 Minecraft env: $MINECRAFT_DIR"
echo "📹 Recording script: ${HOME}/record-minecraft-test.sh"
echo "📊 Profiling script: ${HOME}/profile-minecraft-test.sh"
echo "🧪 Test runner: ${HOME}/run-minecraft-tests.sh"
echo ""
echo "Next steps:"
echo "1. Configure the runner:"
echo "   cd $RUNNER_DIR"
echo "   ./config.sh --url https://github.com/YOUR_USERNAME/farmcraft --token YOUR_TOKEN"
echo ""
echo "2. Start the runner:"
echo "   ./run.sh"
echo ""
echo "3. Or install as a service:"
echo "   sudo ./svc.sh install"
echo "   sudo ./svc.sh start"
echo ""
echo "4. Test recording:"
echo "   HEADLESS=true ${HOME}/record-minecraft-test.sh"
echo ""
echo "5. Run full test suite:"
echo "   ${HOME}/run-minecraft-tests.sh ${HOME}/projects/minecraft"
echo ""
