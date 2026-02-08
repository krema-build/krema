import { useState, useEffect, useRef, useCallback } from 'react';

// ==================== Types ====================

interface SystemInfo {
  osName: string; osVersion: string; osArch: string; javaVersion: string; javaVendor: string;
  processors: number; maxMemoryMb: number; totalMemoryMb: number; freeMemoryMb: number;
}
interface EnvironmentInfo { username: string; homeDir: string; workingDir: string; path: string; platform: string; }
interface PathInfo { home: string; desktop: string; documents: string; downloads: string; temp: string; current: string; }
interface FileInfo { name: string; path: string; isDirectory: boolean; size: number; lastModified: number; }
interface CommandResult { code: number; stdout: string; stderr: string; }
interface ScreenBounds { x: number; y: number; width: number; height: number; }
interface ScreenInfo { name: string; frame: ScreenBounds; visibleFrame: ScreenBounds; scaleFactor: number; refreshRate: number; isPrimary: boolean; }
interface CursorPosition { x: number; y: number; }
interface DroppedFile { name: string; path: string | null; type: string; size: number; }
interface FileDropEvent { payload: { type: string; files: DroppedFile[]; x: number; y: number; }; }
interface FileDropHoverEvent { payload: { type: 'enter' | 'leave'; x: number; y: number; }; }
interface MenuItem { id?: string; label?: string; accelerator?: string; type?: string; enabled?: boolean; checked?: boolean; role?: string; submenu?: MenuItem[]; }
interface MenuClickEvent { payload: { id: string; }; }
interface FsFileInfo { name: string; path: string; isDirectory: boolean; size: number; modifiedTime: number; }
interface WindowState { x: number; y: number; width: number; height: number; minimized: boolean; maximized: boolean; fullscreen: boolean; focused: boolean; visible: boolean; }

const tabs = [
  { id: 'ipc', label: 'IPC' }, { id: 'events', label: 'Events' }, { id: 'native', label: 'Native APIs' },
  { id: 'screen', label: 'Screen' }, { id: 'dragdrop', label: 'Drag & Drop' }, { id: 'menus', label: 'Menus' },
  { id: 'window', label: 'Window' }, { id: 'plugins', label: 'Plugins' }, { id: 'system', label: 'System' },
  { id: 'clipboard', label: 'Clipboard' }, { id: 'shell', label: 'Shell' }, { id: 'files', label: 'Files' },
  { id: 'securestorage', label: 'Secure Storage' }, { id: 'logging', label: 'Logging' },
];

function formatSize(bytes: number): string {
  if (bytes < 1024) return bytes + ' B';
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
  return (bytes / 1024 / 1024).toFixed(1) + ' MB';
}
function formatBytes(bytes: number): string {
  if (bytes < 1024) return bytes + ' B';
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
  if (bytes < 1024 * 1024 * 1024) return (bytes / 1024 / 1024).toFixed(1) + ' MB';
  return (bytes / 1024 / 1024 / 1024).toFixed(2) + ' GB';
}
function fmtTime(d: Date): string {
  return d.toLocaleTimeString('en-US', { hour12: false, hour: '2-digit', minute: '2-digit', second: '2-digit', fractionalSecondDigits: 3 } as Intl.DateTimeFormatOptions);
}

