package io.statescan.bytecode;

import io.statescan.graph.ClassNode;
import io.statescan.graph.FieldNode;
import io.statescan.graph.MethodNode;
import io.statescan.graph.ParameterNode;
import org.objectweb.asm.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * ASM ClassVisitor that extracts class structure information.
 */
public class ClassVisitorAdapter extends ClassVisitor {

    private String className;
    private String superclass;
    private Set<String> interfaces = new HashSet<>();
    private Set<String> annotations = new HashSet<>();
    private Set<FieldNode> fields = new HashSet<>();
    private Set<MethodNode> methods = new HashSet<>();
    private boolean isInterface;
    private boolean isAbstract;
    private boolean isEnum;
    private String sourceFile;

    public ClassVisitorAdapter() {
        super(Opcodes.ASM9);
    }

    @Override
    public void visit(int version, int access, String name, String signature,
            String superName, String[] interfaces) {
        this.className = name.replace('/', '.');
        this.superclass = superName != null ? superName.replace('/', '.') : null;

        if (interfaces != null) {
            this.interfaces = Arrays.stream(interfaces)
                    .map(i -> i.replace('/', '.'))
                    .collect(Collectors.toSet());
        }

        this.isInterface = (access & Opcodes.ACC_INTERFACE) != 0;
        this.isAbstract = (access & Opcodes.ACC_ABSTRACT) != 0;
        this.isEnum = (access & Opcodes.ACC_ENUM) != 0;
    }

    @Override
    public void visitSource(String source, String debug) {
        this.sourceFile = source;
    }

