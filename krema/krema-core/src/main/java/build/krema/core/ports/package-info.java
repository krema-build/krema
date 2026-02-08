/**
 * Port interfaces for Krema's Hexagonal Architecture.
 *
 * <p>Ports define the contracts between the application core and external systems
 * (operating system, native APIs, etc.). Each port is implemented by platform-specific
 * adapters in the {@code adapters} package.</p>
 *
 * <h2>Available Ports</h2>
 * <ul>
 *   <li>{@link build.krema.core.ports.WindowPort} - Window management operations</li>
 *   <li>{@link build.krema.core.ports.WebViewPort} - WebView rendering and IPC</li>
 *   <li>{@link build.krema.core.ports.DialogPort} - File and message dialogs</li>
 *   <li>{@link build.krema.core.ports.MenuPort} - Application and context menus</li>
 *   <li>{@link build.krema.core.ports.NotificationPort} - Desktop notifications</li>
 *   <li>{@link build.krema.core.ports.ScreenPort} - Screen/display information</li>
 *   <li>{@link build.krema.core.ports.GlobalShortcutPort} - System-wide keyboard shortcuts</li>
 *   <li>{@link build.krema.core.ports.DockPort} - Dock/taskbar badges and attention</li>
 * </ul>
 *
 * <h2>Migration from Engine Interfaces</h2>
 * <p>The old {@code *Engine} interfaces (e.g., {@code WindowEngine}) are deprecated
 * and extend the corresponding {@code *Port} interfaces. Existing code continues to work,
 * but new code should use the Port interfaces directly.</p>
 *
 * @see build.krema.core.adapters
 */
package build.krema.core.ports;
