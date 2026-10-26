# VoiceTyping Android — quraşdırma və istifadə

[Əsas README](../README.md)

VoiceTyping Gboard-u dəyişmədən istənilən adi mətn xanasında Azərbaycan dilində səsli yazmağa imkan verir.

## Hazır APK ilə quraşdırma

1. Reponun **Releases** bölməsindən ən son APK faylını telefona endir.
2. Android tələb edərsə, APK-ni açdığın brauzer və ya fayl meneceri üçün “naməlum tətbiq quraşdırma” icazəsini ver.
3. APK-ni quraşdır və VoiceTyping-i aç.

Release-dəki APK debug imzası ilə hazırlanıb. Mövcud fərqli imzalı versiyanın üzərinə quraşdırılmaya bilər. Belə halda əvvəlki versiyanı silmək lazım gələ bilər; bu əməl cihazdakı saxlanmış API açarını da siləcək.

## İlkin sazlama

1. Google AI Studio-dan öz Gemini API açarını əldə et.
2. Açarı tətbiqdəki **Gemini API açarı** xanasına yaz və **Yadda saxla** düyməsinə bas.
3. **Mikrofon** icazəsini ver.
4. **Üzən düymə** icazəsini ver.
5. Android-in Accessibility bölməsində **VoiceTyping mətn yerləşdirmə xidməti** xidmətini aktiv et.
6. Tətbiqə qayıdıb **Üzən düyməni başlat** düyməsinə bas.

API açarı APK-yə yazılmır. Açar cihazda Android Keystore ilə qorunur.

## İstifadə

1. Telegram, brauzer, qeyd tətbiqi və ya başqa tətbiqdə adi mətn xanasını seç.
2. Üzən VoiceTyping düyməsinə bir dəfə toxun.
3. Danış.
4. Danışıq bitəndə düyməyə yenidən toxun.
5. Hazır mətn aktiv xanaya yerləşdirilir. Birbaşa yerləşdirmə mümkün deyilsə, mətn clipboard-da qalır.

Parol xanalarına avtomatik mətn yerləşdirilmir.

## Düyməni daşımaq və bağlamaq

- Üzən düyməni ekranda istədiyin yerə sürüşdür.
- Sürüşdürmə zamanı ekranın aşağısında böyük **X** işarəsi görünür.
- Düyməni həmin işarənin üzərinə buraxanda VoiceTyping xidməti və bildiriş dayanır.
- Xidmət həmçinin tətbiqdən və bildirişdəki dayandırma əməliyyatından bağlana bilər.

## Problem həlli

- **“Gemini-yə qoşulur” uzun çəkir:** İnterneti və API açarının işlək olmasını yoxla. Tətbiq bağlantını vaxt limiti ilə dayandırıb batch üsuluna keçə bilər.
- **“Mətn hazırlanır” qalır:** v1.0.3 və sonrakı versiyalarda 30 saniyəlik qoruyucu vaxt limiti var. Ən son release-i quraşdır.
- **Mətn xanaya yazılmır:** Accessibility xidmətinin aktiv olduğunu və xananın parol xanası olmadığını yoxla. Mətn clipboard-da ola bilər.
- **Düymə görünmür:** “Digər tətbiqlərin üzərində göstər” icazəsini və davam edən VoiceTyping bildirişini yoxla.
- **Limit xətası:** Gemini hesabının kvotasını yoxla. Sorğular sənin API açarınla edilir.

## Məxfilik

Səs transkripsiya üçün birbaşa Google Gemini API-yə göndərilir. Tətbiq səsi və transkript tarixçəsini diskdə saxlamır. Tam məlumat üçün [PRIVACY.md](../PRIVACY.md) sənədini oxu.

API açarını GitHub issue-suna, ekran görüntüsünə və ya log faylına əlavə etmə.

## Mənbədən build

Android SDK 34 və JDK 17 və ya 21 tələb olunur. Cari Android Gradle Plugin JDK 25-i dəstəkləmir.

```bash
git clone https://github.com/fadayat/voicetyping-android.git
cd voicetyping-android
./gradlew testDebugUnitTest lintDebug assembleDebug
```

APK bu ünvanda yaranır:

```text
app/build/outputs/apk/debug/app-debug.apk
```
