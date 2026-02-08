import { Component, OnInit, OnDestroy, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

// Krema types are loaded from src/krema.d.ts

interface SystemInfo {
  osName: string;
  osVersion: string;
  osArch: string;
  javaVersion: string;
  javaVendor: string;
  processors: number;
  maxMemoryMb: number;
  totalMemoryMb: number;
  freeMemoryMb: number;
}

interface EnvironmentInfo {
  username: string;
  homeDir: string;
  workingDir: string;
  path: string;
  platform: string;
}

interface PathInfo {
  home: string;
  desktop: string;
  documents: string;
  downloads: string;
  temp: string;
  current: string;
}

interface FileInfo {
  name: string;
  path: string;
  isDirectory: boolean;
  size: number;
  lastModified: number;
}

interface CommandResult {
  code: number;
  stdout: string;
  stderr: string;
}

// Screen Info types
interface ScreenBounds {
  x: number;
  y: number;
  width: number;
  height: number;
}

interface ScreenInfo {
  name: string;
  frame: ScreenBounds;
  visibleFrame: ScreenBounds;
  scaleFactor: number;
  refreshRate: number;
  isPrimary: boolean;
}

interface CursorPosition {
  x: number;
  y: number;
}

// Drag and Drop types
interface DroppedFile {
  name: string;
  path: string | null;
  type: string;
  size: number;
}

interface FileDropEvent {
  payload: {
    type: string;
    files: DroppedFile[];
    x: number;
    y: number;
  };
}

interface FileDropHoverEvent {
  payload: {
    type: 'enter' | 'leave';
    x: number;
    y: number;
  };
}

// Menu types
interface MenuItem {
  id?: string;
  label?: string;
  accelerator?: string;
  type?: 'normal' | 'separator' | 'checkbox' | 'radio' | 'submenu';
  enabled?: boolean;
  checked?: boolean;
  role?: string;
  submenu?: MenuItem[];
}

interface MenuClickEvent {
  payload: {
    id: string;
  };
}

// FS Plugin types (from FsPlugin auto-discovered via ServiceLoader)
interface FsFileInfo {
  name: string;
  path: string;
  isDirectory: boolean;
  size: number;
  modifiedTime: number;
}

// Window Management types
interface WindowState {
  x: number;
  y: number;
  width: number;
  height: number;
  minimized: boolean;
  maximized: boolean;
  fullscreen: boolean;
  focused: boolean;
  visible: boolean;
}

@Component({
  selector: 'app-root',
  imports: [CommonModule, FormsModule],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css'
})
export class AppComponent implements OnInit, OnDestroy {
  title = 'Krema Angular Demo';

  // Navigation
  tabs = [
    { id: 'ipc', label: 'IPC' },
    { id: 'events', label: 'Events' },
    { id: 'native', label: 'Native APIs' },
    { id: 'screen', label: 'Screen' },
    { id: 'dragdrop', label: 'Drag & Drop' },
    { id: 'menus', label: 'Menus' },
    { id: 'window', label: 'Window' },
    { id: 'plugins', label: 'Plugins' },
    { id: 'system', label: 'System' },
    { id: 'clipboard', label: 'Clipboard' },
    { id: 'shell', label: 'Shell' },
    { id: 'files', label: 'Files' },
    { id: 'securestorage', label: 'Secure Storage' },
    { id: 'logging', label: 'Logging' }
  ];
  activeTab = 'ipc';

  // Greeting
  name = 'World';
  greeting = '';

  // Calculator
  num1 = 10;
  num2 = 5;
  operation = 'add';
  calcResult = '';

  // System info
  systemInfo: SystemInfo | null = null;
  envInfo: EnvironmentInfo | null = null;

  // Timer / Events
  timerCount = 0;
  timerRunning = false;
  lastEvent: any = null;
  private unsubscribers: (() => void)[] = [];

  // Clipboard
  clipboardText = 'Hello from Krema!';
  clipboardContent = '';

  // Shell
  commandInput = 'echo "Hello from Java shell!"';
  commandOutput = '';

  // Files
  paths: PathInfo | null = null;
  pathItems: { label: string; path: string }[] = [];
  currentPath = '';
  files: FileInfo[] = [];
  selectedFile: FileInfo | null = null;
  fileContent = '';

  // Error handling
  lastError = '';

  // Native APIs - Lifecycle
  lifecycleEvents: { event: string; timestamp: Date }[] = [];
  appReady = false;

  // Native APIs - Path utilities
  pathInput = '/Users/demo/documents/file.txt';
  pathResults: { label: string; value: string }[] = [];

  // Native APIs - App directories
  appDirs: { label: string; value: string }[] = [];

  // Native APIs - Dialog
  dialogResult = '';

  // Screen Info
  screens: ScreenInfo[] = [];
  primaryScreen: ScreenInfo | null = null;
  cursorPosition: CursorPosition | null = null;
  screenAtCursor: ScreenInfo | null = null;
  trackingCursor = false;
  private cursorTrackingInterval: any = null;

  // Drag and Drop
  dropEnabled = true;
  dropHovering = false;
  droppedFiles: DroppedFile[] = [];
  lastDropEvent: FileDropEvent | null = null;
  acceptedExtensions = '';
  dropZoneSelector = '';

  // Menus
  menuClickEvents: { id: string; timestamp: Date }[] = [];
  contextMenuResult = '';
  menuConfigured = false;

  // Window Management
  windowState: WindowState | null = null;
  windowTitle = '';
  windowOpacity = 1.0;
  newWidth = 1024;
  newHeight = 768;
  newX = 100;
  newY = 100;

  // Essential Plugins - OS Info
  osInfo: Record<string, unknown> | null = null;
  osMemory: Record<string, number> | null = null;
  osLocale: Record<string, string> | null = null;

  // Essential Plugins - Store
  storeKey = 'myKey';
  storeValue = 'myValue';
  storeResult: unknown = null;
  storeKeys: string[] = [];
  storeEntries: Record<string, unknown> = {};

  // Essential Plugins - Single Instance
  isPrimaryInstance = false;

  // Essential Plugins - Global Shortcut
  shortcutAccelerator = 'Cmd+Shift+K';
  registeredShortcuts: string[] = [];
  shortcutTriggered = '';

  // Dock Badge
  dockBadge = '5';
  currentDockBadge = '';
  dockSupported = false;

  // HTTP Client
  httpUrl = 'https://jsonplaceholder.typicode.com/todos/1';
  httpMethod = 'GET';
  httpBody = '{"title": "test", "completed": false}';
  httpResult: unknown = null;
  httpLoading = false;

  // Positioner Plugin
  positionerResult = '';

  // Frameless Window
  titleBarStyle = 'default';
  trafficLightX = 15;
  trafficLightY = 15;

  // FS Plugin
  fsPath = '/tmp';
  fsExistsResult: boolean | null = null;
  fsStat: FsFileInfo | null = null;
  fsFiles: FsFileInfo[] = [];
  fsCurrentDir = '';
  fsWritePath = '/tmp/krema-plugin-test.txt';
  fsWriteContent = 'Hello from Krema FS Plugin!';
  fsReadResult = '';
  fsPluginError = '';

  // Secure Storage
  ssKey = 'my-secret';
  ssValue = 'super-secret-value';
  ssResult = '';

  // Logging
  logMessage = 'Hello from Angular frontend!';
  logLevel = 'info';
  logHistory: { level: string; message: string; timestamp: Date }[] = [];
  logFilePath = '';

  // Multi-Window
  childWindowLabel = '';
  childWindowTitle = 'Child Window';
  childWindowUrl = '';
  windowList: string[] = [];
  windowMessageEvent = '';
  windowMessagePayload = '{"message": "Hello from main!"}';

  constructor(private cdr: ChangeDetectorRef) {}

  /**
   * Wrapper around this.invoke that triggers Angular change detection.
   * Angular 21 is zoneless by default, so async operations don't automatically
   * trigger change detection — we must call markForCheck() explicitly.
   */
  private async invoke<T = unknown>(command: string, args?: Record<string, unknown>): Promise<T> {
    try {
      const result = await window.krema.invoke<T>(command, args);
      this.cdr.markForCheck();
      return result;
    } catch (e) {
      this.cdr.markForCheck();
      throw e;
    }
  }

  ngOnInit(): void {
    this.setupEventListeners();
  }

  ngOnDestroy(): void {
    this.unsubscribers.forEach(unsub => unsub());
  }

  private setupEventListeners(): void {
    if (!window.krema) return;

    // Timer tick events
    const unsubTick = window.krema.on('timer-tick', (data: unknown) => {
      const d = data as { count: number };
      this.timerCount = d.count;
      this.lastEvent = data;
      this.cdr.markForCheck();
    });
    this.unsubscribers.push(unsubTick);

    // Timer stopped event
    const unsubStopped = window.krema.on('timer-stopped', (data: unknown) => {
      this.timerRunning = false;
      this.lastEvent = { event: 'timer-stopped', ...(data as Record<string, unknown>) };
      this.cdr.markForCheck();
    });
    this.unsubscribers.push(unsubStopped);

    // App lifecycle events
    const unsubReady = window.krema.on('app:ready', () => {
      this.appReady = true;
      this.lifecycleEvents.push({ event: 'app:ready', timestamp: new Date() });
      this.cdr.markForCheck();
    });
    this.unsubscribers.push(unsubReady);

    const unsubBeforeQuit = window.krema.on('app:before-quit', () => {
      this.lifecycleEvents.push({ event: 'app:before-quit', timestamp: new Date() });
      this.cdr.markForCheck();
    });
    this.unsubscribers.push(unsubBeforeQuit);

    // File drop events
    const unsubFileDrop = window.krema.on('file-drop', (data: unknown) => {
      const event = data as FileDropEvent;
      this.lastDropEvent = event;
      this.droppedFiles = event.payload.files;
      this.dropHovering = false;
      this.cdr.markForCheck();
    });
    this.unsubscribers.push(unsubFileDrop);

    const unsubFileDropHover = window.krema.on('file-drop-hover', (data: unknown) => {
      const event = data as FileDropHoverEvent;
      this.dropHovering = event.payload.type === 'enter';
      this.cdr.markForCheck();
    });
    this.unsubscribers.push(unsubFileDropHover);

    // Menu click events
    const unsubMenuClick = window.krema.on('menu:click', (data: unknown) => {
      const event = data as MenuClickEvent;
      this.menuClickEvents.unshift({ id: event.payload.id, timestamp: new Date() });
      // Keep only last 10 events
      if (this.menuClickEvents.length > 10) {
        this.menuClickEvents.pop();
      }
      this.cdr.markForCheck();
    });
    this.unsubscribers.push(unsubMenuClick);

    // Global shortcut triggered events
    const unsubShortcut = window.krema.on('shortcut:triggered', (data: unknown) => {
      const event = data as { payload: { accelerator: string } };
      this.shortcutTriggered = `${event.payload.accelerator} at ${new Date().toLocaleTimeString()}`;
      this.cdr.markForCheck();
    });
    this.unsubscribers.push(unsubShortcut);

    // Second instance events
    const unsubSecondInstance = window.krema.on('app:second-instance', (data: unknown) => {
      const event = data as { payload: { args: string[] } };
      this.lifecycleEvents.push({
        event: `second-instance: ${event.payload.args.join(' ')}`,
        timestamp: new Date()
      });
      this.cdr.markForCheck();
    });
    this.unsubscribers.push(unsubSecondInstance);

    // Custom inter-window messages
    const unsubCustomMessage = window.krema.on('custom-message', (data: unknown) => {
      this.lifecycleEvents.push({
        event: `custom-message: ${JSON.stringify(data)}`,
        timestamp: new Date()
      });
      this.cdr.markForCheck();
    });
    this.unsubscribers.push(unsubCustomMessage);

    // Broadcast messages
    const unsubBroadcast = window.krema.on('broadcast-message', (data: unknown) => {
      this.lifecycleEvents.push({
        event: `broadcast: ${JSON.stringify(data)}`,
        timestamp: new Date()
      });
      this.cdr.markForCheck();
    });
    this.unsubscribers.push(unsubBroadcast);
  }

  // ==================== Basic IPC ====================

  async greet(): Promise<void> {
    try {
      this.greeting = await this.invoke<string>('greet', { name: this.name });
      this.lastError = '';
    } catch (e: any) {
      this.lastError = e.message || 'Unknown error';
    }
  }

  async calculate(): Promise<void> {
    try {
      const result = await this.invoke<number>('calculate', {
        a: this.num1,
        b: this.num2,
        operation: this.operation
      });
      this.calcResult = `Result: ${result}`;
      this.lastError = '';
    } catch (e: any) {
      this.lastError = e.message || 'Unknown error';
      this.calcResult = '';
    }
  }

  // ==================== Events ====================

  async startTimer(): Promise<void> {
    try {
      await this.invoke('startTimer', {});
      this.timerRunning = true;
      this.lastError = '';
    } catch (e: any) {
      this.lastError = e.message || 'Unknown error';
    }
  }

  async stopTimer(): Promise<void> {
    try {
      await this.invoke('stopTimer', {});
      this.timerRunning = false;
      this.lastError = '';
    } catch (e: any) {
      this.lastError = e.message || 'Unknown error';
    }
  }

  // ==================== System Info ====================

  async getSystemInfo(): Promise<void> {
    try {
      this.systemInfo = await this.invoke<SystemInfo>('systemInfo', {});
      this.envInfo = null;
      this.lastError = '';
    } catch (e: any) {
      this.lastError = e.message || 'Unknown error';
    }
  }

  async getEnvironmentInfo(): Promise<void> {
    try {
      this.envInfo = await this.invoke<EnvironmentInfo>('environmentInfo', {});
      this.systemInfo = null;
      this.lastError = '';
    } catch (e: any) {
      this.lastError = e.message || 'Unknown error';
    }
  }

  // ==================== Clipboard ====================

  async clipboardRead(): Promise<void> {
    try {
      this.clipboardContent = await this.invoke<string>('clipboardRead', {});
      this.lastError = '';
    } catch (e: any) {
      this.lastError = e.message || 'Unknown error';
    }
  }

  async clipboardWrite(): Promise<void> {
    try {
      await this.invoke('clipboardWrite', { text: this.clipboardText });
      this.clipboardContent = `Copied: "${this.clipboardText}"`;
      this.lastError = '';
    } catch (e: any) {
      this.lastError = e.message || 'Unknown error';
    }
  }

  // ==================== Shell ====================

  async openUrl(url: string): Promise<void> {
    try {
      await this.invoke('openUrl', { url });
      this.lastError = '';
    } catch (e: any) {
      this.lastError = e.message || 'Unknown error';
    }
  }

  async showNotification(): Promise<void> {
    try {
      await this.invoke('showNotification', {
        title: 'Krema Notification',
        body: 'Hello from the Java backend!'
      });
      this.lastError = '';
    } catch (e: any) {
      this.lastError = e.message || 'Unknown error';
    }
  }

  async runCommand(): Promise<void> {
    try {
      const result = await this.invoke<CommandResult>('runCommand', {
        command: this.commandInput
      });
      this.commandOutput = result.code === 0
        ? result.stdout
        : `Error (code ${result.code}): ${result.stderr || result.stdout}`;
      this.lastError = '';
    } catch (e: any) {
      this.lastError = e.message || 'Unknown error';
    }
  }

  // ==================== File System ====================

  async getPaths(): Promise<void> {
    try {
      this.paths = await this.invoke<PathInfo>('getPaths', {});
      this.pathItems = [
        { label: 'Home', path: this.paths.home },
        { label: 'Desktop', path: this.paths.desktop },
        { label: 'Documents', path: this.paths.documents },
        { label: 'Downloads', path: this.paths.downloads },
        { label: 'Current', path: this.paths.current }
      ];
      this.currentPath = this.paths.home;
      this.lastError = '';
    } catch (e: any) {
      this.lastError = e.message || 'Unknown error';
    }
  }

  async listDirectory(path: string): Promise<void> {
    // Auto-fetch paths if not set
    if (!path) {
      if (!this.paths) {
        await this.getPaths();
      }
      path = this.currentPath || this.paths?.home || '';
      if (!path) return;
    }
    try {
      this.currentPath = path;
      this.files = await this.invoke<FileInfo[]>('listDirectory', { path });
      // Sort: directories first, then by name
      this.files.sort((a, b) => {
        if (a.isDirectory !== b.isDirectory) return a.isDirectory ? -1 : 1;
        return a.name.localeCompare(b.name);
      });
      this.selectedFile = null;
      this.fileContent = '';
      this.lastError = '';
    } catch (e: any) {
      this.lastError = e.message || 'Unknown error';
    }
  }

  selectFile(file: FileInfo): void {
    this.selectedFile = file;
    this.fileContent = '';
  }

  async readFile(path: string): Promise<void> {
    try {
      this.fileContent = await this.invoke<string>('readTextFile', { path });
      // Truncate if too long
      if (this.fileContent.length > 10000) {
        this.fileContent = this.fileContent.substring(0, 10000) + '\n... (truncated)';
      }
      this.lastError = '';
    } catch (e: any) {
      this.lastError = e.message || 'Unknown error';
    }
  }

  formatSize(bytes: number): string {
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
    return (bytes / 1024 / 1024).toFixed(1) + ' MB';
  }

  // ==================== Native APIs - Built-in ====================

  async getAppDirectories(): Promise<void> {
    try {
      const [appData, appConfig, appCache, appLog, home, temp] = await Promise.all([
        this.invoke<string>('path:appData', {}),
        this.invoke<string>('path:appConfig', {}),
        this.invoke<string>('path:appCache', {}),
        this.invoke<string>('path:appLog', {}),
        this.invoke<string>('path:home', {}),
        this.invoke<string>('path:temp', {})
      ]);
      this.appDirs = [
        { label: 'App Data', value: appData },
        { label: 'App Config', value: appConfig },
        { label: 'App Cache', value: appCache },
        { label: 'App Log', value: appLog },
        { label: 'Home', value: home },
        { label: 'Temp', value: temp }
      ];
      this.lastError = '';
    } catch (e: unknown) {
      this.lastError = (e as Error).message || 'Unknown error';
    }
  }

  async analyzePath(): Promise<void> {
    try {
      const path = this.pathInput;
      const [dirname, basename, extname, isAbsolute, normalized] = await Promise.all([
        this.invoke<string>('path:dirname', { path }),
        this.invoke<string>('path:basename', { path, ext: null }),
        this.invoke<string>('path:extname', { path }),
        this.invoke<boolean>('path:isAbsolute', { path }),
        this.invoke<string>('path:normalize', { path })
      ]);
      this.pathResults = [
        { label: 'dirname', value: dirname },
        { label: 'basename', value: basename },
        { label: 'extname', value: extname },
        { label: 'isAbsolute', value: String(isAbsolute) },
        { label: 'normalized', value: normalized }
      ];
      this.lastError = '';
    } catch (e: unknown) {
      this.lastError = (e as Error).message || 'Unknown error';
    }
  }

  async joinPaths(): Promise<void> {
    try {
      const result = await this.invoke<string>('path:join', {
        paths: ['/Users', 'demo', 'documents', 'file.txt']
      });
      this.pathResults = [{ label: 'path:join result', value: result }];
      this.lastError = '';
    } catch (e: unknown) {
      this.lastError = (e as Error).message || 'Unknown error';
    }
  }

  async resolvePath(): Promise<void> {
    try {
      const result = await this.invoke<string>('path:resolve', {
        paths: ['.', 'src', '..', 'dist', 'app.js']
      });
      this.pathResults = [{ label: 'path:resolve result', value: result }];
      this.lastError = '';
    } catch (e: unknown) {
      this.lastError = (e as Error).message || 'Unknown error';
    }
  }

  async showInFinder(): Promise<void> {
    if (!this.currentPath) {
      await this.getPaths();
    }
    try {
      const pathToReveal = this.selectedFile?.path || this.currentPath || this.paths?.home;
      if (pathToReveal) {
        await this.invoke('shell:showItemInFolder', { path: pathToReveal });
      }
      this.lastError = '';
    } catch (e: unknown) {
      this.lastError = (e as Error).message || 'Unknown error';
    }
  }

  async openWithApp(): Promise<void> {
    try {
      // Open a URL with Safari specifically (macOS example)
      await this.invoke('shell:openWith', {
        path: 'https://github.com',
        app: 'Safari'
      });
      this.lastError = '';
    } catch (e: unknown) {
      this.lastError = (e as Error).message || 'Unknown error';
    }
  }

  // ==================== Native APIs - Dialogs ====================

  async openFileDialog(): Promise<void> {
    try {
      const result = await this.invoke<string | null>('dialog:openFile', {
        title: 'Select a file',
        filters: [
          { name: 'Text Files', extensions: ['txt', 'md'] },
          { name: 'All Files', extensions: ['*'] }
        ]
      });
      this.dialogResult = result ? `Selected: ${result}` : 'Cancelled';
      this.lastError = '';
    } catch (e: unknown) {
      this.lastError = (e as Error).message || 'Unknown error';
    }
  }

  async saveFileDialog(): Promise<void> {
    try {
      const result = await this.invoke<string | null>('dialog:saveFile', {
        title: 'Save file as',
        defaultPath: 'untitled.txt'
      });
      this.dialogResult = result ? `Save to: ${result}` : 'Cancelled';
      this.lastError = '';
    } catch (e: unknown) {
      this.lastError = (e as Error).message || 'Unknown error';
    }
  }

  async selectFolderDialog(): Promise<void> {
    try {
      const result = await this.invoke<string | null>('dialog:selectFolder', {
        title: 'Select a folder'
      });
      this.dialogResult = result ? `Folder: ${result}` : 'Cancelled';
      this.lastError = '';
    } catch (e: unknown) {
      this.lastError = (e as Error).message || 'Unknown error';
    }
  }

  async confirmDialog(): Promise<void> {
    try {
      const result = await this.invoke<boolean>('dialog:confirm', {
        title: 'Confirm Action',
        message: 'Are you sure you want to proceed?'
      });
      this.dialogResult = result ? 'Confirmed!' : 'Cancelled';
      this.lastError = '';
    } catch (e: unknown) {
      this.lastError = (e as Error).message || 'Unknown error';
    }
  }

  async promptDialog(): Promise<void> {
    try {
      const result = await this.invoke<string | null>('dialog:prompt', {
        title: 'Enter Your Name',
        message: 'What is your name?',
        defaultValue: 'World'
      });
      this.dialogResult = result ? `Hello, ${result}!` : 'Cancelled';
      this.lastError = '';
    } catch (e: unknown) {
      this.lastError = (e as Error).message || 'Unknown error';
    }
  }

  async showBuiltinNotification(): Promise<void> {
    try {
      await this.invoke('notification:show', {
        title: 'Built-in Notification',
        body: 'This uses the Krema built-in notification API!',
        options: { sound: 'default' }
      });
      this.lastError = '';
    } catch (e: unknown) {
      this.lastError = (e as Error).message || 'Unknown error';
    }
  }

  // ==================== Native APIs - Clipboard (Built-in) ====================

  async builtinClipboardRead(): Promise<void> {
    try {
      this.clipboardContent = await this.invoke<string>('clipboard:readText', {}) || '(empty)';
      this.lastError = '';
    } catch (e: unknown) {
      this.lastError = (e as Error).message || 'Unknown error';
    }
  }

  async builtinClipboardWrite(): Promise<void> {
    try {
      await this.invoke('clipboard:writeText', { text: this.clipboardText });
      this.clipboardContent = `Copied: "${this.clipboardText}"`;
      this.lastError = '';
    } catch (e: unknown) {
      this.lastError = (e as Error).message || 'Unknown error';
    }
  }

  async checkClipboardFormats(): Promise<void> {
    try {
      const [hasText, hasImage, formats] = await Promise.all([
        this.invoke<boolean>('clipboard:hasText', {}),
        this.invoke<boolean>('clipboard:hasImage', {}),
        this.invoke<string[]>('clipboard:getAvailableFormats', {})
      ]);
      this.clipboardContent = `Has text: ${hasText}, Has image: ${hasImage}\nFormats: ${formats.join(', ')}`;
      this.lastError = '';
    } catch (e: unknown) {
      this.lastError = (e as Error).message || 'Unknown error';
    }
  }

  // ==================== Screen Info ====================

  async getAllScreens(): Promise<void> {
    try {
      this.screens = await this.invoke<ScreenInfo[]>('screen:getAll', {});
      this.lastError = '';
    } catch (e: unknown) {
      this.lastError = (e as Error).message || 'Unknown error';
    }
  }

  async getPrimaryScreen(): Promise<void> {
    try {
      this.primaryScreen = await this.invoke<ScreenInfo>('screen:getPrimary', {});
      this.lastError = '';
    } catch (e: unknown) {
      this.lastError = (e as Error).message || 'Unknown error';
    }
  }

  async getCursorPosition(): Promise<void> {
    try {
      this.cursorPosition = await this.invoke<CursorPosition>('screen:getCursorPosition', {});
      this.lastError = '';
    } catch (e: unknown) {
      this.lastError = (e as Error).message || 'Unknown error';
    }
  }

  async getScreenAtCursor(): Promise<void> {
    try {
      this.screenAtCursor = await this.invoke<ScreenInfo>('screen:getScreenAtCursor', {});
      this.lastError = '';
    } catch (e: unknown) {
      this.lastError = (e as Error).message || 'Unknown error';
    }
  }

  toggleCursorTracking(): void {
    if (this.trackingCursor) {
      this.trackingCursor = false;
      if (this.cursorTrackingInterval) {
        clearInterval(this.cursorTrackingInterval);
        this.cursorTrackingInterval = null;
      }
    } else {
      this.trackingCursor = true;
      this.cursorTrackingInterval = setInterval(() => {
        this.getCursorPosition();
      }, 100);
    }
  }

  // ==================== Drag and Drop ====================

  async configureDragDrop(): Promise<void> {
    try {
      const extensions = this.acceptedExtensions
        ? this.acceptedExtensions.split(',').map(e => e.trim()).filter(e => e)
        : null;

      await this.invoke('dragdrop:configure', {
        enabled: this.dropEnabled,
        acceptedExtensions: extensions,
        dropZoneSelector: this.dropZoneSelector || null
      });
      this.lastError = '';
    } catch (e: unknown) {
      this.lastError = (e as Error).message || 'Unknown error';
    }
  }

  async enableDragDrop(): Promise<void> {
    try {
      await this.invoke('dragdrop:enable', {});
      this.dropEnabled = true;
      this.lastError = '';
    } catch (e: unknown) {
      this.lastError = (e as Error).message || 'Unknown error';
    }
  }

  async disableDragDrop(): Promise<void> {
    try {
      await this.invoke('dragdrop:disable', {});
      this.dropEnabled = false;
      this.lastError = '';
    } catch (e: unknown) {
      this.lastError = (e as Error).message || 'Unknown error';
    }
  }

  clearDroppedFiles(): void {
    this.droppedFiles = [];
    this.lastDropEvent = null;
  }

  // ==================== Native Menus ====================

  async setApplicationMenu(): Promise<void> {
    try {
      const menu: MenuItem[] = [
        {
          label: 'App',
          submenu: [
            { id: 'about', label: 'About Krema Angular' },
            { type: 'separator' },
            { id: 'preferences', label: 'Preferences...', accelerator: 'Cmd+,' },
            { type: 'separator' },
            { role: 'quit' }
          ]
        },
        {
          label: 'Edit',
          submenu: [
            { role: 'undo' },
            { role: 'redo' },
            { type: 'separator' },
            { role: 'cut' },
            { role: 'copy' },
            { role: 'paste' },
            { role: 'select-all' }
          ]
        },
        {
          label: 'View',
          submenu: [
            { id: 'reload', label: 'Reload', accelerator: 'Cmd+R' },
            { type: 'separator' },
            { id: 'zoom-in', label: 'Zoom In', accelerator: 'Cmd+Plus' },
            { id: 'zoom-out', label: 'Zoom Out', accelerator: 'Cmd+Minus' },
            { id: 'zoom-reset', label: 'Reset Zoom', accelerator: 'Cmd+0' },
            { type: 'separator' },
            { id: 'fullscreen', label: 'Toggle Full Screen', accelerator: 'Ctrl+Cmd+F' }
          ]
        },
        {
          label: 'Window',
          submenu: [
            { role: 'minimize' },
            { role: 'close' }
          ]
        },
        {
          label: 'Help',
          submenu: [
            { id: 'docs', label: 'Documentation' },
            { id: 'github', label: 'GitHub Repository' },
            { type: 'separator' },
            { id: 'report-issue', label: 'Report Issue...' }
          ]
        }
      ];

      await this.invoke('menu:setApplicationMenu', { menu });
      this.menuConfigured = true;
      this.lastError = '';
    } catch (e: unknown) {
      this.lastError = (e as Error).message || 'Unknown error';
    }
  }

  async showContextMenu(): Promise<void> {
    try {
      const items: MenuItem[] = [
        { id: 'ctx-copy', label: 'Copy' },
        { id: 'ctx-paste', label: 'Paste' },
        { type: 'separator' },
        { id: 'ctx-refresh', label: 'Refresh' },
        { type: 'separator' },
        {
          label: 'More Options',
          submenu: [
            { id: 'ctx-option1', label: 'Option 1' },
            { id: 'ctx-option2', label: 'Option 2' },
            { id: 'ctx-option3', label: 'Option 3', type: 'checkbox', checked: true }
          ]
        }
      ];

      await this.invoke('menu:showContextMenu', { items });
      this.contextMenuResult = 'Context menu shown';
      this.lastError = '';
    } catch (e: unknown) {
      this.lastError = (e as Error).message || 'Unknown error';
    }
  }

  clearMenuEvents(): void {
    this.menuClickEvents = [];
  }

  // ==================== Window Management ====================

  async getWindowState(): Promise<void> {
    try {
      this.windowState = await this.invoke<WindowState>('window:getState');
      this.lastError = '';
    } catch (e: unknown) {
      this.lastError = (e as Error).message || 'Unknown error';
    }
  }

  async getWindowTitle(): Promise<void> {
    try {
      this.windowTitle = await this.invoke<string>('window:getTitle');
      this.lastError = '';
    } catch (e: unknown) {
      this.lastError = (e as Error).message || 'Unknown error';
    }
  }

  async minimizeWindow(): Promise<void> {
    try {
      await this.invoke('window:minimize');
      this.lastError = '';
    } catch (e: unknown) {
      this.lastError = (e as Error).message || 'Unknown error';
    }
  }

  async maximizeWindow(): Promise<void> {
    try {
      await this.invoke('window:maximize');
      this.lastError = '';
    } catch (e: unknown) {
      this.lastError = (e as Error).message || 'Unknown error';
    }
  }

  async restoreWindow(): Promise<void> {
    try {
      await this.invoke('window:restore');
      this.lastError = '';
    } catch (e: unknown) {
      this.lastError = (e as Error).message || 'Unknown error';
    }
  }

  async toggleFullscreen(): Promise<void> {
    try {
      await this.invoke('window:toggleFullscreen');
      this.lastError = '';
    } catch (e: unknown) {
      this.lastError = (e as Error).message || 'Unknown error';
    }
  }

  async centerWindow(): Promise<void> {
    try {
      await this.invoke('window:center');
      this.lastError = '';
    } catch (e: unknown) {
      this.lastError = (e as Error).message || 'Unknown error';
    }
  }

  // ==================== Positioner Plugin ====================

  async moveTo(position: string): Promise<void> {
    try {
      await this.invoke('positioner:moveTo', { position });
      this.positionerResult = `Moved to ${position}`;
      this.lastError = '';
    } catch (e: unknown) {
      this.lastError = (e as Error).message || 'Unknown error';
    }
  }

  async setWindowSize(): Promise<void> {
    try {
      await this.invoke('window:setSize', { width: this.newWidth, height: this.newHeight });
      this.lastError = '';
    } catch (e: unknown) {
      this.lastError = (e as Error).message || 'Unknown error';
    }
  }

  async setWindowPosition(): Promise<void> {
    try {
      await this.invoke('window:setPosition', { x: this.newX, y: this.newY });
      this.lastError = '';
    } catch (e: unknown) {
      this.lastError = (e as Error).message || 'Unknown error';
    }
  }

  async setWindowTitle(): Promise<void> {
    try {
      await this.invoke('window:setTitle', { title: this.windowTitle });
      this.lastError = '';
    } catch (e: unknown) {
      this.lastError = (e as Error).message || 'Unknown error';
    }
  }

  async setWindowOpacity(): Promise<void> {
    try {
      await this.invoke('window:setOpacity', { opacity: this.windowOpacity });
      this.lastError = '';
    } catch (e: unknown) {
      this.lastError = (e as Error).message || 'Unknown error';
    }
  }

  async setAlwaysOnTop(value: boolean): Promise<void> {
    try {
      await this.invoke('window:setAlwaysOnTop', { alwaysOnTop: value });
      this.lastError = '';
    } catch (e: unknown) {
      this.lastError = (e as Error).message || 'Unknown error';
    }
  }

  async setResizable(value: boolean): Promise<void> {
    try {
      await this.invoke('window:setResizable', { resizable: value });
      this.lastError = '';
    } catch (e: unknown) {
      this.lastError = (e as Error).message || 'Unknown error';
    }
  }

  // ==================== Essential Plugins - OS Info ====================

  async getOsInfo(): Promise<void> {
    try {
      this.osInfo = await this.invoke<Record<string, unknown>>('os:info');
      this.lastError = '';
    } catch (e: unknown) {
      this.lastError = (e as Error).message || 'Unknown error';
    }
  }

  async getOsMemory(): Promise<void> {
    try {
      this.osMemory = await this.invoke<Record<string, number>>('os:memory');
      this.lastError = '';
    } catch (e: unknown) {
      this.lastError = (e as Error).message || 'Unknown error';
    }
  }

  async getOsLocale(): Promise<void> {
    try {
      this.osLocale = await this.invoke<Record<string, string>>('os:locale');
      this.lastError = '';
    } catch (e: unknown) {
      this.lastError = (e as Error).message || 'Unknown error';
    }
  }

  formatBytes(bytes: number): string {
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
    if (bytes < 1024 * 1024 * 1024) return (bytes / 1024 / 1024).toFixed(1) + ' MB';
    return (bytes / 1024 / 1024 / 1024).toFixed(2) + ' GB';
  }

  // ==================== Essential Plugins - Store ====================

  async storeGet(): Promise<void> {
    try {
      this.storeResult = await this.invoke('store:get', {
        key: this.storeKey,
        default: null
      });
      this.lastError = '';
    } catch (e: unknown) {
      this.lastError = (e as Error).message || 'Unknown error';
    }
  }

  async storeSet(): Promise<void> {
    try {
      await this.invoke('store:set', {
        key: this.storeKey,
        value: this.storeValue
      });
      this.storeResult = `Saved: ${this.storeKey} = ${this.storeValue}`;
      this.lastError = '';
    } catch (e: unknown) {
      this.lastError = (e as Error).message || 'Unknown error';
    }
  }

  async storeDelete(): Promise<void> {
    try {
      const deleted = await this.invoke<boolean>('store:delete', { key: this.storeKey });
      this.storeResult = deleted ? `Deleted: ${this.storeKey}` : `Key not found: ${this.storeKey}`;
      this.lastError = '';
    } catch (e: unknown) {
      this.lastError = (e as Error).message || 'Unknown error';
    }
  }

  async storeGetKeys(): Promise<void> {
    try {
      this.storeKeys = await this.invoke<string[]>('store:keys', {});
      this.lastError = '';
    } catch (e: unknown) {
      this.lastError = (e as Error).message || 'Unknown error';
    }
  }

  async storeGetEntries(): Promise<void> {
    try {
      this.storeEntries = await this.invoke<Record<string, unknown>>('store:entries', {});
      this.lastError = '';
    } catch (e: unknown) {
      this.lastError = (e as Error).message || 'Unknown error';
    }
  }

  async storeClear(): Promise<void> {
    try {
      await this.invoke('store:clear', {});
      this.storeResult = 'Store cleared';
      this.storeKeys = [];
      this.storeEntries = {};
      this.lastError = '';
    } catch (e: unknown) {
      this.lastError = (e as Error).message || 'Unknown error';
    }
  }

  // ==================== Essential Plugins - Single Instance ====================

  async checkIsPrimary(): Promise<void> {
    try {
      this.isPrimaryInstance = await this.invoke<boolean>('instance:isPrimary');
      this.lastError = '';
    } catch (e: unknown) {
      this.lastError = (e as Error).message || 'Unknown error';
    }
  }

  async requestInstanceLock(): Promise<void> {
    try {
      const acquired = await this.invoke<boolean>('instance:requestLock');
      this.isPrimaryInstance = acquired;
      this.lastError = '';
    } catch (e: unknown) {
      this.lastError = (e as Error).message || 'Unknown error';
    }
  }

  // ==================== Essential Plugins - Global Shortcut ====================

  async registerShortcut(): Promise<void> {
    try {
      const success = await this.invoke<boolean>('shortcut:register', {
        accelerator: this.shortcutAccelerator
      });
      if (success) {
        await this.getRegisteredShortcuts();
      }
      this.lastError = success ? '' : 'Failed to register shortcut';
    } catch (e: unknown) {
      this.lastError = (e as Error).message || 'Unknown error';
    }
  }

  async unregisterShortcut(): Promise<void> {
    try {
      await this.invoke('shortcut:unregister', {
        accelerator: this.shortcutAccelerator
      });
      await this.getRegisteredShortcuts();
      this.lastError = '';
    } catch (e: unknown) {
      this.lastError = (e as Error).message || 'Unknown error';
    }
  }

  async unregisterAllShortcuts(): Promise<void> {
    try {
      await this.invoke('shortcut:unregisterAll');
      this.registeredShortcuts = [];
      this.lastError = '';
    } catch (e: unknown) {
      this.lastError = (e as Error).message || 'Unknown error';
    }
  }

  async getRegisteredShortcuts(): Promise<void> {
    try {
      this.registeredShortcuts = await this.invoke<string[]>('shortcut:getAll');
      this.lastError = '';
    } catch (e: unknown) {
      this.lastError = (e as Error).message || 'Unknown error';
    }
  }

  // ==================== Dock Badge ====================

  async checkDockSupported(): Promise<void> {
    try {
      this.dockSupported = await this.invoke<boolean>('dock:isSupported');
      this.lastError = '';
    } catch (e: unknown) {
      this.lastError = (e as Error).message || 'Unknown error';
    }
  }

  async setDockBadge(): Promise<void> {
    try {
      await this.invoke('dock:setBadge', { text: this.dockBadge });
      this.currentDockBadge = this.dockBadge;
      this.lastError = '';
    } catch (e: unknown) {
      this.lastError = (e as Error).message || 'Unknown error';
    }
  }

  async clearDockBadge(): Promise<void> {
    try {
      await this.invoke('dock:clearBadge');
      this.currentDockBadge = '';
      this.lastError = '';
    } catch (e: unknown) {
      this.lastError = (e as Error).message || 'Unknown error';
    }
  }

  async getDockBadge(): Promise<void> {
    try {
      this.currentDockBadge = await this.invoke<string>('dock:getBadge');
      this.lastError = '';
    } catch (e: unknown) {
      this.lastError = (e as Error).message || 'Unknown error';
    }
  }

  async bounceDock(critical: boolean): Promise<void> {
    try {
      await this.invoke('dock:bounce', { critical });
      this.lastError = '';
    } catch (e: unknown) {
      this.lastError = (e as Error).message || 'Unknown error';
    }
  }

  // ==================== HTTP Client ====================

  async httpRequest(): Promise<void> {
    this.httpLoading = true;
    try {
      const options: Record<string, unknown> = {};
      if (this.httpMethod !== 'GET' && this.httpMethod !== 'HEAD' && this.httpBody) {
        try {
          options['body'] = JSON.parse(this.httpBody);
        } catch {
          options['body'] = this.httpBody;
        }
      }
      options['headers'] = { 'Accept': 'application/json' };

      this.httpResult = await this.invoke('http:request', {
        method: this.httpMethod,
        url: this.httpUrl,
        options
      });
      this.lastError = '';
    } catch (e: unknown) {
      this.lastError = (e as Error).message || 'Unknown error';
      this.httpResult = null;
    } finally {
      this.httpLoading = false;
    }
  }

  async httpFetch(): Promise<void> {
    this.httpLoading = true;
    try {
      this.httpResult = await this.invoke('http:fetch', { url: this.httpUrl });
      this.lastError = '';
    } catch (e: unknown) {
      this.lastError = (e as Error).message || 'Unknown error';
      this.httpResult = null;
    } finally {
      this.httpLoading = false;
    }
  }

  async httpFetchJson(): Promise<void> {
    this.httpLoading = true;
    try {
      this.httpResult = await this.invoke('http:fetchJson', { url: this.httpUrl });
      this.lastError = '';
    } catch (e: unknown) {
      this.lastError = (e as Error).message || 'Unknown error';
      this.httpResult = null;
    } finally {
      this.httpLoading = false;
    }
  }

  // ==================== FS Plugin ====================

  async fsCheckExists(): Promise<void> {
    try {
      this.fsExistsResult = await this.invoke<boolean>('fs:exists', { path: this.fsPath });
      this.fsPluginError = '';
    } catch (e: unknown) {
      this.fsPluginError = (e as Error).message || 'Unknown error';
    }
  }

  async fsGetStat(): Promise<void> {
    try {
      this.fsStat = await this.invoke<FsFileInfo>('fs:stat', { path: this.fsPath });
      this.fsPluginError = '';
    } catch (e: unknown) {
      this.fsStat = null;
      this.fsPluginError = (e as Error).message || 'Unknown error';
    }
  }

  async fsListDir(path?: string): Promise<void> {
    const dir = path || this.fsPath;
    try {
      this.fsCurrentDir = dir;
      this.fsFiles = await this.invoke<FsFileInfo[]>('fs:readDir', { path: dir });
      this.fsFiles.sort((a, b) => {
        if (a.isDirectory !== b.isDirectory) return a.isDirectory ? -1 : 1;
        return a.name.localeCompare(b.name);
      });
      this.fsPluginError = '';
    } catch (e: unknown) {
      this.fsFiles = [];
      this.fsPluginError = (e as Error).message || 'Unknown error';
    }
  }

  async fsReadFile(): Promise<void> {
    try {
      this.fsReadResult = await this.invoke<string>('fs:readTextFile', { path: this.fsPath });
      if (this.fsReadResult.length > 5000) {
        this.fsReadResult = this.fsReadResult.substring(0, 5000) + '\n... (truncated)';
      }
      this.fsPluginError = '';
    } catch (e: unknown) {
      this.fsReadResult = '';
      this.fsPluginError = (e as Error).message || 'Unknown error';
    }
  }

  async fsWriteFile(): Promise<void> {
    try {
      await this.invoke<boolean>('fs:writeTextFile', {
        path: this.fsWritePath,
        content: this.fsWriteContent
      });
      this.fsPluginError = '';
      this.fsReadResult = `Written to ${this.fsWritePath}`;
    } catch (e: unknown) {
      this.fsPluginError = (e as Error).message || 'Unknown error';
    }
  }

  async fsWriteAndReadBack(): Promise<void> {
    try {
      await this.invoke<boolean>('fs:writeTextFile', {
        path: this.fsWritePath,
        content: this.fsWriteContent
      });
      const exists = await this.invoke<boolean>('fs:exists', { path: this.fsWritePath });
      const content = await this.invoke<string>('fs:readTextFile', { path: this.fsWritePath });
      const stat = await this.invoke<FsFileInfo>('fs:stat', { path: this.fsWritePath });
      this.fsReadResult = `Exists: ${exists}\nSize: ${stat.size} bytes\nContent: ${content}`;
      this.fsPluginError = '';
    } catch (e: unknown) {
      this.fsPluginError = (e as Error).message || 'Unknown error';
    }
  }

  fsNavigateDir(file: FsFileInfo): void {
    if (file.isDirectory) {
      this.fsPath = file.path;
      this.fsListDir(file.path);
    } else {
      this.fsPath = file.path;
    }
  }

  // ==================== Frameless Window ====================

  async setTitleBarStyle(): Promise<void> {
    try {
      await this.invoke('window:setTitleBarStyle', { style: this.titleBarStyle });
      this.lastError = '';
    } catch (e: unknown) {
      this.lastError = (e as Error).message || 'Unknown error';
    }
  }

  async setTrafficLightPosition(): Promise<void> {
    try {
      await this.invoke('window:setTrafficLightPosition', {
        x: this.trafficLightX,
        y: this.trafficLightY
      });
      this.lastError = '';
    } catch (e: unknown) {
      this.lastError = (e as Error).message || 'Unknown error';
    }
  }

  async setTitlebarTransparent(transparent: boolean): Promise<void> {
    try {
      await this.invoke('window:setTitlebarTransparent', { transparent });
      this.lastError = '';
    } catch (e: unknown) {
      this.lastError = (e as Error).message || 'Unknown error';
    }
  }

  async setFullSizeContentView(extend: boolean): Promise<void> {
    try {
      await this.invoke('window:setFullSizeContentView', { extend });
      this.lastError = '';
    } catch (e: unknown) {
      this.lastError = (e as Error).message || 'Unknown error';
    }
  }

  // ==================== Multi-Window ====================

  async createChildWindow(): Promise<void> {
    try {
      const result = await this.invoke<{ label: string }>('window:create', {
        title: this.childWindowTitle,
        width: 600,
        height: 400,
        url: this.childWindowUrl || undefined
      });
      this.childWindowLabel = result.label;
      await this.listWindows();
      this.lastError = '';
    } catch (e: unknown) {
      this.lastError = (e as Error).message || 'Unknown error';
    }
  }

  async showModal(): Promise<void> {
    try {
      const result = await this.invoke<{ label: string }>('window:showModal', {
        title: 'Modal Window',
        width: 400,
        height: 300,
        parent: 'main'
      });
      this.childWindowLabel = result.label;
      await this.listWindows();
      this.lastError = '';
    } catch (e: unknown) {
      this.lastError = (e as Error).message || 'Unknown error';
    }
  }

  async listWindows(): Promise<void> {
    try {
      const result = await this.invoke<{ windows: string[]; count: number }>('window:list');
      this.windowList = result.windows;
      this.lastError = '';
    } catch (e: unknown) {
      this.lastError = (e as Error).message || 'Unknown error';
    }
  }

  async closeChildWindow(): Promise<void> {
    if (!this.childWindowLabel) return;
    try {
      await this.invoke('window:close', { label: this.childWindowLabel });
      this.childWindowLabel = '';
      await this.listWindows();
      this.lastError = '';
    } catch (e: unknown) {
      this.lastError = (e as Error).message || 'Unknown error';
    }
  }

  async sendToWindow(): Promise<void> {
    if (!this.childWindowLabel) return;
    try {
      let payload: unknown;
      try {
        payload = JSON.parse(this.windowMessagePayload);
      } catch {
        payload = this.windowMessagePayload;
      }
      await this.invoke('window:sendTo', {
        label: this.childWindowLabel,
        event: this.windowMessageEvent || 'custom-message',
        payload
      });
      this.lastError = '';
    } catch (e: unknown) {
      this.lastError = (e as Error).message || 'Unknown error';
    }
  }

  async broadcastToWindows(): Promise<void> {
    try {
      let payload: unknown;
      try {
        payload = JSON.parse(this.windowMessagePayload);
      } catch {
        payload = this.windowMessagePayload;
      }
      await this.invoke('window:broadcast', {
        event: this.windowMessageEvent || 'broadcast-message',
        payload
      });
      this.lastError = '';
    } catch (e: unknown) {
      this.lastError = (e as Error).message || 'Unknown error';
    }
  }

  // ==================== Secure Storage ====================

  async ssSet(): Promise<void> {
    try {
      const result = await this.invoke<boolean>('secureStorage:set', { key: this.ssKey, value: this.ssValue });
      this.ssResult = result ? `Stored "${this.ssKey}" successfully` : 'Failed to store';
      this.lastError = '';
    } catch (e: unknown) {
      this.lastError = (e as Error).message || 'Unknown error';
    }
  }

  async ssGet(): Promise<void> {
    try {
      const value = await this.invoke<string | null>('secureStorage:get', { key: this.ssKey });
      this.ssResult = value !== null ? `Value: "${value}"` : `Key "${this.ssKey}" not found`;
      this.lastError = '';
    } catch (e: unknown) {
      this.lastError = (e as Error).message || 'Unknown error';
    }
  }

  async ssDelete(): Promise<void> {
    try {
      const result = await this.invoke<boolean>('secureStorage:delete', { key: this.ssKey });
      this.ssResult = result ? `Deleted "${this.ssKey}"` : `Key "${this.ssKey}" not found`;
      this.lastError = '';
    } catch (e: unknown) {
      this.lastError = (e as Error).message || 'Unknown error';
    }
  }

  async ssHas(): Promise<void> {
    try {
      const exists = await this.invoke<boolean>('secureStorage:has', { key: this.ssKey });
      this.ssResult = exists ? `Key "${this.ssKey}" exists` : `Key "${this.ssKey}" not found`;
      this.lastError = '';
    } catch (e: unknown) {
      this.lastError = (e as Error).message || 'Unknown error';
    }
  }

  // ==================== Logging ====================

  async sendLog(): Promise<void> {
    try {
      await this.invoke(`log:${this.logLevel}`, { message: this.logMessage });
      this.logHistory.unshift({ level: this.logLevel, message: this.logMessage, timestamp: new Date() });
      if (this.logHistory.length > 20) {
        this.logHistory.pop();
      }
      this.lastError = '';
    } catch (e: unknown) {
      this.lastError = (e as Error).message || 'Unknown error';
    }
  }

  async sendAllLevels(): Promise<void> {
    try {
      const levels = ['trace', 'debug', 'info', 'warn', 'error'];
      for (const level of levels) {
        await this.invoke(`log:${level}`, { message: `[${level.toUpperCase()}] ${this.logMessage}` });
        this.logHistory.unshift({ level, message: `[${level.toUpperCase()}] ${this.logMessage}`, timestamp: new Date() });
      }
      if (this.logHistory.length > 20) {
        this.logHistory.splice(20);
      }
      this.lastError = '';
    } catch (e: unknown) {
      this.lastError = (e as Error).message || 'Unknown error';
    }
  }

  async getLogFilePath(): Promise<void> {
    try {
      const appLog = await this.invoke<string>('path:appLog', {});
      this.logFilePath = appLog;
      this.lastError = '';
    } catch (e: unknown) {
      this.lastError = (e as Error).message || 'Unknown error';
    }
  }

  async revealLogFile(): Promise<void> {
    if (!this.logFilePath) {
      await this.getLogFilePath();
    }
    if (this.logFilePath) {
      try {
        await this.invoke('shell:showItemInFolder', { path: this.logFilePath });
        this.lastError = '';
      } catch (e: unknown) {
        this.lastError = (e as Error).message || 'Unknown error';
      }
    }
  }

  clearLogHistory(): void {
    this.logHistory = [];
  }
}
