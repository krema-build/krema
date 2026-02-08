package build.krema.processor;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
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
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Annotation processor that generates {@code CommandRegistrar} implementations
 * for classes containing {@code @KremaCommand} annotated methods.
 *
 * <p>This processor works entirely via {@code javax.lang.model} and has zero
 * dependencies on krema-core. It discovers annotations by qualified name.</p>
 */
@SupportedAnnotationTypes("build.krema.KremaCommand")
@SupportedSourceVersion(SourceVersion.RELEASE_25)
public class KremaCommandProcessor extends AbstractProcessor {

    private static final String KREMA_COMMAND_FQN = "build.krema.KremaCommand";
    private static final String IPC_REQUEST_FQN = "build.krema.ipc.IpcHandler.IpcRequest";
    private static final String COMMAND_REGISTRAR_FQN = "build.krema.CommandRegistrar";
    private static final String COMMAND_INVOKER_FQN = "build.krema.CommandInvoker";

    private Elements elementUtils;
    private Types typeUtils;
    private Filer filer;
    private Messager messager;

    private final List<String> allRegistrarFqns = new ArrayList<>();
    private final Set<String> pojoTypesForReflect = new HashSet<>();

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
            if (roundEnv.processingOver() && !allRegistrarFqns.isEmpty()) {
                writeServiceLoaderFile();
                writeReflectConfig();
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

        // Generate a registrar for each class
        for (Map.Entry<TypeElement, List<ExecutableElement>> entry : methodsByClass.entrySet()) {
            try {
                generateRegistrar(entry.getKey(), entry.getValue());
            } catch (IOException e) {
                messager.printMessage(Diagnostic.Kind.ERROR,
                    "Failed to generate registrar: " + e.getMessage(), entry.getKey());
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

            out.println("import build.krema.CommandInvoker;");
            out.println("import build.krema.CommandRegistrar;");
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
                    out.println("            build.krema.ipc.IpcHandler.IpcRequest req = "
                        + "new build.krema.ipc.IpcHandler.IpcRequest(\"" + commandName + "\", args);");
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
                            out.println("            build.krema.ipc.IpcHandler.IpcRequest "
                                + varName + " = new build.krema.ipc.IpcHandler.IpcRequest(\""
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
}
