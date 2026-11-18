package app.wavelet.io

import app.wavelet.data.PlaylistEntry
import app.wavelet.source.MusicSource
import app.wavelet.source.Track
import app.wavelet.source.attempt
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.text.Normalizer
import kotlin.math.abs

/**
 * Finds a playable track for a playlist entry (title + artist) among the enabled sources.
 * Sources are tried in order (your library first, then servers, then catalogs) and a candidate
 * is only accepted when both title and artist are close.
 */
class TrackMatcher(private val sources: () -> List<MusicSource>) {

    data class Candidate(val track: Track, val score: Double)

    /** Sources good for matching; radio and whole-album catalogs are skipped. */
    private fun matchSources(): List<MusicSource> {
        val order = listOf("local", "downloads")
        return sources()
            .filter { it.id != "radio_browser" && it.id != "internet_archive" }
            .sortedBy { s -> order.indexOf(s.id).let { if (it < 0) order.size else it } }
    }

    suspend fun matchAll(
        entries: List<PlaylistEntry>,
        exact: List<Track?>,
        strict: Boolean = false,
        onProgress: (done: Int, total: Int) -> Unit,
    ): List<Track?> = coroutineScope {
        val limit = Semaphore(4)
        var done = 0
        val threshold = if (strict) STRICT_ACCEPT else ACCEPT
        entries.mapIndexed { i, entry ->
            async {
                val result = exact.getOrNull(i) ?: limit.withPermit { best(entry, threshold) }
                synchronized(this@TrackMatcher) { done++ }
                onProgress(done, entries.size)
                result
            }
        }.awaitAll()
    }

    suspend fun best(entry: PlaylistEntry): Track? = best(entry, ACCEPT)

    /** Best match using a strict (near 100%) threshold; used for auto-matching during import. */
    suspend fun bestStrict(entry: PlaylistEntry): Track? = best(entry, STRICT_ACCEPT)

    private suspend fun best(entry: PlaylistEntry, threshold: Double): Track? {
        for (source in matchSources()) {
            val found = candidates(source, entry).firstOrNull { it.score >= threshold }
            if (found != null) return found.track
        }
        return null
    }

    /** Candidates from every source, best first; used by the "Fix match" screen. */
    suspend fun allCandidates(entry: PlaylistEntry, query: String? = null): List<Candidate> = coroutineScope {
        sources().filter { it.id != "radio_browser" }.map { s ->
            async { attempt { candidates(s, entry, query) }.getOrDefault(emptyList()) }
        }.awaitAll().flatten().sortedByDescending { it.score }
    }

    private suspend fun candidates(source: MusicSource, entry: PlaylistEntry, query: String? = null): List<Candidate> {
        val queries = if (query != null) listOf(query) else listOfNotNull(
            cleanTitle(entry.title),
            "${firstArtist(entry.artist)} ${cleanTitle(entry.title)}".trim().takeIf { entry.artist.isNotBlank() },
        ).distinct()
        val seen = mutableSetOf<String>()
        val out = mutableListOf<Candidate>()
        for (q in queries) {
            val results = attempt { source.search(q, 15) }.getOrDefault(emptyList())
            results.filter { seen.add(it.key) && !it.isLive }.forEach { out += Candidate(it, score(entry, it)) }
            if (out.any { it.score >= ACCEPT }) break
        }
        return out.sortedByDescending { it.score }
    }

    companion object {
        const val ACCEPT = 0.78
        /** Near-100% match threshold for automatic import matching. */
        const val STRICT_ACCEPT = 0.95

        fun score(entry: PlaylistEntry, t: Track): Double {
            val titleSim = similarity(cleanTitle(entry.title), cleanTitle(t.title))
            val artistSim = if (entry.artist.isBlank()) 0.7 else maxOf(
                similarity(normalize(entry.artist), normalize(t.artist)),
                similarity(normalize(firstArtist(entry.artist)), normalize(t.artist)),
            )
            if (artistSim < 0.5) return titleSim * 0.4
            var s = titleSim * 0.6 + artistSim * 0.4
            val d1 = entry.durationMs
            val d2 = t.durationMs
            if (d1 != null && d2 != null && d1 > 0 && d2 > 0) {
                val diff = abs(d1 - d2)
                s += when {
                    diff < 3_000 -> 0.05
                    diff > 30_000 -> -0.15
                    else -> 0.0
                }
            }
            return s.coerceIn(0.0, 1.0)
        }

        fun firstArtist(artist: String) = artist.split(",", " & ", " feat", " ft.", " x ", ";", " / ")
            .first().trim()

        /** Drops "(feat. …)", "- Remastered 2011", "[Live]" and similar decorations. */
        fun cleanTitle(title: String): String = normalize(
            title.replace(Regex("""\s*[(\[][^)\]]*[)\]]"""), " ")
                .replace(Regex("""\s+-\s+.*(remaster|version|edit|mix|live|mono|stereo|deluxe).*$""", RegexOption.IGNORE_CASE), " "),
        ).ifBlank { normalize(title) }

        fun normalize(s: String): String =
            Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
                .replace(Regex("""\p{Mn}+"""), "")
                .replace("&", " and ")
                .replace(Regex("""[^\p{L}\p{N}]+"""), " ")
                .trim()

        /** Mix of token overlap and edit distance, 0..1. */
        fun similarity(a: String, b: String): Double {
            if (a.isEmpty() || b.isEmpty()) return 0.0
            if (a == b) return 1.0
            val ta = a.split(' ').toSet()
            val tb = b.split(' ').toSet()
            val jaccard = ta.intersect(tb).size.toDouble() / ta.union(tb).size
            val lev = 1.0 - levenshtein(a, b).toDouble() / maxOf(a.length, b.length)
            val contains = if (a.contains(b) || b.contains(a)) 0.85 else 0.0
            return maxOf(jaccard, lev, contains)
        }

        private fun levenshtein(a: String, b: String): Int {
            var prev = IntArray(b.length + 1) { it }
            for (i in 1..a.length) {
                val cur = IntArray(b.length + 1)
                cur[0] = i
                for (j in 1..b.length) {
                    cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
                }
                prev = cur
            }
            return prev[b.length]
        }
    }
}
