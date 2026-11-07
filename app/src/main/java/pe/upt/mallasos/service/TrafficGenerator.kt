package pe.upt.mallasos.service

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import pe.upt.mallasos.codec.MAX_PAYLOAD_SIZE
import pe.upt.mallasos.data.datastore.TestConfig

/** Crea mensajes `NORMAL` mientras esté activo, para el tráfico mixto de E3 (`spec.md` H2). */
class TrafficGenerator(
    private val config: Flow<TestConfig>,
    private val create: suspend (sizeBytes: Int) -> Unit,
    private val secondMs: Long = 1_000,
) {
    private data class Plan(val enabled: Boolean, val everySeconds: Int, val sizeBytes: Int)

    /** Se ejecuta hasta que se cancela; un cambio de configuración reinicia la espera. */
    suspend fun run() {
        config
            .map { Plan(it.trafficEnabled, it.trafficEverySeconds, it.trafficSizeBytes) }
            .distinctUntilChanged()
            .collectLatest { plan ->
                while (plan.enabled) {
                    delay(plan.everySeconds.coerceAtLeast(1) * secondMs)
                    create(plan.sizeBytes.coerceIn(0, MAX_PAYLOAD_SIZE))
                }
            }
    }
}
