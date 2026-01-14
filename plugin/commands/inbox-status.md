---
description: "Show inbox pairing status and configuration"
allowed-tools: ["Read", "Bash"]
---

# Inbox Status

Show current pairing status and configuration.

## Instructions

### Step 1: Read Config

Read the inbox config:
```bash
cat "C:/Users/jwill/.share-to-inbox/config.json" 2>/dev/null
```

If config doesn't exist:
> **No Inbox Configured**
>
> Run `/inbox setup` to pair your phone.

### Step 2: Parse and Display Status

Parse the config and calculate status:

```javascript
const config = JSON.parse(configContent);
const now = Date.now();
const expiresAt = config.expiresAtTimestamp || new Date(config.expiresAt).getTime();
const msRemaining = expiresAt - now;
const daysRemaining = Math.ceil(msRemaining / (24 * 60 * 60 * 1000));
const isExpired = msRemaining <= 0;

// Mask the secret
const maskedSecret = config.secret.substring(0, 4) + '...' + config.secret.substring(config.secret.length - 4);
```

### Step 3: Display Status

**If expired:**
> **Inbox Status: EXPIRED**
>
> | Property | Value |
> |----------|-------|
> | Status | Expired |
> | Expired | [DATE] ([X] days ago) |
> | Server | [URL] |
>
> Run `/inbox setup` to create a new pairing.

**If active:**
> **Inbox Status: Active**
>
> | Property | Value |
> |----------|-------|
> | Status | Paired |
> | Expires | [DATE] |
> | Days Remaining | [X] days |
> | Window Size | [X] hours |
> | Server | [URL] |
> | Secret | [MASKED] |
> | Hardware Bound | Yes |
>
> *Your inbox is working. Share content from your phone and say "check my inbox" to retrieve it.*

### Step 4: Show Warning if Expiring Soon

If less than 7 days remaining:
> **Warning:** Your pairing expires in [X] days. Consider running `/inbox setup` to renew.

### Step 5: Verify Hardware (Optional)

If user asks for detailed status, run hardware verification:
```bash
cd "C:/Users/jwill/Projects/android/share-to-inbox" && node -e "
import { verifyFingerprint } from './plugin/scripts/hardware-id.js';
import { readFile } from 'fs/promises';

const config = JSON.parse(await readFile('C:/Users/jwill/.share-to-inbox/config.json', 'utf8'));
const valid = verifyFingerprint(config.hardwareFingerprint);
console.log('Hardware verification:', valid ? 'PASS' : 'FAIL');
"
```

If hardware verification fails:
> **Warning:** Hardware fingerprint doesn't match. This config was created on a different computer.
> The inbox will not work on this machine. Run `/inbox setup` to create a new pairing for this computer.
