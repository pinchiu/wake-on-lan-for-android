//! Minimal cryptographic primitives used by command authentication.
//!
//! SHA-256 (FIPS 180-4) and HMAC-SHA256 (RFC 2104) are implemented here directly so the
//! daemon keeps its zero-dependency property. Only one-shot APIs are provided: command
//! envelopes are tiny, so streaming state would add complexity without benefit.
//!
//! The module also hosts small encoding helpers ([`hex_encode`], [`hex_decode`]), the
//! constant-time comparison used for signatures, and the OS random source used to generate
//! secrets and nonces.

/// SHA-256 round constants (FIPS 180-4 section 4.2.2).
const SHA256_K: [u32; 64] = [
    0x428a_2f98,
    0x7137_4491,
    0xb5c0_fbcf,
    0xe9b5_dba5,
    0x3956_c25b,
    0x59f1_11f1,
    0x923f_82a4,
    0xab1c_5ed5,
    0xd807_aa98,
    0x1283_5b01,
    0x2431_85be,
    0x550c_7dc3,
    0x72be_5d74,
    0x80de_b1fe,
    0x9bdc_06a7,
    0xc19b_f174,
    0xe49b_69c1,
    0xefbe_4786,
    0x0fc1_9dc6,
    0x240c_a1cc,
    0x2de9_2c6f,
    0x4a74_84aa,
    0x5cb0_a9dc,
    0x76f9_88da,
    0x983e_5152,
    0xa831_c66d,
    0xb003_27c8,
    0xbf59_7fc7,
    0xc6e0_0bf3,
    0xd5a7_9147,
    0x06ca_6351,
    0x1429_2967,
    0x27b7_0a85,
    0x2e1b_2138,
    0x4d2c_6dfc,
    0x5338_0d13,
    0x650a_7354,
    0x766a_0abb,
    0x81c2_c92e,
    0x9272_2c85,
    0xa2bf_e8a1,
    0xa81a_664b,
    0xc24b_8b70,
    0xc76c_51a3,
    0xd192_e819,
    0xd699_0624,
    0xf40e_3585,
    0x106a_a070,
    0x19a4_c116,
    0x1e37_6c08,
    0x2748_774c,
    0x34b0_bcb5,
    0x391c_0cb3,
    0x4ed8_aa4a,
    0x5b9c_ca4f,
    0x682e_6ff3,
    0x748f_82ee,
    0x78a5_636f,
    0x84c8_7814,
    0x8cc7_0208,
    0x90be_fffa,
    0xa450_6ceb,
    0xbef9_a3f7,
    0xc671_78f2,
];

const SHA256_INITIAL_STATE: [u32; 8] = [
    0x6a09_e667,
    0xbb67_ae85,
    0x3c6e_f372,
    0xa54f_f53a,
    0x510e_527f,
    0x9b05_688c,
    0x1f83_d9ab,
    0x5be0_cd19,
];

const SHA256_BLOCK_BYTES: usize = 64;
const SHA256_DIGEST_BYTES: usize = 32;
const HMAC_BLOCK_BYTES: usize = 64;

/// Compute the SHA-256 digest of `data`.
pub fn sha256(data: &[u8]) -> [u8; SHA256_DIGEST_BYTES] {
    // Pad to a multiple of 64 bytes: 0x80, zeroes, then the 64-bit big-endian bit length.
    let bit_length = (data.len() as u64).wrapping_mul(8);
    let mut message = Vec::with_capacity(data.len() + SHA256_BLOCK_BYTES * 2);
    message.extend_from_slice(data);
    message.push(0x80);
    while message.len() % SHA256_BLOCK_BYTES != SHA256_BLOCK_BYTES - 8 {
        message.push(0);
    }
    message.extend_from_slice(&bit_length.to_be_bytes());

    let mut state = SHA256_INITIAL_STATE;
    for chunk in message.as_chunks::<SHA256_BLOCK_BYTES>().0 {
        let mut schedule = [0u32; 64];
        for (word, bytes) in schedule.iter_mut().take(16).zip(chunk.as_chunks::<4>().0) {
            *word = u32::from_be_bytes(*bytes);
        }
        for index in 16..64 {
            let first = schedule[index - 15];
            let second = schedule[index - 2];
            let sigma0 = first.rotate_right(7) ^ first.rotate_right(18) ^ (first >> 3);
            let sigma1 = second.rotate_right(17) ^ second.rotate_right(19) ^ (second >> 10);
            schedule[index] = schedule[index - 16]
                .wrapping_add(sigma0)
                .wrapping_add(schedule[index - 7])
                .wrapping_add(sigma1);
        }

        let [mut a, mut b, mut c, mut d, mut e, mut f, mut g, mut h] = state;
        for (&constant, &word) in SHA256_K.iter().zip(schedule.iter()) {
            let sigma1 = e.rotate_right(6) ^ e.rotate_right(11) ^ e.rotate_right(25);
            let choose = (e & f) ^ ((!e) & g);
            let temporary1 = h
                .wrapping_add(sigma1)
                .wrapping_add(choose)
                .wrapping_add(constant)
                .wrapping_add(word);
            let sigma0 = a.rotate_right(2) ^ a.rotate_right(13) ^ a.rotate_right(22);
            let majority = (a & b) ^ (a & c) ^ (b & c);
            let temporary2 = sigma0.wrapping_add(majority);

            h = g;
            g = f;
            f = e;
            e = d.wrapping_add(temporary1);
            d = c;
            c = b;
            b = a;
            a = temporary1.wrapping_add(temporary2);
        }

        state[0] = state[0].wrapping_add(a);
        state[1] = state[1].wrapping_add(b);
        state[2] = state[2].wrapping_add(c);
        state[3] = state[3].wrapping_add(d);
        state[4] = state[4].wrapping_add(e);
        state[5] = state[5].wrapping_add(f);
        state[6] = state[6].wrapping_add(g);
        state[7] = state[7].wrapping_add(h);
    }

    let mut digest = [0u8; SHA256_DIGEST_BYTES];
    for (word, bytes) in state.iter().zip(digest.as_chunks_mut::<4>().0) {
        bytes.copy_from_slice(&word.to_be_bytes());
    }
    digest
}

