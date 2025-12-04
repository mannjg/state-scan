package io.statescan.detectors;

import io.statescan.config.LeafTypeConfig;
import io.statescan.graph.*;
import io.statescan.model.Finding;
import io.statescan.model.RiskLevel;
import io.statescan.model.StateType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Detects mutable fields in classes that are bound as singletons via Guice modules.
 *
 * This detector complements SingletonDetector by finding singletons defined
 * through module bindings (e.g., .in(Singleton.class)) rather than annotations.
 */
public class MutableFieldDetector implements Detector {

    // Patterns for singleton binding detection in bytecode
    private static final Set<String> SINGLETON_SCOPE_CLASSES = Set.of(
            "com.google.inject.Singleton",
            "javax.inject.Singleton",
            "jakarta.inject.Singleton",
            "com.google.inject.Scopes" // For Scopes.SINGLETON
    );

    @Override
    public String id() {
        return "module-binding";
    }

    @Override
    public String description() {
        return "Detects singletons bound via Guice modules with mutable state";
    }

    @Override
    public List<Finding> detect(CallGraph graph, LeafTypeConfig config, Set<String> reachableClasses) {
        List<Finding> findings = new ArrayList<>();

        // First, find all Guice modules and extract singleton bindings
        Map<String, ModuleBinding> singletonBindings = extractModuleBindings(graph, config, reachableClasses);

        // Then analyze each bound class for mutable state
        for (Map.Entry<String, ModuleBinding> entry : singletonBindings.entrySet()) {
            String boundClass = entry.getKey();
            ModuleBinding binding = entry.getValue();

            // Skip if already annotated (handled by SingletonDetector)
            ClassNode cls = graph.getClass(boundClass).orElse(null);
            if (cls == null || hasExplicitSingletonAnnotation(cls, config)) {
                continue;
            }

            // Find mutable fields
            List<FieldNode> mutableFields = findMutableFields(cls, config);

            for (FieldNode field : mutableFields) {
                Finding finding = createFinding(cls, field, binding, config);
                findings.add(finding);
            }
        }

        return findings;
    }

    /**
     * Extracts singleton bindings from Guice modules.
     * This is a simplified heuristic based on method invocation patterns.
     */
    private Map<String, ModuleBinding> extractModuleBindings(CallGraph graph, LeafTypeConfig config, Set<String> reachableClasses) {
        Map<String, ModuleBinding> bindings = new HashMap<>();

        for (ClassNode cls : graph.allClasses()) {
            // Only analyze reachable Guice modules
            if (!reachableClasses.contains(cls.fqn()) || !isGuiceModule(cls, config)) {
                continue;
            }

            // Look for configure() method
            for (MethodNode method : cls.methods()) {
                if (method.isGuiceConfigureMethod()) {
                    extractBindingsFromMethod(cls, method, bindings);
                }

                // Also check @Provides methods with @Singleton
                if (method.isProviderMethod() && hasSingletonAnnotation(method)) {
                    String returnType = extractReturnType(method.descriptor());
                    if (returnType != null) {
                        bindings.put(returnType, new ModuleBinding(
                                cls.fqn(),
                                "@Provides @Singleton",
                                method.name()
                        ));
                    }
                }
            }
        }

        return bindings;
    }

    private boolean isGuiceModule(ClassNode cls, LeafTypeConfig config) {
        // Check if class extends AbstractModule or implements Module
        for (String moduleType : config.getModuleTypes()) {
            if (moduleType.equals(cls.superclass()) ||
                    cls.interfaces().contains(moduleType)) {
                return true;
            }
        }
        return false;
    }

    private void extractBindingsFromMethod(ClassNode moduleClass, MethodNode method,
            Map<String, ModuleBinding> bindings) {
        // Analyze method invocations to find bind().to().in(Singleton) patterns
        // This is heuristic-based on the invocation sequence

        String lastBindTarget = null;

        for (MethodRef invocation : method.invocations()) {
            String methodName = invocation.name();
            String owner = invocation.owner();

            // Look for bind(Class) calls
            if ("bind".equals(methodName) && owner.contains("Binder")) {
                lastBindTarget = extractClassFromDescriptor(invocation.descriptor());
            }

            // Look for to(Class) calls
            if ("to".equals(methodName) && lastBindTarget != null) {
                String implClass = extractClassFromDescriptor(invocation.descriptor());
                if (implClass != null) {
                    lastBindTarget = implClass; // Binding is to impl class
                }
            }

            // Look for .in(Singleton.class) or .asEagerSingleton()
            if (lastBindTarget != null) {
                if ("in".equals(methodName) || "asEagerSingleton".equals(methodName)) {
                    // Check if this is a singleton scope
                    if (isSingletonScope(invocation)) {
                        bindings.put(lastBindTarget, new ModuleBinding(
                                moduleClass.fqn(),
                                ".in(Singleton.class)",
                                "configure()"
                        ));
                    }
                }

                // toInstance() is always singleton
                if ("toInstance".equals(methodName)) {
                    bindings.put(lastBindTarget, new ModuleBinding(
                            moduleClass.fqn(),
                            ".toInstance()",
                            "configure()"
                    ));
                }
            }
        }
    }

