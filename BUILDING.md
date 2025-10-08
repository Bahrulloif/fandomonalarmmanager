# Building and Deployment Guide

## Prerequisites

- Android Studio Hedgehog | 2023.1.1 или новее
- JDK 11 или выше
- Android SDK с API Level 30+
- Gradle 8.0+

## Build Instructions

### 1. Синхронизация проекта

После открытия проекта в Android Studio:

```bash
# В терминале Android Studio
./gradlew clean
./gradlew build
```

Или через UI: File → Sync Project with Gradle Files

### 2. Debug Build

```bash
./gradlew assembleDebug
```

APK будет создан в: `app/build/outputs/apk/debug/app-debug.apk`

### 3. Release Build

Для создания release версии нужно настроить подпись:

#### Создание keystore

```bash
keytool -genkey -v -keystore fandomon-release-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias fandomon
```

#### Добавление в gradle.properties

```properties
# ~/.gradle/gradle.properties или ./gradle.properties
FANDOMON_RELEASE_STORE_FILE=../fandomon-release-key.jks
FANDOMON_RELEASE_STORE_PASSWORD=your_store_password
FANDOMON_RELEASE_KEY_ALIAS=fandomon
FANDOMON_RELEASE_KEY_PASSWORD=your_key_password
```

#### Обновление app/build.gradle.kts

```kotlin
android {
    ...
    signingConfigs {
        create("release") {
            storeFile = file(System.getenv("FANDOMON_RELEASE_STORE_FILE") ?: "../fandomon-release-key.jks")
            storePassword = System.getenv("FANDOMON_RELEASE_STORE_PASSWORD")
            keyAlias = System.getenv("FANDOMON_RELEASE_KEY_ALIAS")
            keyPassword = System.getenv("FANDOMON_RELEASE_KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }
}
```

#### Сборка release

```bash
./gradlew assembleRelease
```

APK: `app/build/outputs/apk/release/app-release.apk`

### 4. Bundle (AAB) для Google Play

```bash
./gradlew bundleRelease
```

Bundle: `app/build/outputs/bundle/release/app-release.aab`

## ProGuard Rules

Добавьте в `app/proguard-rules.pro`:

```proguard
# Retrofit
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# MQTT
-keep class org.eclipse.paho.** { *; }
-dontwarn org.eclipse.paho.**

# DTOs
-keep class com.tastamat.fandomon.data.remote.dto.** { *; }
-keep class com.tastamat.fandomon.data.model.** { *; }

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}
```

## Testing

### Unit Tests

```bash
./gradlew test
```

### Instrumented Tests

```bash
# Убедитесь что устройство/эмулятор подключен
./gradlew connectedAndroidTest
```

## Installation

### Via ADB

```bash
adb install app/build/outputs/apk/debug/app-debug.apk

# Для release
adb install app/build/outputs/apk/release/app-release.apk

# С заменой существующей версии
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Direct Install

Скопируйте APK на устройство и откройте через файловый менеджер.

## Deployment Checklist

- [ ] Обновлен versionCode в build.gradle.kts
- [ ] Обновлен versionName в build.gradle.kts
- [ ] Протестирована сборка на целевом устройстве
- [ ] Проверены все разрешения
- [ ] Проверена работа после перезагрузки
- [ ] Протестирована работа в Doze Mode
- [ ] Проверена работа MQTT/REST подключений
- [ ] Проверен автоматический перезапуск Fandomat
- [ ] Проверено логирование событий
- [ ] Создана резервная копия keystore файла

## Version Management

Обновите в `app/build.gradle.kts`:

```kotlin
defaultConfig {
    applicationId = "com.tastamat.fandomon"
    minSdk = 30
    targetSdk = 36
    versionCode = 1  // Увеличивайте для каждого релиза
    versionName = "1.0.0"  // Semantic versioning
}
```

## Continuous Integration (Optional)

### GitHub Actions Example

`.github/workflows/android.yml`:

```yaml
name: Android CI

on:
  push:
    branches: [ main ]
  pull_request:
    branches: [ main ]

jobs:
  build:
    runs-on: ubuntu-latest

    steps:
    - uses: actions/checkout@v3

    - name: Set up JDK 11
      uses: actions/setup-java@v3
      with:
        java-version: '11'
        distribution: 'temurin'

    - name: Grant execute permission for gradlew
      run: chmod +x gradlew

    - name: Build with Gradle
      run: ./gradlew build

    - name: Run tests
      run: ./gradlew test

    - name: Upload APK
      uses: actions/upload-artifact@v3
      with:
        name: app-debug
        path: app/build/outputs/apk/debug/app-debug.apk
```

## Troubleshooting

### Build Errors

**Problem**: `Failed to resolve: androidx.room:room-compiler`
**Solution**: Sync project with Gradle files, проверьте интернет-соединение

**Problem**: `Execution failed for task ':app:kspDebugKotlin'`
**Solution**: Clean project и rebuild
```bash
./gradlew clean
./gradlew build
```

**Problem**: `AAPT: error: resource android:attr/lStar not found`
**Solution**: Обновите compileSdk до 31 или выше

### Runtime Errors

**Problem**: Приложение крашится при старте
**Solution**: Проверьте logcat для stack trace
```bash
adb logcat | grep Fandomon
```

**Problem**: MQTT не подключается
**Solution**: Проверьте разрешение INTERNET и настройки брокера

**Problem**: AlarmManager не срабатывает
**Solution**: Проверьте настройки оптимизации батареи для приложения

## Best Practices

1. **Всегда тестируйте на реальном устройстве** перед деплоем
2. **Храните keystore в безопасном месте** и создайте резервные копии
3. **Используйте semantic versioning** для versionName
4. **Документируйте изменения** в каждом релизе
5. **Тестируйте на разных версиях Android** (минимум API 30)

## Distribution

### Internal Distribution

1. Создайте release APK
2. Загрузите на внутренний сервер/облако
3. Поделитесь ссылкой с администраторами
4. Документируйте инструкции по установке

### Google Play (Internal Testing Track)

1. Создайте AAB файл
2. Загрузите в Google Play Console
3. Добавьте тестеров
4. Распространите через Internal Testing Track

## Support

Для вопросов и проблем:
- Проверьте логи: `adb logcat | grep Fandomon`
- Создайте issue с описанием проблемы
- Приложите версию приложения и Android
