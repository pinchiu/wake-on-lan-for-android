#![cfg_attr(not(test), windows_subsystem = "windows")]

use std::env;
use std::fs::{self, OpenOptions};
use std::io::Write;
use std::net::{SocketAddr, UdpSocket};
use std::path::{Path, PathBuf};
use std::process::Command;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Mutex;
use std::thread;
use std::time::Duration;

#[cfg(windows)]
use std::os::windows::process::CommandExt;

const LISTEN_IP: &str = "0.0.0.0";
const DEFAULT_UDP_PORT: u16 = 9877;
const BUFFER_SIZE: usize = 1024;
const APP_NAME: &str = "pc_onoff";
const CONFIG_FILE_NAME: &str = "config.ini";

#[cfg(windows)]
const CREATE_NO_WINDOW: u32 = 0x08000000;
#[cfg(windows)]
const REG_RUN_KEY: &str = r"HKCU\Software\Microsoft\Windows\CurrentVersion\Run";
#[cfg(windows)]
const REG_RUN_NAME: &str = "WakeOnLanPcOnOff";
#[cfg(windows)]
const MUTEX_NAME: &str = "Local\\WakeOnLanPcOnOffMutex";
#[cfg(windows)]
const ERROR_ALREADY_EXISTS: u32 = 183;

#[cfg(windows)]
#[repr(C)]
#[derive(Default, Debug, Clone, Copy)]
pub struct SystemTimeWin {
    pub w_year: u16,
    pub w_month: u16,
    pub w_day_of_week: u16,
    pub w_day: u16,
    pub w_hour: u16,
    pub w_minute: u16,
    pub w_second: u16,
    pub w_milliseconds: u16,
}

#[cfg(windows)]
mod win_api {
    use super::*;

    #[link(name = "user32")]
    #[link(name = "kernel32")]
    #[link(name = "powrprof")]
    unsafe extern "system" {
        pub fn LockWorkStation() -> i32;
        pub fn SetSuspendState(
            b_hibernate: u8,
            b_force: u8,
            b_wakeup_events_disabled: u8,
        ) -> u8;
        pub fn CreateMutexW(
            lp_mutex_attributes: *mut std::ffi::c_void,
            b_initial_owner: i32,
            lp_name: *const u16,
        ) -> *mut std::ffi::c_void;
        pub fn GetLastError() -> u32;
        pub fn CloseHandle(h_object: *mut std::ffi::c_void) -> i32;
        pub fn GetLocalTime(lp_system_time: *mut SystemTimeWin);
        pub fn AttachConsole(dw_process_id: u32) -> i32;
    }

    pub fn to_wide_chars(s: &str) -> Vec<u16> {
        s.encode_utf16().chain(std::iter::once(0)).collect()
    }
}

pub struct SingleInstanceGuard {
    #[cfg(windows)]
    handle: *mut std::ffi::c_void,
}

impl SingleInstanceGuard {
    pub fn try_acquire() -> Result<Self, ()> {
        #[cfg(windows)]
        {
            let wide_name = win_api::to_wide_chars(MUTEX_NAME);
            let handle = unsafe {
                win_api::CreateMutexW(std::ptr::null_mut(), 0, wide_name.as_ptr())
            };
            if handle.is_null() {
                return Err(());
            }
            let err = unsafe { win_api::GetLastError() };
            if err == ERROR_ALREADY_EXISTS {
                unsafe {
                    win_api::CloseHandle(handle);
                }
                return Err(());
            }
            Ok(Self { handle })
        }
        #[cfg(not(windows))]
        {
            Ok(Self {})
        }
    }
}

impl Drop for SingleInstanceGuard {
    fn drop(&mut self) {
        #[cfg(windows)]
        {
            if !self.handle.is_null() {
                unsafe {
                    win_api::CloseHandle(self.handle);
                }
            }
        }
    }
}

#[derive(Debug, Clone)]
pub struct AppConfig {
    pub udp_port: u16,
    pub autostart: bool,
}

