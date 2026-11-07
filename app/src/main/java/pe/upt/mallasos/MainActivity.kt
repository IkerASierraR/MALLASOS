package pe.upt.mallasos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import pe.upt.mallasos.ui.MallaRoot
import pe.upt.mallasos.ui.theme.MallaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as MallaApp).container
        setContent {
            MallaTheme {
                // Surface fija el color de texto del tema; sin ella sería negro en modo oscuro.
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MallaRoot(container)
                }
            }
        }
    }
}
