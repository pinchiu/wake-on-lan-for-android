#![cfg_attr(not(test), windows_subsystem = "windows")]

use std::env;
use std::fs::{self, OpenOptions};
use std::io::{Read, Write};
use std::net::{SocketAddr, TcpListener, TcpStream, UdpSocket};
use std::path::{Path, PathBuf};
use std::process::Command;
use std::sync::atomic::{AtomicBool, AtomicU16, Ordering};
use std::sync::Mutex;
use std::thread;
use std::time::Duration;

const LISTEN_IP: &str = "0.0.0.0";
const DEFAULT_UDP_PORT: u16 = 9877;
const DEFAULT_WEB_PORT: u16 = 9878;
const BUFFER_SIZE: usize = 1024;
const APP_NAME: &str = "pc_onoff";
const CONFIG_FILE_NAME: &str = "config.ini";

// Windows 註冊表常數
const REG_RUN_KEY: &str = r"HKCU\Software\Microsoft\Windows\CurrentVersion\Run";
const REG_RUN_NAME: &str = "WakeOnLanPcOnOff";

// 全域共享狀態
static SERVER_RUNNING: AtomicBool = AtomicBool::new(false);
static ACTIVE_PORT: AtomicU16 = AtomicU16::new(DEFAULT_UDP_PORT);
static RECENT_LOGS: Mutex<Vec<String>> = Mutex::new(Vec::new());

#[repr(C)]
#[derive(Default, Debug, Clone, Copy)]
struct SystemTimeWin {
    w_year: u16,
    w_month: u16,
    w_day_of_week: u16,
    w_day: u16,
    w_hour: u16,
    w_minute: u16,
    w_second: u16,
    w_milliseconds: u16,
}

#[cfg(windows)]
mod win_api {
    use super::SystemTimeWin;

    #[link(name = "user32")]
    #[link(name = "kernel32")]
    #[link(name = "shell32")]
    unsafe extern "system" {
        pub fn GetConsoleWindow() -> *mut std::ffi::c_void;
        pub fn ShowWindow(hWnd: *mut std::ffi::c_void, nCmdShow: i32) -> i32;
        pub fn GetLocalTime(lpSystemTime: *mut SystemTimeWin);
    }

    pub fn hide_console() {
        unsafe {
            let hwnd = GetConsoleWindow();
            if !hwnd.is_null() {
                ShowWindow(hwnd, 0); // SW_HIDE = 0
            }
        }
    }

    pub fn get_local_time() -> SystemTimeWin {
        let mut st = SystemTimeWin::default();
        unsafe {
            GetLocalTime(&mut st);
        }
        st
    }
}

#[derive(Debug, Clone)]
pub struct AppConfig {
    pub udp_port: u16,
    pub web_port: u16,
    pub autostart: bool,
    pub enable_web_ui: bool,
}

impl Default for AppConfig {
    fn default() -> Self {
        Self {
            udp_port: DEFAULT_UDP_PORT,
            web_port: DEFAULT_WEB_PORT,
            autostart: true, // 預設直接啟用開機自動啟動，無需使用者在終端機手動選擇
            enable_web_ui: true,
        }
    }
}

impl AppConfig {
    pub fn load() -> Self {
        let path = get_config_file_path();
        let mut cfg = AppConfig::default();

        if let Ok(content) = fs::read_to_string(&path) {
            for line in content.lines() {
                let parts: Vec<&str> = line.splitn(2, '=').collect();
                if parts.len() == 2 {
                    let k = parts[0].trim();
                    let v = parts[1].trim();
                    match k {
                        "port" | "udp_port" => {
                            if let Ok(p) = v.parse::<u16>() {
                                cfg.udp_port = p;
                            }
                        }
                        "web_port" => {
                            if let Ok(p) = v.parse::<u16>() {
                                cfg.web_port = p;
                            }
                        }
                        "autostart" => {
                            cfg.autostart = v.eq_ignore_ascii_case("true") || v == "1";
                        }
                        "enable_web_ui" => {
                            cfg.enable_web_ui = v.eq_ignore_ascii_case("true") || v == "1";
                        }
                        _ => {}
                    }
                }
            }
        } else {
            let _ = cfg.save();
        }

        cfg
    }

