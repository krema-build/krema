const path = require('node:path');
const os = require('node:os');

const VERSION = '0.1.0';
const JAVA_VERSION = '25';
const GITHUB_REPO = 'krema-build/krema';
const JAR_NAME = 'krema-cli.jar';

const KREMA_HOME = path.join(os.homedir(), '.krema');
const LIB_DIR = path.join(KREMA_HOME, 'lib');
const JDK_DIR = path.join(KREMA_HOME, 'jdk');
const CACHE_FILE = path.join(KREMA_HOME, 'cache.json');
const TEMURIN_DIR = path.join(JDK_DIR, `temurin-${JAVA_VERSION}`);

function jarUrl(version) {
  return `https://github.com/${GITHUB_REPO}/releases/download/v${version}/${JAR_NAME}`;
}

function nativeBinaryUrl(version, platformKey) {
  const name = platformKey === 'win32-x64' ? 'krema-windows-x64.exe' : `krema-${platformKey}`;
  return `https://github.com/${GITHUB_REPO}/releases/download/v${version}/${name}`;
}

module.exports = {
  VERSION,
  JAVA_VERSION,
  GITHUB_REPO,
  JAR_NAME,
  KREMA_HOME,
  LIB_DIR,
  JDK_DIR,
  CACHE_FILE,
  TEMURIN_DIR,
  jarUrl,
  nativeBinaryUrl,
};
