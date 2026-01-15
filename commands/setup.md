---
description: Set up pairing with your Android phone
allowed-tools: ["Read", "Bash", "AskUserQuestion"]
---

# Inbox Setup

Generate a new pairing secret and display QR code for your phone.

## Instructions

1. Generate a new secret (90-day expiration):
```bash
cd "${CLAUDE_PLUGIN_ROOT}" && node scripts/generate-secret.js
```

2. Display the pairing QR code:
```bash
cd "${CLAUDE_PLUGIN_ROOT}" && node scripts/generate-qr.js --pairing
```

3. Tell the user:
   - Open "Share to Inbox" app on your phone
   - Tap "Scan QR Code"
   - Point camera at the terminal QR code
   - Or tap "Enter Manually" and paste the JSON shown

4. Security notes:
   - QR codes are NEVER saved to disk
   - Secret is hardware-bound to this computer
   - Pairing expires after 90 days
   - Topics rotate every 6 hours via TOTP
