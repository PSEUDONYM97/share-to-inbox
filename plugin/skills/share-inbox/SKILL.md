---
name: share-inbox
description: Use when user mentions phone inbox, phone sharing, checking messages from phone, or setting up phone-to-AI sharing. Provides knowledge about the share-to-inbox system.
---

# Share-to-Inbox System Knowledge

## Overview

Share-to-Inbox is a secure bridge between the user's Android phone and their AI. It uses time-rotating topics, hardware-bound secrets, and ephemeral storage.

## When to Use

- User says "check my inbox" or "anything from my phone"
- User wants to set up phone sharing
- User asks about the security model
- User has issues with pairing or messages

## Key Concepts

### How It Works

1. **Pairing**: One-time QR scan connects phone to computer
2. **Sharing**: Phone computes topic, POSTs to ntfy.sh, forgets
3. **Retrieving**: Computer computes same topic, fetches, displays

### Security Properties

- **TOTP topics**: Change every 6 hours, appear random
- **Hardware binding**: Secret tied to computer hardware
- **Ephemeral**: Messages expire in 12h, topics in 6h
- **Zero identifiers**: No usernames, no device IDs
- **Key evaporation**: Phone never stores computed topics

## Commands

| Command | Purpose |
|---------|---------|
| `/inbox` | Check for messages |
| `/inbox setup` | Start pairing flow |
| `/inbox status` | Show pairing info |
| `/inbox rotate` | Emergency re-pair |

## Common Issues

### "No messages"

- Check if pairing is expired (`/inbox status`)
- Messages expire after 12 hours
- Verify phone app shows "Paired"

### "Pairing expired"

Run `/inbox setup` to create new pairing. Old secret is invalidated.

### "Topic mismatch"

Clock drift between phone and computer. Check both device times.
We fetch current + previous window to handle minor drift.

## Technical Details

### Topic Computation

```
topic = HMAC-SHA256(secret, window_index)
        .hex()
        .substring(0, 32)

window_index = floor(unix_seconds / 21600)  // 6-hour windows
```

### Config Location

Computer: `~/.share-to-inbox/config.json` (or plugin directory)

### Self-Hosting

Users can run their own ntfy server for maximum privacy.

## Responding to Users

When user checks inbox:
1. Compute current + previous topics
2. Fetch from both (handles clock skew)
3. Deduplicate messages
4. Display with timestamps
5. Offer to act on content (summarize, etc.)

When setting up:
1. Show APK QR first
2. Wait for install confirmation
3. Ask pairing duration
4. Generate hardware-bound secret
5. Show pairing QR
6. Confirm success
