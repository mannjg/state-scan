package io.statescan.output;

import io.statescan.model.*;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Outputs scan results to the console in a simple format.
 */
public class ConsoleOutput {

    private boolean showEmptyMethods = false;
    private boolean showOnlyPublicMethods = false;
    private ScanResult scanResult;
    private Map<String, Set<String>> implementationMap;

    public ConsoleOutput showEmptyMethods(boolean show) {
        this.showEmptyMethods = show;
        return this;
    }

    public ConsoleOutput showOnlyPublicMethods(boolean show) {
        this.showOnlyPublicMethods = show;
        return this;
    }

    public void print(ScanResult result) {
        this.scanResult = result;
        this.implementationMap = result.buildImplementationMap();
        
        // Sort classes by FQN
        List<ClassInfo> sortedClasses = result.allClasses()
            .sorted(Comparator.comparing(ClassInfo::fqn))
            .toList();

        for (ClassInfo classInfo : sortedClasses) {
            printClass(classInfo);
        }
    }

    private void printClass(ClassInfo classInfo) {
        List<MethodInfo> methodsToShow = classInfo.methods().values().stream()
            .filter(m -> showEmptyMethods || !m.actors().isEmpty())
            .filter(m -> !showOnlyPublicMethods || m.isPublic())
            .filter(m -> !m.isStaticInitializer())
            .sorted(Comparator.comparing(MethodInfo::name))
            .toList();

        if (methodsToShow.isEmpty()) {
            return;
        }

        System.out.println(classInfo.fqn() + ":");

        for (MethodInfo method : methodsToShow) {
            printMethod(method);
        }
    }

    private void printMethod(MethodInfo method) {
        System.out.println("  " + method.name() + ":");

        if (method.actors().isEmpty()) {
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

        // Check if the actor's type is an interface or abstract class that needs resolution
        String typeFqn = actor.typeFqn();
        String resolvedType = resolveType(typeFqn, actor.type());

        // Print one line per method called
        for (String methodCalled : actor.methodsCalled().stream().sorted().toList()) {
            System.out.printf("    %s %s %s#%s%n",
                typeLabel,
                actor.name(),
                resolvedType,
                methodCalled);
        }
    }

    /**
     * Resolves an interface/abstract type to its implementation.
     * Returns the type with resolution info appended if applicable.
     *
     * @param typeFqn The fully qualified type name
     * @param actorType The actor type - used to skip resolution for non-polymorphic calls
     */
    private String resolveType(String typeFqn, ActorType actorType) {
        // Static method calls and new object creation are NOT polymorphic
        // - STATIC_CLASS: Interface.staticMethod() always calls the interface's method
        // - NEW_OBJECT: new X() is definitively X, never an interface
        if (actorType == ActorType.STATIC_CLASS || actorType == ActorType.NEW_OBJECT) {
            return typeFqn;
        }

        // Check if this type is in our scanned classes and needs resolution
        ClassInfo typeInfo = scanResult.classes().get(typeFqn);
        if (typeInfo == null || !typeInfo.needsResolution()) {
            // Type is not an interface/abstract we know about, or is already concrete
            return typeFqn;
        }

        Set<String> implementations = implementationMap.get(typeFqn);
        if (implementations == null || implementations.isEmpty()) {
            // Interface/abstract with no known implementations
            return typeFqn + " [UNRESOLVED]";
        } else if (implementations.size() == 1) {
            // Single implementation - auto-resolve
            String impl = implementations.iterator().next();
            return typeFqn + " -> " + impl;
        } else {
            // Multiple implementations - ambiguous
            return typeFqn + " [AMBIGUOUS: " + implementations.size() + " impls]";
        }
    }
}
