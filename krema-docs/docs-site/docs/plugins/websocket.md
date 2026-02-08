---
sidebar_position: 3
title: WebSocket Plugin
description: WebSocket connections from the backend
---

# WebSocket Plugin

The WebSocket plugin provides WebSocket connections from the Java backend, bypassing webview restrictions.

## Installation

```xml
<dependency>
    <groupId>build.krema</groupId>
    <artifactId>krema-plugin-websocket</artifactId>
    <version>${krema.version}</version>
</dependency>
```

## Permissions

```toml
[permissions]
allow = ["websocket:connect"]
```

## Commands

### websocket:connect

Opens a WebSocket connection.

```javascript
await window.krema.invoke('websocket:connect', {
  name: 'chat',
  url: 'wss://chat.example.com/ws',
  headers: { 'Authorization': 'Bearer token123' }  // Optional
});
```

### websocket:send

Sends a message.

```javascript
await window.krema.invoke('websocket:send', {
  name: 'chat',
  message: JSON.stringify({ type: 'message', text: 'Hello!' })
});
```

### websocket:disconnect

Closes the connection.

```javascript
await window.krema.invoke('websocket:disconnect', { name: 'chat' });
```

## Events

```javascript
// Connection established
window.krema.on('websocket:connected', (data) => {
  console.log('Connected:', data.name);
});

// Message received
window.krema.on('websocket:message', (data) => {
  console.log('Message from', data.name, ':', data.data);
});

// Connection closed
window.krema.on('websocket:disconnected', (data) => {
  console.log('Disconnected:', data.name, 'code:', data.code);
});

// Error occurred
window.krema.on('websocket:error', (data) => {
  console.error('Error on', data.name, ':', data.error);
});
```

## Example: Chat Client

```javascript
class ChatClient {
  constructor(url, token) {
    this.url = url;
    this.token = token;
    this.setupListeners();
  }

  setupListeners() {
    window.krema.on('websocket:message', (data) => {
      if (data.name === 'chat') {
        const msg = JSON.parse(data.data);
        this.onMessage(msg);
      }
    });

    window.krema.on('websocket:disconnected', () => {
      setTimeout(() => this.connect(), 5000);  // Reconnect
    });
  }

  async connect() {
    await window.krema.invoke('websocket:connect', {
      name: 'chat',
      url: this.url,
      headers: { 'Authorization': `Bearer ${this.token}` }
    });
  }

  async send(message) {
    await window.krema.invoke('websocket:send', {
      name: 'chat',
      message: JSON.stringify(message)
    });
  }

  onMessage(msg) {
    // Handle incoming message
  }
}
```
