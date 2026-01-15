#!/usr/bin/env node
/**
 * Cross-platform hardware fingerprinting
 *
 * Gathers unique hardware identifiers to bind secrets to a specific machine.
 * The fingerprint is a SHA-256 hash of combined hardware IDs.
 *
 * Supports: Windows, macOS, Linux
 *
 * SECURITY NOTE: All commands are hardcoded with no user input.
 * execSync is safe here because we control all command strings.
 */

import { createHash } from 'crypto';
import { execSync } from 'child_process';
import { platform } from 'os';

/**
 * Execute a hardcoded command and return trimmed output, or empty string on failure
 * SECURITY: Only call with literal strings, never with user input
 */
function execCommand(cmd) {
  try {
    return execSync(cmd, {
      encoding: 'utf8',
      timeout: 5000,
      stdio: ['pipe', 'pipe', 'ignore']
    }).trim();
  } catch {
    return '';
  }
}

/**
 * Get CPU identifier
 */
function getCpuId() {
  const os = platform();

  if (os === 'win32') {
    // Windows: WMIC or PowerShell (hardcoded commands)
    let result = execCommand('wmic cpu get processorid /format:value');
    if (result) {
      const match = result.match(/ProcessorId=(\S+)/);
      if (match) return match[1];
    }
    // Fallback to PowerShell
    result = execCommand('powershell -Command "(Get-WmiObject Win32_Processor).ProcessorId"');
    return result || '';
  }

  if (os === 'darwin') {
    // macOS: Use IOPlatformSerialNumber as CPU proxy
    const result = execCommand('ioreg -rd1 -c IOPlatformExpertDevice');
    const match = result.match(/"IOPlatformSerialNumber"\s*=\s*"([^"]+)"/);
    return match ? match[1] : '';
  }

  if (os === 'linux') {
    // Linux: /proc/cpuinfo
    const result = execCommand('cat /proc/cpuinfo');
    if (result) {
      // Hash a portion to get consistent ID
      const relevant = result.split('\n').filter(l =>
        l.includes('model name') || l.includes('Serial')
      ).slice(0, 2).join('');
      if (relevant) return createHash('sha256').update(relevant).digest('hex').substring(0, 16);
    }
    return '';
  }

  return '';
}

/**
 * Get primary disk serial number
 */
function getDiskSerial() {
  const os = platform();

  if (os === 'win32') {
    let result = execCommand('wmic diskdrive get serialnumber /format:value');
    if (result) {
      const match = result.match(/SerialNumber=(\S+)/);
      if (match) return match[1];
    }
    result = execCommand('powershell -Command "(Get-WmiObject Win32_DiskDrive | Select -First 1).SerialNumber"');
    return result ? result.trim() : '';
  }

  if (os === 'darwin') {
    const result = execCommand('diskutil info disk0');
    const match = result.match(/Volume UUID:\s*([A-F0-9-]+)/i) ||
                  result.match(/Disk \/ Partition UUID:\s*([A-F0-9-]+)/i);
    return match ? match[1] : '';
  }

  if (os === 'linux') {
    // Try multiple locations
    let result = execCommand('cat /sys/class/block/sda/device/serial');
    if (result) return result;
    result = execCommand('cat /sys/class/block/nvme0n1/serial');
    return result || '';
  }

  return '';
}

/**
 * Get MAC address of primary network interface
 */
function getMacAddress() {
  const os = platform();

  if (os === 'win32') {
    const result = execCommand('getmac /fo csv /nh');
    if (result) {
      const match = result.match(/([0-9A-F]{2}[:-]){5}[0-9A-F]{2}/i);
      if (match) return match[0].replace(/-/g, ':');
    }
    return '';
  }

  if (os === 'darwin') {
    const result = execCommand('ifconfig en0');
    const match = result.match(/ether\s+([0-9a-f:]{17})/i);
    return match ? match[1] : '';
  }

  if (os === 'linux') {
    const result = execCommand('ip link show');
    const match = result.match(/link\/ether\s+([0-9a-f:]{17})/i);
    return match ? match[1] : '';
  }

  return '';
}

/**
 * Get motherboard/BIOS serial
 */
function getBiosSerial() {
  const os = platform();

  if (os === 'win32') {
    let result = execCommand('wmic bios get serialnumber /format:value');
    if (result) {
      const match = result.match(/SerialNumber=(\S+)/);
      if (match && match[1] !== 'Default' && match[1] !== 'None') return match[1];
    }
    result = execCommand('wmic baseboard get serialnumber /format:value');
    if (result) {
      const match = result.match(/SerialNumber=(\S+)/);
      if (match && match[1] !== 'Default' && match[1] !== 'None') return match[1];
    }
    return '';
  }

  if (os === 'darwin') {
    const result = execCommand('ioreg -rd1 -c IOPlatformExpertDevice');
    const match = result.match(/"IOPlatformUUID"\s*=\s*"([^"]+)"/);
    return match ? match[1] : '';
  }

  if (os === 'linux') {
    let result = execCommand('cat /sys/class/dmi/id/board_serial');
    if (result) return result;
    return '';
  }

  return '';
}

/**
 * Get machine hostname as additional entropy
 */
function getHostname() {
  return execCommand('hostname') || '';
}

/**
 * Gather all hardware identifiers
 */
export function getHardwareIds() {
  return {
    cpu: getCpuId(),
    disk: getDiskSerial(),
    mac: getMacAddress(),
    bios: getBiosSerial(),
    hostname: getHostname(),
    platform: platform()
  };
}

/**
 * Generate hardware fingerprint hash
 *
 * Combines all available hardware IDs into a single SHA-256 hash.
 * At least 2 IDs must be present for a valid fingerprint.
 */
export function getHardwareFingerprint() {
  const ids = getHardwareIds();

  // Combine all non-empty values
  const components = [
    ids.cpu,
    ids.disk,
    ids.mac,
    ids.bios,
    ids.hostname,
    ids.platform
  ].filter(Boolean);

  // Require at least 2 components for security
  if (components.length < 2) {
    throw new Error(`Insufficient hardware identifiers. Found: ${components.length}, need at least 2`);
  }

  // Create deterministic fingerprint
  const combined = components.sort().join('|');
  return createHash('sha256').update(combined).digest('hex');
}

/**
 * Verify hardware fingerprint matches stored value
 */
export function verifyFingerprint(storedFingerprint) {
  try {
    const current = getHardwareFingerprint();
    return current === storedFingerprint;
  } catch {
    return false;
  }
}

// CLI interface
if (import.meta.url === `file://${process.argv[1]}` || process.argv[1]?.endsWith('hardware-id.js')) {
  console.log('Hardware Identification\n');
  console.log('Platform:', platform());
  console.log('');

  const ids = getHardwareIds();
  console.log('Components found:');
  console.log('  CPU ID:      ', ids.cpu || '(not available)');
  console.log('  Disk Serial: ', ids.disk || '(not available)');
  console.log('  MAC Address: ', ids.mac || '(not available)');
  console.log('  BIOS Serial: ', ids.bios || '(not available)');
  console.log('  Hostname:    ', ids.hostname || '(not available)');
  console.log('');

  try {
    const fingerprint = getHardwareFingerprint();
    console.log('Hardware Fingerprint:');
    console.log(' ', fingerprint);
  } catch (err) {
    console.log('ERROR:', err.message);
  }
}
