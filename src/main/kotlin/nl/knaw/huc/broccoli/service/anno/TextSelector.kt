package nl.knaw.huc.broccoli.service.anno

data class TextSelector(private val context: Map<String, Any>) {
    fun start(): Int = context["start"] as Int
    fun beginCharOffset(): Int? = context["beginCharOffset"] as Int?
    fun end(): Int = context["end"] as Int
    fun endCharOffset(): Int? = context["endCharOffset"] as Int?
}
