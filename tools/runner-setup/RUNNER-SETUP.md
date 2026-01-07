# Setting Up Self-Hosted Runner for macOS M2

## Overview

This guide covers setting up a GitHub Actions self-hosted runner on macOS M2 (ARM64) for automated Minecraft mod testing with video recording and profiling.

## Features

- ✅ Native macOS M2 support
- ✅ Automated video recording of tests
- ✅ Headless mode operation
- ✅ System profiling (CPU, GPU, Memory, Network, Disk)
- ✅ ML training dataset generation
- ✅ Long-term archival storage
- ✅ No secrets required (polling mechanism)

## Prerequisites

- macOS 13+ (Ventura or later)
- Apple Silicon M2 processor
- Administrator access
- GitHub repository with Actions enabled

## Quick Setup

```bash
# Download and run the setup script
cd ~/projects/minecraft
./tools/runner-setup/setup-macos-runner.sh
```

## Manual Setup

### 1. Install Dependencies

```bash
# Install Homebrew if not present
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"

# Install required packages
brew install ffmpeg node@20 pnpm openjdk@17
```

### 2. Download GitHub Actions Runner

```bash
mkdir -p ~/actions-runner
cd ~/actions-runner

# Download ARM64 runner
RUNNER_VERSION="2.319.1"
curl -o actions-runner-osx-arm64.tar.gz -L \
    "https://github.com/actions/runner/releases/download/v${RUNNER_VERSION}/actions-runner-osx-arm64-${RUNNER_VERSION}.tar.gz"

tar xzf actions-runner-osx-arm64.tar.gz
```

### 3. Configure Runner

```bash
cd ~/actions-runner

# Register the runner
./config.sh \
    --url https://github.com/YOUR_USERNAME/farmcraft \
    --token YOUR_REGISTRATION_TOKEN \
    --name farmcraft-macos-m2 \
    --labels macos,arm64,minecraft,self-hosted \
    --work _work

# The registration token can be obtained from:
# GitHub → Repository → Settings → Actions → Runners → New self-hosted runner
```

### 4. Install as Service (Recommended)

```bash
cd ~/actions-runner

# Install the service
sudo ./svc.sh install

# Start the service
sudo ./svc.sh start

# Check status
sudo ./svc.sh status
```

### 5. Setup Minecraft Environment

```bash
# Run the Minecraft setup
~/minecraft-test-env/setup-minecraft.sh
```

## Usage

### Running Tests with GitHub Actions

The self-hosted runner automatically picks up jobs with matching labels:

```yaml
jobs:
  test:
    runs-on: [self-hosted, macOS, ARM64, minecraft]
    steps:
      - name: Run tests
        run: ~/run-minecraft-tests.sh
```

### Manual Test Execution

```bash
# Run full test suite
~/run-minecraft-tests.sh ~/projects/minecraft

# With custom output directory
~/run-minecraft-tests.sh ~/projects/minecraft ~/custom-output

# With specific options
HEADLESS=true RECORD_VIDEO=true COLLECT_PROFILE=true \
    ~/run-minecraft-tests.sh ~/projects/minecraft
```

### Recording Tests

```bash
# Record for 5 minutes (default)
~/record-minecraft-test.sh

# Record in headless mode
HEADLESS=true ~/record-minecraft-test.sh

# Custom duration (10 minutes)
DURATION=600 ~/record-minecraft-test.sh
```

### Profiling

```bash
# Start profiling
~/profile-minecraft-test.sh ~/output-dir &
PROFILER_PID=$!

# Run your tests...

# Stop profiling
kill $PROFILER_PID
```

## Video Recording

### Headless Mode

Headless mode records tests without showing windows on screen. This is ideal for:
- CI/CD pipelines
- Background processing
- Server environments
- Unattended operation

**Enable headless mode:**
```bash
HEADLESS=true ~/record-minecraft-test.sh
```

### Visible Mode

Visible mode shows the Minecraft window and records it. Useful for:
- Debugging tests
- Visual verification
- Demo creation
- Interactive testing

**Enable visible mode:**
```bash
HEADLESS=false ~/record-minecraft-test.sh
```

### Video Format

- **Codec:** H.264 (libx264)
- **Format:** MP4
- **Resolution:** Native (matches screen)
- **Frame Rate:** 30 FPS
- **Quality:** Fast preset (good balance)

## Profiling Data

The profiling system collects:

- **CPU Usage:** Per-core and total utilization
- **Memory:** Active, wired, compressed
- **GPU Usage:** M2 GPU activity (via Metal)
- **Network:** RX/TX bytes
- **Disk I/O:** Read/Write MB/s

Data format (JSON):
```json
{
  "timestamp": "2026-01-07T12:00:00.000Z",
  "cpu_percent": 45.2,
  "memory_active": 8192,
  "gpu_usage": "active",
  "network_rx_bytes": 1048576,
  "network_tx_bytes": 524288,
  "disk_read_mb": 12.5,
  "disk_write_mb": 8.3
}
```

## ML Training Dataset

### Frame Extraction

Videos are automatically processed to extract frames for ML training:

```bash
# Extract 1 frame per second
ffmpeg -i video.mp4 -vf fps=1 frames/frame-%04d.jpg
```

### Dataset Structure

