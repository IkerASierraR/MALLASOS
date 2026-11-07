package pe.upt.mallasos.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class NodeMode(val code: Int, val label: String) {
    NORMAL(0, "Normal"),
    PUESTO(1, "Puesto"),
}

enum class DtnMode { PROPUESTO, BASICO }
enum class QueueMode { PRIORIDAD, FIFO }

data class Identity(
    val nodeId: Int,
    val alias: String,
    val mode: NodeMode,
    val onboarded: Boolean,
)

data class TestConfig(
    val dtnMode: DtnMode = DtnMode.PROPUESTO,
    val queueMode: QueueMode = QueueMode.PRIORIDAD,
    val goIntent: String = "AUTO",
    val trafficEnabled: Boolean = false,
    val trafficEverySeconds: Int = 5,
    val trafficSizeBytes: Int = 100,
    val runLabel: String = "",
    val ttlHours: Int = 24,
    val hops: Int = 5,
    /** Decisión D1: cierra el socket tras N bytes de mensajes; 0 = apagado. */
    val cutAfterBytes: Int = 0,
)

private val Context.dataStore by preferencesDataStore("malla_settings")

/** Identidad del nodo y configuración de pruebas (`plan.md` §5). */
class SettingsStore(private val context: Context) {

    private object Keys {
        val NODE_ID = intPreferencesKey("node_id")
        val ALIAS = stringPreferencesKey("alias")
        val MODE = stringPreferencesKey("mode")
        val ONBOARDED = booleanPreferencesKey("onboarded")
        val SEQUENCE = intPreferencesKey("sequence")
        val DTN_MODE = stringPreferencesKey("dtn_mode")
        val QUEUE_MODE = stringPreferencesKey("queue_mode")
        val GO_INTENT = stringPreferencesKey("go_intent")
        val TRAFFIC_ENABLED = booleanPreferencesKey("traffic_enabled")
        val TRAFFIC_EVERY = intPreferencesKey("traffic_every_seconds")
        val TRAFFIC_SIZE = intPreferencesKey("traffic_size_bytes")
        val RUN_LABEL = stringPreferencesKey("run_label")
        val TTL_HOURS = intPreferencesKey("ttl_hours")
        val HOPS = intPreferencesKey("hops")
        val CUT_AFTER = intPreferencesKey("cut_after_bytes")
    }

    /** Emite `null` solo hasta que se genera el ID la primera vez. */
    val identity: Flow<Identity?> = context.dataStore.data.map { prefs ->
        prefs[Keys.NODE_ID]?.let { id ->
            Identity(
                nodeId = id,
                alias = prefs[Keys.ALIAS].orEmpty(),
                mode = enumOr(prefs[Keys.MODE], NodeMode.NORMAL),
                onboarded = prefs[Keys.ONBOARDED] ?: false,
            )
        }
    }

    val testConfig: Flow<TestConfig> = context.dataStore.data.map(::readConfig)

    private fun readConfig(prefs: Preferences): TestConfig {
        val defaults = TestConfig()
        return TestConfig(
            dtnMode = enumOr(prefs[Keys.DTN_MODE], defaults.dtnMode),
            queueMode = enumOr(prefs[Keys.QUEUE_MODE], defaults.queueMode),
            goIntent = prefs[Keys.GO_INTENT] ?: defaults.goIntent,
            trafficEnabled = prefs[Keys.TRAFFIC_ENABLED] ?: defaults.trafficEnabled,
            trafficEverySeconds = prefs[Keys.TRAFFIC_EVERY] ?: defaults.trafficEverySeconds,
            trafficSizeBytes = prefs[Keys.TRAFFIC_SIZE] ?: defaults.trafficSizeBytes,
            runLabel = prefs[Keys.RUN_LABEL] ?: defaults.runLabel,
            ttlHours = prefs[Keys.TTL_HOURS] ?: defaults.ttlHours,
            hops = prefs[Keys.HOPS] ?: defaults.hops,
            cutAfterBytes = prefs[Keys.CUT_AFTER] ?: defaults.cutAfterBytes,
        )
    }

    /** Los cambios de la configuración oculta se aplican al instante (`design.md` §4.8). */
    suspend fun updateTestConfig(change: (TestConfig) -> TestConfig) {
        context.dataStore.edit { prefs ->
            val config = change(readConfig(prefs))
            prefs[Keys.DTN_MODE] = config.dtnMode.name
            prefs[Keys.QUEUE_MODE] = config.queueMode.name
            prefs[Keys.GO_INTENT] = config.goIntent
            prefs[Keys.TRAFFIC_ENABLED] = config.trafficEnabled
            prefs[Keys.TRAFFIC_EVERY] = config.trafficEverySeconds
            prefs[Keys.TRAFFIC_SIZE] = config.trafficSizeBytes
            prefs[Keys.RUN_LABEL] = config.runLabel
            prefs[Keys.TTL_HOURS] = config.ttlHours
            prefs[Keys.HOPS] = config.hops
            prefs[Keys.CUT_AFTER] = config.cutAfterBytes
        }
    }

    suspend fun setMode(mode: NodeMode) {
        context.dataStore.edit { prefs -> prefs[Keys.MODE] = mode.name }
    }

    /** Genera el ID una sola vez, al instalar la app. */
    suspend fun ensureNodeId() {
        context.dataStore.edit { prefs ->
            if (prefs[Keys.NODE_ID] == null) prefs[Keys.NODE_ID] = generateNodeId()
        }
    }

    suspend fun completeOnboarding(alias: String, mode: NodeMode) {
        context.dataStore.edit { prefs ->
            prefs[Keys.ALIAS] = alias
            prefs[Keys.MODE] = mode.name
            prefs[Keys.ONBOARDED] = true
        }
    }

    /** Reserva el siguiente número de secuencia de forma atómica. */
    suspend fun takeSequence(): Int {
        var taken = 0
        context.dataStore.edit { prefs ->
            taken = prefs[Keys.SEQUENCE] ?: 0
            prefs[Keys.SEQUENCE] = nextSequence(taken)
        }
        return taken
    }

    suspend fun requireIdentity(): Identity = checkNotNull(identity.first()) {
        "Identidad sin inicializar"
    }

    private inline fun <reified T : Enum<T>> enumOr(name: String?, default: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: default
}
