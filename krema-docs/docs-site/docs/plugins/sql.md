---
sidebar_position: 2
title: SQL Plugin
description: SQLite database access for Krema applications
---

# SQL Plugin

The SQL plugin provides SQLite database access with parameterized queries and batch operations.

## Installation

```xml
<dependency>
    <groupId>build.krema</groupId>
    <artifactId>krema-plugin-sql</artifactId>
    <version>${krema.version}</version>
</dependency>
```

## Permissions

```toml
[permissions]
allow = ["sql:read", "sql:write"]
```

## Commands

### sql:open

Opens a database connection.

```javascript
await window.krema.invoke('sql:open', {
  name: 'mydb',
  path: '/path/to/db.db'  // Optional, defaults to app data dir
});
```

### sql:execute

Executes a write query (INSERT, UPDATE, DELETE, CREATE).

```javascript
const result = await window.krema.invoke('sql:execute', {
  name: 'mydb',
  sql: 'INSERT INTO users (name, email) VALUES (?, ?)',
  params: ['John', 'john@example.com']
});
// { rowsAffected: 1, lastInsertId: 42 }
```

### sql:select

Executes a read query.

```javascript
const users = await window.krema.invoke('sql:select', {
  name: 'mydb',
  sql: 'SELECT * FROM users WHERE active = ?',
  params: [true]
});
// [{ id: 1, name: 'John', ... }, ...]
```

### sql:batch

Executes multiple statements in a transaction.

```javascript
await window.krema.invoke('sql:batch', {
  name: 'mydb',
  statements: [
    'CREATE TABLE IF NOT EXISTS users (id INTEGER PRIMARY KEY, name TEXT)',
    'CREATE INDEX IF NOT EXISTS idx_users_name ON users(name)'
  ]
});
```

### sql:close

Closes a database connection.

```javascript
await window.krema.invoke('sql:close', { name: 'mydb' });
```

## Example: Notes App

```javascript
class NotesDB {
  async init() {
    await window.krema.invoke('sql:open', { name: 'notes' });
    await window.krema.invoke('sql:batch', {
      name: 'notes',
      statements: [
        `CREATE TABLE IF NOT EXISTS notes (
          id INTEGER PRIMARY KEY AUTOINCREMENT,
          title TEXT NOT NULL,
          content TEXT,
          created_at INTEGER DEFAULT (strftime('%s', 'now'))
        )`
      ]
    });
  }

  async create(title, content) {
    const result = await window.krema.invoke('sql:execute', {
      name: 'notes',
      sql: 'INSERT INTO notes (title, content) VALUES (?, ?)',
      params: [title, content]
    });
    return result.lastInsertId;
  }

  async findAll() {
    return window.krema.invoke('sql:select', {
      name: 'notes',
      sql: 'SELECT * FROM notes ORDER BY created_at DESC'
    });
  }
}
```
