package com.example
import com.example.util.ArticleTextExtractor
import org.junit.Assert.*
import org.junit.Test

class ArticleTextExtractorTest {
    @Test fun articleParagraphsExcludeNavigationAndRecommendations() {
        val html = """<html><body><nav><p>Bu bir menü satırı ve habere ait değil.</p></nav>
            <article><div class="article-body"><p>Yeni ulaşım hattının pazartesi günü hizmete açılacağı duyuruldu.</p>
            <p>Hat, kent merkezindeki beş mahalleyi birbirine bağlayacak.</p>
            <aside><p>Bu önerilen haber içeriğe dahil edilmemeli.</p></aside>
            <div class="related"><p>Başka bir haberin tanıtım metni burada bulunuyor.</p></div></div></article></body></html>"""
        val paragraphs = ArticleTextExtractor.paragraphs(html)
        assertEquals(2, paragraphs.size)
        assertTrue(paragraphs[0].contains("pazartesi"))
        assertFalse(paragraphs.joinToString(" ").contains("önerilen"))
    }
}
