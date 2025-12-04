package io.statescan.bytecode;

import io.statescan.graph.FieldRef;
import io.statescan.graph.MethodRef;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.util.HashSet;
import java.util.Set;

/**
 * ASM MethodVisitor that extracts method invocations, field accesses, and class constant references.
 * <p>
 * Class constants are captured from LDC instructions (e.g., {@code SomeClass.class} in source code).
 * This is critical for extracting Guice binding information like {@code bind(Interface.class).to(Impl.class)}.
 */
public class MethodVisitorAdapter extends MethodVisitor {

    private final Set<MethodRef> invocations = new HashSet<>();
    private final Set<FieldRef> fieldAccesses = new HashSet<>();
    private final Set<String> classConstantRefs = new HashSet<>();

    public MethodVisitorAdapter() {
        super(Opcodes.ASM9);
    }

    public MethodVisitorAdapter(MethodVisitor mv) {
        super(Opcodes.ASM9, mv);
    }

    @Override
    public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
        // Record the method invocation
        String ownerFqn = owner.replace('/', '.');
        invocations.add(new MethodRef(ownerFqn, name, descriptor));

        if (mv != null) {
            mv.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
        }
    }

    @Override
    public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
        // Record field access
        String ownerFqn = owner.replace('/', '.');
        fieldAccesses.add(new FieldRef(ownerFqn, name, descriptor));

        if (mv != null) {
            mv.visitFieldInsn(opcode, owner, name, descriptor);
        }
    }

    @Override
    public void visitLdcInsn(Object value) {
        // Capture class constants loaded via LDC instruction
        // This captures SomeClass.class references in bytecode
        if (value instanceof Type type && type.getSort() == Type.OBJECT) {
            String className = type.getClassName();
            classConstantRefs.add(className);
        }

        if (mv != null) {
            mv.visitLdcInsn(value);
        }
    }

    @Override
    public void visitInvokeDynamicInsn(String name, String descriptor,
            org.objectweb.asm.Handle bootstrapMethodHandle, Object... bootstrapMethodArguments) {
        // Handle lambda and method reference invocations
        // The bootstrapMethodHandle may contain useful info about the target method

        if (mv != null) {
            mv.visitInvokeDynamicInsn(name, descriptor, bootstrapMethodHandle, bootstrapMethodArguments);
        }
    }

    /**
     * Returns all method invocations found in this method.
     */
    public Set<MethodRef> getInvocations() {
        return Set.copyOf(invocations);
    }

    /**
     * Returns all field accesses found in this method.
     */
    public Set<FieldRef> getFieldAccesses() {
        return Set.copyOf(fieldAccesses);
    }

    /**
     * Returns all class constants referenced in this method via LDC instructions.
     * These are class literals like {@code SomeClass.class} in source code.
     * <p>
     * Useful for extracting DI binding information where classes are passed as arguments,
     * e.g., {@code bind(Interface.class).to(Implementation.class)}.
     */
    public Set<String> getClassConstantRefs() {
        return Set.copyOf(classConstantRefs);
    }
}
