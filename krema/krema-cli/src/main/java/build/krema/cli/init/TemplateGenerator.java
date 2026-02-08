package build.krema.cli.init;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Generates project files based on InitConfig.
 */
public class TemplateGenerator {

    private final InitConfig config;
    private final boolean force;
    private Path projectDir;

    public TemplateGenerator(InitConfig config, boolean force) {
        this.config = config;
        this.force = force;
    }

    /**
     * Generate the project structure and files.
     */
    public void generate() throws IOException {
        projectDir = resolveProjectDir();
        System.out.println("Creating Krema project: " + projectDir.toAbsolutePath());

        createDirectoryStructure();
        createConfigFile();
        createPomXml();
        createJavaFiles();
        createFrontendFiles();

        printSuccessMessage();
    }

    private Path resolveProjectDir() throws IOException {
        String appName = config.getAppName();
        Path dir = ".".equals(appName) ? Path.of(".") : Path.of(appName);

        if (!".".equals(appName) && Files.exists(dir) && !force) {
            throw new IOException("Directory already exists: " + dir + ". Use --force to overwrite.");
        }

        return dir;
    }

    private void createDirectoryStructure() throws IOException {
        Files.createDirectories(projectDir.resolve("src"));
        Files.createDirectories(projectDir.resolve("src-java/" + config.getJavaPackagePath()));
        Files.createDirectories(projectDir.resolve("icons"));
        Files.createDirectories(projectDir.resolve("src/main/resources"));

        if ("angular".equalsIgnoreCase(config.getTemplate())) {
            Files.createDirectories(projectDir.resolve("src/app"));
        }
    }

    private void createConfigFile() throws IOException {
        String devCommand = "npm run dev";
        String devUrl = "http://localhost:5173";
        String outDir = "dist";

        switch (config.getTemplate().toLowerCase()) {
            case "angular" -> {
                devCommand = "npm start";
                devUrl = "http://localhost:4200";
                outDir = "dist/" + config.getPackageName() + "/browser";
            }
            case "react", "vue", "svelte" -> {
                // Default Vite settings
            }
        }

        // Collect all permissions (base + plugin requirements)
        String permissionsArray = config.getAllPermissions().stream()
                .map(p -> "\"" + p + "\"")
                .reduce((a, b) -> a + ",\n    " + b)
                .orElse("");

        // Generate plugin configuration sections
        StringBuilder pluginConfigs = new StringBuilder();
        for (InitConfig.Plugin plugin : config.getPlugins()) {
            if (!plugin.isBuiltIn()) {
                pluginConfigs.append("""

            [plugins.%s]
            enabled = true
            """.formatted(plugin.getId()));
            }
        }

        String tomlConfig = """
            [package]
            name = "%s"
            version = "0.1.0"
            identifier = "%s"
            description = "%s"

            [window]
            title = "%s"
            width = %d
            height = %d
            min_width = 800
            min_height = 600
            resizable = %s

            [build]
            frontend_command = "npm run build"
            frontend_dev_command = "%s"
            frontend_dev_url = "%s"
            out_dir = "%s"
            java_source_dir = "src-java"
            main_class = "%s.Main"

            [bundle]
            icon = "icons/icon.icns"
            identifier = "%s"

            [permissions]
            allow = [
                %s
            ]
            %s
            # Environment overrides - uncomment and customize per profile
            # [env.development.window]
            # title = "%s (Dev)"
            #
            # [env.production.updater]
            # check_on_startup = true
            """.formatted(
                config.getAppName(),
                config.getIdentifier(),
                config.getDescription(),
                config.getWindowTitle(),
                config.getWindowWidth(),
                config.getWindowHeight(),
                config.isResizable(),
                devCommand,
                devUrl,
                outDir,
                config.getJavaPackage(),
                config.getIdentifier(),
                permissionsArray,
                pluginConfigs.toString(),
                config.getAppName()
        );

        Files.writeString(projectDir.resolve("krema.toml"), tomlConfig);
        System.out.println("  Created krema.toml");
    }

