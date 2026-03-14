package so.lai.recalo.data.api

/**
 * AI モデルの構成管理
 * * 料金情報源：https://openai.com/ja-JP/api/pricing/
 * （2026 年 3 月 14 日現在）
 * * 写真 1 枚あたりの推定価格：
 * - Android 端末の写真を 1024x1024 にリサイズ
 * - 画像トークン：約 765 tokens
 * - プロンプト：約 200 tokens
 * - 出力：約 500 tokens
 * - 合計：約 1,500 tokens/枚
 */
object AiConfig {
    // モデルレベルの定義
    const val LEVEL_LOW = "low"
    const val LEVEL_MEDIUM = "medium"
    const val LEVEL_HIGH = "high"

    // 各レベルに対応する OpenAI モデル ID
    const val MODEL_LOW = "gpt-4o-mini"
    const val MODEL_MEDIUM = "gpt-5-mini"
    const val MODEL_HIGH = "gpt-5.4"

    // 表示用のラベルとコスト情報
    data class ModelOption(
        val level: String,
        val label: String,
        val description: String,
        val modelId: String
    )

    val options = listOf(
        ModelOption(
            level = LEVEL_LOW,
            label = "Low (gpt-4o-mini)",
            description = "~$0.0004 / image",
            modelId = MODEL_LOW
        ),
        ModelOption(
            level = LEVEL_MEDIUM,
            label = "Medium (gpt-5-mini)",
            description = "~$0.001 / image",
            modelId = MODEL_MEDIUM
        ),
        ModelOption(
            level = LEVEL_HIGH,
            label = "High (gpt-5.4)",
            description = "~$0.02 / image",
            modelId = MODEL_HIGH
        )
    )

    fun getModelId(level: String): String {
        return options.find { it.level == level }?.modelId ?: MODEL_LOW
    }
}
