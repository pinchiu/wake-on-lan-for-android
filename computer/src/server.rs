//! UDP 傳輸適配器：接收封包、交給 [`Dispatcher`]，再以日誌呈現結果。
//!
//! 這個模組是整個服務唯一知道「UDP 9877」的地方；協定語法在
//! [`crate::action`]，電源操作在 [`crate::power`]。若未來新增 MQTT 或
//! HTTP 通道，只需再寫一個同樣薄的適配器。

use std::fmt;
use std::net::{SocketAddr, UdpSocket};
use std::thread;
use std::time::Duration;

use crate::dispatch::{DispatchError, Dispatcher};
use crate::logging::Logger;
use crate::power::PowerController;
use crate::{LISTEN_IP, MAX_COMMAND_BYTES};

const ERROR_BACKOFF: Duration = Duration::from_millis(250);

/// 伺服器啟動失敗。
#[derive(Debug)]
pub struct ServerError {
    message: String,
}

impl ServerError {
    fn new(message: impl Into<String>) -> Self {
        Self {
            message: message.into(),
        }
    }
}

impl fmt::Display for ServerError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(&self.message)
    }
}

impl std::error::Error for ServerError {}

/// 綁定 UDP 連接埠並常駐監聽，直到行程被終止。
pub fn serve<P: PowerController>(
    dispatcher: &Dispatcher<P>,
    port: u16,
    logger: &Logger,
) -> Result<(), ServerError> {
    let bind_address = format!("{LISTEN_IP}:{port}");
    let socket = UdpSocket::bind(&bind_address).map_err(|error| {
        ServerError::new(format!(
            "綁定 UDP {bind_address} 失敗: {error} (連接埠可能已被佔用)"
        ))
    })?;

    logger.record(
        "服務啟動",
        &format!("成功綁定 UDP {bind_address}，正在常駐監聽遠端控制指令..."),
    );

    let mut buffer = [0u8; MAX_COMMAND_BYTES];
    loop {
        match socket.recv_from(&mut buffer) {
            Ok((size, source)) => handle_datagram(dispatcher, &buffer[..size], source, logger),
            Err(error) => {
                logger.record("網路錯誤", &format!("接收 UDP 資料發生異常: {error}"));
                thread::sleep(ERROR_BACKOFF);
            }
        }
    }
}

/// 處理單一 UDP 封包。與迴圈分離，讓分派行為可以單元測試。
fn handle_datagram<P: PowerController>(
    dispatcher: &Dispatcher<P>,
    payload: &[u8],
    source: SocketAddr,
    logger: &Logger,
) {
    let raw = String::from_utf8_lossy(payload);
    let command = raw.trim();

    logger.record(
        "網路接收",
        &format!(
            "來自 {source} 長度: {} 位元組，指令: '{command}'",
            payload.len()
        ),
    );

    match dispatcher.dispatch(command) {
        Ok(action) => logger.record(
            "指令結果",
            &format!("來源: {source} | {} 執行成功", action.label()),
        ),
        Err(error) => {
            let category = match &error {
                DispatchError::Rejected(_) => "警告",
                DispatchError::Execution { .. } => "指令失敗",
            };
            logger.record(category, &format!("來源: {source} | {error}"));
        }
    }
}

/// 取得本機對外 IP（用於在手機端填寫目標位址）。
///
/// UDP `connect` 不會真的送出封包，因此離線時也能快速取得路由介面位址；
/// 失敗時退回 `127.0.0.1`。
pub fn local_ipv4() -> String {
    UdpSocket::bind("0.0.0.0:0")
        .and_then(|socket| {
            socket.connect("8.8.8.8:80")?;
            socket.local_addr()
        })
        .map(|address| address.ip().to_string())
        .unwrap_or_else(|_| "127.0.0.1".to_owned())
}

#[cfg(test)]
mod tests {
    use std::sync::{Arc, Mutex};

    use super::*;
    use crate::action::PowerAction;
    use crate::power::PowerError;

    #[derive(Default)]
    struct FakePower {
        executed: Arc<Mutex<Vec<PowerAction>>>,
    }

    impl FakePower {
        fn observed(&self) -> Arc<Mutex<Vec<PowerAction>>> {
            Arc::clone(&self.executed)
        }
    }

    impl PowerController for FakePower {
        fn execute(&self, action: PowerAction) -> Result<(), PowerError> {
            self.executed.lock().expect("lock").push(action);
            Ok(())
        }
    }

    fn source() -> SocketAddr {
        "192.168.1.50:54321".parse().expect("來源位址")
    }

    #[test]
    fn dispatches_valid_datagrams() {
        let power = FakePower::default();
        let observed = power.observed();
        let dispatcher = Dispatcher::new(power);
        let logger = Logger::disabled();

        handle_datagram(
            &dispatcher,
            "SHUTDOWN:192.168.1.100".as_bytes(),
            source(),
            &logger,
        );

        assert_eq!(*observed.lock().expect("lock"), vec![PowerAction::Shutdown]);
    }

    #[test]
    fn ignores_empty_datagrams() {
        let power = FakePower::default();
        let observed = power.observed();
        let dispatcher = Dispatcher::new(power);
        let logger = Logger::disabled();

        handle_datagram(&dispatcher, b"  \r\n", source(), &logger);

        assert!(observed.lock().expect("lock").is_empty());
    }

    #[test]
    fn ignores_unknown_datagrams() {
        let power = FakePower::default();
        let observed = power.observed();
        let dispatcher = Dispatcher::new(power);
        let logger = Logger::disabled();

        handle_datagram(&dispatcher, b"self-destruct", source(), &logger);

        assert!(observed.lock().expect("lock").is_empty());
    }

    #[test]
    fn handles_invalid_utf8_without_panicking() {
        let power = FakePower::default();
        let observed = power.observed();
        let dispatcher = Dispatcher::new(power);
        let logger = Logger::disabled();

        handle_datagram(&dispatcher, &[0xff, 0xfe, 0x00], source(), &logger);

        assert!(observed.lock().expect("lock").is_empty());
    }

    #[test]
    fn local_ipv4_is_always_parseable() {
        let ip = local_ipv4();
        assert!(ip.parse::<std::net::IpAddr>().is_ok(), "得到 {ip}");
    }
}
