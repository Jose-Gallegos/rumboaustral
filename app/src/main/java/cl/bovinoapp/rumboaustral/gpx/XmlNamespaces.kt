package cl.bovinoapp.rumboaustral.gpx

/**
 * Resolución de prefijos de namespace XML, aparte del parser de Android para poder probarla.
 *
 * El parser se usa sin procesamiento de namespaces (es más tolerante con archivos de otras apps: un prefijo sin
 * declarar no lo rompe), así que los prefijos se resuelven aquí a partir de las declaraciones `xmlns` de los
 * elementos de la ruta hacia el elemento que interesa.
 */
internal object XmlNamespaces {
    /** Declaraciones `xmlns` / `xmlns:p` de un elemento, a partir de sus atributos (nombre, valor). Clave "" = por defecto. */
    fun declarations(attributes: List<Pair<String, String>>): Map<String, String> =
        attributes.mapNotNull { (name, value) ->
            when {
                name == "xmlns" -> "" to value
                name.startsWith("xmlns:") -> name.removePrefix("xmlns:") to value
                else -> null
            }
        }.toMap()

    /**
     * URI del namespace de [qualifiedName] ("rumbo:label" o "label"), buscando su prefijo (o el namespace por
     * defecto si no tiene) en [scopes], del elemento más interno al más externo. Null si no está declarado.
     */
    fun resolve(qualifiedName: String, scopes: List<Map<String, String>>): String? {
        val prefix = if (':' in qualifiedName) qualifiedName.substringBefore(':') else ""
        return scopes.firstNotNullOfOrNull { it[prefix] }
    }
}
