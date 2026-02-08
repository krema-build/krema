/**
 * Krema API TypeScript Definitions
 * Provides type-safe access to native APIs from the frontend.
 */

declare namespace krema {
    // ============================================================================
    // Core IPC
    // ============================================================================

    /**
     * Invoke a backend command.
     * @param command - The command name (e.g., "dialog:openFile")
     * @param args - The command arguments
     * @returns Promise resolving to the command result
     */
    function invoke<T = unknown>(command: string, args?: Record<string, unknown>): Promise<T>;

    /**
     * Register an event listener for backend events.
     * @param event - The event name
     * @param callback - The callback function
     * @returns Unsubscribe function
     */
    function on<T = unknown>(event: string, callback: (data: T) => void): () => void;

    // ============================================================================
    // Dialog API
    // ============================================================================

    namespace dialog {
        interface FileFilter {
            name: string;
            extensions: string[];
        }

        interface OpenFileOptions {
            title?: string;
            defaultPath?: string;
            filters?: FileFilter[];
        }

        interface SaveFileOptions {
            title?: string;
            defaultPath?: string;
            filters?: FileFilter[];
        }

        interface FolderOptions {
            title?: string;
            defaultPath?: string;
        }

        type MessageType = 'info' | 'warning' | 'error';

        /**
         * Opens a file selection dialog.
         * @returns Selected file path or null if cancelled
         */
        function openFile(options?: OpenFileOptions): Promise<string | null>;

        /**
         * Opens a multi-file selection dialog.
         * @returns Array of selected file paths or null if cancelled
         */
        function openFiles(options?: OpenFileOptions): Promise<string[] | null>;

        /**
         * Opens a save file dialog.
         * @returns Selected save path or null if cancelled
         */
        function saveFile(options?: SaveFileOptions): Promise<string | null>;

        /**
         * Opens a folder selection dialog.
         * @returns Selected folder path or null if cancelled
         */
        function selectFolder(options?: FolderOptions): Promise<string | null>;

        /**
         * Shows a message dialog.
         */
        function message(title: string, message: string, type?: MessageType): Promise<void>;

        /**
         * Shows a confirmation dialog.
         * @returns true if confirmed, false otherwise
         */
        function confirm(title: string, message: string): Promise<boolean>;

        /**
         * Shows a prompt dialog for text input.
         * @returns User input or null if cancelled
         */
        function prompt(title: string, message: string, defaultValue?: string): Promise<string | null>;
    }

    // ============================================================================
    // Clipboard API
    // ============================================================================

    namespace clipboard {
        /**
         * Reads text from the clipboard.
         * @returns Clipboard text or null if unavailable
         */
        function readText(): Promise<string | null>;

        /**
         * Writes text to the clipboard.
         * @returns true on success
         */
        function writeText(text: string): Promise<boolean>;

        /**
         * Reads HTML content from the clipboard.
         * @returns HTML string or null if unavailable
         */
        function readHtml(): Promise<string | null>;

        /**
         * Checks if the clipboard contains text.
         */
        function hasText(): Promise<boolean>;

        /**
         * Checks if the clipboard contains an image.
         */
        function hasImage(): Promise<boolean>;

        /**
         * Reads an image from the clipboard as base64 PNG.
         * @returns Base64-encoded PNG or null if unavailable
         */
        function readImageBase64(): Promise<string | null>;

        /**
         * Clears the clipboard contents.
         * @returns true on success
         */
        function clear(): Promise<boolean>;

        /**
         * Gets available clipboard formats.
         * @returns Array of MIME types
         */
        function getAvailableFormats(): Promise<string[]>;
    }

    // ============================================================================
    // Secure Storage API
    // ============================================================================

    namespace secureStorage {
        /**
         * Stores a secret value in the platform's native credential store.
         * - macOS: Keychain Services
         * - Windows: Credential Manager
         * - Linux: Secret Service (libsecret)
         */
        function set(key: string, value: string): Promise<boolean>;

