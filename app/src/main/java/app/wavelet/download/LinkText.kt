package app.wavelet.download

/** Pulls links and yt-dlp arguments out of text the user pasted or shared. */
object LinkText {
    private val url = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)

    fun urls(text: String): List<String> = url.findAll(text)
        .map { it.value.trimEnd('.', ',', ')', ']', '>', '"', '\'') }
        .distinct()
        .toList()

    /** Splits a command line on whitespace, keeping quoted pieces together. */
    fun args(text: String): List<String> {
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var quote: Char? = null
        for (c in text) {
            when {
                quote != null && c == quote -> quote = null
                quote == null && (c == '"' || c == '\'') -> quote = c
                quote == null && c.isWhitespace() -> {
                    if (cur.isNotEmpty()) {
                        out += cur.toString()
                        cur.clear()
                    }
                }
                else -> cur.append(c)
            }
        }
        if (cur.isNotEmpty()) out += cur.toString()
        return out
    }

    fun looksLikeVideoId(id: String) = id.matches(Regex("^[A-Za-z0-9_-]{11}$"))
}
