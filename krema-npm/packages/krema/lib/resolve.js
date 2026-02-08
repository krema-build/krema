const fs = require('node:fs');
const path = require('node:path');
const { execFileSync } = require('node:child_process');
const { getPlatform } = require('./platform');
const { JAVA_VERSION, LIB_DIR, JAR_NAME, VERSION } = require('./constants');
const { jarUrl } = require('./constants');
const { download } = require('./download');
const { temurinJavaPath } = require('./jdk');

/**
 * Try to find the native binary from the platform-specific npm optional dependency.
 * Returns the path to the binary, or null.
 */
function findNativeBinary() {
  const platform = getPlatform();
  if (!platform) return null;

  try {
    const pkgDir = path.dirname(require.resolve(`${platform.npmPkg}/package.json`));
    const bin = path.join(pkgDir, 'bin', process.platform === 'win32' ? 'krema.exe' : 'krema');
    if (fs.existsSync(bin)) {
      return bin;
    }
  } catch {
    // Package not installed (wrong platform or optional dep skipped)
  }
  return null;
}

/**
 * Find the fat JAR. Downloads it if not present.
 * Returns the path to the JAR.
 */
async function findOrDownloadJar() {
  const jarPath = path.join(LIB_DIR, JAR_NAME);
  if (fs.existsSync(jarPath)) {
    return jarPath;
  }

  console.error(`  Downloading ${JAR_NAME}...`);
  await download(jarUrl(VERSION), jarPath);
  return jarPath;
}

/**
 * Find a Java 25 installation.
 * Search order:
 *   1. KREMA_JAVA_HOME env
 *   2. macOS: /usr/libexec/java_home -v 25
 *   3. JAVA_HOME (if version matches)
 *   4. java on PATH (if version matches)
 *   5. ~/.krema/jdk/temurin-25/
 * Returns the path to the java binary, or null.
 */
function findJava() {
  // 1. KREMA_JAVA_HOME override
  if (process.env.KREMA_JAVA_HOME) {
    const java = path.join(process.env.KREMA_JAVA_HOME, 'bin', 'java');
    if (fs.existsSync(java)) return java;
  }

  // 2. macOS: /usr/libexec/java_home
  if (process.platform === 'darwin') {
    try {
      const jh = execFileSync('/usr/libexec/java_home', ['-v', JAVA_VERSION], {
        encoding: 'utf8',
        stdio: ['pipe', 'pipe', 'pipe'],
      }).trim();
      const java = path.join(jh, 'bin', 'java');
      if (jh && fs.existsSync(java)) return java;
    } catch {
      // Not found
    }
  }

  // 3. JAVA_HOME if version matches
  if (process.env.JAVA_HOME) {
    const java = path.join(process.env.JAVA_HOME, 'bin', 'java');
    if (fs.existsSync(java) && checkJavaVersion(java)) return java;
  }

  // 4. java on PATH
  try {
    const javaOnPath = execFileSync(process.platform === 'win32' ? 'where' : 'which', ['java'], {
      encoding: 'utf8',
      stdio: ['pipe', 'pipe', 'pipe'],
    }).trim().split('\n')[0];
    if (javaOnPath && checkJavaVersion(javaOnPath)) return javaOnPath;
  } catch {
    // Not found
  }

  // 5. ~/.krema/jdk/temurin-25/
  const temurin = temurinJavaPath();
  if (temurin) return temurin;

  return null;
}

function checkJavaVersion(javaPath) {
  try {
    const output = execFileSync(javaPath, ['-version'], {
      encoding: 'utf8',
      stdio: ['pipe', 'pipe', 'pipe'],
    });
    // java -version outputs to stderr, but execFileSync with stdio pipe captures it
    // Some JDKs output to stdout, some to stderr. Check both.
    const combined = output || '';
    return parseJavaVersion(combined) === JAVA_VERSION;
  } catch (err) {
    // java -version writes to stderr which becomes err.stderr
    if (err.stderr) {
      return parseJavaVersion(err.stderr) === JAVA_VERSION;
    }
    return false;
  }
}

function parseJavaVersion(text) {
  const match = text.match(/"(\d+)/);
  return match ? match[1] : null;
}

/**
 * Resolve the execution mode. Returns { mode, args } where:
 *   - mode "native": args = [binaryPath, ...userArgs]
 *   - mode "jar":    args = [javaPath, "-jar", jarPath, ...userArgs]
 */
async function resolve() {
  // Tier 1: Native binary
  const nativeBin = findNativeBinary();
  if (nativeBin) {
    return { mode: 'native', binary: nativeBin, java: null, jar: null };
  }

  // Tier 2 & 3: JAR + Java
  const jarPath = await findOrDownloadJar();
  const javaPath = findJava();

  return { mode: 'jar', binary: null, java: javaPath, jar: jarPath };
}

module.exports = { findNativeBinary, findOrDownloadJar, findJava, resolve };
