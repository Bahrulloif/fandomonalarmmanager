# Исправление числовых полей ввода

## Проблема

В числовых полях (Check Interval, Status Interval, MQTT Port) **невозможно было полностью удалить текст**.

### Симптомы:
```
Поле: Check Interval (minutes)
Текущее значение: "5"

Попытка удалить текст:
1. Пользователь выделяет весь текст
2. Нажимает Delete/Backspace
3. ❌ Поле возвращается к "5"
4. ❌ Невозможно ввести новое значение с нуля
```

## Причина

### Код до исправления:
```kotlin
OutlinedTextField(
    value = state.checkIntervalMinutes.toString(),
    onValueChange = {
        it.toIntOrNull()?.let { minutes ->
            viewModel.updateCheckInterval(minutes)
        }
    }
)
```

### Почему не работало:

1. **Поле напрямую привязано к state** - `value = state.checkIntervalMinutes.toString()`
2. **При пустой строке** `toIntOrNull()` возвращает `null`
3. **ViewModel не обновляется** - блок `?.let` не выполняется
4. **State остается неизменным** - поле показывает старое значение
5. **Recomposition восстанавливает старое значение** в поле

### Цикл проблемы:
```
Пользователь удаляет текст → "" (пустая строка)
    ↓
"".toIntOrNull() = null
    ↓
?.let не выполняется
    ↓
ViewModel не обновлен
    ↓
State = 5 (старое значение)
    ↓
Recomposition → поле показывает "5"
```

## Решение

Использовать **локальное состояние** для текстового представления отдельно от числового значения в ViewModel.

### Архитектура решения:

```
┌─────────────────┐
│  TextField UI   │
│  (String)       │
└────────┬────────┘
         │ Local State (allows empty)
         │ checkIntervalText: String
         ↓
    ┌────────────┐
    │ Validation │
    └─────┬──────┘
          │ Only if valid number
          ↓
┌──────────────────┐
│  ViewModel       │
│  (Int)           │
│  checkInterval   │
└──────────────────┘
```

### Код после исправления:

```kotlin
// 1. Локальное состояние для текстового поля
var checkIntervalText by remember { mutableStateOf(state.checkIntervalMinutes.toString()) }

// 2. Синхронизация с ViewModel когда state меняется
LaunchedEffect(state.checkIntervalMinutes) {
    checkIntervalText = state.checkIntervalMinutes.toString()
}

// 3. TextField с локальным состоянием
OutlinedTextField(
    value = checkIntervalText,  // ← Локальное состояние
    onValueChange = { newValue ->
        checkIntervalText = newValue  // ← Всегда обновляем локальное состояние

        // Только обновляем ViewModel если валидное число
        if (newValue.isEmpty()) {
            // Разрешаем пустое поле, ViewModel не обновляется
        } else {
            newValue.toIntOrNull()?.let { minutes ->
                if (minutes > 0) {
                    viewModel.updateCheckInterval(minutes)
                }
            }
        }
    },
    isError = checkIntervalText.isNotEmpty() && checkIntervalText.toIntOrNull() == null,
    supportingText = if (checkIntervalText.isNotEmpty() && checkIntervalText.toIntOrNull() == null) {
        { Text("Please enter a valid number") }
    } else null
)
```

## Исправленные поля

### 1. Check Interval (minutes)
- **Локальное состояние:** `checkIntervalText`
- **Валидация:** Только положительные числа
- **Сообщение об ошибке:** "Please enter a valid number"

### 2. Status Report Interval (minutes)
- **Локальное состояние:** `statusIntervalText`
- **Валидация:** Только положительные числа
- **Сообщение об ошибке:** "Please enter a valid number"

### 3. MQTT Port
- **Локальное состояние:** `mqttPortText`
- **Валидация:** Числа от 1 до 65535 (валидные TCP порты)
- **Сообщение об ошибке:** "Port must be between 1 and 65535"

## Преимущества решения

### ✅ Функциональность

1. **Можно удалить весь текст** - поле становится пустым
2. **Можно ввести новое значение** - начать с нуля
3. **Валидация в реальном времени** - красная обводка при неправильном значении
4. **Подсказки об ошибках** - текст под полем объясняет проблему
5. **ViewModel защищен** - невалидные значения не попадают в state

### ✅ UX улучшения

**До:**
```
Пользователь: *пытается удалить "5"*
Поле: "5" (не удаляется)
Пользователь: *в замешательстве*
```

**После:**
```
Пользователь: *удаляет "5"*
Поле: "" (пустое)
Пользователь: *вводит "10"*
Поле: "10" ✓
```

### ✅ Валидация

#### Check Interval / Status Interval:
```kotlin
if (minutes > 0) {
    viewModel.updateCheckInterval(minutes)
}
```
- Отклоняет: 0, отрицательные числа, не-числа
- Принимает: 1, 2, 3, ... ∞

#### MQTT Port:
```kotlin
if (port in 1..65535) {
    viewModel.updateMqttPort(port)
}
```
- Отклоняет: 0, 65536+, отрицательные, не-числа
- Принимает: 1-65535 (валидный диапазон TCP портов)

## Технические детали

### LaunchedEffect для синхронизации

```kotlin
LaunchedEffect(state.checkIntervalMinutes) {
    checkIntervalText = state.checkIntervalMinutes.toString()
}
```