    pub fn save(&self) -> Result<(), String> {
        let path = get_config_file_path();
        if let Some(parent) = path.parent() {
            let _ = fs::create_dir_all(parent);
        }
        let content = format!(
            "# Wake-on-LAN 電腦端電源控制設定\nudp_port={}\nweb_port={}\nautostart={}\nenable_web_ui={}\n",
            self.udp_port, self.web_port, self.autostart, self.enable_web_ui
        );
        fs::write(&path, content).map_err(|e| format!("無法儲存設定檔案: {}", e))
    }
}

fn get_app_dir() -> PathBuf {
    #[cfg(windows)]
    {
        if let Ok(local_app_data) = env::var("LOCALAPPDATA") {
            let path = Path::new(&local_app_data).join(APP_NAME);
            let _ = fs::create_dir_all(&path);
            return path;
        }
    }
    #[cfg(not(windows))]
    {
        if let Ok(home) = env::var("HOME") {
            let path = Path::new(&home).join(".config").join(APP_NAME);
            let _ = fs::create_dir_all(&path);
            return path;
        }
    }
    env::current_dir().unwrap_or_else(|_| PathBuf::from("."))
}

fn get_config_file_path() -> PathBuf {
    if let Ok(exe_path) = env::current_exe() {
        if let Some(parent) = exe_path.parent() {
            let candidate = parent.join(CONFIG_FILE_NAME);
            if candidate.exists()
                || OpenOptions::new()
                    .create(true)
                    .write(true)
                    .open(&candidate)
                    .is_ok()
            {
                return candidate;
            }
        }
    }
    get_app_dir().join(CONFIG_FILE_NAME)
}

fn get_log_file_path() -> PathBuf {
    if let Ok(exe_path) = env::current_exe() {
        if let Some(parent) = exe_path.parent() {
            let candidate = parent.join(format!("{}.log", APP_NAME));
            if OpenOptions::new()
                .create(true)
                .append(true)
                .open(&candidate)
                .is_ok()
            {
                return candidate;
            }
        }
    }
    get_app_dir().join(format!("{}.log", APP_NAME))
}

fn get_installed_exe_path() -> PathBuf {
    #[cfg(windows)]
    {
        get_app_dir().join(format!("{}.exe", APP_NAME))
    }
    #[cfg(not(windows))]
    {
        get_app_dir().join(APP_NAME)
    }
}

fn get_local_timestamp() -> String {
    #[cfg(windows)]
    {
        let st = win_api::get_local_time();
        format!(
            "{:04}-{:02}-{:02} {:02}:{:02}:{:02}",
            st.w_year, st.w_month, st.w_day, st.w_hour, st.w_minute, st.w_second
        )
    }
    #[cfg(not(windows))]
    {
        if let Ok(output) = Command::new("date").arg("+%Y-%m-%d %H:%M:%S").output() {
            let s = String::from_utf8_lossy(&output.stdout).trim().to_string();
            if !s.is_empty() {
                return s;
            }
        }
        "未知時間".to_string()
    }
}

fn get_local_ip() -> String {
    if let Ok(socket) = UdpSocket::bind("0.0.0.0:0") {
        if socket.connect("8.8.8.8:80").is_ok() {
            if let Ok(addr) = socket.local_addr() {
                return addr.ip().to_string();
            }
        }
    }
    "127.0.0.1".to_string()
}

fn log_record(category: &str, message: &str) {
    let timestamp = get_local_timestamp();
    let log_line = format!("[{}] [{}] {}", timestamp, category, message);

    println!("{}", log_line);

    // 寫入本地日誌檔案
    let log_path = get_log_file_path();
    if let Ok(mut file) = OpenOptions::new().create(true).append(true).open(&log_path) {
        let _ = writeln!(file, "{}", log_line);
    }

    // 保留最近 100 筆於記憶體中供 Web UI 即時查詢
    if let Ok(mut logs) = RECENT_LOGS.lock() {
        logs.push(log_line);
        if logs.len() > 100 {
            logs.remove(0);
        }
    }
}

// 跨平台開機自動啟動註冊與解除
fn is_autostart_registered() -> bool {
    #[cfg(windows)]
    {
        let output = Command::new("reg")
            .args(["query", REG_RUN_KEY, "/v", REG_RUN_NAME])
            .output();
        if let Ok(out) = output {
            return out.status.success();
        }
    }
    #[cfg(not(windows))]
    {
        // 檢查 systemd 或 autostart .desktop
        if let Ok(home) = env::var("HOME") {
            let desktop_path = Path::new(&home).join(".config/autostart/pc_onoff.desktop");
            let service_path = Path::new(&home).join(".config/systemd/user/pc_onoff.service");
            return desktop_path.exists() || service_path.exists();
        }
    }
    false
}

