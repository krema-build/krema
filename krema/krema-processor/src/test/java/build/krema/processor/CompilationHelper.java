package build.krema.processor;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class CompilationHelper {

    record CompilationResult(boolean success, List<Diagnostic<? extends JavaFileObject>> diagnostics, String tsContent) {}

    static CompilationResult compile(JavaFileObject... sources) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();

        Path tempDir = Files.createTempDirectory("krema-test");

        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, null)) {
            fileManager.setLocation(StandardLocation.CLASS_OUTPUT, List.of(tempDir.toFile()));
            fileManager.setLocation(StandardLocation.SOURCE_OUTPUT, List.of(tempDir.toFile()));

            List<JavaFileObject> allSources = new ArrayList<>(List.of(sources));
            allSources.addAll(stubSources());

            List<String> options = List.of(
                "--release", "25",
                "-parameters",
                "-processor", KremaCommandProcessor.class.getName()
            );

            JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, diagnostics, options, null, allSources);
            boolean success = task.call();

            String tsContent = null;
            Path tsFile = tempDir.resolve("krema-commands.d.ts");
            if (Files.exists(tsFile)) {
                tsContent = Files.readString(tsFile);
            }

            return new CompilationResult(success, diagnostics.getDiagnostics(), tsContent);
        }
    }

    private static List<JavaFileObject> stubSources() {
        return List.of(
            source("build.krema.core.KremaCommand", """
                package build.krema.core;
                import java.lang.annotation.*;
                @Retention(RetentionPolicy.RUNTIME)
                @Target(ElementType.METHOD)
                public @interface KremaCommand {
                    String value() default "";
                }
                """),
            source("build.krema.core.CommandRegistrar", """
                package build.krema.core;
                import java.util.Map;
                public interface CommandRegistrar {
                    Class<?> targetType();
                    Map<String, build.krema.core.CommandInvoker> createInvokers(Object instance);
                }
                """),
            source("build.krema.core.CommandInvoker", """
                package build.krema.core;
                import com.fasterxml.jackson.databind.JsonNode;
                @FunctionalInterface
                public interface CommandInvoker {
                    Object invoke(JsonNode args) throws Exception;
                }
                """),
            source("build.krema.core.ipc.IpcHandler", """
                package build.krema.core.ipc;
                import com.fasterxml.jackson.databind.JsonNode;
                public class IpcHandler {
                    public static class IpcRequest {
                        private final String command;
                        private final JsonNode args;
                        public IpcRequest(String command, JsonNode args) {
                            this.command = command;
                            this.args = args;
                        }
                        public String getCommand() { return command; }
                        public JsonNode getArgs() { return args; }
                    }
                }
                """)
        );
    }

    static JavaFileObject source(String qualifiedName, String code) {
        URI uri = URI.create("string:///" + qualifiedName.replace('.', '/') + ".java");
        return new SimpleJavaFileObject(uri, JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return code;
            }
        };
    }

    private CompilationHelper() {}
}
