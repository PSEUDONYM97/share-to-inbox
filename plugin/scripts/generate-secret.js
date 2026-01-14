#!/usr/bin/env node
/**
 * Hardware-bound secret generation
 *
 * Creates a secret that is tied to the current machine's hardware.
 * The secret cannot be used on a different computer.
 */

import { createHmac, randomBytes } from 'crypto';
import { getHardwareFingerprint } from './hardware-id.js';

/**
 * Generate a hardware-bound secret
 *
 * @param {number} [expirationDays=90] - Days until secret expires
 * @returns {object} Secret configuration
 */
export function generateSecret(expirationDays = 90) {
  // Get hardware fingerprint
  const hardwareFingerprint = getHardwareFingerprint();

  // Generate random entropy
  const randomEntropy = randomBytes(32);
  const timestamp = Date.now();

  // Combine: HMAC(hardware, random + timestamp)
  const hmac = createHmac('sha256', Buffer.from(hardwareFingerprint, 'hex'));
  hmac.update(randomEntropy);
  hmac.update(Buffer.from(timestamp.toString()));
  const secret = hmac.digest('hex');

  // Calculate expiration
  const expiresAt = timestamp + (expirationDays * 24 * 60 * 60 * 1000);

  return {
    secret,
    hardwareFingerprint,
    createdAt: timestamp,
    expiresAt,
    expirationDays,
    windowSeconds: 21600, // 6 hours default
    server: 'https://ntfy.sh'
  };
}

/**
 * Create the config object for storage
 */
export function createConfig(secretData) {
  return {
    secret: secretData.secret,
    hardwareFingerprint: secretData.hardwareFingerprint,
    createdAt: new Date(secretData.createdAt).toISOString(),
    expiresAt: new Date(secretData.expiresAt).toISOString(),
    expiresAtTimestamp: secretData.expiresAt,
    windowSeconds: secretData.windowSeconds,
    server: secretData.server
  };
}

/**
 * Create the QR payload for phone pairing
 * Compact format to fit in QR code
 */
export function createQrPayload(secretData) {
  return {
    s: secretData.secret,                    // secret
    e: Math.floor(secretData.expiresAt / 1000), // expires (unix seconds)
    w: secretData.windowSeconds,              // window
    u: secretData.server                      // server URL
  };
}

/**
 * Check if config is expired
 */
export function isExpired(config) {
  const now = Date.now();
  const expiresAt = config.expiresAtTimestamp || new Date(config.expiresAt).getTime();
  return now >= expiresAt;
}

/**
 * Get days remaining until expiration
 */
export function getDaysRemaining(config) {
  const now = Date.now();
  const expiresAt = config.expiresAtTimestamp || new Date(config.expiresAt).getTime();
  const msRemaining = expiresAt - now;
  if (msRemaining <= 0) return 0;
  return Math.ceil(msRemaining / (24 * 60 * 60 * 1000));
}

/**
 * Mask secret for display (show first/last 4 chars)
 */
export function maskSecret(secret) {
  if (!secret || secret.length < 12) return '****';
  return `${secret.substring(0, 4)}...${secret.substring(secret.length - 4)}`;
}

// CLI interface
if (import.meta.url === `file://${process.argv[1]}` || process.argv[1]?.endsWith('generate-secret.js')) {
  const days = parseInt(process.argv[2]) || 90;

  console.log(`Generating ${days}-day secret...\n`);

  try {
    const secretData = generateSecret(days);
    const config = createConfig(secretData);
    const qrPayload = createQrPayload(secretData);

    console.log('Config (for local storage):');
    console.log(JSON.stringify(config, null, 2));
    console.log('');

    console.log('QR Payload (for phone):');
    console.log(JSON.stringify(qrPayload));
    console.log('');

    console.log('Summary:');
    console.log('  Secret:    ', maskSecret(secretData.secret));
    console.log('  Expires:   ', new Date(secretData.expiresAt).toLocaleDateString());
    console.log('  Days:      ', days);
    console.log('  Window:    ', secretData.windowSeconds / 3600, 'hours');
    console.log('  Server:    ', secretData.server);
  } catch (err) {
    console.error('ERROR:', err.message);
    process.exit(1);
  }
}