fn sync_autostart(enable: bool) -> Result<(), String> {
    #[cfg(windows)]
    {
        if enable {
            let current_exe =
                env::current_exe().map_err(|e| format!("無法取得目前執行檔路徑: {}", e))?;
            let target_exe = get_installed_exe_path();

            if current_exe != target_exe {
                if let Some(parent) = target_exe.parent() {
                    let _ = fs::create_dir_all(parent);
                }
                let _ = fs::copy(&current_exe, &target_exe);
            }

            let reg_value = format!("\"{}\" --background", target_exe.to_string_lossy());
            let status = Command::new("reg")
                .args(["add", REG_RUN_KEY, "/v", REG_RUN_NAME, "/t", "REG_SZ", "/d", &reg_value, "/f"])
                .status()
                .map_err(|e| format!("執行 reg 指令失敗: {}", e))?;

            if !status.success() {
                return Err("寫入 Windows 登錄檔失敗".to_string());
            }
            log_record("系統設定", "開機自動啟動已啟用 (Windows HKCU Run)");
        } else {
            let _ = Command::new("reg")
                .args(["delete", REG_RUN_KEY, "/v", REG_RUN_NAME, "/f"])
                .status();
            log_record("系統設定", "開機自動啟動已停用 (自 Windows 登錄項移除)");
        }
        Ok(())
    }
    #[cfg(not(windows))]
    {
        if let Ok(home) = env::var("HOME") {
            let autostart_dir = Path::new(&home).join(".config/autostart");
            let desktop_file = autostart_dir.join("pc_onoff.desktop");

            let systemd_dir = Path::new(&home).join(".config/systemd/user");
            let service_file = systemd_dir.join("pc_onoff.service");

            if enable {
                let current_exe = env::current_exe().map_err(|e| format!("無法取得執行檔路徑: {}", e))?;
                let exe_str = current_exe.to_string_lossy();

                // 1. 建立 XDG Autostart 檔案 (相容各大 Linux 桌面環境 GNOME/KDE/XFCE 等)
                let _ = fs::create_dir_all(&autostart_dir);
                let desktop_entry = format!(
                    "[Desktop Entry]\nType=Application\nName=Wake-on-LAN PC Control\nExec={}\nTerminal=false\nX-GNOME-Autostart-enabled=true\n",
                    exe_str
                );
                let _ = fs::write(&desktop_file, desktop_entry);

                // 2. 建立 systemd user service (相容無桌面或伺服器環境)
                let _ = fs::create_dir_all(&systemd_dir);
                let service_entry = format!(
                    "[Unit]\nDescription=Wake-on-LAN PC Control Service\nAfter=network.target\n\n[Service]\nType=simple\nExecStart={}\nRestart=always\n\n[Install]\nWantedBy=default.target\n",
                    exe_str
                );
                let _ = fs::write(&service_file, service_entry);
                let _ = Command::new("systemctl").args(["--user", "daemon-reload"]).status();
                let _ = Command::new("systemctl").args(["--user", "enable", "pc_onoff.service"]).status();

                log_record("系統設定", "已在 Linux 建立開機啟動設定 (systemd user service & XDG autostart)");
            } else {
                if desktop_file.exists() {
                    let _ = fs::remove_file(&desktop_file);
                }
                if service_file.exists() {
                    let _ = Command::new("systemctl").args(["--user", "disable", "--now", "pc_onoff.service"]).status();
                    let _ = fs::remove_file(&service_file);
                    let _ = Command::new("systemctl").args(["--user", "daemon-reload"]).status();
                }
                log_record("系統設定", "已解除 Linux 開機啟動設定");
            }
            Ok(())
        } else {
            Err("找不到 HOME 環境變數".to_string())
        }
    }
}

pub fn parse_action(cmd: &str) -> &str {
    let trimmed = cmd.trim();
    if let Some(idx) = trimmed.find(|c| c == ':' || c == ',') {
        trimmed[..idx].trim()
    } else {
        trimmed
    }
}