/// Compute HMAC-SHA256 over `data` using `key` (RFC 2104).
pub fn hmac_sha256(key: &[u8], data: &[u8]) -> [u8; SHA256_DIGEST_BYTES] {
    let mut normalized_key = [0u8; HMAC_BLOCK_BYTES];
    if key.len() > HMAC_BLOCK_BYTES {
        normalized_key[..SHA256_DIGEST_BYTES].copy_from_slice(&sha256(key));
    } else {
        normalized_key[..key.len()].copy_from_slice(key);
    }

    let mut inner_pad = [0x36u8; HMAC_BLOCK_BYTES];
    let mut outer_pad = [0x5cu8; HMAC_BLOCK_BYTES];
    for (inner, key_byte) in inner_pad.iter_mut().zip(normalized_key.iter()) {
        *inner ^= key_byte;
    }
    for (outer, key_byte) in outer_pad.iter_mut().zip(normalized_key.iter()) {
        *outer ^= key_byte;
    }

    let mut inner_message = Vec::with_capacity(HMAC_BLOCK_BYTES + data.len());
    inner_message.extend_from_slice(&inner_pad);
    inner_message.extend_from_slice(data);
    let inner_digest = sha256(&inner_message);

    let mut outer_message = Vec::with_capacity(HMAC_BLOCK_BYTES + SHA256_DIGEST_BYTES);
    outer_message.extend_from_slice(&outer_pad);
    outer_message.extend_from_slice(&inner_digest);
    sha256(&outer_message)
}

/// Encode bytes as lowercase hexadecimal.
pub fn hex_encode(bytes: &[u8]) -> String {
    const DIGITS: &[u8; 16] = b"0123456789abcdef";
    let mut output = String::with_capacity(bytes.len() * 2);
    for &byte in bytes {
        output.push(DIGITS[(byte >> 4) as usize] as char);
        output.push(DIGITS[(byte & 0x0f) as usize] as char);
    }
    output
}

/// Decode hexadecimal text (upper- or lowercase). Returns `None` for odd length or non-hex.
pub fn hex_decode(text: &str) -> Option<Vec<u8>> {
    let bytes = text.as_bytes();
    if bytes.len() & 1 != 0 {
        return None;
    }

    let mut output = Vec::with_capacity(bytes.len() / 2);
    for pair in bytes.as_chunks::<2>().0 {
        output.push((hex_value(pair[0])? << 4) | hex_value(pair[1])?);
    }
    Some(output)
}

fn hex_value(byte: u8) -> Option<u8> {
    match byte {
        b'0'..=b'9' => Some(byte - b'0'),
        b'a'..=b'f' => Some(byte - b'a' + 10),
        b'A'..=b'F' => Some(byte - b'A' + 10),
        _ => None,
    }
}

/// Compare two byte strings without early exit on the first differing byte.
///
/// The length comparison is not secret (signatures have a fixed public length); the byte
/// comparison loop always inspects every byte of equal-length inputs.
pub fn constant_time_eq(left: &[u8], right: &[u8]) -> bool {
    if left.len() != right.len() {
        return false;
    }

    let mut difference = 0u8;
    for (left_byte, right_byte) in left.iter().zip(right.iter()) {
        difference |= left_byte ^ right_byte;
    }
    difference == 0
}

/// Fill `buffer` with random bytes from the operating system.
///
/// Returns `false` if the platform random source is unavailable. Unix reads `/dev/urandom`;
/// Windows calls `BCryptGenRandom`; other platforms fall back to hashing std's randomly
/// seeded `RandomState`, which keeps the source buildable but is not a CSPRNG.
pub fn fill_random(buffer: &mut [u8]) -> bool {
    os_random(buffer)
}

#[cfg(unix)]
fn os_random(buffer: &mut [u8]) -> bool {
    use std::fs::File;
    use std::io::Read;

    let Ok(mut source) = File::open("/dev/urandom") else {
        return false;
    };
    source.read_exact(buffer).is_ok()
}

