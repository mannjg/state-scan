package io.statescan.output;

import io.statescan.bytecode.DescriptorParser;
import io.statescan.model.*;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Outputs scan results to the console in a readable format.
 */
public class ConsoleOutput {

    private boolean showEmptyMethods = false;
    private boolean showOnlyPublicMethods = false;

    /**
     * Configure whether to show methods with no actors.
     */
    public ConsoleOutput showEmptyMethods(boolean show) {
        this.showEmptyMethods = show;
        return this;
    }

    /**
     * Configure whether to show only public methods.
     */
    public ConsoleOutput showOnlyPublicMethods(boolean show) {
        this.showOnlyPublicMethods = show;
        return this;
    }

    /**
     * Prints the scan result to stdout.
     */
    public void print(ScanResult result) {
        System.out.println("=".repeat(70));
        System.out.println("ACTOR SCAN RESULTS");
        System.out.println("=".repeat(70));
        System.out.println();
        System.out.printf("Classes: %d | Methods: %d | Actors: %d%n",
            result.classCount(), result.methodCount(), result.actorCount());
        System.out.println();

        // Sort classes by FQN
        List<ClassInfo> sortedClasses = result.allClasses()
            .sorted(Comparator.comparing(ClassInfo::fqn))
            .toList();

        for (ClassInfo classInfo : sortedClasses) {
            printClass(classInfo);
        }
    }

    private void printClass(ClassInfo classInfo) {
        // Get methods with actors (or all if showEmptyMethods)
        List<MethodInfo> methodsToShow = classInfo.methods().values().stream()
            .filter(m -> showEmptyMethods || !m.actors().isEmpty())
            .filter(m -> !showOnlyPublicMethods || m.isPublic())
            .filter(m -> !m.isStaticInitializer()) // Skip <clinit>
            .sorted(Comparator.comparing(MethodInfo::name))
            .toList();

        if (methodsToShow.isEmpty()) {
            return; // Skip classes with no methods to show
        }

        System.out.println("─".repeat(70));
        String classType = classInfo.isInterface() ? "Interface" : "Class";
        System.out.printf("%s: %s%n", classType, classInfo.fqn());
        System.out.println("─".repeat(70));

        for (MethodInfo method : methodsToShow) {
            printMethod(method);
        }

        System.out.println();
    }

    private void printMethod(MethodInfo method) {
        String visibility = method.isPublic() ? "public " : "";
        String staticMod = method.isStatic() ? "static " : "";
        String signature = formatMethodSignature(method);

        System.out.printf("  %s%s%s%n", visibility, staticMod, signature);

        if (method.actors().isEmpty()) {
            System.out.println("    (no actors)");
            return;
        }

        // Sort actors by type, then by name
        List<Actor> sortedActors = method.actors().stream()
            .sorted(Comparator
                .comparing(Actor::type)
                .thenComparing(Actor::name))
            .toList();

        for (Actor actor : sortedActors) {
            printActor(actor);
        }
    }

    private void printActor(Actor actor) {
        String typeLabel = switch (actor.type()) {
            case FIELD -> "FIELD";
            case PARAMETER -> "PARAM";
            case LOCAL -> "LOCAL";
            case STATIC_CLASS -> "STATIC";
            case NEW_OBJECT -> "NEW";
        };

        String methodsCalled = actor.methodsCalled().stream()
            .sorted()
            .collect(Collectors.joining(", "));

        // Shorten type FQN for readability
        String shortType = shortenType(actor.typeFqn());

        System.out.printf("    [%s] %s : %s%n", typeLabel, actor.name(), shortType);
        if (!methodsCalled.isEmpty()) {
            System.out.printf("           -> %s%n", methodsCalled);
        }
    }

    private String formatMethodSignature(MethodInfo method) {
        List<String> params = DescriptorParser.parseParameterTypes(method.descriptor());
        String returnType = DescriptorParser.parseReturnType(method.descriptor());

        String paramsStr = params.stream()
            .map(this::shortenType)
            .collect(Collectors.joining(", "));

        String name = method.isConstructor() ? "<init>" : method.name();

        return String.format("%s(%s) : %s", name, paramsStr, shortenType(returnType));
    }

    private String shortenType(String fqn) {
        if (fqn == null) {
            return "void";
        }
        // Keep java.lang types short
        if (fqn.startsWith("java.lang.") && !fqn.substring(10).contains(".")) {
            return fqn.substring(10);
        }
        // Keep primitives as-is
        if (DescriptorParser.isPrimitive(fqn)) {
            return fqn;
        }
        // For other types, return the FQN
        return fqn;
    }
}
