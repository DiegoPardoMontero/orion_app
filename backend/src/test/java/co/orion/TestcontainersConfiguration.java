package co.orion;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Postgres real para los tests, con la misma imagen que corre en local (paridad con producción).
 * {@code @ServiceConnection} hace que Boot cablee el datasource al contenedor automáticamente.
 *
 * <p><strong>Un contenedor por contexto de Spring, a sabiendas.</strong> Como cada test que añade
 * su propio {@code @TestConfiguration} —un mailer de mentira, un reloj congelado— estrena contexto,
 * llegan a convivir unos treinta Postgres y la suite pesa. Se probó a compartir uno solo: ahorra
 * memoria, pero pone a las cuarenta y seis clases a compartir base, y entonces una que deja
 * conversaciones sin limpiar rompe el {@code users.deleteAll()} de otra que no tiene nada que ver.
 * La aislación vale más que la memoria: un fallo por vecindad cuesta media tarde de encontrar.
 *
 * <p>Lo que sí conviene saber: una suite interrumpida deja sus contenedores en pie, y se acumulan
 * entre corrida y corrida. {@code docker ps -a --filter "label=org.testcontainers=true"} los lista.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));
    }
}
