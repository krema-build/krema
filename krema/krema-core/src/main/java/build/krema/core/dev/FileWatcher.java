package build.krema.core.dev;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import build.krema.core.util.Logger;

/**
 * Watches file system for changes and triggers callbacks.
 * Used for hot reload during development.
 */
public class FileWatcher implements AutoCloseable {

    private static final Logger LOG = new Logger("FileWatcher");

    private final WatchService watchService;
    private final Map<WatchKey, Path> watchKeys = new ConcurrentHashMap<>();
    private final Set<String> extensions;
    private final Consumer<FileChangeEvent> callback;
    private final ExecutorService executor;
    private volatile boolean running = false;

    // Debounce settings
    private final long debounceMs;
    private final Map<Path, Long> lastEventTimes = new ConcurrentHashMap<>();

    public FileWatcher(Consumer<FileChangeEvent> callback, Set<String> extensions, long debounceMs) throws IOException {
        this.watchService = FileSystems.getDefault().newWatchService();
        this.callback = callback;
        this.extensions = extensions != null ? extensions : Set.of();
        this.debounceMs = debounceMs;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "FileWatcher");
            t.setDaemon(true);
            return t;
        });
    }

    public FileWatcher(Consumer<FileChangeEvent> callback) throws IOException {
        this(callback, null, 100);
    }

    public void watch(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            throw new IllegalArgumentException("Not a directory: " + directory);
        }

        // Register directory and all subdirectories
        Files.walkFileTree(directory, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                registerDirectory(dir);
                return FileVisitResult.CONTINUE;
            }
        });

        LOG.debug("Watching directory: %s", directory);
    }

    private void registerDirectory(Path dir) throws IOException {
        WatchKey key = dir.register(watchService,
            StandardWatchEventKinds.ENTRY_CREATE,
            StandardWatchEventKinds.ENTRY_DELETE,
            StandardWatchEventKinds.ENTRY_MODIFY
        );
        watchKeys.put(key, dir);
    }

    public void start() {
        if (running) {
            return;
        }
        running = true;

        executor.submit(() -> {
            LOG.info("File watcher started");
            while (running) {
                try {
                    WatchKey key = watchService.poll(500, TimeUnit.MILLISECONDS);
                    if (key == null) {
                        continue;
                    }

                    Path dir = watchKeys.get(key);
                    if (dir == null) {
                        continue;
                    }

                    for (WatchEvent<?> event : key.pollEvents()) {
                        WatchEvent.Kind<?> kind = event.kind();

                        if (kind == StandardWatchEventKinds.OVERFLOW) {
                            continue;
                        }

                        @SuppressWarnings("unchecked")
                        WatchEvent<Path> ev = (WatchEvent<Path>) event;
                        Path fileName = ev.context();
                        Path fullPath = dir.resolve(fileName);

                        // Filter by extension if specified
                        if (!extensions.isEmpty()) {
                            String name = fileName.toString();
                            boolean matches = extensions.stream().anyMatch(ext -> name.endsWith("." + ext));
                            if (!matches) {
                                continue;
                            }
                        }

                        // Debounce
                        long now = System.currentTimeMillis();
                        Long lastTime = lastEventTimes.get(fullPath);
                        if (lastTime != null && now - lastTime < debounceMs) {
                            continue;
                        }
                        lastEventTimes.put(fullPath, now);

                        // Determine event type
                        FileChangeEvent.Type type;
                        if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
                            type = FileChangeEvent.Type.CREATED;
                            // If a new directory is created, watch it
                            if (Files.isDirectory(fullPath)) {
                                registerDirectory(fullPath);
                            }
                        } else if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                            type = FileChangeEvent.Type.DELETED;
                        } else {
                            type = FileChangeEvent.Type.MODIFIED;
                        }

                        // Notify callback
                        try {
                            callback.accept(new FileChangeEvent(fullPath, type));
                        } catch (Exception e) {
                            LOG.error("Error in file change callback", e);
                        }
                    }

                    boolean valid = key.reset();
                    if (!valid) {
                        watchKeys.remove(key);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (IOException e) {
                    LOG.error("Error watching files", e);
                }
            }
            LOG.info("File watcher stopped");
        });
    }

    public void stop() {
        running = false;
    }

    @Override
    public void close() {
        stop();
        executor.shutdown();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        try {
            watchService.close();
        } catch (IOException e) {
            // Ignore
        }
    }

    public record FileChangeEvent(Path path, Type type) {
        public enum Type {
            CREATED, MODIFIED, DELETED
        }

        public boolean isJavaFile() {
            return path.toString().endsWith(".java");
        }

        public boolean isResourceFile() {
            String name = path.toString();
            return name.endsWith(".html") || name.endsWith(".css") ||
                   name.endsWith(".js") || name.endsWith(".json");
        }
    }
}
