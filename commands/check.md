---
description: Check your Android inbox for shared content
allowed-tools: ["Read", "Bash"]
---

# Check Inbox

Fetch and display messages shared from your Android phone.

## Instructions

1. Run the fetch script:
```bash
cd "${CLAUDE_PLUGIN_ROOT}" && node scripts/fetch-inbox.js
```

2. The script will:
   - Show pairing status and expiration
   - Fetch from current and previous TOTP windows
   - Auto-download any attachments
   - Auto-decode images to temp files
   - Display messages with type icons

3. After displaying messages, offer to help:
   - Summarize long text
   - Fetch and summarize URLs
   - View decoded images (read the temp file path shown)