// 跨平台系統電源指令執行
fn execute_command(src: Option<SocketAddr>, raw_cmd: &str) {
    let action = parse_action(raw_cmd);
    let src_str = match src {
        Some(addr) => addr.to_string(),
        None => "Web UI / 本機手動測試".to_string(),
    };

    match action {
        "shutdown" => {
            log_record(
                "執行指令",
                &format!("來源: {} | 指令: 關機 (shutdown)", src_str),
            );
            #[cfg(windows)]
            {
                let _ = Command::new("shutdown").args(["/s", "/t", "0"]).status();
            }
            #[cfg(not(windows))]
            {
                let _ = Command::new("systemctl").arg("poweroff").status()
                    .or_else(|_| Command::new("shutdown").args(["-h", "now"]).status());
            }
        }
        "reboot" => {
            log_record(
                "執行指令",
                &format!("來源: {} | 指令: 重啟 (reboot)", src_str),
            );
            #[cfg(windows)]
            {
                let _ = Command::new("shutdown").args(["/r", "/t", "0"]).status();
            }
            #[cfg(not(windows))]
            {
                let _ = Command::new("systemctl").arg("reboot").status()
                    .or_else(|_| Command::new("reboot").status());
            }
        }
        "sleep" => {
            log_record(
                "執行指令",
                &format!("來源: {} | 指令: 睡眠 (sleep)", src_str),
            );
            #[cfg(windows)]
            {
                let _ = Command::new("cmd").args(["/c", "rundll32.exe user32.dll,LockWorkStation"]).status();
                let _ = Command::new("cmd").args(["/c", "powercfg.exe /hibernate off"]).status();
                let _ = Command::new("cmd").args(["/c", "rundll32.exe powrprof.dll,SetSuspendState 0,1,0"]).status();
                let _ = Command::new("cmd").args(["/c", "powercfg.exe /hibernate on"]).status();
            }
            #[cfg(not(windows))]
            {
                let _ = Command::new("systemctl").arg("suspend").status()
                    .or_else(|_| Command::new("pm-suspend").status());
            }
        }
        "hibernate" => {
            log_record(
                "執行指令",
                &format!("來源: {} | 指令: 休眠 (hibernate)", src_str),
            );
            #[cfg(windows)]
            {
                let _ = Command::new("cmd").args(["/c", "rundll32.exe user32.dll,LockWorkStation"]).status();
                let _ = Command::new("cmd").args(["/c", "rundll32.exe powrprof.dll,SetSuspendState 1,1,0"]).status();
            }
            #[cfg(not(windows))]
            {
                let _ = Command::new("systemctl").arg("hibernate").status()
                    .or_else(|_| Command::new("pm-hibernate").status());
            }
        }
        "lock" => {
            log_record(
                "執行指令",
                &format!("來源: {} | 指令: 鎖定螢幕 (lock)", src_str),
            );
            #[cfg(windows)]
            {
                let _ = Command::new("cmd").args(["/c", "rundll32.exe user32.dll,LockWorkStation"]).status();
            }
            #[cfg(not(windows))]
            {
                let _ = Command::new("loginctl").args(["lock-session"]).status()
                    .or_else(|_| Command::new("xdg-screensaver").arg("lock").status());
            }
        }
        unknown => {
            log_record(
                "警告",
                &format!("來源: {} | 收到未知指令: '{}' (不進行任何動作)", src_str, unknown),
            );
        }
    }
}

// 背景 UDP 監聽服務
fn start_udp_listener(port: u16) {
    ACTIVE_PORT.store(port, Ordering::SeqCst);
    SERVER_RUNNING.store(true, Ordering::SeqCst);

    thread::spawn(move || {
        let bind_addr = format!("{}:{}", LISTEN_IP, port);
        let socket = match UdpSocket::bind(&bind_addr) {
            Ok(s) => {
                log_record(
                    "服務啟動",
                    &format!("成功綁定 UDP {}，正在監聽遠端開關機控制指令...", bind_addr),
                );
                s
            }
            Err(e) => {
                log_record(
                    "啟動錯誤",
                    &format!("綁定連接埠 {} 失敗: {} (可能已被佔用)", bind_addr, e),
                );
                SERVER_RUNNING.store(false, Ordering::SeqCst);
                return;
            }
        };

        let _ = socket.set_read_timeout(Some(Duration::from_millis(500)));
        let mut buf = [0u8; BUFFER_SIZE];

        while SERVER_RUNNING.load(Ordering::SeqCst) {
            match socket.recv_from(&mut buf) {
                Ok((size, src)) => {
                    let raw = String::from_utf8_lossy(&buf[..size]);
                    let command = raw.trim().to_lowercase();
                    log_record("網路接收", &format!("來自 {} 長度: {} 位元組，指令: '{}'", src, size, command));
                    execute_command(Some(src), &command);
                }
                Err(ref e) if e.kind() == std::io::ErrorKind::TimedOut || e.kind() == std::io::ErrorKind::WouldBlock => {
                    // 超時循環，繼續檢查 SERVER_RUNNING
                }
                Err(e) => {
                    log_record("網路錯誤", &format!("接收 UDP 資料發生異常: {}", e));
                }
            }
        }
        log_record("服務停止", &format!("已停止監聽連接埠 {}", port));
    });
}

