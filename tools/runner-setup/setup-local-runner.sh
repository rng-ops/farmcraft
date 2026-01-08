#!/bin/bash

# Quick Local Runner Setup
# Run this to get your GitHub Actions runner running locally on M2 Mac

set -e

echo "🚀 FarmCraft Local Runner Setup"
echo "================================"
echo ""

# Check if we're on macOS
if [[ "$OSTYPE" != "darwin"* ]]; then
    echo "❌ This script is for macOS only"
    exit 1
fi

# Get GitHub PAT
echo "📋 You'll need a GitHub Personal Access Token with 'repo' and 'workflow' scope"
echo ""
echo "To create one:"
echo "1. Go to https://github.com/settings/tokens/new"
echo "2. Select 'repo' and 'workflow' scopes"
echo "3. Generate token and copy it"
echo ""
read -p "Enter your GitHub PAT: " GITHUB_PAT
echo ""

# Get repository owner and name
REPO_OWNER="rng-ops"
REPO_NAME="farmcraft"
REPO_URL="https://github.com/${REPO_OWNER}/${REPO_NAME}"

echo "Repository: $REPO_URL"
echo ""

# Install dependencies via Homebrew
echo "📦 Installing dependencies..."
brew install --quiet ffmpeg node@20 pnpm openjdk@17 2>/dev/null || echo "Dependencies already installed"

# Setup runner directory
RUNNER_DIR="${HOME}/actions-runner"
mkdir -p "$RUNNER_DIR"
cd "$RUNNER_DIR"

# Download runner if not present
if [ ! -f "config.sh" ]; then
    echo "📥 Downloading GitHub Actions Runner..."
    RUNNER_VERSION="2.319.1"
    curl -o actions-runner-osx-arm64.tar.gz -L \
        "https://github.com/actions/runner/releases/download/v${RUNNER_VERSION}/actions-runner-osx-arm64-${RUNNER_VERSION}.tar.gz"
    tar xzf actions-runner-osx-arm64.tar.gz
    rm actions-runner-osx-arm64.tar.gz
fi

# Get registration token from GitHub API
echo "🔐 Getting registration token..."
REG_TOKEN=$(curl -s -X POST \
    -H "Accept: application/vnd.github+json" \
    -H "Authorization: Bearer ${GITHUB_PAT}" \
    -H "X-GitHub-Api-Version: 2022-11-28" \
    "https://api.github.com/repos/${REPO_OWNER}/${REPO_NAME}/actions/runners/registration-token" \
    | grep -o '"token": "[^"]*' | sed 's/"token": "//')

if [ -z "$REG_TOKEN" ]; then
    echo "❌ Failed to get registration token. Check your PAT and repository access."
    exit 1
fi

echo "✅ Registration token obtained"
echo ""

# Configure the runner
echo "⚙️  Configuring runner..."
./config.sh \
    --url "$REPO_URL" \
    --token "$REG_TOKEN" \
    --name "farmcraft-local-m2" \
    --labels "macos,arm64,minecraft,self-hosted" \
    --work "_work" \
    --replace

echo ""
echo "✅ Runner configured!"
echo ""

# Setup Minecraft test environment
echo "🎮 Setting up Minecraft test environment..."
MINECRAFT_DIR="${HOME}/minecraft-test-env"
mkdir -p "$MINECRAFT_DIR/mods"
mkdir -p "$MINECRAFT_DIR/logs"

# Setup scripts if not exists
if [ ! -f "${HOME}/record-minecraft-test.sh" ]; then
    echo "📹 Setting up recording script..."
    bash "${HOME}/projects/minecraft/tools/runner-setup/setup-macos-runner.sh" || true
fi

echo ""
echo "================================================"
echo "✅ Setup Complete!"
echo "================================================"
echo ""
echo "To start the runner:"
echo "  cd $RUNNER_DIR"
echo "  ./run.sh"
echo ""
echo "Or install as a service (recommended):"
echo "  cd $RUNNER_DIR"
echo "  sudo ./svc.sh install"
echo "  sudo ./svc.sh start"
echo ""
echo "Runner Details:"
echo "  Name: farmcraft-local-m2"
echo "  Labels: macos, arm64, minecraft, self-hosted"
echo "  Repository: $REPO_URL"
echo ""
echo "View runners at:"
echo "  https://github.com/${REPO_OWNER}/${REPO_NAME}/settings/actions/runners"
echo ""
