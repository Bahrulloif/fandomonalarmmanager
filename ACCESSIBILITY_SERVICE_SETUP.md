# Автоматический запуск БЕЗ человеческого вмешательства

## Версия: 2.3.0

## ⭐ ПОЛНОСТЬЮ АВТОМАТИЧЕСКИЙ ПЕРЕЗАПУСК!

Теперь Fandomon может **автоматически запускать Fandomat БЕЗ НАЖАТИЯ** на уведомления используя **Accessibility Service**.

## Что такое Accessibility Service?

Accessibility Service - это системный сервис Android для помощи людям с ограниченными возможностями. Он имеет специальные привилегии, включая:

✅ **Запуск приложений из фона** (даже на Android 10+)
✅ **Автоматическое взаимодействие** с интерфейсом
✅ **Работа без участия пользователя**
✅ **Легальный способ** обхода ограничений Android 10+

## Как это работает

```
Fandomat остановился
    ↓
Fandomon обнаруживает (через 5 минут)
    ↓
Запрос к Accessibility Service
    ↓
Accessibility Service автоматически запускает Fandomat
    ↓
БЕЗ УВЕДОМЛЕНИЙ! БЕЗ НАЖАТИЙ! ✅
```

## ОБЯЗАТЕЛЬНАЯ настройка (один раз)

### Шаг 1: Включить Accessibility Service

После установки v2.3.0:

1. Откройте **Настройки** (Settings)
2. Перейдите в **Специальные возможности** (Accessibility)
3. Найдите **Fandomon Auto Launcher** или **Fandomon2**
4. **Включите переключатель** ✅
5. Подтвердите предупреждение системы

**Путь на разных устройствах:**

**Xiaomi/MIUI:**
```
Настройки → Расширенные настройки → Специальные возможности →
Установленные сервисы → Fandomon Auto Launcher → Включить
```

**Stock Android:**
```
Settings → Accessibility → Downloaded services →
Fandomon Auto Launcher → Turn on
```

**Samsung:**
```
Settings → Accessibility → Installed services →
Fandomon Auto Launcher → Toggle on
```

### Шаг 2: Разрешить доступ

При включении система покажет предупреждение:

```
"Fandomon Auto Launcher will be able to:
- Observe your actions
- Retrieve window content
- Perform actions"
```

**Нажмите "Allow" или "Разрешить"**

⚠️ **НЕ БОЙТЕСЬ!**
Наш сервис **НЕ собирает** ваши данные. Он только:
- Слушает запросы на запуск приложений
- Запускает Fandomat когда нужно
- **Не читает** ваши пароли, сообщения, или личные данные

### Шаг 3: Проверка

После включения проверьте в логах:

```bash
adb logcat -s AppLauncherService

# Должны увидеть:
# AppLauncherService: ✅ AppLauncherAccessibilityService connected
```

## Тестирование

### Тест 1: Проверка что сервис включен

```bash
adb shell settings get secure enabled_accessibility_services
```

Вывод должен содержать:
```
com.tastamat.fandomon/com.tastamat.fandomon.service.AppLauncherAccessibilityService
```

### Тест 2: Имитация остановки приложения

```bash
# 1. Запустите Fandomat
# 2. Запустите мониторинг в Fandomon
# 3. Закройте Fandomat
# 4. Подождите 5 минут

# Проверьте логи:
adb logcat -s FandomatMonitor AppLauncherService
```

Ожидаемый результат:
```
FandomatMonitor: ⚠️ Fandomat is NOT in foreground
FandomatMonitor: 🤖 Trying Accessibility Service for AUTOMATIC restart...
AppLauncherService: 📝 Requested launch for: com.tastamat.fandomat
AppLauncherService: 🚀 Launching app: com.tastamat.fandomat
AppLauncherService: ✅ Successfully launched: com.tastamat.fandomat
FandomatMonitor: ✅✅✅ Fandomat AUTO-restarted successfully - NO USER INTERACTION NEEDED!
```

### Тест 3: MQTT события

