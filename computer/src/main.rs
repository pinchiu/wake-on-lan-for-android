#![cfg_attr(not(test), windows_subsystem = "windows")]

use std::env;
use std::fs::{self, OpenOptions};
use std::io::Write;
use std::net::{SocketAddr, UdpSocket};
use std::path::{Path, PathBuf};
use std::process::Command;
use std::sync::atomic::{AtomicBool, AtomicU16, Ordering};
#[cfg(windows)]
use std::sync::atomic::AtomicUsize;
use std::sync::Mutex;
use std::thread;
use std::time::Duration;

const LISTEN_IP: &str = "0.0.0.0";
const DEFAULT_UDP_PORT: u16 = 9877;
const BUFFER_SIZE: usize = 1024;
const APP_NAME: &str = "pc_onoff";
const CONFIG_FILE_NAME: &str = "config.ini";

#[cfg(windows)]
// Windows 登錄檔常數
const REG_RUN_KEY: &str = r"HKCU\Software\Microsoft\Windows\CurrentVersion\Run";
#[cfg(windows)]
const REG_RUN_NAME: &str = "WakeOnLanPcOnOff";

#[cfg(windows)]
const WM_DESTROY: u32 = 0x0002;
#[cfg(windows)]
const WM_CLOSE: u32 = 0x0010;
#[cfg(windows)]
const WM_COMMAND: u32 = 0x0111;
#[cfg(windows)]
const WM_USER: u32 = 0x0400;
#[cfg(windows)]
const WM_TRAYICON: u32 = WM_USER + 1;
#[cfg(windows)]
const WM_UPDATE_LOG: u32 = WM_USER + 2;

#[cfg(windows)]
const WM_LBUTTONDBLCLK: usize = 0x0203;
#[cfg(windows)]
const WM_RBUTTONUP: usize = 0x0205;

// Win32 視窗樣式
#[cfg(windows)]
const WS_OVERLAPPED: u32 = 0x00000000;
#[cfg(windows)]
const WS_CAPTION: u32 = 0x00C00000;
#[cfg(windows)]
const WS_SYSMENU: u32 = 0x00080000;
#[cfg(windows)]
const WS_MINIMIZEBOX: u32 = 0x00020000;
#[cfg(windows)]
const WS_VISIBLE: u32 = 0x10000000;
#[cfg(windows)]
const WS_CHILD: u32 = 0x40000000;
#[cfg(windows)]
const WS_BORDER: u32 = 0x00800000;
#[cfg(windows)]
const WS_VSCROLL: u32 = 0x00200000;

// 控制項樣式
#[cfg(windows)]
const BS_GROUPBOX: u32 = 0x00000007;
#[cfg(windows)]
const BS_AUTOCHECKBOX: u32 = 0x00000003;
#[cfg(windows)]
const BS_PUSHBUTTON: u32 = 0x00000000;

#[cfg(windows)]
const ES_MULTILINE: u32 = 0x0004;
#[cfg(windows)]
const ES_AUTOVSCROLL: u32 = 0x0040;
#[cfg(windows)]
const ES_READONLY: u32 = 0x0800;

#[cfg(windows)]
const BM_GETCHECK: u32 = 0x00F0;
#[cfg(windows)]
const BM_SETCHECK: u32 = 0x00F1;
#[cfg(windows)]
const BST_CHECKED: usize = 1;
#[cfg(windows)]
const BST_UNCHECKED: usize = 0;

#[cfg(windows)]
const SW_HIDE: i32 = 0;
#[cfg(windows)]
const SW_SHOWNORMAL: i32 = 1;
#[cfg(windows)]
const SW_RESTORE: i32 = 9;

#[cfg(windows)]
const IDI_APPLICATION: usize = 32512;
#[cfg(windows)]
const IDC_ARROW: usize = 32512;
#[cfg(windows)]
const COLOR_BTNFACE: usize = 15;
#[cfg(windows)]
const DEFAULT_GUI_FONT: usize = 17;
#[cfg(windows)]
const WM_SETFONT: u32 = 0x0030;
#[cfg(windows)]
const WM_VSCROLL_MSG: u32 = 0x0115;
#[cfg(windows)]
const SB_BOTTOM: usize = 7;

#[cfg(windows)]
const NIM_ADD: u32 = 0x00000000;
#[cfg(windows)]
const NIM_DELETE: u32 = 0x00000002;
#[cfg(windows)]
const NIF_MESSAGE: u32 = 0x00000001;
#[cfg(windows)]
const NIF_ICON: u32 = 0x00000002;
#[cfg(windows)]
const NIF_TIP: u32 = 0x00000004;
#[cfg(windows)]
const NIF_INFO: u32 = 0x00000010;

#[cfg(windows)]
const TPM_RIGHTBUTTON: u32 = 0x0002;
#[cfg(windows)]
const MF_STRING: u32 = 0x00000000;
#[cfg(windows)]
const MF_CHECKED: u32 = 0x00000008;
#[cfg(windows)]
const MF_UNCHECKED: u32 = 0x00000000;

