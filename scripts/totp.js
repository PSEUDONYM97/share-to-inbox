/**
 * TOTP-style topic generation for share-to-inbox
 *
 * This module computes time-based topics using HMAC-SHA256.
 * Both the computer and Android app must produce identical output
 * for the same inputs.
 *
 * Topic = HMAC-SHA256(secret, floor(unixTime / windowSeconds)).hex().substring(0, 32)
 *
 * Properties:
 * - Deterministic: same secret + same time window = same topic
 * - Unlinkable: topics from different windows appear completely random
 * - No prefix: pure hash output, no identifiers
 */

import { createHmac } from 'crypto';

/**
 * Compute the current time window index
 * @param {number} windowSeconds - Size of each window in seconds (default: 6 hours)
 * @param {number} [timestamp] - Unix timestamp in milliseconds (default: now)
 * @returns {number} - The window index
 */
export function getWindowIndex(windowSeconds = 21600, timestamp = Date.now()) {
  const unixSeconds = Math.floor(timestamp / 1000);
  return Math.floor(unixSeconds / windowSeconds);
}

/**
 * Generate a topic for a specific window
 * @param {string} secret - The shared secret (hex string)
 * @param {number} windowIndex - The time window index
 * @param {number} [topicLength=32] - Length of the resulting topic
 * @returns {string} - The topic (hex string, no prefix)
 */
export function generateTopic(secret, windowIndex, topicLength = 32) {
  // Convert window index to buffer (big-endian 64-bit)
  const windowBuffer = Buffer.alloc(8);
  windowBuffer.writeBigUInt64BE(BigInt(windowIndex));

  // HMAC-SHA256(secret, windowIndex)
  const secretBuffer = Buffer.from(secret, 'hex');
  const hmac = createHmac('sha256', secretBuffer);
  hmac.update(windowBuffer);
  const hash = hmac.digest('hex');

  // Return first N characters (default 32 = 128 bits of entropy)
  return hash.substring(0, topicLength);
}

/**
 * Get the current topic
 * @param {string} secret - The shared secret (hex string)
 * @param {number} [windowSeconds=21600] - Window size in seconds
 * @param {number} [topicLength=32] - Length of the resulting topic
 * @returns {string} - The current topic
 */
export function getCurrentTopic(secret, windowSeconds = 21600, topicLength = 32) {
  const windowIndex = getWindowIndex(windowSeconds);
  return generateTopic(secret, windowIndex, topicLength);
}

/**
 * Get current and previous topics (for retrieval with clock skew tolerance)
 * @param {string} secret - The shared secret (hex string)
 * @param {number} [windowSeconds=21600] - Window size in seconds
 * @param {number} [topicLength=32] - Length of the resulting topic
 * @returns {string[]} - Array of topics [current, previous]
 */
export function getRetrievalTopics(secret, windowSeconds = 21600, topicLength = 32) {
  const currentWindow = getWindowIndex(windowSeconds);
  return [
    generateTopic(secret, currentWindow, topicLength),
    generateTopic(secret, currentWindow - 1, topicLength)
  ];
}

/**
 * Calculate when the current window expires
 * @param {number} [windowSeconds=21600] - Window size in seconds
 * @returns {Date} - When the current window ends
 */
export function getWindowExpiry(windowSeconds = 21600) {
  const currentWindow = getWindowIndex(windowSeconds);
  const expirySeconds = (currentWindow + 1) * windowSeconds;
  return new Date(expirySeconds * 1000);
}

// Test vectors for cross-implementation verification
export const TEST_VECTORS = [
  {
    secret: '0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef',
    windowIndex: 0,
    expectedTopic: null, // Will be computed and documented
    description: 'Zero window index'
  },
  {
    secret: '0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef',
    windowIndex: 1000000,
    expectedTopic: null,
    description: 'Large window index'
  },
  {
    secret: 'ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff',
    windowIndex: 12345,
    expectedTopic: null,
    description: 'All-F secret'
  }
];

// If run directly, compute and print test vectors
if (import.meta.url === `file://${process.argv[1]}` || process.argv[1]?.endsWith('totp.js')) {
  console.log('TOTP Test Vectors\n');
  console.log('These values must match the Kotlin implementation exactly.\n');

  for (const vector of TEST_VECTORS) {
    const topic = generateTopic(vector.secret, vector.windowIndex);
    console.log(`Description: ${vector.description}`);
    console.log(`Secret:      ${vector.secret}`);
    console.log(`Window:      ${vector.windowIndex}`);
    console.log(`Topic:       ${topic}`);
    console.log('');
  }

  // Also show current topic for a test secret
  const testSecret = '0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef';
  console.log('--- Current Window Demo ---');
  console.log(`Window size: 6 hours (21600 seconds)`);
  console.log(`Current window index: ${getWindowIndex()}`);
  console.log(`Current topic: ${getCurrentTopic(testSecret)}`);
  console.log(`Window expires: ${getWindowExpiry().toISOString()}`);
}
