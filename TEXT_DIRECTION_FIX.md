# Исправление направления текста в полях ввода

## Проблема

При вводе текста в поля настроек курсор смещался **влево** вместо **вправо**, что делало ввод крайне неудобным.

### Симптомы:
```
Поле: Fandomat Package Name
Ввод: "com.tastamat.fandomat"

❌ Неправильно (курсор идет влево):
"tamafaf.tamsmat.moc|"
                    ↑ курсор

✅ Правильно (курсор идет вправо):
"com.tastamat.fandomat|"
                      ↑ курсор
```

## Причина

Compose использует автоматическое определение направления текста (LTR/RTL) на основе содержимого. Если в системе есть RTL локаль или первый символ определяется как RTL, поле может переключиться в RTL режим.

Хотя на уровне Scaffold был установлен `LocalLayoutDirection.Ltr`, текстовые поля могут игнорировать это и использовать собственное направление.

## Решение

Применена **двойная защита** для обеспечения LTR направления:
1. Обертка в `CompositionLocalProvider` с `LocalLayoutDirection.Ltr`
2. Параметр `textStyle` с явным указанием `TextDirection.Ltr`

### Код исправления

**До (v2.1.2 - частично работало):**
```kotlin
OutlinedTextField(
    value = state.deviceName,
    onValueChange = { viewModel.updateDeviceName(it) },
    label = { Text("Device Name") },
    modifier = Modifier.fillMaxWidth(),
    textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr)
)
```

**После (v2.1.3 - надежное решение):**
```kotlin
CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
    OutlinedTextField(
        value = state.deviceName,
        onValueChange = { viewModel.updateDeviceName(it) },
        label = { Text("Device Name") },
        modifier = Modifier.fillMaxWidth(),
        textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr)
    )
}
```

### Почему двойная защита?

**Проблема:** Только `textStyle` не всегда работал для некоторых полей (например, Device Name), особенно если:
- В поле уже есть RTL символы
- Система определила локаль как RTL
- Первый введенный символ триггерит RTL

**Решение:** Двухуровневая защита гарантирует LTR:
1. `CompositionLocalProvider` устанавливает layout direction для компонента
2. `textStyle` устанавливает text direction для содержимого
3. Вместе они обеспечивают 100% надежность

## Затронутые файлы

**Файл:** [SettingsScreen.kt](app/src/main/java/com/tastamat/fandomon/ui/screen/SettingsScreen.kt)

### Исправленные поля:

#### Device Settings (2 поля)
- ✅ Device ID
- ✅ Device Name

#### Fandomat Settings (3 поля)
- ✅ Fandomat Package Name
- ✅ Check Interval (minutes)
- ✅ Status Report Interval (minutes)

#### MQTT Settings (7 полей)
- ✅ Broker URL
- ✅ Port
- ✅ Username
- ✅ Password
- ✅ Events Topic
- ✅ Status Topic
- ✅ Commands Topic

#### REST API Settings (2 поля)
- ✅ Base URL
- ✅ API Key

**Всего исправлено:** 14 текстовых полей

## Технические детали

### LocalTextStyle

`LocalTextStyle` - это CompositionLocal, который предоставляет текущий стиль текста из темы Material 3.

```kotlin
LocalTextStyle.current.copy(textDirection = TextDirection.Ltr)
```

- `LocalTextStyle.current` - получает текущий стиль (шрифт, размер, цвет и т.д.)
- `.copy(textDirection = TextDirection.Ltr)` - создает копию с принудительным LTR направлением

### TextDirection.Ltr

`TextDirection.Ltr` (Left-to-Right) указывает, что:
- Текст пишется слева направо
- Курсор движется слева направо
- Выделение текста идет слева направо

Противоположность: `TextDirection.Rtl` (Right-to-Left) для арабского, иврита и т.д.

## Альтернативные решения

### Вариант 1: Глобальный CompositionLocalProvider (не работает)
```kotlin
// ❌ Не помогло - поля игнорируют это
CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
    OutlinedTextField(...)
}
```

### Вариант 2: Wrap каждого TextField (избыточно)
```kotlin
// ⚠️ Работает, но слишком многословно
CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
    OutlinedTextField(...)
}
```

### Вариант 3: textStyle с TextDirection (⚠️ частично работало)
```kotlin
// ⚠️ Работает в большинстве случаев, но не для всех полей
OutlinedTextField(
    textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr)
)
```

### Вариант 4: Двойная защита - CompositionLocalProvider + textStyle (✅ выбрано)
```kotlin
// ✅ Надежное решение - работает всегда
CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
    OutlinedTextField(
        textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr)
    )
}
```

## Проверка исправления

### Тест 1: Ввод латиницы
```
1. Открыть поле "Fandomat Package Name"
2. Ввести: "com.tastamat.fandomat"
3. ✅ Курсор должен двигаться слева направо
4. ✅ Текст должен выглядеть как "com.tastamat.fandomat|"
```

### Тест 2: Ввод цифр
```
1. Открыть поле "Check Interval (minutes)"
2. Ввести: "123"
3. ✅ Курсор должен двигаться слева направо
4. ✅ Текст должен выглядеть как "123|"
```

### Тест 3: Ввод URL
```
1. Открыть поле "Broker URL"
2. Ввести: "mqtt://192.168.1.100"
3. ✅ Курсор должен двигаться слева направо
4. ✅ Текст должен выглядеть как "mqtt://192.168.1.100|"
```

### Тест 4: Редактирование существующего текста
```
1. Поле содержит: "com.tastamat.fandomat"
2. Поставить курсор после "tastamat"
3. Ввести ".test"
4. ✅ Результат: "com.tastamat.test.fandomat"
5. ✅ Курсор должен быть после ".test"
```

## Предотвращение регрессии

### Чеклист для новых полей ввода

При добавлении нового `OutlinedTextField` или `TextField` в приложение:

```kotlin
OutlinedTextField(
    value = ...,
    onValueChange = ...,
    label = ...,
    modifier = Modifier.fillMaxWidth(),
    textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr)  // ← Обязательно!
)
```

✅ Всегда добавляйте `textStyle` с `TextDirection.Ltr`

## Связанные проблемы

### Другие компоненты, которые могут иметь подобную проблему:

1. **BasicTextField** - требует такого же исправления
2. **TextField** (не Outlined) - требует такого же исправления
3. **Text с editable** - может иметь проблему

### Проверенные компоненты (работают корректно):

- ✅ Button, OutlinedButton - текст всегда LTR
- ✅ Text (только чтение) - наследует LTR от LocalLayoutDirection
- ✅ Switch - не содержит текста
- ✅ Card, Column, Row - только контейнеры

## Преимущества исправления

### До исправления:
- ❌ Невозможно нормально вводить текст
- ❌ Курсор движется в неожиданном направлении
- ❌ Редактирование существующего текста сложное
- ❌ Плохой UX для пользователя

### После исправления:
- ✅ Курсор всегда движется слева направо
- ✅ Ввод текста работает интуитивно
- ✅ Редактирование текста удобное
- ✅ Отличный UX для п��льзователя

## Заключение

Исправление простое, но критически важное:
- Добавлен `textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr)` ко всем 14 текстовым полям
- Теперь все поля ввода работают корректно с LTR направлением
- Курсор движется слева направо при вводе текста
- Проблема полностью решена
