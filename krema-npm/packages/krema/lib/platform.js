const os = require('node:os');
const process = require('node:process');

const PLATFORMS = {
  'darwin-arm64': { npmPkg: '@krema-build/cli-darwin-arm64', adoptiumOs: 'mac', adoptiumArch: 'aarch64' },
  'darwin-x64': { npmPkg: '@krema-build/cli-darwin-x64', adoptiumOs: 'mac', adoptiumArch: 'x64' },
  'linux-x64': { npmPkg: '@krema-build/cli-linux-x64', adoptiumOs: 'linux', adoptiumArch: 'x64' },
  'linux-arm64': { npmPkg: '@krema-build/cli-linux-arm64', adoptiumOs: 'linux', adoptiumArch: 'aarch64' },
  'win32-x64': { npmPkg: '@krema-build/cli-win32-x64', adoptiumOs: 'windows', adoptiumArch: 'x64' },
};

function getPlatformKey() {
  return `${process.platform}-${os.arch()}`;
}

function getPlatform() {
  const key = getPlatformKey();
  const platform = PLATFORMS[key];
  if (!platform) {
    return null;
  }
  return { key, ...platform };
}

module.exports = { PLATFORMS, getPlatformKey, getPlatform };
