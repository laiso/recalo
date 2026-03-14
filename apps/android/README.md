
## Test Execution

### Quick Start

```bash
# Run all checks (recommended)
./scripts/test.sh

# Run tests only
./gradlew testDevDebugUnitTest

# Run specific test
./gradlew testDevDebugUnitTest --tests "so.lai.recalo.data.repository.MealRepositoryPortionRatioTest"
```

For more details, see docs/TESTING.md.

## CI/CD

GitHub Actions automatically runs tests:

- Unit Tests
- Lint
- ktlint

For more details, see [.github/workflows/android-ci.yml](../.github/workflows/android-ci.yml).
