#!/usr/bin/env python3
"""Быстрый тест команды GET_STATUS"""
import paho.mqtt.client as mqtt
import json
import time

print("🔌 Подключение к broker.hivemq.com:1883...")
client = mqtt.Client()
client.connect("broker.hivemq.com", 1883, 60)
client.loop_start()
time.sleep(1)

print("📤 Отправка команды GET_STATUS...")
command = {"command": "GET_STATUS", "timestamp": int(time.time() * 1000)}
result = client.publish("fandomon/commands", json.dumps(command), qos=1)

if result.rc == 0:
    print(f"✅ Команда отправлена успешно!")
    print(f"   Payload: {json.dumps(command)}")
    print("\n📱 Проверьте логи Fandomon через:")
    print("   adb logcat | grep CommandHandler")
else:
    print(f"❌ Ошибка отправки: {result.rc}")

time.sleep(2)
client.loop_stop()
client.disconnect()
print("✅ Готово")
