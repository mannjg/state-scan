package io.statescan.bytecode;

import io.statescan.model.Actor;
import io.statescan.model.ActorType;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.*;

/**
 * MethodVisitor that tracks which entities receive method calls.
 * Performs lightweight stack simulation to correlate receivers with invocations.
 * <p>
 * This tracks:
 * <ul>
 *   <li>Fields that have methods called on them (FIELD)</li>
 *   <li>Parameters that have methods called on them (PARAMETER)</li>
 *   <li>Local variables that have methods called on them (LOCAL)</li>
 *   <li>Static class references for static method calls (STATIC_CLASS)</li>
 *   <li>Newly created objects (NEW_OBJECT)</li>
 * </ul>
 */
public class ActorTrackingVisitor extends MethodVisitor {

    // Context
    private final String ownerClass;
    private final Map<String, String> classFields; // fieldName -> typeFqn
    private final List<String> parameterTypes;     // from method descriptor
    private final boolean isStaticMethod;
    private final int parameterCount;

    // Stack simulation - tracks what was most recently pushed
    // Simplified: only track top of stack for method receiver detection
    private record StackEntry(ActorType type, String name, String typeFqn) {}
    private Deque<StackEntry> stack = new ArrayDeque<>();

    // Local variable tracking
    private Map<Integer, String> localVarTypes = new HashMap<>();  // slot -> type FQN
    private Map<Integer, String> localVarNames = new HashMap<>();  // slot -> name (if available)

    // Track NEW instructions awaiting constructor call
    private String pendingNewType = null;

    // Results: accumulate method calls per actor
    private record ActorKey(ActorType type, String name, String typeFqn) {}
    private Map<ActorKey, Set<String>> actorMethodCalls = new LinkedHashMap<>();

    /**
     * Creates an ActorTrackingVisitor for analyzing a method.
     *
     * @param ownerClass     FQN of the class containing this method
     * @param classFields    Map of field name to type FQN for the containing class
     * @param parameterTypes List of parameter type FQNs (from method descriptor)
     * @param isStaticMethod Whether this is a static method
     */
    public ActorTrackingVisitor(
            String ownerClass,
            Map<String, String> classFields,
            List<String> parameterTypes,
            boolean isStaticMethod) {
        super(Opcodes.ASM9);
        this.ownerClass = ownerClass;
        this.classFields = classFields;
        this.parameterTypes = parameterTypes;
        this.isStaticMethod = isStaticMethod;
        this.parameterCount = parameterTypes.size();

        // Pre-populate local var types for parameters
        int slot = isStaticMethod ? 0 : 1; // slot 0 is 'this' for instance methods
        for (int i = 0; i < parameterTypes.size(); i++) {
            localVarTypes.put(slot, parameterTypes.get(i));
            localVarNames.put(slot, "param" + i);
            // double and long take 2 slots
            String type = parameterTypes.get(i);
            slot += ("double".equals(type) || "long".equals(type)) ? 2 : 1;
        }
    }

    @Override
    public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
        String typeFqn = DescriptorParser.parseFieldType(descriptor);
        String ownerFqn = DescriptorParser.toFqn(owner);

        if (opcode == Opcodes.GETFIELD) {
            // Instance field access - pop 'this' from stack, push field value
            stack.pollFirst(); // consume 'this' reference
            stack.push(new StackEntry(ActorType.FIELD, name, typeFqn));
        } else if (opcode == Opcodes.GETSTATIC) {
            // Static field access - push field value
            // For static fields, the "actor" is the field itself
            stack.push(new StackEntry(ActorType.FIELD, ownerFqn + "." + name, typeFqn));
        } else if (opcode == Opcodes.PUTFIELD || opcode == Opcodes.PUTSTATIC) {
            // Field assignment - consume from stack
            stack.pollFirst(); // value being stored
            if (opcode == Opcodes.PUTFIELD) {
                stack.pollFirst(); // object reference
            }
        }