export default function App() {
  const [activeTab, setActiveTab] = useState('ipc');
  const [lastError, setLastError] = useState('');

  // IPC
  const [name, setName] = useState('World');
  const [greeting, setGreeting] = useState('');
  const [num1, setNum1] = useState(10);
  const [num2, setNum2] = useState(5);
  const [operation, setOperation] = useState('add');
  const [calcResult, setCalcResult] = useState('');

  // Events
  const [timerCount, setTimerCount] = useState(0);
  const [timerRunning, setTimerRunning] = useState(false);
  const [lastEvent, setLastEvent] = useState<unknown>(null);
  const [lifecycleEvents, setLifecycleEvents] = useState<{ event: string; timestamp: Date }[]>([]);
  const [appReady, setAppReady] = useState(false);

  // Native APIs - Path
  const [pathInput, setPathInput] = useState('/Users/demo/documents/file.txt');
  const [pathResults, setPathResults] = useState<{ label: string; value: string }[]>([]);
  const [appDirs, setAppDirs] = useState<{ label: string; value: string }[]>([]);
  const [dialogResult, setDialogResult] = useState('');

  // HTTP
  const [httpUrl, setHttpUrl] = useState('https://jsonplaceholder.typicode.com/todos/1');
  const [httpMethod, setHttpMethod] = useState('GET');
  const [httpBody, setHttpBody] = useState('{"title": "test", "completed": false}');
  const [httpResult, setHttpResult] = useState<unknown>(null);
  const [httpLoading, setHttpLoading] = useState(false);

  // Screen
  const [screens, setScreens] = useState<ScreenInfo[]>([]);
  const [primaryScreen, setPrimaryScreen] = useState<ScreenInfo | null>(null);
  const [cursorPosition, setCursorPosition] = useState<CursorPosition | null>(null);
  const [screenAtCursor, setScreenAtCursor] = useState<ScreenInfo | null>(null);
  const [trackingCursor, setTrackingCursor] = useState(false);
  const cursorIntervalRef = useRef<ReturnType<typeof setInterval> | null>(null);

  // Drag & Drop
  const [dropEnabled, setDropEnabled] = useState(true);
  const [dropHovering, setDropHovering] = useState(false);
  const [droppedFiles, setDroppedFiles] = useState<DroppedFile[]>([]);
  const [lastDropEvent, setLastDropEvent] = useState<FileDropEvent | null>(null);
  const [acceptedExtensions, setAcceptedExtensions] = useState('');
  const [dropZoneSelector, setDropZoneSelector] = useState('');

  // Menus
  const [menuClickEvents, setMenuClickEvents] = useState<{ id: string; timestamp: Date }[]>([]);
  const [contextMenuResult, setContextMenuResult] = useState('');
  const [menuConfigured, setMenuConfigured] = useState(false);

  // Window
  const [windowState, setWindowState] = useState<WindowState | null>(null);
  const [windowTitle, setWindowTitle] = useState('');
  const [windowOpacity, setWindowOpacity] = useState(1.0);
  const [newWidth, setNewWidth] = useState(1024);
  const [newHeight, setNewHeight] = useState(768);
  const [newX, setNewX] = useState(100);
  const [newY, setNewY] = useState(100);
  const [positionerResult, setPositionerResult] = useState('');
  const [titleBarStyle, setTitleBarStyle] = useState('default');
  const [trafficLightX, setTrafficLightX] = useState(15);
  const [trafficLightY, setTrafficLightY] = useState(15);
  const [childWindowLabel, setChildWindowLabel] = useState('');
  const [childWindowTitle, setChildWindowTitle] = useState('Child Window');
  const [childWindowUrl, setChildWindowUrl] = useState('');
  const [windowList, setWindowList] = useState<string[]>([]);
  const [windowMessageEvent, setWindowMessageEvent] = useState('');
  const [windowMessagePayload, setWindowMessagePayload] = useState('{"message": "Hello from main!"}');

  // Plugins - OS
  const [osInfo, setOsInfo] = useState<Record<string, unknown> | null>(null);
  const [osMemory, setOsMemory] = useState<Record<string, number> | null>(null);
  const [osLocale, setOsLocale] = useState<Record<string, string> | null>(null);

  // Plugins - Store
  const [storeKey, setStoreKey] = useState('myKey');
  const [storeValue, setStoreValue] = useState('myValue');
  const [storeResult, setStoreResult] = useState<unknown>(null);
  const [storeKeys, setStoreKeys] = useState<string[]>([]);
  const [storeEntries, setStoreEntries] = useState<Record<string, unknown>>({});

  // Plugins - Single Instance / Shortcuts / Dock
  const [isPrimaryInstance, setIsPrimaryInstance] = useState(false);
  const [shortcutAccelerator, setShortcutAccelerator] = useState('Cmd+Shift+K');
  const [registeredShortcuts, setRegisteredShortcuts] = useState<string[]>([]);
  const [shortcutTriggered, setShortcutTriggered] = useState('');
  const [dockBadge, setDockBadge] = useState('5');
  const [currentDockBadge, setCurrentDockBadge] = useState('');
  const [dockSupported, setDockSupported] = useState(false);

  // System
  const [systemInfo, setSystemInfo] = useState<SystemInfo | null>(null);
  const [envInfo, setEnvInfo] = useState<EnvironmentInfo | null>(null);

  // Clipboard
  const [clipboardText, setClipboardText] = useState('Hello from Krema!');
  const [clipboardContent, setClipboardContent] = useState('');

  // Shell
  const [commandInput, setCommandInput] = useState('echo "Hello from Java shell!"');
  const [commandOutput, setCommandOutput] = useState('');

  // Files
  const [paths, setPaths] = useState<PathInfo | null>(null);
  const [pathItems, setPathItems] = useState<{ label: string; path: string }[]>([]);
  const [currentPath, setCurrentPath] = useState('');
  const [files, setFiles] = useState<FileInfo[]>([]);
  const [selectedFile, setSelectedFile] = useState<FileInfo | null>(null);
  const [fileContent, setFileContent] = useState('');

  // FS Plugin
  const [fsPath, setFsPath] = useState('/tmp');
  const [fsExistsResult, setFsExistsResult] = useState<boolean | null>(null);
  const [fsStat, setFsStat] = useState<FsFileInfo | null>(null);
  const [fsFiles, setFsFiles] = useState<FsFileInfo[]>([]);
  const [fsCurrentDir, setFsCurrentDir] = useState('');
  const [fsWritePath, setFsWritePath] = useState('/tmp/krema-plugin-test.txt');
  const [fsWriteContent, setFsWriteContent] = useState('Hello from Krema FS Plugin!');
  const [fsReadResult, setFsReadResult] = useState('');
  const [fsPluginError, setFsPluginError] = useState('');

  // Secure Storage
  const [ssKey, setSsKey] = useState('my-secret');
  const [ssValue, setSsValue] = useState('super-secret-value');
  const [ssResult, setSsResult] = useState('');

  // Logging
  const [logMessage, setLogMessage] = useState('Hello from React frontend!');
  const [logLevel, setLogLevel] = useState('info');
  const [logHistory, setLogHistory] = useState<{ level: string; message: string; timestamp: Date }[]>([]);
  const [logFilePath, setLogFilePath] = useState('');

  // ==================== Event Listeners ====================
  useEffect(() => {
    if (!window.krema) return;
    const unsubs: (() => void)[] = [];

    unsubs.push(window.krema.on('timer-tick', (data: unknown) => {
      const d = data as { count: number };
      setTimerCount(d.count);
      setLastEvent(data);
    }));
    unsubs.push(window.krema.on('timer-stopped', (data: unknown) => {
      setTimerRunning(false);
      setLastEvent({ event: 'timer-stopped', ...(data as Record<string, unknown>) });
    }));
    unsubs.push(window.krema.on('app:ready', () => {
      setAppReady(true);
      setLifecycleEvents(prev => [...prev, { event: 'app:ready', timestamp: new Date() }]);
    }));
    unsubs.push(window.krema.on('app:before-quit', () => {
      setLifecycleEvents(prev => [...prev, { event: 'app:before-quit', timestamp: new Date() }]);
    }));
    unsubs.push(window.krema.on('file-drop', (data: unknown) => {
      const event = data as FileDropEvent;
      setLastDropEvent(event);
      setDroppedFiles(event.payload.files);
      setDropHovering(false);
    }));
    unsubs.push(window.krema.on('file-drop-hover', (data: unknown) => {
      const event = data as FileDropHoverEvent;
      setDropHovering(event.payload.type === 'enter');
    }));
    unsubs.push(window.krema.on('menu:click', (data: unknown) => {
      const event = data as MenuClickEvent;
      setMenuClickEvents(prev => [{ id: event.payload.id, timestamp: new Date() }, ...prev].slice(0, 10));
    }));
    unsubs.push(window.krema.on('shortcut:triggered', (data: unknown) => {
      const event = data as { payload: { accelerator: string } };
      setShortcutTriggered(`${event.payload.accelerator} at ${new Date().toLocaleTimeString()}`);
    }));
    unsubs.push(window.krema.on('app:second-instance', (data: unknown) => {
      const event = data as { payload: { args: string[] } };
      setLifecycleEvents(prev => [...prev, { event: `second-instance: ${event.payload.args.join(' ')}`, timestamp: new Date() }]);
    }));
    unsubs.push(window.krema.on('custom-message', (data: unknown) => {
      setLifecycleEvents(prev => [...prev, { event: `custom-message: ${JSON.stringify(data)}`, timestamp: new Date() }]);
    }));
    unsubs.push(window.krema.on('broadcast-message', (data: unknown) => {
      setLifecycleEvents(prev => [...prev, { event: `broadcast: ${JSON.stringify(data)}`, timestamp: new Date() }]);
    }));

    return () => { unsubs.forEach(fn => fn()); };
  }, []);

  // Cleanup cursor tracking
  useEffect(() => {
    return () => { if (cursorIntervalRef.current) clearInterval(cursorIntervalRef.current); };
  }, []);

  // ==================== IPC ====================
  const greet = useCallback(async () => {
    try { setGreeting(await window.krema.invoke<string>('greet', { name })); setLastError(''); }
    catch (e: unknown) { setLastError((e as Error).message || 'Unknown error'); }
  }, [name]);

  const calculate = useCallback(async () => {
    try {
      const result = await window.krema.invoke<number>('calculate', { a: num1, b: num2, operation });
      setCalcResult(`Result: ${result}`); setLastError('');
    } catch (e: unknown) { setLastError((e as Error).message || 'Unknown error'); setCalcResult(''); }
  }, [num1, num2, operation]);

  // ==================== Events ====================
  const startTimer = async () => {
    try { await window.krema.invoke('startTimer', {}); setTimerRunning(true); setLastError(''); }
    catch (e: unknown) { setLastError((e as Error).message || 'Unknown error'); }
  };
  const stopTimer = async () => {
    try { await window.krema.invoke('stopTimer', {}); setTimerRunning(false); setLastError(''); }
    catch (e: unknown) { setLastError((e as Error).message || 'Unknown error'); }
  };

  // ==================== System ====================
  const getSystemInfo = async () => {
    try { setSystemInfo(await window.krema.invoke<SystemInfo>('systemInfo', {})); setEnvInfo(null); setLastError(''); }
    catch (e: unknown) { setLastError((e as Error).message || 'Unknown error'); }
  };
  const getEnvironmentInfo = async () => {
    try { setEnvInfo(await window.krema.invoke<EnvironmentInfo>('environmentInfo', {})); setSystemInfo(null); setLastError(''); }
    catch (e: unknown) { setLastError((e as Error).message || 'Unknown error'); }
  };

  // ==================== Clipboard ====================
  const clipboardReadCmd = async () => {
    try { setClipboardContent(await window.krema.invoke<string>('clipboardRead', {})); setLastError(''); }
    catch (e: unknown) { setLastError((e as Error).message || 'Unknown error'); }
  };
  const clipboardWriteCmd = async () => {
    try { await window.krema.invoke('clipboardWrite', { text: clipboardText }); setClipboardContent(`Copied: "${clipboardText}"`); setLastError(''); }
    catch (e: unknown) { setLastError((e as Error).message || 'Unknown error'); }
  };
  const builtinClipboardRead = async () => {
    try { setClipboardContent(await window.krema.invoke<string>('clipboard:readText', {}) || '(empty)'); setLastError(''); }
    catch (e: unknown) { setLastError((e as Error).message || 'Unknown error'); }
  };
  const builtinClipboardWrite = async () => {
    try { await window.krema.invoke('clipboard:writeText', { text: clipboardText }); setClipboardContent(`Copied: "${clipboardText}"`); setLastError(''); }
    catch (e: unknown) { setLastError((e as Error).message || 'Unknown error'); }
  };
  const checkClipboardFormats = async () => {
    try {
      const [hasText, hasImage, formats] = await Promise.all([
        window.krema.invoke<boolean>('clipboard:hasText', {}),
        window.krema.invoke<boolean>('clipboard:hasImage', {}),
        window.krema.invoke<string[]>('clipboard:getAvailableFormats', {})
      ]);
      setClipboardContent(`Has text: ${hasText}, Has image: ${hasImage}\nFormats: ${formats.join(', ')}`); setLastError('');
    } catch (e: unknown) { setLastError((e as Error).message || 'Unknown error'); }
  };

  // ==================== Shell ====================
  const openUrl = async (url: string) => {
    try { await window.krema.invoke('openUrl', { url }); setLastError(''); }
    catch (e: unknown) { setLastError((e as Error).message || 'Unknown error'); }
  };
  const showNotification = async () => {
    try { await window.krema.invoke('showNotification', { title: 'Krema Notification', body: 'Hello from the Java backend!' }); setLastError(''); }
    catch (e: unknown) { setLastError((e as Error).message || 'Unknown error'); }
  };
  const runCommand = async () => {
    try {
      const result = await window.krema.invoke<CommandResult>('runCommand', { command: commandInput });
      setCommandOutput(result.code === 0 ? result.stdout : `Error (code ${result.code}): ${result.stderr || result.stdout}`); setLastError('');
    } catch (e: unknown) { setLastError((e as Error).message || 'Unknown error'); }
  };

  // ==================== Native APIs ====================
  const analyzePath = async () => {
    try {
      const p = pathInput;
      const [dirname, basename, extname, isAbsolute, normalized] = await Promise.all([
        window.krema.invoke<string>('path:dirname', { path: p }),
        window.krema.invoke<string>('path:basename', { path: p, ext: null }),
        window.krema.invoke<string>('path:extname', { path: p }),
        window.krema.invoke<boolean>('path:isAbsolute', { path: p }),
        window.krema.invoke<string>('path:normalize', { path: p })
      ]);
      setPathResults([
        { label: 'dirname', value: dirname }, { label: 'basename', value: basename },
        { label: 'extname', value: extname }, { label: 'isAbsolute', value: String(isAbsolute) },
        { label: 'normalized', value: normalized }
      ]); setLastError('');
    } catch (e: unknown) { setLastError((e as Error).message || 'Unknown error'); }
  };
  const joinPaths = async () => {
    try {
      const result = await window.krema.invoke<string>('path:join', { paths: ['/Users', 'demo', 'documents', 'file.txt'] });
      setPathResults([{ label: 'path:join result', value: result }]); setLastError('');
    } catch (e: unknown) { setLastError((e as Error).message || 'Unknown error'); }
  };
  const resolvePath = async () => {
    try {
      const result = await window.krema.invoke<string>('path:resolve', { paths: ['.', 'src', '..', 'dist', 'app.js'] });
      setPathResults([{ label: 'path:resolve result', value: result }]); setLastError('');
    } catch (e: unknown) { setLastError((e as Error).message || 'Unknown error'); }
  };
  const getAppDirectories = async () => {
    try {
      const [appData, appConfig, appCache, appLog, home, temp] = await Promise.all([
        window.krema.invoke<string>('path:appData', {}), window.krema.invoke<string>('path:appConfig', {}),
        window.krema.invoke<string>('path:appCache', {}), window.krema.invoke<string>('path:appLog', {}),
        window.krema.invoke<string>('path:home', {}), window.krema.invoke<string>('path:temp', {})
      ]);
      setAppDirs([
        { label: 'App Data', value: appData }, { label: 'App Config', value: appConfig },
        { label: 'App Cache', value: appCache }, { label: 'App Log', value: appLog },
        { label: 'Home', value: home }, { label: 'Temp', value: temp }
      ]); setLastError('');
    } catch (e: unknown) { setLastError((e as Error).message || 'Unknown error'); }
  };
  const showInFinder = async () => {
    try { await window.krema.invoke('shell:showItemInFolder', { path: currentPath || paths?.home || '' }); setLastError(''); }
    catch (e: unknown) { setLastError((e as Error).message || 'Unknown error'); }
  };
  const openWithApp = async () => {
    try { await window.krema.invoke('shell:openWith', { path: 'https://github.com', app: 'Safari' }); setLastError(''); }
    catch (e: unknown) { setLastError((e as Error).message || 'Unknown error'); }
  };

  // Dialogs
  const openFileDialog = async () => {
    try {
      const result = await window.krema.invoke<string | null>('dialog:openFile', { title: 'Select a file', filters: [{ name: 'Text Files', extensions: ['txt', 'md'] }, { name: 'All Files', extensions: ['*'] }] });
      setDialogResult(result ? `Selected: ${result}` : 'Cancelled'); setLastError('');
    } catch (e: unknown) { setLastError((e as Error).message || 'Unknown error'); }
  };
  const saveFileDialog = async () => {
    try {
      const result = await window.krema.invoke<string | null>('dialog:saveFile', { title: 'Save file as', defaultPath: 'untitled.txt' });
      setDialogResult(result ? `Save to: ${result}` : 'Cancelled'); setLastError('');
    } catch (e: unknown) { setLastError((e as Error).message || 'Unknown error'); }
  };
  const selectFolderDialog = async () => {
    try {
      const result = await window.krema.invoke<string | null>('dialog:selectFolder', { title: 'Select a folder' });
      setDialogResult(result ? `Folder: ${result}` : 'Cancelled'); setLastError('');
    } catch (e: unknown) { setLastError((e as Error).message || 'Unknown error'); }
  };
  const confirmDialog = async () => {
    try {
      const result = await window.krema.invoke<boolean>('dialog:confirm', { title: 'Confirm Action', message: 'Are you sure you want to proceed?' });
      setDialogResult(result ? 'Confirmed!' : 'Cancelled'); setLastError('');
    } catch (e: unknown) { setLastError((e as Error).message || 'Unknown error'); }
  };
  const promptDialog = async () => {
    try {
      const result = await window.krema.invoke<string | null>('dialog:prompt', { title: 'Enter Your Name', message: 'What is your name?', defaultValue: 'World' });
      setDialogResult(result ? `Hello, ${result}!` : 'Cancelled'); setLastError('');
    } catch (e: unknown) { setLastError((e as Error).message || 'Unknown error'); }
  };
  const showBuiltinNotification = async () => {
    try { await window.krema.invoke('notification:show', { title: 'Built-in Notification', body: 'This uses the Krema built-in notification API!', options: { sound: 'default' } }); setLastError(''); }
    catch (e: unknown) { setLastError((e as Error).message || 'Unknown error'); }
  };

  // HTTP
  const httpRequest = async () => {
    setHttpLoading(true);
    try {
      const options: Record<string, unknown> = {};
      if (httpMethod !== 'GET' && httpMethod !== 'HEAD' && httpBody) {
        try { options['body'] = JSON.parse(httpBody); } catch { options['body'] = httpBody; }
      }
      options['headers'] = { 'Accept': 'application/json' };
      setHttpResult(await window.krema.invoke('http:request', { method: httpMethod, url: httpUrl, options })); setLastError('');
    } catch (e: unknown) { setLastError((e as Error).message || 'Unknown error'); setHttpResult(null); }
    finally { setHttpLoading(false); }
  };
  const httpFetch = async () => {
    setHttpLoading(true);
    try { setHttpResult(await window.krema.invoke('http:fetch', { url: httpUrl })); setLastError(''); }
    catch (e: unknown) { setLastError((e as Error).message || 'Unknown error'); setHttpResult(null); }
    finally { setHttpLoading(false); }
  };
  const httpFetchJson = async () => {
    setHttpLoading(true);
    try { setHttpResult(await window.krema.invoke('http:fetchJson', { url: httpUrl })); setLastError(''); }
    catch (e: unknown) { setLastError((e as Error).message || 'Unknown error'); setHttpResult(null); }
    finally { setHttpLoading(false); }
  };

  // ==================== Screen ====================
  const getAllScreens = async () => {
    try { setScreens(await window.krema.invoke<ScreenInfo[]>('screen:getAll', {})); setLastError(''); }
    catch (e: unknown) { setLastError((e as Error).message || 'Unknown error'); }
  };
  const getPrimaryScreen = async () => {
    try { setPrimaryScreen(await window.krema.invoke<ScreenInfo>('screen:getPrimary', {})); setLastError(''); }
    catch (e: unknown) { setLastError((e as Error).message || 'Unknown error'); }
  };
  const getCursorPosition = async () => {
    try { setCursorPosition(await window.krema.invoke<CursorPosition>('screen:getCursorPosition', {})); setLastError(''); }
    catch (e: unknown) { setLastError((e as Error).message || 'Unknown error'); }
  };
  const getScreenAtCursor = async () => {
    try { setScreenAtCursor(await window.krema.invoke<ScreenInfo>('screen:getScreenAtCursor', {})); setLastError(''); }
    catch (e: unknown) { setLastError((e as Error).message || 'Unknown error'); }
  };
  const toggleCursorTracking = () => {
    if (trackingCursor) {
      setTrackingCursor(false);
      if (cursorIntervalRef.current) { clearInterval(cursorIntervalRef.current); cursorIntervalRef.current = null; }
    } else {
      setTrackingCursor(true);
      cursorIntervalRef.current = setInterval(() => { getCursorPosition(); }, 100);
    }
  };

  // ==================== Drag & Drop ====================
  const configureDragDrop = async () => {
    try {
      const exts = acceptedExtensions ? acceptedExtensions.split(',').map(e => e.trim()).filter(e => e) : null;
      await window.krema.invoke('dragdrop:configure', { enabled: dropEnabled, acceptedExtensions: exts, dropZoneSelector: dropZoneSelector || null }); setLastError('');
    } catch (e: unknown) { setLastError((e as Error).message || 'Unknown error'); }
  };
  const enableDragDrop = async () => {
    try { await window.krema.invoke('dragdrop:enable', {}); setDropEnabled(true); setLastError(''); }
    catch (e: unknown) { setLastError((e as Error).message || 'Unknown error'); }
  };
  const disableDragDrop = async () => {
    try { await window.krema.invoke('dragdrop:disable', {}); setDropEnabled(false); setLastError(''); }
    catch (e: unknown) { setLastError((e as Error).message || 'Unknown error'); }
  };

  // ==================== Menus ====================
  const setApplicationMenu = async () => {
    try {
      const menu: MenuItem[] = [
        { label: 'App', submenu: [{ id: 'about', label: 'About Krema React' }, { type: 'separator' }, { id: 'preferences', label: 'Preferences...', accelerator: 'Cmd+,' }, { type: 'separator' }, { role: 'quit' }] },
        { label: 'Edit', submenu: [{ role: 'undo' }, { role: 'redo' }, { type: 'separator' }, { role: 'cut' }, { role: 'copy' }, { role: 'paste' }, { role: 'select-all' }] },
        { label: 'View', submenu: [{ id: 'reload', label: 'Reload', accelerator: 'Cmd+R' }, { type: 'separator' }, { id: 'zoom-in', label: 'Zoom In', accelerator: 'Cmd+Plus' }, { id: 'zoom-out', label: 'Zoom Out', accelerator: 'Cmd+Minus' }, { id: 'zoom-reset', label: 'Reset Zoom', accelerator: 'Cmd+0' }, { type: 'separator' }, { id: 'fullscreen', label: 'Toggle Full Screen', accelerator: 'Ctrl+Cmd+F' }] },
        { label: 'Window', submenu: [{ role: 'minimize' }, { role: 'close' }] },
        { label: 'Help', submenu: [{ id: 'docs', label: 'Documentation' }, { id: 'github', label: 'GitHub Repository' }, { type: 'separator' }, { id: 'report-issue', label: 'Report Issue...' }] }
      ];
      await window.krema.invoke('menu:setApplicationMenu', { menu }); setMenuConfigured(true); setLastError('');
    } catch (e: unknown) { setLastError((e as Error).message || 'Unknown error'); }
  };
  const showContextMenu = async () => {
    try {
      const items: MenuItem[] = [
        { id: 'ctx-copy', label: 'Copy' }, { id: 'ctx-paste', label: 'Paste' }, { type: 'separator' }, { id: 'ctx-refresh', label: 'Refresh' }, { type: 'separator' },
        { label: 'More Options', submenu: [{ id: 'ctx-option1', label: 'Option 1' }, { id: 'ctx-option2', label: 'Option 2' }, { id: 'ctx-option3', label: 'Option 3', type: 'checkbox', checked: true }] }
      ];
      await window.krema.invoke('menu:showContextMenu', { items }); setContextMenuResult('Context menu shown'); setLastError('');
    } catch (e: unknown) { setLastError((e as Error).message || 'Unknown error'); }
  };

  // ==================== Window ====================
  const getWindowState = async () => {
    try { setWindowState(await window.krema.invoke<WindowState>('window:getState')); setLastError(''); }
    catch (e: unknown) { setLastError((e as Error).message || 'Unknown error'); }
  };
  const getWindowTitle = async () => {
    try { setWindowTitle(await window.krema.invoke<string>('window:getTitle')); setLastError(''); }
    catch (e: unknown) { setLastError((e as Error).message || 'Unknown error'); }
  };
  const windowAction = async (cmd: string, args?: Record<string, unknown>) => {
    try { await window.krema.invoke(cmd, args); setLastError(''); }
    catch (e: unknown) { setLastError((e as Error).message || 'Unknown error'); }
  };
  const moveTo = async (position: string) => {
    try { await window.krema.invoke('positioner:moveTo', { position }); setPositionerResult(`Moved to ${position}`); setLastError(''); }
    catch (e: unknown) { setLastError((e as Error).message || 'Unknown error'); }
  };
  const listWindows = async () => {
    try { const r = await window.krema.invoke<{ windows: string[]; count: number }>('window:list'); setWindowList(r.windows); setLastError(''); }
    catch (e: unknown) { setLastError((e as Error).message || 'Unknown error'); }
  };
  const createChildWindow = async () => {
    try {
      const r = await window.krema.invoke<{ label: string }>('window:create', { title: childWindowTitle, width: 600, height: 400, url: childWindowUrl || undefined });
      setChildWindowLabel(r.label); await listWindows(); setLastError('');
    } catch (e: unknown) { setLastError((e as Error).message || 'Unknown error'); }
  };
  const showModal = async () => {
    try {
      const r = await window.krema.invoke<{ label: string }>('window:showModal', { title: 'Modal Window', width: 400, height: 300, parent: 'main' });
      setChildWindowLabel(r.label); await listWindows(); setLastError('');
    } catch (e: unknown) { setLastError((e as Error).message || 'Unknown error'); }
  };
  const closeChildWindow = async () => {
    if (!childWindowLabel) return;
    try { await window.krema.invoke('window:close', { label: childWindowLabel }); setChildWindowLabel(''); await listWindows(); setLastError(''); }
    catch (e: unknown) { setLastError((e as Error).message || 'Unknown error'); }
  };
  const sendToWindow = async () => {
    if (!childWindowLabel) return;
    try {
      let payload: unknown;
      try { payload = JSON.parse(windowMessagePayload); } catch { payload = windowMessagePayload; }
      await window.krema.invoke('window:sendTo', { label: childWindowLabel, event: windowMessageEvent || 'custom-message', payload }); setLastError('');
    } catch (e: unknown) { setLastError((e as Error).message || 'Unknown error'); }
  };
  const broadcastToWindows = async () => {
    try {
      let payload: unknown;
      try { payload = JSON.parse(windowMessagePayload); } catch { payload = windowMessagePayload; }
      await window.krema.invoke('window:broadcast', { event: windowMessageEvent || 'broadcast-message', payload }); setLastError('');
    } catch (e: unknown) { setLastError((e as Error).message || 'Unknown error'); }
  };

  // ==================== Plugins ====================
  const getOsInfo = async () => { try { setOsInfo(await window.krema.invoke<Record<string, unknown>>('os:info')); setLastError(''); } catch (e: unknown) { setLastError((e as Error).message || 'Unknown error'); } };
  const getOsMemory = async () => { try { setOsMemory(await window.krema.invoke<Record<string, number>>('os:memory')); setLastError(''); } catch (e: unknown) { setLastError((e as Error).message || 'Unknown error'); } };
  const getOsLocale = async () => { try { setOsLocale(await window.krema.invoke<Record<string, string>>('os:locale')); setLastError(''); } catch (e: unknown) { setLastError((e as Error).message || 'Unknown error'); } };

  const storeSet = async () => { try { await window.krema.invoke('store:set', { key: storeKey, value: storeValue }); setStoreResult(`Saved: ${storeKey} = ${storeValue}`); setLastError(''); } catch (e: unknown) { setLastError((e as Error).message || 'Unknown error'); } };
  const storeGet = async () => { try { setStoreResult(await window.krema.invoke('store:get', { key: storeKey, default: null })); setLastError(''); } catch (e: unknown) { setLastError((e as Error).message || 'Unknown error'); } };
  const storeDelete = async () => { try { const d = await window.krema.invoke<boolean>('store:delete', { key: storeKey }); setStoreResult(d ? `Deleted: ${storeKey}` : `Key not found: ${storeKey}`); setLastError(''); } catch (e: unknown) { setLastError((e as Error).message || 'Unknown error'); } };
  const storeGetKeys = async () => { try { setStoreKeys(await window.krema.invoke<string[]>('store:keys', {})); setLastError(''); } catch (e: unknown) { setLastError((e as Error).message || 'Unknown error'); } };
  const storeGetEntries = async () => { try { setStoreEntries(await window.krema.invoke<Record<string, unknown>>('store:entries', {})); setLastError(''); } catch (e: unknown) { setLastError((e as Error).message || 'Unknown error'); } };
  const storeClear = async () => { try { await window.krema.invoke('store:clear', {}); setStoreResult('Store cleared'); setStoreKeys([]); setStoreEntries({}); setLastError(''); } catch (e: unknown) { setLastError((e as Error).message || 'Unknown error'); } };

  const checkIsPrimary = async () => { try { setIsPrimaryInstance(await window.krema.invoke<boolean>('instance:isPrimary')); setLastError(''); } catch (e: unknown) { setLastError((e as Error).message || 'Unknown error'); } };
  const requestInstanceLock = async () => { try { setIsPrimaryInstance(await window.krema.invoke<boolean>('instance:requestLock')); setLastError(''); } catch (e: unknown) { setLastError((e as Error).message || 'Unknown error'); } };

  const getRegisteredShortcuts = async () => { try { setRegisteredShortcuts(await window.krema.invoke<string[]>('shortcut:getAll')); setLastError(''); } catch (e: unknown) { setLastError((e as Error).message || 'Unknown error'); } };
  const registerShortcut = async () => {
    try { const ok = await window.krema.invoke<boolean>('shortcut:register', { accelerator: shortcutAccelerator }); if (ok) await getRegisteredShortcuts(); setLastError(ok ? '' : 'Failed to register shortcut'); }
    catch (e: unknown) { setLastError((e as Error).message || 'Unknown error'); }
  };
  const unregisterShortcut = async () => { try { await window.krema.invoke('shortcut:unregister', { accelerator: shortcutAccelerator }); await getRegisteredShortcuts(); setLastError(''); } catch (e: unknown) { setLastError((e as Error).message || 'Unknown error'); } };
  const unregisterAllShortcuts = async () => { try { await window.krema.invoke('shortcut:unregisterAll'); setRegisteredShortcuts([]); setLastError(''); } catch (e: unknown) { setLastError((e as Error).message || 'Unknown error'); } };

  const checkDockSupported = async () => { try { setDockSupported(await window.krema.invoke<boolean>('dock:isSupported')); setLastError(''); } catch (e: unknown) { setLastError((e as Error).message || 'Unknown error'); } };
  const setDockBadgeCmd = async () => { try { await window.krema.invoke('dock:setBadge', { text: dockBadge }); setCurrentDockBadge(dockBadge); setLastError(''); } catch (e: unknown) { setLastError((e as Error).message || 'Unknown error'); } };
  const clearDockBadge = async () => { try { await window.krema.invoke('dock:clearBadge'); setCurrentDockBadge(''); setLastError(''); } catch (e: unknown) { setLastError((e as Error).message || 'Unknown error'); } };
  const getDockBadge = async () => { try { setCurrentDockBadge(await window.krema.invoke<string>('dock:getBadge')); setLastError(''); } catch (e: unknown) { setLastError((e as Error).message || 'Unknown error'); } };
  const bounceDock = async (critical: boolean) => { try { await window.krema.invoke('dock:bounce', { critical }); setLastError(''); } catch (e: unknown) { setLastError((e as Error).message || 'Unknown error'); } };

  // ==================== Files ====================
  const getPaths = async () => {
    try {
      const p = await window.krema.invoke<PathInfo>('getPaths', {});
      setPaths(p);
      setPathItems([{ label: 'Home', path: p.home }, { label: 'Desktop', path: p.desktop }, { label: 'Documents', path: p.documents }, { label: 'Downloads', path: p.downloads }, { label: 'Current', path: p.current }]);
      setCurrentPath(p.home); setLastError('');
    } catch (e: unknown) { setLastError((e as Error).message || 'Unknown error'); }
  };
  const listDirectory = async (path?: string) => {
    let dir = path || currentPath;
    if (!dir) { if (!paths) await getPaths(); dir = currentPath || paths?.home || ''; if (!dir) return; }
    try {
      setCurrentPath(dir);
      const f = await window.krema.invoke<FileInfo[]>('listDirectory', { path: dir });
      f.sort((a, b) => { if (a.isDirectory !== b.isDirectory) return a.isDirectory ? -1 : 1; return a.name.localeCompare(b.name); });
      setFiles(f); setSelectedFile(null); setFileContent(''); setLastError('');
    } catch (e: unknown) { setLastError((e as Error).message || 'Unknown error'); }
  };
  const readFile = async (path: string) => {
    try { let c = await window.krema.invoke<string>('readTextFile', { path }); if (c.length > 10000) c = c.substring(0, 10000) + '\n... (truncated)'; setFileContent(c); setLastError(''); }
    catch (e: unknown) { setLastError((e as Error).message || 'Unknown error'); }
  };

  // FS Plugin
  const fsCheckExists = async () => { try { setFsExistsResult(await window.krema.invoke<boolean>('fs:exists', { path: fsPath })); setFsPluginError(''); } catch (e: unknown) { setFsPluginError((e as Error).message || 'Unknown error'); } };
  const fsGetStat = async () => { try { setFsStat(await window.krema.invoke<FsFileInfo>('fs:stat', { path: fsPath })); setFsPluginError(''); } catch (e: unknown) { setFsStat(null); setFsPluginError((e as Error).message || 'Unknown error'); } };
  const fsListDir = async (path?: string) => {
    const dir = path || fsPath;
    try {
      setFsCurrentDir(dir);
      const f = await window.krema.invoke<FsFileInfo[]>('fs:readDir', { path: dir });
      f.sort((a, b) => { if (a.isDirectory !== b.isDirectory) return a.isDirectory ? -1 : 1; return a.name.localeCompare(b.name); });
      setFsFiles(f); setFsPluginError('');
    } catch (e: unknown) { setFsFiles([]); setFsPluginError((e as Error).message || 'Unknown error'); }
  };
  const fsReadFile = async () => {
    try { let c = await window.krema.invoke<string>('fs:readTextFile', { path: fsPath }); if (c.length > 5000) c = c.substring(0, 5000) + '\n... (truncated)'; setFsReadResult(c); setFsPluginError(''); }
    catch (e: unknown) { setFsReadResult(''); setFsPluginError((e as Error).message || 'Unknown error'); }
  };
  const fsWriteFile = async () => {
    try { await window.krema.invoke<boolean>('fs:writeTextFile', { path: fsWritePath, content: fsWriteContent }); setFsPluginError(''); setFsReadResult(`Written to ${fsWritePath}`); }
    catch (e: unknown) { setFsPluginError((e as Error).message || 'Unknown error'); }
  };
  const fsWriteAndReadBack = async () => {
    try {
      await window.krema.invoke<boolean>('fs:writeTextFile', { path: fsWritePath, content: fsWriteContent });
      const exists = await window.krema.invoke<boolean>('fs:exists', { path: fsWritePath });
      const content = await window.krema.invoke<string>('fs:readTextFile', { path: fsWritePath });
      const stat = await window.krema.invoke<FsFileInfo>('fs:stat', { path: fsWritePath });
      setFsReadResult(`Exists: ${exists}\nSize: ${stat.size} bytes\nContent: ${content}`); setFsPluginError('');
    } catch (e: unknown) { setFsPluginError((e as Error).message || 'Unknown error'); }
  };
  const fsNavigateDir = (file: FsFileInfo) => {
    if (file.isDirectory) { setFsPath(file.path); fsListDir(file.path); } else { setFsPath(file.path); }
  };

  // ==================== Secure Storage ====================
  const ssSetCmd = async () => { try { const r = await window.krema.invoke<boolean>('secureStorage:set', { key: ssKey, value: ssValue }); setSsResult(r ? `Stored "${ssKey}" successfully` : 'Failed to store'); setLastError(''); } catch (e: unknown) { setLastError((e as Error).message || 'Unknown error'); } };
  const ssGetCmd = async () => { try { const v = await window.krema.invoke<string | null>('secureStorage:get', { key: ssKey }); setSsResult(v !== null ? `Value: "${v}"` : `Key "${ssKey}" not found`); setLastError(''); } catch (e: unknown) { setLastError((e as Error).message || 'Unknown error'); } };
  const ssDeleteCmd = async () => { try { const r = await window.krema.invoke<boolean>('secureStorage:delete', { key: ssKey }); setSsResult(r ? `Deleted "${ssKey}"` : `Key "${ssKey}" not found`); setLastError(''); } catch (e: unknown) { setLastError((e as Error).message || 'Unknown error'); } };
  const ssHasCmd = async () => { try { const r = await window.krema.invoke<boolean>('secureStorage:has', { key: ssKey }); setSsResult(r ? `Key "${ssKey}" exists` : `Key "${ssKey}" not found`); setLastError(''); } catch (e: unknown) { setLastError((e as Error).message || 'Unknown error'); } };

  // ==================== Logging ====================
  const sendLog = async () => {
    try { await window.krema.invoke(`log:${logLevel}`, { message: logMessage }); setLogHistory(prev => [{ level: logLevel, message: logMessage, timestamp: new Date() }, ...prev].slice(0, 20)); setLastError(''); }
    catch (e: unknown) { setLastError((e as Error).message || 'Unknown error'); }
  };
  const sendAllLevels = async () => {
    try {
      const levels = ['trace', 'debug', 'info', 'warn', 'error'];
      const entries: { level: string; message: string; timestamp: Date }[] = [];
      for (const level of levels) {
        const msg = `[${level.toUpperCase()}] ${logMessage}`;
        await window.krema.invoke(`log:${level}`, { message: msg });
        entries.push({ level, message: msg, timestamp: new Date() });
      }
      setLogHistory(prev => [...entries, ...prev].slice(0, 20)); setLastError('');
    } catch (e: unknown) { setLastError((e as Error).message || 'Unknown error'); }
  };
  const getLogFilePath = async () => { try { setLogFilePath(await window.krema.invoke<string>('path:appLog', {})); setLastError(''); } catch (e: unknown) { setLastError((e as Error).message || 'Unknown error'); } };
  const revealLogFile = async () => {
    let p = logFilePath;
    if (!p) { try { p = await window.krema.invoke<string>('path:appLog', {}); setLogFilePath(p); } catch { return; } }
    try { await window.krema.invoke('shell:showItemInFolder', { path: p }); setLastError(''); } catch (e: unknown) { setLastError((e as Error).message || 'Unknown error'); }
  };

  const logColor = (level: string) => {
    switch (level) { case 'error': return '#e74c3c'; case 'warn': return '#f39c12'; case 'debug': return '#3498db'; case 'trace': return '#95a5a6'; default: return '#2ecc71'; }
  };

  // ==================== RENDER ====================
  return (
    <div className="app">
      <header>
        <h1>Krema React Demo</h1>
        <p className="subtitle">Desktop app powered by React + Java via Krema</p>
      </header>

      <nav className="tabs">
        {tabs.map(tab => (
          <button key={tab.id} className={activeTab === tab.id ? 'active' : ''} onClick={() => setActiveTab(tab.id)}>{tab.label}</button>
        ))}
      </nav>

      <main>
        {/* ==================== IPC ==================== */}
        {activeTab === 'ipc' && <>
          <div className="card">
            <h2>Greeting Command</h2>
            <p className="description">Call Java method with string parameter, receive string response</p>
            <div className="input-group">
              <input type="text" value={name} onChange={e => setName(e.target.value)} placeholder="Enter your name" onKeyDown={e => e.key === 'Enter' && greet()} />
              <button onClick={greet}>Greet</button>
            </div>
            {greeting && <div className="result">{greeting}</div>}
          </div>
          <div className="card">
            <h2>Calculator</h2>
            <p className="description">Pass multiple typed parameters (double, String) to Java</p>
            <div className="input-group">
              <input type="number" className="small" value={num1} onChange={e => setNum1(Number(e.target.value))} />
              <select value={operation} onChange={e => setOperation(e.target.value)}>
                <option value="add">+</option><option value="subtract">-</option><option value="multiply">&times;</option><option value="divide">&divide;</option>
              </select>
              <input type="number" className="small" value={num2} onChange={e => setNum2(Number(e.target.value))} />
              <button onClick={calculate}>Calculate</button>
            </div>
            {calcResult && <div className="result">{calcResult}</div>}
          </div>
        </>}

        {/* ==================== Events ==================== */}
        {activeTab === 'events' && <>
          <div className="card">
            <h2>Backend Events (Java &rarr; JavaScript)</h2>
            <p className="description">Java can push events to the frontend at any time</p>
            <div className="button-group">
              <button onClick={startTimer} disabled={timerRunning}>Start Timer</button>
              <button onClick={stopTimer} disabled={!timerRunning}>Stop Timer</button>
            </div>
            {timerCount > 0 && <div className="timer-display"><span className="count">{timerCount}</span><span className="label">seconds</span></div>}
            {lastEvent ? <pre className="code-block">Last event: {JSON.stringify(lastEvent, null, 2)}</pre> : null}
          </div>
          <div className="card">
            <h2>App Lifecycle Events</h2>
            <p className="description">Receive app:ready, app:before-quit events from the backend</p>
            {lifecycleEvents.length > 0 && <div className="info-grid">
              {lifecycleEvents.map((evt, i) => <div key={i} className="info-item"><span className="label">{evt.event}</span><span className="value">{fmtTime(evt.timestamp)}</span></div>)}
            </div>}
            {appReady && <div className="result">App Ready!</div>}
            {lifecycleEvents.length === 0 && <p style={{ color: '#888', fontSize: '0.85rem' }}>Lifecycle events will appear here when emitted by the backend</p>}
          </div>
        </>}

        {/* ==================== Native APIs ==================== */}
        {activeTab === 'native' && <>
          <div className="card">
            <h2>Path Utilities</h2>
            <p className="description">Built-in path manipulation APIs (path:dirname, path:basename, etc.)</p>
            <div className="input-group"><input type="text" value={pathInput} onChange={e => setPathInput(e.target.value)} placeholder="Enter a file path" /><button onClick={analyzePath}>Analyze Path</button></div>
            <div className="button-group"><button onClick={joinPaths}>Join Paths</button><button onClick={resolvePath}>Resolve Path</button></div>
            {pathResults.length > 0 && <div className="info-grid">{pathResults.map((item, i) => <div key={i} className="info-item"><span className="label">{item.label}</span><span className="value">{item.value}</span></div>)}</div>}
          </div>
          <div className="card">
            <h2>App Directories</h2>
            <p className="description">Platform-specific app data, config, cache, and log directories</p>
            <div className="button-group"><button onClick={getAppDirectories}>Get App Directories</button></div>
            {appDirs.length > 0 && <div className="info-grid">{appDirs.map((dir, i) => <div key={i} className="info-item"><span className="label">{dir.label}</span><span className="value clickable" onClick={() => listDirectory(dir.value)}>{dir.value}</span></div>)}</div>}
          </div>
          <div className="card">
            <h2>Shell Integration</h2>
            <p className="description">shell:showItemInFolder and shell:openWith</p>
            <div className="button-group"><button onClick={showInFinder}>Reveal in Finder</button><button onClick={openWithApp}>Open URL with Safari</button></div>
          </div>
          <div className="card">
            <h2>HTTP Client (Backend)</h2>
            <p className="description">Make HTTP requests from Java backend - bypasses CORS restrictions</p>
            <div className="input-group">
              <input type="text" value={httpUrl} onChange={e => setHttpUrl(e.target.value)} placeholder="URL" style={{ flex: 1 }} />
              <select value={httpMethod} onChange={e => setHttpMethod(e.target.value)} style={{ width: 100 }}>
                <option value="GET">GET</option><option value="POST">POST</option><option value="PUT">PUT</option><option value="DELETE">DELETE</option><option value="PATCH">PATCH</option>
              </select>
            </div>
            {httpMethod !== 'GET' && httpMethod !== 'HEAD' && <div className="input-group"><input type="text" value={httpBody} onChange={e => setHttpBody(e.target.value)} placeholder="Request body (JSON)" style={{ flex: 1 }} /></div>}
            <div className="button-group">
              <button onClick={httpRequest} disabled={httpLoading}>Send Request</button>
              <button onClick={httpFetch} disabled={httpLoading}>Simple Fetch</button>
              <button onClick={httpFetchJson} disabled={httpLoading}>Fetch JSON</button>
            </div>
            {httpLoading && <div className="status-line"><span className="status-indicator pending" />Loading...</div>}
            {httpResult ? <pre className="code-block">{JSON.stringify(httpResult, null, 2)}</pre> : null}
          </div>
          <div className="card">
            <h2>Native Dialogs</h2>
            <p className="description">Built-in file dialogs, confirmation, and prompts</p>
            <div className="button-group"><button onClick={openFileDialog}>Open File</button><button onClick={saveFileDialog}>Save File</button><button onClick={selectFolderDialog}>Select Folder</button></div>
            <div className="button-group"><button onClick={confirmDialog}>Confirm Dialog</button><button onClick={promptDialog}>Prompt Dialog</button><button onClick={showBuiltinNotification}>Notification</button></div>
            {dialogResult && <div className="result">{dialogResult}</div>}
          </div>
        </>}

        {/* ==================== Screen ==================== */}
        {activeTab === 'screen' && <>
          <div className="card">
            <h2>Display Information</h2>
            <p className="description">Get information about connected displays via NSScreen</p>
            <div className="button-group"><button onClick={getAllScreens}>Get All Screens</button><button onClick={getPrimaryScreen}>Get Primary Screen</button></div>
            {screens.length > 0 && <div className="screen-list">{screens.map((s, i) => (
              <div key={i} className="screen-card">
                <div className="screen-header"><span className="screen-name">{s.name || `Display ${i + 1}`}</span>{s.isPrimary && <span className="screen-badge">Primary</span>}</div>
                <div className="screen-details">
                  <div className="detail-row"><span className="label">Resolution</span><span className="value">{s.frame.width} &times; {s.frame.height}</span></div>
                  <div className="detail-row"><span className="label">Position</span><span className="value">({s.frame.x}, {s.frame.y})</span></div>
                  <div className="detail-row"><span className="label">Visible Area</span><span className="value">{s.visibleFrame.width} &times; {s.visibleFrame.height}</span></div>
                  <div className="detail-row"><span className="label">Scale Factor</span><span className="value">{s.scaleFactor}x</span></div>
                  <div className="detail-row"><span className="label">Refresh Rate</span><span className="value">{s.refreshRate} Hz</span></div>
                </div>
              </div>
            ))}</div>}
            {primaryScreen && screens.length === 0 && <pre className="code-block">{JSON.stringify(primaryScreen, null, 2)}</pre>}
          </div>
          <div className="card">
            <h2>Cursor Position</h2>
            <p className="description">Track cursor position in screen coordinates</p>
            <div className="button-group">
              <button onClick={getCursorPosition}>Get Cursor Position</button>
              <button onClick={getScreenAtCursor}>Get Screen at Cursor</button>
              <button onClick={toggleCursorTracking} className={trackingCursor ? 'active' : ''}>{trackingCursor ? 'Stop Tracking' : 'Start Tracking'}</button>
            </div>
            {cursorPosition && <div className="cursor-display">
              <div className="coord"><span className="label">X</span><span className="value">{Math.round(cursorPosition.x)}</span></div>
              <div className="coord"><span className="label">Y</span><span className="value">{Math.round(cursorPosition.y)}</span></div>
            </div>}
            {screenAtCursor && <div className="result">Cursor is on: <strong>{screenAtCursor.name || 'Display'}</strong> ({screenAtCursor.frame.width} &times; {screenAtCursor.frame.height})</div>}
          </div>
        </>}

        {/* ==================== Drag & Drop ==================== */}
        {activeTab === 'dragdrop' && <>
          <div className="card">
            <h2>Drag &amp; Drop Configuration</h2>
            <p className="description">Configure drag-drop behavior via HTML5 Drag API</p>
            <div className="input-group"><input type="text" value={acceptedExtensions} onChange={e => setAcceptedExtensions(e.target.value)} placeholder="Filter extensions (e.g., txt,pdf,png)" /></div>
            <div className="input-group"><input type="text" value={dropZoneSelector} onChange={e => setDropZoneSelector(e.target.value)} placeholder="Drop zone CSS selector (optional)" /></div>
            <div className="button-group">
              <button onClick={configureDragDrop}>Apply Configuration</button>
              <button onClick={enableDragDrop} disabled={dropEnabled}>Enable</button>
              <button onClick={disableDragDrop} disabled={!dropEnabled}>Disable</button>
            </div>
            <div className="status-line"><span className={`status-indicator ${dropEnabled ? 'ready' : 'pending'}`} />Drag &amp; Drop is {dropEnabled ? 'enabled' : 'disabled'}</div>
          </div>
          <div className="card">
            <h2>Drop Zone</h2>
            <p className="description">Drag files here to test the file-drop event</p>
            <div className={`drop-zone ${dropHovering ? 'hovering' : ''} ${droppedFiles.length > 0 ? 'has-files' : ''}`}>
              {droppedFiles.length === 0 && !dropHovering && <div className="drop-zone-content"><span className="drop-icon">&#x1F4C2;</span><p>Drag files here</p></div>}
              {dropHovering && <div className="drop-zone-content"><span className="drop-icon">&#x2B07;&#xFE0F;</span><p>Release to drop</p></div>}
              {droppedFiles.length > 0 && <div className="dropped-files">{droppedFiles.map((file, i) => (
                <div key={i} className="dropped-file"><span className="file-icon">&#x1F4C4;</span><div className="file-info"><span className="file-name">{file.name}</span><span className="file-meta">{file.type || 'unknown'} &bull; {formatSize(file.size)}</span></div></div>
              ))}</div>}
            </div>
            {droppedFiles.length > 0 && <div className="button-group"><button onClick={() => { setDroppedFiles([]); setLastDropEvent(null); }}>Clear Files</button></div>}
            {lastDropEvent && <pre className="code-block">Last event: {JSON.stringify(lastDropEvent, null, 2)}</pre>}
          </div>
        </>}

        {/* ==================== Menus ==================== */}
        {activeTab === 'menus' && <>
          <div className="card">
            <h2>Application Menu</h2>
            <p className="description">Set the native application menu bar (NSMenu)</p>
            <div className="button-group"><button onClick={setApplicationMenu}>Set Application Menu</button></div>
            {menuConfigured && <div className="result"><span className="status-indicator ready" />Application menu configured! Check the menu bar.</div>}
            {!menuConfigured && <p className="hint">Click the button to configure a custom application menu with App, Edit, View, Window, and Help menus.</p>}
          </div>
          <div className="card">
            <h2>Context Menu</h2>
            <p className="description">Show a native right-click context menu</p>
            <div className="button-group"><button onClick={showContextMenu}>Show Context Menu</button></div>
            {contextMenuResult && <div className="result">{contextMenuResult}</div>}
          </div>
          <div className="card">
            <h2>Menu Click Events</h2>
            <p className="description">Receive menu:click events when menu items are selected</p>
            <div className="button-group"><button onClick={() => setMenuClickEvents([])} disabled={menuClickEvents.length === 0}>Clear Events</button></div>
            {menuClickEvents.length > 0 && <div className="event-log">{menuClickEvents.map((evt, i) => <div key={i} className="event-item"><span className="event-id">{evt.id}</span><span className="event-time">{fmtTime(evt.timestamp)}</span></div>)}</div>}
            {menuClickEvents.length === 0 && <p className="hint">Menu click events will appear here when you select menu items.</p>}
          </div>
        </>}

        {/* ==================== Window ==================== */}
        {activeTab === 'window' && <>
          <div className="card">
            <h2>Window State</h2>
            <p className="description">Query the current window state</p>
            <div className="button-group"><button onClick={getWindowState}>Get State</button><button onClick={getWindowTitle}>Get Title</button></div>
            {windowState && <div className="info-grid">
              <div className="info-item"><span className="label">Position</span><span className="value">{windowState.x}, {windowState.y}</span></div>
              <div className="info-item"><span className="label">Size</span><span className="value">{windowState.width} x {windowState.height}</span></div>
              <div className="info-item"><span className="label">Minimized</span><span className="value">{String(windowState.minimized)}</span></div>
              <div className="info-item"><span className="label">Maximized</span><span className="value">{String(windowState.maximized)}</span></div>
              <div className="info-item"><span className="label">Fullscreen</span><span className="value">{String(windowState.fullscreen)}</span></div>
              <div className="info-item"><span className="label">Focused</span><span className="value">{String(windowState.focused)}</span></div>
            </div>}
            {windowTitle && <div className="result">Title: {windowTitle}</div>}
          </div>
          <div className="card">
            <h2>Window Actions</h2>
            <p className="description">Control window state</p>
            <div className="button-group">
              <button onClick={() => windowAction('window:minimize')}>Minimize</button><button onClick={() => windowAction('window:maximize')}>Maximize</button>
              <button onClick={() => windowAction('window:restore')}>Restore</button><button onClick={() => windowAction('window:toggleFullscreen')}>Toggle Fullscreen</button>
              <button onClick={() => windowAction('window:center')}>Center</button>
            </div>
          </div>
          <div className="card">
            <h2>Positioner Plugin</h2>
            <p className="description">Snap window to semantic screen positions via positioner:moveTo</p>
            <div className="position-grid">
              {['TOP_LEFT','TOP_CENTER','TOP_RIGHT','LEFT_CENTER','CENTER','RIGHT_CENTER','BOTTOM_LEFT','BOTTOM_CENTER','BOTTOM_RIGHT'].map(pos => (
                <button key={pos} onClick={() => moveTo(pos)}>{pos.replace('_', ' ').replace('_', ' ').split(' ').map(w => w[0] + w.slice(1).toLowerCase()).join(' ')}</button>
              ))}
            </div>
            {positionerResult && <div className="result">{positionerResult}</div>}
          </div>
          <div className="card">
            <h2>Size &amp; Position</h2>
            <p className="description">Resize and move the window</p>
            <div className="form-group"><label>Size:</label><input type="number" value={newWidth} onChange={e => setNewWidth(Number(e.target.value))} style={{ width: 80 }} /><span> x </span><input type="number" value={newHeight} onChange={e => setNewHeight(Number(e.target.value))} style={{ width: 80 }} /><button onClick={() => windowAction('window:setSize', { width: newWidth, height: newHeight })}>Set Size</button></div>
            <div className="form-group"><label>Position:</label><input type="number" value={newX} onChange={e => setNewX(Number(e.target.value))} style={{ width: 80 }} /><span> , </span><input type="number" value={newY} onChange={e => setNewY(Number(e.target.value))} style={{ width: 80 }} /><button onClick={() => windowAction('window:setPosition', { x: newX, y: newY })}>Set Position</button></div>
          </div>
          <div className="card">
            <h2>Window Properties</h2>
            <p className="description">Modify window properties</p>
            <div className="form-group"><label>Title:</label><input type="text" value={windowTitle} onChange={e => setWindowTitle(e.target.value)} placeholder="Window title" style={{ width: 200 }} /><button onClick={() => windowAction('window:setTitle', { title: windowTitle })}>Set Title</button></div>
            <div className="form-group"><label>Opacity:</label><input type="range" value={windowOpacity} onChange={e => setWindowOpacity(Number(e.target.value))} min="0.1" max="1" step="0.1" style={{ width: 150 }} /><span>{windowOpacity}</span><button onClick={() => windowAction('window:setOpacity', { opacity: windowOpacity })}>Set Opacity</button></div>
            <div className="button-group"><button onClick={() => windowAction('window:setAlwaysOnTop', { alwaysOnTop: true })}>Always On Top: ON</button><button onClick={() => windowAction('window:setAlwaysOnTop', { alwaysOnTop: false })}>Always On Top: OFF</button></div>
            <div className="button-group" style={{ marginTop: 8 }}><button onClick={() => windowAction('window:setResizable', { resizable: true })}>Resizable: ON</button><button onClick={() => windowAction('window:setResizable', { resizable: false })}>Resizable: OFF</button></div>
          </div>
          <div className="card">
            <h2>Frameless Window (macOS)</h2>
            <p className="description">Custom title bar styling - hiddenInset for modern macOS look</p>
            <div className="form-group"><label>Title Bar Style:</label><select value={titleBarStyle} onChange={e => setTitleBarStyle(e.target.value)} style={{ width: 150 }}><option value="default">Default</option><option value="hidden">Hidden</option><option value="hiddenInset">Hidden Inset</option></select><button onClick={() => windowAction('window:setTitleBarStyle', { style: titleBarStyle })}>Apply</button></div>
            <div className="form-group"><label>Traffic Lights Position:</label><input type="number" value={trafficLightX} onChange={e => setTrafficLightX(Number(e.target.value))} style={{ width: 60 }} /><span>,</span><input type="number" value={trafficLightY} onChange={e => setTrafficLightY(Number(e.target.value))} style={{ width: 60 }} /><button onClick={() => windowAction('window:setTrafficLightPosition', { x: trafficLightX, y: trafficLightY })}>Set Position</button></div>
            <div className="button-group"><button onClick={() => windowAction('window:setTitlebarTransparent', { transparent: true })}>Titlebar Transparent</button><button onClick={() => windowAction('window:setTitlebarTransparent', { transparent: false })}>Titlebar Opaque</button></div>
            <div className="button-group" style={{ marginTop: 8 }}><button onClick={() => windowAction('window:setFullSizeContentView', { extend: true })}>Full Size Content: ON</button><button onClick={() => windowAction('window:setFullSizeContentView', { extend: false })}>Full Size Content: OFF</button></div>
            <p className="hint">Use "Hidden Inset" for Electron-style frameless window with inset traffic lights.</p>
          </div>
          <div className="card">
            <h2>Multi-Window</h2>
            <p className="description">Create and manage multiple windows</p>
            <div className="form-group"><label>Title:</label><input type="text" value={childWindowTitle} onChange={e => setChildWindowTitle(e.target.value)} style={{ width: 150 }} /></div>
            <div className="form-group"><label>URL (optional):</label><input type="text" value={childWindowUrl} onChange={e => setChildWindowUrl(e.target.value)} placeholder="https://..." style={{ width: 200 }} /></div>
            <div className="button-group"><button onClick={createChildWindow}>Create Window</button><button onClick={showModal}>Show Modal</button><button onClick={listWindows}>List Windows</button></div>
            {windowList.length > 0 && <div className="info-grid"><div className="info-item"><span className="label">Open Windows</span><span className="value">{windowList.join(', ')}</span></div></div>}
            {childWindowLabel && <div className="result">Last created: <strong>{childWindowLabel}</strong> <button onClick={closeChildWindow} style={{ marginLeft: 8 }}>Close</button></div>}
          </div>
          <div className="card">
            <h2>Inter-Window Messaging</h2>
            <p className="description">Send messages between windows</p>
            <div className="form-group"><label>Event:</label><input type="text" value={windowMessageEvent} onChange={e => setWindowMessageEvent(e.target.value)} placeholder="custom-message" style={{ width: 150 }} /></div>
            <div className="form-group"><label>Payload:</label><input type="text" value={windowMessagePayload} onChange={e => setWindowMessagePayload(e.target.value)} placeholder='{"key": "value"}' style={{ width: 250 }} /></div>
            <div className="button-group"><button onClick={sendToWindow} disabled={!childWindowLabel}>Send to Window</button><button onClick={broadcastToWindows}>Broadcast to All</button></div>
            {!childWindowLabel && <p className="hint">Create a child window first to send messages to it.</p>}
          </div>
        </>}

        {/* ==================== Plugins ==================== */}
        {activeTab === 'plugins' && <>
          <div className="card">
            <h2>OS Information</h2>
            <p className="description">Built-in os:info, os:memory, os:locale APIs</p>
            <div className="button-group"><button onClick={getOsInfo}>Get OS Info</button><button onClick={getOsMemory}>Get Memory</button><button onClick={getOsLocale}>Get Locale</button></div>
            {osInfo && <div className="info-grid">
              {['platform','arch','version','hostname','username','cpuCount'].map(k => <div key={k} className="info-item"><span className="label">{k.charAt(0).toUpperCase() + k.slice(1).replace(/([A-Z])/g, ' $1')}</span><span className="value">{String(osInfo[k])}</span></div>)}
            </div>}
            {osMemory && <div className="info-grid">
              {['total','used','free','max'].map(k => <div key={k} className="info-item"><span className="label">{k.charAt(0).toUpperCase() + k.slice(1)} Memory</span><span className="value">{formatBytes(osMemory[k])}</span></div>)}
            </div>}
            {osLocale && <div className="info-grid">
              {['language','country','displayName','tag'].map(k => <div key={k} className="info-item"><span className="label">{k.charAt(0).toUpperCase() + k.slice(1).replace(/([A-Z])/g, ' $1')}</span><span className="value">{osLocale[k]}</span></div>)}
            </div>}
          </div>
          <div className="card">
            <h2>Persistent Store</h2>
            <p className="description">JSON-based key-value storage (store:get, store:set, etc.)</p>
            <div className="form-group"><label>Key:</label><input type="text" value={storeKey} onChange={e => setStoreKey(e.target.value)} style={{ width: 120 }} /><label>Value:</label><input type="text" value={storeValue} onChange={e => setStoreValue(e.target.value)} style={{ width: 150 }} /></div>
            <div className="button-group"><button onClick={storeSet}>Set</button><button onClick={storeGet}>Get</button><button onClick={storeDelete}>Delete</button></div>
            <div className="button-group" style={{ marginTop: 8 }}><button onClick={storeGetKeys}>Get Keys</button><button onClick={storeGetEntries}>Get All Entries</button><button onClick={storeClear}>Clear Store</button></div>
            {storeResult !== null && <div className="result">Result: {JSON.stringify(storeResult)}</div>}
            {storeKeys.length > 0 && <div className="info-grid"><div className="info-item"><span className="label">Keys</span><span className="value">{storeKeys.join(', ') || '(empty)'}</span></div></div>}
            {Object.keys(storeEntries).length > 0 && <pre className="code-block">{JSON.stringify(storeEntries, null, 2)}</pre>}
          </div>
          <div className="card">
            <h2>Single Instance</h2>
            <p className="description">Ensure only one instance of the app is running</p>
            <div className="button-group"><button onClick={checkIsPrimary}>Check Primary</button><button onClick={requestInstanceLock}>Request Lock</button></div>
            <div className="status-line"><span className={`status-indicator ${isPrimaryInstance ? 'ready' : 'pending'}`} />{isPrimaryInstance ? 'This is the primary instance' : 'Instance status unknown'}</div>
            <p className="hint">When a second instance launches, it will emit an 'app:second-instance' event (check the Events tab for lifecycle events).</p>
          </div>
          <div className="card">
            <h2>Global Shortcuts</h2>
            <p className="description">Register system-wide keyboard shortcuts (limited mode on macOS)</p>
            <div className="input-group"><input type="text" value={shortcutAccelerator} onChange={e => setShortcutAccelerator(e.target.value)} placeholder="e.g., Cmd+Shift+K" style={{ width: 200 }} /><button onClick={registerShortcut}>Register</button><button onClick={unregisterShortcut}>Unregister</button></div>
            <div className="button-group"><button onClick={getRegisteredShortcuts}>List Shortcuts</button><button onClick={unregisterAllShortcuts}>Unregister All</button></div>
            {registeredShortcuts.length > 0 && <div className="info-grid"><div className="info-item"><span className="label">Registered</span><span className="value">{registeredShortcuts.join(', ')}</span></div></div>}
            {shortcutTriggered && <div className="result">Last triggered: <strong>{shortcutTriggered}</strong></div>}
            {registeredShortcuts.length === 0 && <p className="hint">Note: Global shortcuts require Accessibility permissions on macOS. Currently running in limited mode.</p>}
          </div>
          <div className="card">
            <h2>Dock Badge</h2>
            <p className="description">Set badge text on the dock icon and bounce for attention</p>
            <div className="input-group"><input type="text" value={dockBadge} onChange={e => setDockBadge(e.target.value)} placeholder="Badge text (e.g., 5)" style={{ width: 120 }} /><button onClick={setDockBadgeCmd}>Set Badge</button><button onClick={clearDockBadge}>Clear</button></div>
            <div className="button-group"><button onClick={getDockBadge}>Get Badge</button><button onClick={() => bounceDock(false)}>Bounce Once</button><button onClick={() => bounceDock(true)}>Bounce Critical</button></div>
            {currentDockBadge && <div className="result">Current badge: <strong>{currentDockBadge}</strong></div>}
            <div className="button-group" style={{ marginTop: 12 }}><button onClick={checkDockSupported}>Check Support</button></div>
            <div className="status-line"><span className={`status-indicator ${dockSupported ? 'ready' : 'pending'}`} />Dock badge is {dockSupported ? 'supported' : 'not supported'} on this platform</div>
          </div>
        </>}

        {/* ==================== System ==================== */}
        {activeTab === 'system' && <div className="card">
          <h2>System Information</h2>
          <p className="description">Access OS and JVM information from Java</p>
          <div className="button-group"><button onClick={getSystemInfo}>Get System Info</button><button onClick={getEnvironmentInfo}>Get Environment</button></div>
          {systemInfo && <div className="info-grid">
            <div className="info-item"><span className="label">OS</span><span className="value">{systemInfo.osName} {systemInfo.osVersion}</span></div>
            <div className="info-item"><span className="label">Architecture</span><span className="value">{systemInfo.osArch}</span></div>
            <div className="info-item"><span className="label">Java</span><span className="value">{systemInfo.javaVersion} ({systemInfo.javaVendor})</span></div>
            <div className="info-item"><span className="label">Processors</span><span className="value">{systemInfo.processors}</span></div>
            <div className="info-item"><span className="label">Memory</span><span className="value">{systemInfo.freeMemoryMb}MB free / {systemInfo.maxMemoryMb}MB max</span></div>
          </div>}
          {envInfo && <pre className="code-block">{JSON.stringify(envInfo, null, 2)}</pre>}
        </div>}

        {/* ==================== Clipboard ==================== */}
        {activeTab === 'clipboard' && <>
          <div className="card">
            <h2>Clipboard Access (Custom Commands)</h2>
            <p className="description">Read/write system clipboard via custom Java commands</p>
            <div className="input-group"><input type="text" value={clipboardText} onChange={e => setClipboardText(e.target.value)} placeholder="Text to copy" /><button onClick={clipboardWriteCmd}>Copy to Clipboard</button></div>
            <div className="button-group"><button onClick={clipboardReadCmd}>Read from Clipboard</button></div>
            {clipboardContent && <div className="result"><strong>Clipboard content:</strong> {clipboardContent}</div>}
          </div>
          <div className="card">
            <h2>Clipboard API (Built-in)</h2>
            <p className="description">Using Krema's built-in clipboard:readText, clipboard:writeText</p>
            <div className="input-group"><input type="text" value={clipboardText} onChange={e => setClipboardText(e.target.value)} placeholder="Text to copy" /><button onClick={builtinClipboardWrite}>Write (Built-in)</button></div>
            <div className="button-group"><button onClick={builtinClipboardRead}>Read (Built-in)</button><button onClick={checkClipboardFormats}>Check Formats</button></div>
          </div>
        </>}

        {/* ==================== Shell ==================== */}
        {activeTab === 'shell' && <>
          <div className="card">
            <h2>Shell Commands</h2>
            <p className="description">Execute shell commands and open URLs/files via Java</p>
            <div className="button-group"><button onClick={() => openUrl('https://github.com')}>Open GitHub</button><button onClick={() => openUrl('https://react.dev')}>Open React.dev</button><button onClick={showNotification}>Show Notification</button></div>
            <div className="input-group"><input type="text" value={commandInput} onChange={e => setCommandInput(e.target.value)} placeholder='Enter command (e.g., ls -la)' onKeyDown={e => e.key === 'Enter' && runCommand()} /><button onClick={runCommand}>Run</button></div>
            {commandOutput && <pre className="code-block">{commandOutput}</pre>}
          </div>
          <div className="card">
            <h2>Built-in Shell APIs</h2>
            <p className="description">shell:open, shell:showItemInFolder, shell:openWith</p>
            <div className="button-group"><button onClick={showInFinder}>Reveal Current in Finder</button><button onClick={openWithApp}>Open URL with Safari</button></div>
          </div>
        </>}

        {/* ==================== Files ==================== */}
        {activeTab === 'files' && <>
          <div className="card">
            <h2>File System (Custom Commands)</h2>
            <p className="description">Access file system via custom Java commands in Commands.java</p>
            <div className="button-group"><button onClick={getPaths}>Get System Paths</button><button onClick={() => listDirectory()}>List Current Directory</button></div>
            {paths && <div className="info-grid">{pathItems.map((item, i) => <div key={i} className="info-item"><span className="label">{item.label}</span><span className="value clickable" onClick={() => listDirectory(item.path)}>{item.path}</span></div>)}</div>}
          </div>
          {files.length > 0 && <div className="card">
            <h2>{currentPath}</h2>
            <div className="file-list">{files.map((file, i) => (
              <div key={i} className="file-item" onClick={() => file.isDirectory ? listDirectory(file.path) : (setSelectedFile(file), setFileContent(''))}>
                <span className="icon">{file.isDirectory ? '\u{1F4C1}' : '\u{1F4C4}'}</span><span className="name">{file.name}</span>{!file.isDirectory && <span className="size">{formatSize(file.size)}</span>}
              </div>
            ))}</div>
          </div>}
          {selectedFile && <div className="card">
            <h2>{selectedFile.name}</h2>
            {!fileContent && <button onClick={() => readFile(selectedFile.path)}>Read File</button>}
            {fileContent && <pre className="code-block file-content">{fileContent}</pre>}
          </div>}
          <div className="card">
            <h2>FS Plugin (Auto-Discovered)</h2>
            <p className="description">These commands come from FsPlugin, loaded automatically via ServiceLoader. Commands are prefixed with <code>fs:</code> (e.g., fs:exists, fs:readDir, fs:stat).</p>
            <div className="input-group"><input type="text" value={fsPath} onChange={e => setFsPath(e.target.value)} placeholder="Enter a path" /></div>
            <div className="button-group"><button onClick={fsCheckExists}>fs:exists</button><button onClick={fsGetStat}>fs:stat</button><button onClick={() => fsListDir()}>fs:readDir</button><button onClick={fsReadFile}>fs:readTextFile</button></div>
            {fsExistsResult !== null && <div className="info-grid"><div className="info-item"><span className="label">Exists</span><span className="value">{String(fsExistsResult)}</span></div></div>}
            {fsStat && <div className="info-grid">
              <div className="info-item"><span className="label">Name</span><span className="value">{fsStat.name}</span></div>
              <div className="info-item"><span className="label">Path</span><span className="value">{fsStat.path}</span></div>
              <div className="info-item"><span className="label">Type</span><span className="value">{fsStat.isDirectory ? 'Directory' : 'File'}</span></div>
              <div className="info-item"><span className="label">Size</span><span className="value">{formatSize(fsStat.size)}</span></div>
              <div className="info-item"><span className="label">Modified</span><span className="value">{new Date(fsStat.modifiedTime).toLocaleString()}</span></div>
            </div>}
            {fsPluginError && <div className="result" style={{ color: '#e74c3c' }}>{fsPluginError}</div>}
          </div>
          {fsFiles.length > 0 && <div className="card">
            <h2>{fsCurrentDir} <span style={{ fontSize: '0.7em', opacity: 0.6 }}>(via fs:readDir)</span></h2>
            <div className="file-list">{fsFiles.map((file, i) => (
              <div key={i} className="file-item" onClick={() => fsNavigateDir(file)}>
                <span className="icon">{file.isDirectory ? '\u{1F4C1}' : '\u{1F4C4}'}</span><span className="name">{file.name}</span>{!file.isDirectory && <span className="size">{formatSize(file.size)}</span>}
              </div>
            ))}</div>
          </div>}
          <div className="card">
            <h2>FS Plugin - Write &amp; Read</h2>
            <p className="description">Write a file with fs:writeTextFile, then read it back with fs:readTextFile</p>
            <div className="input-group"><input type="text" value={fsWritePath} onChange={e => setFsWritePath(e.target.value)} placeholder="File path to write" /></div>
            <div className="input-group"><input type="text" value={fsWriteContent} onChange={e => setFsWriteContent(e.target.value)} placeholder="Content to write" /></div>
            <div className="button-group"><button onClick={fsWriteFile}>Write File</button><button onClick={fsWriteAndReadBack}>Write &amp; Read Back</button></div>
            {fsReadResult && <pre className="code-block">{fsReadResult}</pre>}
          </div>
        </>}

        {/* ==================== Secure Storage ==================== */}
        {activeTab === 'securestorage' && <div className="card">
          <h2>Secure Storage (Platform Keychain)</h2>
          <p className="description">Store and retrieve secrets using the platform's native credential store (Keychain / Credential Manager / Secret Service)</p>
          <div className="input-group"><input type="text" value={ssKey} onChange={e => setSsKey(e.target.value)} placeholder="Key name" /><input type="text" value={ssValue} onChange={e => setSsValue(e.target.value)} placeholder="Secret value" /></div>
          <div className="button-group"><button onClick={ssSetCmd}>Set</button><button onClick={ssGetCmd}>Get</button><button onClick={ssDeleteCmd}>Delete</button><button onClick={ssHasCmd}>Has</button></div>
          {ssResult && <div className="result">{ssResult}</div>}
        </div>}

        {/* ==================== Logging ==================== */}
        {activeTab === 'logging' && <>
          <div className="card">
            <h2>Structured Logging</h2>
            <p className="description">Send log messages to the backend. Logs are written as JSON Lines to rotating .jsonl files with app metadata (name, version, OS, session ID).</p>
            <div className="input-group">
              <select value={logLevel} onChange={e => setLogLevel(e.target.value)} style={{ width: 100 }}>
                <option value="trace">TRACE</option><option value="debug">DEBUG</option><option value="info">INFO</option><option value="warn">WARN</option><option value="error">ERROR</option>
              </select>
              <input type="text" value={logMessage} onChange={e => setLogMessage(e.target.value)} placeholder="Log message" onKeyDown={e => e.key === 'Enter' && sendLog()} />
              <button onClick={sendLog}>Send</button>
            </div>
            <div className="button-group"><button onClick={sendAllLevels}>Send All Levels</button><button onClick={() => setLogHistory([])} disabled={logHistory.length === 0}>Clear History</button></div>
          </div>
          <div className="card">
            <h2>Log File</h2>
            <p className="description">JSON Lines output location (app.jsonl with rotation)</p>
            <div className="button-group"><button onClick={getLogFilePath}>Get Log Path</button><button onClick={revealLogFile}>Reveal in Finder</button></div>
            {logFilePath && <div className="result">{logFilePath}</div>}
            <p className="hint">Each line in app.jsonl is a JSON object with: timestamp, level, logger, message, appName, appVersion, os, sessionId. Null fields (errorMessage, stackTrace) are omitted for non-error entries.</p>
          </div>
          {logHistory.length > 0 && <div className="card">
            <h2>Sent Log History</h2>
            <p className="description">Recent log messages sent from this session</p>
            <div className="event-log">{logHistory.map((entry, i) => (
              <div key={i} className="event-item">
                <span className="event-id" style={{ color: logColor(entry.level) }}>{entry.level.toUpperCase()}</span>
                <span className="event-time">{fmtTime(entry.timestamp)}</span>
                <span style={{ marginLeft: 8, opacity: 0.8 }}>{entry.message}</span>
              </div>
            ))}</div>
          </div>}
        </>}
      </main>

      {lastError && <div className="error-toast" onClick={() => setLastError('')}><span className="icon">&#x26A0;&#xFE0F;</span><span className="message">{lastError}</span><span className="close">&times;</span></div>}

      <footer><p>Open DevTools (Cmd+Option+I) to see IPC messages</p></footer>
    </div>
  );
}
