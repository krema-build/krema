package build.krema.processor;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import javax.lang.model.type.ArrayType;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Annotation processor that generates {@code CommandRegistrar} implementations
 * for classes containing {@code @KremaCommand} annotated methods.
 *
 * <p>This processor works entirely via {@code javax.lang.model} and has zero
 * dependencies on krema-core. It discovers annotations by qualified name.</p>
 */
@SupportedAnnotationTypes("build.krema.core.KremaCommand")
@SupportedOptions("krema.ts.outDir")
@SupportedSourceVersion(SourceVersion.RELEASE_25)
public class KremaCommandProcessor extends AbstractProcessor {

    private static final String KREMA_COMMAND_FQN = "build.krema.core.KremaCommand";
    private static final String IPC_REQUEST_FQN = "build.krema.core.ipc.IpcHandler.IpcRequest";
    private static final String COMMAND_REGISTRAR_FQN = "build.krema.core.CommandRegistrar";
    private static final String COMMAND_INVOKER_FQN = "build.krema.core.CommandInvoker";

    private Elements elementUtils;
    private Types typeUtils;
    private Filer filer;
    private Messager messager;

    private final List<String> allRegistrarFqns = new ArrayList<>();
    private final Set<String> pojoTypesForReflect = new HashSet<>();

    // TypeScript generation state
    private record TsCommandInfo(String commandName, List<TsParamInfo> params, String resultTsType) {}
    private record TsParamInfo(String name, String tsType) {}

    private final List<TsCommandInfo> allCommandInfos = new ArrayList<>();
    private final LinkedHashMap<String, String> tsInterfaceDefinitions = new LinkedHashMap<>();
    private final Set<String> visitedTypes = new HashSet<>();
    private final Map<String, String> tsInterfaceNames = new HashMap<>();

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.elementUtils = processingEnv.getElementUtils();
        this.typeUtils = processingEnv.getTypeUtils();
        this.filer = processingEnv.getFiler();
        this.messager = processingEnv.getMessager();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (annotations.isEmpty()) {
            if (roundEnv.processingOver()) {
                if (!allRegistrarFqns.isEmpty()) {
                    writeServiceLoaderFile();
                    writeReflectConfig();
                }
                writeTsDefinitionFile();
            }
            return false;
        }

        TypeElement kremaCommandType = elementUtils.getTypeElement(KREMA_COMMAND_FQN);
        if (kremaCommandType == null) {
            return false;
        }

        // Group annotated methods by enclosing type
        Map<TypeElement, List<ExecutableElement>> methodsByClass = new LinkedHashMap<>();

        for (Element element : roundEnv.getElementsAnnotatedWith(kremaCommandType)) {
            if (element.getKind() != ElementKind.METHOD) {
                continue;
            }
            ExecutableElement method = (ExecutableElement) element;
            TypeElement enclosingType = (TypeElement) method.getEnclosingElement();
            methodsByClass.computeIfAbsent(enclosingType, k -> new ArrayList<>()).add(method);
        }

        // Generate a registrar for each class and collect TS metadata
        for (Map.Entry<TypeElement, List<ExecutableElement>> entry : methodsByClass.entrySet()) {
            try {
                generateRegistrar(entry.getKey(), entry.getValue());
            } catch (IOException e) {
                messager.printMessage(Diagnostic.Kind.ERROR,
                    "Failed to generate registrar: " + e.getMessage(), entry.getKey());
            }
            for (ExecutableElement method : entry.getValue()) {
                collectCommandInfo(method);
            }
        }