    private boolean isSingletonScope(MethodRef invocation) {
        // Check if the method is called with Singleton.class parameter
        // This is a heuristic based on descriptor
        String descriptor = invocation.descriptor();
        return descriptor != null &&
                (descriptor.contains("Singleton") ||
                        descriptor.contains("SINGLETON") ||
                        "asEagerSingleton".equals(invocation.name()));
    }

    private String extractClassFromDescriptor(String descriptor) {
        // Extract class name from method descriptor
        // e.g., "(Ljava/lang/Class;)V" -> extract the Class parameter
        if (descriptor == null) return null;

        int start = descriptor.indexOf("Ljava/lang/Class");
        if (start >= 0) {
            // This is a Class<?> parameter, actual class is determined at runtime
            // We'd need more sophisticated analysis
            return null;
        }

        // For direct type references in descriptor
        start = descriptor.indexOf('L');
        if (start >= 0) {
            int end = descriptor.indexOf(';', start);
            if (end > start) {
                return descriptor.substring(start + 1, end).replace('/', '.');
            }
        }

        return null;
    }

    private String extractReturnType(String descriptor) {
        // Extract return type from method descriptor
        // e.g., "()Lcom/example/Service;" -> "com.example.Service"
        if (descriptor == null) return null;

        int parenEnd = descriptor.lastIndexOf(')');
        if (parenEnd < 0) return null;

        String returnPart = descriptor.substring(parenEnd + 1);
        if (returnPart.startsWith("L") && returnPart.endsWith(";")) {
            return returnPart.substring(1, returnPart.length() - 1).replace('/', '.');
        }

        return null;
    }

    private boolean hasExplicitSingletonAnnotation(ClassNode cls, LeafTypeConfig config) {
        for (String annotation : cls.annotations()) {
            if (config.isSingletonAnnotation(annotation)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasSingletonAnnotation(MethodNode method) {
        return method.annotations().stream()
                .anyMatch(a -> a.contains("Singleton"));
    }

    private List<FieldNode> findMutableFields(ClassNode cls, LeafTypeConfig config) {
        List<FieldNode> mutable = new ArrayList<>();

        for (FieldNode field : cls.fields()) {
            if (field.isStatic()) continue;

            String typeName = field.extractTypeName();
            if (typeName != null && config.isSafeType(typeName)) {
                continue;
            }

            if (field.isPotentiallyMutable()) {
                mutable.add(field);
            }
        }

        return mutable;
    }

    private Finding createFinding(ClassNode cls, FieldNode field, ModuleBinding binding,
            LeafTypeConfig config) {
        String typeName = field.extractTypeName();
        RiskLevel risk = determineRisk(field, typeName, config);

        return Finding.builder()
                .className(cls.fqn())
                .fieldName(field.name())
                .fieldType(field.type())
                .stateType(StateType.IN_MEMORY)
                .riskLevel(risk)
                .pattern("Module-bound singleton with mutable field")
                .scopeSource(Finding.ScopeSource.MODULE_BINDING)
                .scopeModule(binding.moduleFqn())
                .description(buildDescription(cls, field, binding))
                .recommendation(buildRecommendation(field, typeName, config))
                .detectorId(id())
                .sourceFile(cls.sourceFile())
                .build();
    }

    private RiskLevel determineRisk(FieldNode field, String typeName, LeafTypeConfig config) {
        if (typeName != null) {
            if (config.isCacheType(typeName)) return RiskLevel.CRITICAL;
            if (config.isMutableCollectionType(typeName)) return RiskLevel.CRITICAL;
        }
        if (!field.isFinal()) return RiskLevel.HIGH;
        return RiskLevel.MEDIUM;
    }

    private String buildDescription(ClassNode cls, FieldNode field, ModuleBinding binding) {
        return String.format(
                "Class '%s' is bound as singleton in module '%s' via %s, " +
                        "but has mutable field '%s'. State is NOT shared across replicas.",
                cls.simpleName(),
                binding.moduleFqn(),
                binding.bindingType(),
                field.name()
        );
    }

    private String buildRecommendation(FieldNode field, String typeName, LeafTypeConfig config) {
        if (typeName != null && config.isCacheType(typeName)) {
            return "Replace in-memory cache with distributed cache (Redis, Hazelcast)";
        }
        if (typeName != null && config.isMutableCollectionType(typeName)) {
            return "Store data in database or distributed cache";
        }
        return "Make field immutable or externalize state to shared storage";
    }

    /**
     * Represents a binding discovered from a Guice module.
     */
    private record ModuleBinding(
            String moduleFqn,
            String bindingType,
            String methodName
    ) {}
}