// 跨平台內建 Web UI 控制台
const HTML_DASHBOARD: &str = r#"<!DOCTYPE html>
<html lang="zh-TW">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Wake-on-LAN 電腦端電源控制設定</title>
    <style>
        :root {
            --bg: #f8fafc;
            --card-bg: #ffffff;
            --text-main: #0f172a;
            --text-sub: #475569;
            --primary: #2563eb;
            --primary-hover: #1d4ed8;
            --success: #16a34a;
            --border: #e2e8f0;
            --log-bg: #0f172a;
            --log-text: #38bdf8;
        }
        @media (prefers-color-scheme: dark) {
            :root {
                --bg: #090d16;
                --card-bg: #131c2e;
                --text-main: #f8fafc;
                --text-sub: #94a3b8;
                --primary: #3b82f6;
                --primary-hover: #2563eb;
                --border: #1e293b;
            }
        }
        * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Microsoft JhengHei", sans-serif; }
        body { background-color: var(--bg); color: var(--text-main); padding: 24px 16px; display: flex; justify-content: center; }
        .container { max-width: 680px; width: 100%; display: flex; flex-direction: column; gap: 20px; }
        .header { display: flex; justify-content: space-between; align-items: center; border-bottom: 1px solid var(--border); padding-bottom: 16px; }
        .title { font-size: 20px; font-weight: 700; }
        .badge { background: #dcfce7; color: #15803d; font-size: 13px; font-weight: 600; padding: 4px 10px; border-radius: 9999px; }
        .card { background: var(--card-bg); border: 1px solid var(--border); border-radius: 12px; padding: 20px; box-shadow: 0 1px 3px rgba(0,0,0,0.05); }
        .card-title { font-size: 16px; font-weight: 600; margin-bottom: 14px; color: var(--text-main); }
        .form-row { display: flex; justify-content: space-between; align-items: center; margin-bottom: 14px; }
        .label { font-size: 14px; color: var(--text-sub); }
        .input-text { width: 120px; padding: 8px 12px; border: 1px solid var(--border); border-radius: 6px; font-size: 14px; background: transparent; color: var(--text-main); }
        .switch { position: relative; display: inline-block; width: 44px; height: 24px; }
        .switch input { opacity: 0; width: 0; height: 0; }
        .slider { position: absolute; cursor: pointer; top: 0; left: 0; right: 0; bottom: 0; background-color: #cbd5e1; transition: .3s; border-radius: 24px; }
        .slider:before { position: absolute; content: ""; height: 18px; width: 18px; left: 3px; bottom: 3px; background-color: white; transition: .3s; border-radius: 50%; }
        input:checked + .slider { background-color: var(--primary); }
        input:checked + .slider:before { transform: translateX(20px); }
        .btn { padding: 9px 18px; background: var(--primary); color: white; border: none; border-radius: 6px; cursor: pointer; font-size: 14px; font-weight: 500; transition: background .2s; }
        .btn:hover { background: var(--primary-hover); }
        .btn-outline { background: transparent; border: 1px solid var(--border); color: var(--text-main); }
        .btn-outline:hover { background: rgba(125,125,125,0.08); }
        .btn-group { display: flex; gap: 10px; flex-wrap: wrap; margin-top: 10px; }
        .log-box { background: var(--log-bg); color: var(--log-text); border-radius: 8px; padding: 12px; height: 180px; overflow-y: auto; font-family: ui-monospace, SFMono-Regular, Consolas, monospace; font-size: 12px; line-height: 1.5; white-space: pre-wrap; word-break: break-all; }
        .alert { padding: 10px 14px; border-radius: 6px; font-size: 13px; margin-top: 10px; display: none; }
        .alert-success { background: #dcfce7; color: #166534; }
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <div>
                <div class="title">Wake-on-LAN 電腦端設定</div>
                <div style="font-size: 13px; color: var(--text-sub); margin-top: 4px;">本機區域網路 IP: <strong id="local-ip">載入中...</strong></div>
            </div>
            <div class="badge" id="service-status">監聽中</div>
        </div>

        <div class="card">
            <div class="card-title">服務與開機設定</div>
            <div class="form-row">
                <div class="label">UDP 監聽連接埠 (預設 9877):</div>
                <input type="number" id="input-port" class="input-text" value="9877">
            </div>
            <div class="form-row">
                <div class="label">開機自動於背景常駐啟動:</div>
                <label class="switch">
                    <input type="checkbox" id="chk-autostart" checked>
                    <span class="slider"></span>
                </label>
            </div>
            <div style="display: flex; justify-content: flex-end; margin-top: 6px;">
                <button class="btn" onclick="saveSettings()">儲存並立即套用</button>
            </div>
            <div id="save-alert" class="alert alert-success">設定已成功儲存並同步至系統！</div>
        </div>

        <div class="card">
            <div class="card-title">本機控制測試</div>
            <div style="font-size: 13px; color: var(--text-sub); margin-bottom: 12px;">可由瀏覽器測試電源呼叫是否正常運作：</div>
            <div class="btn-group">
                <button class="btn btn-outline" onclick="triggerAction('lock')">測試鎖定螢幕</button>
                <button class="btn btn-outline" onclick="triggerAction('sleep')">測試進入睡眠</button>
                <button class="btn btn-outline" onclick="triggerAction('reboot')">測試重新開機</button>
                <button class="btn btn-outline" onclick="triggerAction('shutdown')">測試關機</button>
            </div>
        </div>

        <div class="card">
            <div class="card-title" style="display: flex; justify-content: space-between; align-items: center;">
                <span>即時日誌紀錄</span>
                <button class="btn btn-outline" style="padding: 4px 10px; font-size: 12px;" onclick="loadLogs()">手動重新載入</button>
            </div>
            <div class="log-box" id="log-content">正在連線取得日誌...</div>
        </div>
    </div>

    <script>
        async function loadStatus() {
            try {
                const res = await fetch('/api/status');
                const data = await res.json();
                document.getElementById('local-ip').innerText = data.ip;
                document.getElementById('input-port').value = data.port;
                document.getElementById('chk-autostart').checked = data.autostart;
                document.getElementById('service-status').innerText = data.running ? '監聽中: UDP ' + data.port : '已停止';
            } catch(e) {
                console.error(e);
            }
        }

        async function loadLogs() {
            try {
                const res = await fetch('/api/logs');
                const data = await res.json();
                const logBox = document.getElementById('log-content');
                if (data.logs && data.logs.length > 0) {
                    logBox.innerText = data.logs.join('\n');
                    logBox.scrollTop = logBox.scrollHeight;
                } else {
                    logBox.innerText = '(尚無日誌紀錄)';
                }
            } catch(e) {
                console.error(e);
            }
        }

        async function saveSettings() {
            const port = parseInt(document.getElementById('input-port').value) || 9877;
            const autostart = document.getElementById('chk-autostart').checked;
            try {
                const res = await fetch('/api/save', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ port, autostart })
                });
                if (res.ok) {
                    const alert = document.getElementById('save-alert');
                    alert.style.display = 'block';
                    setTimeout(() => { alert.style.display = 'none'; }, 3000);
                    loadStatus();
                    loadLogs();
                }
            } catch(e) {
                alert('儲存失敗: ' + e);
            }
        }

        async function triggerAction(action) {
            if (action === 'shutdown' || action === 'reboot') {
                if (!confirm('確定要在本機執行 ' + action + ' 指令嗎？')) return;
            }
            try {
                await fetch('/api/action', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ action })
                });
                loadLogs();
            } catch(e) {
                alert('執行異常: ' + e);
            }
        }

        loadStatus();
        loadLogs();
        setInterval(loadLogs, 3000);
    </script>
