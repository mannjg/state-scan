package io.statescan.output;

import io.statescan.bytecode.DescriptorParser;
import io.statescan.model.*;

import java.io.PrintStream;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Outputs callgraph information in various formats.
 */
public class CallGraphOutput {
    private final CallGraph callGraph;
    private final ScanResult scanResult;
    private final PrintStream out;
    private final boolean useColor;

    // ANSI color codes
    private static final String RESET = "\u001B[0m";
    private static final String CYAN = "\u001B[36m";
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String MAGENTA = "\u001B[35m";
    private static final String DIM = "\u001B[2m";

    public CallGraphOutput(CallGraph callGraph, ScanResult scanResult) {
        this(callGraph, scanResult, System.out, true);
    }

    public CallGraphOutput(CallGraph callGraph, ScanResult scanResult, PrintStream out, boolean useColor) {
        this.callGraph = callGraph;
        this.scanResult = scanResult;
        this.out = out;
        this.useColor = useColor;
    }

    /**
     * Print callgraph summary statistics.
     */
    public void printSummary() {
        out.println("=== CALLGRAPH SUMMARY ===");
        out.println("Total edges: " + callGraph.edgeCount());
        out.println("Total methods: " + callGraph.methodCount());
        out.println("Root methods (entry points): " + callGraph.rootMethods().size());
        out.println("Leaf methods (terminal points): " + callGraph.leafMethods().size());
        out.println("Isolated methods (both root and leaf): " + callGraph.getIsolatedMethods().size());
        out.println();
    }

    /**
     * Print root methods (entry points).
     */
    public void printRootMethods() {
        out.println("=== ROOT METHODS (Entry Points) ===");
        List<MethodRef> sorted = callGraph.rootMethods().stream()
            .sorted(Comparator.comparing(MethodRef::key))
            .collect(Collectors.toList());

        for (MethodRef method : sorted) {
            out.println(formatMethodRef(method));
        }
        out.println();
    }

    /**
     * Print leaf methods (terminal points).
     */
    public void printLeafMethods() {
        out.println("=== LEAF METHODS (Terminal Points) ===");
        List<MethodRef> sorted = callGraph.leafMethods().stream()
            .sorted(Comparator.comparing(MethodRef::key))
            .collect(Collectors.toList());

        for (MethodRef method : sorted) {
            out.println(formatMethodRef(method));
        }
        out.println();
    }

    /**
     * Print call tree starting from root classes/methods.
     */
    public void printCallTree() {
        out.println("=== CALL GRAPH ===");

        // Group root methods by class, only those with outgoing calls
        Map<String, List<MethodRef>> rootsByClass = callGraph.rootMethods().stream()
            .filter(this::hasOutgoingCalls)
            .sorted(Comparator.comparing(MethodRef::key))
            .collect(Collectors.groupingBy(MethodRef::classFqn, TreeMap::new, Collectors.toList()));

        for (Map.Entry<String, List<MethodRef>> entry : rootsByClass.entrySet()) {
            out.println(color(CYAN, entry.getKey()) + ":");
            for (MethodRef rootMethod : entry.getValue()) {
                out.println("  " + color(GREEN, rootMethod.methodName()) + ":");
                printCallTreeRecursive(rootMethod, 4, new HashSet<>());
            }
        }
        out.println();
    }

    private void printCallTreeRecursive(MethodRef method, int indent, Set<String> visited) {
        if (!visited.add(method.key())) {
            return;  // Already being visited in this path
        }

        Set<CallEdge> callees = callGraph.getCallees(method);
        if (callees == null || callees.isEmpty()) {
            visited.remove(method.key());
            return;
        }

        // Group edges by display key (actorType + callee key) for leaf consolidation
        Map<String, List<CallEdge>> grouped = new LinkedHashMap<>();
        callees.stream()
            .sorted(Comparator.comparing(e -> e.callee().key()))
            .forEach(edge -> {
                String key = formatReceiverType(edge.receiver()) + "|" + edge.callee().key();
                grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(edge);
            });

        for (List<CallEdge> edges : grouped.values()) {
            CallEdge representative = edges.get(0);
            MethodRef callee = representative.callee();
            String actorType = formatReceiverType(representative.receiver());
            String calleeName = callee.classFqn() + "." + callee.methodName();
            int count = edges.size();

            boolean isCycle = visited.contains(callee.key());
            Set<CallEdge> grandchildren = callGraph.getCallees(callee);
            boolean isLeaf = isCycle || grandchildren == null || grandchildren.isEmpty();

            if (isLeaf) {
                // LEAF: consolidate with count, no colon, no recursion
                String suffix = "";
                if (count > 1) {
                    suffix = " (×" + count + ")";
                }
                if (isCycle) {
                    suffix += " (cycle)";
                }
                out.println(spaces(indent) + color(YELLOW, actorType) + " " + calleeName + color(DIM, suffix));
            } else {
                // NON-LEAF: print once with colon, recurse (no count - only roll-up for leaves)
                out.println(spaces(indent) + color(YELLOW, actorType) + " " + calleeName + ":");
                printCallTreeRecursive(callee, indent + 2, visited);
            }
        }

        visited.remove(method.key());  // Backtrack for other paths
    }

    private boolean hasOutgoingCalls(MethodRef method) {
        Set<CallEdge> callees = callGraph.getCallees(method);
        return callees != null && !callees.isEmpty();
    }

    private String formatReceiverType(ArgumentRef receiver) {
        if (receiver == null) return "STATIC";
        if (receiver instanceof ArgumentRef.ActorArg a) return a.actorType().toString();
        if (receiver instanceof ArgumentRef.ThisArg) return "THIS";
        if (receiver instanceof ArgumentRef.ComputedArg) return "COMPUTED";
        if (receiver instanceof ArgumentRef.LiteralArg) return "LITERAL";
        return "?";
    }