    private void createPomXml() throws IOException {
        // Generate external plugin dependencies
        StringBuilder pluginDependencies = new StringBuilder();
        for (InitConfig.Plugin plugin : config.getExternalPlugins()) {
            pluginDependencies.append("""
                    <dependency>
                        <groupId>build.krema</groupId>
                        <artifactId>%s</artifactId>
                        <version>${krema.version}</version>
                    </dependency>
            """.formatted(plugin.getArtifactId()));
        }

        String pomXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0"
                     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                     xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
                <modelVersion>4.0.0</modelVersion>

                <groupId>%s</groupId>
                <artifactId>%s</artifactId>
                <version>0.1.0</version>
                <packaging>jar</packaging>

                <name>%s</name>
                <description>%s</description>

                <properties>
                    <maven.compiler.release>25</maven.compiler.release>
                    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
                    <krema.version>0.1.0-SNAPSHOT</krema.version>
                </properties>

                <dependencies>
                    <dependency>
                        <groupId>build.krema</groupId>
                        <artifactId>krema-core</artifactId>
                        <version>${krema.version}</version>
                    </dependency>
            %s</dependencies>

                <build>
                    <sourceDirectory>src-java</sourceDirectory>
                    <plugins>
                        <plugin>
                            <groupId>org.apache.maven.plugins</groupId>
                            <artifactId>maven-compiler-plugin</artifactId>
                            <version>3.13.0</version>
                            <configuration>
                                <release>25</release>
                                <parameters>true</parameters>
                            </configuration>
                        </plugin>
                        <plugin>
                            <groupId>org.apache.maven.plugins</groupId>
                            <artifactId>maven-jar-plugin</artifactId>
                            <version>3.4.1</version>
                            <configuration>
                                <archive>
                                    <manifest>
                                        <mainClass>%s.Main</mainClass>
                                    </manifest>
                                </archive>
                            </configuration>
                        </plugin>
                    </plugins>
                </build>
            </project>
            """.formatted(
                getGroupIdFromPackage(),
                config.getArtifactId(),
                config.getAppName(),
                config.getDescription(),
                pluginDependencies.toString(),
                config.getJavaPackage()
        );

        Files.writeString(projectDir.resolve("pom.xml"), pomXml);
        System.out.println("  Created pom.xml");
    }

    private String getGroupIdFromPackage() {
        String pkg = config.getJavaPackage();
        int lastDot = pkg.lastIndexOf('.');
        return lastDot > 0 ? pkg.substring(0, lastDot) : pkg;
    }

    private void createJavaFiles() throws IOException {
        String packagePath = config.getJavaPackagePath();

        // Main.java
        String mainJava = """
            package %s;

            import build.krema.Krema;

            public class Main {

                public static void main(String[] args) {
                    boolean devMode = java.util.Arrays.asList(args).contains("--dev");
                    String devUrl = System.getenv("KREMA_DEV_URL");

                    var app = Krema.app()
                        .title("%s")
                        .size(%d, %d)
                        .commands(new Commands());

                    if (devMode && devUrl != null) {
                        app.devUrl(devUrl);
                    }

                    app.run();
                }
            }
            """.formatted(
                config.getJavaPackage(),
                config.getWindowTitle(),
                config.getWindowWidth(),
                config.getWindowHeight()
        );

        Files.writeString(projectDir.resolve("src-java/" + packagePath + "/Main.java"), mainJava);
        System.out.println("  Created src-java/" + packagePath + "/Main.java");

        // Commands.java
        String commandsJava = """
            package %s;

            import build.krema.KremaCommand;

            public class Commands {

                @KremaCommand
                public String greet(String name) {
                    return "Hello, " + name + "!";
                }

                @KremaCommand
                public SystemInfo getSystemInfo() {
                    return new SystemInfo(
                        System.getProperty("os.name"),
                        System.getProperty("java.version"),
                        Runtime.getRuntime().availableProcessors()
                    );
                }

                public record SystemInfo(String os, String javaVersion, int processors) {}
            }
            """.formatted(config.getJavaPackage());

        Files.writeString(projectDir.resolve("src-java/" + packagePath + "/Commands.java"), commandsJava);
        System.out.println("  Created src-java/" + packagePath + "/Commands.java");
    }

    private void createFrontendFiles() throws IOException {
        switch (config.getTemplate().toLowerCase()) {
            case "angular" -> createAngularFiles();
            case "react" -> createReactFiles();
            case "vue" -> createVueFiles();
            case "svelte" -> createSvelteFiles();
            default -> createVanillaFiles();
        }
    }

    private void createVanillaFiles() throws IOException {
        // package.json
        String packageJson = """
            {
              "name": "%s",
              "private": true,
              "version": "0.1.0",
              "type": "module",
              "scripts": {
                "dev": "vite",
                "build": "vite build",
                "preview": "vite preview"
              },
              "devDependencies": {
                "vite": "^7.0.0"
              }
            }
            """.formatted(config.getPackageName());

        Files.writeString(projectDir.resolve("package.json"), packageJson);
        System.out.println("  Created package.json");

        // vite.config.js
        String viteConfig = """
            import { defineConfig } from 'vite'

            export default defineConfig({
              server: {
                port: 5173,
                strictPort: true
              },
              build: {
                outDir: 'dist',
                emptyOutDir: true
              }
            })
            """;

        Files.writeString(projectDir.resolve("vite.config.js"), viteConfig);
        System.out.println("  Created vite.config.js");

        // index.html
        String indexHtml = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>%s</title>
                <link rel="stylesheet" href="/src/style.css">
            </head>
            <body>
                <div id="app">
                    <h1>%s</h1>
                    <p>Welcome to your Krema app!</p>

                    <div class="card">
                        <input type="text" id="name" placeholder="Enter your name" value="World">
                        <button id="greetBtn">Greet</button>
                        <p id="greeting"></p>
                    </div>

                    <div class="card">
                        <button id="infoBtn">Get System Info</button>
                        <pre id="info"></pre>
                    </div>
                </div>
                <script type="module" src="/src/main.js"></script>
            </body>
            </html>
            """.formatted(config.getWindowTitle(), config.getWindowTitle());

        Files.writeString(projectDir.resolve("index.html"), indexHtml);
        System.out.println("  Created index.html");

        // src/main.js
        String mainJs = """
            import './style.css'

            document.getElementById('greetBtn').addEventListener('click', async () => {
                const name = document.getElementById('name').value || 'World';
                try {
                    const result = await window.krema.invoke('greet', { name });
                    document.getElementById('greeting').textContent = result;
                } catch (e) {
                    document.getElementById('greeting').textContent = 'Error: ' + e.message;
                }
            });

            document.getElementById('infoBtn').addEventListener('click', async () => {
                try {
                    const result = await window.krema.invoke('getSystemInfo', {});
                    document.getElementById('info').textContent = JSON.stringify(result, null, 2);
                } catch (e) {
                    document.getElementById('info').textContent = 'Error: ' + e.message;
                }
            });
            """;

        Files.writeString(projectDir.resolve("src/main.js"), mainJs);
        System.out.println("  Created src/main.js");

        // src/style.css
        writeCommonStyles("src/style.css");

        // .gitignore
        writeGitignore(false);
    }

