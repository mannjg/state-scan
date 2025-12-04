package io.statescan.detectors;

import io.statescan.config.LeafTypeConfig;
import io.statescan.graph.CallGraph;
import io.statescan.graph.ClassNode;
import io.statescan.graph.FieldNode;
import io.statescan.model.Finding;
import io.statescan.model.RiskLevel;
import io.statescan.model.StateType;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Detects HTTP/REST and gRPC client dependencies.
 *
 * Service clients are typically stateless and safe for horizontal scaling,
 * but we document them for operational awareness and to flag potential
 * issues with client-side state (connection pools, auth tokens, etc.).
 */
public class ServiceClientDetector implements Detector {

    @Override
    public String id() {
        return "service-client";
    }

    @Override
    public String description() {
        return "Documents HTTP/REST and gRPC service client dependencies";
    }

    @Override
    public List<Finding> detect(CallGraph graph, LeafTypeConfig config, Set<String> reachableClasses) {
        List<Finding> findings = new ArrayList<>();

        for (ClassNode cls : graph.allClasses()) {
            // Only analyze classes reachable from project roots
            if (!reachableClasses.contains(cls.fqn())) {
                continue;
            }

            for (FieldNode field : cls.fields()) {
                String typeName = field.extractTypeName();
                if (typeName == null) continue;

                if (config.isServiceClientType(typeName)) {
                    findings.add(createHttpFinding(cls, field, typeName));
                } else if (config.isGrpcType(typeName)) {
                    findings.add(createGrpcFinding(cls, field, typeName));
                }
            }
        }

        return findings;
    }

    private Finding createHttpFinding(ClassNode cls, FieldNode field, String typeName) {
        ClientType clientType = categorizeHttpClient(typeName);

        return Finding.builder()
                .className(cls.fqn())
                .fieldName(field.name())
                .fieldType(field.type())
                .stateType(StateType.SERVICE_CLIENT)
                .riskLevel(RiskLevel.INFO)
                .pattern("HTTP client: " + clientType.name)
                .description(buildHttpDescription(cls, field, clientType))
                .recommendation(clientType.recommendation)
                .detectorId(id())
                .sourceFile(cls.sourceFile())
                .build();
    }

    private Finding createGrpcFinding(ClassNode cls, FieldNode field, String typeName) {
        return Finding.builder()
                .className(cls.fqn())
                .fieldName(field.name())
                .fieldType(field.type())
                .stateType(StateType.SERVICE_CLIENT)
                .riskLevel(RiskLevel.INFO)
                .pattern("gRPC client")
                .description(buildGrpcDescription(cls, field, typeName))
                .recommendation(
                        "gRPC channels are typically safe to share across replicas. " +
                                "Ensure load balancing is configured appropriately for the target service."
                )
                .detectorId(id())
                .sourceFile(cls.sourceFile())
                .build();
    }

    private ClientType categorizeHttpClient(String typeName) {
        if (typeName.contains("javax.ws.rs") || typeName.contains("jakarta.ws.rs")) {
            return ClientType.JAX_RS;
        }
        if (typeName.contains("apache.http") || typeName.contains("apache.hc")) {
            return ClientType.APACHE_HTTP;
        }
        if (typeName.contains("okhttp")) {
            return ClientType.OKHTTP;
        }
        if (typeName.contains("java.net.http")) {
            return ClientType.JAVA_HTTP;
        }
        if (typeName.contains("feign")) {
            return ClientType.FEIGN;
        }
        if (typeName.contains("retrofit")) {
            return ClientType.RETROFIT;
        }
        return ClientType.OTHER;
    }

    private String buildHttpDescription(ClassNode cls, FieldNode field, ClientType clientType) {
        return String.format(
                "Class '%s' uses %s HTTP client in field '%s'. %s",
                cls.simpleName(),
                clientType.name,
                field.name(),
                clientType.note
        );
    }

    private String buildGrpcDescription(ClassNode cls, FieldNode field, String typeName) {
        String grpcType = typeName.contains("ManagedChannel") ? "ManagedChannel" :
                typeName.contains("Channel") ? "Channel" :
                        typeName.contains("stub") ? "gRPC stub" : "gRPC component";

        return String.format(
                "Class '%s' uses %s in field '%s'. " +
                        "gRPC clients maintain connection state but are typically safe for horizontal scaling.",
                cls.simpleName(),
                grpcType,
                field.name()
        );
    }

    private enum ClientType {
        JAX_RS(
                "JAX-RS Client",
                "JAX-RS clients are typically stateless and safe for horizontal scaling.",
                "Ensure connection pool is sized appropriately for replica count."
        ),
        APACHE_HTTP(
                "Apache HttpClient",
                "Apache HttpClient maintains connection pools.",
                "Configure connection pool size based on replica count. Consider using shared connection manager."
        ),
        OKHTTP(
                "OkHttp",
                "OkHttp is thread-safe and maintains connection pools.",
                "OkHttp is safe for horizontal scaling. Ensure connection pool is sized appropriately."
        ),
        JAVA_HTTP(
                "Java HttpClient",
                "Java 11+ HttpClient is thread-safe and pools connections.",
                "Java HttpClient is safe for horizontal scaling. Review connection pool settings."
        ),
        FEIGN(
                "Feign Client",
                "Feign clients are declarative HTTP clients.",
                "Feign clients are safe for horizontal scaling. Review retry and circuit breaker configuration."
        ),
        RETROFIT(
                "Retrofit",
                "Retrofit is a type-safe HTTP client.",
                "Retrofit clients are safe for horizontal scaling. Review OkHttp backend configuration."
        ),
        OTHER(
                "HTTP Client",
                "HTTP client detected.",
                "Review client configuration for horizontal scaling compatibility."
        );

        final String name;
        final String note;
        final String recommendation;

        ClientType(String name, String note, String recommendation) {
            this.name = name;
            this.note = note;
            this.recommendation = recommendation;
        }
    }
}
