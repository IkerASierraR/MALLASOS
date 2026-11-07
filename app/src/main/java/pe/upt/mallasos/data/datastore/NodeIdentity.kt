package pe.upt.mallasos.data.datastore

import kotlin.random.Random

/** ID de nodo aleatorio; `FFFFFFFF` y `00000000` están reservados (`spec.md` §6). */
fun generateNodeId(random: Random = Random.Default): Int {
    while (true) {
        val id = random.nextInt()
        if (id != 0 && id != -1) return id
    }
}

/** Presentación en pantalla: `7A3F·91C2`. */
fun formatNodeId(id: Int): String {
    val hex = "%08X".format(id)
    return "${hex.take(4)}·${hex.drop(4)}"
}

/** La secuencia ocupa 2 bytes y vuelve a 0 tras `FFFF`. */
fun nextSequence(current: Int): Int = (current + 1) and 0xFFFF
