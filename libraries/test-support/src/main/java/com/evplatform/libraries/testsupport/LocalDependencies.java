package com.evplatform.libraries.testsupport;

import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Shared Testcontainers factories pinned to the approved local baselines
 * (ENG-001 doc §3; release-manifests/local-images.md digests).
 *
 * Testcontainers 2.x canonical packages (org.testcontainers.postgresql /
 * .rabbitmq); Testcontainers 2.x line is required because Spring Boot 4.1
 * manages JUnit Jupiter 6, which the 1.x extension line does not support.
 *
 * Test-support only (ENG-001 doc §4.1 rule 9): production modules must not
 * depend on this library.
 */
public final class LocalDependencies {

    /** postgres:18 @ digest from release-manifests/local-images.md. */
    public static PostgreSQLContainer newPostgres() {
        return new PostgreSQLContainer(
                DockerImageName.parse("postgres:18@sha256:4ef4dbc939d61acea57712655ddb4b4ab27419c913f94cca0cd57cb3ea3c2280")
                        .asCompatibleSubstituteFor("postgres"));
    }

    /** rabbitmq:4.3-management @ digest from release-manifests/local-images.md. */
    public static RabbitMQContainer newRabbitMq() {
        return new RabbitMQContainer(
                DockerImageName.parse("rabbitmq:4.3-management@sha256:57bddb6fbc3498b5d8b5a14dc6f4506073ebcf94c66ba2a7678c335faa8dd631")
                        .asCompatibleSubstituteFor("rabbitmq"));
    }

    private LocalDependencies() {
    }
}