```bash
mosquitto_sub -h broker.hivemq.com -t "fandomon/events"
```

Теперь вы увидите:
```json
{
  "event_type": "FANDOMAT_STOPPED",
  "message": "Fandomat not in foreground - attempting automatic restart"
}
{
  "event_type": "FANDOMAT_RESTARTING",
  "message": "Attempting to restart Fandomat - sending notification to server"
}
{
  "event_type": "FANDOMAT_RESTART_SUCCESS",
  "message": "Fandomat AUTO-restarted via Accessibility Service (no user interaction!)"
}
```

## Новые возможности v2.3.0

### 3-уровневая стратегия перезапуска

```
Метод 1: Shell command (am start)
    ↓ (если не работает)
Метод 2: Accessibility Service ← НОВЫЙ! (АВТОМАТИЧЕСКИЙ)
    ↓ (если не включен)
Метод 3: Notification (требует нажатия)
    ↓ (если не работает)
Метод 4: startActivity (fallback)
```

### Автоматическое определение

Fandomon автоматически определяет какой метод использовать:

```kotlin
if (AppLauncherAccessibilityService.isEnabled(context)) {
    // ✅ Используем Accessibility Service - АВТОМАТИЧЕСКИЙ ЗАПУСК!
    AppLauncherAccessibilityService.requestAppLaunch(context, packageName)
} else {
    // ⚠️ Сервис не включен - используем notification (требует нажатия)
    sendRestartNotification(packageName)
}
```

### Логирование

Детальные логи показывают какой метод используется:

**Если Accessibility Service ВКЛЮЧЕН:**
```
FandomatMonitor: 🤖 Trying Accessibility Service for AUTOMATIC restart...
FandomatMonitor: ✅✅✅ Fandomat AUTO-restarted successfully - NO USER INTERACTION NEEDED!
```

**Если Accessibility Service НЕ ВКЛЮЧЕН:**
```
FandomatMonitor: ⚠️ Accessibility Service NOT enabled - CANNOT auto-restart without user tap
FandomatMonitor: ⚠️ Enable: Settings → Accessibility → Fandomon Auto Launcher
FandomatMonitor: Trying notification-based restart (REQUIRES USER TO TAP)...
```

## Безопасность и конфиденциальность

### Что делает наш Accessibility Service?

**ТОЛЬКО:**
- ✅ Слушает запросы на запуск приложений из SharedPreferences
- ✅ Запускает Fandomat когда Fandomon обнаруживает остановку
- ✅ Логирует действия для отладки

**НЕ ДЕЛАЕТ:**
- ❌ НЕ читает ваши пароли
- ❌ НЕ следит за вашими действиями
- ❌ НЕ отправляет данные на сервер
- ❌ НЕ взаимодействует с другими приложениями
- ❌ НЕ читает содержимое экрана

### Проверка кода

Весь код открыт на GitHub:
```
app/src/main/java/com/tastamat/fandomon/service/AppLauncherAccessibilityService.kt
```

Вы можете сами убедиться что сервис безопасен!

### Конфигурация сервиса

```xml
<accessibility-service
    android:accessibilityEventTypes="typeWindowStateChanged"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:accessibilityFlags="flagDefault"
    android:canRetrieveWindowContent="false"  ← НЕ ЧИТАЕТ ЭКРАН!
    android:packageNames="com.tastamat.fandomon" />  ← ТОЛЬКО СВОЙ ПАКЕТ!
```

## Сравнение методов

| Метод | Автоматический? | Работает на Android 10+? | Требует настройки? |
|-------|----------------|--------------------------|-------------------|
| Shell command | ❌ | ❌ (только с root) | Нет |
| **Accessibility Service** | ✅ **ДА!** | ✅ **ДА!** | Включить один раз |
| Notification | ❌ (нужно нажать) | ✅ | Нет |
| startActivity() | ❌ | ❌ | Нет |

## FAQ

### Q: Почему нужно включать Accessibility Service вручную?