#[cfg(windows)]
fn os_random(buffer: &mut [u8]) -> bool {
    use std::ffi::c_void;

    #[link(name = "bcrypt")]
    unsafe extern "system" {
        fn BCryptGenRandom(
            algorithm: *mut c_void,
            buffer: *mut u8,
            buffer_length: u32,
            flags: u32,
        ) -> i32;
    }

    // With BCRYPT_USE_SYSTEM_PREFERRED_RNG the algorithm handle must be NULL.
    const BCRYPT_USE_SYSTEM_PREFERRED_RNG: u32 = 0x0000_0002;

    if buffer.is_empty() {
        return true;
    }
    let Ok(length) = u32::try_from(buffer.len()) else {
        return false;
    };

    // NTSTATUS 0 (STATUS_SUCCESS) means the buffer was filled.
    unsafe {
        BCryptGenRandom(
            std::ptr::null_mut(),
            buffer.as_mut_ptr(),
            length,
            BCRYPT_USE_SYSTEM_PREFERRED_RNG,
        ) == 0
    }
}

#[cfg(not(any(unix, windows)))]
fn os_random(buffer: &mut [u8]) -> bool {
    use std::collections::hash_map::RandomState;
    use std::hash::{BuildHasher, Hasher};
    use std::sync::atomic::{AtomicU64, Ordering};
    use std::time::{SystemTime, UNIX_EPOCH};

    static COUNTER: AtomicU64 = AtomicU64::new(0);
    let state = RandomState::new();
    let mut offset = 0;
    while offset < buffer.len() {
        let mut hasher = state.build_hasher();
        hasher.write_u32(std::process::id());
        hasher.write_u64(COUNTER.fetch_add(1, Ordering::Relaxed));
        let nanos = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .map(|duration| duration.as_nanos())
            .unwrap_or(0);
        hasher.write_u128(nanos);
        let output = hasher.finish().to_le_bytes();
        let take = output.len().min(buffer.len() - offset);
        buffer[offset..offset + take].copy_from_slice(&output[..take]);
        offset += take;
    }
    true
}

#[cfg(test)]
mod tests {
    use super::*;

    fn hex(bytes: &[u8]) -> String {
        hex_encode(bytes)
    }

    // FIPS 180-4 / NIST CAVP SHA-256 vectors.
    #[test]
    fn sha256_matches_nist_vectors() {
        assert_eq!(
            hex(&sha256(b"")),
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        );
        assert_eq!(
            hex(&sha256(b"abc")),
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
        );
        assert_eq!(
            hex(&sha256(
                b"abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq"
            )),
            "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1"
        );
    }

    #[test]
    fn sha256_handles_multi_block_input() {
        assert_eq!(
            hex(&sha256(&vec![b'a'; 1_000_000])),
            "cdc76e5c9914fb9281a1c7e284d73e67f1809a48a497200e046d39ccc7112cd0"
        );
    }

    // RFC 4231 test cases 1, 2, 3, and 6 (the last exercises a key longer than a block).
    #[test]
    fn hmac_sha256_matches_rfc_4231_vectors() {
        assert_eq!(
            hex(&hmac_sha256(&[0x0b; 20], b"Hi There")),
            "b0344c61d8db38535ca8afceaf0bf12b881dc200c9833da726e9376c2e32cff7"
        );
        assert_eq!(
            hex(&hmac_sha256(b"Jefe", b"what do ya want for nothing?")),
            "5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843"
        );
        assert_eq!(
            hex(&hmac_sha256(&[0xaa; 20], &[0xdd; 50])),
            "773ea91e36800e46854db8ebd09181a72959098b3ef8c122d9635514ced565fe"
        );
        assert_eq!(
            hex(&hmac_sha256(
                &[0xaa; 131],
                b"Test Using Larger Than Block-Size Key - Hash Key First"
            )),
            "60e431591ee0b67f0d8a26aacbf5b77f8e0bc6213728c5140546040f0ee37f54"
        );
    }

    #[test]
    fn hex_round_trips_and_rejects_invalid_input() {
        let bytes: Vec<u8> = (0..=255).collect();
        let encoded = hex_encode(&bytes);
        assert_eq!(encoded.len(), 512);
        assert_eq!(hex_decode(&encoded), Some(bytes));
        assert_eq!(hex_decode("00FFa1"), Some(vec![0x00, 0xff, 0xa1]));
        assert_eq!(hex_decode("abc"), None);
        assert_eq!(hex_decode("zz"), None);
        assert_eq!(hex_decode("-0"), None);
    }

    #[test]
    fn constant_time_comparison_checks_every_byte() {
        assert!(constant_time_eq(b"", b""));
        assert!(constant_time_eq(b"same", b"same"));
        assert!(!constant_time_eq(b"same", b"Same"));
        assert!(!constant_time_eq(b"same", b"samf"));
        assert!(!constant_time_eq(b"short", b"longer"));
    }

    #[test]
    fn os_random_fills_the_buffer() {
        let mut buffer = [0u8; 32];
        assert!(fill_random(&mut buffer), "OS random source unavailable");
        assert!(
            buffer.iter().any(|byte| *byte != 0),
            "random output should not be all zeroes"
        );
    }
}
