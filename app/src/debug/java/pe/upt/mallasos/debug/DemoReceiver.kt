package pe.upt.mallasos.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import pe.upt.mallasos.MallaApp
import pe.upt.mallasos.codec.ANY_POST
import pe.upt.mallasos.codec.EmergencyType
import pe.upt.mallasos.codec.Header
import pe.upt.mallasos.codec.MessageType
import pe.upt.mallasos.codec.PayloadCodec
import pe.upt.mallasos.codec.Priority
import pe.upt.mallasos.codec.SosPayload
import pe.upt.mallasos.codec.Uid
import pe.upt.mallasos.data.datastore.DtnMode
import pe.upt.mallasos.data.datastore.NodeMode
import pe.upt.mallasos.data.datastore.QueueMode
import pe.upt.mallasos.dtn.ExchangeConfig
import pe.upt.mallasos.dtn.MessageExchange
import pe.upt.mallasos.p2p.LocalNode
import pe.upt.mallasos.p2p.PeerSession
import pe.upt.mallasos.p2p.SessionRunner
import java.net.ServerSocket

/**
 * Solo en compilaciones debug: un emulador no tiene Wi-Fi Direct, así que esto simula a un vecino
 * por TCP local contra la base de datos real. Sirve para probar `MessageDao` y ver las pantallas:
 *
 *   adb shell am broadcast -n pe.upt.mallasos/.debug.DemoReceiver
 *
 * El resultado se escribe en logcat con la etiqueta `MallaDemo`.
 */
class DemoReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val container = (context.applicationContext as MallaApp).container
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                run(container)
            } catch (error: Exception) {
                Log.e(TAG, "FALLO: $error", error)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun run(container: pe.upt.mallasos.AppContainer) {
        val settings = container.settings
        val identity = settings.requireIdentity()
        val config = settings.testConfig.first()
        val dao = container.database.messages()
        val local = LocalNode(identity.nodeId, identity.alias, identity.mode)
        val exchange = MessageExchange(dao, container.logger, settings::takeSequence)
        val exchangeConfig = ExchangeConfig(
            local, DtnMode.PROPUESTO, QueueMode.PRIORIDAD, config.ttlHours, config.hops,
        )
        val neighbor = LocalNode(0x0B0B0B0B, "Vecino demo", NodeMode.NORMAL)
        val seq = (System.currentTimeMillis() / 1000 and 0xFFFF).toInt()
        val payload = PayloadCodec.encodeSos(
            SosPayload(EmergencyType.ATRAPADO, 3, 62, null, "Pabellón B, piso 2", "Escalera bloqueada"),
        )
        val header = Header(
            MessageType.SOS, Priority.SOS, neighbor.nodeId, seq, ANY_POST,
            System.currentTimeMillis() / 1000, 24, 3, payload.size,
        )
        val port = ServerSocket(0).use { it.localPort }
        val runner = SessionRunner(port = port)

        val neighborSide = CoroutineScope(Dispatchers.IO).async {
            delay(100)
            runner.connect("127.0.0.1", neighbor) { session -> playNeighbor(session, neighbor, header, payload) }
        }
        val phoneSide = runner.serve(local) { session -> exchange.run(session, exchangeConfig) }
        val seen = neighborSide.await()

        val uid = Uid(neighbor.nodeId, seq).hex
        val stored = dao.observeMessage(uid).first()
        Log.i(TAG, "sesion=$phoneSide vecino=$seen fila=${stored?.state} modo=${identity.mode}")
        // El segundo envío comprueba `insertIfNew` con un identificador ya visto.
        val duplicate = dao.insertIfNew(stored ?: return)
        Log.i(TAG, "duplicado_insertado=$duplicate cola=${dao.queued().size}")
        if (dao.confirmDelivered("000000000000", System.currentTimeMillis())) {
            Log.e(TAG, "FALLO: la lápida no debería borrar copias")
        }
        Log.i(TAG, "OK")
    }

    /** Devuelve los tipos que el teléfono envió al vecino. */
    private fun playNeighbor(
        session: PeerSession,
        neighbor: LocalNode,
        header: Header,
        payload: ByteArray,
    ): List<MessageType> {
        val received = mutableListOf<MessageType>()
        session.frames.write(header, payload)
        session.frames.write(
            Header(
                MessageType.SUMMARY, Priority.CONTROL, neighbor.nodeId, 0, session.remote.nodeId,
                1, 0, 0, 0,
            ),
            ByteArray(0),
        )
        var finished = false
        while (true) {
            val frame = session.frames.readOrNull() ?: return received
            received += frame.header.type
            when (frame.header.type) {
                MessageType.SUMMARY -> {
                    session.finishSending()
                    finished = true
                }
                MessageType.ACK_HOP -> Unit
                else -> if (!finished) {
                    val ack = PayloadCodec.encodeAckHop(frame.header.uid)
                    session.frames.write(
                        Header(
                            MessageType.ACK_HOP, Priority.CONTROL, neighbor.nodeId, 0,
                            session.remote.nodeId, 1, 0, 0, ack.size,
                        ),
                        ack,
                    )
                }
            }
        }
    }

    private companion object {
        const val TAG = "MallaDemo"
    }
}
