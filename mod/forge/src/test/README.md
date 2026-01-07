# FarmCraft Mod Testing

This directory contains integration tests for the FarmCraft mod, specifically focusing on command registration and functionality.

## Test Structure

- **CommandIntegrationTest.java** - Comprehensive tests for all mod commands
  - Tests command execution
  - Validates command output
  - Checks error handling
- **CommandRegistrationTest.java** - Tests command registration
  - Ensures all commands are registered
  - Validates command structure
  - Checks argument handling

- **ModLoadingTest.java** - Basic mod loading tests
  - Validates mod initialization
  - Checks mod metadata

## Running Tests

### Local Development

```bash
cd mod/forge
./gradlew test
```

### CI/CD Integration

The tests are automatically run as part of the CI/CD pipeline:

```bash
# Run all tests
./gradlew test

# Run only command validation
./gradlew validateCommands

# Run CI tests with full output
./gradlew ciTest
```

### Individual Test Execution

```bash
# Run only command integration tests
./gradlew test --tests "com.farmcraft.commands.CommandIntegrationTest"

# Run only registration tests
./gradlew test --tests "com.farmcraft.commands.CommandRegistrationTest"
```

## What Gets Tested

### Commands Tested

1. `/farmcraft` - Main command (defaults to guide)
2. `/farmcraft guide` - Shows in-game guide
3. `/farmcraft status` - Displays server connection status
4. `/farmcraft help <topic>` - Gets help on specific topics
5. `/farmcraft ask <question>` - Asks the AI assistant
6. `/farmcraft topics` - Lists all available topics

### Test Coverage

- ✅ Command registration validation
- ✅ Command execution success
- ✅ Argument parsing (string, greedy string)
- ✅ Error handling for invalid commands
- ✅ Message output verification
- ✅ Permission checking
- ✅ Return value validation

## CI/CD Integration

Tests are integrated into the build process:

1. **Build Phase** - Tests run before JAR creation
2. **CI Pipeline** - GitHub Actions runs tests on push/PR
3. **Validation Gate** - Build fails if any command test fails

### GitHub Actions Workflow

The `.github/workflows/mod-ci.yml` workflow:

- Runs on push to main/develop branches
- Runs on pull requests
- Executes all tests
- Validates command registration
- Builds the mod
- Uploads test results and artifacts

## Adding New Command Tests

When adding a new command:

1. Register the command in `FarmCraftCommand.java`
2. Add a test method in `CommandIntegrationTest.java`
3. Add registration validation in `CommandRegistrationTest.java`
4. Run tests locally: `./gradlew test`
5. Ensure CI passes before merging

Example test structure:

```java
@Test
public void testNewCommand() throws CommandSyntaxException {
    int result = dispatcher.execute("farmcraft newcommand", mockSource);
    assertEquals(1, result, "New command should execute successfully");
    verify(mockSource, atLeastOnce()).sendSuccess(any(), eq(false));
}
```

## Troubleshooting

### Tests Fail Locally

```bash
# Clean and rebuild
./gradlew clean test

# Run with verbose output
./gradlew test --info

# Run specific test
./gradlew test --tests "CommandIntegrationTest.testGuideCommand"
```

### CI/CD Failures

Check:

1. Test logs in GitHub Actions
2. JUnit XML reports in artifacts
3. Build output for dependency issues

### Command Not Found

If a command test fails with "command not found":

1. Check `FarmCraftCommand.register()` method
2. Verify command is added to dispatcher
3. Run `validateCommands` task
