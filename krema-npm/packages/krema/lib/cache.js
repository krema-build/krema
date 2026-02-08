const fs = require('node:fs');
const path = require('node:path');
const { CACHE_FILE, KREMA_HOME } = require('./constants');

function read() {
  try {
    return JSON.parse(fs.readFileSync(CACHE_FILE, 'utf8'));
  } catch {
    return null;
  }
}

function write(data) {
  fs.mkdirSync(KREMA_HOME, { recursive: true });
  fs.writeFileSync(CACHE_FILE, JSON.stringify(data, null, 2) + '\n');
}

module.exports = { read, write };
