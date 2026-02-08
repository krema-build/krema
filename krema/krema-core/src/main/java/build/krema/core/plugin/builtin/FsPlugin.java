package build.krema.core.plugin.builtin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

import build.krema.core.KremaCommand;
import build.krema.core.plugin.KremaPlugin;
import build.krema.core.plugin.PluginContext;

/**
 * Built-in file system plugin.
 * Provides file read/write operations.
 */
public class FsPlugin implements KremaPlugin {

    @Override
    public String getId() {
        return "krema.fs";
    }

    @Override
    public String getName() {
        return "File System";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getDescription() {
        return "File system operations for Krema applications";
    }

    @Override
    public List<Object> getCommandHandlers() {
        return List.of(new FsCommands());
    }

    @Override
    public List<String> getRequiredPermissions() {
        return List.of("fs:read", "fs:write");
    }

    public static class FsCommands {

        @KremaCommand("fs:readTextFile")
        public String readTextFile(String path) throws IOException {
            return Files.readString(Path.of(path), StandardCharsets.UTF_8);
        }

        @KremaCommand("fs:readBinaryFile")
        public String readBinaryFile(String path) throws IOException {
            byte[] bytes = Files.readAllBytes(Path.of(path));
            return Base64.getEncoder().encodeToString(bytes);
        }

        @KremaCommand("fs:writeTextFile")
        public boolean writeTextFile(String path, String content) throws IOException {
            Files.writeString(Path.of(path), content, StandardCharsets.UTF_8);
            return true;
        }

        @KremaCommand("fs:writeBinaryFile")
        public boolean writeBinaryFile(String path, String base64Content) throws IOException {
            byte[] bytes = Base64.getDecoder().decode(base64Content);
            Files.write(Path.of(path), bytes);
            return true;
        }

        @KremaCommand("fs:appendTextFile")
        public boolean appendTextFile(String path, String content) throws IOException {
            Files.writeString(Path.of(path), content, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            return true;
        }

        @KremaCommand("fs:exists")
        public boolean exists(String path) {
            return Files.exists(Path.of(path));
        }

        @KremaCommand("fs:isFile")
        public boolean isFile(String path) {
            return Files.isRegularFile(Path.of(path));
        }

        @KremaCommand("fs:isDirectory")
        public boolean isDirectory(String path) {
            return Files.isDirectory(Path.of(path));
        }

        @KremaCommand("fs:createDir")
        public boolean createDir(String path) throws IOException {
            Files.createDirectories(Path.of(path));
            return true;
        }

        @KremaCommand("fs:remove")
        public boolean remove(String path) throws IOException {
            Path p = Path.of(path);
            if (Files.isDirectory(p)) {
                // Recursively delete directory
                Files.walkFileTree(p, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        Files.delete(file);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                        Files.delete(dir);
                        return FileVisitResult.CONTINUE;
                    }
                });
            } else {
                Files.deleteIfExists(p);
            }
            return true;
        }

        @KremaCommand("fs:rename")
        public boolean rename(String oldPath, String newPath) throws IOException {
            Files.move(Path.of(oldPath), Path.of(newPath), StandardCopyOption.REPLACE_EXISTING);
            return true;
        }

        @KremaCommand("fs:copy")
        public boolean copy(String source, String destination) throws IOException {
            Path src = Path.of(source);
            Path dest = Path.of(destination);

            if (Files.isDirectory(src)) {
                // Recursively copy directory
                Files.walkFileTree(src, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                        Path targetDir = dest.resolve(src.relativize(dir));
                        Files.createDirectories(targetDir);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        Files.copy(file, dest.resolve(src.relativize(file)), StandardCopyOption.REPLACE_EXISTING);
                        return FileVisitResult.CONTINUE;
                    }
                });
            } else {
                Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        }

        @KremaCommand("fs:readDir")
        public List<FileInfo> readDir(String path) throws IOException {
            List<FileInfo> entries = new ArrayList<>();
            try (var stream = Files.list(Path.of(path))) {
                stream.forEach(p -> {
                    try {
                        BasicFileAttributes attrs = Files.readAttributes(p, BasicFileAttributes.class);
                        entries.add(new FileInfo(
                            p.getFileName().toString(),
                            p.toString(),
                            attrs.isDirectory(),
                            attrs.size(),
                            attrs.lastModifiedTime().toMillis()
                        ));
                    } catch (IOException e) {
                        // Skip entries we can't read
                    }
                });
            }
            return entries;
        }

        @KremaCommand("fs:stat")
        public FileInfo stat(String path) throws IOException {
            Path p = Path.of(path);
            BasicFileAttributes attrs = Files.readAttributes(p, BasicFileAttributes.class);
            return new FileInfo(
                p.getFileName().toString(),
                p.toString(),
                attrs.isDirectory(),
                attrs.size(),
                attrs.lastModifiedTime().toMillis()
            );
        }

        public record FileInfo(
            String name,
            String path,
            boolean isDirectory,
            long size,
            long modifiedTime
        ) {}
    }
}
