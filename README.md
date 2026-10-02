# NFC Kart Okuyucu — Android kaynak projesi

Bu paket kaynak koddur; hazır APK içermez. Android 6.0+ ve NFC destekli gerçek telefon gerekir.

## Yaptıkları

- Kartın UID değerini ve telefonun algıladığı teknolojileri gösterir.
- NFC-A, NFC-V ve ISO-DEP kartların erişilebilir protokol bilgilerini gösterir.
- NDEF mesajlarını okur; metin ve URI kayıtlarını çözümler, diğer kayıtların ham verisini hex olarak gösterir.
- MIFARE Classic destekli telefon/kart birleşiminde, kullanıcının verdiği tek anahtar ile seçilen tek sektörü doğrulayıp veri bloklarını okur. Anahtar A/B seçilebilir. Anahtar ve sektör trailer bloğu rapora alınmaz.
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

- Kart belleğine yazma, formatlama veya bakiye değiştirme işlevi bulunmaz. Anahtar tahmini yapılmaz.
- Korumalı DESFire/ISO-DEP uygulamalarının belleğini okumak için üreticinin uygulama kimliği, protokolü ve erişim anahtarlarıyla ek geliştirme gerekir. Bu sürüm o alanları okuyamaz.
- NFC destekli her Android telefon MIFARE Classic okumayı desteklemek zorunda değildir. Donanım uyumsuzluğu uygulamayla giderilemez.
- 125 kHz RFID kartlar telefon NFC'siyle okunamaz; uyumlu harici okuyucu gerekir.
- Anahtar yalnızca uygulama sürecinde tutulur; dosyaya ve rapora yazılmaz. Uygulama kapanınca tekrar girilir. JSON raporu kart kimliği ve okunan verileri içerir.

## Doğrulama

Bu ortamda Android SDK/Gradle bulunmadığından APK derlenmedi ve gerçek kartta denenmedi. Platformdan bağımsız Codec sınıfının hex dönüşümü, anahtar doğrulaması, UTF-8/UTF-16 NDEF metin çözümlemesi ve bozuk kayıt kontrolleri çalıştırıldı. Java kaynaklarının sözdizimi ve XML dosyaları ayrıca kontrol edildi.

Telefon üzerinde kontrol et: NFC kapalı/açık, NDEF metin ve URL etiketi, NDEF içermeyen kart, geçerli/geçersiz Classic anahtarı, kartın erken uzaklaştırılması, uygulamanın arka plana alınması ve JSON kaydetme/iptal.

## Teknik temel

AGP 8.7.3, Gradle 8.9, compileSdk 35, targetSdk 34, minSdk 23, Java 17. Framework bileşenleri kullanılır; uygulama kütüphane bağımlılığı yoktur. targetSdk 34 kişisel yerel deneme içindir; mağaza yayını için güncel hedef SDK gereklilikleri ayrıca ele alınmalıdır.

- https://developer.android.com/reference/android/nfc/NfcAdapter
- https://developer.android.com/reference/android/nfc/tech/Ndef
- https://developer.android.com/reference/android/nfc/tech/MifareClassic
- https://developer.android.com/build/releases/agp-8-7-0-release-notes
