package cl.bovinoapp.rumboaustral.data

/** Reglas puras para llevar etiquetas de un archivo a la base (sin Android, para poder probarlas). */
object LabelImport {
    const val MAX_LABEL_NAME = 60

    /**
     * Clave de identidad de un nombre de etiqueta: recortado y sin distinguir mayúsculas. Es la MISMA regla con
     * que el administrador de etiquetas rechaza duplicados; así un respaldo no fusiona etiquetas que la app
     * considera distintas ("A-1" y "A1") ni deja pasar como distintas dos que no pueden coexistir.
     */
    fun key(name: String): String = name.trim().lowercase()

    /**
     * Limpia las etiquetas leídas de un archivo: nombres recortados (sin tocar su contenido), sin vacíos y a lo
     * sumo una por categoría (gana la última).
     */
    fun sanitize(refs: List<LabelRef>): List<LabelRef> =
        refs.map { it.copy(name = it.name.trim().take(MAX_LABEL_NAME).trim()) }
            .filter { key(it.name).isNotEmpty() }
            .associateBy { it.kind }
            .values.toList()

    /** Referencias que aún no existen en [existing]; sin repetidos por categoría+nombre (se conserva el primer color). */
    fun missing(refs: Collection<LabelRef>, existing: List<Label>): List<LabelRef> {
        val known = existing.mapTo(HashSet()) { it.kind to key(it.name) }
        return refs.filter { key(it.name).isNotEmpty() }
            .distinctBy { it.kind to key(it.name) }
            .filter { (it.kind to key(it.name)) !in known }
    }
}