        super.visitFieldInsn(opcode, owner, name, descriptor);
    }

    @Override
    public void visitVarInsn(int opcode, int varIndex) {
        if (opcode == Opcodes.ALOAD) {
            if (varIndex == 0 && !isStaticMethod) {
                // Loading 'this' - usually followed by GETFIELD
                stack.push(new StackEntry(null, "this", ownerClass));
            } else {
                // Loading a parameter or local variable
                int effectiveIndex = isStaticMethod ? varIndex : varIndex - 1;

                if (effectiveIndex >= 0 && effectiveIndex < parameterCount) {
                    // It's a parameter
                    String paramName = localVarNames.getOrDefault(varIndex, "param" + effectiveIndex);
                    String paramType = localVarTypes.getOrDefault(varIndex, "java.lang.Object");
                    stack.push(new StackEntry(ActorType.PARAMETER, paramName, paramType));
                } else {
                    // It's a local variable
                    String localName = localVarNames.getOrDefault(varIndex, "local" + varIndex);
                    String localType = localVarTypes.getOrDefault(varIndex, "java.lang.Object");
                    stack.push(new StackEntry(ActorType.LOCAL, localName, localType));
                }
            }
        } else if (opcode == Opcodes.ASTORE) {
            // Storing to local variable - pop and remember the type
            StackEntry entry = stack.pollFirst();
            if (entry != null && entry.typeFqn() != null) {
                localVarTypes.put(varIndex, entry.typeFqn());
                // Keep existing name if we have one, otherwise use the stored name
                if (!localVarNames.containsKey(varIndex) && entry.name() != null) {
                    // Don't inherit names like "this" or "param0"
                    if (entry.type() == ActorType.NEW_OBJECT) {
                        localVarNames.put(varIndex, entry.name());
                    }
                }
            }
        } else if (opcode >= Opcodes.ILOAD && opcode <= Opcodes.DLOAD) {
            // Primitive load - push a placeholder (primitives don't receive method calls)
            stack.push(new StackEntry(null, null, "primitive"));
        } else if (opcode >= Opcodes.ISTORE && opcode <= Opcodes.DSTORE) {
            // Primitive store - pop
            stack.pollFirst();
        }

        super.visitVarInsn(opcode, varIndex);
    }

    @Override
    public void visitTypeInsn(int opcode, String type) {
        if (opcode == Opcodes.NEW) {
            // NEW instruction - track the type being created
            String typeFqn = DescriptorParser.toFqn(type);
            pendingNewType = typeFqn;
            // Don't push yet - wait for DUP and constructor call
        } else if (opcode == Opcodes.CHECKCAST) {
            // Cast - update the type of top of stack
            StackEntry entry = stack.pollFirst();
            if (entry != null) {
                String newType = DescriptorParser.toFqn(type);
                stack.push(new StackEntry(entry.type(), entry.name(), newType));
            }
        }

        super.visitTypeInsn(opcode, type);
    }

    @Override
    public void visitInsn(int opcode) {
        if (opcode == Opcodes.DUP && pendingNewType != null) {
            // DUP after NEW - push the new object onto stack
            String simpleName = DescriptorParser.simpleName(pendingNewType);
            stack.push(new StackEntry(ActorType.NEW_OBJECT, simpleName, pendingNewType));
        } else if (opcode >= Opcodes.ICONST_M1 && opcode <= Opcodes.DCONST_1) {
            // Constant push
            stack.push(new StackEntry(null, null, "primitive"));
        } else if (opcode == Opcodes.ACONST_NULL) {
            stack.push(new StackEntry(null, null, "null"));
        } else if (opcode == Opcodes.ARRAYLENGTH) {
            stack.pollFirst(); // consume array ref
            stack.push(new StackEntry(null, null, "int"));
        } else if (opcode == Opcodes.POP) {
            stack.pollFirst();
        } else if (opcode == Opcodes.POP2) {
            stack.pollFirst();
            stack.pollFirst();
        } else if (opcode == Opcodes.DUP) {
            StackEntry top = stack.peekFirst();
            if (top != null) {
                stack.push(new StackEntry(top.type(), top.name(), top.typeFqn()));
            }
        } else if (opcode == Opcodes.SWAP) {
            StackEntry a = stack.pollFirst();
            StackEntry b = stack.pollFirst();
            if (a != null) stack.push(a);
            if (b != null) stack.push(b);
        } else if (opcode >= Opcodes.IADD && opcode <= Opcodes.LXOR) {
            // Binary operations - pop 2, push 1
            stack.pollFirst();
            stack.pollFirst();
            stack.push(new StackEntry(null, null, "primitive"));
        } else if (opcode >= Opcodes.INEG && opcode <= Opcodes.DNEG) {
            // Unary negation - pop 1, push 1
            stack.pollFirst();
            stack.push(new StackEntry(null, null, "primitive"));
        } else if (opcode >= Opcodes.I2L && opcode <= Opcodes.I2S) {
            // Type conversions - pop 1, push 1
            stack.pollFirst();
            stack.push(new StackEntry(null, null, "primitive"));
        } else if (opcode >= Opcodes.LCMP && opcode <= Opcodes.DCMPG) {
            // Comparisons - pop 2, push 1
            stack.pollFirst();
            stack.pollFirst();
            stack.push(new StackEntry(null, null, "int"));
        } else if (opcode == Opcodes.ATHROW || opcode >= Opcodes.IRETURN && opcode <= Opcodes.RETURN) {
            // Return or throw - clear stack
            stack.clear();
        }

        super.visitInsn(opcode);
    }

    @Override
    public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
        String ownerFqn = DescriptorParser.toFqn(owner);
        List<String> params = DescriptorParser.parseParameterTypes(descriptor);
        String returnType = DescriptorParser.parseReturnType(descriptor);

        // Pop arguments from stack
        for (int i = 0; i < params.size(); i++) {
            stack.pollFirst();
        }

        if (opcode == Opcodes.INVOKESTATIC) {
            // Static method call - no receiver on stack
            String simpleName = DescriptorParser.simpleName(ownerFqn);
            recordActorMethodCall(ActorType.STATIC_CLASS, simpleName, ownerFqn, name);

        } else if (opcode == Opcodes.INVOKESPECIAL && "<init>".equals(name)) {
            // Constructor call
            if (pendingNewType != null) {
                // This is a constructor call on a NEW object
                String simpleName = DescriptorParser.simpleName(pendingNewType);
                recordActorMethodCall(ActorType.NEW_OBJECT, simpleName, pendingNewType, "<init>");
                // Push the constructed object as result
                stack.push(new StackEntry(ActorType.NEW_OBJECT, simpleName, pendingNewType));
                pendingNewType = null;
            } else {
                // This is a super() or this() call - pop 'this'
                stack.pollFirst();
            }

        } else {
            // INVOKEVIRTUAL, INVOKEINTERFACE, or non-init INVOKESPECIAL
            // Pop the receiver and record the method call
            StackEntry receiver = stack.pollFirst();

            if (receiver != null && receiver.type() != null) {
                recordActorMethodCall(receiver.type(), receiver.name(), receiver.typeFqn(), name);
            }

            // Push the return value if non-void
            if (!"void".equals(returnType)) {
                stack.push(new StackEntry(null, null, returnType));
            }
        }

        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
    }

    @Override
    public void visitLdcInsn(Object value) {
        if (value instanceof String) {
            stack.push(new StackEntry(null, null, "java.lang.String"));
        } else if (value instanceof Integer) {
            stack.push(new StackEntry(null, null, "int"));
        } else if (value instanceof Long) {
            stack.push(new StackEntry(null, null, "long"));
        } else if (value instanceof Float) {
            stack.push(new StackEntry(null, null, "float"));
        } else if (value instanceof Double) {
            stack.push(new StackEntry(null, null, "double"));
        } else if (value instanceof org.objectweb.asm.Type) {
            stack.push(new StackEntry(null, null, "java.lang.Class"));
        } else {
            stack.push(new StackEntry(null, null, "unknown"));
        }

        super.visitLdcInsn(value);
    }

    @Override
    public void visitLocalVariable(String name, String descriptor, String signature,
                                   Label start, Label end, int index) {
        // Capture local variable names from debug info
        String type = DescriptorParser.parseFieldType(descriptor);
        localVarNames.put(index, name);
        localVarTypes.put(index, type);

        super.visitLocalVariable(name, descriptor, signature, start, end, index);
    }

    @Override
    public void visitIntInsn(int opcode, int operand) {
        if (opcode == Opcodes.BIPUSH || opcode == Opcodes.SIPUSH) {
            stack.push(new StackEntry(null, null, "int"));
        } else if (opcode == Opcodes.NEWARRAY) {
            stack.pollFirst(); // size
            stack.push(new StackEntry(null, null, "array"));
        }
        super.visitIntInsn(opcode, operand);
    }

    @Override
    public void visitMultiANewArrayInsn(String descriptor, int numDimensions) {
        for (int i = 0; i < numDimensions; i++) {
            stack.pollFirst(); // dimension sizes
        }
        stack.push(new StackEntry(null, null, DescriptorParser.parseFieldType(descriptor)));
        super.visitMultiANewArrayInsn(descriptor, numDimensions);
    }

    @Override
    public void visitJumpInsn(int opcode, Label label) {
        // Conditional jumps pop values
        if (opcode >= Opcodes.IFEQ && opcode <= Opcodes.IFLE) {
            stack.pollFirst();
        } else if (opcode >= Opcodes.IF_ICMPEQ && opcode <= Opcodes.IF_ACMPNE) {
            stack.pollFirst();
            stack.pollFirst();
        } else if (opcode == Opcodes.IFNULL || opcode == Opcodes.IFNONNULL) {
            stack.pollFirst();
        }
        // GOTO doesn't pop anything
        super.visitJumpInsn(opcode, label);
    }

    @Override
    public void visitIincInsn(int varIndex, int increment) {
        // IINC doesn't affect the stack
        super.visitIincInsn(varIndex, increment);
    }

    private void recordActorMethodCall(ActorType type, String name, String typeFqn, String methodName) {
        ActorKey key = new ActorKey(type, name, typeFqn);
        actorMethodCalls.computeIfAbsent(key, k -> new LinkedHashSet<>()).add(methodName);
    }

    /**
     * Returns the set of actors found in this method.
     */
    public Set<Actor> getActors() {
        Set<Actor> actors = new LinkedHashSet<>();
        for (Map.Entry<ActorKey, Set<String>> entry : actorMethodCalls.entrySet()) {
            ActorKey key = entry.getKey();
            actors.add(new Actor(key.type(), key.name(), key.typeFqn(), entry.getValue()));
        }
        return actors;
    }
}
