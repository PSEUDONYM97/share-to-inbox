#!/usr/bin/env node
/**
 * Fetch messages from ntfy.sh inbox
 *
 * Handles:
 * - Plain text messages
 * - URLs
 * - Images (base64 encoded, stored as attachments)
 * - Automatic attachment downloading
 * - Image decoding to temp files
 */

import { getRetrievalTopics, getWindowExpiry } from '../../core/totp.js';
import * as fs from 'fs';
import * as os from 'os';
import * as path from 'path';

/**
 * Fetch a single ntfy.sh topic
 */
async function fetchTopic(server, topic, since = '24h') {
  const url = `${server}/${topic}/json?poll=1&since=${since}`;

  try {
    const response = await fetch(url);
    if (!response.ok) {
      if (response.status === 404) return [];
      throw new Error(`HTTP ${response.status}`);
    }

    const text = await response.text();
    if (!text.trim()) return [];

    return text.trim().split('\n')
      .map(line => {
        try { return JSON.parse(line); }
        catch { return null; }
      })
      .filter(msg => msg && msg.event === 'message');
  } catch (err) {
    console.error(`Error fetching ${topic}:`, err.message);
    return [];
  }
}

/**
 * Download attachment content from ntfy.sh
 */
async function downloadAttachment(attachmentUrl) {
  try {
    const response = await fetch(attachmentUrl);
    if (!response.ok) return null;
    return await response.text();
  } catch {
    return null;
  }
}

/**
 * Decode base64 image and save to temp file
 * Returns the file path or null on failure
 */
function decodeAndSaveImage(content, messageId) {
  const match = content.match(/^\[IMAGE:data:image\/(\w+);base64,(.+)\]$/);
  if (!match) return null;

  const format = match[1]; // jpeg, png, etc.
  const base64Data = match[2];

  try {
    const buffer = Buffer.from(base64Data, 'base64');
    const tempDir = path.join(os.tmpdir(), 'share-to-inbox');

    // Create temp dir if needed
    if (!fs.existsSync(tempDir)) {
      fs.mkdirSync(tempDir, { recursive: true });
    }

    const filePath = path.join(tempDir, `image-${messageId}.${format}`);
    fs.writeFileSync(filePath, buffer);
    return filePath;
  } catch {
    return null;
  }
}

/**
 * Determine message type from content
 */
function classifyContent(content) {
  if (content.startsWith('[IMAGE:data:image/')) {
    return 'image';
  }
  if (/^https?:\/\/\S+$/.test(content.trim())) {
    return 'url';
  }
  return 'text';
}

/**
 * Process a raw ntfy message into structured format
 */
async function processMessage(msg) {
  let content = msg.message;
  let type = 'text';
  let filePath = null;

  // If there's an attachment, download it first
  if (msg.attachment && msg.attachment.url) {
    const attachmentContent = await downloadAttachment(msg.attachment.url);
    if (attachmentContent) {
      content = attachmentContent;
    }
  }

  // Classify the content
  type = classifyContent(content);

  // Handle images specially
  if (type === 'image') {
    filePath = decodeAndSaveImage(content, msg.id);
    if (filePath) {
      content = `Image saved to: ${filePath}`;
    } else {
      content = '[Image decode failed]';
    }
  }

  return {
    id: msg.id,
    time: new Date(msg.time * 1000),
    timestamp: msg.time,
    type,
    content,
    filePath,
    hasAttachment: !!msg.attachment
  };
}

/**
 * Fetch and process all inbox messages
 */
export async function fetchInbox(config) {
  const { secret, server, windowSeconds } = config;
  const topics = getRetrievalTopics(secret, windowSeconds);

  // Fetch from both current and previous window topics
  const rawMessages = [];
  for (const topic of topics) {
    const messages = await fetchTopic(server, topic, '24h');
    rawMessages.push(...messages);
  }

  // Process all messages (download attachments, decode images)
  const processed = await Promise.all(rawMessages.map(processMessage));

  // Deduplicate by ID and sort newest first
  const seen = new Set();
  return processed
    .filter(msg => {
      if (seen.has(msg.id)) return false;
      seen.add(msg.id);
      return true;
    })
    .sort((a, b) => b.timestamp - a.timestamp);
}

