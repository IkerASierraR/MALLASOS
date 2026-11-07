package pe.upt.mallasos.log

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pe.upt.mallasos.data.db.EventDao
import pe.upt.mallasos.data.db.EventEntity
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Formato del CSV de `plan.md` §6. Cambiarlo rompe los scripts de análisis. */
object CsvFormat {
    const val HEADER =
        "ts_ms,run_label,node_id,event,uid,peer_id,hops_left,dtn_mode,queue_mode,go_intent,extra"

    fun row(event: EventEntity): String = listOf(
        event.tsMs.toString(),
        event.runLabel,
        nodeHex(event.nodeId),
        event.event,
        event.uid.orEmpty(),
        event.peerId?.let(::nodeHex).orEmpty(),
        event.hopsLeft?.toString().orEmpty(),
        event.dtnMode,
        event.queueMode,
        event.goIntent,
        event.extra.orEmpty(),
    ).joinToString(",", transform = ::field)

    fun document(events: List<EventEntity>): String =
        (listOf(HEADER) + events.map(::row)).joinToString(separator = "\n", postfix = "\n")

    fun nodeHex(id: Int): String = "%08X".format(id)

    private fun field(value: String): String =
        if (value.any { it in ",\"\n\r" }) "\"${value.replace("\"", "\"\"")}\"" else value
}

/**
 * [shareUri] sirve para la hoja de compartir. [savedToDownloads] es `false` en API 26 a 28, donde
 * escribir en Descargas requeriría un permiso más: allí el archivo solo se comparte.
 */
data class CsvExport(val shareUri: Uri, val fileName: String, val rows: Int, val savedToDownloads: Boolean)

class CsvExporter(private val context: Context, private val events: EventDao) {

    suspend fun export(nodeId: Int): CsvExport = withContext(Dispatchers.IO) {
        val rows = events.all()
        val bytes = CsvFormat.document(rows).toByteArray()
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
        val name = "malla-sos-${CsvFormat.nodeHex(nodeId)}-$stamp.csv"

        val folder = File(context.cacheDir, "csv").apply { mkdirs() }
        folder.listFiles()?.forEach(File::delete)
        val file = File(folder, name).apply { writeBytes(bytes) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        CsvExport(uri, name, rows.size, saveToDownloads(name, bytes))
    }

    private fun saveToDownloads(name: String, bytes: ByteArray): Boolean {
        if (Build.VERSION.SDK_INT < 29) return false
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "text/csv")
            put(MediaStore.Downloads.RELATIVE_PATH, "Download/MALLA-SOS")
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
        return resolver.openOutputStream(uri)?.use { it.write(bytes) } != null
    }
}
