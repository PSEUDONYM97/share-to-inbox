---
description: "Set up phone-to-AI sharing with QR pairing"
allowed-tools: ["Read", "Bash", "Write", "AskUserQuestion"]
---

# Inbox Setup

Pair your phone with your AI for secure content sharing.

## Instructions

### Step 1: Show APK Download QR

First, display the QR code for downloading the Android app.

Run this command to generate the QR:
```bash
cd "C:/Users/jwill/Projects/android/share-to-inbox" && node -e "
const url = 'https://github.com/jaredwilliam/share-to-inbox/releases/latest/download/share-to-inbox.apk';
console.log('');
console.log('═'.repeat(60));
console.log('  STEP 1: Download the App');
console.log('═'.repeat(60));
console.log('');
console.log('Scan this QR code with your phone to download the app:');
console.log('');
console.log('URL: ' + url);
console.log('');
console.log('(QR code display requires: npm install qrcode-terminal)');
console.log('');
"
```

Tell the user:
> **Step 1: Download the App**
>
> Scan the QR code above with your phone's camera to download the Share-to-Inbox app.
>
> Or download directly from:
> https://github.com/jaredwilliam/share-to-inbox/releases
>
> Let me know when you've installed the app.

### Step 2: Ask for Pairing Duration

Use AskUserQuestion to ask how long the pairing should last:

```
Question: "How long should this pairing last?"
Options:
- 30 days (Higher security, more frequent re-pairing)
- 90 days (Recommended balance of security and convenience)
- 1 year (Maximum convenience)
```

### Step 3: Generate Hardware-Bound Secret

Run the secret generation script with the chosen duration:

```bash
cd "C:/Users/jwill/Projects/android/share-to-inbox" && node plugin/scripts/generate-secret.js [DAYS]
```

Capture the output - you'll need:
- The full config JSON (for saving)
- The QR payload (for the phone)

### Step 4: Save Config

Save the config to the user's Claude directory:

```bash
# Create directory if needed
mkdir -p ~/.share-to-inbox

# Save config (use the JSON from Step 3)
```

Write the config to: `C:/Users/jwill/.share-to-inbox/config.json`

### Step 5: Display Pairing QR

Display the QR code containing the pairing data:

```
═════════════════════════════════════════════════════════════
  STEP 2: Pair Your Phone
═════════════════════════════════════════════════════════════

Open the Share-to-Inbox app and scan this QR code:

[QR CODE or JSON payload]

Pairing data (if QR doesn't work, enter manually in app):
{"s":"[SECRET]","e":[EXPIRY],"w":21600,"u":"https://ntfy.sh"}
```

### Step 6: Confirm Success

Tell the user:

> **Pairing Complete!**
>
> Your phone is now paired until [EXPIRY DATE].
>
> **To share content:**
> 1. Select any text, link, or content on your phone
> 2. Tap Share
> 3. Select "Share to Inbox"
>
> **To check your inbox:**
> Just say "check my inbox" or run `/inbox`
>
> **Security notes:**
> - Topics rotate every 6 hours automatically
> - Messages expire after 12 hours
> - Your secret is bound to this computer's hardware

### Error Handling

If hardware ID gathering fails:
> I couldn't gather enough hardware identifiers for a secure pairing.
> This might happen on virtual machines or systems with restricted access.
>
> You can try running with administrator privileges, or use the manual setup mode.

If config already exists:
> You already have an active pairing that expires on [DATE].
> Would you like to:
> - Keep the existing pairing
> - Create a new pairing (invalidates the old one)