    private void createAngularFiles() throws IOException {
        // package.json
        String packageJson = """
            {
              "name": "%s",
              "private": true,
              "version": "0.1.0",
              "scripts": {
                "ng": "ng",
                "start": "ng serve",
                "build": "ng build",
                "watch": "ng build --watch --configuration development"
              },
              "dependencies": {
                "@angular/common": "^21.0.0",
                "@angular/compiler": "^21.0.0",
                "@angular/core": "^21.0.0",
                "@angular/forms": "^21.0.0",
                "@angular/platform-browser": "^21.0.0",
                "rxjs": "~7.8.0",
                "tslib": "^2.3.0"
              },
              "devDependencies": {
                "@angular/build": "^21.0.0",
                "@angular/cli": "^21.0.0",
                "@angular/compiler-cli": "^21.0.0",
                "typescript": "~5.9.0"
              }
            }
            """.formatted(config.getPackageName());

        Files.writeString(projectDir.resolve("package.json"), packageJson);
        System.out.println("  Created package.json");

        // angular.json
        String angularJson = """
            {
              "$schema": "./node_modules/@angular/cli/lib/config/schema.json",
              "version": 1,
              "newProjectRoot": "projects",
              "projects": {
                "%s": {
                  "projectType": "application",
                  "root": "",
                  "sourceRoot": "src",
                  "prefix": "app",
                  "architect": {
                    "build": {
                      "builder": "@angular/build:application",
                      "options": {
                        "outputPath": "dist/%s",
                        "index": "src/index.html",
                        "browser": "src/main.ts",
                        "polyfills": [],
                        "tsConfig": "tsconfig.app.json",
                        "assets": [],
                        "styles": ["src/styles.css"],
                        "scripts": []
                      },
                      "configurations": {
                        "production": {
                          "budgets": [
                            {
                              "type": "initial",
                              "maximumWarning": "500kB",
                              "maximumError": "1MB"
                            }
                          ],
                          "outputHashing": "all"
                        },
                        "development": {
                          "optimization": false,
                          "extractLicenses": false,
                          "sourceMap": true
                        }
                      },
                      "defaultConfiguration": "production"
                    },
                    "serve": {
                      "builder": "@angular/build:dev-server",
                      "configurations": {
                        "production": {
                          "buildTarget": "%s:build:production"
                        },
                        "development": {
                          "buildTarget": "%s:build:development"
                        }
                      },
                      "defaultConfiguration": "development"
                    }
                  }
                }
              }
            }
            """.formatted(config.getPackageName(), config.getPackageName(), config.getPackageName(), config.getPackageName());

        Files.writeString(projectDir.resolve("angular.json"), angularJson);
        System.out.println("  Created angular.json");

        // tsconfig.json
        String tsconfig = """
            {
              "compileOnSave": false,
              "compilerOptions": {
                "outDir": "./dist/out-tsc",
                "strict": true,
                "noImplicitOverride": true,
                "noPropertyAccessFromIndexSignature": true,
                "noImplicitReturns": true,
                "noFallthroughCasesInSwitch": true,
                "skipLibCheck": true,
                "isolatedModules": true,
                "esModuleInterop": true,
                "sourceMap": true,
                "declaration": false,
                "experimentalDecorators": true,
                "moduleResolution": "bundler",
                "importHelpers": true,
                "target": "ES2022",
                "module": "ES2022",
                "useDefineForClassFields": false,
                "lib": ["ES2022", "dom"]
              },
              "angularCompilerOptions": {
                "enableI18nLegacyMessageIdFormat": false,
                "strictInjectionParameters": true,
                "strictInputAccessModifiers": true,
                "strictTemplates": true
              }
            }
            """;

        Files.writeString(projectDir.resolve("tsconfig.json"), tsconfig);
        System.out.println("  Created tsconfig.json");

        // tsconfig.app.json
        String tsconfigApp = """
            {
              "extends": "./tsconfig.json",
              "compilerOptions": {
                "outDir": "./out-tsc/app",
                "types": []
              },
              "files": ["src/main.ts"],
              "include": ["src/**/*.d.ts"]
            }
            """;

        Files.writeString(projectDir.resolve("tsconfig.app.json"), tsconfigApp);
        System.out.println("  Created tsconfig.app.json");

        // src/index.html
        String indexHtml = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
              <meta charset="utf-8">
              <title>%s</title>
              <base href="/">
              <meta name="viewport" content="width=device-width, initial-scale=1">
            </head>
            <body>
              <app-root></app-root>
            </body>
            </html>
            """.formatted(config.getWindowTitle());

        Files.writeString(projectDir.resolve("src/index.html"), indexHtml);
        System.out.println("  Created src/index.html");

        // src/main.ts
        String mainTs = """
            import { bootstrapApplication } from '@angular/platform-browser';
            import { AppComponent } from './app/app.component';

            bootstrapApplication(AppComponent).catch(err => console.error(err));
            """;

        Files.writeString(projectDir.resolve("src/main.ts"), mainTs);
        System.out.println("  Created src/main.ts");

        // src/styles.css
        writeCommonStyles("src/styles.css");

        // src/app/app.component.ts
        String appComponent = """
            import { Component } from '@angular/core';
            import { FormsModule } from '@angular/forms';
            import { CommonModule } from '@angular/common';

            @Component({
              selector: 'app-root',
              imports: [FormsModule, CommonModule],
              template: `
                <div class="app">
                  <h1>%s</h1>
                  <p>Welcome to your Krema + Angular app!</p>

                  <div class="card">
                    <input
                      type="text"
                      [(ngModel)]="name"
                      placeholder="Enter your name"
                      (keyup.enter)="greet()"
                    />
                    <button (click)="greet()">Greet</button>
                    <p *ngIf="greeting" class="greeting">{{ greeting }}</p>
                  </div>

                  <div class="card">
                    <button (click)="getSystemInfo()">Get System Info</button>
                    <pre *ngIf="systemInfo">{{ systemInfo | json }}</pre>
                  </div>
                </div>
              `,
              styles: []
            })
            export class AppComponent {
              name = 'World';
              greeting = '';
              systemInfo: any = null;

              async greet() {
                try {
                  this.greeting = await (window as any).krema.invoke('greet', { name: this.name });
                } catch (e: any) {
                  this.greeting = 'Error: ' + e.message;
                }
              }

              async getSystemInfo() {
                try {
                  this.systemInfo = await (window as any).krema.invoke('getSystemInfo', {});
                } catch (e: any) {
                  this.systemInfo = { error: e.message };
                }
              }
            }
            """.formatted(config.getWindowTitle());

        Files.writeString(projectDir.resolve("src/app/app.component.ts"), appComponent);
        System.out.println("  Created src/app/app.component.ts");

        // src/krema.d.ts
        writeKremaTypes("src/krema.d.ts");

        // .gitignore
        writeGitignore(true);
    }

    private void createReactFiles() throws IOException {
        boolean useTs = config.isTypescript();

        // package.json
        String packageJson = useTs ? """
            {
              "name": "%s",
              "private": true,
              "version": "0.1.0",
              "type": "module",
              "scripts": {
                "dev": "vite",
                "build": "vite build",
                "preview": "vite preview"
              },
              "dependencies": {
                "react": "^19.0.0",
                "react-dom": "^19.0.0"
              },
              "devDependencies": {
                "@types/react": "^19.0.0",
                "@types/react-dom": "^19.0.0",
                "@vitejs/plugin-react": "^5.0.0",
                "typescript": "^5.8.0",
                "vite": "^7.0.0"
              }
            }
            """.formatted(config.getPackageName()) : """
            {
              "name": "%s",
              "private": true,
              "version": "0.1.0",
              "type": "module",
              "scripts": {
                "dev": "vite",
                "build": "vite build",
                "preview": "vite preview"
              },
              "dependencies": {
                "react": "^19.0.0",
                "react-dom": "^19.0.0"
              },
              "devDependencies": {
                "@vitejs/plugin-react": "^5.0.0",
                "vite": "^7.0.0"
              }
            }
            """.formatted(config.getPackageName());

        Files.writeString(projectDir.resolve("package.json"), packageJson);
        System.out.println("  Created package.json");

        // vite.config
        String viteConfig = """
            import { defineConfig } from 'vite'
            import react from '@vitejs/plugin-react'

            export default defineConfig({
              plugins: [react()],
              server: {
                port: 5173,
                strictPort: true
              }
            })
            """;

        String viteConfigFile = useTs ? "vite.config.ts" : "vite.config.js";
        Files.writeString(projectDir.resolve(viteConfigFile), viteConfig);
        System.out.println("  Created " + viteConfigFile);

        // index.html
        String mainExt = useTs ? "tsx" : "jsx";
        String indexHtml = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>%s</title>
            </head>
            <body>
                <div id="root"></div>
                <script type="module" src="/src/main.%s"></script>
            </body>
            </html>
            """.formatted(config.getWindowTitle(), mainExt);

        Files.writeString(projectDir.resolve("index.html"), indexHtml);
        System.out.println("  Created index.html");

        // src/main.tsx or jsx
        String mainTsx = """
            import React from 'react'
            import ReactDOM from 'react-dom/client'
            import App from './App'
            import './index.css'

            ReactDOM.createRoot(document.getElementById('root')!).render(
              <React.StrictMode>
                <App />
              </React.StrictMode>,
            )
            """;

        String mainJsx = """
            import React from 'react'
            import ReactDOM from 'react-dom/client'
            import App from './App'
            import './index.css'

            ReactDOM.createRoot(document.getElementById('root')).render(
              <React.StrictMode>
                <App />
              </React.StrictMode>,
            )
            """;

        Files.writeString(projectDir.resolve("src/main." + mainExt), useTs ? mainTsx : mainJsx);
        System.out.println("  Created src/main." + mainExt);

        // src/App.tsx or jsx
        String appExt = useTs ? "tsx" : "jsx";
        String appTsx = """
            import { useState } from 'react'

            function App() {
              const [name, setName] = useState('World')
              const [greeting, setGreeting] = useState('')
              const [systemInfo, setSystemInfo] = useState<any>(null)

              const greet = async () => {
                try {
                  const result = await window.krema.invoke<string>('greet', { name })
                  setGreeting(result)
                } catch (e: any) {
                  setGreeting('Error: ' + e.message)
                }
              }

              const getSystemInfo = async () => {
                try {
                  const result = await window.krema.invoke('getSystemInfo', {})
                  setSystemInfo(result)
                } catch (e: any) {
                  setSystemInfo({ error: e.message })
                }
              }

              return (
                <div className="app">
                  <h1>%s</h1>
                  <p>Welcome to your Krema + React app!</p>

                  <div className="card">
                    <input
                      type="text"
                      value={name}
                      onChange={(e) => setName(e.target.value)}
                      placeholder="Enter your name"
                    />
                    <button onClick={greet}>Greet</button>
                    {greeting && <p className="greeting">{greeting}</p>}
                  </div>

                  <div className="card">
                    <button onClick={getSystemInfo}>Get System Info</button>
                    {systemInfo && <pre>{JSON.stringify(systemInfo, null, 2)}</pre>}
                  </div>
                </div>
              )
            }

            export default App
            """.formatted(config.getWindowTitle());

        String appJsx = """
            import { useState } from 'react'

            function App() {
              const [name, setName] = useState('World')
              const [greeting, setGreeting] = useState('')
              const [systemInfo, setSystemInfo] = useState(null)

              const greet = async () => {
                try {
                  const result = await window.krema.invoke('greet', { name })
                  setGreeting(result)
                } catch (e) {
                  setGreeting('Error: ' + e.message)
                }
              }

              const getSystemInfo = async () => {
                try {
                  const result = await window.krema.invoke('getSystemInfo', {})
                  setSystemInfo(result)
                } catch (e) {
                  setSystemInfo({ error: e.message })
                }
              }

              return (
                <div className="app">
                  <h1>%s</h1>
                  <p>Welcome to your Krema + React app!</p>

                  <div className="card">
                    <input
                      type="text"
                      value={name}
                      onChange={(e) => setName(e.target.value)}
                      placeholder="Enter your name"
                    />
                    <button onClick={greet}>Greet</button>
                    {greeting && <p className="greeting">{greeting}</p>}
                  </div>

                  <div className="card">
                    <button onClick={getSystemInfo}>Get System Info</button>
                    {systemInfo && <pre>{JSON.stringify(systemInfo, null, 2)}</pre>}
                  </div>
                </div>
              )
            }

            export default App
            """.formatted(config.getWindowTitle());

        Files.writeString(projectDir.resolve("src/App." + appExt), useTs ? appTsx : appJsx);
        System.out.println("  Created src/App." + appExt);

        // src/index.css
        writeCommonStyles("src/index.css");

        if (useTs) {
            // src/krema.d.ts
            writeKremaTypes("src/krema.d.ts");

            // tsconfig.json
            String tsconfig = """
                {
                  "compilerOptions": {
                    "target": "ES2020",
                    "useDefineForClassFields": true,
                    "lib": ["ES2020", "DOM", "DOM.Iterable"],
                    "module": "ESNext",
                    "skipLibCheck": true,
                    "moduleResolution": "bundler",
                    "allowImportingTsExtensions": true,
                    "resolveJsonModule": true,
                    "isolatedModules": true,
                    "noEmit": true,
                    "jsx": "react-jsx",
                    "strict": true,
                    "noUnusedLocals": true,
                    "noUnusedParameters": true,
                    "noFallthroughCasesInSwitch": true
                  },
                  "include": ["src"]
                }
                """;

            Files.writeString(projectDir.resolve("tsconfig.json"), tsconfig);
            System.out.println("  Created tsconfig.json");
        }

        // .gitignore
        writeGitignore(false);
    }

