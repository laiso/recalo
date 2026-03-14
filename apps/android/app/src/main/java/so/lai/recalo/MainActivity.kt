package so.lai.recalo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import so.lai.recalo.data.api.SessionManager
import so.lai.recalo.ui.screens.HomeScreen

// 緑ベースのカラースキーム
private val GreenColorScheme = lightColorScheme(
    primary = Color(0xFF1B8A5D),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF1B8A5D),
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF2E9E6F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD5F5E3),
    onSecondaryContainer = Color(0xFF1B8A5D),
    background = Color(0xFFF5F5F5),
    onBackground = Color(0xFF1C1B1F),
    surface = Color.White,
    onSurface = Color(0xFF1C1B1F),
    onSurfaceVariant = Color(0xFF666666),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B)
)

@Composable
fun CaroliTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = GreenColorScheme,
        content = content
    )
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sessionManager = SessionManager(this)

        setContent {
            CaroliTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    HomeScreen(sessionManager = sessionManager)
                }
            }
        }
    }
}
