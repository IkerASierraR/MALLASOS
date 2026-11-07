package pe.upt.mallasos

import android.app.Application

class MallaApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
