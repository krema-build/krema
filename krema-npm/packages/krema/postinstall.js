#!/usr/bin/env node
/**
 * Postinstall script for the krema npm package.
 * Non-interactive: detects the best execution mode and caches the result.
 * Always exits 0 to never break npm install.
 */

const { findNativeBinary, findJava } = require('./lib/resolve');
const { download } = require('./lib/download');
const { read, write } = require('./lib/cache');
const { VERSION, LIB_DIR, JAR_NAME, jarUrl } = require('./lib/constants');
const path = require('node:path');
const fs = require('node:fs');

async function main() {
  // 1. Check for native binary
  const nativeBin = findNativeBinary();
  if (nativeBin) {
    write({ mode: 'native', path: nativeBin, version: VERSION });
    return;
  }

  // 2. No native binary — download JAR
  const jarPath = path.join(LIB_DIR, JAR_NAME);
  if (!fs.existsSync(jarPath)) {
    try {
      await download(jarUrl(VERSION), jarPath, { quiet: true });
    } catch {
      // Non-fatal: bin/krema.js will retry at runtime
    }
  }

  // 3. Check for Java 25
  const javaPath = findJava();
  if (javaPath) {
    write({ mode: 'jar', java: javaPath, jar: jarPath, version: VERSION });
  } else {
    write({ mode: 'jar-no-java', jar: jarPath, version: VERSION });
  }
}

main().catch(() => {
  // Never break npm install
  process.exit(0);
});
