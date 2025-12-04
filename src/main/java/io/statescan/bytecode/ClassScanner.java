package io.statescan.bytecode;

import io.statescan.model.ClassInfo;
import io.statescan.model.MethodInfo;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ClassVisitor that scans a class and builds a ClassInfo model.
 * <p>
 * For each method, creates an ActorTrackingVisitor to extract the actors
 * (entities that receive method calls).
 */
public class ClassScanner extends ClassVisitor {

    private String className;
    private boolean isInterface;
    private boolean isAbstract;
    private String superClass;
    private Set<String> implementedInterfaces = new HashSet<>();
    private final Map<String, String> fields = new HashMap<>();     // name -> type FQN
    private final Map<String, MethodInfo> methods = new HashMap<>(); // key -> MethodInfo

    public ClassScanner() {
        super(Opcodes.ASM9);
    }

    @Override
    public void visit(int version, int access, String name, String signature,
                      String superName, String[] interfaces) {
        this.className = DescriptorParser.toFqn(name);
        this.isInterface = (access & Opcodes.ACC_INTERFACE) != 0;
        this.isAbstract = (access & Opcodes.ACC_ABSTRACT) != 0;
        
        // Convert superName to FQN (null for java/lang/Object itself)
        if (superName != null && !superName.equals("java/lang/Object")) {
            this.superClass = DescriptorParser.toFqn(superName);
        }
        
        // Convert interface internal names to FQNs
        if (interfaces != null) {
            Arrays.stream(interfaces)
                .map(DescriptorParser::toFqn)
                .forEach(implementedInterfaces::add);
        }

        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public FieldVisitor visitField(int access, String name, String descriptor,
                                    String signature, Object value) {
        String typeFqn = DescriptorParser.parseFieldType(descriptor);
        fields.put(name, typeFqn);

        return super.visitField(access, name, descriptor, signature, value);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor,
                                      String signature, String[] exceptions) {
        boolean isStatic = (access & Opcodes.ACC_STATIC) != 0;
        boolean isPublic = (access & Opcodes.ACC_PUBLIC) != 0;
        boolean isAbstract = (access & Opcodes.ACC_ABSTRACT) != 0;
        boolean isNative = (access & Opcodes.ACC_NATIVE) != 0;

        // Skip abstract and native methods - they have no body to analyze
        if (isAbstract || isNative) {
            // Still record the method but with empty actors and invocations
            MethodInfo methodInfo = new MethodInfo(name, descriptor, java.util.Set.of(), java.util.List.of(), isStatic, isPublic);
            methods.put(name + descriptor, methodInfo);
            return super.visitMethod(access, name, descriptor, signature, exceptions);
        }

        List<String> paramTypes = DescriptorParser.parseParameterTypes(descriptor);

        // Create the actor tracking visitor
        ActorTrackingVisitor actorVisitor = new ActorTrackingVisitor(
            className, fields, paramTypes, isStatic
        );

        // Return a wrapper that builds MethodInfo at visitEnd
        final String methodName = name;
        final String methodDescriptor = descriptor;
        final boolean methodIsStatic = isStatic;
        final boolean methodIsPublic = isPublic;

        return new MethodVisitor(Opcodes.ASM9, actorVisitor) {
            @Override
            public void visitEnd() {
                super.visitEnd();

                MethodInfo methodInfo = new MethodInfo(
                    methodName,
                    methodDescriptor,
                    actorVisitor.getActors(),
                    actorVisitor.getInvocations(),
                    methodIsStatic,
                    methodIsPublic
                );

                methods.put(methodName + methodDescriptor, methodInfo);
            }
        };
    }

    /**
     * Builds and returns the ClassInfo for this scanned class.
     *
     * @return The ClassInfo, or null if no class was visited
     */
    public ClassInfo buildClassInfo() {
        if (className == null) {
            return null;
        }
        return new ClassInfo(
            className, 
            methods, 
            fields, 
            isInterface, 
            isAbstract, 
            superClass, 
            implementedInterfaces
        );
    }
}
