import socket
import os
import subprocess
import sys

# Settings
LISTEN_IP = "0.0.0.0"  # Listen on all IPs (for security, change to a specific IP)
LISTEN_PORT = 9877
BUFFER_SIZE = 1024

def shutdown_pc():
    if sys.platform == "win32":
        subprocess.call(["shutdown", "/s", "/t", "0"])
    else:  # Linux/Mac (requires permissions)
        subprocess.call(["sudo", "shutdown", "now"])

def reboot_pc():
    if sys.platform == "win32":
        subprocess.call(["shutdown", "/r", "/t", "0"])
    else:  # Linux/Mac (requires permissions)
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
    print(f"Listening on {LISTEN_IP}:{LISTEN_PORT}, waiting for remote commands...")
    with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as sock:
        sock.bind((LISTEN_IP, LISTEN_PORT))
        while True:
            data, addr = sock.recvfrom(BUFFER_SIZE)
            command = data.decode('utf-8').strip().lower()
            print(f"Received command: '{command}' from {addr}")

            if command == "shutdown":
                print("Executing shutdown...")
                shutdown_pc()
            elif command == "reboot":
                print("Executing reboot...")
                reboot_pc()
            elif command == "sleep":
                print("Executing sleep...")
                sleep_pc()
            elif command == "hibernate":
                print("Executing hibernate...")
                hibernate_pc()
            else:
                print(f"Unknown command: {command}")

if __name__ == "__main__":
    main()