package pe.upt.mallasos.lora

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Estado del enlace Bluetooth LE entre el teléfono del Rescatista y la placa LoRa. */
enum class LoraLinkState(val label: String) {
    SIN_PLACA("Sin placa configurada"),
    DESCONECTADO("Desconectado"),
    BUSCANDO("Buscando la placa"),
    CONECTADO("Conectado"),
}

/** Qué se envía al puesto. El formato en bytes de cada tipo se definirá con la placa. */
enum class LoraReportType(val label: String) {
    SOS("SOS reenviado"),
    ZONA("Estado de zona"),
    RECURSOS("Pedido de recursos"),
    OTRO("Otro"),
}

enum class LoraSendStatus(val label: String) {
    EN_ESPERA("Esperando la placa"),
    ENVIADO("Enviado por LoRa"),
    FALLIDO("Falló el envío"),
}

/** Lo que el Rescatista manda al puesto; [sosUid] enlaza el SOS de la malla que se reenvía. */
data class LoraReport(
    val id: Long,
    val type: LoraReportType,
    val people: Int,
    val text: String,
    val sosUid: String? = null,
    val createdAt: Long,
    val status: LoraSendStatus = LoraSendStatus.EN_ESPERA,
)

/**
 * Enlace con la placa ESP32 + LoRa por Bluetooth LE. La implementación real llegará cuando la
 * placa defina su servicio GATT y el formato de cada tipo de reporte.
 */
interface LoraLink {
    val state: StateFlow<LoraLinkState>
    suspend fun connect()
    suspend fun disconnect()

    /** `true` si la placa aceptó el reporte para transmitirlo. */
    suspend fun send(report: LoraReport): Boolean
}

/** Mientras no exista la placa: la pantalla funciona y los reportes quedan en espera. */
class PendingBoardLink : LoraLink {
    override val state: StateFlow<LoraLinkState> = MutableStateFlow(LoraLinkState.SIN_PLACA)
    override suspend fun connect() = Unit
    override suspend fun disconnect() = Unit
    override suspend fun send(report: LoraReport): Boolean = false
}

/**
 * Cola de salida LoRa del Rescatista. Envía en cuanto la placa está conectada y, si no, guarda el
 * reporte en espera hasta [flush]. Vive en memoria: se pierde al cerrar la app.
 */
class LoraGateway(
    val link: LoraLink,
    private val clockMs: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()
    private var nextId = 1L
    private val _outbox = MutableStateFlow<List<LoraReport>>(emptyList())

    /** Lo más reciente primero. */
    val outbox: StateFlow<List<LoraReport>> = _outbox.asStateFlow()

    suspend fun submit(
        type: LoraReportType,
        people: Int,
        text: String,
        sosUid: String? = null,
    ): LoraReport {
        val report = mutex.withLock {
            LoraReport(nextId++, type, people, text.trim(), sosUid, clockMs())
                .also { added -> _outbox.update { listOf(added) + it } }
        }
        return if (link.state.value == LoraLinkState.CONECTADO) deliver(report) else report
    }

    /** Reintenta lo que esté en espera o haya fallado; se llama al conectar la placa. */
    suspend fun flush() {
        if (link.state.value != LoraLinkState.CONECTADO) return
        _outbox.value
            .filter { it.status != LoraSendStatus.ENVIADO }
            .sortedBy { it.id }
            .forEach { deliver(it) }
    }

    /** Envía cada vez que la placa pasa a conectada. Se ejecuta hasta que se cancela. */
    suspend fun run() {
        while (true) {
            link.state.first { it == LoraLinkState.CONECTADO }
            flush()
            link.state.first { it != LoraLinkState.CONECTADO }
        }
    }

    private suspend fun deliver(report: LoraReport): LoraReport {
        val status = if (link.send(report)) LoraSendStatus.ENVIADO else LoraSendStatus.FALLIDO
        val updated = report.copy(status = status)
        _outbox.update { list -> list.map { if (it.id == report.id) updated else it } }
        return updated
    }
}
