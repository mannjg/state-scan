package io.statescan.bytecode;

import io.statescan.model.*;
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
    // Enhanced to support argument tracking for callgraph
    private record StackEntry(ActorType type, String name, String typeFqn, Object literalValue, boolean isThis) {
        // Convenience constructors
        static StackEntry actor(ActorType type, String name, String typeFqn) {
            return new StackEntry(type, name, typeFqn, null, false);
        }

        static StackEntry computed(String typeFqn) {
            return new StackEntry(null, null, typeFqn, null, false);
        }

        static StackEntry literal(Object value, String typeFqn) {
            return new StackEntry(null, null, typeFqn, value, false);
        }

        static StackEntry thisRef(String typeFqn) {
            return new StackEntry(null, "this", typeFqn, null, true);
        }

        /**
         * Convert this stack entry to an ArgumentRef.
         */
        ArgumentRef toArgumentRef() {
            if (isThis) {
                return new ArgumentRef.ThisArg(typeFqn);
            } else if (literalValue != null) {
                return new ArgumentRef.LiteralArg(literalValue, typeFqn);
            } else if (type != null) {
                return new ArgumentRef.ActorArg(type, name, typeFqn);
            }
            return new ArgumentRef.ComputedArg(typeFqn);
        }
    }
    private Deque<StackEntry> stack = new ArrayDeque<>();

    // Local variable tracking
    private Map<Integer, String> localVarTypes = new HashMap<>();  // slot -> type FQN
    private Map<Integer, String> localVarNames = new HashMap<>();  // slot -> name (if available)

    // Track NEW instructions awaiting constructor call
    private String pendingNewType = null;

    // Results: accumulate method calls per actor
    private record ActorKey(ActorType type, String name, String typeFqn) {}
    private Map<ActorKey, Set<String>> actorMethodCalls = new LinkedHashMap<>();

    // Invocation tracking for callgraph
    private final List<MethodInvocation> invocations = new ArrayList<>();
    private int bytecodeOffset = 0;

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
            stack.push(StackEntry.actor(ActorType.FIELD, name, typeFqn));
        } else if (opcode == Opcodes.GETSTATIC) {
            // Static field access - push field value
            // For static fields, the "actor" is the field itself
            stack.push(StackEntry.actor(ActorType.FIELD, ownerFqn + "." + name, typeFqn));
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
                // Loading 'this' - track as ThisArg for callgraph
                stack.push(StackEntry.thisRef(ownerClass));
            } else {
                // Loading a parameter or local variable
                int effectiveIndex = isStaticMethod ? varIndex : varIndex - 1;

                if (effectiveIndex >= 0 && effectiveIndex < parameterCount) {
                    // It's a parameter
                    String paramName = localVarNames.getOrDefault(varIndex, "param" + effectiveIndex);
                    String paramType = localVarTypes.getOrDefault(varIndex, "java.lang.Object");
                    stack.push(StackEntry.actor(ActorType.PARAMETER, paramName, paramType));
                } else {
                    // It's a local variable
                    String localName = localVarNames.getOrDefault(varIndex, "local" + varIndex);
                    String localType = localVarTypes.getOrDefault(varIndex, "java.lang.Object");
                    stack.push(StackEntry.actor(ActorType.LOCAL, localName, localType));
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
            stack.push(StackEntry.computed("primitive"));
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
                stack.push(new StackEntry(entry.type(), entry.name(), newType, entry.literalValue(), entry.isThis()));
            }
        } else if (opcode == Opcodes.INSTANCEOF) {
            // INSTANCEOF - pop ref, push int
            stack.pollFirst();
            stack.push(StackEntry.computed("int"));
        } else if (opcode == Opcodes.ANEWARRAY) {
            // ANEWARRAY - pop size, push array
            stack.pollFirst();
            String arrayType = DescriptorParser.toFqn(type) + "[]";
            stack.push(StackEntry.computed(arrayType));
        }

        super.visitTypeInsn(opcode, type);
    }

    @Override
    public void visitInsn(int opcode) {
        if (opcode == Opcodes.DUP && pendingNewType != null) {
            // DUP after NEW - push the new object onto stack
            String simpleName = DescriptorParser.simpleName(pendingNewType);
            stack.push(StackEntry.actor(ActorType.NEW_OBJECT, simpleName, pendingNewType));
        } else if (opcode >= Opcodes.ICONST_M1 && opcode <= Opcodes.DCONST_1) {
            // Constant push - track the literal value
            Object value = getConstantValue(opcode);
            stack.push(StackEntry.literal(value, "primitive"));
        } else if (opcode == Opcodes.ACONST_NULL) {
            stack.push(StackEntry.literal(null, "null"));
        } else if (opcode == Opcodes.ARRAYLENGTH) {
            stack.pollFirst(); // consume array ref
            stack.push(StackEntry.computed("int"));
        } else if (opcode == Opcodes.POP) {
            stack.pollFirst();
        } else if (opcode == Opcodes.POP2) {
            stack.pollFirst();
            stack.pollFirst();
        } else if (opcode == Opcodes.DUP) {
            StackEntry top = stack.peekFirst();
            if (top != null) {
                stack.push(new StackEntry(top.type(), top.name(), top.typeFqn(), top.literalValue(), top.isThis()));
            }
        } else if (opcode == Opcodes.DUP_X1) {
            // DUP_X1: ..., v2, v1 -> ..., v1, v2, v1
            StackEntry v1 = stack.pollFirst();
            StackEntry v2 = stack.pollFirst();
            if (v1 != null) stack.push(v1);
            if (v2 != null) stack.push(v2);
            if (v1 != null) stack.push(v1);
        } else if (opcode == Opcodes.DUP_X2) {
            // DUP_X2: ..., v3, v2, v1 -> ..., v1, v3, v2, v1
            StackEntry v1 = stack.pollFirst();
            StackEntry v2 = stack.pollFirst();
            StackEntry v3 = stack.pollFirst();
            if (v1 != null) stack.push(v1);
            if (v3 != null) stack.push(v3);
            if (v2 != null) stack.push(v2);
            if (v1 != null) stack.push(v1);
        } else if (opcode == Opcodes.DUP2) {
            // DUP2: ..., v2, v1 -> ..., v2, v1, v2, v1
            StackEntry v1 = stack.peekFirst();
            Iterator<StackEntry> it = stack.iterator();
            if (it.hasNext()) it.next();
            StackEntry v2 = it.hasNext() ? it.next() : null;
            if (v2 != null) stack.push(new StackEntry(v2.type(), v2.name(), v2.typeFqn(), v2.literalValue(), v2.isThis()));
            if (v1 != null) stack.push(new StackEntry(v1.type(), v1.name(), v1.typeFqn(), v1.literalValue(), v1.isThis()));
        } else if (opcode == Opcodes.SWAP) {
            StackEntry a = stack.pollFirst();
            StackEntry b = stack.pollFirst();
            if (a != null) stack.push(a);
            if (b != null) stack.push(b);
        } else if (opcode >= Opcodes.IADD && opcode <= Opcodes.LXOR) {
            // Binary operations - pop 2, push 1
            stack.pollFirst();
            stack.pollFirst();
            stack.push(StackEntry.computed("primitive"));
        } else if (opcode >= Opcodes.INEG && opcode <= Opcodes.DNEG) {
            // Unary negation - pop 1, push 1
            stack.pollFirst();
            stack.push(StackEntry.computed("primitive"));
        } else if (opcode >= Opcodes.I2L && opcode <= Opcodes.I2S) {
            // Type conversions - pop 1, push 1
            stack.pollFirst();
            stack.push(StackEntry.computed("primitive"));
        } else if (opcode >= Opcodes.LCMP && opcode <= Opcodes.DCMPG) {
            // Comparisons - pop 2, push 1
            stack.pollFirst();
            stack.pollFirst();
            stack.push(StackEntry.computed("int"));
        } else if (opcode == Opcodes.ATHROW || opcode >= Opcodes.IRETURN && opcode <= Opcodes.RETURN) {
            // Return or throw - clear stack
            stack.clear();
        } else if (opcode >= Opcodes.IALOAD && opcode <= Opcodes.SALOAD) {
            // Array load - pop index and array, push element
            stack.pollFirst(); // index
            stack.pollFirst(); // array
            stack.push(StackEntry.computed("element"));
        } else if (opcode >= Opcodes.IASTORE && opcode <= Opcodes.SASTORE) {
            // Array store - pop value, index, array
            stack.pollFirst();
            stack.pollFirst();
            stack.pollFirst();
        }

        super.visitInsn(opcode);
    }

    private Object getConstantValue(int opcode) {
        if (opcode == Opcodes.ICONST_M1) return -1;
        if (opcode == Opcodes.ICONST_0) return 0;
        if (opcode == Opcodes.ICONST_1) return 1;
        if (opcode == Opcodes.ICONST_2) return 2;
        if (opcode == Opcodes.ICONST_3) return 3;
        if (opcode == Opcodes.ICONST_4) return 4;
        if (opcode == Opcodes.ICONST_5) return 5;
        if (opcode == Opcodes.LCONST_0) return 0L;
        if (opcode == Opcodes.LCONST_1) return 1L;
        if (opcode == Opcodes.FCONST_0) return 0.0f;
        if (opcode == Opcodes.FCONST_1) return 1.0f;
        if (opcode == Opcodes.FCONST_2) return 2.0f;
        if (opcode == Opcodes.DCONST_0) return 0.0d;
        if (opcode == Opcodes.DCONST_1) return 1.0d;
        return null;
    }

    @Override
    public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
        String ownerFqn = DescriptorParser.toFqn(owner);
        List<String> paramTypes = DescriptorParser.parseParameterTypes(descriptor);
        String returnType = DescriptorParser.parseReturnType(descriptor);

        // Determine invoke type
        InvokeType invokeType;
        if (opcode == Opcodes.INVOKESTATIC) {
            invokeType = InvokeType.STATIC;
        } else if (opcode == Opcodes.INVOKEINTERFACE) {
            invokeType = InvokeType.INTERFACE;
        } else if (opcode == Opcodes.INVOKESPECIAL) {
            invokeType = InvokeType.SPECIAL;
        } else {
            invokeType = InvokeType.VIRTUAL;
        }

        // Pop arguments from stack in REVERSE order (last arg is on top)
        List<ArgumentRef> arguments = new ArrayList<>();
        for (int i = paramTypes.size() - 1; i >= 0; i--) {
            StackEntry argEntry = stack.pollFirst();
            if (argEntry != null) {
                arguments.add(0, argEntry.toArgumentRef());  // Insert at front
            } else {
                arguments.add(0, new ArgumentRef.ComputedArg(paramTypes.get(i)));
            }
        }

        ArgumentRef receiver = null;

        if (opcode == Opcodes.INVOKESTATIC) {
            // Static method call - no receiver on stack
            String simpleName = DescriptorParser.simpleName(ownerFqn);
            recordActorMethodCall(ActorType.STATIC_CLASS, simpleName, ownerFqn, name);
            // receiver stays null for static calls

        } else if (opcode == Opcodes.INVOKESPECIAL && "<init>".equals(name)) {
            // Constructor call
            if (pendingNewType != null) {
                // This is a constructor call on a NEW object
                String simpleName = DescriptorParser.simpleName(pendingNewType);
                recordActorMethodCall(ActorType.NEW_OBJECT, simpleName, pendingNewType, "<init>");
                receiver = new ArgumentRef.ActorArg(ActorType.NEW_OBJECT, simpleName, pendingNewType);
                // Push the constructed object as result
                stack.push(StackEntry.actor(ActorType.NEW_OBJECT, simpleName, pendingNewType));
                // Use pendingNewType as target for the invocation
                ownerFqn = pendingNewType;
                pendingNewType = null;
            } else {
                // This is a super() or this() call - pop 'this'
                StackEntry thisEntry = stack.pollFirst();
                if (thisEntry != null) {
                    receiver = thisEntry.toArgumentRef();
                }
            }

        } else {
            // INVOKEVIRTUAL, INVOKEINTERFACE, or non-init INVOKESPECIAL
            // Pop the receiver and record the method call
            StackEntry receiverEntry = stack.pollFirst();

            if (receiverEntry != null) {
                receiver = receiverEntry.toArgumentRef();
                if (receiverEntry.type() != null) {
                    recordActorMethodCall(receiverEntry.type(), receiverEntry.name(), receiverEntry.typeFqn(), name);
                }
            }

            // Push the return value if non-void
            if (!"void".equals(returnType)) {
                stack.push(StackEntry.computed(returnType));
            }
        }

        // Record the full invocation for callgraph
        invocations.add(new MethodInvocation(
            ownerFqn,
            name,
            descriptor,
            invokeType,
            receiver,
            arguments,
            bytecodeOffset++
        ));

        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
    }

    @Override
    public void visitLdcInsn(Object value) {
        if (value instanceof String s) {
            stack.push(StackEntry.literal(s, "java.lang.String"));
        } else if (value instanceof Integer i) {
            stack.push(StackEntry.literal(i, "int"));
        } else if (value instanceof Long l) {
            stack.push(StackEntry.literal(l, "long"));
        } else if (value instanceof Float f) {
            stack.push(StackEntry.literal(f, "float"));
        } else if (value instanceof Double d) {
            stack.push(StackEntry.literal(d, "double"));
        } else if (value instanceof org.objectweb.asm.Type t) {
            stack.push(StackEntry.literal(t.getClassName(), "java.lang.Class"));
        } else {
            stack.push(StackEntry.computed("unknown"));
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
            stack.push(StackEntry.literal(operand, "int"));
        } else if (opcode == Opcodes.NEWARRAY) {
            stack.pollFirst(); // size
            stack.push(StackEntry.computed("array"));
        }
        super.visitIntInsn(opcode, operand);
    }

    @Override
    public void visitMultiANewArrayInsn(String descriptor, int numDimensions) {
        for (int i = 0; i < numDimensions; i++) {
            stack.pollFirst(); // dimension sizes
        }
        stack.push(StackEntry.computed(DescriptorParser.parseFieldType(descriptor)));
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

    /**
     * Returns the list of method invocations found in this method.
     * Used for building the callgraph.
     */
    public List<MethodInvocation> getInvocations() {
        return List.copyOf(invocations);
    }
}
