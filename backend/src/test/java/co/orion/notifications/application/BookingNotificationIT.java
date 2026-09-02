package co.orion.notifications.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Executor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import co.orion.TestcontainersConfiguration;
import co.orion.identity.domain.ProfessorProfile;
import co.orion.identity.domain.User;
import co.orion.identity.domain.UserRole;
import co.orion.identity.persistence.ProfessorProfileRepository;
import co.orion.scheduling.api.BookingResponse;
import co.orion.scheduling.api.CancelBookingRequest;
import co.orion.scheduling.api.CreateBookingRequest;
import co.orion.scheduling.domain.AvailabilityRule;
import co.orion.scheduling.domain.BusinessZone;
import co.orion.scheduling.persistence.AvailabilityRuleRepository;
import co.orion.scheduling.persistence.BookingRepository;
import co.orion.support.ApiIntegrationSupport;
import jakarta.mail.internet.MimeMessage;

/**
 * @MockitoBean, no @MockBean: @MockBean fue eliminado en Boot 4.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import({TestcontainersConfiguration.class, BookingNotificationIT.FrozenClockConfiguration.class})
class BookingNotificationIT extends ApiIntegrationSupport {

    private static final Instant FROZEN_NOW = Instant.parse("2026-07-13T17:00:00Z");
    private static final LocalDate WEDNESDAY = LocalDate.of(2026, 7, 15);

    @TestConfiguration
    static class FrozenClockConfiguration {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(FROZEN_NOW, ZoneOffset.UTC);
        }

        /**
         * En producción el listener de reservas es @Async (la reserva no espera al correo). Aquí lo
         * volvemos síncrono con un SyncTaskExecutor: así los correos se envían en el hilo de la
         * llamada y verify(...) es determinista, sin tareas async solapándose entre tests.
         */
        @Bean
        Executor taskExecutor() {
            return new SyncTaskExecutor();
        }
    }

    @MockitoBean
    private JavaMailSender mailSender;

    @Autowired
    private BookingRepository bookings;

    @Autowired
    private AvailabilityRuleRepository rules;

    @Autowired
    private ProfessorProfileRepository profiles;

    private User ana;
    private User maria;
    private ApiIntegrationSupport.Session anaSession;

    @BeforeEach
    void seed() {
        bookings.deleteAll();
        rules.deleteAll();
        profiles.deleteAll();
        users.deleteAll();

        ana = createUser("ana@orion.test", "Ana Ramírez", UserRole.STUDENT);
        maria = createUser("maria@orion.test", "María Gómez", UserRole.PROFESSOR);
        ana.changeWhatsappPhone("+57 300 111 2233");
        maria.changeWhatsappPhone("+573009998877");
        users.save(ana);
        users.save(maria);

        ProfessorProfile published = new ProfessorProfile(maria);
        published.publish();
        profiles.save(published);
        approveTeacher(maria.getId());
        rules.save(new AvailabilityRule(maria.getId(), DayOfWeek.WEDNESDAY,
                LocalTime.of(8, 0), LocalTime.of(11, 0)));

        anaSession = login("ana@orion.test");

        // El mock tiene que saber fabricar el MimeMessage que le pide el sender.
        // jakarta.mail.Session cualificada: el Session heredado de ApiIntegrationSupport la tapa.
        when(mailSender.createMimeMessage())
                .thenAnswer(invocation ->
                        new MimeMessage(jakarta.mail.Session.getInstance(new Properties())));
    }

    private CreateBookingRequest bookingRequest() {
        return new CreateBookingRequest(
                maria.getId(),
                ZonedDateTime.of(WEDNESDAY, LocalTime.of(9, 0), BusinessZone.BOGOTA).toOffsetDateTime(),
                "VIRTUAL", "Google Meet", null);
    }

    private BookingResponse book() {
        return post("/api/v1/bookings", anaSession, bookingRequest(), BookingResponse.class).getBody();
    }

    @Test
    void creatingABookingSendsOneEmailToEachParticipant() throws Exception {
        book();

        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, timeout(5000).times(2)).send(sent.capture());

        List<String> recipients = sent.getAllValues().stream()
                .map(message -> {
                    try {
                        return message.getAllRecipients()[0].toString();
                    } catch (Exception ex) {
                        throw new IllegalStateException(ex);
                    }
                })
                .toList();

        assertThat(recipients).containsExactlyInAnyOrder("ana@orion.test", "maria@orion.test");
    }

    @Test
    void theConfirmationCarriesTheIcsAttachment() throws Exception {
        book();

        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, timeout(5000).times(2)).send(sent.capture());

        MimeMessage message = sent.getAllValues().getFirst();
        assertThat(message.getSubject()).contains("agendada");
        // El .ics viaja como adjunto: el cuerpo del mensaje lo menciona por nombre.
        assertThat(messageAsString(message)).contains("clase-orion.ics");
        assertThat(messageAsString(message)).contains("calendar.google.com");
    }

    @Test
    void cancellingSendsTwoMoreEmailsWithoutAttachment() throws Exception {
        BookingResponse booking = book();

        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        // Motivo sin acentos a propósito: el cuerpo viaja en quoted-printable y una "ó" se
        // escribiría como "=C3=B3", lo que haría fallar un contains() por una razón que no
        // tiene nada que ver con lo que este test quiere probar.
        post("/api/v1/bookings/" + booking.id() + "/cancel", anaSession,
                new CancelBookingRequest("Viaje imprevisto"), BookingResponse.class);

        // 2 de la creación + 2 de la cancelación.
        verify(mailSender, timeout(5000).times(4)).send(sent.capture());

        // El envío es async: no asumimos orden. Filtramos las cancelaciones por asunto.
        List<MimeMessage> cancellations = sent.getAllValues().stream()
                .filter(message -> {
                    try {
                        return message.getSubject().contains("cancelada");
                    } catch (Exception ex) {
                        throw new IllegalStateException(ex);
                    }
                })
                .toList();

        assertThat(cancellations).hasSize(2);
        assertThat(cancellations).allSatisfy(cancellation ->
                assertThat(messageAsString(cancellation)).doesNotContain("clase-orion.ics"));
        assertThat(cancellations).anySatisfy(cancellation ->
                assertThat(messageAsString(cancellation)).contains("Viaje imprevisto"));
    }

    @Test
    void aMailServerFailureDoesNotBreakTheBooking() {
        doThrow(new org.springframework.mail.MailSendException("Mailpit caído"))
                .when(mailSender).send(any(MimeMessage.class));

        ResponseEntity<BookingResponse> response = post(
                "/api/v1/bookings", anaSession, bookingRequest(), BookingResponse.class);

        // El correo es un efecto secundario deseable, no una condición para reservar.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(bookings.findById(response.getBody().id()).orElseThrow().isConfirmed()).isTrue();
    }

    @Test
    void theEmailShowsTheTimeInBogotaAndPointsToOrionNotWhatsapp() throws Exception {
        book();

        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, timeout(5000).times(2)).send(sent.capture());

        String toAna = sent.getAllValues().stream()
                .filter(message -> {
                    try {
                        return message.getAllRecipients()[0].toString().equals("ana@orion.test");
                    } catch (Exception ex) {
                        throw new IllegalStateException(ex);
                    }
                })
                .map(this::messageAsString)
                .findFirst()
                .orElseThrow();

        // La hora se muestra en Bogotá (09:00), no en UTC (14:00).
        assertThat(toAna).contains("09:00");
        assertThat(toAna).contains("hora de Bogot");
        // D4: el contacto ocurre dentro de Orión (mensajería), no por WhatsApp. Ya no hay wa.me.
        assertThat(toAna).doesNotContain("wa.me");
        assertThat(toAna).doesNotContain("WhatsApp");
        assertThat(toAna).contains("Mensajes");
    }

    private String messageAsString(MimeMessage message) {
        try {
            var out = new java.io.ByteArrayOutputStream();
            message.writeTo(out);
            // El cuerpo viaja en quoted-printable: deshacemos los cortes de línea blandos para
            // poder buscar texto que el codificador pudo partir en dos.
            return out.toString(java.nio.charset.StandardCharsets.UTF_8).replace("=\r\n", "");
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
