#!/usr/bin/env python3
"""
Test script for Fandomon targeted MQTT commands

This script demonstrates how to send commands to specific devices
when you have multiple Fandomon installations.

Usage:
    python test_targeted_commands.py

Requirements:
    pip install paho-mqtt
"""

import paho.mqtt.client as mqtt
import json
import time

# ============================================
# MQTT Configuration - UPDATE THESE VALUES
# ============================================
BROKER = "broker.hivemq.com"  # Your MQTT broker
PORT = 1883
USERNAME = "your_username"     # Your MQTT username
PASSWORD = "your_password"     # Your MQTT password

# ============================================
# Device IDs - UPDATE WITH YOUR DEVICE IDS
# ============================================
DEVICE_IDS = [
    "warehouse-a",
    "warehouse-b",
    "office-main",
]

# ============================================
# Available Commands
# ============================================
COMMANDS = {
    "1": {"name": "restart_fandomat", "desc": "Restart Fandomat application"},
    "2": {"name": "start_monitoring", "desc": "Start monitoring"},
    "3": {"name": "stop_monitoring", "desc": "Stop monitoring"},
    "4": {"name": "send_status", "desc": "Request status report"},
    "5": {"name": "sync_events", "desc": "Sync unsent events"},
}


def send_command_to_device(device_id, command):
    """Send command to specific device"""
    try:
        client = mqtt.Client(client_id=f"fandomon_test_{int(time.time())}")

        if USERNAME and PASSWORD:
            client.username_pw_set(USERNAME, PASSWORD)

        print(f"Connecting to {BROKER}:{PORT}...")
        client.connect(BROKER, PORT, 60)

        topic = f"fandomon/{device_id}/commands"
        payload = json.dumps({"command": command})

        result = client.publish(topic, payload, qos=1)
        result.wait_for_publish()

        print(f"✅ Sent '{command}' to device '{device_id}'")
        print(f"   Topic: {topic}")
        print(f"   Payload: {payload}")

        client.disconnect()
        return True

    except Exception as e:
        print(f"❌ Error sending command: {e}")
        return False


def send_broadcast_command(command):
    """Send command to ALL devices"""
    try:
        client = mqtt.Client(client_id=f"fandomon_broadcast_{int(time.time())}")

        if USERNAME and PASSWORD:
            client.username_pw_set(USERNAME, PASSWORD)

        print(f"Connecting to {BROKER}:{PORT}...")
        client.connect(BROKER, PORT, 60)

        topic = "fandomon/commands"
        payload = json.dumps({"command": command})

        result = client.publish(topic, payload, qos=1)
        result.wait_for_publish()

        print(f"📢 Broadcast '{command}' to ALL devices")
        print(f"   Topic: {topic}")
        print(f"   Payload: {payload}")

        client.disconnect()
        return True

    except Exception as e:
        print(f"❌ Error broadcasting command: {e}")
        return False


def print_menu():
    """Print main menu"""
    print("\n" + "="*60)
    print("Fandomon Targeted Commands Test")
    print("="*60)
    print("\n📱 Registered Devices:")
    for i, device_id in enumerate(DEVICE_IDS, 1):
        print(f"  {i}. {device_id}")
    print(f"  {len(DEVICE_IDS) + 1}. BROADCAST to ALL devices")

    print("\n🎯 Available Commands:")
    for key, cmd in COMMANDS.items():
        print(f"  {key}. {cmd['name']} - {cmd['desc']}")

    print("\n0. Exit")
    print("="*60)


def get_device_choice():
    """Get device selection from user"""
    while True:
        try:
            choice = int(input("\nSelect device (number): "))
            if choice == 0:
                return None
            if 1 <= choice <= len(DEVICE_IDS):
                return DEVICE_IDS[choice - 1]
            elif choice == len(DEVICE_IDS) + 1:
                return "BROADCAST"
            else:
                print("❌ Invalid device number")
        except ValueError:
            print("❌ Please enter a number")


def get_command_choice():
    """Get command selection from user"""
    while True:
        try:
            choice = input("\nSelect command (number): ")
            if choice in COMMANDS:
                return COMMANDS[choice]['name']
            else:
                print("❌ Invalid command number")
        except ValueError:
            print("❌ Please enter a number")


def test_targeted_command():
    """Interactive test for targeted commands"""
    print("\n" + "="*60)
    print("🧪 Testing Targeted Commands")
    print("="*60)

    # Select device
    device = get_device_choice()
    if device is None:
        return False

    # Select command
    command = get_command_choice()

    # Send command
    print("\n📤 Sending command...")
    if device == "BROADCAST":
        success = send_broadcast_command(command)
    else:
        success = send_command_to_device(device, command)

    if success:
        print("\n✅ Command sent successfully!")
        print("💡 Check the device logs to verify execution")

    return True


def demo_scenarios():
    """Run demo scenarios"""
    print("\n" + "="*60)
    print("🎬 Demo Scenarios")
    print("="*60)

    scenarios = [
        {
            "name": "Scenario 1: Restart specific device",
            "action": lambda: send_command_to_device(DEVICE_IDS[0], "restart_fandomat"),
            "desc": f"Restart Fandomat on {DEVICE_IDS[0]} only"
        },
        {
            "name": "Scenario 2: Get status from all devices",
            "action": lambda: send_broadcast_command("send_status"),
            "desc": "Request status report from all devices"
        },
        {
            "name": "Scenario 3: Stop monitoring on one device",
            "action": lambda: send_command_to_device(DEVICE_IDS[1], "stop_monitoring"),
            "desc": f"Stop monitoring on {DEVICE_IDS[1]}"
        },
    ]

    for i, scenario in enumerate(scenarios, 1):
        print(f"\n{i}. {scenario['name']}")
        print(f"   {scenario['desc']}")

        response = input("\n   Run this scenario? (y/n): ")
        if response.lower() == 'y':
            print("\n   📤 Executing...")
            scenario['action']()
            time.sleep(1)

    print("\n✅ Demo scenarios completed!")


def main():
    """Main function"""
    print("\n🚀 Fandomon Targeted Commands Test Script")
    print(f"📡 Broker: {BROKER}:{PORT}")
    print(f"👤 Username: {USERNAME}")

    if not USERNAME or USERNAME == "your_username":
        print("\n⚠️  WARNING: Update MQTT credentials in the script!")
        response = input("Continue anyway? (y/n): ")
        if response.lower() != 'y':
            return

    while True:
        print_menu()
        choice = input("\nYour choice: ")

        if choice == "0":
            print("\n👋 Goodbye!")
            break
        elif choice == "d":
            demo_scenarios()
        else:
            test_targeted_command()
            input("\nPress Enter to continue...")


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("\n\n👋 Interrupted by user. Goodbye!")
    except Exception as e:
        print(f"\n❌ Error: {e}")
