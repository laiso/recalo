package so.lai.recalo.health

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class HealthConnectRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { RationaleScreen { finish() } }
    }
}

@Composable
private fun RationaleScreen(onDone: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Health Connect access is needed to write nutrition results.",
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            text = "You can revoke access any time in Health Connect settings.",
            modifier = Modifier.padding(top = 12.dp)
        )
        Button(
            onClick = onDone,
            modifier = Modifier.padding(top = 20.dp)
        ) {
            Text(text = "OK")
        }
    }
}
