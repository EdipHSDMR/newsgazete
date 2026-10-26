package com.example
import com.example.util.NewsSummary
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], manifest = Config.NONE)
class NewsSummaryTest {
    @Test fun aCompleteSourceLeadNeverBecomesAnEmptySummary() {
        val source = "Belediye, sel nedeniyle üç ilçede eğitime bir gün ara verildiğini açıkladı."
        val summary = NewsSummary.bestAvailableSummary(source, source, "Üç ilçede eğitime ara verildi")
        assertTrue(summary.contains("sel nedeniyle"))
        assertTrue(summary.contains("bir gün"))
    }

    @Test fun aTimeQuestionGetsTheSourceAnswerInsteadOfTheTeaser() {
        val source = "Ekim Dolunayı meraklılarının gündemindeki yerini aldı. Ekim Dolunayı 7 Ekim saat 06.47'de gerçekleşecek. Gözlem için bulutsuz bir alan seçilmesi öneriliyor."
        val summary = NewsSummary.concise(source, "Ekim Dolunayı ne zaman, saat kaçta?")
        assertTrue(summary.startsWith("Ekim Dolunayı 7 Ekim"))
        assertTrue(summary.contains("06.47"))
        assertFalse(summary.contains("gündemindeki"))
    }

    @Test fun publisherParagraphsAndAbbreviationsArePreserved() {
        val text = "Dr. Ayşe Yılmaz yeni raporun sonuçlarını açıkladı.\n\nRaporda hava kirliliğinin son bir yılda yüzde 12 azaldığı belirtildi."
        val paragraphs = NewsSummary.paragraphs(text)
        assertEquals(2, paragraphs.size)
        assertTrue(paragraphs.first().startsWith("Dr. Ayşe"))
    }

    @Test fun aTitleAloneIsNotLabeledAsASummary() {
        assertEquals("", NewsSummary.bestAvailableSummary("Yeni karar açıklandı", "Yeni karar açıklandı", "Yeni karar açıklandı"))
    }
}
