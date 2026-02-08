/**
 * Platform-specific adapter implementations for Krema's Hexagonal Architecture.
 *
 * <p>Adapters implement the port interfaces defined in {@link build.krema.core.ports}
 * and provide platform-specific functionality for each operating system.</p>
 *
 * <h2>Package Structure</h2>
 * <ul>
 *   <li>{@code factory/} - Unified adapter factory</li>
 *   <li>{@code macos/} - macOS implementations (Cocoa via FFM)</li>
 *   <li>{@code windows/} - Windows implementations (Win32 via FFM)</li>
 *   <li>{@code linux/} - Linux implementations (GTK via FFM)</li>
 * </ul>
 *
 * <h2>Using Adapters</h2>
 * <pre>{@code
 * // Preferred: Use AdapterFactory
 * WindowPort window = AdapterFactory.window();
 *
 * // Or get all adapters
 * AdapterFactory.Adapters adapters = AdapterFactory.all();
 * }</pre>
 *
 * <h2>Legacy Engine Classes</h2>
 * <p>The existing {@code *Engine} implementations in subsystem packages
 * (e.g., {@code window.macos.MacOSWindowEngine}) remain available but are
 * deprecated. New code should use the adapter factory.</p>
 *
 * @see build.krema.core.ports
 * @see build.krema.core.adapters.factory.AdapterFactory
 */
package build.krema.core.adapters;
