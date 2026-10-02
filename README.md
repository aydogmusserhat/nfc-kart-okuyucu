# NFC Kart Okuyucu — Android kaynak projesi

Android 6.0+ ve NFC destekli gerçek telefon gerekir. APK, GitHub Actions ile oluşturulur; Actions çalıştırmasının Artifacts bölümünden indirilebilir. Online kurulum için ONLINE-KURULUM.md dosyasına bak.

## Yaptıkları

- Kartın UID değerini ve telefonun algıladığı teknolojileri gösterir.
- NFC-A, NFC-V ve ISO-DEP kartların erişilebilir protokol bilgilerini gösterir.
- NDEF mesajlarını okur; metin ve URI kayıtlarını çözümler, diğer kayıtların ham verisini hex olarak gösterir.
- MIFARE Classic destekli telefon/kart birleşiminde, kullanıcının verdiği tek anahtar ile seçilen tek sektörü doğrulayıp veri bloklarını okur. Anahtar A/B seçilebilir. Anahtar ve sektör trailer bloğu rapora alınmaz.
- Yazılabilir NDEF kartlara metin veya HTTP/HTTPS bağlantısı yazar. Mevcut NDEF içeriğini boş kayıtla değiştirebilir. NdefFormatable desteği olan, henüz NDEF biçiminde olmayan kartları NDEF biçimine dönüştürebilir.
- Sonucu sistem dosya seçicisiyle JSON olarak kaydeder. Uygulamanın internet veya genel depolama izni yoktur.

## Windows'ta APK oluştur

1. Android Studio kur. SDK Manager üzerinden Android SDK Platform 35 ve Android SDK Build-Tools 34.0.0 bileşenlerini kur; SDK lisanslarını kabul et.
2. ZIP dosyasını aç. `build-windows.ps1` bulunan klasörde PowerShell aç.
3. Şu komutu çalıştır:

   ```powershell
   powershell -NoProfile -ExecutionPolicy Bypass -File .\build-windows.ps1
   ```

   `Bypass` yalnızca bu PowerShell işlemi için geçerlidir. Script JDK/SDK yollarını varsayılan Android Studio konumlarından bulur; farklı kurulumda JAVA_HOME ve ANDROID_HOME değişkenlerini ayarla. İlk derleme Gradle 8.9 ve Android derleme bağımlılıklarını internetten indirir. Gradle arşivi üreticinin SHA-256 değeriyle kontrol edilir.
4. Oluşan dosya: `app/build/outputs/apk/debug/app-debug.apk`. Telefona aktar, dosyayı aç ve Android'in kurulum yönergelerini izle. Bu kişisel deneme APK'sıdır.

Android Studio'da düzenlemek için proje klasörünü File → Open ile aç. Gradle wrapper pakete eklenmedi; `build-windows.ps1` ilk kez çalıştıktan sonra Settings → Build Tools → Gradle üzerinden `.tools/gradle-8.9` yerel dağıtımını seçebilirsin. Gradle JDK için Studio'nun uyumlu gömülü JDK'sını veya JDK 17 kullan. Alternatif: bilgisayarında Gradle 8.9 kuruluysa `gradle assembleDebug`.

## Kullanım

1. Telefonda NFC'yi aç. Uygulamayı açıp kartı telefonun arkasına tut.
2. Kart türünü ve okunabilen kayıtları incele. UID bakiye değildir; NDEF bulunmaması kartın boş olduğunu göstermez.
3. Kart MIFARE Classic ise ve sektör anahtarı biliniyorsa sektör numarasını ve 12 hex karakterlik anahtarı gir. A veya B türünü seç, **Anahtarı uygula** düğmesine dokun. Kartı uzaklaştırıp yeniden okut.
4. **Sonucu JSON olarak kaydet** ile raporu dışa aktar. Okunan ham blokların bakiye, sayaç vb. anlamları için üretici veri düzeni gerekir. Uygulama bakiyeyi tahmin etmez.

## Kapsam ve sınırlar

- Yazma ve formatlama yalnızca NDEF desteği üzerinden uygulanır. Dolum kartının özel bellek düzenini sıfırlama veya bakiyesini değiştirme bu sürümde bulunmaz. Bunun için kart tipi, veri düzeni ve dolum sisteminin yetkili işlem protokolü gerekir. Anahtar tahmini yapılmaz.
- Korumalı DESFire/ISO-DEP uygulamalarının belleğini okumak için üreticinin uygulama kimliği, protokolü ve erişim anahtarlarıyla ek geliştirme gerekir. Bu sürüm o alanları okuyamaz.
- NFC destekli her Android telefon MIFARE Classic okumayı desteklemek zorunda değildir. Donanım uyumsuzluğu uygulamayla giderilemez.
- 125 kHz RFID kartlar telefon NFC'siyle okunamaz; uyumlu harici okuyucu gerekir.
- Anahtar yalnızca uygulama sürecinde tutulur; dosyaya ve rapora yazılmaz. Uygulama kapanınca tekrar girilir. JSON raporu kart kimliği ve okunan verileri içerir.

## Doğrulama

