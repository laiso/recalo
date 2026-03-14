# テスト実行ガイド

## ローカルでのテスト実行

### スクリプトを使用（推奨）

```bash
# すべてのチェックを実行
./scripts/test.sh

# または
./scripts/test.sh --all

# テストのみ実行
./scripts/test.sh --tests

# Lint のみ実行
./scripts/test.sh --lint

# ktlint のみ実行
./scripts/test.sh --ktlint

# 複数実行
./scripts/test.sh --tests --lint

# ヘルプ表示
./scripts/test.sh --help
```

### Gradle コマンド直接使用

```bash
cd apps/android

# 全ユニットテスト
./gradlew testDevDebugUnitTest

# 特定のテストクラス
./gradlew testDevDebugUnitTest --tests "so.lai.recalo.data.repository.MealRepositoryPortionRatioTest"

# 特定のパッケージ
./gradlew testDevDebugUnitTest --tests "so.lai.recalo.data.repository.*"

# Lint
./gradlew lint

# ktlint
./gradlew ktlintCheck

# ktlint 自動修正
./gradlew ktlintFormat
```

### テストレポート

テスト実行後、以下のレポートが生成されます：

- HTML レポート: apps/android/app/build/reports/tests/testDevDebugUnitTest/index.html
- Lint レポート: apps/android/app/build/reports/lint-results.html

## GitHub Actions でのテスト実行

### 自動実行

GitHub Actions は以下のタイミングで自動実行されます：

- main ブランチへのプッシュ
- main ブランチへの PR

### 手動実行

1. GitHub リポジトリの Actions タブを開く
2. Android CI ワークフローを選択
3. Run workflow ボタンをクリック
4. ブランチを選択して実行

### ワークフローログの確認

1. Actions タブ -> 実行中のワークフローを選択
2. 各ジョブ（test, lint, ktlint）をクリック
3. ステップごとのログを確認

### アーティファクト

テスト失敗時、以下のアーティファクトが生成されます：

- test-results: テスト結果
- test-report: HTML テストレポート
- lint-reports: Lint レポート

## テストの追加方法

### 新しいテストクラスの作成

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

### テストファイルの配置

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

## CI/CD パイプライン

### テストジョブ

- 環境: Ubuntu latest
- JDK: 17 (Temurin)
- タイムアウト: 30 分
- キャッシュ: Gradle キャッシュ有効

### ステップ

1. コードのチェックアウト
2. JDK 17 のセットアップ
3. gradlew の実行権限付与
4. ユニットテスト実行
5. テスト結果のアップロード（常に）
6. テストレポートのアップロード（失敗時のみ）

## トラブルシューティング

### テストが失敗する場合

1. ログを確認:
   ```bash
   ./gradlew testDevDebugUnitTest --info
   ```

2. 特定のテストのみ実行:
   ```bash
   ./gradlew testDevDebugUnitTest --tests "so.lai.recalo.YourTest"
   ```

3. ビルドキャッシュをクリア:
   ```bash
   ./gradlew clean testDevDebugUnitTest
   ```

### Lint エラー

1. エラー詳細を確認:
   ```bash
   ./gradlew lint
   cat apps/android/app/build/reports/lint-results.html
   ```

2. 一般的な修正:
   - 未使用の import を削除
   - 未使用の変数を削除
   - メソッドが長すぎる場合は分割

### ktlint エラー

1. 自動修正を試行:
   ```bash
   ./gradlew ktlintFormat
   ```

2. 手動修正:
   - 行長 100 文字以内に収める
   - import を辞書順に並べる
   - 末尾の空白を削除

## ローカルで GitHub Actions を再現

act を使用してローカルで GitHub Actions を実行できます：

```bash
# act のインストール（macOS）
brew install act

# 全ワークフローを実行
act

# 特定のワークフローを実行
act -j test

# 詳細ログ
act -v
```

## ベストプラクティス

1. PR 前にローカルで全テストを実行
   ```bash
   ./scripts/test.sh --all
   ```

2. テストカバレッジを維持
   - 新しい機能には必ずテストを追加
   - リファクタリング時は既存テストが通ることを確認

3. CI の green を維持
   - PR が red の場合はマージしない
   - 失敗したテストはすぐに修正

4. テストの命名規則
   - 機能_条件_期待結果 形式
   - 例：updatePortionRatio_shouldScaleItemCalories
