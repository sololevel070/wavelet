package app.wavelet

import app.wavelet.data.PlaylistEntry
import app.wavelet.io.ExportFormat
import app.wavelet.io.PlaylistFormats
import app.wavelet.io.TrackMatcher
import app.wavelet.source.Track
import app.wavelet.source.impl.parseLrc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportLogicTest {

    @Test fun exportifyCsv() {
        val csv = "\"Track URI\",\"Track Name\",\"Artist Name(s)\",\"Album Name\",\"Duration (ms)\"\n" +
            "\"spotify:track:1\",\"Bohemian Rhapsody - Remastered 2011\",\"Queen\",\"A Night at the Opera\",\"354320\"\n" +
            "\"spotify:track:2\",\"Under Pressure\",\"Queen, David Bowie\",\"Hot Space\",\"248440\"\n"
        val p = PlaylistFormats.parse("My Mix.csv", csv)
        assertEquals("My Mix", p.name)
        assertEquals(2, p.entries.size)
        assertEquals("Bohemian Rhapsody - Remastered 2011", p.entries[0].title)
        assertEquals("Queen, David Bowie", p.entries[1].artist)
        assertEquals(354320L, p.entries[0].durationMs)
    }

    @Test fun tuneMyMusicCsvWithPlaylistName() {
        val csv = "Track name,Artist name,Album,Playlist name,Type\nHello,Adele,25,Road trip,Playlist\n"
        val p = PlaylistFormats.parse("export.csv", csv)
        assertEquals("Road trip", p.name)
        assertEquals("Adele", p.entries[0].artist)
    }

    @Test fun csvQuotesAndSemicolons() {
        val rows = PlaylistFormats.csvRows("Title;Artist\n\"A; \"\"quoted\"\" song\";Someone\n")
        assertEquals(listOf("A; \"quoted\" song", "Someone"), rows[1])
    }

    @Test fun m3uWithExtinfAndUrls() {
        val m3u = "#EXTM3U\n#PLAYLIST:Chill\n#EXTINF:215,Nujabes - Aruarian Dance\n/sdcard/Music/aruarian.mp3\n" +
            "#EXTINF:-1,Radio Paradise\nhttps://stream.radioparadise.com/mp3-192\n"
        val p = PlaylistFormats.parse("x.m3u8", m3u)
        assertEquals("Chill", p.name)
        assertEquals("Nujabes", p.entries[0].artist)
        assertEquals("Aruarian Dance", p.entries[0].title)
        assertEquals(215_000L, p.entries[0].durationMs)
        assertNull(p.exact[0])
        assertEquals("https://stream.radioparadise.com/mp3-192", p.exact[1]?.streamUrl)
    }

    @Test fun jspf() {
        val json = """{"playlist":{"title":"Weekly Jams","track":[{"title":"Song A","creator":"Artist A"},{"title":"Song B","creator":"Artist B","album":"X"}]}}"""
        val p = PlaylistFormats.parse("lb.json", json)
        assertEquals("Weekly Jams", p.name)
        assertEquals(2, p.entries.size)
        assertEquals("X", p.entries[1].album)
    }

    @Test fun waveletJsonRoundTripKeepsTracks() {
        val t = Track("42", "jamendo", "Song", "Band", "Album", 1000, null, "https://cdn/x.mp3")
        val text = PlaylistFormats.export(ExportFormat.Json, "P", "", listOf(PlaylistFormats.ExportRow("Song", "Band", "Album", 1000, t, "Jamendo")))
        val back = PlaylistFormats.parse("p.json", text)
        assertEquals("P", back.name)
        assertEquals("jamendo:42", back.exact[0]?.key)
    }

    @Test fun exportNeverLeaksServerTokens() {
        val t = Track("9", "jellyfin-1", "S", "A", streamUrl = "https://jf/Audio/9/stream?static=true&api_key=SECRET")
        val rows = listOf(PlaylistFormats.ExportRow("S", "A", null, null, t, "Jellyfin"))
        ExportFormat.entries.forEach { f ->
            val out = PlaylistFormats.export(f, "P", "", rows)
            assertTrue("$f leaked token", !out.contains("SECRET"))
        }
    }

    @Test fun exportNeverLeaksPlexToken() {
        val t = Track("7", "plex-1", "S", "A", streamUrl = "http://nas:32400/library/parts/7/file.mp3?X-Plex-Token=PLEXSECRET")
        val rows = listOf(PlaylistFormats.ExportRow("S", "A", null, null, t, "Plex"))
        ExportFormat.entries.forEach { f -> assertTrue("$f leaked Plex token", !PlaylistFormats.export(f, "P", "", rows).contains("PLEXSECRET")) }
    }

    @Test fun csvExportReimports() {
        val rows = listOf(PlaylistFormats.ExportRow("Hello, World", "A \"B\"", "Alb", 5000, null, null))
        val out = PlaylistFormats.export(ExportFormat.Csv, "P", "", rows)
        val back = PlaylistFormats.parse("p.csv", out)
        assertEquals("Hello, World", back.entries[0].title)
        assertEquals("A \"B\"", back.entries[0].artist)
        assertEquals("P", back.name)
    }

    @Test fun matcherScoring() {
        val entry = PlaylistEntry("Bohemian Rhapsody - Remastered 2011", "Queen", durationMs = 354_000)
        val good = Track("1", "local", "Bohemian Rhapsody", "Queen", durationMs = 355_000)
        val wrongArtist = Track("2", "jamendo", "Bohemian Rhapsody", "Some Cover Band")
        val wrongSong = Track("3", "local", "Radio Ga Ga", "Queen")
        assertTrue(TrackMatcher.score(entry, good) >= TrackMatcher.ACCEPT)
        assertTrue(TrackMatcher.score(entry, wrongArtist) < TrackMatcher.ACCEPT)
        assertTrue(TrackMatcher.score(entry, wrongSong) < TrackMatcher.ACCEPT)
        val feat = PlaylistEntry("Under Pressure (feat. David Bowie)", "Queen, David Bowie")
        assertTrue(TrackMatcher.score(feat, Track("4", "local", "Under Pressure", "Queen")) >= TrackMatcher.ACCEPT)
        val accents = PlaylistEntry("Déjà Vu", "Beyoncé")
        assertTrue(TrackMatcher.score(accents, Track("5", "local", "Deja Vu", "Beyonce")) >= TrackMatcher.ACCEPT)
    }

    @Test fun lrc() {
        val lines = parseLrc("[00:01.50]Hello\n[01:02.00][01:30.25]Chorus\nno tag")
        assertEquals(3, lines.size)
        assertEquals(1500L, lines[0].timeMs)
        assertEquals(62_000L, lines[1].timeMs)
        assertEquals(90_250L, lines[2].timeMs)
    }
}