        return true;
    }

    private void generateRegistrar(TypeElement targetType, List<ExecutableElement> methods) throws IOException {
        String packageName = getPackageName(targetType);
        String targetSimpleName = getSimpleClassName(targetType);
        String registrarSimpleName = targetSimpleName.replace('.', '_') + "_CommandRegistrar";
        String registrarFqn = packageName.isEmpty()
            ? registrarSimpleName
            : packageName + "." + registrarSimpleName;

        allRegistrarFqns.add(registrarFqn);

        JavaFileObject sourceFile = filer.createSourceFile(registrarFqn, targetType);
        try (PrintWriter out = new PrintWriter(sourceFile.openWriter())) {
            if (!packageName.isEmpty()) {
                out.println("package " + packageName + ";");
                out.println();
            }

            out.println("import build.krema.core.CommandInvoker;");
            out.println("import build.krema.core.CommandRegistrar;");
            out.println("import com.fasterxml.jackson.databind.JsonNode;");
            out.println("import com.fasterxml.jackson.databind.ObjectMapper;");
            out.println();
            out.println("import java.util.HashMap;");
            out.println("import java.util.Map;");
            out.println();
            out.println("/**");
            out.println(" * Auto-generated CommandRegistrar for {@link " + targetType.getQualifiedName() + "}.");
            out.println(" * Do not edit — regenerated by the Krema annotation processor.");
            out.println(" */");
            out.println("public class " + registrarSimpleName + " implements CommandRegistrar {");
            out.println();
            out.println("    private static final ObjectMapper MAPPER = new ObjectMapper();");
            out.println();

            // targetType()
            out.println("    @Override");
            out.println("    public Class<?> targetType() {");
            out.println("        return " + targetType.getQualifiedName() + ".class;");
            out.println("    }");
            out.println();

            // createInvokers()
            out.println("    @Override");
            out.println("    public Map<String, CommandInvoker> createInvokers(Object instance) {");
            out.println("        " + targetType.getQualifiedName() + " target = ("
                + targetType.getQualifiedName() + ") instance;");
            out.println("        Map<String, CommandInvoker> m = new HashMap<>();");
            out.println();

            for (ExecutableElement method : methods) {
                String commandName = getCommandName(method);
                String methodName = method.getSimpleName().toString();
                List<? extends VariableElement> params = method.getParameters();
                boolean isVoid = method.getReturnType().getKind() == TypeKind.VOID;

                out.println("        m.put(\"" + commandName + "\", (args) -> {");

                if (params.isEmpty()) {
                    // No parameters
                    if (isVoid) {
                        out.println("            target." + methodName + "();");
                        out.println("            return null;");
                    } else {
                        out.println("            return target." + methodName + "();");
                    }
                } else if (params.size() == 1 && isIpcRequestType(params.get(0).asType())) {
                    // Single IpcRequest parameter
                    out.println("            build.krema.core.ipc.IpcHandler.IpcRequest req = "
                        + "new build.krema.core.ipc.IpcHandler.IpcRequest(\"" + commandName + "\", args);");
                    if (isVoid) {
                        out.println("            target." + methodName + "(req);");
                        out.println("            return null;");
                    } else {
                        out.println("            return target." + methodName + "(req);");
                    }
                } else if (params.size() == 1 && isComplexType(params.get(0).asType())) {
                    // Single POJO parameter — deserialize entire args
                    TypeMirror paramType = params.get(0).asType();
                    String paramTypeName = getTypeString(paramType);
                    String rawTypeName = getRawTypeString(paramType);
                    trackPojoType(paramType);
                    if (hasTypeArguments(paramType)) {
                        out.println("            @SuppressWarnings(\"unchecked\")");
                        out.println("            " + paramTypeName + " p0 = (" + paramTypeName
                            + ") MAPPER.treeToValue(args, " + rawTypeName + ".class);");
                    } else {
                        out.println("            " + paramTypeName + " p0 = MAPPER.treeToValue(args, "
                            + rawTypeName + ".class);");
                    }
                    if (isVoid) {
                        out.println("            target." + methodName + "(p0);");
                        out.println("            return null;");
                    } else {
                        out.println("            return target." + methodName + "(p0);");
                    }
                } else {
                    // Multiple parameters or simple types — extract by name
                    StringBuilder callArgs = new StringBuilder();
                    for (int i = 0; i < params.size(); i++) {
                        VariableElement param = params.get(i);
                        String paramName = param.getSimpleName().toString();
                        TypeMirror paramType = param.asType();
                        String varName = "p" + i;

                        if (isIpcRequestType(paramType)) {
                            out.println("            build.krema.core.ipc.IpcHandler.IpcRequest "
                                + varName + " = new build.krema.core.ipc.IpcHandler.IpcRequest(\""
                                + commandName + "\", args);");
                        } else {
                            writeExtractCode(out, paramName, paramType, varName);
                        }

                        if (i > 0) callArgs.append(", ");
                        callArgs.append(varName);
                    }

                    if (isVoid) {
                        out.println("            target." + methodName + "(" + callArgs + ");");
                        out.println("            return null;");
                    } else {
                        out.println("            return target." + methodName + "(" + callArgs + ");");
                    }
                }

                out.println("        });");
                out.println();
            }

            out.println("        return m;");
            out.println("    }");
            out.println("}");
        }
    }

    private String getCommandName(ExecutableElement method) {
        // Read annotation value via the mirror API
        for (var annotationMirror : method.getAnnotationMirrors()) {
            if (annotationMirror.getAnnotationType().toString().equals(KREMA_COMMAND_FQN)) {
                for (var entry : annotationMirror.getElementValues().entrySet()) {
                    if (entry.getKey().getSimpleName().toString().equals("value")) {
                        String value = entry.getValue().getValue().toString();
                        if (!value.isEmpty()) {
                            return value;
                        }
                    }
                }
            }
        }
        return method.getSimpleName().toString();
    }

    private void writeExtractCode(PrintWriter out, String paramName, TypeMirror type, String varName) {
        String typeStr = getTypeString(type);
        TypeKind kind = type.getKind();

        if (kind.isPrimitive()) {
            String line = switch (kind) {
                case INT -> typeStr + " " + varName + " = args != null && args.has(\"" + paramName + "\") ? args.get(\"" + paramName + "\").asInt() : 0;";
                case LONG -> typeStr + " " + varName + " = args != null && args.has(\"" + paramName + "\") ? args.get(\"" + paramName + "\").asLong() : 0L;";
                case DOUBLE -> typeStr + " " + varName + " = args != null && args.has(\"" + paramName + "\") ? args.get(\"" + paramName + "\").asDouble() : 0.0;";
                case FLOAT -> typeStr + " " + varName + " = args != null && args.has(\"" + paramName + "\") ? (float) args.get(\"" + paramName + "\").asDouble() : 0.0f;";
                case BOOLEAN -> typeStr + " " + varName + " = args != null && args.has(\"" + paramName + "\") ? args.get(\"" + paramName + "\").asBoolean() : false;";
                case SHORT -> typeStr + " " + varName + " = args != null && args.has(\"" + paramName + "\") ? (short) args.get(\"" + paramName + "\").asInt() : (short) 0;";
                case BYTE -> typeStr + " " + varName + " = args != null && args.has(\"" + paramName + "\") ? (byte) args.get(\"" + paramName + "\").asInt() : (byte) 0;";
                case CHAR -> typeStr + " " + varName + " = args != null && args.has(\"" + paramName + "\") ? args.get(\"" + paramName + "\").asText().charAt(0) : '\\0';";
                default -> typeStr + " " + varName + " = 0; // unsupported primitive";
            };
            out.println("            " + line);
            return;
        }

        // String
        if (isStringType(type)) {
            out.println("            String " + varName + " = args != null && args.has(\"" + paramName + "\") ? args.get(\"" + paramName + "\").asText() : null;");
            return;
        }

        // Boxed primitives
        String boxedName = getBoxedTypeName(type);
        if (boxedName != null) {
            String line = switch (boxedName) {
                case "Integer" -> "Integer " + varName + " = args != null && args.has(\"" + paramName + "\") ? args.get(\"" + paramName + "\").asInt() : null;";
                case "Long" -> "Long " + varName + " = args != null && args.has(\"" + paramName + "\") ? args.get(\"" + paramName + "\").asLong() : null;";
                case "Double" -> "Double " + varName + " = args != null && args.has(\"" + paramName + "\") ? args.get(\"" + paramName + "\").asDouble() : null;";
                case "Float" -> "Float " + varName + " = args != null && args.has(\"" + paramName + "\") ? (float) args.get(\"" + paramName + "\").asDouble() : null;";
                case "Boolean" -> "Boolean " + varName + " = args != null && args.has(\"" + paramName + "\") ? args.get(\"" + paramName + "\").asBoolean() : null;";
                case "Short" -> "Short " + varName + " = args != null && args.has(\"" + paramName + "\") ? (short) args.get(\"" + paramName + "\").asInt() : null;";
                case "Byte" -> "Byte " + varName + " = args != null && args.has(\"" + paramName + "\") ? (byte) args.get(\"" + paramName + "\").asInt() : null;";
                case "Character" -> "Character " + varName + " = args != null && args.has(\"" + paramName + "\") ? args.get(\"" + paramName + "\").asText().charAt(0) : null;";
                default -> typeStr + " " + varName + " = null;";
            };
            out.println("            " + line);
            return;
        }

        // Path type
        if (isPathType(type)) {
            out.println("            java.nio.file.Path " + varName + " = args != null && args.has(\"" + paramName + "\") ? java.nio.file.Path.of(args.get(\"" + paramName + "\").asText()) : null;");
            return;
        }

        // Complex type — use Jackson to deserialize from the named field
        trackPojoType(type);
        String rawType = getRawTypeString(type);
        if (hasTypeArguments(type)) {
            out.println("            @SuppressWarnings(\"unchecked\")");
            out.println("            " + typeStr + " " + varName + " = args != null && args.has(\"" + paramName + "\") ? (" + typeStr + ") MAPPER.treeToValue(args.get(\"" + paramName + "\"), " + rawType + ".class) : null;");
        } else {
            out.println("            " + typeStr + " " + varName + " = args != null && args.has(\"" + paramName + "\") ? MAPPER.treeToValue(args.get(\"" + paramName + "\"), " + rawType + ".class) : null;");
        }
    }

    private boolean isIpcRequestType(TypeMirror type) {
        if (type.getKind() != TypeKind.DECLARED) return false;
        TypeElement element = (TypeElement) ((DeclaredType) type).asElement();
        return element.getQualifiedName().toString().equals(IPC_REQUEST_FQN);
    }

    private boolean isStringType(TypeMirror type) {
        if (type.getKind() != TypeKind.DECLARED) return false;
        TypeElement element = (TypeElement) ((DeclaredType) type).asElement();
        return element.getQualifiedName().toString().equals("java.lang.String");
    }

    private boolean isPathType(TypeMirror type) {
        if (type.getKind() != TypeKind.DECLARED) return false;
        TypeElement element = (TypeElement) ((DeclaredType) type).asElement();
        return element.getQualifiedName().toString().equals("java.nio.file.Path");
    }

    private String getBoxedTypeName(TypeMirror type) {
        if (type.getKind() != TypeKind.DECLARED) return null;
        TypeElement element = (TypeElement) ((DeclaredType) type).asElement();
        String fqn = element.getQualifiedName().toString();
        return switch (fqn) {
            case "java.lang.Integer" -> "Integer";
            case "java.lang.Long" -> "Long";
            case "java.lang.Double" -> "Double";
            case "java.lang.Float" -> "Float";
            case "java.lang.Boolean" -> "Boolean";
            case "java.lang.Short" -> "Short";
            case "java.lang.Byte" -> "Byte";
            case "java.lang.Character" -> "Character";
            default -> null;
        };
    }

    private boolean isComplexType(TypeMirror type) {
        if (type.getKind().isPrimitive()) return false;
        if (type.getKind() != TypeKind.DECLARED) return false;
        TypeElement element = (TypeElement) ((DeclaredType) type).asElement();
        String fqn = element.getQualifiedName().toString();
        return !fqn.equals("java.lang.String") &&
               !fqn.startsWith("java.lang.Number") &&
               !fqn.equals("java.lang.Integer") &&
               !fqn.equals("java.lang.Long") &&
               !fqn.equals("java.lang.Double") &&
               !fqn.equals("java.lang.Float") &&
               !fqn.equals("java.lang.Boolean") &&
               !fqn.equals("java.lang.Short") &&
               !fqn.equals("java.lang.Byte") &&
               !fqn.equals("java.lang.Character") &&
               !fqn.equals("java.nio.file.Path");
    }

    private String getTypeString(TypeMirror type) {
        if (type.getKind().isPrimitive()) {
            return switch (type.getKind()) {
                case INT -> "int";
                case LONG -> "long";
                case DOUBLE -> "double";
                case FLOAT -> "float";
                case BOOLEAN -> "boolean";
                case SHORT -> "short";
                case BYTE -> "byte";
                case CHAR -> "char";
                default -> type.toString();
            };
        }
        return type.toString();
    }

    /**
     * Returns the raw (erased) type string suitable for use in {@code .class} literals.
     * For example, {@code java.util.Map<String, Object>} becomes {@code java.util.Map}.
     */
    private String getRawTypeString(TypeMirror type) {
        if (type.getKind().isPrimitive()) {
            return getTypeString(type);
        }
        if (type.getKind() == TypeKind.DECLARED) {
            TypeElement element = (TypeElement) ((DeclaredType) type).asElement();
            return element.getQualifiedName().toString();
        }
        return typeUtils.erasure(type).toString();
    }

    /**
     * Returns true if the type has type arguments (generics).
     */
    private boolean hasTypeArguments(TypeMirror type) {
        if (type.getKind() != TypeKind.DECLARED) return false;
        DeclaredType declaredType = (DeclaredType) type;
        return !declaredType.getTypeArguments().isEmpty();
    }

    private void trackPojoType(TypeMirror type) {
        if (type.getKind() != TypeKind.DECLARED) return;
        TypeElement element = (TypeElement) ((DeclaredType) type).asElement();
        String fqn = element.getQualifiedName().toString();
        // Only track non-JDK types
        if (!fqn.startsWith("java.") && !fqn.startsWith("javax.")) {
            pojoTypesForReflect.add(fqn);
        }
    }

    private String getPackageName(TypeElement type) {
        // Walk up to the top-level type, then get its package
        Element enclosing = type;
        while (enclosing.getEnclosingElement().getKind() != ElementKind.PACKAGE) {
            enclosing = enclosing.getEnclosingElement();
        }
        PackageElement pkg = (PackageElement) enclosing.getEnclosingElement();
        return pkg.isUnnamed() ? "" : pkg.getQualifiedName().toString();
    }

    private String getSimpleClassName(TypeElement type) {
        // For inner classes: Outer.Inner -> Outer_Inner
        StringBuilder sb = new StringBuilder();
        Element current = type;
        List<String> parts = new ArrayList<>();
        while (current.getKind() == ElementKind.CLASS || current.getKind() == ElementKind.INTERFACE) {
            parts.add(0, current.getSimpleName().toString());
            current = current.getEnclosingElement();
        }
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) sb.append('_');
            sb.append(parts.get(i));
        }
        return sb.toString();
    }

    private void writeServiceLoaderFile() {
        try {
            FileObject file = filer.createResource(
                StandardLocation.CLASS_OUTPUT,
                "",
                "META-INF/services/" + COMMAND_REGISTRAR_FQN);
            try (Writer writer = file.openWriter()) {
                for (String fqn : allRegistrarFqns) {
                    writer.write(fqn + "\n");
                }
            }
        } catch (IOException e) {
            messager.printMessage(Diagnostic.Kind.ERROR,
                "Failed to write ServiceLoader file: " + e.getMessage());
        }
    }

    private void writeReflectConfig() {
        if (pojoTypesForReflect.isEmpty()) {
            return;
        }
        try {
            FileObject file = filer.createResource(
                StandardLocation.CLASS_OUTPUT,
                "",
                "META-INF/native-image/reflect-config.json");
            try (Writer writer = file.openWriter()) {
                writer.write("[\n");
                int i = 0;
                for (String fqn : pojoTypesForReflect) {
                    if (i > 0) writer.write(",\n");
                    writer.write("  {\n");
                    writer.write("    \"name\": \"" + fqn + "\",\n");
                    writer.write("    \"allDeclaredConstructors\": true,\n");
                    writer.write("    \"allDeclaredMethods\": true,\n");
                    writer.write("    \"allDeclaredFields\": true\n");
                    writer.write("  }");
                    i++;
                }
                writer.write("\n]\n");
            }
        } catch (IOException e) {
            messager.printMessage(Diagnostic.Kind.ERROR,
                "Failed to write reflect-config.json: " + e.getMessage());
        }
    }

    // ==================== TypeScript Type Generation ====================

    /**
     * Maps a Java {@link TypeMirror} to a TypeScript type string.
     * Side-effect: populates {@link #tsInterfaceDefinitions} for complex types.
     */
    private String toTsType(TypeMirror type) {
        if (type == null) return "unknown";

        TypeKind kind = type.getKind();

        // Primitives
        if (kind.isPrimitive()) {
            return switch (kind) {
                case INT, LONG, DOUBLE, FLOAT, SHORT, BYTE -> "number";
                case BOOLEAN -> "boolean";
                case CHAR -> "string";
                default -> "unknown";
            };
        }

        // void
        if (kind == TypeKind.VOID) return "void";

        // Arrays
        if (kind == TypeKind.ARRAY) {
            ArrayType arrayType = (ArrayType) type;
            return toTsType(arrayType.getComponentType()) + "[]";
        }

        if (kind != TypeKind.DECLARED) return "unknown";

        DeclaredType declaredType = (DeclaredType) type;
        TypeElement element = (TypeElement) declaredType.asElement();
        String fqn = element.getQualifiedName().toString();

        // String, char/Character
        if (fqn.equals("java.lang.String") || fqn.equals("java.lang.Character")) {
            return "string";
        }

        // Numeric boxed types
        if (fqn.equals("java.lang.Integer") || fqn.equals("java.lang.Long") ||
            fqn.equals("java.lang.Double") || fqn.equals("java.lang.Float") ||
            fqn.equals("java.lang.Short") || fqn.equals("java.lang.Byte")) {
            return "number";
        }

        // Boolean boxed
        if (fqn.equals("java.lang.Boolean")) return "boolean";

        // Void boxed
        if (fqn.equals("java.lang.Void")) return "void";

        // Path
        if (fqn.equals("java.nio.file.Path")) return "string";

        // Object
        if (fqn.equals("java.lang.Object")) return "unknown";

        // CompletableFuture<T> -> unwrap to T
        if (fqn.equals("java.util.concurrent.CompletableFuture")) {
            List<? extends TypeMirror> typeArgs = declaredType.getTypeArguments();
            if (!typeArgs.isEmpty()) {
                return toTsType(typeArgs.get(0));
            }
            return "unknown";
        }

        // List<T>, Set<T>, Collection<T> -> T[]
        if (fqn.equals("java.util.List") || fqn.equals("java.util.Set") ||
            fqn.equals("java.util.Collection") || fqn.equals("java.util.ArrayList") ||
            fqn.equals("java.util.LinkedList") || fqn.equals("java.util.HashSet")) {
            List<? extends TypeMirror> typeArgs = declaredType.getTypeArguments();
            if (!typeArgs.isEmpty()) {
                return toTsType(typeArgs.get(0)) + "[]";
            }
            return "unknown[]";
        }

        // Map<String, V> -> Record<string, V>
        if (fqn.equals("java.util.Map") || fqn.equals("java.util.HashMap") ||
            fqn.equals("java.util.LinkedHashMap") || fqn.equals("java.util.TreeMap")) {
            List<? extends TypeMirror> typeArgs = declaredType.getTypeArguments();
            if (typeArgs.size() == 2) {
                return "Record<" + toTsType(typeArgs.get(0)) + ", " + toTsType(typeArgs.get(1)) + ">";
            }
            return "Record<string, unknown>";
        }

        // Enum -> union of string literals
        if (element.getKind() == ElementKind.ENUM) {
            List<String> constants = element.getEnclosedElements().stream()
                .filter(e -> e.getKind() == ElementKind.ENUM_CONSTANT)
                .map(e -> "'" + e.getSimpleName().toString() + "'")
                .collect(Collectors.toList());
            if (constants.isEmpty()) return "string";
            return String.join(" | ", constants);
        }

        // Records and POJOs -> generate interface
        return generateTsInterface(element);
    }

    /**
     * Generates a TypeScript interface for a record or POJO class.
     * Returns the interface name for use as a type reference.
     */
    private String generateTsInterface(TypeElement element) {
        String fqn = element.getQualifiedName().toString();

        // Already generated — return its name
        if (tsInterfaceNames.containsKey(fqn)) {
            return tsInterfaceNames.get(fqn);
        }

        // Guard against circular references
        if (visitedTypes.contains(fqn)) {
            return resolveTsInterfaceName(element);
        }
        visitedTypes.add(fqn);

        String interfaceName = resolveTsInterfaceName(element);
        tsInterfaceNames.put(fqn, interfaceName);

        StringBuilder body = new StringBuilder();
        body.append("export interface ").append(interfaceName).append(" {\n");

        // Try record components first (Java 16+)
        var recordComponents = element.getRecordComponents();
        if (recordComponents != null && !recordComponents.isEmpty()) {
            for (var component : recordComponents) {
                String fieldName = component.getSimpleName().toString();
                String tsType = toTsType(component.asType());
                body.append("  ").append(fieldName).append(": ").append(tsType).append(";\n");
            }
        } else {
            // Fall back to public fields and getters
            Set<String> addedFields = new HashSet<>();

            // Public fields
            for (Element enclosed : element.getEnclosedElements()) {
                if (enclosed.getKind() == ElementKind.FIELD &&
                    enclosed.getModifiers().contains(Modifier.PUBLIC) &&
                    !enclosed.getModifiers().contains(Modifier.STATIC)) {
                    String fieldName = enclosed.getSimpleName().toString();
                    String tsType = toTsType(enclosed.asType());
                    body.append("  ").append(fieldName).append(": ").append(tsType).append(";\n");
                    addedFields.add(fieldName);
                }
            }

            // Getter methods: getX() -> x, isX() -> x
            for (Element enclosed : element.getEnclosedElements()) {
                if (enclosed.getKind() != ElementKind.METHOD) continue;
                ExecutableElement method = (ExecutableElement) enclosed;
                if (!method.getModifiers().contains(Modifier.PUBLIC) ||
                    method.getModifiers().contains(Modifier.STATIC) ||
                    !method.getParameters().isEmpty()) continue;

                String methodName = method.getSimpleName().toString();
                String fieldName = null;

                if (methodName.startsWith("get") && methodName.length() > 3) {
                    fieldName = Character.toLowerCase(methodName.charAt(3)) + methodName.substring(4);
                } else if (methodName.startsWith("is") && methodName.length() > 2 &&
                           (method.getReturnType().getKind() == TypeKind.BOOLEAN ||
                            isBoxedBoolean(method.getReturnType()))) {
                    fieldName = Character.toLowerCase(methodName.charAt(2)) + methodName.substring(3);
                }

                if (fieldName != null && !addedFields.contains(fieldName)) {
                    String tsType = toTsType(method.getReturnType());
                    body.append("  ").append(fieldName).append(": ").append(tsType).append(";\n");
                    addedFields.add(fieldName);
                }
            }
        }

        body.append("}");
        tsInterfaceDefinitions.put(fqn, body.toString());

        visitedTypes.remove(fqn);
        return interfaceName;
    }

    /**
     * Resolves a unique TypeScript interface name for a Java type.
     * Uses simple name by default; prefixes with enclosing class name on collision.
     */
    private String resolveTsInterfaceName(TypeElement element) {
        String simpleName = element.getSimpleName().toString();

        // Check for collision: another FQN already mapped to this simple name
        for (Map.Entry<String, String> entry : tsInterfaceNames.entrySet()) {
            if (entry.getValue().equals(simpleName) &&
                !entry.getKey().equals(element.getQualifiedName().toString())) {
                // Collision — use enclosing class prefix
                Element enclosing = element.getEnclosingElement();
                if (enclosing.getKind() == ElementKind.CLASS ||
                    enclosing.getKind() == ElementKind.INTERFACE) {
                    return enclosing.getSimpleName().toString() + simpleName;
                }
            }
        }

        return simpleName;
    }

    private boolean isBoxedBoolean(TypeMirror type) {
        if (type.getKind() != TypeKind.DECLARED) return false;
        TypeElement element = (TypeElement) ((DeclaredType) type).asElement();
        return element.getQualifiedName().toString().equals("java.lang.Boolean");
    }

    // ==================== Command Info Collection ====================

    /**
     * Extracts TypeScript metadata from a {@code @KremaCommand} method.
     */
    private void collectCommandInfo(ExecutableElement method) {
        String commandName = getCommandName(method);
        TypeMirror returnType = method.getReturnType();

        // Unwrap CompletableFuture<T>
        String resultTsType = toTsType(returnType);

        List<? extends VariableElement> params = method.getParameters();
        List<TsParamInfo> tsParams = new ArrayList<>();

        // Filter out IpcRequest params
        List<VariableElement> relevantParams = new ArrayList<>();
        for (VariableElement param : params) {
            if (!isIpcRequestType(param.asType())) {
                relevantParams.add(param);
            }
        }

        if (relevantParams.size() == 1 && isComplexType(relevantParams.get(0).asType())) {
            // Single POJO param: flatten its fields as the args
            TypeMirror paramType = relevantParams.get(0).asType();
            if (paramType.getKind() == TypeKind.DECLARED) {
                TypeElement paramElement = (TypeElement) ((DeclaredType) paramType).asElement();
                var recordComponents = paramElement.getRecordComponents();
                if (recordComponents != null && !recordComponents.isEmpty()) {
                    for (var component : recordComponents) {
                        tsParams.add(new TsParamInfo(
                            component.getSimpleName().toString(),
                            toTsType(component.asType())
                        ));
                    }
                } else {
                    // Fall back to getters/fields for POJOs
                    flattenPojoFields(paramElement, tsParams);
                }
            }
        } else {
            // Multiple or simple params: each becomes a named arg field
            for (VariableElement param : relevantParams) {
                tsParams.add(new TsParamInfo(
                    param.getSimpleName().toString(),
                    toTsType(param.asType())
                ));
            }
        }

        allCommandInfos.add(new TsCommandInfo(commandName, tsParams, resultTsType));
    }

    /**
     * Extracts public fields and getter-derived fields from a POJO for flattening.
     */
    private void flattenPojoFields(TypeElement element, List<TsParamInfo> tsParams) {
        Set<String> addedFields = new HashSet<>();

        for (Element enclosed : element.getEnclosedElements()) {
            if (enclosed.getKind() == ElementKind.FIELD &&
                enclosed.getModifiers().contains(Modifier.PUBLIC) &&
                !enclosed.getModifiers().contains(Modifier.STATIC)) {
                String name = enclosed.getSimpleName().toString();
                tsParams.add(new TsParamInfo(name, toTsType(enclosed.asType())));
                addedFields.add(name);
            }
        }

        for (Element enclosed : element.getEnclosedElements()) {
            if (enclosed.getKind() != ElementKind.METHOD) continue;
            ExecutableElement method = (ExecutableElement) enclosed;
            if (!method.getModifiers().contains(Modifier.PUBLIC) ||
                method.getModifiers().contains(Modifier.STATIC) ||
                !method.getParameters().isEmpty()) continue;

            String methodName = method.getSimpleName().toString();
            String fieldName = null;

            if (methodName.startsWith("get") && methodName.length() > 3) {
                fieldName = Character.toLowerCase(methodName.charAt(3)) + methodName.substring(4);
            } else if (methodName.startsWith("is") && methodName.length() > 2) {
                fieldName = Character.toLowerCase(methodName.charAt(2)) + methodName.substring(3);
            }

            if (fieldName != null && !addedFields.contains(fieldName)) {
                tsParams.add(new TsParamInfo(fieldName, toTsType(method.getReturnType())));
                addedFields.add(fieldName);
            }
        }
    }

    // ==================== .d.ts File Writing ====================

    /**
     * Writes the {@code krema-commands.d.ts} file with all collected command types.
     */
    private void writeTsDefinitionFile() {
        if (allCommandInfos.isEmpty()) return;

        try {
            StringBuilder sb = new StringBuilder();
            sb.append("// Auto-generated by Krema annotation processor. Do not edit.\n\n");

            // Emit interface definitions
            for (String interfaceBody : tsInterfaceDefinitions.values()) {
                sb.append(interfaceBody).append("\n\n");
            }

            // Emit KremaCommandMap
            sb.append("export interface KremaCommandMap {\n");
            for (TsCommandInfo cmd : allCommandInfos) {
                sb.append("  '").append(cmd.commandName()).append("': { ");
                if (!cmd.params().isEmpty()) {
                    sb.append("args: { ");
                    for (int i = 0; i < cmd.params().size(); i++) {
                        TsParamInfo param = cmd.params().get(i);
                        if (i > 0) sb.append("; ");
                        sb.append(param.name()).append(": ").append(param.tsType());
                    }
                    sb.append(" }; ");
                }
                sb.append("result: ").append(cmd.resultTsType()).append(" };\n");
            }
            sb.append("}\n\n");

            // Emit typed invoke declaration
            sb.append("declare namespace krema {\n");
            sb.append("  function invoke<K extends keyof KremaCommandMap>(\n");
            sb.append("    command: K,\n");
            sb.append("    ...args: KremaCommandMap[K] extends { args: infer A } ? [args: A] : [args?: Record<string, never>]\n");
            sb.append("  ): Promise<KremaCommandMap[K]['result']>;\n");
            sb.append("}\n");

            String content = sb.toString();

            // Check for custom output directory
            String outDir = processingEnv.getOptions().get("krema.ts.outDir");
            if (outDir != null && !outDir.isEmpty()) {
                Path outPath = Path.of(outDir, "krema-commands.d.ts");
                Files.createDirectories(outPath.getParent());
                Files.writeString(outPath, content);
            } else {
                FileObject file = filer.createResource(
                    StandardLocation.CLASS_OUTPUT, "", "krema-commands.d.ts");
                try (Writer writer = file.openWriter()) {
                    writer.write(content);
                }
            }
        } catch (IOException e) {
            messager.printMessage(Diagnostic.Kind.ERROR,
                "Failed to write krema-commands.d.ts: " + e.getMessage());
        }
    }
}
