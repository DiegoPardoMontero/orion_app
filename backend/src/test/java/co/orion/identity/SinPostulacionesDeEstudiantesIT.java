package co.orion.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import co.orion.TestcontainersConfiguration;
import co.orion.identity.domain.ApplicationEventType;
import co.orion.identity.domain.ApplicationStatus;
import co.orion.identity.domain.DocumentType;
import co.orion.identity.domain.ProfessorProfile;
import co.orion.identity.domain.TeacherApplication;
import co.orion.identity.domain.TeacherApplicationEvent;
import co.orion.identity.domain.TeacherDocument;
import co.orion.identity.domain.User;
import co.orion.identity.domain.UserRole;
import co.orion.identity.persistence.ProfessorProfileRepository;
import co.orion.identity.persistence.TeacherApplicationEventRepository;
import co.orion.identity.persistence.TeacherApplicationRepository;
import co.orion.identity.persistence.TeacherDocumentRepository;
import co.orion.legal.domain.AgreementAcceptance;
import co.orion.legal.persistence.AgreementAcceptanceRepository;
import co.orion.support.ApiIntegrationSupport;

/**
 * La V69: las postulaciones hechas desde cuentas de estudiante se borran «como si nunca hubiera
 * pasado» (Pardo, 25/09/2026). Flyway la corre sobre una base vacía al arrancar, así que aquí se
 * siembran los casos y se vuelve a correr el mismo archivo encima: lo que se prueba es el SQL que
 * llega a producción, no una copia.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(TestcontainersConfiguration.class)
class SinPostulacionesDeEstudiantesIT extends ApiIntegrationSupport {

    @Autowired
    private TeacherApplicationRepository applications;

    @Autowired
    private TeacherApplicationEventRepository events;

    @Autowired
    private TeacherDocumentRepository documents;

    @Autowired
    private AgreementAcceptanceRepository agreements;

    @Autowired
    private ProfessorProfileRepository profiles;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbc;

    private User estudiante;
    private User rechazado;
    private User aspirante;
    private User profesor;
    private User exProfesor;

    /** Correos nuevos en cada prueba: no depende de lo que otras dejen en la base. */
    @BeforeEach
    void seed() {
        String sufijo = UUID.randomUUID().toString().substring(0, 8);
        estudiante = createUser("ana." + sufijo + "@orion.test", "Ana Ramírez", UserRole.STUDENT);
        rechazado = createUser("rechazado." + sufijo + "@orion.test", "Re Chazado", UserRole.STUDENT);
        aspirante = createUser("aspi." + sufijo + "@orion.test", "Aspi Rante", UserRole.STUDENT);
        aspirante.intendsToTeach();
        users.save(aspirante);
        profesor = createUser("profe." + sufijo + "@orion.test", "Profe Sora", UserRole.PROFESSOR);
        // Un profesor que el admin volvió estudiante: su aprobada dice que la cuenta fue de profesor.
        exProfesor = createUser("ex." + sufijo + "@orion.test", "Ex Profe", UserRole.STUDENT);

        // La que se hizo con el botón de «Mi ficha»: borrador, documento, acuerdo y perfil a medias.
        TeacherApplication borrador = applications.saveAndFlush(new TeacherApplication(estudiante.getId()));
        events.save(new TeacherApplicationEvent(borrador.getId(), ApplicationEventType.CREATED, estudiante.getId(), null));
        documents.save(new TeacherDocument(estudiante.getId(), borrador.getId(), DocumentType.CV,
                "cv.pdf", "orion/documents/cv", "application/pdf", 3));
        agreements.save(new AgreementAcceptance(estudiante.getId(), "TEACHER_AGREEMENT", "1.0", "127.0.0.1", "test"));
        profiles.save(new ProfessorProfile(estudiante));

        applications.save(new TeacherApplication(rechazado.getId(), ApplicationStatus.REJECTED, null, Instant.now()));
        applications.save(new TeacherApplication(aspirante.getId()));
        applications.save(new TeacherApplication(profesor.getId(), ApplicationStatus.APPROVED, null, Instant.now()));
        applications.save(new TeacherApplication(exProfesor.getId(), ApplicationStatus.APPROVED, null, Instant.now()));
    }

    @AfterEach
    void limpiar() {
        List<UUID> ids = List.of(estudiante.getId(), rechazado.getId(), aspirante.getId(),
                profesor.getId(), exProfesor.getId());
        for (UUID id : ids) {
            jdbc.update("delete from teacher_documents where user_id = ?", id);
            jdbc.update("delete from teacher_applications where user_id = ?", id);
            jdbc.update("delete from agreement_acceptances where user_id = ?", id);
            jdbc.update("delete from professor_profiles where user_id = ?", id);
            jdbc.update("delete from users where id = ?", id);
        }
    }

    private void correrLaV69() {
        new ResourceDatabasePopulator(
                new ClassPathResource("db/migration/V69__sin_postulaciones_de_estudiantes.sql"))
                .execute(dataSource);
    }

    private long postulacionesDe(User quien) {
        return applications.findAll().stream().filter(a -> a.getUserId().equals(quien.getId())).count();
    }

    @Test
    void laDeUnaCuentaDeEstudianteSeVaConTodoLoQueDejo() {
        correrLaV69();

        assertThat(postulacionesDe(estudiante)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from teacher_documents where user_id = ?",
                Long.class, estudiante.getId())).isZero();
        assertThat(agreements.existsByUserIdAndDocumentCode(estudiante.getId(), "TEACHER_AGREEMENT")).isFalse();
        assertThat(profiles.findById(estudiante.getId())).isEmpty();
        // La cuenta sigue: se borró la postulación, no la persona.
        assertThat(users.findById(estudiante.getId())).isPresent();
    }

    @Test
    void tambienLaDelAspiranteRechazadoQueVolvioASerEstudiante() {
        correrLaV69();

        assertThat(postulacionesDe(rechazado)).isZero();
    }

    @Test
    void noTocaAlAspiranteEnCursoNiANadieQueFueProfesor() {
        correrLaV69();

        assertThat(postulacionesDe(aspirante)).isEqualTo(1);
        assertThat(postulacionesDe(profesor)).isEqualTo(1);
        assertThat(postulacionesDe(exProfesor)).isEqualTo(1);
    }
}