impl Default for AppConfig {
    fn default() -> Self {
        Self {
            udp_port: DEFAULT_UDP_PORT,
            autostart: true,
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
                        "autostart" => {
                            cfg.autostart = v.eq_ignore_ascii_case("true") || v == "1";
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
            "# Wake-on-LAN 電腦端電源控制設定\nudp_port={}\nautostart={}\n",
            self.udp_port, self.autostart
        );
        fs::write(&path, content).map_err(|e| format!("無法儲存設定檔: {}", e))
    }
}

static SERVER_RUNNING: AtomicBool = AtomicBool::new(true);
static RECENT_LOGS: Mutex<Vec<String>> = Mutex::new(Vec::new());

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

fn get_local_timestamp() -> String {
    #[cfg(windows)]
    {
        let mut st = SystemTimeWin::default();
        unsafe {
            win_api::GetLocalTime(&mut st);
        }
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

pub fn log_record(category: &str, message: &str) {
    let timestamp = get_local_timestamp();
    let log_line = format!("[{}] [{}] {}", timestamp, category, message);

    println!("{}", log_line);

    let log_path = get_log_file_path();
    if let Ok(mut file) = OpenOptions::new().create(true).append(true).open(&log_path) {
        let _ = writeln!(file, "{}", log_line);
    }

    if let Ok(mut logs) = RECENT_LOGS.lock() {
        logs.push(log_line);
        if logs.len() > 100 {
            logs.remove(0);
        }
    }
}

pub fn is_autostart_registered() -> bool {
    #[cfg(windows)]
    {
        let output = Command::new("reg")
            .args(["query", REG_RUN_KEY, "/v", REG_RUN_NAME])
            .creation_flags(CREATE_NO_WINDOW)
            .output();
        if let Ok(out) = output {
            return out.status.success();
        }
    }
    #[cfg(not(windows))]
    {
        if let Ok(home) = env::var("HOME") {
            let desktop_path = Path::new(&home).join(".config/autostart/pc_onoff.desktop");
            let service_path = Path::new(&home).join(".config/systemd/user/pc_onoff.service");
            return desktop_path.exists() || service_path.exists();
        }
    }
    false
}

pub fn sync_autostart(enable: bool) -> Result<(), String> {
    #[cfg(windows)]
    {
        if enable {
            let current_exe =
                env::current_exe().map_err(|e| format!("無法取得目前執行檔路徑: {}", e))?;
            let reg_value = format!("\"{}\"", current_exe.to_string_lossy());

            let status = Command::new("reg")
                .args(["add", REG_RUN_KEY, "/v", REG_RUN_NAME, "/t", "REG_SZ", "/d", &reg_value, "/f"])
                .creation_flags(CREATE_NO_WINDOW)
                .status()
                .map_err(|e| format!("執行 reg 指令失敗: {}", e))?;

            if !status.success() {
                return Err("寫入 Windows 開機啟動登錄項失敗".to_string());
            }
            log_record("設定", &format!("開機自動啟動已註冊至 HKCU Run: {}", reg_value));
        } else {
            let _ = Command::new("reg")
                .args(["delete", REG_RUN_KEY, "/v", REG_RUN_NAME, "/f"])
                .creation_flags(CREATE_NO_WINDOW)
                .status();
            log_record("設定", "開機自動啟動已自 HKCU Run 移除");
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

                let _ = fs::create_dir_all(&autostart_dir);
                let desktop_entry = format!(
                    "[Desktop Entry]\nType=Application\nName=Wake-on-LAN PC Control\nExec={}\nTerminal=false\nX-GNOME-Autostart-enabled=true\n",
                    exe_str
                );
                let _ = fs::write(&desktop_file, desktop_entry);

                let _ = fs::create_dir_all(&systemd_dir);
                let service_entry = format!(
                    "[Unit]\nDescription=Wake-on-LAN PC Control Service\nAfter=network.target\n\n[Service]\nType=simple\nExecStart={}\nRestart=always\n\n[Install]\nWantedBy=default.target\n",
                    exe_str
                );
                let _ = fs::write(&service_file, service_entry);
                let _ = Command::new("systemctl").args(["--user", "daemon-reload"]).status();
                let _ = Command::new("systemctl").args(["--user", "enable", "pc_onoff.service"]).status();

                log_record("設定", "已在 Linux 建立開機啟動設定 (systemd user service & XDG autostart)");
            } else {
                if desktop_file.exists() {
                    let _ = fs::remove_file(&desktop_file);
                }
                if service_file.exists() {
                    let _ = Command::new("systemctl").args(["--user", "disable", "--now", "pc_onoff.service"]).status();
                    let _ = fs::remove_file(&service_file);
                    let _ = Command::new("systemctl").args(["--user", "daemon-reload"]).status();
                }
                log_record("設定", "已解除 Linux 開機啟動設定");
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

pub fn execute_command(src: Option<SocketAddr>, raw_cmd: &str) {
    let action = parse_action(raw_cmd).to_lowercase();
    let src_str = match src {
        Some(addr) => addr.to_string(),
        None => "本機指令".to_string(),
    };

    match action.as_str() {
        "shutdown" => {
            log_record(
                "執行指令",
                &format!("來源: {} | 指令: 關機 (shutdown)", src_str),
            );
            #[cfg(windows)]
            {
                let status = Command::new("shutdown")
                    .args(["/s", "/t", "0"])
                    .creation_flags(CREATE_NO_WINDOW)
                    .status();
                log_record("指令結果", &format!("關機執行狀態: {:?}", status));
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
                let status = Command::new("shutdown")
                    .args(["/r", "/t", "0"])
                    .creation_flags(CREATE_NO_WINDOW)
                    .status();
                log_record("指令結果", &format!("重啟執行狀態: {:?}", status));
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
                &format!("來源: {} | 指令: 睡眠 (sleep / S3 Standby)", src_str),
            );
            #[cfg(windows)]
            {
                unsafe {
                    win_api::LockWorkStation();
                }
                // 調用 powrprof.dll 原生 SetSuspendState(0, 0, 0)
                // 參數 1: 0 (b_hibernate = FALSE，進入睡眠而非休眠)
                // 參數 2: 0 (b_force = FALSE)
                // 參數 3: 0 (b_wakeup_events_disabled = FALSE，保留 Wake-on-LAN 喚醒能力)
                let ret = unsafe { win_api::SetSuspendState(0, 0, 0) };
                log_record("指令結果", &format!("睡眠 SetSuspendState 回傳值: {}", ret));
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
                &format!("來源: {} | 指令: 休眠 (hibernate / S4)", src_str),
            );
            #[cfg(windows)]
            {
                unsafe {
                    win_api::LockWorkStation();
                }
                let status = Command::new("shutdown")
                    .args(["/h"])
                    .creation_flags(CREATE_NO_WINDOW)
                    .status();
                if let Ok(s) = status {
                    if !s.success() {
                        let ret = unsafe { win_api::SetSuspendState(1, 0, 0) };
                        log_record("指令結果", &format!("休眠 SetSuspendState 回傳值: {}", ret));
                    } else {
                        log_record("指令結果", "休眠 shutdown /h 成功呼叫");
                    }
                } else {
                    let ret = unsafe { win_api::SetSuspendState(1, 0, 0) };
                    log_record("指令結果", &format!("休眠 SetSuspendState 回傳值: {}", ret));
                }
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
                let ret = unsafe { win_api::LockWorkStation() };
                log_record("指令結果", &format!("鎖定螢幕回傳值: {}", ret));
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

pub fn run_server(port: u16) {
    let bind_addr = format!("{}:{}", LISTEN_IP, port);
    let socket = match UdpSocket::bind(&bind_addr) {
        Ok(s) => {
            log_record(
                "服務啟動",
                &format!("成功綁定 UDP {}，正在常駐監聽遠端控制指令...", bind_addr),
            );
            s
        }
        Err(e) => {
            log_record(
                "啟動錯誤",
                &format!("綁定連接埠 {} 失敗: {} (可能已被佔用)", bind_addr, e),
            );
            return;
        }
    };

    let mut buf = [0u8; BUFFER_SIZE];
    while SERVER_RUNNING.load(Ordering::SeqCst) {
        match socket.recv_from(&mut buf) {
            Ok((size, src)) => {
                let raw = String::from_utf8_lossy(&buf[..size]);
                let command = raw.trim();
                log_record("網路接收", &format!("來自 {} 長度: {} 位元組，指令: '{}'", src, size, command));
                execute_command(Some(src), command);
            }
            Err(e) => {
                log_record("網路錯誤", &format!("接收 UDP 資料發生異常: {}", e));
                thread::sleep(Duration::from_millis(100));
            }
        }
    }
}

fn stop_existing_instances() {
    #[cfg(windows)]
    {
        let current_pid = std::process::id();
        let _ = Command::new("powershell")
            .args([
                "-NoProfile",
                "-Command",
                &format!(
                    "Get-Process -Name 'pc_onoff' -ErrorAction SilentlyContinue | Where-Object {{ $_.Id -ne {} }} | Stop-Process -Force",
                    current_pid
                ),
            ])
            .creation_flags(CREATE_NO_WINDOW)
            .status();
    }
    #[cfg(not(windows))]
    {
        let current_pid = std::process::id();
        let _ = Command::new("pkill")
            .args(["-f", "pc_onoff"])
            .arg("--signal")
            .arg("TERM")
            .status();
        let _ = current_pid;
    }
}

fn main() {
    #[cfg(windows)]
    unsafe {
        // 若從 CMD 或 PowerShell 終端機執行，自動連結至父控制台輸出文字
        win_api::AttachConsole(0xFFFFFFFF);
    }

    let args: Vec<String> = env::args().collect();

    if args.iter().any(|a| a == "--stop") {
        stop_existing_instances();
        println!("已終止背景運行的 pc_onoff 服務。");
        return;
    }

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

    if args.iter().any(|a| a == "--status") {
        let autostart = is_autostart_registered();
        println!("Wake-on-LAN 電腦端服務狀態:");
        println!("  本機 IP:       {}", get_local_ip());
        println!("  預設連接埠:    {}", DEFAULT_UDP_PORT);
        println!("  開機自動啟動:  {}", if autostart { "已啟用" } else { "未啟用" });
        println!("  日誌檔案路徑:  {}", get_log_file_path().display());
        return;
    }

    if args.iter().any(|a| a == "--log") {
        let log_path = get_log_file_path();
        if let Ok(content) = fs::read_to_string(&log_path) {
            let lines: Vec<&str> = content.lines().collect();
            let start = lines.len().saturating_sub(25);
            for line in &lines[start..] {
                println!("{}", line);
            }
        } else {
            println!("尚未產生日誌檔案。");
        }
        return;
    }

    if args.iter().any(|a| a == "--help" || a == "-h") {
        println!("Wake-on-LAN 電腦端遠端控制常駐程式");
        println!();
        println!("用法:");
        println!("  pc_onoff.exe              點擊直接在背景執行並自動設定開機自啟 (預設)");
        println!("  pc_onoff.exe --status     檢視目前運作與開機自啟狀態");
        println!("  pc_onoff.exe --log        檢視最新日誌記錄");
        println!("  pc_onoff.exe --stop       停止正在背景執行的服務");
        println!("  pc_onoff.exe --uninstall  解除開機自動啟動");
        return;
    }

    // 單實例守護：防止重複雙擊導致連接埠衝突
    let _guard = match SingleInstanceGuard::try_acquire() {
        Ok(g) => g,
        Err(_) => {
            // 已經有實例在背景執行中，直接確認開機自啟並退出重複程序
            let _ = sync_autostart(true);
            log_record("常駐檢查", "已檢測到背景實例正在執行中，已確認開機自啟設定，結束重複啟動。");
            return;
        }
    };

    let cfg = AppConfig::load();

    // 核心要求：點了之後預設確保開機自動運行，並同步註冊表路徑
    let _ = sync_autostart(true);

    log_record("服務啟動", &format!("pc_onoff 背景常駐服務啟動 (PID: {})", std::process::id()));
    log_record("本機資訊", &format!("區域網路 IP: {} (請於手機端填寫此 IP)", get_local_ip()));

    // 常駐監聽 UDP 9877
    run_server(cfg.udp_port);
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
        assert!(cfg.autostart);
    }
}
