# FarmCraft - Testing Guide

This document describes the complete testing infrastructure for the FarmCraft project, including mod command tests, server integration tests, and CI/CD integration.

## Overview

The FarmCraft project has comprehensive test coverage:

1. **Mod Command Tests** - Integration tests for all in-game commands
2. **Server Integration Tests** - E2E tests for Recipe, MCP, and Docs servers
3. **CI/CD Pipeline** - Automated testing on every push/PR

## Prerequisites

### For Mod Tests
- Java 17 or higher
- Gradle (included via wrapper)

### For Server Tests
- Node.js 20+
- pnpm 8+
- Running servers (Recipe, MCP, Docs AI)

## Running Tests

### Quick Start - All Tests

```bash
# Start all servers first
pnpm run start:all

# Then run complete test suite
pnpm run test:all
```

### Mod Command Tests

```bash
# Run all mod tests
pnpm run test:mod

# Run with CI configuration (verbose output)
pnpm run test:mod:ci

# Validate command registration only
pnpm run test:mod:validate

# Or use the script directly
./run-mod-tests.sh
```

### Server Integration Tests

```bash
# Ensure servers are running first!
pnpm run test:e2e
```

### Individual Test Suites

```bash
# Mod tests via Gradle
cd mod/forge
./gradlew test                 # All tests
./gradlew validateCommands     # Registration validation only
./gradlew ciTest              # CI configuration

# Specific test class
./gradlew test --tests "com.farmcraft.commands.CommandIntegrationTest"
```

## Test Coverage

### Mod Commands Tested

All commands exposed by the mod are tested:

| Command | Test Coverage |
|---------|--------------|
| `/farmcraft` | ✅ Registration, execution, defaults to guide |
| `/farmcraft guide` | ✅ Shows guide messages |
| `/farmcraft status` | ✅ Checks server connectivity |
| `/farmcraft help <topic>` | ✅ Loads and displays documentation |
| `/farmcraft ask <question>` | ✅ Queries AI assistant |
| `/farmcraft topics` | ✅ Lists available topics |

### Test Types

1. **Command Registration Tests**
   - Verifies all commands are registered in dispatcher
   - Checks command structure and arguments
   - Validates subcommand relationships

2. **Command Execution Tests**
   - Tests successful command execution
   - Validates return values
   - Checks message output

3. **Error Handling Tests**
   - Invalid commands throw proper exceptions
   - Missing arguments are handled
   - Permission checks work correctly

4. **Integration Tests**
   - Commands work with mocked Minecraft environment
   - Proper message formatting
   - Async operations (docs/AI queries) are triggered

## CI/CD Integration

### GitHub Actions Workflow

Location: `.github/workflows/mod-ci.yml`

**Triggers:**
- Push to `main` or `develop` branches
- Pull requests to `main` or `develop`

**Jobs:**

1. **build-and-test**
   - Runs all command tests
   - Validates command registration
   - Builds the mod JAR
   - Uploads test results and artifacts

2. **validate-commands**
   - Ensures all commands are properly registered
   - Fails if any command is missing

**Artifacts:**
- Test reports (HTML & XML)
- Compiled mod JAR
- Test logs

### Local CI Simulation

```bash
# Run the same tests as CI
pnpm run test:mod:ci

# Check test output
open mod/forge/build/reports/tests/test/index.html
```

## Build Integration

Tests are integrated into the build process:

```bash
# Build will fail if tests fail
cd mod/forge
./gradlew build

# This automatically runs:
# 1. compileJava
# 2. test (all tests must pass)
# 3. jar
# 4. reobfJar
```

To skip tests during development (not recommended):
```bash
./gradlew build -x test
```

## Test Reports

### HTML Reports

After running tests, view detailed reports:

```bash
# Mod tests
open mod/forge/build/reports/tests/test/index.html

# CI tests
open mod/forge/build/reports/tests/ci/index.html
```

### XML Reports (for CI tools)

JUnit XML format reports are generated at:
- `mod/forge/build/test-results/test/`
- `mod/forge/build/test-results/ci/`

## Adding New Commands

