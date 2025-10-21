#!/bin/bash

echo "======================================"
echo "Boot Receiver Test Script"
echo "======================================"
echo ""

# Цвета для вывода
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${YELLOW}1. Проверка установки приложения...${NC}"
adb shell pm list packages | grep com.tastamat.fandomon
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✅ Приложение установлено${NC}"
else
    echo -e "${RED}❌ Приложение НЕ установлено${NC}"
    exit 1
fi

echo ""
echo -e "${YELLOW}2. Проверка информации об устройстве...${NC}"
echo "Manufacturer: $(adb shell getprop ro.product.manufacturer)"
echo "Brand: $(adb shell getprop ro.product.brand)"
echo "Model: $(adb shell getprop ro.product.model)"
echo "Android: $(adb shell getprop ro.build.version.release)"

echo ""
echo -e "${YELLOW}3. Проверка BootReceiver в манифесте...${NC}"
adb shell dumpsys package com.tastamat.fandomon | grep -A 5 "BootReceiver"

echo ""
echo -e "${YELLOW}4. Проверка сохраненного состояния мониторинга...${NC}"
adb shell run-as com.tastamat.fandomon ls -la files/datastore/ 2>/dev/null || echo "DataStore файлы недоступны (требуется root)"

echo ""
echo -e "${YELLOW}5. Очистка логов...${NC}"
adb logcat -c

echo ""
echo -e "${YELLOW}6. Отправка BOOT_COMPLETED broadcast...${NC}"
adb shell am broadcast -a android.intent.action.BOOT_COMPLETED -p com.tastamat.fandomon

echo ""
echo -e "${YELLOW}7. Ожидание 5 секунд...${NC}"
sleep 5

echo ""
echo -e "${YELLOW}8. Проверка логов BootReceiver...${NC}"
echo -e "${GREEN}======================== BOOT RECEIVER LOGS ========================${NC}"
adb logcat -d -s BootReceiver

echo ""
echo -e "${GREEN}======================== ALARM SCHEDULER LOGS ========================${NC}"
adb logcat -d -s AlarmScheduler

echo ""
echo -e "${GREEN}======================== XIAOMI UTILS LOGS ========================${NC}"
adb logcat -d -s XiaomiUtils

echo ""
echo -e "${GREEN}======================== MAIN ACTIVITY LOGS ========================${NC}"
adb logcat -d -s MainActivity

echo ""
echo -e "${YELLOW}9. Проверка запущенных alarm-ов...${NC}"
adb shell dumpsys alarm | grep com.tastamat.fandomon

echo ""
echo "======================================"
echo -e "${GREEN}Тест завершен!${NC}"
echo "======================================"
echo ""
echo -e "${YELLOW}Для реальной перезагрузки выполните:${NC}"
echo "  adb reboot"
echo ""
echo -e "${YELLOW}После перезагрузки проверьте логи:${NC}"
echo "  adb logcat -s BootReceiver AlarmScheduler"
