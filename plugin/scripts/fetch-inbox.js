#!/usr/bin/env node
/**
 * Fetch messages from ntfy.sh inbox
 *
 * Retrieves messages from the current and previous time windows
 * to handle clock skew gracefully.
 */

import { getCurrentTopic, getRetrievalTopics, getWindowExpiry } from '../../core/totp.js';

/**
 * Fetch messages for a single topic
 *
 * @param {string} server - ntfy server URL
 * @param {string} topic - Topic to fetch
 * @param {string} since - Time filter (e.g., "24h", "12h")
 * @returns {Promise<Array>} Messages
 */
async function fetchTopic(server, topic, since = '24h') {
  const url = `${server}/${topic}/json?poll=1&since=${since}`;

  try {
    const response = await fetch(url, {
      method: 'GET',
      headers: {
        'Accept': 'application/json'
      }
    });

    if (!response.ok) {
      if (response.status === 404) {
        return []; // No messages
      }
      throw new Error(`HTTP ${response.status}: ${response.statusText}`);
    }

    const text = await response.text();
    if (!text.trim()) return [];

    // Each line is a separate JSON object
    const messages = text.trim().split('\n')
      .map(line => {
        try {
          return JSON.parse(line);
        } catch {
          return null;
        }
      })
      .filter(msg => msg && msg.event === 'message');

    return messages;
  } catch (err) {
    // Network errors, etc.
    console.error(`Error fetching ${topic}:`, err.message);
    return [];
  }
}

/**
 * Fetch messages from inbox using TOTP topics
 *
 * Fetches from both current and previous window to handle clock skew.
 *
 * @param {object} config - Inbox configuration
 * @returns {Promise<Array>} Deduplicated messages sorted by time
 */
export async function fetchInbox(config) {
  const { secret, server, windowSeconds } = config;

  // Get topics for current and previous windows
  const topics = getRetrievalTopics(secret, windowSeconds);

  // Fetch from both topics in parallel
  const results = await Promise.all(
    topics.map(topic => fetchTopic(server, topic, '24h'))
  );

  // Flatten and deduplicate by message ID
  const seen = new Set();
  const messages = results.flat()
    .filter(msg => {
      if (seen.has(msg.id)) return false;
      seen.add(msg.id);
      return true;
    })
    .sort((a, b) => b.time - a.time); // Newest first

  return messages;
}

/**
 * Format a message for display
 */
export function formatMessage(msg) {
  const time = new Date(msg.time * 1000);
  const timeStr = time.toLocaleString();

  return {
    id: msg.id,
    time: timeStr,
    timestamp: msg.time,
    content: msg.message,
    title: msg.title || null,
    tags: msg.tags || [],
    priority: msg.priority || 3
  };
}

/**
 * Format all messages for AI-friendly output
 */
export function formatInboxForDisplay(messages) {
  if (messages.length === 0) {
    return {
      count: 0,
      summary: 'No messages in inbox.',
      messages: []
    };
  }

  const formatted = messages.map(formatMessage);

  return {
    count: messages.length,
    summary: `${messages.length} message${messages.length === 1 ? '' : 's'} in inbox.`,
    messages: formatted
  };
}

/**
 * Get inbox status information
 */
export function getInboxStatus(config) {
  const windowExpiry = getWindowExpiry(config.windowSeconds);
  const expiresAt = new Date(config.expiresAt);
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

// CLI interface
if (import.meta.url === `file://${process.argv[1]}` || process.argv[1]?.endsWith('fetch-inbox.js')) {
  import('fs').then(fs => {
    import('os').then(os => {
      import('path').then(async path => {
        // Load config from file
        const configPath = path.join(os.homedir(), '.share-to-inbox', 'config.json');
        let config;

        try {
          config = JSON.parse(fs.readFileSync(configPath, 'utf8'));
        } catch (e) {
          console.error('No config found at', configPath);
          console.error('Run generate-secret.js first to set up pairing.');
          process.exit(1);
        }

        console.log('Fetching inbox...\n');
        console.log('Config:', {
          secret: config.secret.substring(0, 8) + '...',
          server: config.server,
          windowSeconds: config.windowSeconds
        });
        console.log('');

        const topics = getRetrievalTopics(config.secret, config.windowSeconds);
        console.log('Checking topics:');
        console.log('  Current:  ', topics[0]);
        console.log('  Previous: ', topics[1]);
        console.log('');

        try {
          const messages = await fetchInbox(config);
          const result = formatInboxForDisplay(messages);
          console.log('Result:', result.summary);
          if (result.messages.length > 0) {
            console.log('\nMessages:');
            for (const msg of result.messages) {
              console.log(`\n[${msg.time}]`);
              console.log(msg.content);
            }
          }
        } catch (err) {
          console.error('Error:', err.message);
        }
      });
    });
  });
}