// 控制項 ID
#[cfg(windows)]
const ID_EDIT_PORT: usize = 101;
#[cfg(windows)]
const ID_CHK_AUTOSTART: usize = 102;
#[cfg(windows)]
const ID_CHK_MINIMIZED: usize = 103;
#[cfg(windows)]
const ID_BTN_SAVE: usize = 104;
#[cfg(windows)]
const ID_BTN_FIREWALL: usize = 105;
#[cfg(windows)]
const ID_BTN_LOCK: usize = 106;
#[cfg(windows)]
const ID_BTN_LOG: usize = 107;
#[cfg(windows)]
const ID_BTN_TRAY: usize = 108;
#[cfg(windows)]
const ID_STATIC_STATUS: usize = 109;
#[cfg(windows)]
const ID_STATIC_IP: usize = 110;
#[cfg(windows)]
const ID_EDIT_LOGS: usize = 111;

// 系統匣選單 ID
#[cfg(windows)]
const ID_TRAY_OPEN: usize = 201;
#[cfg(windows)]
const ID_TRAY_AUTOSTART: usize = 202;
#[cfg(windows)]
const ID_TRAY_LOG: usize = 203;
#[cfg(windows)]
const ID_TRAY_EXIT: usize = 204;

#[cfg(windows)]
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
#[repr(C)]
struct NotifyIconDataW {
    cb_size: u32,
    h_wnd: *mut std::ffi::c_void,
    u_id: u32,
    u_flags: u32,
    u_callback_message: u32,
    h_icon: *mut std::ffi::c_void,
    sz_tip: [u16; 128],
    dw_state: u32,
    dw_state_mask: u32,
    sz_info: [u16; 256],
    u_timeout_or_version: u32,
    sz_info_title: [u16; 64],
    dw_info_flags: u32,
    guid_item: [u8; 16],
    h_balloon_icon: *mut std::ffi::c_void,
}

#[cfg(windows)]
#[repr(C)]
struct PointWin {
    x: i32,
    y: i32,
}

#[cfg(windows)]
#[repr(C)]
struct MsgWin {
    hwnd: *mut std::ffi::c_void,
    message: u32,
    w_param: usize,
    l_param: isize,
    time: u32,
    pt: PointWin,
    l_private: u32,
}

#[cfg(windows)]
#[repr(C)]
struct WndClassExW {
    cb_size: u32,
    style: u32,
    lpfn_wnd_proc: Option<
        unsafe extern "system" fn(
            *mut std::ffi::c_void,
            u32,
            usize,
            isize,
        ) -> isize,
    >,
    cb_cls_extra: i32,
    cb_wnd_extra: i32,
    h_instance: *mut std::ffi::c_void,
    h_icon: *mut std::ffi::c_void,
    h_cursor: *mut std::ffi::c_void,
    h_br_background: *mut std::ffi::c_void,
    lpsz_menu_name: *const u16,
    lpsz_class_name: *const u16,
    h_icon_sm: *mut std::ffi::c_void,
}

#[cfg(windows)]
mod win_api {
    use super::*;

    #[link(name = "user32")]
    #[link(name = "kernel32")]
    #[link(name = "gdi32")]
    #[link(name = "shell32")]
    unsafe extern "system" {
        pub fn GetModuleHandleW(lpModuleName: *const u16) -> *mut std::ffi::c_void;
        pub fn RegisterClassExW(lpwcx: *const WndClassExW) -> u16;
        pub fn CreateWindowExW(
            dwExStyle: u32,
            lpClassName: *const u16,
            lpWindowName: *const u16,
            dwStyle: u32,
            X: i32,
            Y: i32,
            nWidth: i32,
            nHeight: i32,
            hWndParent: *mut std::ffi::c_void,
            hMenu: *mut std::ffi::c_void,
            hInstance: *mut std::ffi::c_void,
            lpParam: *mut std::ffi::c_void,
        ) -> *mut std::ffi::c_void;
        pub fn DefWindowProcW(
            hWnd: *mut std::ffi::c_void,
            Msg: u32,
            wParam: usize,
            lParam: isize,
        ) -> isize;
        pub fn ShowWindow(hWnd: *mut std::ffi::c_void, nCmdShow: i32) -> i32;
        pub fn UpdateWindow(hWnd: *mut std::ffi::c_void) -> i32;
        pub fn GetMessageW(
            lpMsg: *mut MsgWin,
            hWnd: *mut std::ffi::c_void,
            wMsgFilterMin: u32,
            wMsgFilterMax: u32,
        ) -> i32;
        pub fn TranslateMessage(lpMsg: *const MsgWin) -> i32;
        pub fn DispatchMessageW(lpMsg: *const MsgWin) -> isize;
        pub fn PostQuitMessage(nExitCode: i32);
        pub fn SendMessageW(
            hWnd: *mut std::ffi::c_void,
            Msg: u32,
            wParam: usize,
            lParam: isize,
        ) -> isize;
        pub fn PostMessageW(
            hWnd: *mut std::ffi::c_void,
            Msg: u32,
            wParam: usize,
            lParam: isize,
        ) -> i32;
        pub fn SetWindowTextW(hWnd: *mut std::ffi::c_void, lpString: *const u16) -> i32;
        pub fn GetWindowTextW(
            hWnd: *mut std::ffi::c_void,
            lpString: *mut u16,
            nMaxCount: i32,
        ) -> i32;
        pub fn GetStockObject(i: usize) -> *mut std::ffi::c_void;
        pub fn LoadIconW(
            hInstance: *mut std::ffi::c_void,
            lpIconName: *const u16,
        ) -> *mut std::ffi::c_void;
        pub fn LoadCursorW(
            hInstance: *mut std::ffi::c_void,
            lpCursorName: *const u16,
        ) -> *mut std::ffi::c_void;
        pub fn Shell_NotifyIconW(
            dwMessage: u32,
            lpData: *mut NotifyIconDataW,
        ) -> i32;
        pub fn CreatePopupMenu() -> *mut std::ffi::c_void;
        pub fn AppendMenuW(
            hMenu: *mut std::ffi::c_void,
            uFlags: u32,
            uIDNewItem: usize,
            lpNewItem: *const u16,
        ) -> i32;
        pub fn TrackPopupMenu(
            hMenu: *mut std::ffi::c_void,
            uFlags: u32,
            x: i32,
            y: i32,
            nReserved: i32,
            hWnd: *mut std::ffi::c_void,
            prcRect: *const std::ffi::c_void,
        ) -> i32;
        pub fn DestroyMenu(hMenu: *mut std::ffi::c_void) -> i32;
        pub fn GetCursorPos(lpPoint: *mut PointWin) -> i32;
        pub fn SetForegroundWindow(hWnd: *mut std::ffi::c_void) -> i32;
        pub fn GetLocalTime(lpSystemTime: *mut SystemTimeWin);
        pub fn MessageBoxW(
            hWnd: *mut std::ffi::c_void,
            lpText: *const u16,
            lpCaption: *const u16,
            uType: u32,
        ) -> i32;
    }

