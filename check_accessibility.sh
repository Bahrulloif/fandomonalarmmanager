#!/bin/bash

echo "======================================"
echo "Accessibility Service Check Script"
echo "======================================"
echo ""

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${YELLOW}1. Проверка установки приложения...${NC}"
adb shell pm list packages | grep com.tastamat.fandomon
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✅ Fandomon установлен${NC}"
else
    echo -e "${RED}❌ Fandomon НЕ установлен${NC}"
    exit 1
fi

echo ""
echo -e "${YELLOW}2. Проверка статуса Accessibility Service...${NC}"
ENABLED_SERVICES=$(adb shell settings get secure enabled_accessibility_services)
echo "Enabled services: $ENABLED_SERVICES"

if echo "$ENABLED_SERVICES" | grep -q "com.tastamat.fandomon/com.tastamat.fandomon.service.AppLauncherAccessibilityService"; then
    echo -e "${GREEN}✅ Accessibility Service ВКЛЮЧЕН${NC}"
else
    echo -e "${RED}❌ Accessibility Service НЕ ВКЛЮЧЕН${NC}"
    echo -e "${YELLOW}Включите: Settings → Accessibility → Fandomon Auto Launcher${NC}"
fi

echo ""
echo -e "${YELLOW}3. Проверка разрешений приложения...${NC}"
adb shell dumpsys package com.tastamat.fandomon | grep -A 3 "grantedPermissions"

echo ""
echo -e "${YELLOW}4. Очистка логов...${NC}"
adb logcat -c

echo ""
echo -e "${YELLOW}5. Проверка логов Accessibility Service (последние 10 секунд)...${NC}"
sleep 1
adb logcat -d -s AppLauncherService | tail -20

echo ""
echo -e "${YELLOW}6. Проверка логов FandomatMonitor (последние)...${NC}"
adb logcat -d -s FandomatMonitor | tail -30

echo ""
echo -e "${YELLOW}7. Проверка SharedPreferences для pending launch...${NC}"
adb shell run-as com.tastamat.fandomon cat shared_prefs/app_launcher_prefs.xml 2>/dev/null || echo "Нет pending launch запросов"

echo ""
echo -e "${YELLOW}8. Тест: Запрос на запуск Fandomat...${NC}"
echo "Симулируем запрос на запуск..."

# Создаем тестовый запрос
adb shell "run-as com.tastamat.fandomon sh -c 'echo \"<?xml version='\\''1.0'\\'' encoding='\\''utf-8'\\'' standalone='\\''yes'\\'' ?><map><string name=\\\"pending_launch_package\\\">com.tastamat.fandomat</string></map>\" > shared_prefs/app_launcher_prefs.xml'" 2>/dev/null

echo ""
echo -e "${YELLOW}9. Ожидание 5 секунд и проверка логов...${NC}"
sleep 5

adb logcat -d -s AppLauncherService FandomatMonitor | tail -40

echo ""
echo "======================================"
echo -e "${GREEN}Проверка завершена!${NC}"
echo "======================================"
echo ""
echo -e "${YELLOW}Для просмотра live логов:${NC}"
echo "  adb logcat -s AppLauncherService FandomatMonitor"
