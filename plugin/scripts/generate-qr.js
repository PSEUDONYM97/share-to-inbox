#!/usr/bin/env node
/**
 * QR Code generation for terminal and file output
 *
 * Uses qrcode-terminal for ASCII output and qrcode for image files.
 * Falls back to displaying the raw data if libraries aren't available.
 */

/**
 * Generate ASCII QR code for terminal display
 *
 * @param {string} data - Data to encode in QR
 * @param {object} options - Options
 * @param {boolean} options.small - Use small mode (half-height characters)
 * @returns {Promise<string>} ASCII QR code
 */
export async function generateAsciiQr(data, options = {}) {
  try {
    // Try qrcode-terminal first (commonly available)
    const qrcodeTerminal = await import('qrcode-terminal').catch(() => null);
    if (qrcodeTerminal) {
      return new Promise((resolve) => {
        let output = '';
        qrcodeTerminal.generate(data, { small: options.small !== false }, (qr) => {
          output = qr;
          resolve(output);
        });
        // Fallback if callback doesn't fire
        setTimeout(() => resolve(output || generateFallbackQr(data)), 1000);
      });
    }

    // Try qrcode library (generates to string)
    const qrcode = await import('qrcode').catch(() => null);
    if (qrcode) {
      return await qrcode.toString(data, { type: 'terminal', small: options.small !== false });
    }

    // Fallback: just show the data
    return generateFallbackQr(data);
  } catch {
    return generateFallbackQr(data);
  }
}

/**
 * Fallback when QR libraries aren't available
 */
function generateFallbackQr(data) {
  return `
╔════════════════════════════════════════════════════════════╗
║  QR CODE LIBRARIES NOT INSTALLED                           ║
║                                                            ║
║  To enable QR display, run:                                ║
║    npm install qrcode qrcode-terminal                      ║
║                                                            ║
║  Manual data (copy this):                                  ║
╚════════════════════════════════════════════════════════════╝

${data}
`;
}

/**
 * Generate QR code and save to PNG file
 *
 * @param {string} data - Data to encode
 * @param {string} filepath - Output file path
 * @returns {Promise<boolean>} Success
 */
export async function generateQrFile(data, filepath) {
  try {
    const qrcode = await import('qrcode').catch(() => null);
    if (qrcode) {
      await qrcode.toFile(filepath, data, {
        width: 300,
        margin: 2,
        color: {
          dark: '#000000',
          light: '#ffffff'
        }
      });
      return true;
    }
    return false;
  } catch {
    return false;
  }
}

/**
 * Display QR code with a title
 */
export async function displayQr(title, data, options = {}) {
  const qr = await generateAsciiQr(data, options);

  console.log('');
  console.log('═'.repeat(60));
  console.log(`  ${title}`);
  console.log('═'.repeat(60));
  console.log('');
  console.log(qr);
  console.log('');
}

/**
 * Generate APK download URL
 */
export function getApkDownloadUrl(repoOwner = 'jaredwilliam', repoName = 'share-to-inbox') {
  return `https://github.com/${repoOwner}/${repoName}/releases/latest/download/share-to-inbox.apk`;
}

// CLI interface
if (import.meta.url === `file://${process.argv[1]}` || process.argv[1]?.endsWith('generate-qr.js')) {
  const data = process.argv[2] || 'https://example.com/test';
  const title = process.argv[3] || 'QR Code';

  displayQr(title, data).then(() => {
    console.log('Data encoded:', data);
  });
}
