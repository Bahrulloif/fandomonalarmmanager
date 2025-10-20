#!/usr/bin/env python3
"""
Скрипт для тестирования удаленных команд Fandomon через MQTT
Использует библиотеку paho-mqtt
Установка: pip3 install paho-mqtt
"""

import paho.mqtt.client as mqtt
import json
import time

# Настройки MQTT брокера
BROKER = "broker.hivemq.com"
PORT = 1883
COMMANDS_TOPIC = "fandomon/commands"
STATUS_TOPIC = "fandomon/status"
EVENTS_TOPIC = "fandomon/events"

# Callback функции
def on_connect(client, userdata, flags, rc):
    if rc == 0:
        print(f"✅ Подключено к {BROKER}:{PORT}")
        # Подписываемся на топики ответов
        client.subscribe(STATUS_TOPIC)
        client.subscribe(EVENTS_TOPIC)
        print(f"📥 Подписка на {STATUS_TOPIC} и {EVENTS_TOPIC}")
    else:
        print(f"❌ Ошибка подключения, код: {rc}")

def on_message(client, userdata, msg):
    print(f"\n📨 Получено сообщение на топик '{msg.topic}':")
    try:
        payload = json.loads(msg.payload.decode())
        print(json.dumps(payload, indent=2, ensure_ascii=False))
    except:
        print(msg.payload.decode())

def send_command(client, command_type, parameters=None):
    """Отправка команды на Fandomon"""
    command = {
        "command": command_type,
        "timestamp": int(time.time() * 1000)
    }
    if parameters:
        command["parameters"] = parameters

    payload = json.dumps(command)
    print(f"\n📤 Отправка команды {command_type}...")
    print(f"   Топик: {COMMANDS_TOPIC}")
    print(f"   Payload: {payload}")

    result = client.publish(COMMANDS_TOPIC, payload, qos=1)
    if result.rc == mqtt.MQTT_ERR_SUCCESS:
        print("✅ Команда отправлена успешно")
    else:
        print(f"❌ Ошибка отправки: {result.rc}")

    # Ждем ответа
    print("⏳ Ожидание ответа (5 секунд)...")
    time.sleep(5)

def main():
    print("=" * 60)
    print("🎯 Тестирование удаленных команд Fandomon")
    print("=" * 60)

    # Создаем MQTT клиент
    client = mqtt.Client(client_id="fandomon_test_client")
    client.on_connect = on_connect
    client.on_message = on_message

    # Подключаемся
    print(f"\n🔌 Подключение к {BROKER}:{PORT}...")
    try:
        client.connect(BROKER, PORT, 60)
        client.loop_start()
        time.sleep(2)  # Даем время подключиться

        while True:
            print("\n" + "=" * 60)
            print("Доступные команды:")
            print("1. GET_STATUS - Запросить текущий статус")
            print("2. FORCE_SYNC - Принудительная синхронизация")
            print("3. UPDATE_SETTINGS - Обновить настройки")
            print("4. RESTART_FANDOMAT - Перезапустить Fandomat")
            print("5. CLEAR_EVENTS - Очистить события")
            print("6. RESTART_FANDOMON - Перезапустить Fandomon")
            print("0. Выход")
            print("=" * 60)

            choice = input("\nВыберите команду (0-6): ").strip()

            if choice == "0":
                break
            elif choice == "1":
                send_command(client, "GET_STATUS")
            elif choice == "2":
                send_command(client, "FORCE_SYNC")
            elif choice == "3":
                print("\nПараметры для UPDATE_SETTINGS:")
                interval = input("  Интервал проверки (минуты, Enter=пропустить): ").strip()
                status = input("  Интервал статуса (минуты, Enter=пропустить): ").strip()
                name = input("  Имя устройства (Enter=пропустить): ").strip()

                params = {}
                if interval:
                    params["check_interval"] = interval
                if status:
                    params["status_interval"] = status
                if name:
                    params["device_name"] = name

                if params:
                    send_command(client, "UPDATE_SETTINGS", params)
                else:
                    print("⚠️ Параметры не указаны, команда не отправлена")
            elif choice == "4":
                confirm = input("⚠️ Перезапустить Fandomat? (yes/no): ").lower()
                if confirm == "yes":
                    send_command(client, "RESTART_FANDOMAT")
            elif choice == "5":
                confirm = input("⚠️ Очистить все события из БД? (yes/no): ").lower()
                if confirm == "yes":
                    send_command(client, "CLEAR_EVENTS")
            elif choice == "6":
                confirm = input("⚠️ Перезапустить само приложение Fandomon? (yes/no): ").lower()
                if confirm == "yes":
                    send_command(client, "RESTART_FANDOMON")
            else:
                print("❌ Неверный выбор")

    except KeyboardInterrupt:
        print("\n\n⚠️ Прервано пользователем")
    except Exception as e:
        print(f"\n❌ Ошибка: {e}")
    finally:
        print("\n🔌 Отключение от брокера...")
        client.loop_stop()
        client.disconnect()
        print("✅ Готово")

if __name__ == "__main__":
    main()
