package pe.upt.mallasos.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pe.upt.mallasos.data.datastore.TestConfig
import java.util.concurrent.CopyOnWriteArrayList

class TrafficGeneratorTest {

    private val sizes = CopyOnWriteArrayList<Int>()
    private val active = TestConfig(trafficEnabled = true, trafficEverySeconds = 1, trafficSizeBytes = 100)

    private fun running(config: MutableStateFlow<TestConfig>, body: suspend () -> Unit) = runBlocking {
        val generator = TrafficGenerator(config, { sizes += it }, secondMs = 5)
        val job = launch(Dispatchers.Default) { generator.run() }
        try {
            withTimeout(3_000) { body() }
        } finally {
            job.cancelAndJoin()
        }
    }

    @Test
    fun `apagado no genera nada`() {
        running(MutableStateFlow(TestConfig())) { delay(40) }
        assertTrue(sizes.isEmpty())
    }

    @Test
    fun `activo genera mensajes del tamano configurado`() {
        running(MutableStateFlow(active)) {
            while (sizes.size < 3) delay(2)
        }
        assertEquals(setOf(100), sizes.toSet())
    }

    @Test
    fun `apagarlo detiene la generacion`() {
        val config = MutableStateFlow(active)
        running(config) {
            while (sizes.isEmpty()) delay(2)
            config.value = TestConfig()
            delay(10)
            val stopped = sizes.size
            delay(40)
            assertEquals(stopped, sizes.size)
        }
    }

    @Test
    fun `el tamano nunca supera el contenido maximo`() {
        running(MutableStateFlow(active.copy(trafficSizeBytes = 9_000))) {
            while (sizes.isEmpty()) delay(2)
        }
        assertEquals(255, sizes.first())
    }
}
