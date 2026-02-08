---
sidebar_position: 4
title: Upload Plugin
description: File uploads with progress tracking
---

# Upload Plugin

The Upload plugin provides file uploads via multipart/form-data with progress tracking.

## Installation

```xml
<dependency>
    <groupId>build.krema</groupId>
    <artifactId>krema-plugin-upload</artifactId>
    <version>${krema.version}</version>
</dependency>
```

## Permissions

```toml
[permissions]
allow = ["upload:send"]
```

## Commands

### upload:upload

Uploads files to a server.

```javascript
const result = await window.krema.invoke('upload:upload', {
  url: 'https://api.example.com/upload',
  files: ['/path/to/file.jpg'],
  method: 'POST',                    // Optional
  headers: { 'Authorization': 'Bearer token' },
  formFields: { 'description': 'My upload' },
  timeout: 60,                       // Seconds
  id: 'upload-123'                   // For tracking
});

// result: { id, status, body, headers }
```

## Events

```javascript
// Progress updates
window.krema.on('upload:progress', (data) => {
  console.log(`${data.id}: ${data.percentage}%`);
  // data: { id, bytesSent, totalBytes, percentage }
});

// Completion
window.krema.on('upload:completed', (data) => {
  console.log(`${data.id} completed: ${data.status}`);
});

// Errors
window.krema.on('upload:error', (data) => {
  console.error(`${data.id} failed: ${data.error}`);
});
```

## Example: Upload with Progress

```javascript
async function uploadWithProgress(filePath) {
  const progressBar = document.getElementById('progress');

  const unsubscribe = window.krema.on('upload:progress', (data) => {
    progressBar.value = data.percentage;
  });

  try {
    const result = await window.krema.invoke('upload:upload', {
      url: 'https://api.example.com/files',
      files: [filePath],
      headers: { 'Authorization': `Bearer ${getToken()}` }
    });

    return JSON.parse(result.body);
  } finally {
    unsubscribe();
  }
}
```