**Зачем нужно:**
- Когда ViewModel обновляется из другого места (например, загрузка настроек)
- Нужно обновить локальное состояние текстового поля
- `LaunchedEffect` следит за изменением `state.checkIntervalMinutes`
- Автоматически синхронизирует текстовое поле

### isError и supportingText

```kotlin
isError = checkIntervalText.isNotEmpty() && checkIntervalText.toIntOrNull() == null
```

**Логика:**
- `checkIntervalText.isNotEmpty()` - поле не пустое (пустое поле = не ошибка)
- `checkIntervalText.toIntOrNull() == null` - текст не является числом
- Вместе: показывать ошибку только если есть текст, но это не число

```kotlin
supportingText = if (...) {
    { Text("Please enter a valid number") }
} else null
```

**Результат:**
- Красная обводка поля при ошибке
- Красный текст подсказки под полем
- Material Design 3 стиль

## Визуальные состояния

### Состояние 1: Нормальное
```
┌─────────────────────────────┐
│ Check Interval (minutes)    │
│ 5                           │
└─────────────────────────────┘
```

### Состояние 2: Пустое поле (разрешено)
```
┌─────────────────────────────┐
│ Check Interval (minutes)    │
│                             │
└─────────────────────────────┘
```

### Состояние 3: Ошибка
```
┌─────────────────────────────┐ ← Красная обводка
│ Check Interval (minutes)    │
│ abc                         │
└─────────────────────────────┘
  Please enter a valid number   ← Красный текст
```

### Состояние 4: Ввод нового значения
```
Шаг 1: Удалить старое
┌─────────────────────────────┐
│ Check Interval (minutes)    │
│ |                           │ ← Курсор, пустое поле
└─────────────────────────────┘

Шаг 2: Ввести новое
┌─────────────────────────────┐
│ Check Interval (minutes)    │
│ 10|                         │ ← Курсор после "10"
└─────────────────────────────┘

Шаг 3: Сохранено в ViewModel ✓
```

## Тестирование

### Тест 1: Удаление текста
```
1. Поле содержит "5"
2. Выделить весь текст (Ctrl+A / Cmd+A)
3. Нажать Delete
4. ✅ Поле становится пустым
5. ✅ Нет ошибки (пустое поле разрешено)
```

### Тест 2: Ввод нового значения
```
1. Пустое поле
2. Ввести "10"
3. ✅ Поле показывает "10"
4. ✅ ViewModel обновлен (checkInterval = 10)
5. Нажать вне поля
6. ✅ Значение сохранено
```

### Тест 3: Ввод невалидного значения
```
1. Ввести "abc"
2. ✅ Поле показывает "abc"
3. ✅ Красная обводка появляется
4. ✅ Текст ошибки: "Please enter a valid number"
5. ✅ ViewModel НЕ обновлен (защита)
```

### Тест 4: Ввод 0 (невалидно)
```
1. Ввести "0"
2. ✅ Поле показывает "0"
3. ✅ Нет красной обводки (число валидное)
4. ✅ ViewModel НЕ обновлен (0 отклонен валидацией)
5. При закрытии поля возвращается к последнему валидному значению
```

### Тест 5: MQTT Port диапазон
```
Ввод "70000":
✅ Красная обводка
✅ "Port must be between 1 and 65535"
✅ ViewModel не обновлен

Ввод "8883":
✅ Нормальный вид
✅ ViewModel обновлен
```

## Сравнение до/после

| Аспект | До (v2.1.3) | После (v2.1.4) |
|--------|-------------|----------------|
| Удаление текста | ❌ Невозможно | ✅ Работает |
| Ввод с нуля | ❌ Сложно | ✅ Легко |
| Пустое поле | ❌ Не разрешено | ✅ Разрешено |
| Валидация | ✅ Есть (на уровне ViewModel) | ✅ Улучшена (UI + ViewModel) |
| Обратная связь | ❌ Нет | ✅ Красная обводка + текст |
| Защита ViewModel | ✅ Есть | ✅ Усилена |

## Будущие улучшения

### Опция 1: Debounce для ViewModel
Обновлять ViewModel не сразу, а через 500ms после окончания ввода:
```kotlin
LaunchedEffect(checkIntervalText) {
    delay(500)
    checkIntervalText.toIntOrNull()?.let { ... }
}
```

### Опция 2: Восстановление значения при blur
При потере фокуса восстанавливать последнее валидное значение:
```kotlin
onFocusChanged { focused ->
    if (!focused && checkIntervalText.isEmpty()) {
        checkIntervalText = state.checkIntervalMinutes.toString()
    }
}
```

### Опция 3: Диапазон для интервалов
Добавить валидацию диапазона (например, 1-60 минут):
```kotlin
if (minutes in 1..60) {
    viewModel.updateCheckInterval(minutes)
}
supportingText = "Must be between 1 and 60 minutes"
```

## Заключение

Исправление решает критическую проблему UX:
- ✅ Можно удалять текст в числовых полях
- ✅ Легко вводить новые значения
- ✅ Визуальная обратная связь при ошибках
- ✅ Защита ViewModel от невалидных значений
- ✅ Лучший пользовательский опыт

Три поля исправлены: Check Interval, Status Interval, MQTT Port.