</body>
</html>
"#;

fn handle_http_client(mut stream: TcpStream) {
    let mut buf = [0u8; 4096];
    let bytes_read = match stream.read(&mut buf) {
        Ok(n) if n > 0 => n,
        _ => return,
    };

    let req_str = String::from_utf8_lossy(&buf[..bytes_read]);
    let first_line = req_str.lines().next().unwrap_or("");
    let parts: Vec<&str> = first_line.split_whitespace().collect();
    if parts.len() < 2 {
        return;
    }

    let method = parts[0];
    let path = parts[1];

    if method == "GET" && (path == "/" || path == "/index.html") {
        let response = format!(
            "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{}",
            HTML_DASHBOARD.len(),
            HTML_DASHBOARD
        );
        let _ = stream.write_all(response.as_bytes());
        return;
    }

    if method == "GET" && path == "/api/status" {
        let port = ACTIVE_PORT.load(Ordering::SeqCst);
        let running = SERVER_RUNNING.load(Ordering::SeqCst);
        let ip = get_local_ip();
        let autostart = is_autostart_registered();
        let os = if cfg!(windows) { "windows" } else { "linux" };

        let json = format!(
            "{{\"running\":{},\"port\":{},\"autostart\":{},\"ip\":\"{}\",\"os\":\"{}\"}}",
            running, port, autostart, ip, os
        );
        let response = format!(
            "HTTP/1.1 200 OK\r\nContent-Type: application/json; charset=utf-8\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{}",
            json.len(),
            json
        );
        let _ = stream.write_all(response.as_bytes());
        return;
    }

    if method == "GET" && path == "/api/logs" {
        let logs_json = if let Ok(logs) = RECENT_LOGS.lock() {
            let escaped: Vec<String> = logs
                .iter()
                .map(|l| format!("\"{}\"", l.replace('\\', "\\\\").replace('"', "\\\"")))
                .collect();
            format!("[{}]", escaped.join(","))
        } else {
            "[]".to_string()
        };

        let json = format!("{{\"logs\":{}}}", logs_json);
        let response = format!(
            "HTTP/1.1 200 OK\r\nContent-Type: application/json; charset=utf-8\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{}",
            json.len(),
            json
        );
        let _ = stream.write_all(response.as_bytes());
        return;
    }

    if method == "POST" && path == "/api/save" {
        // 解析 body
        if let Some(body_start) = req_str.find("\r\n\r\n") {
            let body = &req_str[body_start + 4..];
            let mut new_port = ACTIVE_PORT.load(Ordering::SeqCst);
            let mut new_autostart = is_autostart_registered();

            if let Some(p_idx) = body.find("\"port\":") {
                let after = &body[p_idx + 7..];
                let num_str: String = after.chars().take_while(|c| c.is_ascii_digit()).collect();
                if let Ok(p) = num_str.parse::<u16>() {
                    new_port = p;
                }
            }
            if let Some(a_idx) = body.find("\"autostart\":") {
                let after = &body[a_idx + 12..].trim_start();
                if after.starts_with("true") {
                    new_autostart = true;
                } else if after.starts_with("false") {
                    new_autostart = false;
                }
            }

            let mut cfg = AppConfig::load();
            let port_changed = cfg.udp_port != new_port;
            cfg.udp_port = new_port;
            cfg.autostart = new_autostart;
            let _ = cfg.save();
            let _ = sync_autostart(new_autostart);

            if port_changed {
                SERVER_RUNNING.store(false, Ordering::SeqCst);
                thread::sleep(Duration::from_millis(600));
                start_udp_listener(new_port);
            }

            log_record(
                "設定更新",
                &format!("Web UI 儲存設定: UDP連接埠={}, 開機自動啟動={}", new_port, new_autostart),
            );

            let res_body = "{\"success\":true}";
            let response = format!(
                "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{}",
                res_body.len(),
                res_body
            );
            let _ = stream.write_all(response.as_bytes());
            return;
        }
    }

    if method == "POST" && path == "/api/action" {
        if let Some(body_start) = req_str.find("\r\n\r\n") {
            let body = &req_str[body_start + 4..];
            if let Some(act_idx) = body.find("\"action\":\"") {
                let after = &body[act_idx + 10..];
                if let Some(end_idx) = after.find('"') {
                    let action = &after[..end_idx];
                    log_record("手動控制", &format!("透過 Web UI 觸發指令: '{}'", action));
                    execute_command(None, action);
                }
            }
        }
        let res_body = "{\"success\":true}";
        let response = format!(
            "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{}",
            res_body.len(),
            res_body
        );
        let _ = stream.write_all(response.as_bytes());
        return;
    }

    // 404
    let not_found = "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n";
    let _ = stream.write_all(not_found.as_bytes());
}

