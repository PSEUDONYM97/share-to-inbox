# Share-to-Inbox Architecture

## Overview

Share-to-Inbox is a privacy-first system for sending content from your phone to your AI assistant. It uses time-based rotating topics, hardware-bound secrets, and ephemeral storage to ensure zero-knowledge operation.

## Threat Model

### What We Protect Against

| Threat | Mitigation |
|--------|------------|
| Topic guessing | 32-char hex = 128 bits entropy, computationally infeasible |
| Topic correlation | Pure HMAC output, no prefix/identifier, topics appear random |
| Historical reconstruction | No topic history stored, keys evaporate after use |
| Persistent surveillance | Messages expire after 12 hours on ntfy.sh |
| Device theft (phone) | Secret auto-deletes on expiration, no message history |
| Config file theft (computer) | Hardware-bound secret, useless without original hardware |

### What We Don't Protect Against

| Threat | Why |
|--------|-----|
| Device compromise while paired | If attacker owns your device, all bets are off |
| AI provider seeing content | That's the feature - you're sending TO your AI |
| ntfy.sh being malicious | Mitigated by self-hosting option |
| Quantum computing | Overkill for this threat model |

## Security Properties

### Zero-Knowledge Design

1. **No identifiers anywhere** - Topics are pure hash output
2. **No message history** - Phone doesn't log what it sent
3. **No topic history** - Computed, used, forgotten
4. **Auto-expiration** - Everything dies: messages (12h), topics (6h), pairing (user-defined)

### Hardware-Bound Secrets

```
secret = HMAC-SHA256(
    hardware_fingerprint,    // CPU ID + disk serial + MAC + BIOS
    random_bytes(32) + timestamp
)
```

The secret is tied to the specific computer. Stealing the config file is useless without the hardware.

### Time-Based Topic Rotation (TOTP-style)

```
topic = HMAC-SHA256(secret, floor(unix_time / window_seconds))
        .hex()
        .substring(0, 32)
```

- Default window: 6 hours
- Topics rotate automatically
- Both sides compute independently
- No coordination needed

## Data Flow

```
┌─────────┐         ┌─────────┐         ┌─────────┐
│  Phone  │         │ ntfy.sh │         │ Computer│
└────┬────┘         └────┬────┘         └────┬────┘
     │                   │                   │
     │ 1. Compute topic  │                   │
     │    (TOTP)         │                   │
     │                   │                   │
     │ 2. POST /topic    │                   │
     │ ─────────────────>│                   │
     │                   │                   │
     │ 3. Evaporate      │                   │
     │    (forget topic) │                   │
     │                   │                   │
     │                   │   4. Compute topic│
     │                   │      (same TOTP)  │
     │                   │                   │
     │                   │   5. GET /topic   │
     │                   │<──────────────────│
     │                   │                   │
     │                   │   6. Return msgs  │
     │                   │──────────────────>│
     │                   │                   │
     │                   │   7. Display to   │
     │                   │      AI/user      │
```

## Components

### Core (shared)

- `core/totp.js` - JavaScript TOTP implementation
- `core/TopicGenerator.kt` - Kotlin TOTP implementation
- `core/test-vectors.json` - Cross-implementation verification

### Plugin (computer side)

- `plugin/commands/inbox.md` - Main `/inbox` command
- `plugin/commands/inbox-setup.md` - Pairing flow
- `plugin/scripts/hardware-id.js` - Hardware fingerprinting
- `plugin/scripts/generate-secret.js` - Secret generation
- `plugin/scripts/generate-qr.js` - QR code generation
- `plugin/scripts/fetch-inbox.js` - ntfy.sh retrieval

### Android App

- `SetupActivity` - QR scanner for pairing
- `ShareActivity` - Receives shares, posts to ntfy.sh
- `TopicGenerator` - TOTP implementation (must match JS exactly)
- `SecureConfig` - Android Keystore wrapper
- `ExpirationChecker` - Auto-wipe on expiration

## Pairing Flow

```
1. User runs `/inbox setup`
2. Computer generates hardware fingerprint
3. Computer generates random entropy
4. Computer creates secret: HMAC(hardware, entropy + timestamp)
5. Computer saves config locally
6. Computer displays QR #1: APK download link
7. User installs app
8. Computer displays QR #2: {secret, expires, window, server}
9. User scans QR with app
10. App stores in Android Keystore
11. Pairing complete
```

## Expiration Behavior

### Phone
1. Before each send, check `expires_at < now`
2. If expired: show error, delete all stored data, return to setup screen
3. No grace period, hard cutoff

### Computer
1. Before each fetch, check `expires_at < now`
2. If expired: show error, prompt re-setup
3. Config remains (user can re-pair same hardware)

## QR Code Payloads

### QR #1: APK Download
```
https://github.com/USER/share-to-inbox/releases/latest/download/share-to-inbox.apk
```

### QR #2: Pairing Data
```json
{
  "s": "abc123...",     // secret (64 hex chars)
  "e": 1752710400,      // expires (unix timestamp)
  "w": 21600,           // window (seconds)
  "u": "https://ntfy.sh" // server URL
}
```

Compact encoding to fit in QR code. No identifiers.

## Self-Hosting

For maximum privacy, users can run their own ntfy server:

```bash
docker run -p 80:80 binwiederhier/ntfy serve
```

Then set `server` in config to their own URL.

## Implementation Notes

### Topic Computation (Critical)

Both JS and Kotlin MUST produce identical output. The algorithm:

1. Convert window index to 8-byte big-endian buffer
2. Convert hex secret to bytes
3. HMAC-SHA256(secret_bytes, window_bytes)
4. Convert result to hex
5. Take first 32 characters

Test vectors in `core/test-vectors.json` verify correctness.

### Key Evaporation

Phone implementation MUST NOT:
- Log topics
- Store topic history
- Keep "last sent" records
- Write anything to disk except the pairing config

The topic is computed in memory, used, and the variable goes out of scope.

### Secure Storage

- **Android**: Use EncryptedSharedPreferences or Keystore
- **Computer**: File permissions (600), optionally encrypt with user key

## Version History

- **0.1.0** - Initial architecture