    pub fn to_wide_chars(s: &str) -> Vec<u16> {
        s.encode_utf16().chain(std::iter::once(0)).collect()
    }
}

#[derive(Debug, Clone)]
pub struct AppConfig {
    pub udp_port: u16,
    pub autostart: bool,
    pub start_minimized: bool,
}

impl Default for AppConfig {
    fn default() -> Self {
        Self {
            udp_port: DEFAULT_UDP_PORT,
            autostart: true, // 預設即啟用開機自動啟動，無需手動選擇 [1]
            start_minimized: false,
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
                        "start_minimized" => {
                            cfg.start_minimized = v.eq_ignore_ascii_case("true") || v == "1";
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
            "# Wake-on-LAN 電腦端電源控制設定\nudp_port={}\nautostart={}\nstart_minimized={}\n",
            self.udp_port, self.autostart, self.start_minimized
        );
        fs::write(&path, content).map_err(|e| format!("無法儲存設定檔: {}", e))
    }
}

// 全域共享狀態
#[cfg(windows)]
static MAIN_HWND: AtomicUsize = AtomicUsize::new(0);
static RECENT_LOGS: Mutex<Vec<String>> = Mutex::new(Vec::new());
static SERVER_RUNNING: AtomicBool = AtomicBool::new(false);
static ACTIVE_PORT: AtomicU16 = AtomicU16::new(DEFAULT_UDP_PORT);

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

#[cfg(windows)]
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

#[cfg(windows)]
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

    // 保留最近 100 筆於記憶體中供 GUI 即時顯示
    if let Ok(mut logs) = RECENT_LOGS.lock() {
        logs.push(log_line);
        if logs.len() > 100 {
            logs.remove(0);
        }
    }

