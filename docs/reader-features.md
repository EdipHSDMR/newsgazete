# Okuma, kişiselleştirme ve bildirimler

## Kullanıcıya sunulanlar

- İlk açılışta ve Ayarlar → Akışımı kişiselleştir üzerinden konu ve takım seçimi. Seçenekler alınan haberler, gerçek fikstür ve puan tablosundan oluşur. Senin için akışı seçili konuları ve takımlarla ilgili haberleri tarihe göre sıralar.
- Keşfet, bellekteki ilk haberlerle sınırlı kalmadan Room arşivini tarar. Türkçe harfleri destekleyen sözcük araması, yayıncı, konu ve başlangıç/bitiş günü filtreleri; 60 kayıtlık sayfalama ve son 10 arama vardır. Arama yalnızca kaynaklardan daha önce alınmış arşivi kapsar.
- Aynı haber birleştirildiğinde kaynaklar korunur. Metni kullanılan yayıncı ile kaynak adresi birlikte tutulur. Yayıncı kategorisi ve URL bölümü, belirsiz anahtar sözcüklerden önce gelir. Eski yanlış kategoriler bir defa, küçük gruplar hâlinde düzeltilir.
- Yer imi, haberin anlık kopyasını hemen kaydeder; erişilebilen tam metin arka planda hazırlanır. Kitaplık, indirme sürerken, kısa içerik kayıtlıyken ve tam metin çevrimdışı hazırken farklı durumlar gösterir. Başarısız indirme yeniden denenebilir. Görseller önbelleğe alınır; çevrimdışı metin kaydı görselin her zaman bulunacağını garanti etmez.
- Son 200 okuma ve paragraf konumu cihazda saklanır. Tamamlanmamış haber aynı konumda açılır; tamamlanan haber baştan açılır. Okuma geçmişi ve kayıtlı haberler ayrı temizlenebilir.
- Bildirim kutusu ve okundu durumu kalıcıdır. Son dakika, konu/takım haberi, günlük özet, maç başlangıcı ve skor/sonuç tercihleri ayrıdır. Yeni bildirim türleri kullanıcı açana kadar kapalıdır. Önceki tercihler korunur. Sessiz saatlerde sistem bildirimi çıkmaz; gelişme uygulamanın kutusunda kalır.
- Bildirim kimlikleri kalıcı olarak tekilleştirilir; kutuyu temizlemek aynı gelişmeyi yeniden bildirmez. İlk alınan skor geçmiş gol bildirimi üretmez. Basketbol skorları en fazla beş dakikada bir birleştirilir. Arka planda en fazla altı takip edilen lig yenilenir.
- Dört yazı boyutu, cihazın kendi yazı boyutuyla birlikte uygulanır. Yüksek kontrast, hareket azaltma, başlık işaretleri, seçim durumları ve ana eylemler için daha geniş dokunma alanları vardır.

## Özetler

Özetler kaynak metninden seçilir; harici ücretli model kullanılmaz. Tarih/saat, neden, yayın kanalı ve tutar soruları önceliklendirilir. Kaynakta açık yanıt yoksa ekranda belirtilir. Başlık tek başına özet sayılmaz. RSS paragrafları korunur; sıra sayıları ve dakika ifadeleri ortadan bölünmez. Kullanıcının özet değerlendirmesi yalnızca bu cihazda tutulur; bir sunucuya gönderilmez ve otomatik model eğitimi yapmaz.

## Çalışma sınırları

Tam metin hazırlama, yayıncının erişilebilen içeriğine bağlıdır. Arka plan yenileme WorkManager ile yaklaşık 15 dakikalık dönemlerde istenir; Android pil/bağlantı koşulları bunu geciktirebilir. Bu kurulum sunucu üzerinden anlık push teslimatı sağlamaz. Spor ekranındaki mevcut 10 saniyelik ön plan sorgulaması korunur; veri sağlayıcısının gecikmesi geçerlidir. Yeni abonelik veya ücretli servis kurulmamıştır.

## Doğrulama

- Birim testleri: soru yanıtları, eksik kaynak, paragraf/sıra sayısı, RSS HTML, Türkçe arama, kişisel akış, geceyi aşan sessiz saatler, eski/bilinmeyen/gelecek tarihli haberler ve kaynak birleştirme.
- Room testleri: çevrimdışı kopya ve konumun arşiv silinmesinden bağımsız kalması, tarih sınırıyla eski arşiv araması, yinelenen bildirim ve kalıcı okundu durumu.
- Android emülatörü: mevcut sürüm 3 veritabanından sürüm 4'e kayıtlar korunarak geçiş; uçak modunda kayıtlı haber; haberi yeniden açınca aynı metin ve kaydırma konumu; konu/takım seçimi; büyük yazı, kontrast ve hareket azaltma kontrolleri.