        /**
         * Retrieves a secret value from the native credential store.
         * @returns The stored value, or null if not found
         */
        function get(key: string): Promise<string | null>;

        /**
         * Deletes a secret from the native credential store.
         * @returns true if the secret was deleted
         */
        function delete(key: string): Promise<boolean>;

        /**
         * Checks if a secret exists in the native credential store.
         */
        function has(key: string): Promise<boolean>;
    }

    // ============================================================================
    // Shell API
    // ============================================================================

    namespace shell {
        interface CommandResult {
            code: number;
            stdout: string;
            stderr: string;
            success: boolean;
        }

        interface ExecuteOptions {
            cwd?: string;
            env?: Record<string, string>;
            timeout?: number;
        }

        /**
         * Opens a URL or file with the default application.
         * @returns true on success
         */
        function open(path: string): Promise<boolean>;

        /**
         * Opens a URL with the default browser.
         * @returns true on success
         */
        function openUrl(url: string): Promise<boolean>;

        /**
         * Opens a file with the default application.
         * @returns true on success
         */
        function openFile(path: string): Promise<boolean>;

        /**
         * Opens a file with a specific application.
         * @returns true on success
         */
        function openWith(path: string, app: string): Promise<boolean>;

        /**
         * Reveals a file in the system file manager (Finder/Explorer).
         * @returns true on success
         */
        function showItemInFolder(path: string): Promise<boolean>;

        /**
         * Alias for showItemInFolder.
         * @returns true on success
         */
        function revealInFinder(path: string): Promise<boolean>;

        /**
         * Executes a shell command.
         * @returns Command execution result
         */
        function execute(command: string, options?: ExecuteOptions): Promise<CommandResult>;
    }

    // ============================================================================
    // Path API
    // ============================================================================

    namespace path {
        /**
         * Gets the app-specific data directory.
         * - macOS: ~/Library/Application Support/{app}
         * - Windows: %APPDATA%\{app}
         * - Linux: ~/.local/share/{app}
         */
        function appDataDir(): Promise<string>;

        /**
         * Gets the app-specific config directory.
         * - macOS: ~/Library/Preferences/{app}
         * - Windows: %APPDATA%\{app}
         * - Linux: ~/.config/{app}
         */
        function appConfigDir(): Promise<string>;

        /**
         * Gets the app-specific cache directory.
         * - macOS: ~/Library/Caches/{app}
         * - Windows: %LOCALAPPDATA%\{app}\Cache
         * - Linux: ~/.cache/{app}
         */
        function appCacheDir(): Promise<string>;

        /**
         * Gets the app-specific log directory.
         * - macOS: ~/Library/Logs/{app}
         * - Windows: %LOCALAPPDATA%\{app}\Logs
         * - Linux: ~/.local/state/{app}/logs
         */
        function appLogDir(): Promise<string>;

        /**
         * Gets the user's home directory.
         */
        function homeDir(): Promise<string>;

        /**
         * Gets the system temp directory.
         */
        function tempDir(): Promise<string>;

        /**
         * Gets the user's Desktop directory.
         */
        function desktopDir(): Promise<string>;

        /**
         * Gets the user's Documents directory.
         */
        function documentsDir(): Promise<string>;

        /**
         * Gets the user's Downloads directory.
         */
        function downloadsDir(): Promise<string>;

        /**
         * Joins path segments.
         */
        function join(...paths: string[]): Promise<string>;

        /**
         * Gets the directory name of a path.
         */
        function dirname(path: string): Promise<string>;

        /**
         * Gets the file name of a path.
         * @param ext - Optional extension to remove
         */
        function basename(path: string, ext?: string): Promise<string>;

        /**
         * Gets the extension of a path (including the dot).
         */
        function extname(path: string): Promise<string>;

        /**
         * Resolves path segments to an absolute path.
         */
        function resolve(...paths: string[]): Promise<string>;

