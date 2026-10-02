# Android Studio kurmadan APK oluşturma

1. https://github.com/new sayfasında bir depo oluştur. Adı `nfc-kart-okuyucu` olabilir. Private seçebilirsin. README ekleme seçeneğini işaretlemek ilk yüklemeyi kolaylaştırır.
2. Bu ZIP'i bilgisayarında aç. ZIP dosyasının kendisini yükleme. İçindeki `app`, `.github`, `tests`, `build.gradle`, `settings.gradle`, `gradle.properties` ve diğer dosyaları GitHub deposunun **Add file → Upload files** ekranına sürükle. **Commit changes** ile kaydet.
3. `app` ve `settings.gradle` depo kökünde görünmeli; üstte fazladan `NFC-Kart-Okuyucu-Online` klasörü olmamalı. `.github/workflows/android-apk.yml` dosyasının yüklendiğini kontrol et.
4. **Actions** sekmesini aç. **NFC APK oluştur** işlemi otomatik başlayabilir. Başlamazsa solda iş akışını seçip **Run workflow** düğmesini kullan. Gerekirse depoda Actions'ı etkinleştir.
5. İşlem başarıyla tamamlanınca ilgili çalıştırmayı aç. **Artifacts** bölümündeki **NFC-Kart-Okuyucu-APK** dosyasını indir. İndirdiğin ZIP'in içinde `app-debug.apk` bulunur.
6. APK'yı NFC destekli Android telefonuna aktar ve dosyayı açarak kur.

`.github` klasörü yüklenmezse GitHub'da **Add file → Create new file** seç. Dosya adına `.github/workflows/android-apk.yml` yaz. Paketteki aynı dosyanın içeriğini metin düzenleyicisinde açıp GitHub editörüne kopyala ve kaydet.

Bu yöntem hesabındaki GitHub Actions kullanım hakkını kullanır. Standart runner'lar açık depolarda ücretsizdir; özel depolarda planın dahilindeki kota geçerlidir. Ayrı Codemagic hesabı veya bilgisayarında Android Studio kurulumu gerekmez.

İş akışı sözdizimi kontrol edildi; burada GitHub Actions çalıştırması yapılmadı. Derleme kırmızı olursa **APK oluştur** adımındaki hata kaydını paylaş. Kaynakların önceki veri çözümleme kontrolleri geçmiştir; kart okuma için gerçek telefon denemesi gerekir.

## APK imzası

APK debug anahtarıyla imzalanır; kişisel deneme içindir. Farklı bir bulut çalıştırmasında debug imzası değişebilir. Daha önceki sürümün üzerine kurulum imza hatası verirse eski uygulamayı kaldırıp yenisini kur. Sürekli dağıtım için sabit bir imza anahtarı ayrıca yapılandırılmalıdır.

Kaynaklar:
- https://docs.github.com/en/actions/tutorials/build-and-test-code/java-with-gradle
- https://docs.github.com/en/actions/how-tos/manage-workflow-runs/download-workflow-artifacts
- https://docs.github.com/en/billing/concepts/product-billing/github-actions
