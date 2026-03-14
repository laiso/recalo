
## テスト実行

### クイックスタート

```bash
# すべてのチェックを実行（推奨）
./scripts/test.sh

# テストのみ実行
./gradlew testDevDebugUnitTest

# 特定のテスト
./gradlew testDevDebugUnitTest --tests "so.lai.recalo.data.repository.MealRepositoryPortionRatioTest"
```

詳細は docs/TESTING.md を参照してください。

## CI/CD

GitHub Actions が自動でテストを実行します：

- ユニットテスト
- Lint
- ktlint

詳細は [.github/workflows/android-ci.yml](../.github/workflows/android-ci.yml) を参照してください。
