package co.orion.messaging.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import co.orion.messaging.application.ContactMasker.MaskResult;
import co.orion.messaging.domain.FlaggedReason;

/**
 * La política de contacto es una función pura: se prueba exhaustivamente sin levantar Spring.
 */
class ContactMaskerTest {

    @Test
    void cleanTextIsUntouched() {
        MaskResult result = ContactMasker.mask("Hola, ¿podemos ver el condicional el jueves?");

        assertThat(result.wasMasked()).isFalse();
        assertThat(result.reason()).isNull();
        assertThat(result.masked()).isEqualTo("Hola, ¿podemos ver el condicional el jueves?");
    }

    @Test
    void nullAndBlankPassThrough() {
        assertThat(ContactMasker.mask(null).reason()).isNull();
        assertThat(ContactMasker.mask("   ").reason()).isNull();
    }

    @Test
    void aColombianMobileIsMaskedAsContactInfo() {
        MaskResult result = ContactMasker.mask("Escríbeme al 3001112233 mejor");

        assertThat(result.reason()).isEqualTo(FlaggedReason.CONTACT_INFO);
        assertThat(result.masked()).doesNotContain("3001112233");
        assertThat(result.masked()).contains(ContactMasker.PHONE_MASK);
    }

    @Test
    void aPhoneWithSeparatorsIsMasked() {
        MaskResult result = ContactMasker.mask("mi cel es +57 300 111 2233 ok");

        assertThat(result.reason()).isEqualTo(FlaggedReason.CONTACT_INFO);
        assertThat(result.masked()).doesNotContain("300");
        assertThat(result.masked()).doesNotContain("2233");
    }

    @Test
    void aShortNumberIsNotAPhone() {
        // Seis dígitos o menos no cuentan como teléfono: no se toca ni se marca.
        MaskResult result = ContactMasker.mask("La lección 12345 de la página 6");

        assertThat(result.wasMasked()).isFalse();
        assertThat(result.masked()).contains("12345");
    }

    @Test
    void anEmailIsMaskedAsContactInfo() {
        MaskResult result = ContactMasker.mask("mándame un correo a juan.perez@gmail.com por fa");

        assertThat(result.reason()).isEqualTo(FlaggedReason.CONTACT_INFO);
        assertThat(result.masked()).doesNotContain("juan.perez@gmail.com");
        assertThat(result.masked()).contains(ContactMasker.EMAIL_MASK);
    }

    @Test
    void aChannelMentionIsOffPlatform() {
        MaskResult result = ContactMasker.mask("mejor hablemos por WhatsApp");

        assertThat(result.reason()).isEqualTo(FlaggedReason.OFF_PLATFORM);
        assertThat(result.masked()).doesNotContainIgnoringCase("whatsapp");
        assertThat(result.masked()).contains(ContactMasker.CHANNEL_MASK);
    }

    @Test
    void telegramAndWaMeAreOffPlatform() {
        assertThat(ContactMasker.mask("búscame en Telegram").reason())
                .isEqualTo(FlaggedReason.OFF_PLATFORM);
        assertThat(ContactMasker.mask("aquí wa.me/573001112233").reason())
                // wa.me + número: gana CONTACT_INFO por tener info directa.
                .isEqualTo(FlaggedReason.CONTACT_INFO);
    }

    @Test
    void aPhoneBeatsAChannelMentionForTheReason() {
        MaskResult result = ContactMasker.mask("escríbeme por WhatsApp al 3009998877");

        assertThat(result.reason()).isEqualTo(FlaggedReason.CONTACT_INFO);
        assertThat(result.masked()).doesNotContain("3009998877");
        assertThat(result.masked()).doesNotContainIgnoringCase("whatsapp");
    }

    @Test
    void multipleContactBitsAreAllMasked() {
        MaskResult result = ContactMasker.mask(
                "tel 3001112233 o correo a@b.com y mi Instagram");

        assertThat(result.reason()).isEqualTo(FlaggedReason.CONTACT_INFO);
        assertThat(result.masked()).doesNotContain("3001112233");
        assertThat(result.masked()).doesNotContain("a@b.com");
        assertThat(result.masked()).doesNotContainIgnoringCase("instagram");
    }
}
