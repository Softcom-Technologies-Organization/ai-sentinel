# Contributing to AI Sentinel

Thank you for your interest in contributing to AI Sentinel! We welcome contributions from everyone, whether it's bug fixes, new features, documentation improvements, or suggestions. This guide will help you get started.

## Table of Contents

- [Code of Conduct](#code-of-conduct)
- [How Can I Contribute?](#how-can-i-contribute)
- [Getting Help](#getting-help)
- [Reporting Bugs](#reporting-bugs)
- [Suggesting Enhancements](#suggesting-enhancements)
- [Development Setup](#development-setup)
- [Coding Standards](#coding-standards)
- [Testing Requirements](#testing-requirements)
- [Pull Request Process](#pull-request-process)
- [Architecture Guidelines](#architecture-guidelines)
- [License](#license)

## Code of Conduct

By participating in this project, you agree to maintain a respectful and inclusive environment for all contributors. We follow the [Contributor Covenant Code of Conduct](https://www.contributor-covenant.org/).

**Expected behavior:**
- Be respectful and constructive in discussions
- Welcome newcomers and help them get started
- Focus on what is best for the community and project
- Show empathy towards other contributors

**Unacceptable behavior:**
- Harassment, discrimination, or offensive comments
- Personal attacks or trolling
- Publishing others' private information
- Any conduct inappropriate for a professional setting

## How Can I Contribute?

There are many ways to contribute to AI Sentinel:

### 🐛 Bug Reports
Found a bug? Report it! See [Reporting Bugs](#reporting-bugs).

### ✨ Feature Suggestions
Have an idea for improvement? We'd love to hear it! See [Suggesting Enhancements](#suggesting-enhancements).

### 📝 Documentation
Help improve our documentation, guides, or examples.

### 🧪 Testing
Write tests, perform testing, or report test coverage gaps.

### 💻 Code Contributions
Fix bugs, implement features, or improve performance.

### 🌍 Translations
Help translate the UI or documentation to other languages.

### 📊 Use Cases & Feedback
Share how you're using AI Sentinel and provide feedback.

## Getting Help

Need assistance? Here's how to get help:

- **Documentation**: Check our [README](README.md) and service-specific docs
- **GitHub Issues**: Search [existing issues](https://github.com/Softcom-Technologies-Organization/ai-sentinel/issues) for similar questions
- **Email Support**: Contact us at support@softcom-technologies.com

## Reporting Bugs

Before creating a bug report, please check if the issue has already been reported in [GitHub Issues](https://github.com/Softcom-Technologies-Organization/ai-sentinel/issues).

### How to Submit a Good Bug Report

**Use our bug report template** and include:

1. **Clear Title**: Descriptive summary of the issue
2. **Environment Details**:
   - AI Sentinel version
   - Operating system (Windows/macOS/Linux)
   - Docker version
   - Browser (for UI issues)
3. **Steps to Reproduce**:
   - Exact steps to trigger the bug
   - What you expected to happen
   - What actually happened
4. **Logs and Screenshots**:
   ```bash
   # Get service logs
   docker compose logs pii-detector > logs.txt
   docker compose logs pii-reporting-api >> logs.txt
   docker compose logs pii-reporting-ui >> logs.txt
   ```
   - Include relevant error messages
   - Add screenshots if applicable
5. **Additional Context**: Any other information that might be helpful

> **🔐 Security Vulnerabilities**: If you discover a security issue, please **DO NOT** open a public issue. Email security@softcom-technologies.com with details.

## Suggesting Enhancements

We welcome feature suggestions and enhancements!

### Before Suggesting

1. Check if the feature already exists
2. Search [existing issues](https://github.com/Softcom-Technologies-Organization/ai-sentinel/issues) and discussions
3. Consider if it aligns with the project's goals

### How to Submit a Good Enhancement Suggestion

**Create an issue** with:

1. **Clear Title**: Concise description of the enhancement
2. **Use Case**: Why this enhancement would be useful
3. **Proposed Solution**: How you envision it working
4. **Alternative Solutions**: Other approaches you've considered
5. **Additional Context**: Examples, mockups, or references

## Development Setup

### Prerequisites

Ensure you have the following installed:

- **Docker Desktop** 20.10+ ([Installation Guide](https://docs.docker.com/get-docker/))
- **Git** ([Download](https://git-scm.com/downloads))
- **Node.js** 20+ (for UI development) ([Download](https://nodejs.org/))
- **Python** 3.13+ (for detector development) ([Download](https://www.python.org/))
- **Java** 21+ (for API development) ([Download](https://adoptium.net/))
- **Maven** 3.9+ (for Java builds) ([Download](https://maven.apache.org/))

### Local Development Setup

**1. Fork and Clone**

```bash
# Fork the repository on GitHub, then clone your fork
git clone https://github.com/YOUR-USERNAME/ai-sentinel.git
cd ai-sentinel

# Add upstream remote
git remote add upstream https://github.com/Softcom-Technologies-Organization/ai-sentinel.git
```

**2. Configure Environment**

```bash
# Copy example environment file
cp .env.example .env

# Edit .env with your credentials
# At minimum, you need:
# - HUGGING_FACE_API_KEY
# - CONFLUENCE_BASE_URL
# - CONFLUENCE_USERNAME
# - CONFLUENCE_API_TOKEN
```

**3. Start Development Environment**

```bash
# Build and start all services
docker compose -f docker-compose.dev.yml up -d --build

# Check service status
docker compose -f docker-compose.dev.yml ps

# View logs
docker compose -f docker-compose.dev.yml logs -f
```

**4. Access Services**

- Frontend (UI): http://localhost:4200
- Backend API: http://localhost:8080/sentinelle
- PII Detector: gRPC on localhost:50051
- Database: PostgreSQL on localhost:5432

### Service-Specific Development

#### Python PII Detector Service

```bash
cd pii-detector-service

# Create virtual environment
python -m venv venv
source venv/bin/activate  # On Windows: venv\Scripts\activate

# Install dependencies
pip install -e ".[dev]"

# Run tests
pytest --cov=pii_detector --cov-report=html

# Run linter
ruff check .

# Format code
ruff format .
```

#### Java Backend API

```bash
cd pii-reporting-api

# Run tests
mvn test

# Run with coverage
mvn clean test jacoco:report

# Build
mvn clean package

# Run SonarQube analysis (if configured)
mvn clean test verify jacoco:report sonar:sonar
```

#### Angular Frontend UI

```bash
cd pii-reporting-ui

# Install dependencies
npm install

# Run development server
npm start

# Run tests
npm test

# Run E2E tests
npm run e2e

# Lint
npm run lint

# Format
npm run format
```

## Coding Standards

### General Principles

All code must follow these principles:

- **KISS** (Keep It Simple, Stupid): Prefer simple, clear solutions
- **DRY** (Don't Repeat Yourself): Avoid code duplication
- **YAGNI** (You Aren't Gonna Need It): Don't add unused functionality
- **SOLID** principles: Especially Single Responsibility
- **Clean Code**: Self-documenting code with minimal comments

### Java Standards (Backend API)

**Style Guide**: Follow [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html)

**Key Requirements**:
- ✅ Use Java 21 features (Records, Pattern Matching, Virtual Threads)
- ✅ Follow hexagonal architecture (see [Architecture Guidelines](#architecture-guidelines))
- ✅ Maximum cyclomatic complexity: 5 (absolute max: 7)
- ✅ Maximum 3 parameters per method
- ✅ Use Optional instead of null returns
- ✅ Proper exception handling
- ✅ Javadoc for public APIs (focus on business purpose, not implementation)

**Example**:
```java
/**
 * Validates customer eligibility for scan based on business rules.
 * 
 * Business Rule: User must have admin role or be space owner.
 * 
 * @param user The user to validate
 * @param spaceKey The Confluence space key
 * @return true if eligible, false otherwise
 */
public boolean canStartScan(User user, String spaceKey) {
    return user.hasRole(Role.ADMIN) || 
           isSpaceOwner(user, spaceKey);
}
```

### Python Standards (PII Detector)

**Style Guide**: Follow [PEP 8](https://www.python.org/dev/peps/pep-0008/)

**Key Requirements**:
- ✅ Use Python 3.13+ features
- ✅ Type hints for all functions
- ✅ Docstrings for all public functions (Google style)
- ✅ Use `ruff` for linting and formatting
- ✅ Maximum line length: 88 characters (Black default)
- ✅ Use dataclasses or Pydantic models

**Example**:
```python
from typing import Optional
from dataclasses import dataclass

@dataclass
class PIIEntity:
    """Represents a detected PII entity.
    
    Attributes:
        text: The detected text
        entity_type: Type of PII (e.g., EMAIL, PERSON)
        start_pos: Starting position in text
        end_pos: Ending position in text
        confidence: Detection confidence score (0-1)
    """
    text: str
    entity_type: str
    start_pos: int
    end_pos: int
    confidence: float

def detect_pii(text: str, threshold: float = 0.5) -> list[PIIEntity]:
    """Detect PII entities in text.
    
    Args:
        text: Text to analyze
        threshold: Minimum confidence threshold
        
    Returns:
        List of detected PII entities
        
    Raises:
        ValueError: If text is empty
    """
    if not text:
        raise ValueError("Text cannot be empty")
    
    # Implementation
    return []
```

### TypeScript/Angular Standards (Frontend)

**Style Guide**: Follow [Angular Style Guide](https://angular.io/guide/styleguide)

**Key Requirements**:
- ✅ Use Angular 17+ standalone components
- ✅ Use modern control flow syntax (`@if`, `@for`, `@switch`)
- ✅ Signals for reactive state management
- ✅ Strict TypeScript configuration
- ✅ ESLint and Prettier for code quality
- ✅ Accessibility (WCAG 2.1 Level AA)

**Example**:
```typescript
import { Component, signal, computed } from '@angular/core';

@Component({
  selector: 'app-scan-list',
  standalone: true,
  template: `
    @if (loading()) {
      <app-spinner />
    } @else if (scans().length === 0) {
      <p>No scans found</p>
    } @else {
      @for (scan of scans(); track scan.id) {
        <app-scan-card [scan]="scan" />
      }
    }
  `
})
export class ScanListComponent {
  scans = signal<Scan[]>([]);
  loading = signal(true);
  
  totalScans = computed(() => this.scans().length);
  activeScans = computed(() => 
    this.scans().filter(s => s.status === 'RUNNING').length
  );
}
```

### Commit Message Convention

Follow [Conventional Commits](https://www.conventionalcommits.org/):

```
<type>(<scope>): <subject>

<body>

<footer>
```

**Types**:
- `feat`: New feature
- `fix`: Bug fix
- `docs`: Documentation changes
- `style`: Code style (formatting, no logic change)
- `refactor`: Code refactoring
- `test`: Adding or updating tests
- `chore`: Maintenance tasks
- `perf`: Performance improvements

**Examples**:
```
feat(detector): add support for Italian language detection

fix(api): resolve scan checkpoint save failure
Fixes #123

docs(readme): update installation instructions for Windows

test(detector): add integration tests for GLiNER model
```

## Testing Requirements

**All code contributions must include appropriate tests.**

### Python Tests (PII Detector)

**Requirements**:
- ✅ Unit tests for all new functions/classes
- ✅ Integration tests for detector components
- ✅ Minimum 80% code coverage
- ✅ Use pytest and pytest-cov

**Running Tests**:
```bash
cd pii-detector-service

# Run all tests with coverage
pytest --cov=pii_detector --cov-report=html --cov-report=term

# Run specific test file
pytest tests/unit/test_gliner_detector.py -v

# Run tests in parallel
pytest -n auto
```

**Test Naming Convention**:
```python
def test_should_detect_email_when_text_contains_valid_email():
    """Test that email is correctly detected in text."""
    # Arrange
    text = "Contact me at john.doe@example.com"
    
    # Act
    result = detect_pii(text)
    
    # Assert
    assert len(result) == 1
    assert result[0].entity_type == "EMAIL"
    assert result[0].text == "john.doe@example.com"
```

### Java Tests (Backend API)

**Requirements**:
- ✅ Unit tests using JUnit 5 and Mockito
- ✅ Integration tests with @SpringBootTest
- ✅ Minimum 80% code coverage
- ✅ AssertJ for fluent assertions
- ✅ Architecture tests for hexagonal compliance

**Running Tests**:
```bash
cd pii-reporting-api

# Run all tests
mvn test

# Run with coverage
mvn clean test jacoco:report

# Run architecture tests
mvn test -Dtest=HexagonalArchitectureTest

# View coverage report
open target/site/jacoco/index.html
```

**Test Naming Convention**:
```java
@Test
void Should_CreateScan_When_ValidSpaceKeyProvided() {
    // Arrange
    CreateScanRequest request = new CreateScanRequest("DS", "Doc Space", 100);
    
    // Act
    ScanResponse response = scanService.createScan(request);
    
    // Assert
    assertThat(response.getId()).isNotNull();
    assertThat(response.getSpaceKey()).isEqualTo("DS");
    assertThat(response.getStatus()).isEqualTo(ScanStatus.CREATED);
}
```

### Angular Tests (Frontend)

**Requirements**:
- ✅ Unit tests for components, services, and utilities
- ✅ E2E tests for critical user flows
- ✅ Minimum 80% code coverage
- ✅ Use Jasmine/Karma and Playwright

**Running Tests**:
```bash
cd pii-reporting-ui

# Unit tests
npm test

# E2E tests
npm run e2e

# Coverage
npm run test:coverage
```

## Pull Request Process

### Before Submitting

**Checklist**:
- [ ] Code follows project coding standards
- [ ] All tests pass locally
- [ ] New tests added for new functionality
- [ ] Code coverage maintained or improved
- [ ] Documentation updated (if needed)
- [ ] Commit messages follow convention
- [ ] No merge conflicts with main branch

### Creating a Pull Request

**1. Create a Feature Branch**

```bash
# Update your fork
git fetch upstream
git checkout main
git merge upstream/main

# Create feature branch
git checkout -b feat/amazing-feature
```

**2. Make Your Changes**

- Write clean, tested code
- Commit regularly with meaningful messages
- Keep commits focused and atomic

**3. Push and Create PR**

```bash
# Push your branch
git push origin feat/amazing-feature

# Create PR on GitHub
# Use the PR template provided
```

**4. PR Title and Description**

**Title Format**: `<type>(<scope>): <description>`

**Description Must Include**:
- **Summary**: What does this PR do?
- **Motivation**: Why is this change needed?
- **Changes**: List of main changes
- **Testing**: How was this tested?
- **Screenshots**: If UI changes
- **Breaking Changes**: If any
- **Related Issues**: Fixes #123

**Example PR Description**:
```markdown
## Summary
Add support for Spanish language PII detection using multilingual model.

## Motivation
Users with Spanish Confluence spaces need PII detection support.

## Changes
- Added Spanish entity types configuration
- Integrated multilingual NER model
- Updated detection service to handle Spanish text
- Added integration tests for Spanish text

## Testing
- All existing tests pass
- New integration tests for Spanish detection
- Manual testing with Spanish Confluence pages

## Breaking Changes
None

## Related Issues
Closes #234
```

### Code Review Process

**What to Expect**:
1. **Automated Checks**: CI/CD runs tests and quality checks
2. **Maintainer Review**: A maintainer reviews your code
3. **Feedback**: You may receive change requests
4. **Iteration**: Address feedback and update PR
5. **Approval**: Once approved, a maintainer merges

**Review Timeline**:
- Simple fixes: 1-2 days
- Features: 3-7 days
- Major changes: 1-2 weeks

**Tips for Faster Review**:
- Keep PRs small and focused
- Provide clear description and context
- Respond promptly to feedback
- Be patient and respectful

## Architecture Guidelines

### Hexagonal Architecture (Backend API)

The Java backend follows **hexagonal architecture** (ports and adapters):

```
pii-reporting-api/
└── src/main/java/com/softcom/sentinel/
    ├── domain/          # Business logic (no external dependencies)
    │   ├── model/       # Domain entities
    │   ├── port/        # Interfaces (ports)
    │   └── service/     # Business services
    ├── application/     # Use cases and orchestration
    │   └── usecase/     # Application services
    └── infrastructure/  # Technical implementations (adapters)
        ├── adapter/
        │   ├── in/      # Input adapters (REST controllers)
        │   └── out/     # Output adapters (repositories, clients)
        └── config/      # Configuration
```

**Rules**:
- ✅ **Domain** layer has NO external dependencies
- ✅ **Application** layer depends only on domain
- ✅ **Infrastructure** implements domain ports
- ✅ Dependencies point inward (domain ← application ← infrastructure)
- ✅ Use dependency injection for ports

**Verification**:
```bash
# Architecture tests must pass
mvn test -Dtest=HexagonalArchitectureTest
```

### Microservices Communication

**gRPC** for inter-service communication:
- Backend API → PII Detector: gRPC calls
- Proto definitions in `/proto` directory
- Use generated code, don't modify manually

**REST** for client-facing APIs:
- Frontend → Backend API: REST/JSON
- Follow REST best practices
- Use proper HTTP status codes

## License

By contributing to AI Sentinel, you agree that your contributions will be licensed under the MIT License.

All submissions are subject to review and acceptance by the project maintainers. We reserve the right to reject contributions that don't align with the project's goals or quality standards.

---

## Questions?

If you have questions not covered in this guide:

- Open a [GitHub Discussion](https://github.com/Softcom-Technologies-Organization/ai-sentinel/discussions)
- Email: support@softcom-technologies.com
- Check our [README](README.md)

**Thank you for contributing to AI Sentinel! Together, we're building a powerful solution for PII detection and data protection.** 🚀

---

*This guide is adapted from industry best practices and is continuously updated. Last updated: January 2025*
