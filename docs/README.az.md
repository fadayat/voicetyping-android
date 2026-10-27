# VoiceTyping Android — quraşdırma və istifadə

[Əsas README](../README.md)

VoiceTyping Android üçün ayrıca səsli klaviaturadır. Gboard silinmir və aktiv qalır. Danışıq bitəndə VoiceTyping mətni birbaşa xanaya yazır, sonra əvvəlki klaviaturaya qayıdır.

## İlkin sazlama

1. APK-ni quraşdır və VoiceTyping-i aç.
2. Öz Gemini API açarını daxil edib **Yadda saxla** düyməsinə bas.
3. **Mikrofon** icazəsini ver.
4. Tətbiqdən klaviatura ayarlarını aç.
5. **VoiceTyping səsli klaviatura** seçimini aktiv et. Gboard-u söndürmə.
6. Yazı sahəsini aç və Android-in klaviatura seçicisindən VoiceTyping-i seç.

API açarı APK-yə yazılmır. Açar cihazda Android Keystore ilə qorunur.

## İstifadə

1. Telegram, brauzer, qeyd tətbiqi və ya başqa tətbiqdə adi mətn xanasını seç.
2. Klaviatura seçicisindən VoiceTyping-i seç.
3. **Danış** düyməsinə toxun və danış.
4. Bitirdikdə **Dayandır** düyməsinə toxun.
5. Mətn aktiv xanaya yazılır və VoiceTyping əvvəlki klaviaturaya qayıdır.

Avtomatik keçid alınmasa, **Gboard-a qayıt** düyməsinə bas və sistem siyahısından Gboard-u seç.

VoiceTyping parol xanalarında səs yazısını bloklayır. Tətbiq aktiv xananın mövcud mətnini oxumur.

Gecikməni azaltmaq üçün adi mətn xanasında klaviatura görünən kimi Gemini bağlantısı əvvəlcədən hazırlana bilər. **Danış** düyməsinə toxunana qədər mikrofon açılmır və səs göndərilmir.

## Təhlükəsizlik dəyişikliyi

Version 2-də üzən düymə, “digər tətbiqlərin üzərində göstər” icazəsi və Accessibility xidməti tam silinib. Tətbiq yalnız internet və mikrofon icazəsi istəyir. Hazır mətn Android-in standart klaviatura API-si ilə xanaya yazılır.

Bu dəyişiklik bank tətbiqlərinin risk kimi gördüyü əsas imkanları aradan qaldırır. Buna baxmayaraq, hər bank tətbiqinin qərarına zəmanət vermək mümkün deyil. Cari APK debug imzalı və sideload edilmiş build-dir. Play Protect-i söndürmə. Tam istehsal etibarı üçün stabil release imzası və təsdiqlənmiş paylama kanalı lazımdır.

## Problem həlli

- **VoiceTyping siyahıda yoxdur:** Android ayarlarında klaviaturanı aktiv et.
- **Mikrofon başlamır:** VoiceTyping üçün mikrofon icazəsini yoxla.
- **Mətn yazılmır:** Adi, redaktə edilə bilən xana seç. Parol xanaları qəsdən bloklanır.
- **Gboard-a qayıtmır:** VoiceTyping-də **Gboard-a qayıt** düyməsinə bas və siyahıdan Gboard-u seç.
- **Limit xətası:** Gemini hesabının kvotasını yoxla.

## Məxfilik

Səs transkripsiya üçün birbaşa Google Gemini API-yə göndərilir. Tətbiq səsi, transkripti və yazı sahəsinin məzmununu diskdə saxlamır. Tam məlumat üçün [PRIVACY.md](../PRIVACY.md) sənədini oxu.

## Mənbədən build

Android SDK 34 və JDK 17 və ya 21 tələb olunur.

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

APK bu ünvanda yaranır: `app/build/outputs/apk/debug/app-debug.apk`.
