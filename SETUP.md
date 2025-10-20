# Fandomon - Quick Setup Guide

## ✅ Проект успешно реализован и собран!

### Результат сборки
```
BUILD SUCCESSFUL in 1m 10s
107 actionable tasks: 106 executed
```

APK файлы находятся в:
- **Debug**: `app/build/outputs/apk/debug/app-debug.apk`
- **Release**: `app/build/outputs/apk/release/app-release.apk`

## 🚀 Быстрый старт

### 1. Установка приложения

```bash
# Через ADB
adb install app/build/outputs/apk/debug/app-debug.apk

# Или скопируйте APK на устройство и установите вручную
```

### 2. Первоначальная настройка

При первом запуске:

1. **Предоставьте разрешения**:
   - Уведомления (Android 13+)
   - Точное планирование (Android 12+)
   - Приложение автоматически запросит необходимые разрешения

2. **Настройте устройство**:
   - Device ID: `device-001` (уникальный ID для идентификации устройства)
   - Device Name: `Tablet Warehouse A` (понятное имя устройства)
   - Если не заполнено, используются значения по умолчанию

3. **Настройте параметры Fandomat**:
   - Package Name: `com.tastamat.fandomat` (измените на свой)
   - Check Interval: `5` минут (как часто проверять Fandomat)
   - Status Report Interval: `15` минут (как часто отправлять статус)

4. **Настройте MQTT** (если используете):
   - Включите переключатель MQTT
   - Broker URL: `mqtt.example.com` (ваш брокер)
   - Port: `1883`
   - Username: `fandomon_user`
   - Password: `********`
   - Events Topic: `fandomon/events`
   - Status Topic: `fandomon/status`

5. **Настройте REST API** (если используете):
   - Включите переключатель REST
   - Base URL: `https://api.example.com/`
   - API Key: `********`

6. **Запустите мониторинг**:
   - Нажмите кнопку **"Start Monitoring"**
   - Приложение начнет работать в фоновом режиме

### 3. Проверка работы

```bash
# Смотрите логи
adb logcat | grep Fandomon

# Ожидаемые сообщения:
# D/AlarmScheduler: Scheduled Fandomat check every 5 minutes
# D/FandomatMonitor: Fandomat (com.tastamat.fandomat) running: true
```

## 📋 Чек-лист после установки

- [ ] Приложение установлено
- [ ] Разрешения предоставлены
- [ ] Package name Fandomat настроен
- [ ] MQTT или REST API настроены
- [ ] Мониторинг запущен
- [ ] Логи показывают активность
- [ ] Устройство добавлено в исключения оптимизации батареи

### Добавление в исключения батареи

Для надежной работы AlarmManager:

1. Настройки → Батарея
2. Оптимизация батареи
3. Найдите "Fandomon"
4. Выберите "Не оптимизировать"

## 🧪 Тестирование

### Тест 1: Остановка Fandomat
```bash
# Остановите Fandomat
adb shell am force-stop com.tastamat.fandomat

# Проверьте логи - Fandomon должен обнаружить остановку и перезапустить
adb logcat | grep Fandomon
```

### Тест 2: Отключение интернета
```bash
# Отключите Wi-Fi на устройстве
# Проверьте логи - событие должно быть залогировано
adb logcat | grep NetworkChangeReceiver
```

### Тест 3: Отключение питания
```bash
# Отключите зарядное устройство
# Проверьте логи - событие должно быть залогировано
adb logcat | grep PowerConnectionReceiver
```

### Тест 4: Перезагрузка устройства
```bash
# Перезагрузите устройство
adb reboot

# После загрузки проверьте, что мониторинг возобновился
adb logcat | grep BootReceiver
```

## 📊 Мониторинг работы

### Просмотр событий в базе данных

```bash
# Зайдите в shell устройства
adb shell

# Просмотр базы данных (требуется root или debug build)
run-as com.tastamat.fandomon
cd /data/data/com.tastamat.fandomon/databases
sqlite3 fandomon_database

# SQL запросы
SELECT * FROM monitor_events ORDER BY timestamp DESC LIMIT 10;
SELECT COUNT(*) FROM monitor_events WHERE isSent = 0;
```

