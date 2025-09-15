#!/usr/bin/env python3
"""
Remote Monitoring Server - Образовательный проект
Сервер для безопасного управления Android устройствами с согласия пользователя
"""

import socket
import json
import threading
import ssl
import hashlib
import time
import logging
from datetime import datetime
import base64

# Настройка логирования
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s',
    handlers=[
        logging.FileHandler('monitoring_server.log'),
        logging.StreamHandler()
    ]
)

class MonitoringServer:
    def __init__(self, host='0.0.0.0', port=8443):
        self.host = host
        self.port = port
        self.clients = {}
        self.authenticated_clients = set()
        self.server_key = "educational_project_key_2024"  # В реальном проекте используйте случайный ключ
        
    def generate_challenge(self):
        """Генерация challenge для аутентификации"""
        timestamp = str(int(time.time()))
        challenge = hashlib.sha256((self.server_key + timestamp).encode()).hexdigest()
        return challenge, timestamp
    
    def verify_auth(self, client_response, challenge, timestamp):
        """Проверка аутентификации клиента"""
        expected = hashlib.sha256((challenge + self.server_key).encode()).hexdigest()
        return client_response == expected
    
    def handle_client(self, client_socket, address):
        """Обработка подключения клиента"""
        logging.info(f"Новое подключение от {address}")
        client_id = f"{address[0]}:{address[1]}"
        self.clients[client_id] = client_socket
        
        try:
            # Аутентификация
            challenge, timestamp = self.generate_challenge()
            auth_request = {
                "type": "auth_challenge",
                "challenge": challenge,
                "timestamp": timestamp
            }
            
            client_socket.send(json.dumps(auth_request).encode() + b'\n')
            response = client_socket.recv(1024).decode().strip()
            
            try:
                auth_data = json.loads(response)
                if self.verify_auth(auth_data.get('response'), challenge, timestamp):
                    self.authenticated_clients.add(client_id)
                    logging.info(f"Клиент {client_id} аутентифицирован")
                    
                    # Отправка подтверждения аутентификации
                    success_msg = {"type": "auth_success", "message": "Аутентификация успешна"}
                    client_socket.send(json.dumps(success_msg).encode() + b'\n')
                    
                    # Основной цикл обработки команд
                    self.command_loop(client_socket, client_id)
                else:
                    logging.warning(f"Неудачная аутентификация от {client_id}")
                    error_msg = {"type": "auth_error", "message": "Аутентификация не удалась"}
                    client_socket.send(json.dumps(error_msg).encode() + b'\n')
            except json.JSONDecodeError:
                logging.error(f"Ошибка декодирования JSON от {client_id}")
                
        except Exception as e:
            logging.error(f"Ошибка при обработке клиента {client_id}: {e}")
        finally:
            if client_id in self.clients:
                del self.clients[client_id]
            if client_id in self.authenticated_clients:
                self.authenticated_clients.remove(client_id)
            client_socket.close()
            logging.info(f"Соединение с {client_id} закрыто")
    
    def command_loop(self, client_socket, client_id):
        """Основной цикл обработки команд"""
        while True:
            try:
                # Меню команд
                menu = {
                    "type": "command_menu",
                    "commands": [
                        {"id": "info", "name": "Информация о системе", "description": "Получить базовую информацию об устройстве"},
                        {"id": "battery", "name": "Статус батареи", "description": "Уровень заряда и статус зарядки"},
                        {"id": "location", "name": "GPS координаты", "description": "Текущее местоположение (требует разрешения)"},
                        {"id": "photo", "name": "Сделать фото", "description": "Фото с камеры (требует разрешения)"},
                        {"id": "network", "name": "Информация о сети", "description": "Статус сетевых подключений"},
                        {"id": "storage", "name": "Хранилище", "description": "Информация о свободном месте"},
                        {"id": "apps", "name": "Установленные приложения", "description": "Список установленных приложений"},
                        {"id": "disconnect", "name": "Отключиться", "description": "Завершить сеанс"}
                    ]
                }
                
                client_socket.send(json.dumps(menu).encode() + b'\n')
                
                # Получение выбора пользователя
                response = client_socket.recv(4096).decode().strip()
                if not response:
                    break
                    
                try:
                    command_data = json.loads(response)
                    command = command_data.get('command')
                    
                    logging.info(f"Получена команда '{command}' от {client_id}")
                    
                    if command == 'disconnect':
                        break
                    elif command in ['info', 'battery', 'location', 'photo', 'network', 'storage', 'apps']:
                        # Отправка команды устройству
                        cmd_request = {
                            "type": "execute_command",
                            "command": command,
                            "timestamp": int(time.time())
                        }
                        client_socket.send(json.dumps(cmd_request).encode() + b'\n')
                        
                        # Получение результата
                        result = client_socket.recv(8192).decode().strip()
                        if result:
                            result_data = json.loads(result)
                            self.display_result(command, result_data)
                    else:
                        logging.warning(f"Неизвестная команда '{command}' от {client_id}")
                        
                except json.JSONDecodeError:
                    logging.error(f"Ошибка декодирования команды от {client_id}")
                    break
                    
            except Exception as e:
                logging.error(f"Ошибка в командном цикле для {client_id}: {e}")
                break
    
    def display_result(self, command, result_data):
        """Отображение результата команды"""
        print(f"\n=== Результат команды '{command}' ===")
        print(f"Статус: {result_data.get('status', 'unknown')}")
        print(f"Время: {datetime.fromtimestamp(result_data.get('timestamp', 0))}")
        
        data = result_data.get('data', {})
        
        if command == 'info':
            print(f"Устройство: {data.get('device_model', 'Неизвестно')}")
            print(f"Android версия: {data.get('android_version', 'Неизвестно')}")
            print(f"Производитель: {data.get('manufacturer', 'Неизвестно')}")
        elif command == 'battery':
            print(f"Уровень заряда: {data.get('level', 'Неизвестно')}%")
            print(f"Статус зарядки: {data.get('status', 'Неизвестно')}")
        elif command == 'location':
            if data.get('latitude') and data.get('longitude'):
                print(f"Широта: {data.get('latitude')}")
                print(f"Долгота: {data.get('longitude')}")
                print(f"Точность: {data.get('accuracy', 'Неизвестно')} м")
            else:
                print("GPS недоступен или нет разрешения")
        elif command == 'photo':
            if data.get('image_base64'):
                # Сохранение фото
                img_data = base64.b64decode(data.get('image_base64'))
                filename = f"photo_{int(time.time())}.jpg"
                with open(filename, 'wb') as f:
                    f.write(img_data)
                print(f"Фото сохранено как: {filename}")
            else:
                print("Не удалось получить фото")
        elif command == 'network':
            print(f"WiFi: {data.get('wifi_status', 'Неизвестно')}")
            print(f"Мобильная сеть: {data.get('mobile_status', 'Неизвестно')}")
        elif command == 'storage':
            print(f"Общий объем: {data.get('total_space', 'Неизвестно')} MB")
            print(f"Свободное место: {data.get('free_space', 'Неизвестно')} MB")
        elif command == 'apps':
            apps = data.get('installed_apps', [])
            print(f"Количество приложений: {len(apps)}")
            for app in apps[:10]:  # Показываем первые 10
                print(f"- {app.get('name', 'Неизвестно')} ({app.get('package', 'Неизвестно')})")
        
        print("=" * 50)
    
    def start_server(self):
        """Запуск сервера"""
        server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        server_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        
        try:
            server_socket.bind((self.host, self.port))
            server_socket.listen(5)
            logging.info(f"Сервер запущен на {self.host}:{self.port}")
            print(f"Сервер мониторинга запущен на {self.host}:{self.port}")
            print("Ожидание подключений...")
            
            while True:
                client_socket, address = server_socket.accept()
                client_thread = threading.Thread(
                    target=self.handle_client,
                    args=(client_socket, address)
                )
                client_thread.daemon = True
                client_thread.start()
                
        except KeyboardInterrupt:
            logging.info("Сервер остановлен пользователем")
            print("\nСервер остановлен")
        except Exception as e:
            logging.error(f"Ошибка сервера: {e}")
        finally:
            server_socket.close()

def main():
    print("=== Remote Monitoring Server - Образовательный проект ===")
    print("⚠️  Используйте только для образовательных целей!")
    print("⚠️  Получайте явное согласие перед мониторингом устройств!")
    print()
    
    # Настройки сервера
    host = input("Введите IP адрес сервера (по умолчанию 0.0.0.0): ").strip() or "0.0.0.0"
    port_input = input("Введите порт сервера (по умолчанию 8443): ").strip()
    port = int(port_input) if port_input.isdigit() else 8443
    
    server = MonitoringServer(host, port)
    server.start_server()

if __name__ == "__main__":
    main()