    private void createVueFiles() throws IOException {
        boolean useTs = config.isTypescript();

        // package.json
        String packageJson = useTs ? """
            {
              "name": "%s",
              "private": true,
              "version": "0.1.0",
              "type": "module",
              "scripts": {
                "dev": "vite",
                "build": "vue-tsc && vite build",
                "preview": "vite preview"
              },
              "dependencies": {
                "vue": "^3.4.0"
              },
              "devDependencies": {
                "@vitejs/plugin-vue": "^6.0.0",
                "typescript": "^5.8.0",
                "vite": "^7.0.0",
                "vue-tsc": "^3.0.0"
              }
            }
            """.formatted(config.getPackageName()) : """
            {
              "name": "%s",
              "private": true,
              "version": "0.1.0",
              "type": "module",
              "scripts": {
                "dev": "vite",
                "build": "vite build",
                "preview": "vite preview"
              },
              "dependencies": {
                "vue": "^3.4.0"
              },
              "devDependencies": {
                "@vitejs/plugin-vue": "^6.0.0",
                "vite": "^7.0.0"
              }
            }
            """.formatted(config.getPackageName());

        Files.writeString(projectDir.resolve("package.json"), packageJson);
        System.out.println("  Created package.json");

        // vite.config
        String viteConfig = """
            import { defineConfig } from 'vite'
            import vue from '@vitejs/plugin-vue'

            export default defineConfig({
              plugins: [vue()],
              server: {
                port: 5173,
                strictPort: true
              }
            })
            """;

        String viteConfigFile = useTs ? "vite.config.ts" : "vite.config.js";
        Files.writeString(projectDir.resolve(viteConfigFile), viteConfig);
        System.out.println("  Created " + viteConfigFile);

        // index.html
        String mainExt = useTs ? "ts" : "js";
        String indexHtml = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>%s</title>
            </head>
            <body>
                <div id="app"></div>
                <script type="module" src="/src/main.%s"></script>
            </body>
            </html>
            """.formatted(config.getWindowTitle(), mainExt);

        Files.writeString(projectDir.resolve("index.html"), indexHtml);
        System.out.println("  Created index.html");

        // src/main.ts or js
        String mainTs = """
            import { createApp } from 'vue'
            import App from './App.vue'
            import './style.css'

            createApp(App).mount('#app')
            """;

        Files.writeString(projectDir.resolve("src/main." + mainExt), mainTs);
        System.out.println("  Created src/main." + mainExt);

        // src/App.vue
        String scriptLang = useTs ? " lang=\"ts\"" : "";
        String appVue = """
            <script setup%s>
            import { ref } from 'vue'

            const name = ref('World')
            const greeting = ref('')
            const systemInfo = ref%s(null)

            async function greet() {
              try {
                greeting.value = await window.krema.invoke%s('greet', { name: name.value })
              } catch (e%s) {
                greeting.value = 'Error: ' + e.message
              }
            }

            async function getSystemInfo() {
              try {
                systemInfo.value = await window.krema.invoke('getSystemInfo', {})
              } catch (e%s) {
                systemInfo.value = { error: e.message }
              }
            }
            </script>

            <template>
              <div class="app">
                <h1>%s</h1>
                <p>Welcome to your Krema + Vue app!</p>

                <div class="card">
                  <input v-model="name" placeholder="Enter your name" @keyup.enter="greet" />
                  <button @click="greet">Greet</button>
                  <p v-if="greeting" class="greeting">{{ greeting }}</p>
                </div>

                <div class="card">
                  <button @click="getSystemInfo">Get System Info</button>
                  <pre v-if="systemInfo">{{ JSON.stringify(systemInfo, null, 2) }}</pre>
                </div>
              </div>
            </template>

            <style scoped>
            </style>
            """.formatted(
                scriptLang,
                useTs ? "<any>" : "",
                useTs ? "<string>" : "",
                useTs ? ": any" : "",
                useTs ? ": any" : "",
                config.getWindowTitle()
        );

        Files.writeString(projectDir.resolve("src/App.vue"), appVue);
        System.out.println("  Created src/App.vue");

        // src/style.css
        writeCommonStyles("src/style.css");

        if (useTs) {
            // src/krema.d.ts
            writeKremaTypes("src/krema.d.ts");

            // tsconfig.json
            String tsconfig = """
                {
                  "compilerOptions": {
                    "target": "ES2020",
                    "useDefineForClassFields": true,
                    "module": "ESNext",
                    "lib": ["ES2020", "DOM", "DOM.Iterable"],
                    "skipLibCheck": true,
                    "moduleResolution": "bundler",
                    "allowImportingTsExtensions": true,
                    "resolveJsonModule": true,
                    "isolatedModules": true,
                    "noEmit": true,
                    "jsx": "preserve",
                    "strict": true,
                    "noUnusedLocals": true,
                    "noUnusedParameters": true,
                    "noFallthroughCasesInSwitch": true
                  },
                  "include": ["src/**/*.ts", "src/**/*.tsx", "src/**/*.vue"]
                }
                """;

            Files.writeString(projectDir.resolve("tsconfig.json"), tsconfig);
            System.out.println("  Created tsconfig.json");
        }

        // .gitignore
        writeGitignore(false);
    }

    private void createSvelteFiles() throws IOException {
        boolean useTs = config.isTypescript();

        // package.json
        String packageJson = useTs ? """
            {
              "name": "%s",
              "private": true,
              "version": "0.1.0",
              "type": "module",
              "scripts": {
                "dev": "vite",
                "build": "vite build",
                "preview": "vite preview",
                "check": "svelte-check --tsconfig ./tsconfig.json"
              },
              "devDependencies": {
                "@sveltejs/vite-plugin-svelte": "^6.0.0",
                "svelte": "^5.0.0",
                "svelte-check": "^4.0.0",
                "typescript": "^5.8.0",
                "vite": "^7.0.0"
              }
            }
            """.formatted(config.getPackageName()) : """
            {
              "name": "%s",
              "private": true,
              "version": "0.1.0",
              "type": "module",
              "scripts": {
                "dev": "vite",
                "build": "vite build",
                "preview": "vite preview"
              },
              "devDependencies": {
                "@sveltejs/vite-plugin-svelte": "^6.0.0",
                "svelte": "^5.0.0",
                "vite": "^7.0.0"
              }
            }
            """.formatted(config.getPackageName());

        Files.writeString(projectDir.resolve("package.json"), packageJson);
        System.out.println("  Created package.json");

        // vite.config
        String viteConfig = """
            import { defineConfig } from 'vite'
            import { svelte } from '@sveltejs/vite-plugin-svelte'

            export default defineConfig({
              plugins: [svelte()],
              server: {
                port: 5173,
                strictPort: true
              }
            })
            """;

        String viteConfigFile = useTs ? "vite.config.ts" : "vite.config.js";
        Files.writeString(projectDir.resolve(viteConfigFile), viteConfig);
        System.out.println("  Created " + viteConfigFile);

        // svelte.config.js
        String svelteConfig = """
            import { vitePreprocess } from '@sveltejs/vite-plugin-svelte'

            export default {
              preprocess: vitePreprocess()
            }
            """;

        Files.writeString(projectDir.resolve("svelte.config.js"), svelteConfig);
        System.out.println("  Created svelte.config.js");

        // index.html
        String mainExt = useTs ? "ts" : "js";
        String indexHtml = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>%s</title>
            </head>
            <body>
                <div id="app"></div>
                <script type="module" src="/src/main.%s"></script>
            </body>
            </html>
            """.formatted(config.getWindowTitle(), mainExt);

        Files.writeString(projectDir.resolve("index.html"), indexHtml);
        System.out.println("  Created index.html");

        // src/main.ts or js
        String mainTs = """
            import { mount } from 'svelte'
            import App from './App.svelte'
            import './app.css'

            const app = mount(App, {
              target: document.getElementById('app')%s
            })

            export default app
            """.formatted(useTs ? "!" : "");

        Files.writeString(projectDir.resolve("src/main." + mainExt), mainTs);
        System.out.println("  Created src/main." + mainExt);

        // src/App.svelte
        String scriptLang = useTs ? " lang=\"ts\"" : "";
        String appSvelte = """
            <script%s>
              let name = $state('World')
              let greeting = $state('')
              let systemInfo = $state%s(null)

              async function greet() {
                try {
                  greeting = await window.krema.invoke%s('greet', { name })
                } catch (e%s) {
                  greeting = 'Error: ' + e.message
                }
              }

              async function getSystemInfo() {
                try {
                  systemInfo = await window.krema.invoke('getSystemInfo', {})
                } catch (e%s) {
                  systemInfo = { error: e.message }
                }
              }
            </script>

            <div class="app">
              <h1>%s</h1>
              <p>Welcome to your Krema + Svelte app!</p>

              <div class="card">
                <input
                  type="text"
                  bind:value={name}
                  placeholder="Enter your name"
                  onkeydown={(e) => e.key === 'Enter' && greet()}
                />
                <button onclick={greet}>Greet</button>
                {#if greeting}
                  <p class="greeting">{greeting}</p>
                {/if}
              </div>

              <div class="card">
                <button onclick={getSystemInfo}>Get System Info</button>
                {#if systemInfo}
                  <pre>{JSON.stringify(systemInfo, null, 2)}</pre>
                {/if}
              </div>
            </div>
            """.formatted(
                scriptLang,
                useTs ? "<any>" : "",
                useTs ? "<string>" : "",
                useTs ? ": any" : "",
                useTs ? ": any" : "",
                config.getWindowTitle()
        );

        Files.writeString(projectDir.resolve("src/App.svelte"), appSvelte);
        System.out.println("  Created src/App.svelte");

        // src/app.css
        writeCommonStyles("src/app.css");

        if (useTs) {
            // src/krema.d.ts
            writeKremaTypes("src/krema.d.ts");

            // tsconfig.json
            String tsconfig = """
                {
                  "compilerOptions": {
                    "target": "ESNext",
                    "useDefineForClassFields": true,
                    "module": "ESNext",
                    "moduleResolution": "bundler",
                    "lib": ["ESNext", "DOM", "DOM.Iterable"],
                    "resolveJsonModule": true,
                    "allowJs": true,
                    "checkJs": true,
                    "isolatedModules": true,
                    "skipLibCheck": true,
                    "strict": true,
                    "noEmit": true,
                    "verbatimModuleSyntax": true
                  },
                  "include": ["src/**/*.ts", "src/**/*.svelte"],
                  "references": [{ "path": "./tsconfig.node.json" }]
                }
                """;

            Files.writeString(projectDir.resolve("tsconfig.json"), tsconfig);
            System.out.println("  Created tsconfig.json");

            String tsconfigNode = """
                {
                  "compilerOptions": {
                    "composite": true,
                    "skipLibCheck": true,
                    "module": "ESNext",
                    "moduleResolution": "bundler",
                    "strict": true
                  },
                  "include": ["vite.config.ts"]
                }
                """;

            Files.writeString(projectDir.resolve("tsconfig.node.json"), tsconfigNode);
            System.out.println("  Created tsconfig.node.json");
        }

        // .gitignore
        writeGitignore(false);
    }

