package com.example.util

import org.jsoup.Jsoup

object ArticleTextExtractor {
    fun paragraphs(html: String): List<String> {
        val document = Jsoup.parse(html)
        document.select("script, style, nav, aside, footer, header, form, button, [class*=related], [class*=recommend], [class*=advert], [class*=social], [class*=share], [class*=breadcrumb]").remove()
        val candidates = document.select("[itemprop=articleBody], .article-body, .article__body, .article-content, .news-content, .news-detail-content, .news-text, .detail-text, .content-text, .entry-content, .content-news-tag-selector, article, main")
        return candidates.map { candidate ->
            candidate.select("p, h2, h3").mapNotNull { element ->
                val text = element.text().trim()
                val links = element.select("a").sumOf { it.text().length }
                text.takeIf { it.length >= 28 && links < it.length * 0.45 && !BOILERPLATE.containsMatchIn(it) }
            }.distinct()
        }.maxByOrNull { paragraphs -> paragraphs.sumOf { it.length } }.orEmpty().take(80)
    }

    private val BOILERPLATE = Regex("tüm hakları saklıdır|abone ol|bildirimleri aç|bizi takip edin|çerez politikası|kişisel verilerin korunması", RegexOption.IGNORE_CASE)
}
