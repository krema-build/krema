---
sidebar_position: 2
title: Quick Start
description: Create your first Krema application
---

# Quick Start

Create and run your first Krema desktop application in minutes.

## Create a New Project

Use the Krema CLI to scaffold a new project:

```bash
krema init my-app --template react
```

Available templates: `vanilla`, `react`, `vue`, `svelte`, `angular`

:::tip Interactive Wizard
Want more control? Use the wizard mode for full configuration including plugins:
```bash
krema init --wizard
```
:::

## Navigate to Your Project

```bash
cd my-app
```

## Start Development Mode

```bash
krema dev
```

This will:
1. Start your frontend dev server (Vite)
2. Launch a native window pointing to the dev server
3. Enable hot reload for both frontend and backend changes

## Your First Backend Command

Open `src-java/com/example/myapp/Commands.java`:

```java
import build.krema.command.KremaCommand;

public class Commands {

    @KremaCommand
    public String greet(String name) {
        return "Hello, " + name + "!";
    }
}
```

## Call It from the Frontend

In your frontend code (e.g., `src/App.jsx` for React):

```jsx
import { useEffect, useState } from 'react';

function App() {
    const [greeting, setGreeting] = useState('');

    useEffect(() => {
        window.krema.invoke('greet', { name: 'World' })
            .then(setGreeting);
    }, []);

    return <h1>{greeting}</h1>;
}

export default App;
```

:::tip Type-Safe Commands
Krema automatically generates TypeScript definitions for your `@KremaCommand` methods at compile time. The `greet` call above gets full autocompletion — command name, argument types, and return type are all inferred from your Java code. See [TypeScript Support](/docs/api/javascript-api#generated-command-types) for details.
:::

## Build for Production

When ready to distribute your app:

```bash
# Build the app
krema build

# Create a distributable bundle (.app, .exe, .AppImage)
krema bundle
```

See the [Building & Bundling](/docs/guides/building) guide for the full details.

## What's Next?

- Learn about [project structure](/docs/getting-started/project-structure)
- Explore [native APIs](/docs/guides/native-apis) like file dialogs and notifications
- [Build and bundle](/docs/guides/building) your app for distribution
- Set up [auto-updates](/docs/guides/auto-updates) for your app
- [Migrate from Electron](/docs/migration/from-electron) or [Tauri](/docs/migration/from-tauri)
