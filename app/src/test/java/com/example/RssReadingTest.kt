package com.example

import com.example.data.remote.RssNewsService
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], manifest = Config.NONE)
class RssReadingTest {
    @Test fun rssParagraphsAndPublisherCategorySurviveImport() {
        val xml = """<rss><channel><item>
            <title>Altın Top ödülü sahibini buldu</title>
            <link>https://example.com/spor/odul</link>
            <category>Spor</category>
            <pubDate>Mon, 26 Oct 2026 18:00:00 GMT</pubDate>
            <description><![CDATA[<p>Yılın futbolcusu ödülü düzenlenen törenle verildi.</p><p>Oylamaya milli takım kaptanları ve gazeteciler katıldı.</p>]]></description>
            </item></channel></rss>"""
        val story = RssNewsService(OkHttpClient()).parseRssFeedRobust(xml, "Yayıncı", "Ekonomi").single()
        assertEquals("Spor", story.topic)
        assertEquals(2, story.paragraphs.size)
        assertTrue(story.fullText.contains("\n\n"))
        assertTrue(story.publishedAtEpochMs > 0)
        assertFalse(story.fullText.contains("<p>"))
    }
}
