---
sidebar_position: 7
title: App Environment API
description: Environment profiles and variables API reference
---

# App Environment API

The App Environment API provides access to the current environment profile and custom environment variables defined in your configuration.

## Environment Profiles

Krema supports environment profiles that can be selected at runtime using the `--profile` flag:

```bash
krema dev --profile development
krema build --profile production
```

Profiles are defined in `krema.toml` under the `[env.*]` sections.

## Commands

### app:getEnvironment

Returns the complete environment information including name and custom variables.

```javascript
const env = await window.krema.invoke('app:getEnvironment');
// {
//   name: "development",
//   vars: {
//     API_URL: "http://localhost:3000",
//     DEBUG: "true"
//   }
// }
```

### app:getEnvironmentName

Returns just the environment name.

```javascript
const envName = await window.krema.invoke('app:getEnvironmentName');
// "development" | "staging" | "production" | etc.
```

### app:getEnvironmentVar

Returns a specific environment variable.

```javascript
const apiUrl = await window.krema.invoke('app:getEnvironmentVar', { key: 'API_URL' });
// "http://localhost:3000"

const missing = await window.krema.invoke('app:getEnvironmentVar', { key: 'NONEXISTENT' });
// null
```

## TypeScript Types

```typescript
interface AppEnvironment {
  name: string;
  vars: Record<string, string>;
}
```

## Configuration

### Using .env Files

Krema automatically loads environment variables from `.env` files:

```
.env                 # Loaded in all environments
.env.local           # Loaded in all environments, gitignored
.env.development     # Loaded when --profile development
.env.production      # Loaded when --profile production
```

Example `.env.development`:
```bash
API_URL=http://localhost:3000
DEBUG=true
LOG_LEVEL=debug
```

Example `.env.production`:
```bash
API_URL=https://api.example.com
DEBUG=false
LOG_LEVEL=error
```

### Inline Configuration

You can also define environment-specific variables in `krema.toml`:

```toml
[env.development]
vars = { API_URL = "http://localhost:3000", DEBUG = "true" }

[env.staging]
vars = { API_URL = "https://staging-api.example.com" }

[env.production]
vars = { API_URL = "https://api.example.com" }
```

## Examples

### Environment-Aware API Client

```javascript
class ApiClient {
  constructor() {
    this.baseUrl = null;
  }

  async init() {
    this.baseUrl = await window.krema.invoke('app:getEnvironmentVar', {
      key: 'API_URL'
    }) || 'https://api.example.com';

    const debug = await window.krema.invoke('app:getEnvironmentVar', {
      key: 'DEBUG'
    });
    this.debug = debug === 'true';
  }

  async fetch(endpoint, options = {}) {
    const url = `${this.baseUrl}${endpoint}`;

    if (this.debug) {
      console.log(`[API] ${options.method || 'GET'} ${url}`);
    }

    const response = await fetch(url, options);
    return response.json();
  }
}

const api = new ApiClient();
await api.init();
```

### Environment Indicator

```javascript
async function showEnvironmentBadge() {
  const envName = await window.krema.invoke('app:getEnvironmentName');

  // Only show badge in non-production environments
  if (envName !== 'production') {
    const badge = document.createElement('div');
    badge.className = 'env-badge';
    badge.textContent = envName.toUpperCase();
    badge.style.cssText = `
      position: fixed;
      top: 0;
      right: 0;
      background: ${envName === 'development' ? '#4caf50' : '#ff9800'};
      color: white;
      padding: 4px 12px;
      font-size: 12px;
      font-weight: bold;
      z-index: 9999;
    `;
    document.body.appendChild(badge);
  }
}
```

### Feature Flags by Environment

```javascript
async function getFeatureFlags() {
  const env = await window.krema.invoke('app:getEnvironment');

  // Base flags
  const flags = {
    analytics: true,
    newEditor: false,
    darkMode: true
  };

  // Environment-specific overrides
  if (env.name === 'development') {
    flags.newEditor = true;  // Enable experimental features in dev
    flags.analytics = false; // Disable analytics in dev
  }

  // Check for explicit overrides in env vars
  if (env.vars.ENABLE_NEW_EDITOR === 'true') {
    flags.newEditor = true;
  }

  return flags;
}
```

### Conditional Logging

```javascript
class Logger {
  constructor() {
    this.level = 'info';
  }

  async init() {
    const level = await window.krema.invoke('app:getEnvironmentVar', {
      key: 'LOG_LEVEL'
    });
    if (level) {
      this.level = level;
    }
  }

  debug(...args) {
    if (this.level === 'debug') {
      console.log('[DEBUG]', ...args);
    }
  }

  info(...args) {
    if (['debug', 'info'].includes(this.level)) {
      console.log('[INFO]', ...args);
    }
  }

  error(...args) {
    console.error('[ERROR]', ...args);
  }
}
```

## Notes

- If no profile is specified, the environment name defaults to `"production"`
- Environment variables from `.env` files take precedence over inline `krema.toml` definitions
- The `app:getEnvironmentVar` command only returns variables defined in your Krema configuration, not system environment variables (use `os:env` for system variables)
