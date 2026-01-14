# Security Model

## Design Philosophy

Share-to-Inbox is built on one principle: **minimize attack surface through ephemerality**.

Rather than building complex access controls, we make data disappear. Topics rotate. Messages expire. Keys evaporate. There's nothing to steal because nothing persists.

## Threat Model

### Assets We Protect

1. **Message content** - What you're sharing
2. **Sharing patterns** - When and how often you share
3. **Identity linkage** - Connecting shares to a person

### Threat Actors

| Actor | Capability | Mitigations |
|-------|------------|-------------|
| **Passive network observer** | Sees encrypted HTTPS traffic | TLS encryption, no plaintext |
| **ntfy.sh operator** | Sees messages during 12h window | Rotating topics, self-host option |
| **Phone thief** | Physical access to device | Expiration auto-wipe, no history |
| **Config file thief** | Gets computer config | Hardware-bound secret |
| **Forensic analyst** | Deep device analysis | Key evaporation, no logs |

### What We DON'T Protect Against

| Threat | Why Not |
|--------|---------|
| **Device malware** | If attacker owns your device, all security is meaningless |
| **AI provider** | You're deliberately sending content to your AI - that's the feature |
| **Targeted government actor** | Overkill for personal productivity tool |
| **Quantum computers** | Not a realistic near-term threat |

## Security Properties

### 1. Topic Unlinkability

Topics are pure HMAC-SHA256 output with no prefix or identifier.

```
Window 1: a3f8c91b2d4e6f8a1c3b5d7e9f0a2b4c
Window 2: 7e2f9c4a1b8d3e6f5a0c2b4d6e8f1a3b
Window 3: 1d5e8f2a4b7c9e3f6a1b4c7d0e3f6a9b
```

An observer cannot determine:
- That these topics belong to the same user
- Any pattern in topic generation
- What the next topic will be

### 2. Hardware Binding

The secret is derived from hardware identifiers:

```
hardware_id = SHA256(cpu_id + disk_serial + mac_address + bios_serial)
secret = HMAC-SHA256(hardware_id, random_bytes + timestamp)
```

If an attacker steals your config file:
- They cannot compute the topic without your hardware
- Moving to a new computer breaks the old pairing anyway
- Hardware spoofing requires physical access to original machine

### 3. Key Evaporation

The phone computes topics in memory and immediately discards them:

```kotlin
fun share(content: String) {
    val topic = computeTopic(secret, currentWindow())  // In memory
    post("$server/$topic", content)                     // Send
    // topic goes out of scope - never stored
}
```

Phone storage contains ONLY:
- The secret (until expiration)
- Server URL
- Expiration timestamp
- Window size

It does NOT contain:
- Any computed topics
- Any message history
- Any timestamps of sends
- Any correlation data

### 4. Automatic Expiration

| Data | Lifespan | Location |
|------|----------|----------|
| Messages | 12 hours | ntfy.sh |
| Topic validity | 6 hours | Computed, not stored |
| Pairing | User-defined (30-365 days) | Phone + computer |

When pairing expires:
- Phone deletes all stored data
- Phone returns to "scan to pair" screen
- Computer prompts for re-setup
- No trace remains

### 5. No Logging

The Android app MUST NOT:
- Write to Android logging system (Logcat)
- Use analytics or crash reporting
- Store any history
- Create any files beyond config

This is enforced through code review and ProGuard configuration.

## Attack Scenarios

### Scenario 1: Phone Theft (while paired)

**Attacker has:** Physical phone, bypasses lock screen

**Attacker can:**
- Extract secret from Keystore (difficult but possible)
- Compute current topic
- Read messages in current window (max 6 hours old)
- Post fake messages until expiration

**Attacker cannot:**
- See historical topics (never stored)
- See message history (never stored)
- Extend the pairing (requires computer QR)
- Link to computer identity (no identifiers)

**Mitigation:** Short pairing durations for sensitive users.

### Scenario 2: Phone Theft (after expiration)

**Attacker has:** Physical phone

**Attacker can:** Nothing

**Attacker cannot:**
- Find any secret (deleted)
- Find any history (never existed)
- Prove the app was ever used (no artifacts)

### Scenario 3: Config File Theft

**Attacker has:** Copy of computer's config file

**Attacker can:** See encrypted/hashed secret

**Attacker cannot:**
- Use the secret (hardware-bound)
- Brute force the hardware ID (256-bit entropy)
- Compute topics without the original computer

### Scenario 4: Network Interception

**Attacker has:** Man-in-the-middle position

**Attacker can:**
- See that HTTPS requests go to ntfy.sh
- See request sizes (traffic analysis)

**Attacker cannot:**
- See message content (TLS encrypted)
- See topic names (TLS encrypted)
- Correlate topics over time (they rotate)

### Scenario 5: ntfy.sh Compromise

**Attacker has:** Full access to ntfy.sh server

**Attacker can:**
- Read all messages in current windows
- See topic names (but they're meaningless)
- Correlate traffic patterns (IP addresses)

**Attacker cannot:**
- See historical messages (expired)
- Predict future topics (no pattern)
- Identify users (no identifiers in messages)

**Mitigation:** Self-host ntfy for maximum privacy.

## Recommendations

### For Normal Use
- 90-day pairing is reasonable
- Public ntfy.sh is fine
- Default 6-hour windows

### For Higher Security
- 30-day pairing
- Self-hosted ntfy
- Shorter windows (1-2 hours)
- Delete config when not traveling

### For Maximum Paranoia
- 7-day pairing
- Self-hosted ntfy on private network
- 1-hour windows
- Regenerate pairing frequently
- Use dedicated device

## Known Limitations

1. **Trust in ntfy.sh** - They could log everything during the 12h window. Self-host to eliminate.

2. **Hardware ID stability** - If hardware components change (disk replacement), pairing breaks. This is a feature, not a bug.

3. **Clock synchronization** - Phone and computer must be within a few minutes. We check current + previous window to handle small drift.

4. **AI context persistence** - Your AI may remember message content in its context window. This is outside our threat model.

## Audit Checklist

Before each release:

- [ ] No logging calls in Android code
- [ ] No analytics/tracking SDKs
- [ ] No network calls except to configured server
- [ ] No file writes except config
- [ ] ProGuard obfuscation enabled
- [ ] Test vectors pass on both platforms
- [ ] Expiration wipe tested
- [ ] No identifiers in any payload

## Reporting Security Issues

If you find a security vulnerability, please report it responsibly. Open a private security advisory on GitHub rather than a public issue.