/**
 * Format messages for display
 */
export function formatInboxForDisplay(messages) {
  if (messages.length === 0) {
    return {
      count: 0,
      summary: 'No messages in inbox.',
      messages: []
    };
  }

  const byType = {
    text: messages.filter(m => m.type === 'text'),
    url: messages.filter(m => m.type === 'url'),
    image: messages.filter(m => m.type === 'image')
  };

  const summary = [
    `${messages.length} message${messages.length === 1 ? '' : 's'} in inbox`,
    byType.text.length > 0 ? `${byType.text.length} text` : null,
    byType.url.length > 0 ? `${byType.url.length} URL${byType.url.length > 1 ? 's' : ''}` : null,
    byType.image.length > 0 ? `${byType.image.length} image${byType.image.length > 1 ? 's' : ''}` : null
  ].filter(Boolean).join(' | ');

  return {
    count: messages.length,
    summary,
    byType,
    messages: messages.map(m => ({
      id: m.id,
      time: m.time.toLocaleString(),
      type: m.type,
      content: m.content,
      filePath: m.filePath
    }))
  };
}

/**
 * Get inbox status information
 */
export function getInboxStatus(config) {
  const windowExpiry = getWindowExpiry(config.windowSeconds);
  const expiresAt = new Date(config.expiresAt || config.expiresAtTimestamp);
  const now = new Date();

  const msUntilExpiry = expiresAt.getTime() - now.getTime();
  const daysRemaining = Math.ceil(msUntilExpiry / (24 * 60 * 60 * 1000));

  return {
    paired: true,
    expired: msUntilExpiry <= 0,
    expiresAt: expiresAt.toLocaleDateString(),
    daysRemaining: Math.max(0, daysRemaining),
    windowExpiry: windowExpiry.toLocaleTimeString(),
    server: config.server,
    windowHours: config.windowSeconds / 3600
  };
}

// ============================================================
// CLI Interface
// ============================================================
if (import.meta.url === `file://${process.argv[1]}` || process.argv[1]?.endsWith('fetch-inbox.js')) {
  (async () => {
    // Load config
    const configPath = path.join(os.homedir(), '.share-to-inbox', 'config.json');
    let config;

    try {
      config = JSON.parse(fs.readFileSync(configPath, 'utf8'));
    } catch (e) {
      console.error('ERROR: No config found at', configPath);
      console.error('Run /inbox-setup first to pair with your phone.');
      process.exit(1);
    }

    // Show status
    const status = getInboxStatus(config);
    console.log('╔══════════════════════════════════════════════════════════╗');
    console.log('║                    SHARE TO INBOX                        ║');
    console.log('╚══════════════════════════════════════════════════════════╝');
    console.log(`  Pairing expires: ${status.expiresAt} (${status.daysRemaining} days)`);
    console.log(`  Topic window: ${status.windowHours}h (rotates at ${status.windowExpiry})`);
    console.log('');

    // Fetch messages
    console.log('Fetching messages...\n');
    const messages = await fetchInbox(config);
    const result = formatInboxForDisplay(messages);

    console.log(`📬 ${result.summary}\n`);

    if (messages.length === 0) {
      console.log('  Share something from your phone to see it here.');
      process.exit(0);
    }

    // Display messages grouped by type
    for (const msg of result.messages) {
      const icon = msg.type === 'image' ? '🖼️' : msg.type === 'url' ? '🔗' : '📝';
      console.log(`${icon} [${msg.time}]`);

      if (msg.type === 'image' && msg.filePath) {
        console.log(`   ${msg.filePath}`);
      } else if (msg.type === 'url') {
        console.log(`   ${msg.content}`);
      } else {
        // Truncate long text for display
        const preview = msg.content.length > 200
          ? msg.content.substring(0, 200) + '...'
          : msg.content;
        console.log(`   ${preview}`);
      }
      console.log('');
    }
  })();
}
