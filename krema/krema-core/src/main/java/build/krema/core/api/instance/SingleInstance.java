package build.krema.core.api.instance;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import build.krema.core.KremaCommand;
import build.krema.core.event.EventEmitter;

/**
 * Single instance API.
 * Ensures only one instance of the application is running.
 * Can pass arguments from secondary instances to the primary instance.
 */
public class SingleInstance {

    private final Path lockFile;
    private final int port;
    private FileChannel channel;
    private FileLock lock;
    private ServerSocket serverSocket;
    private ExecutorService executor;
    private EventEmitter eventEmitter;
    private boolean isPrimary = false;

    /**
     * Creates a SingleInstance manager.
     *
     * @param appDataDir The application data directory
     * @param appId A unique identifier for the application
     */
    public SingleInstance(Path appDataDir, String appId) {
        this.lockFile = appDataDir.resolve(appId + ".lock");
        // Use a hash of the appId to generate a consistent port number (49152-65535 range)
        this.port = 49152 + Math.abs(appId.hashCode() % 16383);
    }

    /**
     * Sets the event emitter for second-instance events.
     */
    public void setEventEmitter(EventEmitter emitter) {
        this.eventEmitter = emitter;
    }

    /**
     * Attempts to acquire the single instance lock.
     * Returns true if this is the primary (first) instance.
     * Returns false if another instance is already running.
     */
    public boolean requestLock() {
        try {
            // Create parent directories if needed
            Files.createDirectories(lockFile.getParent());

            // Try to acquire file lock
            channel = FileChannel.open(lockFile,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE);
            lock = channel.tryLock();

            if (lock != null) {
                // We are the primary instance
                isPrimary = true;
                startServer();

                // Write PID to lock file for debugging
                channel.write(java.nio.ByteBuffer.wrap(
                    String.valueOf(ProcessHandle.current().pid()).getBytes()
                ));

                // Register shutdown hook to clean up
                Runtime.getRuntime().addShutdownHook(new Thread(this::releaseLock));

                return true;
            } else {
                // Another instance has the lock
                channel.close();
                return false;
            }
        } catch (Exception e) {
            System.err.println("[SingleInstance] Failed to acquire lock: " + e.getMessage());
            return false;
        }
    }

    /**
     * Releases the single instance lock.
     */
    public void releaseLock() {
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            if (executor != null) {
                executor.shutdownNow();
            }
            if (lock != null && lock.isValid()) {
                lock.release();
            }
            if (channel != null && channel.isOpen()) {
                channel.close();
            }
            Files.deleteIfExists(lockFile);
        } catch (Exception e) {
            System.err.println("[SingleInstance] Failed to release lock: " + e.getMessage());
        }
    }

    /**
     * Notifies the primary instance that a second instance was launched.
     * Called by the secondary instance.
     */
    public boolean notifyPrimaryInstance(String[] args) {
        try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), port);
             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream())) {

            out.writeObject(args);
            out.flush();
            return true;
        } catch (Exception e) {
            System.err.println("[SingleInstance] Failed to notify primary instance: " + e.getMessage());
            return false;
        }
    }

    private void startServer() {
        try {
            serverSocket = new ServerSocket(port, 50, InetAddress.getLoopbackAddress());
            executor = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "SingleInstance-Server");
                t.setDaemon(true);
                return t;
            });

            executor.submit(() -> {
                while (!serverSocket.isClosed()) {
                    try {
                        Socket client = serverSocket.accept();
                        handleSecondInstance(client);
                    } catch (Exception e) {
                        if (!serverSocket.isClosed()) {
                            System.err.println("[SingleInstance] Server error: " + e.getMessage());
                        }
                    }
                }
            });
        } catch (Exception e) {
            System.err.println("[SingleInstance] Failed to start server: " + e.getMessage());
        }
    }

    private void handleSecondInstance(Socket client) {
        try (ObjectInputStream in = new ObjectInputStream(client.getInputStream())) {
            String[] args = (String[]) in.readObject();

            // Emit event to notify the app
            if (eventEmitter != null) {
                eventEmitter.emit("app:second-instance", Map.of(
                    "args", args != null ? args : new String[0]
                ));
            }
        } catch (Exception e) {
            System.err.println("[SingleInstance] Failed to handle second instance: " + e.getMessage());
        }
    }

    @KremaCommand("instance:isPrimary")
    public boolean isPrimary() {
        return isPrimary;
    }

    @KremaCommand("instance:requestLock")
    public boolean requestLockCommand() {
        return requestLock();
    }

    @KremaCommand("instance:releaseLock")
    public void releaseLockCommand() {
        releaseLock();
    }

    @KremaCommand("instance:focusWindow")
    public void focusWindow() {
        // This would typically be called by the primary instance
        // when it receives a second-instance notification
        // The actual window focus is handled by the Window API
        if (eventEmitter != null) {
            eventEmitter.emit("app:focus-requested", Map.of());
        }
    }
}
