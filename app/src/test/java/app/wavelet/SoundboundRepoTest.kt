package app.wavelet

import app.wavelet.source.RepoManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SoundboundRepoTest {
    private val sample = """[
      {"source_id":"in.shabinder.soundbound.extension.shabinder.navidrome","source_name":"NavidromeProvider","source_version_name":"0.40.80","link":"https://x/manifest.zipline.json","source_type":"DOWNLOADABLE_PROVIDER"},
      {"source_id":"in.shabinder.soundbound.extension.shabinder.youtube","source_name":"YoutubeProvider","source_version_name":"0.40.80","link":"https://x/manifest.zipline.json","source_type":"DOWNLOADABLE_PROVIDER"},
      {"source_id":"in.shabinder.soundbound.extension.shabinder.audius","source_name":"AudiusProvider","source_version_name":"0.40.80","link":"https://x/manifest.zipline.json","source_type":"DOWNLOADABLE_PROVIDER"},
      {"source_id":"in.shabinder.soundbound.extension.shabinder.netease","source_name":"NeteaseLyricsProvider","source_version_name":"0.40.80","link":"https://x/manifest.zipline.json","source_type":"PROVIDER"}
    ]"""

    @Test fun mapsSoundboundEntries() {
        val index = RepoManager.parse(sample, "https://example/private-extensions.json")
        val byName = index.sources.associateBy { it.name }
        assertEquals("subsonic", byName["Navidrome"]?.engine)
        assertNull(byName["Navidrome"]?.unsupportedReason)
        assertEquals("audius", byName["Audius"]?.engine)
        assertTrue(byName["Youtube"]?.unsupportedReason!!.contains("YouTube"))
        assertTrue(byName["Netease Lyrics"]?.unsupportedReason!!.contains("lyrics"))
        // Usable entries are listed first.
        assertNull(index.sources.first().unsupportedReason)
    }

    @Test fun waveletFormatStillWorks() {
        val index = RepoManager.parse("""{"name":"Mine","sources":[{"id":"a","engine":"radio_browser","name":"Radio"}]}""", "u")
        assertEquals("Mine", index.name)
        assertEquals("radio_browser", index.sources.single().engine)
    }

    /** Runs against a real downloaded Soundbound repo file when one is supplied. */
    @Test fun realRepoFileIfPresent() {
        val path = System.getenv("SOUNDBOUND_REPO_FILE") ?: return
        val f = File(path)
        if (!f.exists()) return
        val index = RepoManager.parse(f.readText(), "real")
        assertNotNull(index.sources.firstOrNull())
        println("REAL REPO: " + index.description)
        index.sources.forEach { println("  ${if (it.unsupportedReason == null) "OK  " else "NO  "} ${it.name} -> ${it.engine.ifEmpty { it.unsupportedReason }}") }
    }
}