    @Override
    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
        // Convert descriptor like "Ljavax/inject/Singleton;" to "javax.inject.Singleton"
        String annotationClass = descriptorToClassName(descriptor);
        annotations.add(annotationClass);
        return null; // We don't need to visit annotation values
    }

    @Override
    public FieldVisitor visitField(int access, String name, String descriptor,
            String signature, Object value) {
        boolean isStatic = (access & Opcodes.ACC_STATIC) != 0;
        boolean isFinal = (access & Opcodes.ACC_FINAL) != 0;
        boolean isPrivate = (access & Opcodes.ACC_PRIVATE) != 0;
        boolean isVolatile = (access & Opcodes.ACC_VOLATILE) != 0;

        Set<String> fieldAnnotations = new HashSet<>();

        return new FieldVisitor(Opcodes.ASM9) {
            @Override
            public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                fieldAnnotations.add(descriptorToClassName(descriptor));
                return null;
            }

            @Override
            public void visitEnd() {
                FieldNode fieldNode = FieldNode.builder()
                        .name(name)
                        .type(descriptor)
                        .annotations(fieldAnnotations)
                        .isStatic(isStatic)
                        .isFinal(isFinal)
                        .isPrivate(isPrivate)
                        .isVolatile(isVolatile)
                        .build();
                fields.add(fieldNode);
            }
        };
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor,
            String signature, String[] exceptions) {
        boolean isStatic = (access & Opcodes.ACC_STATIC) != 0;
        boolean isPublic = (access & Opcodes.ACC_PUBLIC) != 0;
        boolean isAbstract = (access & Opcodes.ACC_ABSTRACT) != 0;

        Set<String> methodAnnotations = new HashSet<>();
        Map<Integer, Set<String>> parameterAnnotations = new HashMap<>();

        // Create our method visitor to capture invocations
        MethodVisitorAdapter methodAdapter = new MethodVisitorAdapter();

        return new MethodVisitor(Opcodes.ASM9, methodAdapter) {
            @Override
            public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                methodAnnotations.add(descriptorToClassName(desc));
                return null;
            }

            @Override
            public AnnotationVisitor visitParameterAnnotation(int parameter, String desc, boolean visible) {
                parameterAnnotations
                        .computeIfAbsent(parameter, k -> new HashSet<>())
                        .add(descriptorToClassName(desc));
                return null;
            }

            @Override
            public void visitEnd() {
                // Build parameter nodes from descriptor and annotations
                List<ParameterNode> parameters = buildParameterNodes(descriptor, parameterAnnotations, methodAnnotations);

                MethodNode methodNode = MethodNode.builder()
                        .name(name)
                        .descriptor(descriptor)
                        .invocations(methodAdapter.getInvocations())
                        .fieldAccesses(methodAdapter.getFieldAccesses())
                        .classConstantRefs(methodAdapter.getClassConstantRefs())
                        .annotations(methodAnnotations)
                        .parameters(parameters)
                        .isStatic(isStatic)
                        .isPublic(isPublic)
                        .isAbstract(isAbstract)
                        .build();
                methods.add(methodNode);
            }
        };
    }

    /**
     * Builds ParameterNode list from method descriptor and collected parameter annotations.
     * Only populates parameters for DI-relevant methods (constructors with @Inject, @Provides methods).
     */
    private List<ParameterNode> buildParameterNodes(String descriptor,
            Map<Integer, Set<String>> parameterAnnotations,
            Set<String> methodAnnotations) {
        // Only capture parameters for DI-relevant methods to minimize memory usage
        boolean isDIRelevant = isInjectAnnotated(methodAnnotations) ||
                isProvidesAnnotated(methodAnnotations) ||
                isConstructorDescriptor(descriptor) && !parameterAnnotations.isEmpty();

        if (!isDIRelevant && parameterAnnotations.isEmpty()) {
            return List.of();
        }

        // Parse parameter types from descriptor
        List<String> paramTypes = DescriptorParser.parseParameterTypes(descriptor);

        if (paramTypes.isEmpty()) {
            return List.of();
        }

        // Build ParameterNode for each parameter
        List<ParameterNode> params = new ArrayList<>();
        for (int i = 0; i < paramTypes.size(); i++) {
            String type = paramTypes.get(i);
            Set<String> annotations = parameterAnnotations.getOrDefault(i, Set.of());
            params.add(new ParameterNode(i, type, annotations));
        }

        return params;
    }

    /**
     * Checks if method annotations indicate @Inject.
     */
    private boolean isInjectAnnotated(Set<String> annotations) {
        return annotations.contains("javax.inject.Inject") ||
                annotations.contains("jakarta.inject.Inject") ||
                annotations.contains("com.google.inject.Inject");
    }

    /**
     * Checks if method annotations indicate @Provides.
     */
    private boolean isProvidesAnnotated(Set<String> annotations) {
        return annotations.contains("com.google.inject.Provides");
    }

    /**
     * Checks if this could be a constructor based on descriptor pattern.
     * Note: We don't have the method name here, so this is a heuristic.
     */
    private boolean isConstructorDescriptor(String descriptor) {
        // Constructors return void
        return descriptor != null && descriptor.endsWith(")V");
    }

    /**
     * Converts a type descriptor to a class name.
     * E.g., "Ljavax/inject/Singleton;" -> "javax.inject.Singleton"
     */
    private String descriptorToClassName(String descriptor) {
        if (descriptor == null) return null;
        if (descriptor.startsWith("L") && descriptor.endsWith(";")) {
            return descriptor.substring(1, descriptor.length() - 1).replace('/', '.');
        }
        return descriptor.replace('/', '.');
    }

    /**
     * Builds the ClassNode from the visited data.
     *
     * @param isProjectClass Whether this is a project class (vs dependency)
     */
    public ClassNode buildClassNode(boolean isProjectClass) {
        return ClassNode.builder()
                .fqn(className)
                .superclass(superclass)
                .interfaces(interfaces)
                .methods(methods)
                .fields(fields)
                .annotations(annotations)
                .isProjectClass(isProjectClass)
                .isInterface(isInterface)
                .isAbstract(isAbstract)
                .isEnum(isEnum)
                .sourceFile(sourceFile)
                .build();
    }

    /**
     * Returns the fully qualified class name.
     */
    public String getClassName() {
        return className;
    }
}
