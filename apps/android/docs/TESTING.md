# Test Execution Guide

## Local Test Execution

### Using Script (Recommended)

```bash
# Run all checks
./scripts/test.sh

# Or
./scripts/test.sh --all

# Run tests only
./scripts/test.sh --tests

# Run Lint only
./scripts/test.sh --lint

# Run ktlint only
./scripts/test.sh --ktlint

# Run multiple
./scripts/test.sh --tests --lint

# Show help
./scripts/test.sh --help
```

### Direct Gradle Commands

```bash
cd apps/android

# All unit tests
./gradlew testDevDebugUnitTest

# Specific test class
./gradlew testDevDebugUnitTest --tests "so.lai.recalo.data.repository.MealRepositoryPortionRatioTest"

# Specific package
./gradlew testDevDebugUnitTest --tests "so.lai.recalo.data.repository.*"

# Lint
./gradlew lint

# ktlint
./gradlew ktlintCheck

# ktlint auto-fix
./gradlew ktlintFormat
```

### Test Reports

After running tests, the following reports are generated:

- HTML Report: apps/android/app/build/reports/tests/testDevDebugUnitTest/index.html
- Lint Report: apps/android/app/build/reports/lint-results.html

## Running Tests with GitHub Actions

### Automatic Execution

GitHub Actions runs automatically on:

- Push to main branch
- PR to main branch

### Manual Execution

1. Open the Actions tab in the GitHub repository.
2. Select the Android CI workflow.
3. Click the Run workflow button.
4. Select the branch and run.

### Checking Workflow Logs

1. Actions tab -> Select a running workflow.
2. Click each job (test, lint, ktlint).
3. Check logs for each step.

### Artifacts

When tests fail, the following artifacts are generated:

- test-results: Test results
- test-report: HTML test report
- lint-reports: Lint reports

## How to Add Tests

### Creating a New Test Class

```kotlin
package so.lai.recalo.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import so.lai.recalo.data.local.CaroliDatabase

@RunWith(RobolectricTestRunner::class)
class YourFeatureTest {

    private lateinit var database: CaroliDatabase
    private lateinit var repository: MealRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, CaroliDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = MealRepository(database.mealDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun yourTestName() = runTest {
        // Arrange
        
        // Act
        
        // Assert
        assertTrue(true)
    }
}
```

### Test File Placement

```
apps/android/app/src/test/java/so/lai/recalo/
├── data/
│   ├── repository/
│   │   └── YourRepositoryTest.kt
│   └── api/
│       └── YourApiTest.kt
├── ui/
│   └── screens/
│       └── YourScreenTest.kt
└── health/
    └── HealthConnectManagerTest.kt
```

## CI/CD Pipeline

### Test Job

- Environment: Ubuntu latest
- JDK: 17 (Temurin)
- Timeout: 30 minutes
- Cache: Gradle cache enabled

### Steps

1. Checkout code
2. Setup JDK 17
3. Grant execute permission to gradlew
4. Run unit tests
5. Upload test results (always)
6. Upload test report (only on failure)

## Troubleshooting

### If Tests Fail

1. Check logs:
   ```bash
   ./gradlew testDevDebugUnitTest --info
   ```

2. Run only specific test:
   ```bash
   ./gradlew testDevDebugUnitTest --tests "so.lai.recalo.YourTest"
   ```

3. Clear build cache:
   ```bash
   ./gradlew clean testDevDebugUnitTest
   ```

### Lint Errors

1. Check error details:
   ```bash
   ./gradlew lint
   cat apps/android/app/build/reports/lint-results.html
   ```

2. Common fixes:
   - Remove unused imports
   - Remove unused variables
   - Split long methods

### ktlint Errors

1. Try auto-fix:
   ```bash
   ./gradlew ktlintFormat
   ```

2. Manual fixes:
   - Keep line length under 100 characters
   - Sort imports alphabetically
   - Remove trailing whitespace

## Replicating GitHub Actions Locally

You can use [act](https://github.com/nektos/act) to run GitHub Actions locally:

```bash
# Install act (macOS)
brew install act

# Run all workflows
act

# Run specific workflow
act -j test

# Verbose logs
act -v
```

## Best Practices

1. Run all tests locally before PR
   ```bash
   ./scripts/test.sh --all
   ```

2. Maintain test coverage
   - Always add tests for new features
   - Ensure existing tests pass during refactoring

3. Maintain green CI
   - Do not merge PR if CI is red
   - Fix failed tests immediately

4. Test naming convention
   - `Feature_Condition_ExpectedResult` format
   - Example: `updatePortionRatio_shouldScaleItemCalories`
