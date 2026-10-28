# Google Play yayın hazırlığı

## Yerelde imzalı AAB üretme

Release anahtarı repoya konulmamalıdır. Android Studio ile **Build → Generate Signed Bundle/APK → Android App Bundle** seçerek güvenli bir upload keystore oluşturun. Alternatif olarak:

```powershell
keytool -genkeypair -v -keystore my-upload-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias upload
```

Keystore dosyasını ve parolalarını güvenli bir parola yöneticisinde saklayın. Gradle için `KEYSTORE_PATH`, `STORE_PASSWORD` ve `KEY_PASSWORD` ortam değişkenlerini ayarlayıp:

```powershell
gradle.bat :app:bundleRelease --no-daemon
```

komutunu çalıştırın. Oluşan `app-release.aab` dosyasını Play Console'da önce Internal testing kanalına yükleyin.

## Play Console'da tamamlanacaklar

- Mağaza gizlilik politikası URL'si: `https://github.com/EdipHSDMR/newsgazete/blob/main/docs/privacy-policy.md` (main dalı yayınlandıktan sonra doğrulayın).
- Data Safety, reklam durumu, içerik derecelendirmesi ve hedef kitle beyanları.
- Uygulama simgesi, özellik görseli, telefon ekran görüntüleri, kısa ve tam açıklama.
- Yeni yayınlarda `versionCode` her yüklemede artırılmalıdır.
- Kişisel geliştirici hesaplarında production erişimi için Play Console'un güncel closed-test şartları uygulanır.

## Mağaza metni taslağı

- Uygulama adı: **Çizgi Haber**
- Kategori: **Haberler ve Dergiler**
- Kısa açıklama (80 karakter sınırı içinde): **Gündem, spor, ekonomi ve hava durumu tek akışta.**
- Tam açıklama:

  **Çizgi ile gündemi tek yerde takip edin.** Haberleri kaynakları ve yayın zamanlarıyla okuyun; ilgi alanlarınıza ve takip ettiğiniz takımlara göre akışınızı düzenleyin.

  **Haberler:** Farklı yayıncıların haberlerini inceleyin, aynı konuya dair kaynakları karşılaştırın, kısa özet ve haber metnini görüntüleyin.

  **Spor:** Futbol ve basketbol fikstürü, canlı skorlar, puan durumları ve maç ayrıntılarını takip edin. Veri kapsamı lig ve sağlayıcıya göre değişebilir.

  **Piyasalar:** BIST, döviz, altın, emtia ve kripto verilerini kaynak ve güncelleme bilgisiyle görüntüleyin. Fiyatlar sağlayıcı gecikmesine tabidir; yatırım tavsiyesi değildir.

  **Hava durumu:** Seçtiğiniz il ve ilçeye göre güncel koşulları ve tahmini görün. İsterseniz hava uyarılarını açın.

  Haberleri kaydedin, geçmişte arayın, yazı boyutunu ve görünümü ayarlayın. Haber ve veri kapsamı kullanılan yayıncı ve sağlayıcılara bağlıdır.

## Data Safety beyanı için uygulama inceleme notu

Bu kod incelemesine göre uygulama hesabı, reklam SDK'sı, analitik SDK'sı veya GPS izni kullanmıyor. Hava durumu çağrılarında kullanıcı tarafından seçilen il/ilçe adı Open-Meteo geocoding/weather uç noktalarına ve yapılandırılmışsa WeatherAPI'ye gönderilebildiğinden, Play Console formunda **konum (yaklaşık)** verisinin üçüncü taraflarla paylaşımı değerlendirilmelidir. Seçim cihazda saklanır; ancak ağ çağrısı yapılınca seçilen bölge hizmet sağlayıcısına gider. Gizlilik politikası bunu açıklar.

Bu build'de `google-services.json` olmadığı için FCM etkin değil. Gerçek Play release'i için Firebase yapılandırması daha sonra eklenirse FCM token/device ID, push teslimatı ve Firebase SDK davranışlarını yeniden inceleyip Data Safety beyanını güncelleyin. Play Console beyanlarını gerçek yayın derlemesinin SDK ve yapılandırmasına göre geliştirici olarak doğrulamak gerekir.
