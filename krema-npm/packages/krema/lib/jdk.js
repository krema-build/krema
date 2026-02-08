const fs = require('node:fs');
const path = require('node:path');
const { execFileSync } = require('node:child_process');
const { getPlatform } = require('./platform');
const { JAVA_VERSION, JDK_DIR, TEMURIN_DIR } = require('./constants');
const { download } = require('./download');

function adoptiumUrl() {
  const platform = getPlatform();
  if (!platform) {
    return null;
  }
  return `https://api.adoptium.net/v3/binary/latest/${JAVA_VERSION}/ga/${platform.adoptiumOs}/${platform.adoptiumArch}/jdk/hotspot/normal/eclipse`;
}

function temurinJavaPath() {
  // After extraction, Temurin tarballs contain a top-level directory like
  // jdk-25+36 (or jdk-25.0.1+9 etc). We find it dynamically.
  if (!fs.existsSync(TEMURIN_DIR)) {
    return null;
  }
  const entries = fs.readdirSync(TEMURIN_DIR);
  for (const entry of entries) {
    const candidate = process.platform === 'darwin'
      ? path.join(TEMURIN_DIR, entry, 'Contents', 'Home', 'bin', 'java')
      : path.join(TEMURIN_DIR, entry, 'bin', 'java');
    if (fs.existsSync(candidate)) {
      return candidate;
    }
  }
  return null;
}

async function installJdk() {
  const url = adoptiumUrl();
  if (!url) {
    throw new Error(`No Adoptium JDK available for ${process.platform}-${process.arch}`);
  }

  const ext = process.platform === 'win32' ? '.zip' : '.tar.gz';
  const tmpFile = path.join(JDK_DIR, `temurin-${JAVA_VERSION}${ext}`);

  console.error(`  Downloading Eclipse Temurin ${JAVA_VERSION}...`);
  await download(url, tmpFile);

  console.error('  Extracting...');
  fs.mkdirSync(TEMURIN_DIR, { recursive: true });

  if (ext === '.tar.gz') {
    execFileSync('tar', ['xzf', tmpFile, '-C', TEMURIN_DIR], { stdio: 'pipe' });
  } else {
    // Windows: use PowerShell to extract zip
    execFileSync('powershell', [
      '-NoProfile', '-Command',
      `Expand-Archive -Path '${tmpFile}' -DestinationPath '${TEMURIN_DIR}' -Force`
    ], { stdio: 'pipe' });
  }

  fs.unlinkSync(tmpFile);

  const javaPath = temurinJavaPath();
  if (!javaPath) {
    throw new Error('JDK extraction succeeded but java binary not found');
  }

  // Verify
  const out = execFileSync(javaPath, ['-version'], { encoding: 'utf8', stdio: ['pipe', 'pipe', 'pipe'] });
  // java -version writes to stderr
  console.error(`  Installed: ${javaPath}`);
  return javaPath;
}

module.exports = { adoptiumUrl, temurinJavaPath, installJdk };