        /**
         * Checks if a path is absolute.
         */
        function isAbsolute(path: string): Promise<boolean>;

        /**
         * Normalizes a path (resolves . and ..).
         */
        function normalize(path: string): Promise<string>;
    }

    // ============================================================================
    // Notification API
    // ============================================================================

    namespace notification {
        interface NotificationOptions {
            sound?: string;
            icon?: string;
        }

        /**
         * Shows a system notification.
         * @returns true on success
         */
        function show(title: string, body: string, options?: NotificationOptions): Promise<boolean>;

        /**
         * Checks if notifications are supported on this platform.
         */
        function isSupported(): Promise<boolean>;
    }

    // ============================================================================
    // System Tray API
    // ============================================================================

    namespace tray {
        interface MenuItem {
            label: string;
            action?: string;
            separator?: boolean;
        }

        /**
         * Sets the tray icon.
         * @param iconPath - Path to icon image
         */
        function setIcon(iconPath: string): Promise<boolean>;

        /**
         * Sets the tray tooltip.
         */
        function setTooltip(tooltip: string): Promise<boolean>;

        /**
         * Sets the tray menu items.
         */
        function setMenu(items: MenuItem[]): Promise<boolean>;

        /**
         * Shows the tray icon.
         */
        function show(): Promise<boolean>;

        /**
         * Hides the tray icon.
         */
        function hide(): Promise<boolean>;
    }

    // ============================================================================
    // Screen API
    // ============================================================================

    namespace screen {
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

        /**
         * Gets information about all connected screens.
         */
        function getAll(): Promise<ScreenInfo[]>;

        /**
         * Gets information about the primary screen.
         */
        function getPrimary(): Promise<ScreenInfo>;

        /**
         * Gets the number of connected screens.
         */
        function getCount(): Promise<number>;

        /**
         * Gets the current cursor position.
         */
        function getCursorPosition(): Promise<CursorPosition>;

        /**
         * Gets the screen containing the specified point.
         */
        function getScreenAtPoint(x: number, y: number): Promise<ScreenInfo | null>;

        /**
         * Gets the screen where the cursor is currently located.
         */
        function getScreenAtCursor(): Promise<ScreenInfo | null>;
    }

    // ============================================================================
    // Drag and Drop API
    // ============================================================================

    namespace dragdrop {
        interface DroppedFile {
            name: string;
            path: string | null;
            type: string;
            size: number;
        }

        interface FileDropEvent {
            type: 'drop';
            files: DroppedFile[];
            x: number;
            y: number;
        }

        interface FileDropHoverEvent {
            type: 'enter' | 'leave';
            x: number;
            y: number;
        }

        interface ConfigureOptions {
            enabled?: boolean;
            acceptedExtensions?: string[];
            acceptedMimeTypes?: string[];
            dropZoneSelector?: string;
        }

        /**
         * Configures drag-drop behavior.
         */
        function configure(options: ConfigureOptions): Promise<void>;

        /**
         * Enables drag-drop.
         */
        function enable(): Promise<void>;

        /**
         * Disables drag-drop.
         */
        function disable(): Promise<void>;
    }

    // ============================================================================
    // Native Menu API
    // ============================================================================

    namespace menu {
        type MenuItemType = 'normal' | 'separator' | 'checkbox' | 'radio' | 'submenu';

        type MenuItemRole =
            | 'about' | 'services' | 'hide' | 'hide-others' | 'unhide' | 'quit'
            | 'undo' | 'redo' | 'cut' | 'copy' | 'paste' | 'paste-and-match-style' | 'delete' | 'select-all'
            | 'reload' | 'force-reload' | 'toggle-dev-tools' | 'reset-zoom' | 'zoom-in' | 'zoom-out' | 'toggle-fullscreen'
            | 'minimize' | 'zoom' | 'close' | 'front';

