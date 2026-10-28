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

    @Test fun aCauseQuestionGetsTheReportedCause() {
        val source = "Kentte ulaşım çalışmaları sürüyor. Seferler, yoğun kar yağışı nedeniyle iptal edildi. Ekipler yollarda temizlik çalışması başlattı."
        val result = NewsSummary.assess("", source, "Seferler neden iptal edildi?")
        assertTrue(result.text.contains("kar yağışı nedeniyle"))
        assertNull(result.missingAnswer)
    }

    @Test fun channelAndPriceAnswersComeFromTheSource() {
        val channel = NewsSummary.assess("", "Karşılaşmanın hazırlıkları tamamlandı. Mücadele beIN Sports 1 kanalından yayınlanacak.", "Maç hangi kanalda?")
        assertTrue(channel.text.contains("beIN Sports 1"))
        assertNull(channel.missingAnswer)
        val price = NewsSummary.assess("", "Yeni tarife duyuruldu. Aylık abonelik bedeli 250 TL olarak açıklandı.", "Abonelik ne kadar?")
        assertTrue(price.text.contains("250 TL"))
        assertNull(price.missingAnswer)
    }

    @Test fun missingAnswersAreReportedWithoutInventingTheTitleClaim() {
        val source = "Kurum, etkinlik için hazırlıkların devam ettiğini açıkladı. Ayrıntıların daha sonra paylaşılacağı belirtildi."
        val result = NewsSummary.assess("", source, "Etkinlik ne zaman, saat kaçta?")
        assertNotNull(result.missingAnswer)
        assertTrue(result.missingAnswer!!.contains("tarih/saat"))
        assertFalse(result.text.contains("20.00"))
    }

    @Test fun ordinalMatchMinutesStayWithTheirSentence() {
        val opening = "Takım, karşılaşmanın 29. dakikasında üstünlük golünü kaydetti."
        val source = ("Hazırlıklar tamamlandı. ".repeat(20)) + opening + " Bu sonuçla ligdeki puanını artırdı."
        val paragraphs = NewsSummary.paragraphs(source)
        assertTrue(paragraphs.any { it.contains(opening) })
        assertFalse(paragraphs.any { it.startsWith("dakikasında") })
    }

    @Test fun questionSummariesChooseAConcreteOutcomeAndRemainSourceGrounded() {
        val source = "Karşılaşma öncesi iki takım da hazırlıklarını tamamladı. Beşiktaş, 78. dakikada attığı golle maçı 2-1 kazandı. Bu sonuçla puanını 40'a yükseltti."
        val result = NewsSummary.assess("", source, "Maçta ne oldu?")
        assertTrue(result.text.contains("78. dakikada"))
        assertTrue(result.text.contains("2-1 kazandı"))
        assertTrue(result.sourceCoverage > 0.9f)
        assertTrue(result.includesConcreteFact)
        assertNull(result.missingAnswer)
    }

    @Test fun missingOutcomeIsCalledOutInsteadOfFilledIn() {
        val result = NewsSummary.assess("", "Takımlar karşılaşma öncesi son antrenmanını yaptı.", "Maçta ne oldu?")
        assertNotNull(result.missingAnswer)
        assertTrue(result.missingAnswer!!.contains("olayın sonucu"))
        assertFalse(result.text.contains("kazandı"))
    }

    @Test fun percentagesAndFullDatesCountAsDirectAnswers() {
        val date = NewsSummary.assess("", "Etkinliğin 22 Ekim 2026 tarihinde başlayacağı açıklandı.", "Etkinlik ne zaman?")
        assertTrue(date.text.contains("22 Ekim 2026"))
        assertNull(date.missingAnswer)
        val rate = NewsSummary.assess("", "Yeni düzenleme ile aidatlara yüzde 12 zam yapılacak.", "Aidat ne kadar artacak?")
        assertTrue(rate.text.contains("yüzde 12"))
    }
}