    private String spaces(int count) {
        return " ".repeat(count);
    }

    /**
     * Print type narrowings.
     */
    public void printTypeNarrowings() {
        if (callGraph.typeContexts().isEmpty()) {
            out.println("=== TYPE NARROWINGS ===");
            out.println("(none detected)");
            out.println();
            return;
        }

        out.println("=== TYPE NARROWINGS ===");

        for (Map.Entry<String, List<TypeContext>> entry : callGraph.typeContexts().entrySet()) {
            String methodKey = entry.getKey();
            List<TypeContext> contexts = entry.getValue();

            out.println(color(CYAN, methodKey) + ":");

            for (TypeContext ctx : contexts) {
                String callPath = String.join(" -> ", ctx.callPath());
                out.println("  via " + color(DIM, callPath) + ":");

                for (Map.Entry<Integer, Set<String>> narrowing : ctx.parameterTypes().entrySet()) {
                    int paramIdx = narrowing.getKey();
                    Set<String> types = narrowing.getValue();
                    String typeStr = types.stream()
                        .map(DescriptorParser::simpleName)
                        .collect(Collectors.joining(", "));
                    out.println("    param[" + paramIdx + "] narrowed to: " + color(GREEN, typeStr));
                }
            }
        }
        out.println();
    }

    /**
     * Print parameter flow for a specific method.
     */
    public void printParameterFlow(MethodRef method) {
        Set<CallEdge> callers = callGraph.getCallers(method);
        if (callers.isEmpty()) {
            out.println("No incoming calls to " + method.key());
            return;
        }

        out.println("Parameter flow into " + color(CYAN, method.key()) + ":");

        for (CallEdge edge : callers) {
            out.println("  from " + color(DIM, edge.caller().key()) + ":");
            for (Map.Entry<Integer, ArgumentRef> flow : edge.parameterFlow().entrySet()) {
                out.println("    param[" + flow.getKey() + "] <- " + formatArgRef(flow.getValue()));
            }
        }
    }

    /**
     * Print full callgraph output.
     */
    public void printFull() {
        printSummary();
        printCallTree();
    }

    // Formatting helpers

    private String formatMethodRef(MethodRef ref) {
        String simpleName = DescriptorParser.simpleName(ref.classFqn());
        return color(CYAN, simpleName) + "." + color(GREEN, ref.methodName()) +
               color(DIM, formatDescriptor(ref.descriptor()));
    }

    private String formatMethodName(String methodKey) {
        int parenIdx = methodKey.indexOf('(');
        if (parenIdx > 0) {
            String name = methodKey.substring(0, parenIdx);
            String desc = methodKey.substring(parenIdx);
            return color(GREEN, name) + color(DIM, formatDescriptor(desc));
        }
        return color(GREEN, methodKey);
    }

    private String formatCallEdge(CallEdge edge) {
        StringBuilder sb = new StringBuilder();
        sb.append("-> ");
        sb.append(color(CYAN, DescriptorParser.simpleName(edge.callee().classFqn())));
        sb.append(".");
        sb.append(color(GREEN, edge.callee().methodName()));
        sb.append(color(DIM, formatDescriptor(edge.callee().descriptor())));

        // Show parameter flow summary
        if (!edge.parameterFlow().isEmpty()) {
            sb.append(" [");
            List<String> flows = new ArrayList<>();
            for (Map.Entry<Integer, ArgumentRef> flow : edge.parameterFlow().entrySet()) {
                flows.add(shortArgRef(flow.getValue()));
            }
            sb.append(String.join(", ", flows));
            sb.append("]");
        }

        return sb.toString();
    }

    private String formatArgRef(ArgumentRef ref) {
        if (ref instanceof ArgumentRef.ActorArg a) {
            return color(YELLOW, a.actorType().toString()) + " " + a.name() +
                   " (" + color(DIM, DescriptorParser.simpleName(a.typeFqn())) + ")";
        } else if (ref instanceof ArgumentRef.LiteralArg l) {
            return color(MAGENTA, "literal") + " " + l.value() +
                   " (" + color(DIM, l.typeFqn()) + ")";
        } else if (ref instanceof ArgumentRef.ComputedArg c) {
            return color(DIM, "computed") + " (" + DescriptorParser.simpleName(c.typeFqn()) + ")";
        } else if (ref instanceof ArgumentRef.ThisArg t) {
            return color(YELLOW, "this") + " (" + color(DIM, DescriptorParser.simpleName(t.typeFqn())) + ")";
        }
        return ref.toString();
    }

    private String shortArgRef(ArgumentRef ref) {
        if (ref instanceof ArgumentRef.ActorArg a) {
            return a.actorType().toString().charAt(0) + ":" + a.name();
        } else if (ref instanceof ArgumentRef.LiteralArg l) {
            return "L:" + l.value();
        } else if (ref instanceof ArgumentRef.ComputedArg) {
            return "?";
        } else if (ref instanceof ArgumentRef.ThisArg) {
            return "this";
        }
        return "?";
    }

    private String formatDescriptor(String descriptor) {
        // Simplify descriptor for display
        if (descriptor == null || descriptor.isEmpty()) {
            return "";
        }
        // For now, just show simplified parameter count
        List<String> params = DescriptorParser.parseParameterTypes(descriptor);
        if (params.isEmpty()) {
            return "()";
        }
        return "(" + params.size() + " params)";
    }

    private String color(String code, String text) {
        if (useColor) {
            return code + text + RESET;
        }
        return text;
    }
}
