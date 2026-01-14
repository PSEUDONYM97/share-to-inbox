---
description: "Check your phone inbox for shared content"
allowed-tools: ["Read", "Bash"]
argument-hint: "[status|rotate] - optional action"
---

# Phone Inbox

Check for content shared from your phone.

## Arguments
$ARGUMENTS

## Instructions

### Step 1: Check for Subcommand

If argument is "status" or "rotate", redirect to the appropriate command:
- "status" → Run /inbox-status instructions
- "rotate" → Run /inbox-setup with rotation mode

If no argument or "check", continue to fetch messages.

### Step 2: Read Config

Read the inbox config:
```bash
cat "C:/Users/jwill/.share-to-inbox/config.json" 2>/dev/null
```

If config doesn't exist, tell the user:
> No inbox configured. Run `/inbox setup` to pair your phone.

### Step 3: Check Expiration

Parse the config and check if expired:
- If `expiresAtTimestamp < Date.now()`, tell user pairing is expired
- Show how long ago it expired
- Suggest running `/inbox setup` to re-pair

### Step 4: Fetch Messages

Run the fetch script:
```bash
cd "C:/Users/jwill/Projects/android/share-to-inbox" && node -e "
import { readFile } from 'fs/promises';
import { fetchInbox, formatInboxForDisplay } from './plugin/scripts/fetch-inbox.js';

const config = JSON.parse(await readFile('C:/Users/jwill/.share-to-inbox/config.json', 'utf8'));
const messages = await fetchInbox(config);
const result = formatInboxForDisplay(messages);

console.log(JSON.stringify(result, null, 2));
"
```

### Step 5: Display Results

**If no messages:**
> **Inbox Empty**
>
> No messages in the last 24 hours.
>
> Share something from your phone and it will appear here!

**If messages found:**
> **Inbox** (X messages)
>
> ### [Timestamp 1]
> [Message content]
>
> ---
>
> ### [Timestamp 2]
> [Message content]
>
> ---
>
> Would you like me to:
> - Summarize any of these?
> - Take action on a link?
> - Save something to a file?

### Step 6: Offer Actions

After displaying messages, proactively offer to help:
- If a message contains a URL, offer to fetch/summarize it
- If a message is long text, offer to summarize
- If a message looks like notes, offer to format/organize

### Status Information

At the end, optionally show:
> *Pairing expires: [DATE] ([X] days remaining)*
> *Current topic window expires: [TIME]*
