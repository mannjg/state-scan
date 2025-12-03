package io.statescan.bytecode;

import io.statescan.graph.FieldRef;
import io.statescan.graph.MethodRef;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.HashSet;
import java.util.Set;

/**
 * ASM MethodVisitor that extracts method invocations and field accesses.
 */
public class MethodVisitorAdapter extends MethodVisitor {

    private final Set<MethodRef> invocations = new HashSet<>();
    private final Set<FieldRef> fieldAccesses = new HashSet<>();

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
}