### Проверка отправки данных

#### MQTT:
```bash
# Подпишитесь на топики (на вашем сервере)
mosquitto_sub -h mqtt.example.com -u fandomon_user -P password -t "fandomon/#" -v
```

#### REST API:
```bash
# Проверьте логи сервера на наличие POST запросов
tail -f /var/log/api/access.log | grep fandomon
```

## ⚙️ Конфигурация

### Изменение интервалов

Интервалы можно изменить в настройках приложения:
- **Check Interval**: как часто проверять состояние Fandomat (минимум 1 минута)
- **Status Report Interval**: как часто отправлять статус (минимум 1 минута)

После изменения нажмите "Start Monitoring" для перезапуска с новыми настройками.

### Формат данных

#### Event (MQTT/REST):
```json
{
  "id": 123,
  "event_type": "FANDOMAT_STOPPED",
  "timestamp": 1699876543210,
  "message": "Fandomat application stopped",
  "device_id": "abc123def456"
}
```

#### Status (MQTT/REST):
```json
{
  "fandomon_running": true,
  "fandomat_running": false,
  "internet_connected": true,
  "timestamp": 1699876543210,
  "device_id": "abc123def456"
}
```

## 🔧 Troubleshooting

### Проблема: Мониторинг не работает

**Решение**:
1. Проверьте что приложение не в списке оптимизации батареи
2. Проверьте разрешение SCHEDULE_EXACT_ALARM (Android 12+)
3. Перезапустите мониторинг через UI

### Проблема: Fandomat не перезапускается

**Решение**:
1. Проверьте правильность package name
2. Убедитесь что Fandomat установлен
3. Проверьте разрешение QUERY_ALL_PACKAGES
4. Проверьте логи: `adb logcat | grep FandomatMonitor`

### Проблема: События не отправляются

**Решение**:
1. Проверьте настройки MQTT/REST
2. Проверьте интернет-соединение
3. Проверьте логи: `adb logcat | grep DataSyncService`
4. Проверьте firewall на сервере

### Проблема: После перезагрузки мониторинг не работает

**Решение**:
1. Проверьте разрешение RECEIVE_BOOT_COMPLETED
2. Проверьте логи: `adb logcat | grep BootReceiver`
3. Убедитесь что приложение не отключено системой

## 📱 Поддерживаемые версии Android

- **Минимум**: Android 11 (API 30)
- **Целевая**: Android 14 (API 36)
- **Тестировано**: Android 11, 12, 13, 14

## 🔒 Безопасность

- Все данные передаются по HTTPS (для REST)
- MQTT поддерживает аутентификацию
- API ключи хранятся в зашифрованном DataStore
- База данных локальная, доступна только приложению

## 📚 Дополнительные ресурсы

- [README.md](README.md) - Полная документация
- [API_EXAMPLES.md](API_EXAMPLES.md) - Примеры серверной реализации
- [BUILDING.md](BUILDING.md) - Инструкции по сборке
- [spec.md](spec.md) - Техническая спецификация

## 💡 Советы

1. **Регулярно проверяйте логи** для выявления проблем
2. **Настройте автоматическую очистку** старых событий (реализовано в EventRepository)
3. **Используйте MQTT для real-time** мониторинга
4. **Используйте REST для надежной** доставки событий
5. **Настройте уведомления на сервере** для критических событий

## 🆘 Поддержка

При возникновении проблем:

1. Соберите логи: `adb logcat > fandomon_logs.txt`
2. Проверьте версию приложения: Settings → About → Version
3. Опишите проблему и шаги для воспроизведения
4. Приложите логи и версию Android

---

**Приложение готово к использованию!** 🎉

Для производственного использования рекомендуется:
- Создать release build с подписью
- Настроить ProGuard для обфускации
- Настроить серверную часть для приёма данных
- Настроить мониторинг и алерты
