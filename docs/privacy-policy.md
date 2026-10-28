# Çizgi Gizlilik Politikası

Son güncelleme: 28 Ekim 2026

Çizgi; haber, spor, piyasa ve hava durumu bilgilerini sunar. Bu politika, uygulamanın hangi bilgileri işlediğini ve bunları nasıl yönetebileceğinizi açıklar.

## Cihazda tutulan bilgiler

Takip edilen konu ve takımlar, tema ve yazı boyutu tercihleri, bildirim ve piyasa alarmı ayarları, kaydedilen haberler ve okuma konumu uygulamanın özel alanında cihazda saklanır. Kullanıcı hesabı oluşturulmaz. İsteğe bağlı JSON yedeği yalnızca Android belge seçicisinde seçtiğiniz yere yazılır.

## Konum ve hava durumu

Çizgi GPS veya cihazın hassas konum iznini kullanmaz. Hava durumu için uygulamada seçtiğiniz il/ilçe adı, hava durumu ve konum bulma sağlayıcılarına gönderilir. Bu bilgi hava durumu özelliğini sunmak için kullanılır. Sağlayıcıların kendi hizmet günlüklerinde IP adresi gibi standart ağ bilgilerini işlemesi mümkündür.

## Ağ ve üçüncü taraf sağlayıcılar

Haber, spor, piyasa ve hava durumu istekleri ilgili veri sağlayıcılarının sunucularına gönderilebilir. Hava durumu için Open-Meteo ve yapılandırılmışsa WeatherAPI; haber ve spor için yayıncı RSS/API uç noktaları; piyasa için ilgili finansal veri uç noktaları kullanılır. Bu sağlayıcılar istekleri ve kendi günlüklerini kendi gizlilik politikalarına göre işler. Uygulama kişisel tercihleri veya kayıtlı haberleri satmaz.

Uygulama kullanıcı hesabı, reklam SDK'sı, konum izni veya cihaz reklam kimliği kullanmaz. Bu derlemede Firebase yapılandırması etkin olmadığından uzak push bildirimleri etkin değildir; yerel bildirimler cihazda hazırlanır. Uygulamada hesap oluşturma veya sunucuda kullanıcı profili bulunmaz.

Haber detayındaki “Haberde sorun bildir” seçeneği, e-posta uygulamasında alıcı, haber başlığı ve kaynak bağlantısı doldurulmuş bir taslak açar. Taslak yalnızca kullanıcı e-posta uygulamasından gönderirse paylaşılır; uygulama bu bildirimi kendi sunucusuna kaydetmez.

## Saklama ve silme

“Verileri ve Önbelleği Temizle” seçeneği uygulamanın cihazda tuttuğu haber ve tercihleri temizler. Dışa aktarılan JSON dosyaları seçtiğiniz konumda kalır; dosyayı Android dosya yöneticisinden silebilirsiniz.

## Güvenlik

Uygulamanın ağ istekleri HTTPS kullanır. Cihaz içindeki kayıtlar uygulamanın özel depolama alanında tutulur. Veri sağlayıcılarının kendi sistemlerindeki saklama ve güvenlik uygulamaları ilgili sağlayıcıların sorumluluğundadır.

## Çocuklar

Çizgi çocuklara yönelik tasarlanmamıştır ve bilerek çocuklardan kişisel veri toplamaz.

## İletişim

Gizlilik soruları için geliştirici profiline ulaşabilirsiniz: [github.com/EdipHSDMR](https://github.com/EdipHSDMR).

