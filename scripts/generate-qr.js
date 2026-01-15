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
    // Try qrcode library first (more reliable API)
    const qrcode = await import('qrcode').catch(() => null);
    if (qrcode && qrcode.toString) {
      return await qrcode.toString(data, { type: 'terminal', small: options.small !== false });
    }
    if (qrcode && qrcode.default && qrcode.default.toString) {
      return await qrcode.default.toString(data, { type: 'terminal', small: options.small !== false });
    }

    // Fallback: just show the data
    return generateFallbackQr(data);
  } catch (e) {
    console.error('QR generation error:', e.message);
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
  import('fs').then(async (fs) => {
    import('os').then(async (os) => {
      import('path').then(async (path) => {
        // Check for --pairing flag to generate from config
        if (process.argv.includes('--pairing')) {
          const configPath = path.join(os.homedir(), '.share-to-inbox', 'config.json');
          try {
            const config = JSON.parse(fs.readFileSync(configPath, 'utf8'));
            const pairingData = JSON.stringify({
              s: config.secret,
              e: config.expiresAtTimestamp,
              w: config.windowSeconds,
              u: config.server
            });
            await displayQr('PAIRING QR CODE - Scan with Share to Inbox app', pairingData);
            console.log('Pairing expires:', new Date(config.expiresAtTimestamp).toLocaleDateString());
          } catch (e) {
            console.error('No config found. Run generate-secret.js first.');
            process.exit(1);
          }
        } else {
          const data = process.argv[2] || 'https://example.com/test';
          const title = process.argv[3] || 'QR Code';
          await displayQr(title, data);
          console.log('Data encoded:', data);
        }
      });
    });
  });
}