fn start_web_ui_server(port: u16) {
    thread::spawn(move || {
        let bind_addr = format!("127.0.0.1:{}", port);
        let listener = match TcpListener::bind(&bind_addr) {
            Ok(l) => {
                log_record("控制台", &format!("本地 Web UI 設定介面已啟動: http://{}", bind_addr));
                l
            }
            Err(e) => {
                log_record("控制台錯誤", &format!("綁定 Web UI 連接埠 {} 失敗: {}", bind_addr, e));
                return;
            }
        };

        for stream in listener.incoming() {
            if let Ok(s) = stream {
                thread::spawn(|| {
                    handle_http_client(s);
                });
            }
        }
    });
}

fn open_browser(url: &str) {
    #[cfg(windows)]
    {
        let _ = Command::new("cmd").args(["/c", "start", url]).spawn();
    }
    #[cfg(target_os = "macos")]
    {
        let _ = Command::new("open").arg(url).spawn();
    }
    #[cfg(target_os = "linux")]
    {
        let _ = Command::new("xdg-open").arg(url).spawn();
    }
}

fn main() {
    let args: Vec<String> = env::args().collect();

    // 1. 若為 Windows 平台且處於背景/托盤模式，隱藏主終端視窗
    #[cfg(windows)]
    {
        if args.iter().any(|a| a == "--background" || a == "-b") {
            win_api::hide_console();
        }
    }

    // 2. 命令列常規參數
    if args.iter().any(|a| a == "--install" || a == "-i") {
        let _ = sync_autostart(true);
        println!("開機自動啟動設定成功。");
        return;
    }

    if args.iter().any(|a| a == "--uninstall" || a == "-u") {
        let _ = sync_autostart(false);
        println!("開機自動啟動已解除。");
        return;
    }

    if args.iter().any(|a| a == "--help" || a == "-h") {
        println!("Wake-on-LAN 電腦端電源控制公用程式 (支援 Windows 與 Linux 跨平台)");
        println!();
        println!("用法:");
        println!("  pc_onoff                 預設啟動：自動在背景常駐並啟用開機自動啟動");
        println!("  pc_onoff --ui            啟動並直接開啟瀏覽器設定控制台 (http://127.0.0.1:9878)");
        println!("  pc_onoff --background    於背景靜默模式常駐");
        println!("  pc_onoff --install       註冊開機自動啟動");
        println!("  pc_onoff --uninstall     解除開機自動啟動");
        return;
    }

    // 3. 載入設定（若無設定檔自動建立，autostart 預設即為 true）
    let cfg = AppConfig::load();

    // 4. 關鍵要求：預設直接掛在後台，自動啟用開機啟動，無需任何手動選單按鍵 [1]
    if cfg.autostart && !is_autostart_registered() {
        let _ = sync_autostart(true);
    }

    // 5. 啟動背景 UDP 監聽服務
    start_udp_listener(cfg.udp_port);

    // 6. 啟動跨平台 Web 控制台 (預設 127.0.0.1:9878)
    if cfg.enable_web_ui {
        start_web_ui_server(cfg.web_port);
    }

    // 7. 若帶有 --ui 或首次執行，自動為使用者開啟瀏覽器設定頁面
    if args.iter().any(|a| a == "--ui" || a == "-s") {
        let ui_url = format!("http://127.0.0.1:{}", cfg.web_port);
        open_browser(&ui_url);
    }

    log_record("常駐執行", "守護行程已在背景持續常駐監聽中。隨時可至瀏覽器訪問 http://127.0.0.1:9878 進行設定與查看日誌。");

    // 8. 主行程保持常駐
    loop {
        thread::sleep(Duration::from_secs(3600));
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_parse_action_plain() {
        assert_eq!(parse_action("shutdown"), "shutdown");
        assert_eq!(parse_action("reboot"), "reboot");
        assert_eq!(parse_action("sleep"), "sleep");
        assert_eq!(parse_action("hibernate"), "hibernate");
        assert_eq!(parse_action("lock"), "lock");
    }

    #[test]
    fn test_parse_action_with_delimiter() {
        assert_eq!(parse_action("shutdown:192.168.1.100"), "shutdown");
        assert_eq!(parse_action("sleep,192.168.1.100"), "sleep");
        assert_eq!(parse_action("reboot:10.0.0.5"), "reboot");
        assert_eq!(parse_action("hibernate,127.0.0.1"), "hibernate");
        assert_eq!(parse_action("lock:local"), "lock");
    }

    #[test]
    fn test_parse_action_with_spaces() {
        assert_eq!(parse_action("  shutdown  "), "shutdown");
        assert_eq!(parse_action(" sleep : 192.168.1.50 "), "sleep");
        assert_eq!(parse_action(" reboot , 192.168.1.50 "), "reboot");
    }

    #[test]
    fn test_default_config() {
        let cfg = AppConfig::default();
        assert_eq!(cfg.udp_port, 9877);
        assert_eq!(cfg.web_port, 9878);
        assert!(cfg.autostart); // 預設即為開機自動啟動
        assert!(cfg.enable_web_ui);
    }
}
