package co.orion.identity.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import co.orion.TestcontainersConfiguration;
import co.orion.identity.domain.ProfessorProfile;
import co.orion.identity.domain.User;
import co.orion.identity.domain.UserRole;
import co.orion.identity.persistence.ProfessorProfileRepository;
import co.orion.support.ApiIntegrationSupport;

/**
 * El aviso 14 días antes de que termine el beneficio de fundador: sale una sola vez, y solo para
 * fundadores cuyo conteo ya empezó y termina dentro de esos 14 días. Reloj congelado.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import({TestcontainersConfiguration.class, AvisoDeFinDeFundadorIT.RelojCongelado.class})
class AvisoDeFinDeFundadorIT extends ApiIntegrationSupport {

    private static final Instant AHORA = Instant.parse("2026-12-29T17:00:00Z");

    @TestConfiguration
    static class RelojCongelado {
        @Bean
        @Primary
        Clock relojCongelado() {
            return Clock.fixed(AHORA, ZoneOffset.UTC);
        }
    }

    @Autowired
    private AvisoDeFinDeFundador aviso;

    @Autowired
    private ProfessorProfileRepository profiles;

    @Autowired
    private JdbcTemplate jdbc;

    private final List<UUID> creados = new java.util.ArrayList<>();

    @BeforeEach
    void limpiarAvisosViejos() {
        jdbc.update("update professor_profiles set founder_expiry_notified_at = now() where founder_until is not null");
    }

    @AfterEach
    void limpiar() {
        for (UUID id : creados) {
            jdbc.update("delete from notifications where user_id = ?", id);
            jdbc.update("delete from professor_profiles where user_id = ?", id);
            jdbc.update("delete from users where id = ?", id);
        }
        creados.clear();
    }

    /** Un profe con su beneficio; {@code terminaEn} nulo es un fundador que no ha empezado. */
    private User profe(boolean fundador, Duration terminaEn) {
        User u = createUser("profe." + UUID.randomUUID().toString().substring(0, 8) + "@orion.test",
                "María Gómez", UserRole.PROFESSOR);
        creados.add(u.getId());
        ProfessorProfile perfil = new ProfessorProfile(u);
        if (fundador) {
            perfil.grantFounder(1500, 3, AHORA.minus(Duration.ofDays(80)));
        }
        profiles.save(perfil);
        if (fundador && terminaEn != null) {
            Instant hasta = AHORA.plus(terminaEn);
            jdbc.update("update professor_profiles set founder_started_at = ?, founder_until = ? where user_id = ?",
                    Timestamp.from(hasta.minus(Duration.ofDays(90))), Timestamp.from(hasta), u.getId());
        }
        return u;
    }

    private List<String> avisosDe(User u) {
        return jdbc.queryForList("select body from notifications where user_id = ? and type = 'FOUNDER_ENDING'",
                String.class, u.getId());
    }

    @Test
    void sale14DiasAntesYUnaSolaVez() {
        // Termina el 12/01/2027 a las 00:00 de Bogotá.
        User maria = profe(true, Duration.between(AHORA, Instant.parse("2027-01-12T05:00:00Z")));

        assertThat(aviso.avisar()).isEqualTo(1);
        assertThat(aviso.avisar()).isZero();

        assertThat(avisosDe(maria)).singleElement().isEqualTo(
                "Tu comisión de profe fundador (15 %) termina el 12 de enero de 2027. Desde ese día, las reservas "
                        + "nuevas llevan la comisión estándar de Orión, 20 %. Las reservas que ya tengas conservan el 15 %.");
    }

    @Test
    void noAvisaAntesDeTiempoNiAQuienNoTieneFechaDeFin() {
        User lejos = profe(true, Duration.ofDays(20));
        User sinEmpezar = profe(true, null);
        User noFundador = profe(false, null);
        User yaTermino = profe(true, Duration.ofDays(-2));

        assertThat(aviso.avisar()).isZero();

        assertThat(avisosDe(lejos)).isEmpty();
        assertThat(avisosDe(sinEmpezar)).isEmpty();
        assertThat(avisosDe(noFundador)).isEmpty();
        assertThat(avisosDe(yaTermino)).isEmpty();
    }
}