        interface MenuItem {
            id?: string;
            label?: string;
            accelerator?: string;
            type?: MenuItemType;
            enabled?: boolean;
            checked?: boolean;
            role?: MenuItemRole;
            submenu?: MenuItem[];
        }

        interface MenuClickEvent {
            id: string;
        }

        /**
         * Sets the application menu bar.
         */
        function setApplicationMenu(menu: MenuItem[]): Promise<void>;

        /**
         * Shows a context menu at the specified position.
         */
        function showContextMenu(options: { items: MenuItem[]; x?: number; y?: number }): Promise<void>;

        /**
         * Sets the dock menu (macOS only).
         */
        function setDockMenu(items: MenuItem[]): Promise<void>;

        /**
         * Updates an existing menu item.
         */
        function updateItem(options: { id: string; enabled?: boolean; checked?: boolean }): Promise<void>;
    }

    // ============================================================================
    // Updater API
    // ============================================================================

    namespace updater {
        interface CheckResult {
            updateAvailable: boolean;
            version: string | null;
            notes: string | null;
            date: string | null;
            mandatory: boolean;
            size: number;
        }

        interface DownloadResult {
            downloaded: boolean;
            path: string | null;
        }

        interface UpdateAvailableEvent {
            version: string;
            notes: string;
            date: string;
            mandatory: boolean;
            size: number;
        }

        interface DownloadProgressEvent {
            progress: number;
        }

        interface UpdateReadyEvent {
            path: string;
        }

        interface UpdateErrorEvent {
            message: string;
        }

        /**
         * Checks for available updates.
         * @returns Check result indicating whether an update is available
         */
        function check(): Promise<CheckResult>;

        /**
         * Downloads the available update.
         * Emits 'updater:download-progress' events during download.
         * @returns Download result with the path to the downloaded file
         */
        function download(): Promise<DownloadResult>;

        /**
         * Installs a previously downloaded update.
         * @returns true on success
         */
        function install(): Promise<boolean>;

        /**
         * Installs the update and restarts the application.
         * Emits 'updater:before-restart' before restarting.
         * @returns true on success (though the app will restart)
         */
        function installAndRestart(): Promise<boolean>;
    }

    // ============================================================================
    // Deep Link API
    // ============================================================================

    namespace deepLink {
        interface DeepLinkReceivedEvent {
            url: string;
        }

        /**
         * Gets the URL that launched the app, if any.
         * @returns Object with url property, or empty object if no deep link
         */
        function getCurrent(): Promise<{ url?: string }>;
    }

    // ============================================================================
    // App API & Lifecycle Events
    // ============================================================================

    namespace app {
        interface EnvironmentInfo {
            name: string;
            vars: Record<string, string>;
        }

        /**
         * Gets the current environment profile and custom variables.
         */
        function getEnvironment(): Promise<EnvironmentInfo>;

        /**
         * Gets the current environment profile name (e.g., "development", "production").
         */
        function getEnvironmentName(): Promise<string>;

        /**
         * Gets a single environment variable by key.
         * @returns The variable value, or null if not found
         */
        function getEnvironmentVar(key: string): Promise<string | null>;

        /**
         * Emitted when the app is fully initialized.
         */
        const READY: 'app:ready';

        /**
         * Emitted when the app is activated (macOS: dock click with no windows).
         */
        const ACTIVATE: 'app:activate';

        /**
         * Emitted before the app quits.
         */
        const BEFORE_QUIT: 'app:before-quit';

        /**
         * Emitted when all windows are closed.
         */
        const WINDOW_ALL_CLOSED: 'app:window-all-closed';
    }
}

// Extend the Window interface
interface Window {
    krema: typeof krema;
    __krema_invoke: (request: string) => Promise<unknown>;
    __krema_emit: (event: string, dataJson: string) => void;
}

// Helper type for creating typed invoke wrappers
type KremaInvoke = typeof krema.invoke;

export = krema;
export as namespace krema;
