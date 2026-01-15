---
description: Show pairing status and expiration info
allowed-tools: ["Read", "Bash"]
---

# Inbox Status

Display the current pairing status.

## Instructions

1. Read the config:
```bash
cat ~/.share-to-inbox/config.json
```

2. If config doesn't exist, tell user to run `/share-to-inbox:setup`

3. Parse and display:
   - Pairing status (active/expired)
   - Days remaining until expiration
   - Window size (hours)
   - Server URL
   - Secret (masked: first 4 and last 4 chars only)

4. If expired or expiring soon (<7 days), suggest running `/share-to-inbox:setup` to re-pair