```
ml-dataset/
├── test-20260107-120000/
│   ├── frame-0001.jpg
│   ├── frame-0002.jpg
│   ├── ...
│   └── metadata.json
└── test-20260107-130000/
    ├── frame-0001.jpg
    └── metadata.json
```

### Metadata

Each dataset includes metadata:
```json
{
  "source_video": "test-20260107-120000.mp4",
  "test_scenario": "pull_request",
  "commit": "abc123...",
  "timestamp": "2026-01-07T12:00:00Z",
  "frame_rate": 1,
  "total_frames": 300
}
```

## Archival Storage

### Local Storage

Test outputs are stored locally at:
- **Videos:** `~/minecraft-test-output/videos/`
- **Profiles:** `~/minecraft-test-output/profiles/`
- **Logs:** `~/minecraft-test-output/logs/`
- **ML Dataset:** `~/minecraft-test-output/ml-dataset/`

### GitHub Artifacts

Artifacts are automatically uploaded to GitHub Actions:
- **Retention:** 30 days (videos/profiles) | 90 days (dataset/summary)
- **Access:** Via GitHub UI or API
- **Download:** `gh run download RUN_ID`

### Long-Term Archival

For long-term storage, integrate with:

**AWS S3:**
```bash
aws s3 sync ~/minecraft-test-output/ s3://farmcraft-test-archive/
```

**Google Cloud Storage:**
```bash
gsutil -m rsync -r ~/minecraft-test-output/ gs://farmcraft-test-archive/
```

**Azure Blob Storage:**
```bash
az storage blob upload-batch \
    -s ~/minecraft-test-output/ \
    -d farmcraft-test-archive
```

## Troubleshooting

### Runner Not Picking Up Jobs

```bash
# Check runner status
cd ~/actions-runner
./run.sh --check

# View runner logs
tail -f ~/actions-runner/_diag/Runner_*.log
```

### Video Recording Issues

```bash
# Check ffmpeg installation
ffmpeg -version

# Test screen capture
ffmpeg -f avfoundation -list_devices true -i ""

# Grant screen recording permissions
# System Settings → Privacy & Security → Screen Recording → Terminal
```

### Minecraft Won't Start

```bash
# Check Java version
java -version

# Verify mod installation
ls -la ~/minecraft-test-env/mods/

# Check logs
tail -f ~/minecraft-test-env/logs/latest.log
```

### Performance Issues

```bash
# Monitor system resources
top -l 1 | grep -E "CPU|PhysMem"

# Check disk space
df -h

# View active processes
ps aux | grep -i minecraft
```

## Security Considerations

### Runner Isolation

- Runs as dedicated user
- Limited file system access
- No sudo privileges in workflow
- Isolated work directory

### Network Security

- Outbound-only connections
- No inbound ports required
- Polls GitHub for jobs
- No exposed services

### Secrets Management

- No secrets stored on runner
- GitHub provides secrets at runtime
- Temporary credentials only
- Auto-cleanup after job

## Maintenance

### Update Runner

```bash
cd ~/actions-runner
./svc.sh stop
./config.sh remove
# Download new version
./config.sh [options]
./svc.sh install
./svc.sh start
```

### Clean Up Storage

```bash
# Remove old test outputs
find ~/minecraft-test-output -mtime +7 -delete

# Clear runner work directory
rm -rf ~/actions-runner/_work/*

# Clean Gradle cache
rm -rf ~/.gradle/caches/
```

### Monitor Disk Usage

```bash
# Check overall usage
df -h

# Find large directories
du -sh ~/minecraft-test-output/*

# Set up automatic cleanup (cron)
crontab -e
# Add: 0 2 * * * find ~/minecraft-test-output -mtime +7 -delete
```

## Advanced Configuration

### Multiple Runners

Run multiple runners on the same machine:

```bash
# Create additional runner directories
mkdir -p ~/actions-runner-2
cd ~/actions-runner-2
# Configure with different name
./config.sh --name farmcraft-macos-m2-2 [...]
```

### Custom Labels

Add custom labels for targeting:

```bash
./config.sh \
    --labels macos,arm64,minecraft,self-hosted,gpu-enabled,high-memory
```

### Environment Variables

Set persistent environment variables:

```bash
# In ~/.zshrc or ~/.bash_profile
export MINECRAFT_HOME=~/minecraft-test-env
export FARMCRAFT_RUNNER=true
export HEADLESS=true
```

## Cost Optimization

### Reduce Video Size

```bash
# Lower bitrate
ffmpeg ... -b:v 1000k ...

# Reduce resolution
ffmpeg ... -vf scale=1280:720 ...

# Increase compression
ffmpeg ... -preset slow ...
```

### Selective Recording

Only record specific tests:
```yaml
- name: Record only gameplay tests
  if: contains(github.event.head_commit.message, '[record]')
  run: ~/record-minecraft-test.sh
```

### Cleanup Old Artifacts

Use GitHub API to delete old artifacts:
```bash
gh api repos/{owner}/{repo}/actions/artifacts \
    --paginate | jq -r '.artifacts[] | select(.expired == false) | .id' | \
    xargs -I {} gh api -X DELETE repos/{owner}/{repo}/actions/artifacts/{}
```

## Support

For issues or questions:
- Check logs: `~/actions-runner/_diag/`
- GitHub Discussions: Repository discussions
- Issues: Create GitHub issue with `runner` label
