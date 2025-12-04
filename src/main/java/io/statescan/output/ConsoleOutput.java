package io.statescan.output;

import io.statescan.model.*;

import java.util.Comparator;
import java.util.List;

/**
 * Outputs scan results to the console in a simple format.
 */
public class ConsoleOutput {

    private boolean showEmptyMethods = false;
    private boolean showOnlyPublicMethods = false;

    public ConsoleOutput showEmptyMethods(boolean show) {
        this.showEmptyMethods = show;
        return this;
    }

    public ConsoleOutput showOnlyPublicMethods(boolean show) {
        this.showOnlyPublicMethods = show;
        return this;
    }

    public void print(ScanResult result) {
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

        // Print one line per method called
        for (String methodCalled : actor.methodsCalled().stream().sorted().toList()) {
            System.out.printf("    %s %s %s#%s%n",
                typeLabel,
                actor.name(),
                actor.typeFqn(),
                methodCalled);
        }
    }
}