When adding a new command to the mod:

### 1. Implement the Command

```java
// In FarmCraftCommand.java
dispatcher.register(
    Commands.literal("farmcraft")
        .then(Commands.literal("newcommand")
            .executes(FarmCraftCommand::executeNewCommand))
);
```

### 2. Add Registration Test

```java
// In CommandRegistrationTest.java
@Test
public void testNewCommandIsRegistered() {
    var farmcraft = dispatcher.getRoot().getChild("farmcraft");
    assertNotNull(farmcraft.getChild("newcommand"),
        "/farmcraft newcommand should be registered");
}
```

### 3. Add Integration Test

```java
// In CommandIntegrationTest.java
@Test
public void testNewCommand() throws CommandSyntaxException {
    int result = dispatcher.execute("farmcraft newcommand", mockSource);
    assertEquals(1, result, "New command should execute successfully");
    verify(mockSource, atLeastOnce()).sendSuccess(any(), eq(false));
}
```

### 4. Run Tests

```bash
pnpm run test:mod
```

### 5. Verify CI Passes

Push to a feature branch and ensure GitHub Actions passes.

## Troubleshooting

### Java Not Found

```bash
# Check Java installation
java -version

# Should be Java 17+
# Install from: https://adoptium.net/
```

### Gradle Build Fails

```bash
# Clean and rebuild
cd mod/forge
./gradlew clean build

# Check Gradle version
./gradlew --version
```

### Tests Pass Locally But Fail in CI

1. Check Java version matches (17)
2. Verify all dependencies are committed
3. Check for environment-specific code
4. Review CI logs in GitHub Actions

### Server Tests Fail

```bash
# Ensure all servers are running
pnpm run start:all

# Check ports
lsof -i :7420  # Recipe server
lsof -i :7422  # MCP server
lsof -i :7424  # Docs AI server

# Check server health
curl http://localhost:7424/health
```

### Mock Failures

If mocking fails in tests:

```bash
# Ensure mockito version matches in build.gradle
testImplementation 'org.mockito:mockito-core:5.3.1'
testImplementation 'org.mockito:mockito-junit-jupiter:5.3.1'
```

## Test Output Examples

### Successful Test Run

```
✅ All tests passed successfully!

Test reports available at:
   HTML: file:///path/to/mod/forge/build/reports/tests/test/index.html
   XML:  /path/to/mod/forge/build/test-results/test/
```

### Failed Test Run

```
❌ Tests failed!

CommandIntegrationTest > testGuideCommand FAILED
    org.mockito.exceptions.misusing.InvalidUseOfMatchersException
    at CommandIntegrationTest.testGuideCommand(CommandIntegrationTest.java:85)

Check the following for details:
   Test log:    /path/to/mod/forge/test-output.log
   HTML report: file:///path/to/mod/forge/build/reports/tests/test/index.html
```

## Performance

Typical test execution times:

- **Mod tests**: 5-10 seconds
- **Server tests**: 10-20 seconds
- **Complete suite**: 20-30 seconds

## Best Practices

1. **Run tests before committing**
   ```bash
   pnpm run test:mod
   ```

2. **Add tests for new commands immediately**
   - Don't wait until the end

3. **Check test reports for details**
   - HTML reports have full stack traces

4. **Keep tests fast**
   - Mock external dependencies
   - Use test doubles instead of real servers

5. **Write descriptive test names**
   ```java
   @Test
   public void testHelpCommandWithInvalidTopicShowsError()
   ```

6. **Verify CI passes before merging**
   - Check GitHub Actions tab

## Additional Resources

- [JUnit 5 Documentation](https://junit.org/junit5/docs/current/user-guide/)
- [Mockito Documentation](https://javadoc.io/doc/org.mockito/mockito-core/latest/org/mockito/Mockito.html)
- [Forge Testing](https://docs.minecraftforge.net/en/latest/concepts/testing/)
- [GitHub Actions](https://docs.github.com/en/actions)

## Support

For issues with tests:
1. Check test output logs
2. Review HTML reports
3. Check CI workflow logs
4. Open an issue with test output
