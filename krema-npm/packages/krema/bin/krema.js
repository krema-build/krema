#!/usr/bin/env node
/**
 * Krema CLI shim.
 * Resolves the best way to run Krema (native binary, JAR+Java, or JAR+auto-install JDK)
 * and execs the resolved command with inherited stdio.
 */

const { spawn } = require('node:child_process');
const readline = require('node:readline');
const { resolve, findJava } = require('../lib/resolve');
const { read, write } = require('../lib/cache');
const { installJdk } = require('../lib/jdk');
const { JAVA_VERSION, VERSION } = require('../lib/constants');

async function main() {
  const args = process.argv.slice(2);

  // Try cached resolution first
  const cached = read();
  if (cached && cached.version === VERSION) {
    if (cached.mode === 'native' && cached.path) {
      return exec(cached.path, args);
    }
    if (cached.mode === 'jar' && cached.java && cached.jar) {
      return exec(cached.java, ['--enable-native-access=ALL-UNNAMED', '-jar', cached.jar, ...args]);
    }
  }

  // Full resolution
  const resolved = await resolve();

  if (resolved.mode === 'native') {
    write({ mode: 'native', path: resolved.binary, version: VERSION });
    return exec(resolved.binary, args);
  }

  // JAR mode — need Java
  let javaPath = resolved.java;

  if (!javaPath) {
    // Interactive prompt for JDK install
    if (process.stdin.isTTY) {
      const answer = await prompt(
        `Java ${JAVA_VERSION} is required but was not found.\n` +
        `Install Eclipse Temurin ${JAVA_VERSION} to ~/.krema/jdk? [Y/n] `
      );

      if (answer === '' || answer.toLowerCase() === 'y' || answer.toLowerCase() === 'yes') {
        try {
          javaPath = await installJdk();
        } catch (err) {
          console.error(`\nFailed to install JDK: ${err.message}`);
          process.exit(1);
        }
      } else {
        printManualInstallInstructions();
        process.exit(1);
      }
    } else {
      // Non-interactive: try auto-install
      try {
        javaPath = await installJdk();
      } catch (err) {
        console.error(`Error: Java ${JAVA_VERSION} is required but was not found.`);
        printManualInstallInstructions();
        process.exit(1);
      }
    }
  }

  write({ mode: 'jar', java: javaPath, jar: resolved.jar, version: VERSION });
  return exec(javaPath, ['--enable-native-access=ALL-UNNAMED', '-jar', resolved.jar, ...args]);
}

function exec(command, args) {
  const child = spawn(command, args, { stdio: 'inherit' });
  child.on('error', (err) => {
    console.error(`Failed to start: ${err.message}`);
    process.exit(1);
  });
  child.on('close', (code) => {
    process.exit(code ?? 1);
  });
}

function prompt(question) {
  return new Promise((resolve) => {
    const rl = readline.createInterface({ input: process.stdin, output: process.stderr });
    rl.question(question, (answer) => {
      rl.close();
      resolve(answer.trim());
    });
  });
}

function printManualInstallInstructions() {
  console.error('');
  console.error(`To use Krema, install Java ${JAVA_VERSION} and either:`);
  console.error('  - Set KREMA_JAVA_HOME to point at the JDK');
  console.error(`  - Ensure /usr/libexec/java_home -v ${JAVA_VERSION} works (macOS)`);
  console.error('  - Or add java to your PATH');
  console.error('');
  console.error('Download from: https://adoptium.net/temurin/releases/');
}

main().catch((err) => {
  console.error(err.message);
  process.exit(1);
});