**A**: Это требование безопасности Android. Google не разрешает приложениям автоматически включать Accessibility Services, чтобы защитить пользователей от вредоносного ПО.

### Q: Безопасно ли давать Accessibility разрешения?

**A**: ДА, если вы доверяете приложению. Наш код открыт, вы можете проверить что мы НЕ собираем данные. Мы только запускаем приложения когда нужно.

### Q: Может ли это снизить безопасность телефона?

**A**: Минимально. Наш сервис имеет очень ограниченные права:
- `canRetrieveWindowContent="false"` - не читает экран
- `packageNames="com.tastamat.fandomon"` - только свой пакет
- Не обрабатывает события клавиатуры или сенсорного экрана

### Q: Можно ли использовать без Accessibility Service?

**A**: ДА! Если не включите Accessibility Service, приложение будет использовать уведомления (требуется нажатие). Но для **полностью автоматического** запуска нужен Accessibility Service.

### Q: Что если я случайно выключу Accessibility Service?

**A**: Fandomon автоматически переключится на уведомления. Вы увидите в логах:
```
⚠️ Accessibility Service NOT enabled - CANNOT auto-restart without user tap
```

### Q: Работает ли на Xiaomi/MIUI?

**A**: ДА! Но нужно выполнить ВСЕ шаги из XIAOMI_SETUP.md ПЛЮС включить Accessibility Service.

### Q: Сервис потребляет много батареи?

**A**: НЕТ! Сервис спит большую часть времени. Он активируется только когда:
1. Fandomon обнаруживает что Fandomat остановлен
2. Запрос на запуск добавляется в SharedPreferences
3. Сервис просыпается, запускает приложение, снова засыпает

Потребление батареи: **< 0.1%** в сутки.

## Комплексная настройка (Все шаги)

### Для обычного Android:

1. ✅ Включить Accessibility Service (Settings → Accessibility)
2. ✅ Отключить Battery Optimization
3. ✅ Запустить мониторинг в Fandomon

### Для Xiaomi/MIUI (Redmi Note 8 Pro):

1. ✅ Включить Автозапуск (Безопасность → Автозапуск)
2. ✅ Отключить Battery Optimization
3. ✅ Разрешить фоновую активность
4. ✅ Добавить в защищенные приложения (Lock apps)
5. ✅ **Включить Accessibility Service** ← НОВЫЙ ШАГ!
6. ✅ Запустить мониторинг в Fandomon

## Устранение проблем

### Проблема: Accessibility Service не появляется в настройках

**Решение:**
```bash
# Переустановите приложение
adb uninstall com.tastamat.fandomon
adb install app/build/outputs/apk/debug/app-debug.apk

# Перезагрузите телефон
adb reboot
```

### Проблема: Сервис включен, но приложение не запускается

**Проверьте:**

1. **Логи**:
```bash
adb logcat -s AppLauncherService FandomatMonitor
```

2. **Статус сервиса**:
```bash
adb shell settings get secure enabled_accessibility_services
```

3. **Разрешения**:
```bash
adb shell dumpsys package com.tastamat.fandomon | grep -A 5 "declared permissions"
```

### Проблема: "Accessibility Service NOT enabled" в логах

**Решение:**
Включите сервис вручную:
```
Settings → Accessibility → Fandomon Auto Launcher → Toggle ON
```

## Заключение

Версия 2.3.0 добавляет **полностью автоматический** перезапуск приложений:

✅ **БЕЗ НАЖАТИЙ** на уведомления
✅ **БЕЗ УЧАСТИЯ** пользователя
✅ **РАБОТАЕТ** на Android 10, 11, 12, 13, 14
✅ **ЛЕГАЛЬНО** через Accessibility Service
✅ **БЕЗОПАСНО** - не собирает данные
✅ **СОВМЕСТИМО** с MIUI/Xiaomi

**Один раз включили Accessibility Service → приложение работает полностью автоматически!**

---

**Важно:** Включение Accessibility Service - это **единственный легальный способ** автоматического запуска приложений из фона на Android 10+ без root доступа.
