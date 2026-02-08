const fs = require('node:fs');
const path = require('node:path');
const https = require('node:https');
const http = require('node:http');

function follow(url) {
  return url.startsWith('https://') ? https : http;
}

function download(url, dest, { onProgress, quiet } = {}) {
  return new Promise((resolve, reject) => {
    fs.mkdirSync(path.dirname(dest), { recursive: true });
    const file = fs.createWriteStream(dest);

    const get = (currentUrl) => {
      follow(currentUrl).get(currentUrl, (res) => {
        if (res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
          res.resume();
          get(res.headers.location);
          return;
        }

        if (res.statusCode !== 200) {
          file.close();
          fs.unlinkSync(dest);
          reject(new Error(`Download failed: HTTP ${res.statusCode} for ${currentUrl}`));
          return;
        }

        const total = parseInt(res.headers['content-length'], 10) || 0;
        let downloaded = 0;

        res.on('data', (chunk) => {
          downloaded += chunk.length;
          if (onProgress && total > 0) {
            onProgress(downloaded, total);
          } else if (!quiet && total > 0 && process.stderr.isTTY) {
            const pct = Math.round((downloaded / total) * 100);
            const mb = (downloaded / 1048576).toFixed(1);
            const totalMb = (total / 1048576).toFixed(1);
            process.stderr.write(`\r  Downloading... ${mb}/${totalMb} MB (${pct}%)`);
          }
        });

        res.pipe(file);

        file.on('finish', () => {
          if (!quiet && total > 0 && process.stderr.isTTY) {
            process.stderr.write('\n');
          }
          file.close(resolve);
        });

        file.on('error', (err) => {
          fs.unlinkSync(dest);
          reject(err);
        });
      }).on('error', (err) => {
        file.close();
        try { fs.unlinkSync(dest); } catch {}
        reject(err);
      });
    };

    get(url);
  });
}

module.exports = { download };
