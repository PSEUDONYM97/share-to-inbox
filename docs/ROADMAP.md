# Roadmap

## Current: Phase 2 - Computer Side
- [ ] Hardware ID gathering (Windows/Mac/Linux)
- [ ] Secret generation with hardware binding
- [ ] QR code generation (terminal + image)
- [ ] `/inbox setup` full implementation
- [ ] `/inbox` message retrieval
- [ ] `/inbox status` and `/inbox rotate`

## Phase 3: Android App
- [ ] Android Studio project setup
- [ ] QR scanner for pairing
- [ ] Share receiver activity
- [ ] Secure storage (Keystore)
- [ ] TOTP topic computation
- [ ] POST to ntfy.sh

## Phase 4: Security Hardening
- [ ] Key evaporation verification
- [ ] Auto-wipe on expiration
- [ ] ProGuard obfuscation
- [ ] Security audit
- [ ] No-logging verification

## Phase 5: Distribution
- [ ] Signed APK releases
- [ ] GitHub Actions CI/CD
- [ ] Full documentation
- [ ] Demo video
- [ ] External testing

---

## Phase 6: Local Inbox Server (Priority Enhancement)

### Overview
A local HTTP server that provides an alternative to ntfy.sh. Run your own inbox endpoint on your computer.

### Components
- [ ] Local HTTP server (Node.js, single file)
- [ ] TOTP-authenticated POST endpoint
- [ ] In-memory message storage (ephemeral)
- [ ] CLI inbox viewer (`inbox-server view`)
- [ ] Optional web UI viewer (localhost)
- [ ] AI can query local server instead of ntfy.sh

### Benefits
- **Zero external dependencies** - No ntfy.sh required
- **True zero-knowledge** - Messages never leave your network
- **Direct phone-to-computer** - If on same WiFi
- **Multiple entry points** - Phone, browser extension, scripts
- **Local viewer** - See inbox without AI

### Architecture
```
Phone ──────┐
Browser ────┼──► localhost:PORT/share ──► Local Inbox ──► Viewer / AI
Scripts ────┘    (TOTP auth)              (in-memory)
```

### Endpoint
```
POST http://localhost:PORT/share
X-Topic: <computed TOTP topic>
Body: content to share

GET http://localhost:PORT/inbox
X-Topic: <computed TOTP topic>
Returns: JSON array of messages
```

---

## Future Ideas (Post-v1)

### Multiple Inboxes
Support multiple independent inboxes:
- Work inbox / personal inbox
- Different AI assistants
- Team shared inbox

### Browser Extension
Chrome/Firefox extension that adds "Share to Inbox" to right-click menu.

### Watch App
Wear OS companion for voice notes directly from watch.

### End-to-End Encryption
Optional encryption layer on top of ntfy.sh transport:
- Encrypt content with derived key
- Even ntfy.sh can't read messages
- Slight complexity increase

### Webhook Integration
Fire webhooks when messages arrive:
- Integrate with other tools
- Trigger automations
- Notifications via other channels