1.0 sürümü GitHub Actions üzerinde başarıyla APK olarak derlendi. 1.1 yazma sürümü de GitHub Actions üzerinde başarıyla APK olarak derlendi. 1.2 sürümünün derleme sonucu ilgili Actions çalıştırmasında görülebilir. Gerçek kartta okuma/yazma denemesi yapılmadı. Platformdan bağımsız Codec sınıfının hex dönüşümü, anahtar doğrulaması, UTF-8/UTF-16 NDEF metin çözümlemesi ve bozuk kayıt kontrolleri çalıştırıldı. Java kaynaklarının sözdizimi ve XML dosyaları ayrıca kontrol edildi. Yazma onayının kart kimliği, süre aşımı, tek kullanım ve iptal senaryoları ayrıca test edildi.

Telefon üzerinde kontrol et: NFC kapalı/açık, NDEF metin ve URL etiketi, NDEF içermeyen kart, geçerli/geçersiz Classic anahtarı, kartın erken uzaklaştırılması, uygulamanın arka plana alınması ve JSON kaydetme/iptal.

## Teknik temel

AGP 8.7.3, Gradle 8.9, compileSdk 35, targetSdk 34, minSdk 23, Java 17. Framework bileşenleri kullanılır; uygulama kütüphane bağımlılığı yoktur. targetSdk 34 kişisel yerel deneme içindir; mağaza yayını için güncel hedef SDK gereklilikleri ayrıca ele alınmalıdır.

- https://developer.android.com/reference/android/nfc/NfcAdapter
- https://developer.android.com/reference/android/nfc/tech/Ndef
- https://developer.android.com/reference/android/nfc/tech/MifareClassic
- https://developer.android.com/build/releases/agp-8-7-0-release-notes

## Yazma ve formatlama — sürüm 1.1

1. Hedef kartı önce normal şekilde okut.
2. Metin/bağlantı için içeriği gir ve **NDEF kaydı yaz** seç. İçeriği boşaltmak için **NDEF içeriğini temizle** seç. Henüz NDEF biçiminde olmayan uyumlu kart için **NDEF biçiminde formatla** seç.
3. Kart UID'sini ve işlem önizlemesini kontrol edip onayla. Aynı kartı 30 saniye içinde uzaklaştırıp tekrar yaklaştır.
4. Kartı işlem sonuna kadar sabit tut. Yazma raporunda doğrulama sonucunu incele. Formatlama sonrası teknolojiler yeniden algılansın diye kartı tekrar okut.

Yazma ve temizleme, önceki NDEF mesajının ham baytlarını işlem raporundaki `onceki_ndef_hex` alanına alır. Bu rapor tam bellek yedeği değildir. Formatlama, üreticiye özgü uygulama verilerini kaybettirebilir. Mevcut NDEF kaydını korumak istiyorsan yazmadan önce JSON raporunu kaydet. Formatlama için otomatik eski veri yedeği oluşturulmaz.

Yazma onayı tek kullanımlıktır; farklı UID, süre aşımı, manuel iptal veya uygulamanın arka plana alınması onayı iptal eder. UID kartı hedeflemek içindir, kriptografik kimlik doğrulama değildir.

NDEF yazmada kapasite ve salt okunurluk kontrol edilir; yazılan mesaj karttan yeniden okunup karşılaştırılır. İletişim kesilirse işlem otomatik tekrarlanmaz; kartın son durumu yeniden okunmalıdır. NDEF temizleme, belleğin fiziksel olarak güvenli silinmesi değildir. Formatlama uyumluluğu telefon ve kart üreticisine bağlıdır.

https://developer.android.com/reference/android/nfc/tech/NdefFormatable

## Otomatik kart analizi — sürüm 1.2

Her okumada kartın algılanan ailesi, NDEF veri düzeni, okunabilen kayıt sayısı, bildirilen yazılabilirlik, formatlama desteği ve okunan Classic veri blokları özetlenir. Analiz ekranda ve JSON raporunda bulunur. ISO-DEP tek başına belirli bir ürün/üreticiyi tanımlamaz; kart ailesi ile üreticinin mali uygulama protokolü ayrı bilgiler olarak ele alınır.

Bu sürüm üreticiye özgü bir dolum/bakiye entegrasyonu içermez. Otomatik teknik analiz, bakiye alanını veya yetkili dolum protokolünü keşfetmiş sayılmaz. Raporda `dolum_protokolu_dogrulandi` ve `bakiye_islemi_destekleniyor` alanları false kalır. Kart dump'ındaki rastgele sayılar bakiye olarak etiketlenmez. Üreticiye özgü bakiye işlemini eklemek için belgelenmiş veri şeması, yetkili işlem protokolü ve gerekli erişim bilgileri sağlanmalıdır.

## Metin/web bağlantısı kopyalama — sürüm 1.2

Kaynak etiketi okut, **Okunan etiketi kopyalama kaynağı seç** düğmesine dokun. Ardından farklı hedef etiketi normal şekilde okut ve **Seçilen mesajı okunan hedef etikete yaz** seçeneğini kullan. Hedef UID ve işlem açıklamasını onayladıktan sonra aynı hedefi 30 saniye içinde yeniden yaklaştır.

Kaynak mesajın bütün kayıtları standart NDEF metin veya HTTP/HTTPS web bağlantısı türünde olmalıdır. Özel/bilinmeyen kayıt varsa mesajın tamamı kopyalama için reddedilir. Hedefteki mevcut NDEF mesajı değiştirilir. Hedef yazılabilirlik ve kapasite kontrolleri ile yazma sonrası doğrulama uygulanır.

Bu, kart klonlama değildir: UID, anahtarlar, korumalı sektörler, dolum uygulaması ve bakiye kopyalanmaz. Uygulama kapanınca seçilen kopyalama kaynağı unutulur.
