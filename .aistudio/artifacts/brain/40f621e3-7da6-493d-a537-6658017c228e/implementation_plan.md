# Çizgi — Gerçek Canlı Spor İstatistikleri ve Haber Akışları Entegrasyon Planı

Kullanıcının talebi doğrultusunda, uygulamadaki tüm manuel/statik veriler (haberler, gol, asist ve kart krallığı istatistikleri) tamamen kaldırılarak; gerçek zamanlı API'ler, canlı RSS servisleri ve Room tabanlı yerel önbellekleme (offline-first caching) mimarisiyle yeniden yapılandırılacaktır.

---

### Kullanıcı Tercihleri ve Onaylanan Kararlar

> [!IMPORTANT]
> Kullanıcı ile yapılan 1. Aşama soru-cevap sürecinde aşağıdaki kritik kararlar kesinleştirilmiştir:
> - **Öncelikli Ligler**: Trendyol Süper Lig, İngiltere Premier Lig ve UEFA Şampiyonlar Ligi.
> - **Spor İstatistik Tabloları**: Gol krallığı, asist krallığı ve kart istatistikleri bir arada (sekmeli filtreleme ile).
> - **Canlı Haber Kaynakları**: Anadolu Ajansı (AA), TRT Haber, NTV ve Habertürk canlı RSS akışları.
> - **Çevrimdışı ve Kota Davranışı**: Manuel sahte veri yerine en son çekilen gerçek verilerin Room veritabanında saklanıp önbellekten sunulması.

---

## 1. Genel Bakış ve Temel Konsept

- **Ne Yapıyor?**: Çizgi haber ve spor uygulamasını, hiçbir yapay/manuel veri kalmayacak şekilde tamamen canlı veri kaynaklarına bağlar. Spor ekranında Süper Lig, Premier Lig ve Şampiyonlar Ligi için gerçek zamanlı gol, asist ve kart krallığı verilerini sunar. Haber ekranlarında AA, TRT Haber, NTV ve Habertürk'ten çekilen gerçek haberleri gösterir.
- **Hedef Kitle**: Türkiye ve Avrupa futbolunu, anlık gündemi, piyasaları ve hava durumunu tek bir editoryal çatı altında takip eden mobil kullanıcılar.
- **Sağlanan Değer**: Gerçek zamanlı, güvenilir ve hiçbir şekilde sahte/manuel veri barındırmayan editoryal bir deneyim. İnternet kesilse bile Room veritabanındaki son gerçek veriler görüntülenir.

---

## 2. Kullanıcı Deneyimi ve Arayüz Tasarımı (UX & UI)

### Spor İstatistikleri Ekranı (Mackolik & Canlı Spor Deneyimi)
- **Lig Seçici**: Trendyol Süper Lig, İngiltere Premier Lig, UEFA Şampiyonlar Ligi arasında geçiş çipleri.
- **İstatistik Türü Sekmeleri (TabRow)**:
  - ⚽ **Gol Krallığı**: Sıralama, oyuncu adı, takım arması/adı, oynadığı maç ve gol sayısı.
  - 🎯 **Asist Krallığı**: Sıralama, oyuncu adı, takım adı, asist sayısı ve toplam skor katkısı.
  - 🟨🟥 **Kart Raporu**: En çok sarı ve kırmızı kart gören oyuncular ve ceza sınırındakiler.
- **Canlı Durum Rozetleri**: API'den gelen son güncelleme saati ve kaynak etiketi ("API-Sports / Canlı").
- **Görsel Tasarım**: M3 kart bileşenleri, sıralama madalyaları (1. Altın, 2. Gümüş, 3. Bronz rozetler), akıcı liste animasyonları.

### Haber Akışı Deneyimi
- **Kaynak Etiketleri**: AA, TRT Haber, NTV ve Habertürk canlı akışlarından otomatik ayrıştırılmış kategoriler (Gündem, Ekonomi, Spor, Teknoloji, Dünya).
- **Gerçek Zamanlı Detay**: RSS'ten çekilen özgün başlık, tam metin, yayınlanma tarihi, kaynak linki ve görsel.
- **Boş & Yükleme Durumları**: İlk açılışta Room önbelleğindeki gerçek veriler hemen ekrana gelir; arka planda çekilen taze haberlerle liste pürüzsüzce güncellenir.

---

## 3. Temel Ürün ve Mimari Kararları

