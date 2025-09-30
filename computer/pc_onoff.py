import socket
import os
import subprocess
import sys

# 設定
LISTEN_IP = "0.0.0.0"  # 監聽所有 IP（若要安全，可改為特定 IP）
LISTEN_PORT = 9877
BUFFER_SIZE = 1024

def shutdown_pc():
    if sys.platform == "win32":
        subprocess.call(["shutdown", "/s", "/t", "0"])
    else:  # Linux/Mac (需權限)
        subprocess.call(["sudo", "shutdown", "now"])

def reboot_pc():
    if sys.platform == "win32":
        subprocess.call(["shutdown", "/r", "/t", "0"])
    else:  # Linux/Mac (需權限)
        subprocess.call(["sudo", "reboot"])

def sleep_pc():
    if sys.platform == "win32":
        subprocess.call(["rundll32.exe", "powrprof.dll,SetSuspendState", "0,1,0"])
    else: # Linux/Mac (needs configuration)
        subprocess.call(["sudo", "pm-suspend"])

def hibernate_pc():
    if sys.platform == "win32":
        subprocess.call(["rundll32.exe", "powrprof.dll,SetSuspendState", "1,1,0"])
    else: # Linux/Mac (needs configuration)
        subprocess.call(["sudo", "pm-hibernate"])

def main():
    print(f"監聽 {LISTEN_IP}:{LISTEN_PORT}，等待遠端命令...")
    with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as sock:
        sock.bind((LISTEN_IP, LISTEN_PORT))
        while True:
            data, addr = sock.recvfrom(BUFFER_SIZE)
            command = data.decode('utf-8').strip().lower()
            print(f"收到命令: '{command}' 來自 {addr}")

            if command == "shutdown":
                print("執行關機...")
                shutdown_pc()
            elif command == "reboot":
                print("執行重新開機...")
                reboot_pc()
            elif command == "sleep":
                print("執行睡眠...")
                sleep_pc()
            elif command == "hibernate":
                print("執行休眠...")
                hibernate_pc()
            else:
                print(f"未知命令: {command}")

if __name__ == "__main__":
    main()