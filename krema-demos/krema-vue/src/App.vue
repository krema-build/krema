<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue';

// ==================== Types ====================
interface SystemInfo { osName: string; osVersion: string; osArch: string; javaVersion: string; javaVendor: string; processors: number; maxMemoryMb: number; totalMemoryMb: number; freeMemoryMb: number; }
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

const tabsList = [
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
function logColor(level: string): string {
  switch (level) { case 'error': return '#e74c3c'; case 'warn': return '#f39c12'; case 'debug': return '#3498db'; case 'trace': return '#95a5a6'; default: return '#2ecc71'; }
}

// ==================== State ====================
const activeTab = ref('ipc');
const lastError = ref('');

// IPC
const name = ref('World');
const greeting = ref('');
const num1 = ref(10);
const num2 = ref(5);
const operation = ref('add');
const calcResult = ref('');

// Events
const timerCount = ref(0);
const timerRunning = ref(false);
const lastEvent = ref<unknown>(null);
const lifecycleEvents = ref<{ event: string; timestamp: Date }[]>([]);
const appReady = ref(false);

// Native APIs
const pathInput = ref('/Users/demo/documents/file.txt');
const pathResults = ref<{ label: string; value: string }[]>([]);
const appDirs = ref<{ label: string; value: string }[]>([]);
const dialogResult = ref('');
const httpUrl = ref('https://jsonplaceholder.typicode.com/todos/1');
const httpMethod = ref('GET');
const httpBody = ref('{"title": "test", "completed": false}');
const httpResult = ref<unknown>(null);
const httpLoading = ref(false);

// Screen
const screens = ref<ScreenInfo[]>([]);
const primaryScreen = ref<ScreenInfo | null>(null);
const cursorPosition = ref<CursorPosition | null>(null);
const screenAtCursor = ref<ScreenInfo | null>(null);
const trackingCursor = ref(false);
let cursorInterval: ReturnType<typeof setInterval> | null = null;

// Drag & Drop
const dropEnabled = ref(true);
const dropHovering = ref(false);
const droppedFiles = ref<DroppedFile[]>([]);
const lastDropEvent = ref<FileDropEvent | null>(null);
const acceptedExtensions = ref('');
const dropZoneSelector = ref('');

// Menus
const menuClickEvents = ref<{ id: string; timestamp: Date }[]>([]);
const contextMenuResult = ref('');
const menuConfigured = ref(false);

// Window
const windowState = ref<WindowState | null>(null);
const windowTitle = ref('');
const windowOpacity = ref(1.0);
const newWidth = ref(1024);
const newHeight = ref(768);
const newX = ref(100);
const newY = ref(100);
const positionerResult = ref('');
const titleBarStyle = ref('default');
const trafficLightX = ref(15);
const trafficLightY = ref(15);
const childWindowLabel = ref('');
const childWindowTitle = ref('Child Window');
const childWindowUrl = ref('');
const windowList = ref<string[]>([]);
const windowMessageEvent = ref('');
const windowMessagePayload = ref('{"message": "Hello from main!"}');

// Plugins
const osInfo = ref<Record<string, unknown> | null>(null);
const osMemory = ref<Record<string, number> | null>(null);
const osLocale = ref<Record<string, string> | null>(null);
const storeKey = ref('myKey');
const storeValue = ref('myValue');
const storeResult = ref<unknown>(null);
const storeKeys = ref<string[]>([]);
const storeEntries = ref<Record<string, unknown>>({});
const isPrimaryInstance = ref(false);
const shortcutAccelerator = ref('Cmd+Shift+K');
const registeredShortcuts = ref<string[]>([]);
const shortcutTriggered = ref('');
const dockBadge = ref('5');
const currentDockBadge = ref('');
const dockSupported = ref(false);

// System
const systemInfo = ref<SystemInfo | null>(null);
const envInfo = ref<EnvironmentInfo | null>(null);

// Clipboard
const clipboardText = ref('Hello from Krema!');
const clipboardContent = ref('');

// Shell
const commandInput = ref('echo "Hello from Java shell!"');
const commandOutput = ref('');

// Files
const paths = ref<PathInfo | null>(null);
const pathItems = ref<{ label: string; path: string }[]>([]);
const currentPath = ref('');
const files = ref<FileInfo[]>([]);
const selectedFile = ref<FileInfo | null>(null);
const fileContent = ref('');

// FS Plugin
const fsPath = ref('/tmp');
const fsExistsResult = ref<boolean | null>(null);
const fsStat = ref<FsFileInfo | null>(null);
const fsFiles = ref<FsFileInfo[]>([]);
const fsCurrentDir = ref('');
const fsWritePath = ref('/tmp/krema-plugin-test.txt');
const fsWriteContent = ref('Hello from Krema FS Plugin!');
const fsReadResult = ref('');
const fsPluginError = ref('');

// Secure Storage
const ssKey = ref('my-secret');
const ssValue = ref('super-secret-value');
const ssResult = ref('');

// Logging
const logMessage = ref('Hello from Vue frontend!');
const logLevel = ref('info');
const logHistory = ref<{ level: string; message: string; timestamp: Date }[]>([]);
const logFilePath = ref('');

// ==================== Event Listeners ====================
const unsubs: (() => void)[] = [];

onMounted(() => {
  if (!window.krema) return;
  unsubs.push(window.krema.on('timer-tick', (data: unknown) => { const d = data as { count: number }; timerCount.value = d.count; lastEvent.value = data; }));
  unsubs.push(window.krema.on('timer-stopped', (data: unknown) => { timerRunning.value = false; lastEvent.value = { event: 'timer-stopped', ...(data as Record<string, unknown>) }; }));
  unsubs.push(window.krema.on('app:ready', () => { appReady.value = true; lifecycleEvents.value.push({ event: 'app:ready', timestamp: new Date() }); }));
  unsubs.push(window.krema.on('app:before-quit', () => { lifecycleEvents.value.push({ event: 'app:before-quit', timestamp: new Date() }); }));
  unsubs.push(window.krema.on('file-drop', (data: unknown) => { const e = data as FileDropEvent; lastDropEvent.value = e; droppedFiles.value = e.payload.files; dropHovering.value = false; }));
  unsubs.push(window.krema.on('file-drop-hover', (data: unknown) => { const e = data as FileDropHoverEvent; dropHovering.value = e.payload.type === 'enter'; }));
  unsubs.push(window.krema.on('menu:click', (data: unknown) => { const e = data as MenuClickEvent; menuClickEvents.value.unshift({ id: e.payload.id, timestamp: new Date() }); if (menuClickEvents.value.length > 10) menuClickEvents.value.pop(); }));
  unsubs.push(window.krema.on('shortcut:triggered', (data: unknown) => { const e = data as { payload: { accelerator: string } }; shortcutTriggered.value = `${e.payload.accelerator} at ${new Date().toLocaleTimeString()}`; }));
  unsubs.push(window.krema.on('app:second-instance', (data: unknown) => { const e = data as { payload: { args: string[] } }; lifecycleEvents.value.push({ event: `second-instance: ${e.payload.args.join(' ')}`, timestamp: new Date() }); }));
  unsubs.push(window.krema.on('custom-message', (data: unknown) => { lifecycleEvents.value.push({ event: `custom-message: ${JSON.stringify(data)}`, timestamp: new Date() }); }));
  unsubs.push(window.krema.on('broadcast-message', (data: unknown) => { lifecycleEvents.value.push({ event: `broadcast: ${JSON.stringify(data)}`, timestamp: new Date() }); }));
});
onUnmounted(() => { unsubs.forEach(fn => fn()); if (cursorInterval) clearInterval(cursorInterval); });

// ==================== Methods ====================
async function greet() { try { greeting.value = await window.krema.invoke<string>('greet', { name: name.value }); lastError.value = ''; } catch (e: unknown) { lastError.value = (e as Error).message || 'Unknown error'; } }
async function calculate() { try { const r = await window.krema.invoke<number>('calculate', { a: num1.value, b: num2.value, operation: operation.value }); calcResult.value = `Result: ${r}`; lastError.value = ''; } catch (e: unknown) { lastError.value = (e as Error).message || 'Unknown error'; calcResult.value = ''; } }
async function startTimer() { try { await window.krema.invoke('startTimer', {}); timerRunning.value = true; lastError.value = ''; } catch (e: unknown) { lastError.value = (e as Error).message || 'Unknown error'; } }
async function stopTimer() { try { await window.krema.invoke('stopTimer', {}); timerRunning.value = false; lastError.value = ''; } catch (e: unknown) { lastError.value = (e as Error).message || 'Unknown error'; } }
async function getSystemInfo() { try { systemInfo.value = await window.krema.invoke<SystemInfo>('systemInfo', {}); envInfo.value = null; lastError.value = ''; } catch (e: unknown) { lastError.value = (e as Error).message || 'Unknown error'; } }
async function getEnvironmentInfo() { try { envInfo.value = await window.krema.invoke<EnvironmentInfo>('environmentInfo', {}); systemInfo.value = null; lastError.value = ''; } catch (e: unknown) { lastError.value = (e as Error).message || 'Unknown error'; } }

// Clipboard
async function clipboardReadCmd() { try { clipboardContent.value = await window.krema.invoke<string>('clipboardRead', {}); lastError.value = ''; } catch (e: unknown) { lastError.value = (e as Error).message || 'Unknown error'; } }
async function clipboardWriteCmd() { try { await window.krema.invoke('clipboardWrite', { text: clipboardText.value }); clipboardContent.value = `Copied: "${clipboardText.value}"`; lastError.value = ''; } catch (e: unknown) { lastError.value = (e as Error).message || 'Unknown error'; } }
async function builtinClipboardRead() { try { clipboardContent.value = await window.krema.invoke<string>('clipboard:readText', {}) || '(empty)'; lastError.value = ''; } catch (e: unknown) { lastError.value = (e as Error).message || 'Unknown error'; } }
async function builtinClipboardWrite() { try { await window.krema.invoke('clipboard:writeText', { text: clipboardText.value }); clipboardContent.value = `Copied: "${clipboardText.value}"`; lastError.value = ''; } catch (e: unknown) { lastError.value = (e as Error).message || 'Unknown error'; } }
async function checkClipboardFormats() { try { const [hasText, hasImage, formats] = await Promise.all([window.krema.invoke<boolean>('clipboard:hasText', {}), window.krema.invoke<boolean>('clipboard:hasImage', {}), window.krema.invoke<string[]>('clipboard:getAvailableFormats', {})]); clipboardContent.value = `Has text: ${hasText}, Has image: ${hasImage}\nFormats: ${formats.join(', ')}`; lastError.value = ''; } catch (e: unknown) { lastError.value = (e as Error).message || 'Unknown error'; } }

// Shell
async function openUrl(url: string) { try { await window.krema.invoke('openUrl', { url }); lastError.value = ''; } catch (e: unknown) { lastError.value = (e as Error).message || 'Unknown error'; } }
async function showNotification() { try { await window.krema.invoke('showNotification', { title: 'Krema Notification', body: 'Hello from the Java backend!' }); lastError.value = ''; } catch (e: unknown) { lastError.value = (e as Error).message || 'Unknown error'; } }
async function runCommand() { try { const r = await window.krema.invoke<CommandResult>('runCommand', { command: commandInput.value }); commandOutput.value = r.code === 0 ? r.stdout : `Error (code ${r.code}): ${r.stderr || r.stdout}`; lastError.value = ''; } catch (e: unknown) { lastError.value = (e as Error).message || 'Unknown error'; } }

// Native APIs
async function analyzePath() { try { const p = pathInput.value; const [dirname, basename, extname, isAbsolute, normalized] = await Promise.all([window.krema.invoke<string>('path:dirname', { path: p }), window.krema.invoke<string>('path:basename', { path: p, ext: null }), window.krema.invoke<string>('path:extname', { path: p }), window.krema.invoke<boolean>('path:isAbsolute', { path: p }), window.krema.invoke<string>('path:normalize', { path: p })]); pathResults.value = [{ label: 'dirname', value: dirname }, { label: 'basename', value: basename }, { label: 'extname', value: extname }, { label: 'isAbsolute', value: String(isAbsolute) }, { label: 'normalized', value: normalized }]; lastError.value = ''; } catch (e: unknown) { lastError.value = (e as Error).message || 'Unknown error'; } }
async function joinPaths() { try { const r = await window.krema.invoke<string>('path:join', { paths: ['/Users', 'demo', 'documents', 'file.txt'] }); pathResults.value = [{ label: 'path:join result', value: r }]; lastError.value = ''; } catch (e: unknown) { lastError.value = (e as Error).message || 'Unknown error'; } }
async function resolvePath() { try { const r = await window.krema.invoke<string>('path:resolve', { paths: ['.', 'src', '..', 'dist', 'app.js'] }); pathResults.value = [{ label: 'path:resolve result', value: r }]; lastError.value = ''; } catch (e: unknown) { lastError.value = (e as Error).message || 'Unknown error'; } }
async function getAppDirectories() { try { const [appData, appConfig, appCache, appLog, home, temp] = await Promise.all([window.krema.invoke<string>('path:appData', {}), window.krema.invoke<string>('path:appConfig', {}), window.krema.invoke<string>('path:appCache', {}), window.krema.invoke<string>('path:appLog', {}), window.krema.invoke<string>('path:home', {}), window.krema.invoke<string>('path:temp', {})]); appDirs.value = [{ label: 'App Data', value: appData }, { label: 'App Config', value: appConfig }, { label: 'App Cache', value: appCache }, { label: 'App Log', value: appLog }, { label: 'Home', value: home }, { label: 'Temp', value: temp }]; lastError.value = ''; } catch (e: unknown) { lastError.value = (e as Error).message || 'Unknown error'; } }
async function showInFinder() { try { await window.krema.invoke('shell:showItemInFolder', { path: currentPath.value || paths.value?.home || '' }); lastError.value = ''; } catch (e: unknown) { lastError.value = (e as Error).message || 'Unknown error'; } }
async function openWithApp() { try { await window.krema.invoke('shell:openWith', { path: 'https://github.com', app: 'Safari' }); lastError.value = ''; } catch (e: unknown) { lastError.value = (e as Error).message || 'Unknown error'; } }

// Dialogs
async function openFileDialog() { try { const r = await window.krema.invoke<string | null>('dialog:openFile', { title: 'Select a file', filters: [{ name: 'Text Files', extensions: ['txt', 'md'] }, { name: 'All Files', extensions: ['*'] }] }); dialogResult.value = r ? `Selected: ${r}` : 'Cancelled'; lastError.value = ''; } catch (e: unknown) { lastError.value = (e as Error).message || 'Unknown error'; } }
async function saveFileDialog() { try { const r = await window.krema.invoke<string | null>('dialog:saveFile', { title: 'Save file as', defaultPath: 'untitled.txt' }); dialogResult.value = r ? `Save to: ${r}` : 'Cancelled'; lastError.value = ''; } catch (e: unknown) { lastError.value = (e as Error).message || 'Unknown error'; } }
async function selectFolderDialog() { try { const r = await window.krema.invoke<string | null>('dialog:selectFolder', { title: 'Select a folder' }); dialogResult.value = r ? `Folder: ${r}` : 'Cancelled'; lastError.value = ''; } catch (e: unknown) { lastError.value = (e as Error).message || 'Unknown error'; } }
async function confirmDialog() { try { const r = await window.krema.invoke<boolean>('dialog:confirm', { title: 'Confirm Action', message: 'Are you sure you want to proceed?' }); dialogResult.value = r ? 'Confirmed!' : 'Cancelled'; lastError.value = ''; } catch (e: unknown) { lastError.value = (e as Error).message || 'Unknown error'; } }
async function promptDialog() { try { const r = await window.krema.invoke<string | null>('dialog:prompt', { title: 'Enter Your Name', message: 'What is your name?', defaultValue: 'World' }); dialogResult.value = r ? `Hello, ${r}!` : 'Cancelled'; lastError.value = ''; } catch (e: unknown) { lastError.value = (e as Error).message || 'Unknown error'; } }
async function showBuiltinNotification() { try { await window.krema.invoke('notification:show', { title: 'Built-in Notification', body: 'This uses the Krema built-in notification API!', options: { sound: 'default' } }); lastError.value = ''; } catch (e: unknown) { lastError.value = (e as Error).message || 'Unknown error'; } }

// HTTP
async function httpRequestCmd() { httpLoading.value = true; try { const opts: Record<string, unknown> = {}; if (httpMethod.value !== 'GET' && httpMethod.value !== 'HEAD' && httpBody.value) { try { opts['body'] = JSON.parse(httpBody.value); } catch { opts['body'] = httpBody.value; } } opts['headers'] = { 'Accept': 'application/json' }; httpResult.value = await window.krema.invoke('http:request', { method: httpMethod.value, url: httpUrl.value, options: opts }); lastError.value = ''; } catch (e: unknown) { lastError.value = (e as Error).message || 'Unknown error'; httpResult.value = null; } finally { httpLoading.value = false; } }
async function httpFetch() { httpLoading.value = true; try { httpResult.value = await window.krema.invoke('http:fetch', { url: httpUrl.value }); lastError.value = ''; } catch (e: unknown) { lastError.value = (e as Error).message || 'Unknown error'; httpResult.value = null; } finally { httpLoading.value = false; } }
async function httpFetchJson() { httpLoading.value = true; try { httpResult.value = await window.krema.invoke('http:fetchJson', { url: httpUrl.value }); lastError.value = ''; } catch (e: unknown) { lastError.value = (e as Error).message || 'Unknown error'; httpResult.value = null; } finally { httpLoading.value = false; } }

// Screen
async function getAllScreens() { try { screens.value = await window.krema.invoke<ScreenInfo[]>('screen:getAll', {}); lastError.value = ''; } catch (e: unknown) { lastError.value = (e as Error).message || 'Unknown error'; } }
async function getPrimaryScreen() { try { primaryScreen.value = await window.krema.invoke<ScreenInfo>('screen:getPrimary', {}); lastError.value = ''; } catch (e: unknown) { lastError.value = (e as Error).message || 'Unknown error'; } }
async function getCursorPosition() { try { cursorPosition.value = await window.krema.invoke<CursorPosition>('screen:getCursorPosition', {}); lastError.value = ''; } catch (e: unknown) { lastError.value = (e as Error).message || 'Unknown error'; } }
async function getScreenAtCursor() { try { screenAtCursor.value = await window.krema.invoke<ScreenInfo>('screen:getScreenAtCursor', {}); lastError.value = ''; } catch (e: unknown) { lastError.value = (e as Error).message || 'Unknown error'; } }
function toggleCursorTracking() { if (trackingCursor.value) { trackingCursor.value = false; if (cursorInterval) { clearInterval(cursorInterval); cursorInterval = null; } } else { trackingCursor.value = true; cursorInterval = setInterval(() => { getCursorPosition(); }, 100); } }

// Drag & Drop
async function configureDragDrop() { try { const exts = acceptedExtensions.value ? acceptedExtensions.value.split(',').map(e => e.trim()).filter(e => e) : null; await window.krema.invoke('dragdrop:configure', { enabled: dropEnabled.value, acceptedExtensions: exts, dropZoneSelector: dropZoneSelector.value || null }); lastError.value = ''; } catch (e: unknown) { lastError.value = (e as Error).message || 'Unknown error'; } }
async function enableDragDrop() { try { await window.krema.invoke('dragdrop:enable', {}); dropEnabled.value = true; lastError.value = ''; } catch (e: unknown) { lastError.value = (e as Error).message || 'Unknown error'; } }
async function disableDragDrop() { try { await window.krema.invoke('dragdrop:disable', {}); dropEnabled.value = false; lastError.value = ''; } catch (e: unknown) { lastError.value = (e as Error).message || 'Unknown error'; } }

// Menus
async function setApplicationMenu() { try { const menu: MenuItem[] = [{ label: 'App', submenu: [{ id: 'about', label: 'About Krema Vue' }, { type: 'separator' }, { id: 'preferences', label: 'Preferences...', accelerator: 'Cmd+,' }, { type: 'separator' }, { role: 'quit' }] }, { label: 'Edit', submenu: [{ role: 'undo' }, { role: 'redo' }, { type: 'separator' }, { role: 'cut' }, { role: 'copy' }, { role: 'paste' }, { role: 'select-all' }] }, { label: 'View', submenu: [{ id: 'reload', label: 'Reload', accelerator: 'Cmd+R' }, { type: 'separator' }, { id: 'zoom-in', label: 'Zoom In', accelerator: 'Cmd+Plus' }, { id: 'zoom-out', label: 'Zoom Out', accelerator: 'Cmd+Minus' }, { id: 'zoom-reset', label: 'Reset Zoom', accelerator: 'Cmd+0' }, { type: 'separator' }, { id: 'fullscreen', label: 'Toggle Full Screen', accelerator: 'Ctrl+Cmd+F' }] }, { label: 'Window', submenu: [{ role: 'minimize' }, { role: 'close' }] }, { label: 'Help', submenu: [{ id: 'docs', label: 'Documentation' }, { id: 'github', label: 'GitHub Repository' }, { type: 'separator' }, { id: 'report-issue', label: 'Report Issue...' }] }]; await window.krema.invoke('menu:setApplicationMenu', { menu }); menuConfigured.value = true; lastError.value = ''; } catch (e: unknown) { lastError.value = (e as Error).message || 'Unknown error'; } }
async function showContextMenu() { try { const items: MenuItem[] = [{ id: 'ctx-copy', label: 'Copy' }, { id: 'ctx-paste', label: 'Paste' }, { type: 'separator' }, { id: 'ctx-refresh', label: 'Refresh' }, { type: 'separator' }, { label: 'More Options', submenu: [{ id: 'ctx-option1', label: 'Option 1' }, { id: 'ctx-option2', label: 'Option 2' }, { id: 'ctx-option3', label: 'Option 3', type: 'checkbox', checked: true }] }]; await window.krema.invoke('menu:showContextMenu', { items }); contextMenuResult.value = 'Context menu shown'; lastError.value = ''; } catch (e: unknown) { lastError.value = (e as Error).message || 'Unknown error'; } }

// Window
async function getWindowState() { try { windowState.value = await window.krema.invoke<WindowState>('window:getState'); lastError.value = ''; } catch (e: unknown) { lastError.value = (e as Error).message || 'Unknown error'; } }
async function getWindowTitle() { try { windowTitle.value = await window.krema.invoke<string>('window:getTitle'); lastError.value = ''; } catch (e: unknown) { lastError.value = (e as Error).message || 'Unknown error'; } }
async function windowAction(cmd: string, args?: Record<string, unknown>) { try { await window.krema.invoke(cmd, args); lastError.value = ''; } catch (e: unknown) { lastError.value = (e as Error).message || 'Unknown error'; } }
async function moveTo(position: string) { try { await window.krema.invoke('positioner:moveTo', { position }); positionerResult.value = `Moved to ${position}`; lastError.value = ''; } catch (e: unknown) { lastError.value = (e as Error).message || 'Unknown error'; } }
async function listWindows() { try { const r = await window.krema.invoke<{ windows: string[]; count: number }>('window:list'); windowList.value = r.windows; lastError.value = ''; } catch (e: unknown) { lastError.value = (e as Error).message || 'Unknown error'; } }
async function createChildWindow() { try { const r = await window.krema.invoke<{ label: string }>('window:create', { title: childWindowTitle.value, width: 600, height: 400, url: childWindowUrl.value || undefined }); childWindowLabel.value = r.label; await listWindows(); lastError.value = ''; } catch (e: unknown) { lastError.value = (e as Error).message || 'Unknown error'; } }
async function showModal() { try { const r = await window.krema.invoke<{ label: string }>('window:showModal', { title: 'Modal Window', width: 400, height: 300, parent: 'main' }); childWindowLabel.value = r.label; await listWindows(); lastError.value = ''; } catch (e: unknown) { lastError.value = (e as Error).message || 'Unknown error'; } }
async function closeChildWindow() { if (!childWindowLabel.value) return; try { await window.krema.invoke('window:close', { label: childWindowLabel.value }); childWindowLabel.value = ''; await listWindows(); lastError.value = ''; } catch (e: unknown) { lastError.value = (e as Error).message || 'Unknown error'; } }
async function sendToWindow() { if (!childWindowLabel.value) return; try { let payload: unknown; try { payload = JSON.parse(windowMessagePayload.value); } catch { payload = windowMessagePayload.value; } await window.krema.invoke('window:sendTo', { label: childWindowLabel.value, event: windowMessageEvent.value || 'custom-message', payload }); lastError.value = ''; } catch (e: unknown) { lastError.value = (e as Error).message || 'Unknown error'; } }
async function broadcastToWindows() { try { let payload: unknown; try { payload = JSON.parse(windowMessagePayload.value); } catch { payload = windowMessagePayload.value; } await window.krema.invoke('window:broadcast', { event: windowMessageEvent.value || 'broadcast-message', payload }); lastError.value = ''; } catch (e: unknown) { lastError.value = (e as Error).message || 'Unknown error'; } }

// Plugins
async function getOsInfo() { try { osInfo.value = await window.krema.invoke<Record<string, unknown>>('os:info'); lastError.value = ''; } catch (e: unknown) { lastError.value = (e as Error).message || 'Unknown error'; } }
async function getOsMemory() { try { osMemory.value = await window.krema.invoke<Record<string, number>>('os:memory'); lastError.value = ''; } catch (e: unknown) { lastError.value = (e as Error).message || 'Unknown error'; } }
async function getOsLocale() { try { osLocale.value = await window.krema.invoke<Record<string, string>>('os:locale'); lastError.value = ''; } catch (e: unknown) { lastError.value = (e as Error).message || 'Unknown error'; } }
async function storeSetCmd() { try { await window.krema.invoke('store:set', { key: storeKey.value, value: storeValue.value }); storeResult.value = `Saved: ${storeKey.value} = ${storeValue.value}`; lastError.value = ''; } catch (e: unknown) { lastError.value = (e as Error).message || 'Unknown error'; } }
async function storeGetCmd() { try { storeResult.value = await window.krema.invoke('store:get', { key: storeKey.value, default: null }); lastError.value = ''; } catch (e: unknown) { lastError.value = (e as Error).message || 'Unknown error'; } }
async function storeDeleteCmd() { try { const d = await window.krema.invoke<boolean>('store:delete', { key: storeKey.value }); storeResult.value = d ? `Deleted: ${storeKey.value}` : `Key not found: ${storeKey.value}`; lastError.value = ''; } catch (e: unknown) { lastError.value = (e as Error).message || 'Unknown error'; } }
async function storeGetKeysCmd() { try { storeKeys.value = await window.krema.invoke<string[]>('store:keys', {}); lastError.value = ''; } catch (e: unknown) { lastError.value = (e as Error).message || 'Unknown error'; } }
async function storeGetEntriesCmd() { try { storeEntries.value = await window.krema.invoke<Record<string, unknown>>('store:entries', {}); lastError.value = ''; } catch (e: unknown) { lastError.value = (e as Error).message || 'Unknown error'; } }
async function storeClearCmd() { try { await window.krema.invoke('store:clear', {}); storeResult.value = 'Store cleared'; storeKeys.value = []; storeEntries.value = {}; lastError.value = ''; } catch (e: unknown) { lastError.value = (e as Error).message || 'Unknown error'; } }
async function checkIsPrimary() { try { isPrimaryInstance.value = await window.krema.invoke<boolean>('instance:isPrimary'); lastError.value = ''; } catch (e: unknown) { lastError.value = (e as Error).message || 'Unknown error'; } }
async function requestInstanceLock() { try { isPrimaryInstance.value = await window.krema.invoke<boolean>('instance:requestLock'); lastError.value = ''; } catch (e: unknown) { lastError.value = (e as Error).message || 'Unknown error'; } }
async function getRegisteredShortcuts() { try { registeredShortcuts.value = await window.krema.invoke<string[]>('shortcut:getAll'); lastError.value = ''; } catch (e: unknown) { lastError.value = (e as Error).message || 'Unknown error'; } }
async function registerShortcut() { try { const ok = await window.krema.invoke<boolean>('shortcut:register', { accelerator: shortcutAccelerator.value }); if (ok) await getRegisteredShortcuts(); lastError.value = ok ? '' : 'Failed to register shortcut'; } catch (e: unknown) { lastError.value = (e as Error).message || 'Unknown error'; } }
async function unregisterShortcut() { try { await window.krema.invoke('shortcut:unregister', { accelerator: shortcutAccelerator.value }); await getRegisteredShortcuts(); lastError.value = ''; } catch (e: unknown) { lastError.value = (e as Error).message || 'Unknown error'; } }
async function unregisterAllShortcuts() { try { await window.krema.invoke('shortcut:unregisterAll'); registeredShortcuts.value = []; lastError.value = ''; } catch (e: unknown) { lastError.value = (e as Error).message || 'Unknown error'; } }
async function checkDockSupported() { try { dockSupported.value = await window.krema.invoke<boolean>('dock:isSupported'); lastError.value = ''; } catch (e: unknown) { lastError.value = (e as Error).message || 'Unknown error'; } }
async function setDockBadgeCmd() { try { await window.krema.invoke('dock:setBadge', { text: dockBadge.value }); currentDockBadge.value = dockBadge.value; lastError.value = ''; } catch (e: unknown) { lastError.value = (e as Error).message || 'Unknown error'; } }
async function clearDockBadge() { try { await window.krema.invoke('dock:clearBadge'); currentDockBadge.value = ''; lastError.value = ''; } catch (e: unknown) { lastError.value = (e as Error).message || 'Unknown error'; } }
async function getDockBadge() { try { currentDockBadge.value = await window.krema.invoke<string>('dock:getBadge'); lastError.value = ''; } catch (e: unknown) { lastError.value = (e as Error).message || 'Unknown error'; } }
async function bounceDock(critical: boolean) { try { await window.krema.invoke('dock:bounce', { critical }); lastError.value = ''; } catch (e: unknown) { lastError.value = (e as Error).message || 'Unknown error'; } }

// Files
async function getPaths() { try { const p = await window.krema.invoke<PathInfo>('getPaths', {}); paths.value = p; pathItems.value = [{ label: 'Home', path: p.home }, { label: 'Desktop', path: p.desktop }, { label: 'Documents', path: p.documents }, { label: 'Downloads', path: p.downloads }, { label: 'Current', path: p.current }]; currentPath.value = p.home; lastError.value = ''; } catch (e: unknown) { lastError.value = (e as Error).message || 'Unknown error'; } }
async function listDirectory(path?: string) { let dir = path || currentPath.value; if (!dir) { if (!paths.value) await getPaths(); dir = currentPath.value || paths.value?.home || ''; if (!dir) return; } try { currentPath.value = dir; const f = await window.krema.invoke<FileInfo[]>('listDirectory', { path: dir }); f.sort((a, b) => { if (a.isDirectory !== b.isDirectory) return a.isDirectory ? -1 : 1; return a.name.localeCompare(b.name); }); files.value = f; selectedFile.value = null; fileContent.value = ''; lastError.value = ''; } catch (e: unknown) { lastError.value = (e as Error).message || 'Unknown error'; } }
async function readFile(path: string) { try { let c = await window.krema.invoke<string>('readTextFile', { path }); if (c.length > 10000) c = c.substring(0, 10000) + '\n... (truncated)'; fileContent.value = c; lastError.value = ''; } catch (e: unknown) { lastError.value = (e as Error).message || 'Unknown error'; } }
function selectFileItem(file: FileInfo) { selectedFile.value = file; fileContent.value = ''; }

// FS Plugin
async function fsCheckExists() { try { fsExistsResult.value = await window.krema.invoke<boolean>('fs:exists', { path: fsPath.value }); fsPluginError.value = ''; } catch (e: unknown) { fsPluginError.value = (e as Error).message || 'Unknown error'; } }
async function fsGetStat() { try { fsStat.value = await window.krema.invoke<FsFileInfo>('fs:stat', { path: fsPath.value }); fsPluginError.value = ''; } catch (e: unknown) { fsStat.value = null; fsPluginError.value = (e as Error).message || 'Unknown error'; } }
async function fsListDir(path?: string) { const dir = path || fsPath.value; try { fsCurrentDir.value = dir; const f = await window.krema.invoke<FsFileInfo[]>('fs:readDir', { path: dir }); f.sort((a, b) => { if (a.isDirectory !== b.isDirectory) return a.isDirectory ? -1 : 1; return a.name.localeCompare(b.name); }); fsFiles.value = f; fsPluginError.value = ''; } catch (e: unknown) { fsFiles.value = []; fsPluginError.value = (e as Error).message || 'Unknown error'; } }
async function fsReadFile() { try { let c = await window.krema.invoke<string>('fs:readTextFile', { path: fsPath.value }); if (c.length > 5000) c = c.substring(0, 5000) + '\n... (truncated)'; fsReadResult.value = c; fsPluginError.value = ''; } catch (e: unknown) { fsReadResult.value = ''; fsPluginError.value = (e as Error).message || 'Unknown error'; } }
async function fsWriteFile() { try { await window.krema.invoke<boolean>('fs:writeTextFile', { path: fsWritePath.value, content: fsWriteContent.value }); fsPluginError.value = ''; fsReadResult.value = `Written to ${fsWritePath.value}`; } catch (e: unknown) { fsPluginError.value = (e as Error).message || 'Unknown error'; } }
async function fsWriteAndReadBack() { try { await window.krema.invoke<boolean>('fs:writeTextFile', { path: fsWritePath.value, content: fsWriteContent.value }); const exists = await window.krema.invoke<boolean>('fs:exists', { path: fsWritePath.value }); const content = await window.krema.invoke<string>('fs:readTextFile', { path: fsWritePath.value }); const stat = await window.krema.invoke<FsFileInfo>('fs:stat', { path: fsWritePath.value }); fsReadResult.value = `Exists: ${exists}\nSize: ${stat.size} bytes\nContent: ${content}`; fsPluginError.value = ''; } catch (e: unknown) { fsPluginError.value = (e as Error).message || 'Unknown error'; } }
function fsNavigateDir(file: FsFileInfo) { if (file.isDirectory) { fsPath.value = file.path; fsListDir(file.path); } else { fsPath.value = file.path; } }

// Secure Storage
async function ssSetCmd() { try { const r = await window.krema.invoke<boolean>('secureStorage:set', { key: ssKey.value, value: ssValue.value }); ssResult.value = r ? `Stored "${ssKey.value}" successfully` : 'Failed to store'; lastError.value = ''; } catch (e: unknown) { lastError.value = (e as Error).message || 'Unknown error'; } }
async function ssGetCmd() { try { const v = await window.krema.invoke<string | null>('secureStorage:get', { key: ssKey.value }); ssResult.value = v !== null ? `Value: "${v}"` : `Key "${ssKey.value}" not found`; lastError.value = ''; } catch (e: unknown) { lastError.value = (e as Error).message || 'Unknown error'; } }
async function ssDeleteCmd() { try { const r = await window.krema.invoke<boolean>('secureStorage:delete', { key: ssKey.value }); ssResult.value = r ? `Deleted "${ssKey.value}"` : `Key "${ssKey.value}" not found`; lastError.value = ''; } catch (e: unknown) { lastError.value = (e as Error).message || 'Unknown error'; } }
async function ssHasCmd() { try { const r = await window.krema.invoke<boolean>('secureStorage:has', { key: ssKey.value }); ssResult.value = r ? `Key "${ssKey.value}" exists` : `Key "${ssKey.value}" not found`; lastError.value = ''; } catch (e: unknown) { lastError.value = (e as Error).message || 'Unknown error'; } }

// Logging
async function sendLog() { try { await window.krema.invoke(`log:${logLevel.value}`, { message: logMessage.value }); logHistory.value.unshift({ level: logLevel.value, message: logMessage.value, timestamp: new Date() }); if (logHistory.value.length > 20) logHistory.value.pop(); lastError.value = ''; } catch (e: unknown) { lastError.value = (e as Error).message || 'Unknown error'; } }
async function sendAllLevels() { try { const levels = ['trace', 'debug', 'info', 'warn', 'error']; for (const level of levels) { const msg = `[${level.toUpperCase()}] ${logMessage.value}`; await window.krema.invoke(`log:${level}`, { message: msg }); logHistory.value.unshift({ level, message: msg, timestamp: new Date() }); } if (logHistory.value.length > 20) logHistory.value.splice(20); lastError.value = ''; } catch (e: unknown) { lastError.value = (e as Error).message || 'Unknown error'; } }
async function getLogFilePath() { try { logFilePath.value = await window.krema.invoke<string>('path:appLog', {}); lastError.value = ''; } catch (e: unknown) { lastError.value = (e as Error).message || 'Unknown error'; } }
async function revealLogFile() { let p = logFilePath.value; if (!p) { try { p = await window.krema.invoke<string>('path:appLog', {}); logFilePath.value = p; } catch { return; } } try { await window.krema.invoke('shell:showItemInFolder', { path: p }); lastError.value = ''; } catch (e: unknown) { lastError.value = (e as Error).message || 'Unknown error'; } }

const positions = ['TOP_LEFT','TOP_CENTER','TOP_RIGHT','LEFT_CENTER','CENTER','RIGHT_CENTER','BOTTOM_LEFT','BOTTOM_CENTER','BOTTOM_RIGHT'];
function posLabel(pos: string) { return pos.replace(/_/g, ' ').split(' ').map(w => w[0] + w.slice(1).toLowerCase()).join(' '); }
</script>

<template>
  <div class="app">
    <header>
      <h1>Krema Vue Demo</h1>
      <p class="subtitle">Desktop app powered by Vue + Java via Krema</p>
    </header>

    <nav class="tabs">
      <button v-for="tab in tabsList" :key="tab.id" :class="{ active: activeTab === tab.id }" @click="activeTab = tab.id">{{ tab.label }}</button>
    </nav>

    <main>
      <!-- IPC -->
      <template v-if="activeTab === 'ipc'">
        <div class="card"><h2>Greeting Command</h2><p class="description">Call Java method with string parameter, receive string response</p><div class="input-group"><input v-model="name" placeholder="Enter your name" @keyup.enter="greet()" /><button @click="greet()">Greet</button></div><div v-if="greeting" class="result">{{ greeting }}</div></div>
        <div class="card"><h2>Calculator</h2><p class="description">Pass multiple typed parameters (double, String) to Java</p><div class="input-group"><input type="number" class="small" v-model.number="num1" /><select v-model="operation"><option value="add">+</option><option value="subtract">-</option><option value="multiply">&times;</option><option value="divide">&divide;</option></select><input type="number" class="small" v-model.number="num2" /><button @click="calculate()">Calculate</button></div><div v-if="calcResult" class="result">{{ calcResult }}</div></div>
      </template>

      <!-- Events -->
      <template v-if="activeTab === 'events'">
        <div class="card"><h2>Backend Events (Java &rarr; JavaScript)</h2><p class="description">Java can push events to the frontend at any time</p><div class="button-group"><button @click="startTimer()" :disabled="timerRunning">Start Timer</button><button @click="stopTimer()" :disabled="!timerRunning">Stop Timer</button></div><div v-if="timerCount > 0" class="timer-display"><span class="count">{{ timerCount }}</span><span class="label">seconds</span></div><pre v-if="lastEvent" class="code-block">Last event: {{ JSON.stringify(lastEvent, null, 2) }}</pre></div>
        <div class="card"><h2>App Lifecycle Events</h2><p class="description">Receive app:ready, app:before-quit events from the backend</p><div v-if="lifecycleEvents.length > 0" class="info-grid"><div v-for="(evt, i) in lifecycleEvents" :key="i" class="info-item"><span class="label">{{ evt.event }}</span><span class="value">{{ fmtTime(evt.timestamp) }}</span></div></div><div v-if="appReady" class="result">App Ready!</div><p v-if="lifecycleEvents.length === 0" style="color: #888; font-size: 0.85rem;">Lifecycle events will appear here when emitted by the backend</p></div>
      </template>

      <!-- Native APIs -->
      <template v-if="activeTab === 'native'">
        <div class="card"><h2>Path Utilities</h2><p class="description">Built-in path manipulation APIs (path:dirname, path:basename, etc.)</p><div class="input-group"><input v-model="pathInput" placeholder="Enter a file path" /><button @click="analyzePath()">Analyze Path</button></div><div class="button-group"><button @click="joinPaths()">Join Paths</button><button @click="resolvePath()">Resolve Path</button></div><div v-if="pathResults.length > 0" class="info-grid"><div v-for="(item, i) in pathResults" :key="i" class="info-item"><span class="label">{{ item.label }}</span><span class="value">{{ item.value }}</span></div></div></div>
        <div class="card"><h2>App Directories</h2><p class="description">Platform-specific app data, config, cache, and log directories</p><div class="button-group"><button @click="getAppDirectories()">Get App Directories</button></div><div v-if="appDirs.length > 0" class="info-grid"><div v-for="(dir, i) in appDirs" :key="i" class="info-item"><span class="label">{{ dir.label }}</span><span class="value clickable" @click="listDirectory(dir.value)">{{ dir.value }}</span></div></div></div>
        <div class="card"><h2>Shell Integration</h2><p class="description">shell:showItemInFolder and shell:openWith</p><div class="button-group"><button @click="showInFinder()">Reveal in Finder</button><button @click="openWithApp()">Open URL with Safari</button></div></div>
        <div class="card"><h2>HTTP Client (Backend)</h2><p class="description">Make HTTP requests from Java backend - bypasses CORS restrictions</p><div class="input-group"><input v-model="httpUrl" placeholder="URL" style="flex:1" /><select v-model="httpMethod" style="width:100px"><option value="GET">GET</option><option value="POST">POST</option><option value="PUT">PUT</option><option value="DELETE">DELETE</option><option value="PATCH">PATCH</option></select></div><div v-if="httpMethod !== 'GET' && httpMethod !== 'HEAD'" class="input-group"><input v-model="httpBody" placeholder="Request body (JSON)" style="flex:1" /></div><div class="button-group"><button @click="httpRequestCmd()" :disabled="httpLoading">Send Request</button><button @click="httpFetch()" :disabled="httpLoading">Simple Fetch</button><button @click="httpFetchJson()" :disabled="httpLoading">Fetch JSON</button></div><div v-if="httpLoading" class="status-line"><span class="status-indicator pending"></span>Loading...</div><pre v-if="httpResult" class="code-block">{{ JSON.stringify(httpResult, null, 2) }}</pre></div>
        <div class="card"><h2>Native Dialogs</h2><p class="description">Built-in file dialogs, confirmation, and prompts</p><div class="button-group"><button @click="openFileDialog()">Open File</button><button @click="saveFileDialog()">Save File</button><button @click="selectFolderDialog()">Select Folder</button></div><div class="button-group"><button @click="confirmDialog()">Confirm Dialog</button><button @click="promptDialog()">Prompt Dialog</button><button @click="showBuiltinNotification()">Notification</button></div><div v-if="dialogResult" class="result">{{ dialogResult }}</div></div>
      </template>

      <!-- Screen -->
      <template v-if="activeTab === 'screen'">
        <div class="card"><h2>Display Information</h2><p class="description">Get information about connected displays via NSScreen</p><div class="button-group"><button @click="getAllScreens()">Get All Screens</button><button @click="getPrimaryScreen()">Get Primary Screen</button></div><div v-if="screens.length > 0" class="screen-list"><div v-for="(s, i) in screens" :key="i" class="screen-card"><div class="screen-header"><span class="screen-name">{{ s.name || `Display ${i + 1}` }}</span><span v-if="s.isPrimary" class="screen-badge">Primary</span></div><div class="screen-details"><div class="detail-row"><span class="label">Resolution</span><span class="value">{{ s.frame.width }} &times; {{ s.frame.height }}</span></div><div class="detail-row"><span class="label">Position</span><span class="value">({{ s.frame.x }}, {{ s.frame.y }})</span></div><div class="detail-row"><span class="label">Visible Area</span><span class="value">{{ s.visibleFrame.width }} &times; {{ s.visibleFrame.height }}</span></div><div class="detail-row"><span class="label">Scale Factor</span><span class="value">{{ s.scaleFactor }}x</span></div><div class="detail-row"><span class="label">Refresh Rate</span><span class="value">{{ s.refreshRate }} Hz</span></div></div></div></div><pre v-if="primaryScreen && screens.length === 0" class="code-block">{{ JSON.stringify(primaryScreen, null, 2) }}</pre></div>
        <div class="card"><h2>Cursor Position</h2><p class="description">Track cursor position in screen coordinates</p><div class="button-group"><button @click="getCursorPosition()">Get Cursor Position</button><button @click="getScreenAtCursor()">Get Screen at Cursor</button><button @click="toggleCursorTracking()" :class="{ active: trackingCursor }">{{ trackingCursor ? 'Stop Tracking' : 'Start Tracking' }}</button></div><div v-if="cursorPosition" class="cursor-display"><div class="coord"><span class="label">X</span><span class="value">{{ Math.round(cursorPosition.x) }}</span></div><div class="coord"><span class="label">Y</span><span class="value">{{ Math.round(cursorPosition.y) }}</span></div></div><div v-if="screenAtCursor" class="result">Cursor is on: <strong>{{ screenAtCursor.name || 'Display' }}</strong> ({{ screenAtCursor.frame.width }} &times; {{ screenAtCursor.frame.height }})</div></div>
      </template>

      <!-- Drag & Drop -->
      <template v-if="activeTab === 'dragdrop'">
        <div class="card"><h2>Drag &amp; Drop Configuration</h2><p class="description">Configure drag-drop behavior via HTML5 Drag API</p><div class="input-group"><input v-model="acceptedExtensions" placeholder="Filter extensions (e.g., txt,pdf,png)" /></div><div class="input-group"><input v-model="dropZoneSelector" placeholder="Drop zone CSS selector (optional)" /></div><div class="button-group"><button @click="configureDragDrop()">Apply Configuration</button><button @click="enableDragDrop()" :disabled="dropEnabled">Enable</button><button @click="disableDragDrop()" :disabled="!dropEnabled">Disable</button></div><div class="status-line"><span :class="['status-indicator', dropEnabled ? 'ready' : 'pending']"></span>Drag &amp; Drop is {{ dropEnabled ? 'enabled' : 'disabled' }}</div></div>
        <div class="card"><h2>Drop Zone</h2><p class="description">Drag files here to test the file-drop event</p><div :class="['drop-zone', { hovering: dropHovering, 'has-files': droppedFiles.length > 0 }]"><div v-if="droppedFiles.length === 0 && !dropHovering" class="drop-zone-content"><span class="drop-icon">&#x1F4C2;</span><p>Drag files here</p></div><div v-if="dropHovering" class="drop-zone-content"><span class="drop-icon">&#x2B07;&#xFE0F;</span><p>Release to drop</p></div><div v-if="droppedFiles.length > 0" class="dropped-files"><div v-for="(file, i) in droppedFiles" :key="i" class="dropped-file"><span class="file-icon">&#x1F4C4;</span><div class="file-info"><span class="file-name">{{ file.name }}</span><span class="file-meta">{{ file.type || 'unknown' }} &bull; {{ formatSize(file.size) }}</span></div></div></div></div><div v-if="droppedFiles.length > 0" class="button-group"><button @click="droppedFiles = []; lastDropEvent = null">Clear Files</button></div><pre v-if="lastDropEvent" class="code-block">Last event: {{ JSON.stringify(lastDropEvent, null, 2) }}</pre></div>
      </template>

      <!-- Menus -->
      <template v-if="activeTab === 'menus'">
        <div class="card"><h2>Application Menu</h2><p class="description">Set the native application menu bar (NSMenu)</p><div class="button-group"><button @click="setApplicationMenu()">Set Application Menu</button></div><div v-if="menuConfigured" class="result"><span class="status-indicator ready"></span>Application menu configured! Check the menu bar.</div><p v-if="!menuConfigured" class="hint">Click the button to configure a custom application menu with App, Edit, View, Window, and Help menus.</p></div>
        <div class="card"><h2>Context Menu</h2><p class="description">Show a native right-click context menu</p><div class="button-group"><button @click="showContextMenu()">Show Context Menu</button></div><div v-if="contextMenuResult" class="result">{{ contextMenuResult }}</div></div>
        <div class="card"><h2>Menu Click Events</h2><p class="description">Receive menu:click events when menu items are selected</p><div class="button-group"><button @click="menuClickEvents = []" :disabled="menuClickEvents.length === 0">Clear Events</button></div><div v-if="menuClickEvents.length > 0" class="event-log"><div v-for="(evt, i) in menuClickEvents" :key="i" class="event-item"><span class="event-id">{{ evt.id }}</span><span class="event-time">{{ fmtTime(evt.timestamp) }}</span></div></div><p v-if="menuClickEvents.length === 0" class="hint">Menu click events will appear here when you select menu items.</p></div>
      </template>

      <!-- Window -->
      <template v-if="activeTab === 'window'">
        <div class="card"><h2>Window State</h2><p class="description">Query the current window state</p><div class="button-group"><button @click="getWindowState()">Get State</button><button @click="getWindowTitle()">Get Title</button></div><div v-if="windowState" class="info-grid"><div class="info-item"><span class="label">Position</span><span class="value">{{ windowState.x }}, {{ windowState.y }}</span></div><div class="info-item"><span class="label">Size</span><span class="value">{{ windowState.width }} x {{ windowState.height }}</span></div><div class="info-item"><span class="label">Minimized</span><span class="value">{{ windowState.minimized }}</span></div><div class="info-item"><span class="label">Maximized</span><span class="value">{{ windowState.maximized }}</span></div><div class="info-item"><span class="label">Fullscreen</span><span class="value">{{ windowState.fullscreen }}</span></div><div class="info-item"><span class="label">Focused</span><span class="value">{{ windowState.focused }}</span></div></div><div v-if="windowTitle" class="result">Title: {{ windowTitle }}</div></div>
        <div class="card"><h2>Window Actions</h2><p class="description">Control window state</p><div class="button-group"><button @click="windowAction('window:minimize')">Minimize</button><button @click="windowAction('window:maximize')">Maximize</button><button @click="windowAction('window:restore')">Restore</button><button @click="windowAction('window:toggleFullscreen')">Toggle Fullscreen</button><button @click="windowAction('window:center')">Center</button></div></div>
        <div class="card"><h2>Positioner Plugin</h2><p class="description">Snap window to semantic screen positions via positioner:moveTo</p><div class="position-grid"><button v-for="pos in positions" :key="pos" @click="moveTo(pos)">{{ posLabel(pos) }}</button></div><div v-if="positionerResult" class="result">{{ positionerResult }}</div></div>
        <div class="card"><h2>Size &amp; Position</h2><p class="description">Resize and move the window</p><div class="form-group"><label>Size:</label><input type="number" v-model.number="newWidth" style="width:80px" /><span> x </span><input type="number" v-model.number="newHeight" style="width:80px" /><button @click="windowAction('window:setSize', { width: newWidth, height: newHeight })">Set Size</button></div><div class="form-group"><label>Position:</label><input type="number" v-model.number="newX" style="width:80px" /><span> , </span><input type="number" v-model.number="newY" style="width:80px" /><button @click="windowAction('window:setPosition', { x: newX, y: newY })">Set Position</button></div></div>
        <div class="card"><h2>Window Properties</h2><p class="description">Modify window properties</p><div class="form-group"><label>Title:</label><input v-model="windowTitle" placeholder="Window title" style="width:200px" /><button @click="windowAction('window:setTitle', { title: windowTitle })">Set Title</button></div><div class="form-group"><label>Opacity:</label><input type="range" v-model.number="windowOpacity" min="0.1" max="1" step="0.1" style="width:150px" /><span>{{ windowOpacity }}</span><button @click="windowAction('window:setOpacity', { opacity: windowOpacity })">Set Opacity</button></div><div class="button-group"><button @click="windowAction('window:setAlwaysOnTop', { alwaysOnTop: true })">Always On Top: ON</button><button @click="windowAction('window:setAlwaysOnTop', { alwaysOnTop: false })">Always On Top: OFF</button></div><div class="button-group" style="margin-top:8px"><button @click="windowAction('window:setResizable', { resizable: true })">Resizable: ON</button><button @click="windowAction('window:setResizable', { resizable: false })">Resizable: OFF</button></div></div>
        <div class="card"><h2>Frameless Window (macOS)</h2><p class="description">Custom title bar styling - hiddenInset for modern macOS look</p><div class="form-group"><label>Title Bar Style:</label><select v-model="titleBarStyle" style="width:150px"><option value="default">Default</option><option value="hidden">Hidden</option><option value="hiddenInset">Hidden Inset</option></select><button @click="windowAction('window:setTitleBarStyle', { style: titleBarStyle })">Apply</button></div><div class="form-group"><label>Traffic Lights Position:</label><input type="number" v-model.number="trafficLightX" style="width:60px" /><span>,</span><input type="number" v-model.number="trafficLightY" style="width:60px" /><button @click="windowAction('window:setTrafficLightPosition', { x: trafficLightX, y: trafficLightY })">Set Position</button></div><div class="button-group"><button @click="windowAction('window:setTitlebarTransparent', { transparent: true })">Titlebar Transparent</button><button @click="windowAction('window:setTitlebarTransparent', { transparent: false })">Titlebar Opaque</button></div><div class="button-group" style="margin-top:8px"><button @click="windowAction('window:setFullSizeContentView', { extend: true })">Full Size Content: ON</button><button @click="windowAction('window:setFullSizeContentView', { extend: false })">Full Size Content: OFF</button></div><p class="hint">Use "Hidden Inset" for Electron-style frameless window with inset traffic lights.</p></div>
        <div class="card"><h2>Multi-Window</h2><p class="description">Create and manage multiple windows</p><div class="form-group"><label>Title:</label><input v-model="childWindowTitle" style="width:150px" /></div><div class="form-group"><label>URL (optional):</label><input v-model="childWindowUrl" placeholder="https://..." style="width:200px" /></div><div class="button-group"><button @click="createChildWindow()">Create Window</button><button @click="showModal()">Show Modal</button><button @click="listWindows()">List Windows</button></div><div v-if="windowList.length > 0" class="info-grid"><div class="info-item"><span class="label">Open Windows</span><span class="value">{{ windowList.join(', ') }}</span></div></div><div v-if="childWindowLabel" class="result">Last created: <strong>{{ childWindowLabel }}</strong> <button @click="closeChildWindow()" style="margin-left:8px">Close</button></div></div>
        <div class="card"><h2>Inter-Window Messaging</h2><p class="description">Send messages between windows</p><div class="form-group"><label>Event:</label><input v-model="windowMessageEvent" placeholder="custom-message" style="width:150px" /></div><div class="form-group"><label>Payload:</label><input v-model="windowMessagePayload" placeholder='{"key": "value"}' style="width:250px" /></div><div class="button-group"><button @click="sendToWindow()" :disabled="!childWindowLabel">Send to Window</button><button @click="broadcastToWindows()">Broadcast to All</button></div><p v-if="!childWindowLabel" class="hint">Create a child window first to send messages to it.</p></div>
      </template>

      <!-- Plugins -->
      <template v-if="activeTab === 'plugins'">
        <div class="card"><h2>OS Information</h2><p class="description">Built-in os:info, os:memory, os:locale APIs</p><div class="button-group"><button @click="getOsInfo()">Get OS Info</button><button @click="getOsMemory()">Get Memory</button><button @click="getOsLocale()">Get Locale</button></div><div v-if="osInfo" class="info-grid"><div v-for="k in ['platform','arch','version','hostname','username','cpuCount']" :key="k" class="info-item"><span class="label">{{ k.charAt(0).toUpperCase() + k.slice(1).replace(/([A-Z])/g, ' $1') }}</span><span class="value">{{ String(osInfo[k]) }}</span></div></div><div v-if="osMemory" class="info-grid"><div v-for="k in ['total','used','free','max']" :key="k" class="info-item"><span class="label">{{ k.charAt(0).toUpperCase() + k.slice(1) }} Memory</span><span class="value">{{ formatBytes(osMemory[k]) }}</span></div></div><div v-if="osLocale" class="info-grid"><div v-for="k in ['language','country','displayName','tag']" :key="k" class="info-item"><span class="label">{{ k.charAt(0).toUpperCase() + k.slice(1).replace(/([A-Z])/g, ' $1') }}</span><span class="value">{{ osLocale[k] }}</span></div></div></div>
        <div class="card"><h2>Persistent Store</h2><p class="description">JSON-based key-value storage (store:get, store:set, etc.)</p><div class="form-group"><label>Key:</label><input v-model="storeKey" style="width:120px" /><label>Value:</label><input v-model="storeValue" style="width:150px" /></div><div class="button-group"><button @click="storeSetCmd()">Set</button><button @click="storeGetCmd()">Get</button><button @click="storeDeleteCmd()">Delete</button></div><div class="button-group" style="margin-top:8px"><button @click="storeGetKeysCmd()">Get Keys</button><button @click="storeGetEntriesCmd()">Get All Entries</button><button @click="storeClearCmd()">Clear Store</button></div><div v-if="storeResult !== null" class="result">Result: {{ JSON.stringify(storeResult) }}</div><div v-if="storeKeys.length > 0" class="info-grid"><div class="info-item"><span class="label">Keys</span><span class="value">{{ storeKeys.join(', ') || '(empty)' }}</span></div></div><pre v-if="Object.keys(storeEntries).length > 0" class="code-block">{{ JSON.stringify(storeEntries, null, 2) }}</pre></div>
        <div class="card"><h2>Single Instance</h2><p class="description">Ensure only one instance of the app is running</p><div class="button-group"><button @click="checkIsPrimary()">Check Primary</button><button @click="requestInstanceLock()">Request Lock</button></div><div class="status-line"><span :class="['status-indicator', isPrimaryInstance ? 'ready' : 'pending']"></span>{{ isPrimaryInstance ? 'This is the primary instance' : 'Instance status unknown' }}</div><p class="hint">When a second instance launches, it will emit an 'app:second-instance' event (check the Events tab for lifecycle events).</p></div>
        <div class="card"><h2>Global Shortcuts</h2><p class="description">Register system-wide keyboard shortcuts (limited mode on macOS)</p><div class="input-group"><input v-model="shortcutAccelerator" placeholder="e.g., Cmd+Shift+K" style="width:200px" /><button @click="registerShortcut()">Register</button><button @click="unregisterShortcut()">Unregister</button></div><div class="button-group"><button @click="getRegisteredShortcuts()">List Shortcuts</button><button @click="unregisterAllShortcuts()">Unregister All</button></div><div v-if="registeredShortcuts.length > 0" class="info-grid"><div class="info-item"><span class="label">Registered</span><span class="value">{{ registeredShortcuts.join(', ') }}</span></div></div><div v-if="shortcutTriggered" class="result">Last triggered: <strong>{{ shortcutTriggered }}</strong></div><p v-if="registeredShortcuts.length === 0" class="hint">Note: Global shortcuts require Accessibility permissions on macOS. Currently running in limited mode.</p></div>
        <div class="card"><h2>Dock Badge</h2><p class="description">Set badge text on the dock icon and bounce for attention</p><div class="input-group"><input v-model="dockBadge" placeholder="Badge text (e.g., 5)" style="width:120px" /><button @click="setDockBadgeCmd()">Set Badge</button><button @click="clearDockBadge()">Clear</button></div><div class="button-group"><button @click="getDockBadge()">Get Badge</button><button @click="bounceDock(false)">Bounce Once</button><button @click="bounceDock(true)">Bounce Critical</button></div><div v-if="currentDockBadge" class="result">Current badge: <strong>{{ currentDockBadge }}</strong></div><div class="button-group" style="margin-top:12px"><button @click="checkDockSupported()">Check Support</button></div><div class="status-line"><span :class="['status-indicator', dockSupported ? 'ready' : 'pending']"></span>Dock badge is {{ dockSupported ? 'supported' : 'not supported' }} on this platform</div></div>
      </template>

      <!-- System -->
      <template v-if="activeTab === 'system'">
        <div class="card"><h2>System Information</h2><p class="description">Access OS and JVM information from Java</p><div class="button-group"><button @click="getSystemInfo()">Get System Info</button><button @click="getEnvironmentInfo()">Get Environment</button></div><div v-if="systemInfo" class="info-grid"><div class="info-item"><span class="label">OS</span><span class="value">{{ systemInfo.osName }} {{ systemInfo.osVersion }}</span></div><div class="info-item"><span class="label">Architecture</span><span class="value">{{ systemInfo.osArch }}</span></div><div class="info-item"><span class="label">Java</span><span class="value">{{ systemInfo.javaVersion }} ({{ systemInfo.javaVendor }})</span></div><div class="info-item"><span class="label">Processors</span><span class="value">{{ systemInfo.processors }}</span></div><div class="info-item"><span class="label">Memory</span><span class="value">{{ systemInfo.freeMemoryMb }}MB free / {{ systemInfo.maxMemoryMb }}MB max</span></div></div><pre v-if="envInfo" class="code-block">{{ JSON.stringify(envInfo, null, 2) }}</pre></div>
      </template>

      <!-- Clipboard -->
      <template v-if="activeTab === 'clipboard'">
        <div class="card"><h2>Clipboard Access (Custom Commands)</h2><p class="description">Read/write system clipboard via custom Java commands</p><div class="input-group"><input v-model="clipboardText" placeholder="Text to copy" /><button @click="clipboardWriteCmd()">Copy to Clipboard</button></div><div class="button-group"><button @click="clipboardReadCmd()">Read from Clipboard</button></div><div v-if="clipboardContent" class="result"><strong>Clipboard content:</strong> {{ clipboardContent }}</div></div>
        <div class="card"><h2>Clipboard API (Built-in)</h2><p class="description">Using Krema's built-in clipboard:readText, clipboard:writeText</p><div class="input-group"><input v-model="clipboardText" placeholder="Text to copy" /><button @click="builtinClipboardWrite()">Write (Built-in)</button></div><div class="button-group"><button @click="builtinClipboardRead()">Read (Built-in)</button><button @click="checkClipboardFormats()">Check Formats</button></div></div>
      </template>

      <!-- Shell -->
      <template v-if="activeTab === 'shell'">
        <div class="card"><h2>Shell Commands</h2><p class="description">Execute shell commands and open URLs/files via Java</p><div class="button-group"><button @click="openUrl('https://github.com')">Open GitHub</button><button @click="openUrl('https://vuejs.org')">Open Vue.js</button><button @click="showNotification()">Show Notification</button></div><div class="input-group"><input v-model="commandInput" placeholder="Enter command (e.g., ls -la)" @keyup.enter="runCommand()" /><button @click="runCommand()">Run</button></div><pre v-if="commandOutput" class="code-block">{{ commandOutput }}</pre></div>
        <div class="card"><h2>Built-in Shell APIs</h2><p class="description">shell:open, shell:showItemInFolder, shell:openWith</p><div class="button-group"><button @click="showInFinder()">Reveal Current in Finder</button><button @click="openWithApp()">Open URL with Safari</button></div></div>
      </template>

      <!-- Files -->
      <template v-if="activeTab === 'files'">
        <div class="card"><h2>File System (Custom Commands)</h2><p class="description">Access file system via custom Java commands in Commands.java</p><div class="button-group"><button @click="getPaths()">Get System Paths</button><button @click="listDirectory()">List Current Directory</button></div><div v-if="paths" class="info-grid"><div v-for="(item, i) in pathItems" :key="i" class="info-item"><span class="label">{{ item.label }}</span><span class="value clickable" @click="listDirectory(item.path)">{{ item.path }}</span></div></div></div>
        <div v-if="files.length > 0" class="card"><h2>{{ currentPath }}</h2><div class="file-list"><div v-for="(file, i) in files" :key="i" class="file-item" @click="file.isDirectory ? listDirectory(file.path) : selectFileItem(file)"><span class="icon">{{ file.isDirectory ? '\u{1F4C1}' : '\u{1F4C4}' }}</span><span class="name">{{ file.name }}</span><span v-if="!file.isDirectory" class="size">{{ formatSize(file.size) }}</span></div></div></div>
        <div v-if="selectedFile" class="card"><h2>{{ selectedFile.name }}</h2><button v-if="!fileContent" @click="readFile(selectedFile.path)">Read File</button><pre v-if="fileContent" class="code-block file-content">{{ fileContent }}</pre></div>
        <div class="card"><h2>FS Plugin (Auto-Discovered)</h2><p class="description">These commands come from FsPlugin, loaded automatically via ServiceLoader. Commands are prefixed with <code>fs:</code> (e.g., fs:exists, fs:readDir, fs:stat).</p><div class="input-group"><input v-model="fsPath" placeholder="Enter a path" /></div><div class="button-group"><button @click="fsCheckExists()">fs:exists</button><button @click="fsGetStat()">fs:stat</button><button @click="fsListDir()">fs:readDir</button><button @click="fsReadFile()">fs:readTextFile</button></div><div v-if="fsExistsResult !== null" class="info-grid"><div class="info-item"><span class="label">Exists</span><span class="value">{{ fsExistsResult }}</span></div></div><div v-if="fsStat" class="info-grid"><div class="info-item"><span class="label">Name</span><span class="value">{{ fsStat.name }}</span></div><div class="info-item"><span class="label">Path</span><span class="value">{{ fsStat.path }}</span></div><div class="info-item"><span class="label">Type</span><span class="value">{{ fsStat.isDirectory ? 'Directory' : 'File' }}</span></div><div class="info-item"><span class="label">Size</span><span class="value">{{ formatSize(fsStat.size) }}</span></div><div class="info-item"><span class="label">Modified</span><span class="value">{{ new Date(fsStat.modifiedTime).toLocaleString() }}</span></div></div><div v-if="fsPluginError" class="result" style="color:#e74c3c">{{ fsPluginError }}</div></div>
        <div v-if="fsFiles.length > 0" class="card"><h2>{{ fsCurrentDir }} <span style="font-size:0.7em;opacity:0.6">(via fs:readDir)</span></h2><div class="file-list"><div v-for="(file, i) in fsFiles" :key="i" class="file-item" @click="fsNavigateDir(file)"><span class="icon">{{ file.isDirectory ? '\u{1F4C1}' : '\u{1F4C4}' }}</span><span class="name">{{ file.name }}</span><span v-if="!file.isDirectory" class="size">{{ formatSize(file.size) }}</span></div></div></div>
        <div class="card"><h2>FS Plugin - Write &amp; Read</h2><p class="description">Write a file with fs:writeTextFile, then read it back with fs:readTextFile</p><div class="input-group"><input v-model="fsWritePath" placeholder="File path to write" /></div><div class="input-group"><input v-model="fsWriteContent" placeholder="Content to write" /></div><div class="button-group"><button @click="fsWriteFile()">Write File</button><button @click="fsWriteAndReadBack()">Write &amp; Read Back</button></div><pre v-if="fsReadResult" class="code-block">{{ fsReadResult }}</pre></div>
      </template>

      <!-- Secure Storage -->
      <template v-if="activeTab === 'securestorage'">
        <div class="card"><h2>Secure Storage (Platform Keychain)</h2><p class="description">Store and retrieve secrets using the platform's native credential store (Keychain / Credential Manager / Secret Service)</p><div class="input-group"><input v-model="ssKey" placeholder="Key name" /><input v-model="ssValue" placeholder="Secret value" /></div><div class="button-group"><button @click="ssSetCmd()">Set</button><button @click="ssGetCmd()">Get</button><button @click="ssDeleteCmd()">Delete</button><button @click="ssHasCmd()">Has</button></div><div v-if="ssResult" class="result">{{ ssResult }}</div></div>
      </template>

      <!-- Logging -->
      <template v-if="activeTab === 'logging'">
        <div class="card"><h2>Structured Logging</h2><p class="description">Send log messages to the backend. Logs are written as JSON Lines to rotating .jsonl files with app metadata (name, version, OS, session ID).</p><div class="input-group"><select v-model="logLevel" style="width:100px"><option value="trace">TRACE</option><option value="debug">DEBUG</option><option value="info">INFO</option><option value="warn">WARN</option><option value="error">ERROR</option></select><input v-model="logMessage" placeholder="Log message" @keyup.enter="sendLog()" /><button @click="sendLog()">Send</button></div><div class="button-group"><button @click="sendAllLevels()">Send All Levels</button><button @click="logHistory = []" :disabled="logHistory.length === 0">Clear History</button></div></div>
        <div class="card"><h2>Log File</h2><p class="description">JSON Lines output location (app.jsonl with rotation)</p><div class="button-group"><button @click="getLogFilePath()">Get Log Path</button><button @click="revealLogFile()">Reveal in Finder</button></div><div v-if="logFilePath" class="result">{{ logFilePath }}</div><p class="hint">Each line in app.jsonl is a JSON object with: timestamp, level, logger, message, appName, appVersion, os, sessionId. Null fields (errorMessage, stackTrace) are omitted for non-error entries.</p></div>
        <div v-if="logHistory.length > 0" class="card"><h2>Sent Log History</h2><p class="description">Recent log messages sent from this session</p><div class="event-log"><div v-for="(entry, i) in logHistory" :key="i" class="event-item"><span class="event-id" :style="{ color: logColor(entry.level) }">{{ entry.level.toUpperCase() }}</span><span class="event-time">{{ fmtTime(entry.timestamp) }}</span><span style="margin-left:8px;opacity:0.8">{{ entry.message }}</span></div></div></div>
      </template>
    </main>

    <div v-if="lastError" class="error-toast" @click="lastError = ''"><span class="icon">&#x26A0;&#xFE0F;</span><span class="message">{{ lastError }}</span><span class="close">&times;</span></div>

    <footer><p>Open DevTools (Cmd+Option+I) to see IPC messages</p></footer>
  </div>
</template>

<style>
.app { max-width: 900px; margin: 0 auto; padding: 20px; min-height: 100vh; color: #e0e0e0; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; }
header { text-align: center; padding: 30px 0 20px; }
header h1 { margin: 0; font-size: 2rem; background: linear-gradient(90deg, #667eea, #764ba2); -webkit-background-clip: text; -webkit-text-fill-color: transparent; background-clip: text; }
.subtitle { margin: 8px 0 0; color: #888; font-size: 0.95rem; }
.tabs { display: flex; flex-wrap: wrap; gap: 4px; margin-bottom: 20px; padding: 4px; background: rgba(255,255,255,0.05); border-radius: 12px; justify-content: center; }
.tabs button { padding: 10px 14px; border: none; background: transparent; color: #888; font-size: 0.8rem; font-weight: 500; border-radius: 8px; cursor: pointer; transition: all 0.2s; white-space: nowrap; }
.tabs button:hover { color: #fff; background: rgba(255,255,255,0.05); }
.tabs button.active { background: linear-gradient(135deg, #667eea, #764ba2); color: white; }
.card { background: rgba(255,255,255,0.05); border-radius: 12px; padding: 20px; margin-bottom: 16px; border: 1px solid rgba(255,255,255,0.1); }
.card h2 { margin: 0 0 8px; font-size: 1.1rem; color: #fff; }
.description { margin: 0 0 16px; color: #888; font-size: 0.85rem; }
.input-group { display: flex; gap: 8px; margin-bottom: 12px; }
input, select { padding: 10px 14px; border: 1px solid rgba(255,255,255,0.2); border-radius: 8px; background: rgba(0,0,0,0.3); color: #fff; font-size: 0.95rem; flex: 1; }
input:focus, select:focus { outline: none; border-color: #667eea; }
input.small { width: 80px; flex: none; }
select { cursor: pointer; width: auto; flex: none; }
button { padding: 10px 20px; border: none; border-radius: 8px; background: linear-gradient(135deg, #667eea, #764ba2); color: white; font-size: 0.9rem; font-weight: 500; cursor: pointer; transition: all 0.2s; }
button:hover { transform: translateY(-1px); box-shadow: 0 4px 12px rgba(102,126,234,0.4); }
button:active { transform: translateY(0); }
button:disabled { opacity: 0.5; cursor: not-allowed; transform: none; box-shadow: none; }
button.active { background: linear-gradient(135deg, #28a745, #20c997); }
.button-group { display: flex; gap: 8px; flex-wrap: wrap; margin-bottom: 12px; }
.result { padding: 12px 16px; background: rgba(102,126,234,0.15); border-radius: 8px; border-left: 3px solid #667eea; margin-top: 12px; word-break: break-word; }
.code-block { background: rgba(0,0,0,0.4); padding: 12px; border-radius: 8px; font-family: 'Monaco','Menlo',monospace; font-size: 0.8rem; overflow-x: auto; white-space: pre-wrap; word-break: break-all; margin: 12px 0 0; max-height: 300px; overflow-y: auto; }
.file-content { max-height: 400px; }
.timer-display { display: flex; align-items: baseline; gap: 8px; padding: 20px; background: rgba(102,126,234,0.15); border-radius: 12px; margin-top: 12px; }
.timer-display .count { font-size: 3rem; font-weight: bold; color: #667eea; }
.timer-display .label { color: #888; font-size: 1rem; }
.info-grid { display: grid; gap: 8px; margin-top: 12px; }
.info-item { display: flex; justify-content: space-between; padding: 10px 14px; background: rgba(0,0,0,0.2); border-radius: 8px; }
.info-item .label { color: #888; font-size: 0.85rem; }
.info-item .value { color: #fff; font-size: 0.85rem; text-align: right; max-width: 60%; word-break: break-all; }
.info-item .value.clickable { cursor: pointer; color: #667eea; }
.info-item .value.clickable:hover { text-decoration: underline; }
.file-list { max-height: 300px; overflow-y: auto; margin-top: 12px; }
.file-item { display: flex; align-items: center; gap: 10px; padding: 10px 14px; border-radius: 6px; cursor: pointer; transition: background 0.15s; }
.file-item:hover { background: rgba(255,255,255,0.1); }
.file-item .icon { font-size: 1.2rem; }
.file-item .name { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.file-item .size { color: #888; font-size: 0.8rem; }
.error-toast { position: fixed; bottom: 20px; left: 50%; transform: translateX(-50%); display: flex; align-items: center; gap: 10px; padding: 14px 20px; background: #dc3545; color: white; border-radius: 8px; cursor: pointer; box-shadow: 0 4px 20px rgba(220,53,69,0.4); z-index: 1000; max-width: 90%; }
.error-toast .message { flex: 1; }
.error-toast .close { opacity: 0.7; font-size: 1.2rem; }
footer { text-align: center; padding: 20px 0; color: #666; font-size: 0.85rem; }
.status-indicator { display: inline-block; width: 8px; height: 8px; border-radius: 50%; margin-right: 8px; }
.status-indicator.ready { background: #28a745; box-shadow: 0 0 8px rgba(40,167,69,0.5); }
.status-indicator.pending { background: #ffc107; }
.status-line { display: flex; align-items: center; padding: 12px; background: rgba(0,0,0,0.2); border-radius: 8px; margin-top: 12px; font-size: 0.9rem; }
.screen-list { display: grid; gap: 12px; margin-top: 16px; }
.screen-card { background: rgba(0,0,0,0.3); border-radius: 10px; padding: 16px; border: 1px solid rgba(255,255,255,0.1); }
.screen-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.screen-name { font-weight: 600; color: #fff; }
.screen-badge { background: linear-gradient(135deg, #667eea, #764ba2); padding: 4px 10px; border-radius: 12px; font-size: 0.75rem; font-weight: 500; }
.screen-details { display: grid; gap: 8px; }
.detail-row { display: flex; justify-content: space-between; font-size: 0.85rem; }
.detail-row .label { color: #888; }
.detail-row .value { color: #e0e0e0; font-family: 'Monaco','Menlo',monospace; }
.cursor-display { display: flex; gap: 24px; padding: 20px; background: rgba(102,126,234,0.15); border-radius: 12px; margin-top: 12px; justify-content: center; }
.cursor-display .coord { text-align: center; }
.cursor-display .coord .label { display: block; color: #888; font-size: 0.8rem; margin-bottom: 4px; }
.cursor-display .coord .value { font-size: 2rem; font-weight: bold; color: #667eea; font-family: 'Monaco','Menlo',monospace; }
.drop-zone { border: 2px dashed rgba(255,255,255,0.2); border-radius: 12px; padding: 40px 20px; text-align: center; transition: all 0.2s; min-height: 180px; display: flex; align-items: center; justify-content: center; }
.drop-zone.hovering { border-color: #667eea; background: rgba(102,126,234,0.1); }
.drop-zone.has-files { border-style: solid; border-color: rgba(102,126,234,0.3); padding: 16px; }
.drop-zone-content { display: flex; flex-direction: column; align-items: center; gap: 8px; }
.drop-zone-content .drop-icon { font-size: 3rem; }
.drop-zone-content p { color: #888; margin: 0; }
.dropped-files { width: 100%; display: grid; gap: 8px; }
.dropped-file { display: flex; align-items: center; gap: 12px; padding: 12px; background: rgba(0,0,0,0.3); border-radius: 8px; text-align: left; }
.dropped-file .file-icon { font-size: 1.5rem; }
.dropped-file .file-info { flex: 1; overflow: hidden; }
.dropped-file .file-name { display: block; color: #fff; font-weight: 500; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.dropped-file .file-meta { display: block; color: #888; font-size: 0.8rem; margin-top: 2px; }
.event-log { margin-top: 12px; max-height: 200px; overflow-y: auto; }
.event-item { display: flex; justify-content: space-between; padding: 10px 14px; background: rgba(0,0,0,0.2); border-radius: 6px; margin-bottom: 6px; }
.event-item .event-id { color: #667eea; font-weight: 500; font-family: 'Monaco','Menlo',monospace; }
.event-item .event-time { color: #888; font-size: 0.85rem; }
.hint { color: #666; font-size: 0.85rem; font-style: italic; margin-top: 12px; }
.position-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; margin-top: 12px; }
.position-grid button { padding: 12px 8px; font-size: 0.8rem; }
.form-group { display: flex; align-items: center; gap: 8px; margin-bottom: 12px; }
.form-group label { color: #888; font-size: 0.85rem; white-space: nowrap; }
input[type="range"] { flex: none; }
</style>
