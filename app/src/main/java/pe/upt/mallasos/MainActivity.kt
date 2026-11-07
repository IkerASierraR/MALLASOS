package pe.upt.mallasos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import pe.upt.mallasos.ui.MallaRoot
import pe.upt.mallasos.ui.theme.MallaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as MallaApp).container
        setContent {
            MallaTheme {
                MallaRoot(container)
            }
        }
    }
}
