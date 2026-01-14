# Share-to-Inbox

Share anything from your phone to your AI. Zero-knowledge, ephemeral, privacy-first.

## What is this?

A secure bridge between your Android phone's share menu and your AI assistant. See an interesting article? Share it. Voice note idea? Share it. Link to check later? Share it. Then just ask your AI to "check my inbox."

## How it works

1. **Pairing:** Run `/inbox setup` on your computer. Scan two QR codes with your phone - one to install the app, one to pair.

2. **Sharing:** On your phone, share anything and select "Share to Inbox." Done. Zero friction.

3. **Retrieving:** Ask your AI "check my inbox" or run `/inbox`. Your content appears.

## Security Model

- **Time-rotating topics** - Where your messages go changes every 6 hours
- **Hardware-bound secrets** - Tied to your specific computer
- **Ephemeral storage** - Messages expire in 12 hours, nothing persists
- **Zero identifiers** - No usernames, no device IDs, pure cryptographic randomness
- **Key evaporation** - Phone forgets each topic immediately after use

See [SECURITY.md](docs/SECURITY.md) for the full threat model.

## Installation

### Computer (Claude Code Plugin)

```bash
# Coming in Phase 2
claude plugins install share-to-inbox
```

### Android

Download from [GitHub Releases](../../releases) or scan the QR during setup.

## Usage

### Setup (one time)
```
You: "Set up my phone inbox"
AI: [Shows QR code for app download]
You: [Install app]
AI: [Shows pairing QR]
You: [Scan with app]
AI: "Paired until April 14, 2026"
```

### Daily use
```
You: [Share something on phone]
... later ...
You: "Check my inbox"
AI: "You have 2 items:
     - Article about quantum computing
     - Link to that repo you found
     Want me to summarize the article?"
```

### Commands

| Command | Description |
|---------|-------------|
| `/inbox` | Check for new messages |
| `/inbox setup` | Start pairing flow |
| `/inbox status` | Show pairing info |
| `/inbox rotate` | Emergency re-pair |

## Self-Hosting

For maximum privacy, run your own ntfy server:

```bash
docker run -p 80:80 binwiederhier/ntfy serve
```

Then during setup, specify your server URL.

## Project Structure

```
share-to-inbox/
├── plugin/           # Claude Code plugin
│   ├── commands/     # Slash commands
│   ├── skills/       # AI knowledge
│   └── scripts/      # Utilities
├── android/          # Android app
├── core/             # Shared crypto
│   ├── totp.js       # JS implementation
│   ├── TopicGenerator.kt  # Kotlin implementation
│   └── test-vectors.json  # Cross-implementation tests
└── docs/
    ├── ARCHITECTURE.md
    ├── SECURITY.md
    └── SELF-HOSTING.md
```

## Development

### Phase 1: Foundation (current)
- [x] Project structure
- [x] TOTP algorithm (JS + Kotlin)
- [x] Test vectors
- [x] Architecture docs

### Phase 2: Computer Side
- [ ] Hardware ID gathering
- [ ] Secret generation
- [ ] QR generation
- [ ] Plugin commands

### Phase 3: Android App
- [ ] QR scanner
- [ ] Share receiver
- [ ] Secure storage

### Phase 4: Security Hardening
- [ ] Key evaporation
- [ ] Auto-wipe on expiry
- [ ] Security audit

### Phase 5: Distribution
- [ ] Signed APK releases
- [ ] Full documentation
- [ ] External testing

## License

MIT

## Credits

Built for privacy-conscious devs who want to send stuff to their AI without leaving a trail.
