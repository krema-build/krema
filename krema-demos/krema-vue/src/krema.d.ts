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
    }

    // ============================================================================
    // Clipboard API
    // ============================================================================

    namespace clipboard {
        // Types defined in main krema namespace
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
    }

    // ============================================================================
    // Path API
    // ============================================================================

    namespace path {
        // Types defined in main krema namespace
    }

    // ============================================================================
    // Notification API
    // ============================================================================

    namespace notification {
        interface NotificationOptions {
            sound?: string;
            icon?: string;
        }
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
    }

    // ============================================================================
    // Window Management API
    // ============================================================================

    namespace window {
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

        interface Position {
            x: number;
            y: number;
        }

        interface Size {
            width: number;
            height: number;
        }
    }

    // ============================================================================
    // App Lifecycle Events
    // ============================================================================

    namespace app {
        const READY: 'app:ready';
        const ACTIVATE: 'app:activate';
        const BEFORE_QUIT: 'app:before-quit';
        const WINDOW_ALL_CLOSED: 'app:window-all-closed';
    }
}

// Extend the Window interface
interface Window {
    krema: typeof krema;
    __krema_invoke: (request: string) => Promise<unknown>;
    __krema_emit: (event: string, dataJson: string) => void;
}
