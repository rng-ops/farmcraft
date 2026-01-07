# Contributing to FarmCraft

Thank you for your interest in contributing to FarmCraft!

## Development Setup

### Prerequisites

- Java 17+ (for Minecraft mod)
- Node.js 20+ (for server and tools)
- pnpm 8+ (package manager)
- Gradle 8+ (mod building)

### Getting Started

1. Clone the repository
```bash
git clone https://github.com/farmcraft/farmcraft-mod.git
cd farmcraft-mod
```

2. Install dependencies
```bash
pnpm install
```

3. Build the project
```bash
pnpm build
```

4. Start the development server
```bash
pnpm dev:server
```

5. Build the mod
```bash
cd mod/forge
./gradlew build
```

## Project Structure

- `packages/` - Shared TypeScript packages
- `mod/forge/` - Forge mod source
- `mod/fabric/` - Fabric mod source (future)
- `server/` - Backend services
- `tools/` - Development utilities
- `docs/` - Documentation

## Making Changes

### For the Mod

1. Make changes in `mod/forge/src/main/java/`
2. Run `./gradlew runClient` to test
3. Run `./gradlew build` to compile

### For the Server

1. Make changes in `server/recipe-server/src/`
2. Run `pnpm dev:server` to test with hot reload
3. Run `pnpm build` to compile

### For Shared Packages

1. Make changes in `packages/*/src/`
2. Run `pnpm build` to compile
3. Changes are automatically available to dependent packages

## Code Style

### Java (Mod)

- Follow standard Java conventions
- Use meaningful variable names
- Add Javadoc for public APIs
- Keep methods focused and small

### TypeScript (Server/Packages)

- Use TypeScript strict mode
- Prefer const over let
- Use explicit types for function parameters
- Add JSDoc comments for public APIs

## Testing

### Running Tests

```bash
# Run all tests
pnpm test

# Run specific package tests
pnpm test --filter=@farmcraft/pow-core
```

### Writing Tests

- Place tests in `*.test.ts` files
- Use descriptive test names
- Test edge cases and error conditions
- Aim for good coverage of core functionality

## Pull Request Process

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Make your changes
4. Run tests (`pnpm test`)
5. Commit your changes (`git commit -m 'Add amazing feature'`)
6. Push to the branch (`git push origin feature/amazing-feature`)
7. Open a Pull Request

### PR Guidelines

- Describe your changes clearly
- Reference any related issues
- Include screenshots for UI changes
- Ensure tests pass
- Keep PRs focused and reasonably sized

## Issue Reporting

When reporting issues, please include:

- Minecraft version
- Mod version
- Steps to reproduce
- Expected behavior
- Actual behavior
- Crash logs (if applicable)

## Questions?

- Open a GitHub issue for bugs/features
- Join our Discord for discussion
- Check the documentation first

## License

By contributing, you agree that your contributions will be licensed under the MIT License.