    private void writeCommonStyles(String path) throws IOException {
        String css = """
            :root {
              font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
              line-height: 1.5;
              font-weight: 400;
              color: #213547;
              background-color: #ffffff;
            }

            body {
              margin: 0;
              min-height: 100vh;
              display: flex;
              justify-content: center;
              padding: 2rem;
            }

            .app, #app {
              max-width: 600px;
              text-align: center;
            }

            h1 {
              font-size: 2.5rem;
              margin-bottom: 1rem;
            }

            .card {
              padding: 1.5rem;
              margin: 1rem 0;
              background: #f9fafb;
              border-radius: 8px;
              text-align: left;
            }

            input {
              padding: 0.6rem 1rem;
              font-size: 1rem;
              border: 1px solid #d1d5db;
              border-radius: 4px;
              margin-right: 0.5rem;
            }

            button {
              padding: 0.6rem 1.2rem;
              font-size: 1rem;
              font-weight: 500;
              color: white;
              background: #3b82f6;
              border: none;
              border-radius: 4px;
              cursor: pointer;
              transition: background 0.2s;
            }

            button:hover {
              background: #2563eb;
            }

            pre {
              background: #1e293b;
              color: #e2e8f0;
              padding: 1rem;
              border-radius: 4px;
              overflow-x: auto;
              font-size: 0.875rem;
              margin-top: 1rem;
            }

            .greeting {
              margin-top: 1rem;
              font-weight: 500;
            }
            """;

        Files.writeString(projectDir.resolve(path), css);
        System.out.println("  Created " + path);
    }

    private void writeKremaTypes(String path) throws IOException {
        String types = """
            interface Krema {
              invoke<T = any>(command: string, args?: Record<string, any>): Promise<T>;
              on(event: string, callback: (data: any) => void): () => void;
            }

            declare global {
              interface Window {
                krema: Krema;
              }
            }

            export {};
            """;

        Files.writeString(projectDir.resolve(path), types);
        System.out.println("  Created " + path);
    }

    private void writeGitignore(boolean includeAngular) throws IOException {
        String gitignore = includeAngular ? """
            node_modules/
            dist/
            target/
            .angular/
            .DS_Store
            *.log
            """ : """
            node_modules/
            dist/
            target/
            .DS_Store
            *.log
            """;

        Files.writeString(projectDir.resolve(".gitignore"), gitignore);
        System.out.println("  Created .gitignore");
    }

    private void printSuccessMessage() {
        System.out.println();
        System.out.println("Project created successfully!");
        System.out.println();
        System.out.println("Next steps:");
        System.out.println("  cd " + projectDir.getFileName());
        System.out.println("  " + config.getPackageManager() + " install");
        System.out.println("  krema dev");
    }
}
