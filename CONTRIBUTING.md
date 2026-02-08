# Contributing to Krema

Thank you for your interest in contributing to Krema! This document provides guidelines for contributing to the project.

## Reporting Issues

- Search existing issues before creating a new one
- Include steps to reproduce, expected behavior, and actual behavior
- Include your OS, Java version, and Krema version

## Development Setup

### Prerequisites

- **Java 25** (with preview features enabled)
- **Maven 3.9+**
- **Node.js 18+** (for frontend demo apps and docs site)

### Building from Source

```bash
# Clone the repository
git clone https://github.com/krema-build/krema.git
cd krema

# Build all modules
mvn clean install

# Build skipping tests
mvn clean install -DskipTests
```

### Running the Demo Apps

```bash
cd krema-demos/krema-react
npm install
mvn compile exec:exec -Pdev
```

### Running Tests

```bash
mvn test
```

## Pull Request Guidelines

1. Fork the repository and create a branch from `main`
2. Keep PRs focused — one feature or fix per PR
3. Include tests for new functionality
4. Ensure all existing tests pass
5. Update documentation if your change affects public APIs

## Code Style

- Follow existing code conventions in the project
- Keep functions short and focused
- Use meaningful names for variables, methods, and classes
- Limit function parameters (3 or fewer when possible)

## Project Structure

- `krema/krema-core` — Core webview bindings and IPC framework
- `krema/krema-cli` — Command-line interface
- `krema/krema-processor` — Compile-time annotation processor
- `krema-plugins/` — Official plugin modules
- `krema-demos/` — Example applications
- `krema-docs/` — Documentation site

## License

By contributing to Krema, you agree that your contributions will be licensed under the [Business Source License 1.1](LICENSE) that covers the project.