    // 通知 Windows GUI 視窗即時重繪日誌框
    #[cfg(windows)]
    {
        let hwnd_val = MAIN_HWND.load(Ordering::SeqCst);
        if hwnd_val != 0 {
            unsafe {
                win_api::PostMessageW(hwnd_val as *mut std::ffi::c_void, WM_UPDATE_LOG, 0, 0);
            }
        }
    }
}

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

            let reg_value = format!("\"{}\" --tray", target_exe.to_string_lossy());
            let status = Command::new("reg")
                .args(["add", REG_RUN_KEY, "/v", REG_RUN_NAME, "/t", "REG_SZ", "/d", &reg_value, "/f"])
                .status()
                .map_err(|e| format!("執行 reg 指令失敗: {}", e))?;

            if !status.success() {
                return Err("寫入 Windows 開機啟動登錄項失敗".to_string());
            }
            log_record("設定", "開機自動啟動已啟用 (已註冊至 HKCU Run)");
        } else {
            let _ = Command::new("reg")
                .args(["delete", REG_RUN_KEY, "/v", REG_RUN_NAME, "/f"])
                .status();
            log_record("設定", "開機自動啟動已停用 (已自 HKCU Run 移除)");
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

fn add_firewall_rule(port: u16) -> Result<(), String> {
    #[cfg(windows)]
    {
        let status = Command::new("netsh")
            .args([
                "advfirewall", "firewall", "add", "rule",
                &format!("name=WakeOnLan PC Control ({})", port),
                "dir=in",
                "action=allow",
                "protocol=UDP",
                &format!("localport={}", port),
            ])
            .status()
            .map_err(|e| format!("執行 netsh 指令失敗: {}", e))?;

        if status.success() {
            log_record("防火牆", &format!("已成功新增 Windows 防火牆規則 (UDP {})", port));
            Ok(())
        } else {
            Err("請以「系統管理員身分執行」以自動套用防火牆規則。".to_string())
        }
    }
    #[cfg(not(windows))]
    {
        let _ = port;
        Err("非 Windows 平台無需配置 netsh 防火牆規則。".to_string())
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

fn execute_command(src: Option<SocketAddr>, raw_cmd: &str) {
    let action = parse_action(raw_cmd);
    let src_str = match src {
        Some(addr) => addr.to_string(),
        None => "GUI/手動測試".to_string(),
    };

    match action {
        "shutdown" => {
            log_record(
                "執行指令",
                &format!("來源: {} | 指令: 關機 (shutdown) | 呼叫: shutdown /s /t 0", src_str),
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
                &format!("來源: {} | 指令: 重啟 (reboot) | 呼叫: shutdown /r /t 0", src_str),
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
                &format!(
                    "來源: {} | 指令: 睡眠 (sleep / S3) | 呼叫: LockWorkStation & SetSuspendState 0,1,0",
                    src_str
                ),
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
                &format!(
                    "來源: {} | 指令: 休眠 (hibernate / S4) | 呼叫: LockWorkStation & SetSuspendState 1,1,0",
                    src_str
                ),
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
                &format!("來源: {} | 指令: 鎖定螢幕 (lock) | 呼叫: LockWorkStation", src_str),
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
                    // 超時繼續循環檢查狀態
                }
                Err(e) => {
                    log_record("網路錯誤", &format!("接收 UDP 資料發生異常: {}", e));
                }
            }
        }
        log_record("服務停止", &format!("已停止監聽連接埠 {}", port));
    });
}

#[cfg(windows)]
struct GuiControls {
    edit_port: usize,
    chk_autostart: usize,
    chk_minimized: usize,
    static_status: usize,
    edit_logs: usize,
}

#[cfg(windows)]
static GUI_CONTROLS: Mutex<Option<GuiControls>> = Mutex::new(None);

#[cfg(windows)]
unsafe extern "system" fn wnd_proc(
    hwnd: *mut std::ffi::c_void,
    msg: u32,
    w_param: usize,
    l_param: isize,
) -> isize {
    unsafe {
        match msg {
            WM_TRAYICON => {
                match l_param as usize {
                    WM_LBUTTONDBLCLK => {
                        win_api::ShowWindow(hwnd, SW_RESTORE);
                        win_api::SetForegroundWindow(hwnd);
                    }
                    WM_RBUTTONUP => {
                        let hmenu = win_api::CreatePopupMenu();
                        let title_open = win_api::to_wide_chars("開啟控制視窗");
                        let title_auto = win_api::to_wide_chars("開機自動啟動");
                        let title_log = win_api::to_wide_chars("檢視完整日誌");
                        let title_exit = win_api::to_wide_chars("結束程式");

                        win_api::AppendMenuW(hmenu, MF_STRING, ID_TRAY_OPEN, title_open.as_ptr());

                        let autostart_flag = if is_autostart_registered() {
                            MF_CHECKED
                        } else {
                            MF_UNCHECKED
                        };
                        win_api::AppendMenuW(hmenu, MF_STRING | autostart_flag, ID_TRAY_AUTOSTART, title_auto.as_ptr());
                        win_api::AppendMenuW(hmenu, MF_STRING, ID_TRAY_LOG, title_log.as_ptr());
                        win_api::AppendMenuW(hmenu, MF_STRING, ID_TRAY_EXIT, title_exit.as_ptr());

                        let mut pt = PointWin { x: 0, y: 0 };
                        win_api::GetCursorPos(&mut pt);
                        win_api::SetForegroundWindow(hwnd);
                        win_api::TrackPopupMenu(hmenu, TPM_RIGHTBUTTON, pt.x, pt.y, 0, hwnd, std::ptr::null());
                        win_api::DestroyMenu(hmenu);
                    }
                    _ => {}
                }
                0
            }
            WM_UPDATE_LOG => {
                if let Ok(controls) = GUI_CONTROLS.lock() {
                    if let Some(ref c) = *controls {
                        if let Ok(logs) = RECENT_LOGS.lock() {
                            let full_text = logs.join("\r\n");
                            let wide_text = win_api::to_wide_chars(&full_text);
                            win_api::SetWindowTextW(c.edit_logs as *mut std::ffi::c_void, wide_text.as_ptr());
                            win_api::SendMessageW(c.edit_logs as *mut std::ffi::c_void, WM_VSCROLL_MSG, SB_BOTTOM, 0);
                        }
                    }
                }
                0
            }
            WM_COMMAND => {
                let ctrl_id = w_param & 0xFFFF;
                match ctrl_id {
                    ID_TRAY_OPEN => {
                        win_api::ShowWindow(hwnd, SW_RESTORE);
                        win_api::SetForegroundWindow(hwnd);
                    }
                    ID_TRAY_AUTOSTART => {
                        let current = is_autostart_registered();
                        let new_state = !current;
                        let _ = sync_autostart(new_state);
                        let mut cfg = AppConfig::load();
                        cfg.autostart = new_state;
                        let _ = cfg.save();

                        if let Ok(controls) = GUI_CONTROLS.lock() {
                            if let Some(ref c) = *controls {
                                let check = if new_state { BST_CHECKED } else { BST_UNCHECKED };
                                win_api::SendMessageW(c.chk_autostart as *mut std::ffi::c_void, BM_SETCHECK, check, 0);
                            }
                        }
                    }
                    ID_TRAY_LOG | ID_BTN_LOG => {
                        let log_path = get_log_file_path();
                        let _ = Command::new("notepad.exe").arg(&log_path).spawn();
                    }
                    ID_TRAY_EXIT => {
                        remove_tray_icon(hwnd);
                        SERVER_RUNNING.store(false, Ordering::SeqCst);
                        win_api::PostQuitMessage(0);
                    }
                    ID_BTN_SAVE => {
                        if let Ok(controls) = GUI_CONTROLS.lock() {
                            if let Some(ref c) = *controls {
                                let mut buf = [0u16; 16];
                                win_api::GetWindowTextW(c.edit_port as *mut std::ffi::c_void, buf.as_mut_ptr(), 16);
                                let port_str = String::from_utf16_lossy(&buf)
                                    .trim_matches(char::from(0))
                                    .trim()
                                    .to_string();
                                let port = port_str.parse::<u16>().unwrap_or(DEFAULT_UDP_PORT);

                                let autostart = win_api::SendMessageW(c.chk_autostart as *mut std::ffi::c_void, BM_GETCHECK, 0, 0) as usize == BST_CHECKED;
                                let start_minimized = win_api::SendMessageW(c.chk_minimized as *mut std::ffi::c_void, BM_GETCHECK, 0, 0) as usize == BST_CHECKED;

                                let new_cfg = AppConfig {
                                    udp_port: port,
                                    autostart,
                                    start_minimized,
                                };
                                let _ = new_cfg.save();
                                let _ = sync_autostart(autostart);

                                let current_active_port = ACTIVE_PORT.load(Ordering::SeqCst);
                                if current_active_port != port {
                                    SERVER_RUNNING.store(false, Ordering::SeqCst);
                                    thread::sleep(Duration::from_millis(600));
                                    start_udp_listener(port);
                                }

                                let status_text = format!("服務狀態: 🟢 監聽中 (0.0.0.0:{})", port);
                                let wide_status = win_api::to_wide_chars(&status_text);
                                win_api::SetWindowTextW(c.static_status as *mut std::ffi::c_void, wide_status.as_ptr());

                                let msg = win_api::to_wide_chars("設定已成功儲存並生效！");
                                let caption = win_api::to_wide_chars("儲存成功");
                                win_api::MessageBoxW(hwnd, msg.as_ptr(), caption.as_ptr(), 0x40);
                                log_record("設定", &format!("已更新設定: Port={}, 開機啟動={}, 最小化啟動={}", port, autostart, start_minimized));
                            }
                        }
                    }
                    ID_BTN_FIREWALL => {
                        let port = ACTIVE_PORT.load(Ordering::SeqCst);
                        match add_firewall_rule(port) {
                            Ok(_) => {
                                let msg = win_api::to_wide_chars(&format!("已成功新增 Windows 防火牆規則 (UDP {})！", port));
                                let caption = win_api::to_wide_chars("防火牆設定");
                                win_api::MessageBoxW(hwnd, msg.as_ptr(), caption.as_ptr(), 0x40);
                            }
                            Err(e) => {
                                let msg = win_api::to_wide_chars(&format!("新增防火牆規則失敗:\n{}\n\n請以「系統管理員身分執行」此程式後重試。", e));
                                let caption = win_api::to_wide_chars("權限不足");
                                win_api::MessageBoxW(hwnd, msg.as_ptr(), caption.as_ptr(), 0x30);
                            }
                        }
                    }
                    ID_BTN_LOCK => {
                        log_record("手動測試", "GUI 觸發測試鎖定螢幕");
                        execute_command(None, "lock");
                    }
                    ID_BTN_TRAY => {
                        win_api::ShowWindow(hwnd, SW_HIDE);
                    }
                    _ => {}
                }
                0
            }
            WM_CLOSE => {
                // 點擊關閉視窗 (X) 時，縮小至系統匣背景常駐，不中斷監聽服務
                win_api::ShowWindow(hwnd, SW_HIDE);
                0
            }
            WM_DESTROY => {
                remove_tray_icon(hwnd);
                SERVER_RUNNING.store(false, Ordering::SeqCst);
                win_api::PostQuitMessage(0);
                0
            }
            _ => win_api::DefWindowProcW(hwnd, msg, w_param, l_param),
        }
    }
}

#[cfg(windows)]
fn add_tray_icon(hwnd: *mut std::ffi::c_void) {
    let mut nid = unsafe { std::mem::zeroed::<NotifyIconDataW>() };
    nid.cb_size = std::mem::size_of::<NotifyIconDataW>() as u32;
    nid.h_wnd = hwnd;
    nid.u_id = 1;
    nid.u_flags = NIF_MESSAGE | NIF_ICON | NIF_TIP | NIF_INFO;
    nid.u_callback_message = WM_TRAYICON;
    nid.h_icon = unsafe { win_api::LoadIconW(std::ptr::null_mut(), IDI_APPLICATION as _) };

    let tip = "Wake-on-LAN 電腦遠端控制服務 (監聽中)";
    for (i, c) in tip.encode_utf16().enumerate().take(127) {
        nid.sz_tip[i] = c;
    }

    let title = "Wake-on-LAN 服務啟動";
    for (i, c) in title.encode_utf16().enumerate().take(63) {
        nid.sz_info_title[i] = c;
    }

    let info = "服務已在背景常駐監聽。點擊系統匣圖示可隨時打開設定視窗。";
    for (i, c) in info.encode_utf16().enumerate().take(255) {
        nid.sz_info[i] = c;
    }

    unsafe {
        win_api::Shell_NotifyIconW(NIM_ADD, &mut nid);
    }
}

#[cfg(windows)]
fn remove_tray_icon(hwnd: *mut std::ffi::c_void) {
    let mut nid = unsafe { std::mem::zeroed::<NotifyIconDataW>() };
    nid.cb_size = std::mem::size_of::<NotifyIconDataW>() as u32;
    nid.h_wnd = hwnd;
    nid.u_id = 1;
    unsafe {
        win_api::Shell_NotifyIconW(NIM_DELETE, &mut nid);
    }
}

#[cfg(windows)]
fn run_windows_gui(start_in_tray: bool) {
    let h_instance = unsafe { win_api::GetModuleHandleW(std::ptr::null()) };
    let class_name = win_api::to_wide_chars("WakeOnLanControlClass");
    let window_title = win_api::to_wide_chars("Wake-on-LAN 電腦端電源控制設定");

    let wnd_class = WndClassExW {
        cb_size: std::mem::size_of::<WndClassExW>() as u32,
        style: 0,
        lpfn_wnd_proc: Some(wnd_proc),
        cb_cls_extra: 0,
        cb_wnd_extra: 0,
        h_instance,
        h_icon: unsafe { win_api::LoadIconW(std::ptr::null_mut(), IDI_APPLICATION as _) },
        h_cursor: unsafe { win_api::LoadCursorW(std::ptr::null_mut(), IDC_ARROW as _) },
        h_br_background: (COLOR_BTNFACE + 1) as *mut std::ffi::c_void,
        lpsz_menu_name: std::ptr::null(),
        lpsz_class_name: class_name.as_ptr(),
        h_icon_sm: std::ptr::null_mut(),
    };

    unsafe {
        win_api::RegisterClassExW(&wnd_class);
    }

    // 建立主視窗 (寬 540，高 510)
    let hwnd = unsafe {
        win_api::CreateWindowExW(
            0,
            class_name.as_ptr(),
            window_title.as_ptr(),
            WS_OVERLAPPED | WS_CAPTION | WS_SYSMENU | WS_MINIMIZEBOX,
            180,
            140,
            540,
            510,
            std::ptr::null_mut(),
            std::ptr::null_mut(),
            h_instance,
            std::ptr::null_mut(),
        )
    };

    if hwnd.is_null() {
        return;
    }

    MAIN_HWND.store(hwnd as usize, Ordering::SeqCst);

    let h_font = unsafe { win_api::GetStockObject(DEFAULT_GUI_FONT) };
    let str_button = win_api::to_wide_chars("BUTTON");
    let str_static = win_api::to_wide_chars("STATIC");
    let str_edit = win_api::to_wide_chars("EDIT");

    // 載入設定
    let cfg = AppConfig::load();
    let current_port = cfg.udp_port;
    let autostart = cfg.autostart;
    let minimized = cfg.start_minimized;

    // 預設若啟用開機自啟，初次執行自動幫使用者完成註冊
    if autostart && !is_autostart_registered() {
        let _ = sync_autostart(true);
    }

    // 啟動背景 UDP 監聽
    start_udp_listener(current_port);

    unsafe {
        // === 群組一：基本設定 ===
        let g1_title = win_api::to_wide_chars("基本設定 (Settings)");
        let g1 = win_api::CreateWindowExW(0, str_button.as_ptr(), g1_title.as_ptr(), WS_CHILD | WS_VISIBLE | BS_GROUPBOX, 15, 10, 495, 115, hwnd, std::ptr::null_mut(), h_instance, std::ptr::null_mut());
        win_api::SendMessageW(g1, WM_SETFONT, h_font as _, 1);

        let lbl_port_t = win_api::to_wide_chars("監聽 UDP 連接埠 (Port):");
        let lbl_p = win_api::CreateWindowExW(0, str_static.as_ptr(), lbl_port_t.as_ptr(), WS_CHILD | WS_VISIBLE, 30, 36, 170, 20, hwnd, std::ptr::null_mut(), h_instance, std::ptr::null_mut());
        win_api::SendMessageW(lbl_p, WM_SETFONT, h_font as _, 1);

        let edit_p_val = win_api::to_wide_chars(&current_port.to_string());
        let edit_p = win_api::CreateWindowExW(0, str_edit.as_ptr(), edit_p_val.as_ptr(), WS_CHILD | WS_VISIBLE | WS_BORDER, 205, 33, 75, 23, hwnd, ID_EDIT_PORT as _, h_instance, std::ptr::null_mut());
        win_api::SendMessageW(edit_p, WM_SETFONT, h_font as _, 1);

        let chk_auto_t = win_api::to_wide_chars("開機自動啟動 (Autostart)");
        let chk_auto = win_api::CreateWindowExW(0, str_button.as_ptr(), chk_auto_t.as_ptr(), WS_CHILD | WS_VISIBLE | BS_AUTOCHECKBOX, 300, 34, 195, 22, hwnd, ID_CHK_AUTOSTART as _, h_instance, std::ptr::null_mut());
        win_api::SendMessageW(chk_auto, WM_SETFONT, h_font as _, 1);
        let check_val = if autostart { BST_CHECKED } else { BST_UNCHECKED };
        win_api::SendMessageW(chk_auto, BM_SETCHECK, check_val, 0);

        let chk_min_t = win_api::to_wide_chars("啟動時最小化至系統匣 (Start Minimized)");
        let chk_min = win_api::CreateWindowExW(0, str_button.as_ptr(), chk_min_t.as_ptr(), WS_CHILD | WS_VISIBLE | BS_AUTOCHECKBOX, 30, 75, 260, 22, hwnd, ID_CHK_MINIMIZED as _, h_instance, std::ptr::null_mut());
        win_api::SendMessageW(chk_min, WM_SETFONT, h_font as _, 1);
        let min_val = if minimized { BST_CHECKED } else { BST_UNCHECKED };
        win_api::SendMessageW(chk_min, BM_SETCHECK, min_val, 0);

        let btn_save_t = win_api::to_wide_chars("儲存並套用設定");
        let btn_save = win_api::CreateWindowExW(0, str_button.as_ptr(), btn_save_t.as_ptr(), WS_CHILD | WS_VISIBLE | BS_PUSHBUTTON, 320, 72, 170, 32, hwnd, ID_BTN_SAVE as _, h_instance, std::ptr::null_mut());
        win_api::SendMessageW(btn_save, WM_SETFONT, h_font as _, 1);

        // === 群組二：服務狀態與操作 ===
        let g2_title = win_api::to_wide_chars("連線與服務狀態 (Status)");
        let g2 = win_api::CreateWindowExW(0, str_button.as_ptr(), g2_title.as_ptr(), WS_CHILD | WS_VISIBLE | BS_GROUPBOX, 15, 135, 495, 110, hwnd, std::ptr::null_mut(), h_instance, std::ptr::null_mut());
        win_api::SendMessageW(g2, WM_SETFONT, h_font as _, 1);

        let status_t = win_api::to_wide_chars(&format!("服務狀態: 🟢 監聽中 (0.0.0.0:{})", current_port));
        let lbl_st = win_api::CreateWindowExW(0, str_static.as_ptr(), status_t.as_ptr(), WS_CHILD | WS_VISIBLE, 30, 160, 260, 20, hwnd, ID_STATIC_STATUS as _, h_instance, std::ptr::null_mut());
        win_api::SendMessageW(lbl_st, WM_SETFONT, h_font as _, 1);

        let local_ip = get_local_ip();
        let ip_t = win_api::to_wide_chars(&format!("本機區域網路 IP: {}", local_ip));
        let lbl_ip = win_api::CreateWindowExW(0, str_static.as_ptr(), ip_t.as_ptr(), WS_CHILD | WS_VISIBLE, 30, 185, 260, 20, hwnd, ID_STATIC_IP as _, h_instance, std::ptr::null_mut());
        win_api::SendMessageW(lbl_ip, WM_SETFONT, h_font as _, 1);

        let btn_fw_t = win_api::to_wide_chars("設定防火牆規則");
        let btn_fw = win_api::CreateWindowExW(0, str_button.as_ptr(), btn_fw_t.as_ptr(), WS_CHILD | WS_VISIBLE | BS_PUSHBUTTON, 300, 156, 95, 28, hwnd, ID_BTN_FIREWALL as _, h_instance, std::ptr::null_mut());
        win_api::SendMessageW(btn_fw, WM_SETFONT, h_font as _, 1);

        let btn_lock_t = win_api::to_wide_chars("測試鎖定螢幕");
        let btn_lk = win_api::CreateWindowExW(0, str_button.as_ptr(), btn_lock_t.as_ptr(), WS_CHILD | WS_VISIBLE | BS_PUSHBUTTON, 400, 156, 95, 28, hwnd, ID_BTN_LOCK as _, h_instance, std::ptr::null_mut());
        win_api::SendMessageW(btn_lk, WM_SETFONT, h_font as _, 1);

        let btn_tray_t = win_api::to_wide_chars("縮小至系統匣");
        let btn_tr = win_api::CreateWindowExW(0, str_button.as_ptr(), btn_tray_t.as_ptr(), WS_CHILD | WS_VISIBLE | BS_PUSHBUTTON, 300, 192, 95, 28, hwnd, ID_BTN_TRAY as _, h_instance, std::ptr::null_mut());
        win_api::SendMessageW(btn_tr, WM_SETFONT, h_font as _, 1);

        let btn_log_t = win_api::to_wide_chars("開啟完整日誌");
        let btn_lg = win_api::CreateWindowExW(0, str_button.as_ptr(), btn_log_t.as_ptr(), WS_CHILD | WS_VISIBLE | BS_PUSHBUTTON, 400, 192, 95, 28, hwnd, ID_BTN_LOG as _, h_instance, std::ptr::null_mut());
        win_api::SendMessageW(btn_lg, WM_SETFONT, h_font as _, 1);

        // === 群組三：即時通訊日誌 ===
        let g3_title = win_api::to_wide_chars("即時日誌紀錄 (Real-time Log)");
        let g3 = win_api::CreateWindowExW(0, str_button.as_ptr(), g3_title.as_ptr(), WS_CHILD | WS_VISIBLE | BS_GROUPBOX, 15, 255, 495, 205, hwnd, std::ptr::null_mut(), h_instance, std::ptr::null_mut());
        win_api::SendMessageW(g3, WM_SETFONT, h_font as _, 1);

        let edit_log = win_api::CreateWindowExW(
            0,
            str_edit.as_ptr(),
            std::ptr::null(),
            WS_CHILD | WS_VISIBLE | WS_BORDER | WS_VSCROLL | ES_MULTILINE | ES_AUTOVSCROLL | ES_READONLY,
            25,
            280,
            475,
            165,
            hwnd,
            ID_EDIT_LOGS as _,
            h_instance,
            std::ptr::null_mut(),
        );
        win_api::SendMessageW(edit_log, WM_SETFONT, h_font as _, 1);

        if let Ok(mut guard) = GUI_CONTROLS.lock() {
            *guard = Some(GuiControls {
                edit_port: edit_p as usize,
                chk_autostart: chk_auto as usize,
                chk_minimized: chk_min as usize,
                static_status: lbl_st as usize,
                edit_logs: edit_log as usize,
            });
        }
    }

    // 加入 Windows 系統匣 (托盤圖示)
    add_tray_icon(hwnd);

    // 視參數決定直接顯示 GUI 視窗，或是最小化至系統匣背景常駐
    if !start_in_tray && !minimized {
        unsafe {
            win_api::ShowWindow(hwnd, SW_SHOWNORMAL);
            win_api::UpdateWindow(hwnd);
        }
        log_record("GUI", "主控制視窗已開啟。");
    } else {
        unsafe {
            win_api::ShowWindow(hwnd, SW_HIDE);
        }
        log_record("系統匣", "程式已最小化至系統匣背景常駐運作中。");
    }

    // 填入初始日誌紀錄
    unsafe {
        win_api::PostMessageW(hwnd, WM_UPDATE_LOG, 0, 0);
    }

    // Windows 訊息幫浦
    let mut msg = unsafe { std::mem::zeroed::<MsgWin>() };
    unsafe {
        while win_api::GetMessageW(&mut msg, std::ptr::null_mut(), 0, 0) > 0 {
            win_api::TranslateMessage(&msg);
            win_api::DispatchMessageW(&msg);
        }
    }
}

fn main() {
    let args: Vec<String> = env::args().collect();

    // 1. 命令列常用參數處理
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

    if args.iter().any(|a| a == "--firewall") {
        let cfg = AppConfig::load();
        let _ = add_firewall_rule(cfg.udp_port);
        return;
    }

    if args.iter().any(|a| a == "--help" || a == "-h") {
        println!("Wake-on-LAN 電腦端電源控制公用程式 (具備原生 GUI 視窗與系統匣支援)");
        println!();
        println!("用法:");
        println!("  pc_onoff.exe              雙擊直接開啟原生 GUI 設定視窗");
        println!("  pc_onoff.exe --tray       於背景靜默啟動並常駐於系統匣");
        println!("  pc_onoff.exe --install    註冊開機自動啟動");
        println!("  pc_onoff.exe --uninstall  解除開機自動啟動");
        return;
    }

    // 2. Windows 平台：預設直接開啟原生 GUI 控制視窗！
    #[cfg(windows)]
    {
        let start_in_tray = args.iter().any(|a| a == "--tray" || a == "--background" || a == "-b");
        run_windows_gui(start_in_tray);
    }

    // 3. Linux 平台：若在 Linux 執行，無 Win32 時以背景 Daemon 運作
    #[cfg(not(windows))]
    {
        let cfg = AppConfig::load();
        if cfg.autostart && !is_autostart_registered() {
            let _ = sync_autostart(true);
        }
        start_udp_listener(cfg.udp_port);
        log_record("常駐執行", &format!("Linux 背景守護行程已啟動，監聽 UDP {}", cfg.udp_port));
        loop {
            thread::sleep(Duration::from_secs(3600));
        }
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
        assert!(cfg.autostart); // 預設即為開機自動啟動
        assert!(!cfg.start_minimized);
    }
}
