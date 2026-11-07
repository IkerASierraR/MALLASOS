package pe.upt.mallasos.p2p

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class ContactSchedulerTest {

    private val group = P2pGroup(isGroupOwner = false, ownerAddress = "192.168.49.1")
    private val ana = P2pPeer("aa:aa", "Ana", PEER_STATUS_AVAILABLE)
    private val beto = P2pPeer("bb:bb", "Beto", PEER_STATUS_AVAILABLE)

    private class FakeLink(initial: P2pState) : P2pLink {
        val flow = MutableStateFlow(initial)
        override val state: StateFlow<P2pState> = flow
        val discoveries = AtomicInteger()
        val connects = CopyOnWriteArrayList<String>()
        val disconnects = AtomicInteger()
        var formsGroup: P2pGroup? = null
        var failConnect = false
        var failDiscover = false

        override suspend fun discover() {
            discoveries.incrementAndGet()
            if (failDiscover) throw P2pActionException("discoverPeers", 2)
        }

        override suspend fun connect(peer: P2pPeer) {
            connects += peer.address
            if (failConnect) throw P2pActionException("connect", 2)
            flow.update { it.copy(group = formsGroup) }
        }

        override suspend fun disconnect() {
            disconnects.incrementAndGet()
            flow.update { it.copy(group = null) }
        }
    }

    private val clock = AtomicLong(1_000_000)
    private val timings = ContactTimings(
        discoveryCycleMs = 20,
        cooldownMs = 60_000,
        groupTimeoutMs = 30,
        sessionTimeoutMs = 40,
        disconnectTimeoutMs = 30,
        maxJitterMs = 0,
    )
    private val sessions = CopyOnWriteArrayList<P2pGroup>()
    private val recording = ContactSession { sessions += it }

    private fun scheduler(link: P2pLink, session: ContactSession = recording) =
        ContactScheduler(link, session, timings, clock::get)

    /** Ejecuta el planificador hasta que se cumple [done]. */
    private fun running(scheduler: ContactScheduler, done: suspend () -> Boolean) = runBlocking {
        val job = CoroutineScope(Dispatchers.Default).launch { scheduler.run() }
        try {
            withTimeout(3_000) {
                while (!done()) delay(5)
            }
        } finally {
            job.cancelAndJoin()
        }
    }

    @Test
    fun `conecta con un vecino, ejecuta la sesion y desconecta`() {
        val link = FakeLink(P2pState(enabled = true, peers = listOf(ana)))
        link.formsGroup = group
        running(scheduler(link)) { link.disconnects.get() > 0 }

        assertEquals(listOf("aa:aa"), link.connects.toList())
        assertEquals(listOf(group), sessions.toList())
        assertNull(link.flow.value.group)
    }

    @Test
    fun `no repite un vecino durante el enfriamiento`() {
        val link = FakeLink(P2pState(enabled = true, peers = listOf(ana)))
        link.formsGroup = group
        running(scheduler(link)) { link.discoveries.get() >= 5 }
        assertEquals(1, link.connects.size)
    }

    @Test
    fun `vuelve a contactarlo cuando vence el enfriamiento`() {
        val link = FakeLink(P2pState(enabled = true, peers = listOf(ana)))
        link.formsGroup = group
        running(scheduler(link)) {
            if (link.connects.size == 1) clock.addAndGet(60_000)
            link.connects.size == 2
        }
        assertEquals(2, sessions.size)
    }

    @Test
    fun `pasa al siguiente vecino disponible`() {
        val link = FakeLink(P2pState(enabled = true, peers = listOf(ana, beto)))
        link.formsGroup = group
        running(scheduler(link)) { link.connects.size == 2 }
        assertEquals(listOf("aa:aa", "bb:bb"), link.connects.toList())
    }

    @Test
    fun `atiende un grupo formado por el otro telefono sin llamarlo`() {
        val connected = ana.copy(status = PEER_STATUS_CONNECTED)
        val link = FakeLink(P2pState(enabled = true, peers = listOf(connected), group = group))
        val scheduler = scheduler(link)
        running(scheduler) { link.disconnects.get() > 0 }

        assertEquals(listOf(group), sessions.toList())
        assertTrue(link.connects.isEmpty())

        // Tras la sesión, ese vecino queda disponible pero sigue en enfriamiento.
        link.flow.value = P2pState(enabled = true, peers = listOf(ana))
        val discoveries = link.discoveries.get()
        running(scheduler) { link.discoveries.get() >= discoveries + 3 }
        assertTrue(link.connects.isEmpty())
    }

    @Test
    fun `un grupo que aparece durante la busqueda tambien se atiende`() {
        val link = FakeLink(P2pState(enabled = true))
        running(scheduler(link)) {
            if (link.discoveries.get() >= 2) link.flow.update { it.copy(group = group) }
            sessions.isNotEmpty()
        }
        assertEquals(group, sessions.first())
    }

    @Test
    fun `si connect falla sigue buscando y enfria a ese vecino`() {
        val link = FakeLink(P2pState(enabled = true, peers = listOf(ana)))
        link.failConnect = true
        running(scheduler(link)) { link.discoveries.get() >= 4 }
        assertEquals(1, link.connects.size)
        assertTrue(sessions.isEmpty())
    }

    @Test
    fun `si el grupo no se forma cancela la conexion`() {
        val link = FakeLink(P2pState(enabled = true, peers = listOf(ana)))
        running(scheduler(link)) { link.disconnects.get() > 0 }
        assertTrue(sessions.isEmpty())
    }

    @Test
    fun `una sesion colgada se corta por tiempo y desconecta`() {
        val link = FakeLink(P2pState(enabled = true, peers = listOf(ana)))
        link.formsGroup = group
        val hung = ContactSession { delay(60_000) }
        running(scheduler(link, hung)) { link.disconnects.get() > 0 }
        assertNull(link.flow.value.group)
    }

    @Test
    fun `sigue intentando si la busqueda falla`() {
        val link = FakeLink(P2pState(enabled = true))
        link.failDiscover = true
        running(scheduler(link)) { link.discoveries.get() >= 3 }
    }

    @Test
    fun `espera a que Wi-Fi Direct este encendido`() {
        val link = FakeLink(P2pState(enabled = false))
        val scheduler = scheduler(link)
        runBlocking {
            val job = CoroutineScope(Dispatchers.Default).launch { scheduler.run() }
            delay(60)
            assertEquals(0, link.discoveries.get())
            job.cancelAndJoin()
        }
        link.flow.update { it.copy(enabled = true) }
        running(scheduler) { link.discoveries.get() > 0 }
    }

    @Test
    fun `al detener el servicio cierra el grupo abierto`() = runBlocking {
        val link = FakeLink(P2pState(enabled = true, peers = listOf(ana)))
        link.formsGroup = group
        val hung = ContactSession { delay(60_000) }
        val job = CoroutineScope(Dispatchers.Default).launch { scheduler(link, hung).run() }
        withTimeout(1_000) {
            while (link.flow.value.group == null) delay(1)
        }
        job.cancelAndJoin()
        assertEquals(1, link.disconnects.get())
    }

    @Test
    fun `selectPeer ignora a quien no esta disponible`() {
        val busy = ana.copy(status = PEER_STATUS_CONNECTED)
        assertNull(selectPeer(listOf(busy), emptyMap(), 0, 60_000))
    }

    @Test
    fun `selectPeer respeta el limite exacto del enfriamiento`() {
        val contacted = mapOf(ana.address to 1_000L)
        assertNull(selectPeer(listOf(ana), contacted, 60_999, 60_000))
        assertEquals(ana, selectPeer(listOf(ana), contacted, 61_000, 60_000))
    }
}