### Karar 1: Spor İstatistikleri API Mimarisi (API-Sports + ESPN Fallback)
- **Seçilen Yaklaşım**: Birincil olarak `v3.football.api-sports.io/players/topscorers`, `topassists` ve `topyellowcards` endpoint'leri çağrılır. API kotası veya gecikme durumunda ESPN kamu futbol liderleri veri akışı devreye girer.
- **Neden**: API-Sports gerçek Süper Lig ve Avrupa ligleri gol/asist verilerini en yüksek doğrulukla sunar. Kotasız ESPN genel liderler servisi ise kesintisiz canlı yedekleme sağlar.
- **Değerlendirilen Alternatifler**: Statik mock veri listeleri (kullanıcı tarafından tamamen yasaklandı).

### Karar 2: Room Tabanlı "Offline-First" Veri Saklama
- **Seçilen Yaklaşım**: Room veritabanında `CachedStoryEntity` ve `CachedPlayerStatEntity` tabloları oluşturulur.
- **Neden**: Uygulama açıldığında statik mock veri göstermek yerine, daha önce internet varken çekilmiş gerçek dünya verileri anında ekrana basılır. Ağdan yeni veri geldiğinde Room güncellenir ve Flow üzerinden UI reaktif olarak yenilenir.
- **Değerlendirilen Alternatifler**: Yalnızca bellekte tutma (uygulama her açıldığında ekranın boş kalmasına yol açardı).

---

## 4. Teknik Mimari ve Veri Stratejisi

```
┌──────────────────────────────────────────────────────────────────┐
│                   Çizgi Jetpack Compose UI                       │
│    AkisScreen       SporScreen (Gol/Asist/Kart)     HaberDetay   │
└─────────────────────────────────┬────────────────────────────────┘
                                  │
                                  ▼
┌──────────────────────────────────────────────────────────────────┐
│                        CizgiRepository                           │
│  StateFlow<List<Story>>           StateFlow<List<TopScorer>>     │
└──────────────────┬───────────────────────────────┬───────────────┘
                   │                               │
        (Arka Plan Senkronizasyonu)        (Reaktif Okuma)
                   │                               │
                   ▼                               ▼
┌──────────────────────────────────┐   ┌───────────────────────────┐
│         Remote Services          │   │       Room Database       │
│  - RssNewsService (AA,TRT,NTV,HT)│   │  - SavedArticleDao        │
│  - FootballApiService (ApiSports)│──▶│  - CachedStoryDao         │
│  - WeatherApiService             │   │  - CachedSportStatsDao    │
└──────────────────────────────────┘   └───────────────────────────┘
```

### Veri Modelleri ve DAO Yapısı
1. **`CachedStoryEntity`**:
   - `id`, `title`, `summary`, `topic`, `publisher`, `publishedAt`, `imageUrl`, `fullText`, `canonicalUrl`, `timestamp`.
2. **`CachedSportStatsEntity`**:
   - `id`, `competition`, `statType` (GOL / ASIST / KART), `rank`, `player`, `team`, `count`, `secondaryCount`, `matches`.
3. **`FootballApiService` Güncellemeleri**:
   - `fetchTopScorers(competition: String): List<TopScorer>`
   - `fetchTopAssists(competition: String): List<TopScorer>`
   - `fetchCardLeaders(competition: String): List<TopScorer>`
4. **`RssNewsService` Güncellemeleri**:
   - Anadolu Ajansı (AA) güncel RSS beslemeleri eklenir.
   - TRT Haber, NTV, Habertürk beslemeleri optimize edilir.
   - HTML etiketleri ve CDATA parse temizliği güçlendirilir.
5. **`CizgiData.kt` (Seed Data)**:
   - Manuel sahte veri bağımlılıkları tamamen kaldırılır. Varsayılan UI durumları Room akışlarına bağlanır.

---

## 5. Doğrulama ve Test Adımları

1. **Gradle Derleme Kontrolü**: `compile_applet` ile Room KSP şeması ve yeni API modellerinin hatasız derlendiğinin doğrulanması.
2. **Spor İstatistikleri Testi**: Süper Lig, Premier Lig ve Şampiyonlar Ligi için Gol, Asist ve Kart sekmelerinin gerçek oyuncular ve takımlarla yüklendiğinin doğrulanması.
3. **Canlı Haber Akışı Testi**: AA, TRT, NTV ve Habertürk akışlarından canlı haberlerin çekilip başarıyla listelendiğinin kontrolü.
4. **Çevrimdışı/Önbellek Testi**: Verilerin Room'a kaydedildiği ve uygulama yeniden açıldığında sahte veri olmadan doğrudan gerçek önbellekten geldiğinin test edilmesi.
