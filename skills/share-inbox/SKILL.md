---
name: share-inbox
description: Use when user mentions phone inbox, phone sharing, checking messages from phone, or wants to set up phone-to-AI sharing. Provides knowledge about the secure share-to-inbox system.
---

# Share-to-Inbox System Knowledge

## Overview

Share-to-Inbox is a secure, ephemeral bridge between the user's Android phone and their AI. It uses TOTP-style rotating topics, hardware-bound secrets, and zero-knowledge design.

## When to Use

- User says "check my inbox" or "anything from my phone"
- User wants to set up phone sharing
- User asks about the security model
- User has issues with pairing or messages
- User mentions "share to inbox" or "phone inbox"

## Commands

| Command | Purpose |
|---------|---------|
| `/inbox` | Check for new messages |
| `/inbox setup` | Start pairing flow (QR codes) |
| `/inbox status` | Show pairing info and expiration |

## How It Works

### Pairing (One-Time Setup)

1. User runs `/inbox setup`
2. Display QR #1 → APK download link
3. User installs app on phone
4. Ask pairing duration (30/90/365 days)
5. Generate hardware-bound secret
6. Display QR #2 → Pairing data for phone
7. Save config locally
8. Done - phone is paired

### Daily Sharing

Phone side:
1. User shares any content
2. App computes TOTP topic: `HMAC-SHA256(secret, floor(time/21600))`
3. POSTs to `ntfy.sh/[topic]`
4. Forgets the topic immediately (evaporation)

Computer side:
1. User says "check my inbox"
2. Read config, compute same TOTP topic
3. Fetch from ntfy.sh
4. Display messages
5. Offer to take action (summarize, etc.)

### Security Properties

| Property | How |
|----------|-----|
| Rotating topics | Change every 6 hours (TOTP) |
| Hardware binding | Secret tied to machine's CPU/disk/MAC |
| Ephemeral messages | Expire in 12 hours on ntfy.sh |
| Zero identifiers | No usernames, pure hash topics |
| Key evaporation | Phone never stores computed topics |
| Auto-expiration | Pairing dies after chosen duration |

## Config Location

```
C:/Users/jwill/.share-to-inbox/config.json
```

Contains:
- `secret`: The shared secret (64 hex chars)
- `hardwareFingerprint`: Hash of hardware IDs
- `expiresAt`: ISO timestamp
- `expiresAtTimestamp`: Unix timestamp (ms)
- `windowSeconds`: TOTP window (21600 = 6 hours)
- `server`: ntfy.sh URL

## Troubleshooting

### "No messages"

Check:
1. Is pairing expired? Run `/inbox status`
2. Messages expire after 12 hours
3. Did the share actually send? (Phone should show "Sent!")
4. Clock sync - phone and computer should be within a few minutes

### "Pairing expired"

Run `/inbox setup` to create new pairing. The old secret becomes useless.

### "Hardware mismatch"

The config was created on a different computer. Hardware-bound secrets only work on the originating machine.

### "Topic mismatch"

Usually clock drift. We check current + previous window to handle minor drift (up to 6 hours).

## Scripts

Located in `scripts/`:

- `hardware-id.js` - Cross-platform hardware fingerprinting
- `generate-secret.js` - Hardware-bound secret generation
- `generate-qr.js` - QR code generation
- `fetch-inbox.js` - Message retrieval from ntfy.sh

## TOTP Algorithm

Both JS and Kotlin must produce identical output:

```javascript
function generateTopic(secret, windowIndex) {
  // Window index as 8-byte big-endian
  const windowBuffer = Buffer.alloc(8);
  windowBuffer.writeBigUInt64BE(BigInt(windowIndex));

  // HMAC-SHA256
  const hmac = createHmac('sha256', Buffer.from(secret, 'hex'));
  hmac.update(windowBuffer);

  // First 32 chars of hex output
  return hmac.digest('hex').substring(0, 32);
}

const windowIndex = Math.floor(Date.now() / 1000 / 21600);
const topic = generateTopic(secret, windowIndex);
```

## Responding to Users

### When checking inbox

1. Read config from `~/.share-to-inbox/config.json`
2. Check if expired - if so, tell user to re-pair
3. Compute current + previous TOTP topics
4. Fetch from both (handles clock skew)
5. Deduplicate by message ID
6. Display with timestamps
7. Offer to act on content

### When setting up

1. Show APK download QR
2. Wait for install confirmation
3. Ask pairing duration
4. Generate hardware-bound secret
5. Save config
6. Show pairing QR
7. Confirm success

### Proactive help

After showing messages:
- URLs → Offer to fetch/summarize
- Long text → Offer to summarize
- Notes → Offer to organize
- Code → Offer to explain/save

## Future: Local Inbox Server

Phase 6 will add a local HTTP endpoint as an alternative to ntfy.sh:
- Direct phone-to-computer on same network
- Zero external dependencies
- Local inbox viewer
- Multiple entry points (phone, browser, scripts)
