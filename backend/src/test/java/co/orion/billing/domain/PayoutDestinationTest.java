package co.orion.billing.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** Cada tipo de llave Bre-B, validado y normalizado; y lo que se ve enmascarado. */
class PayoutDestinationTest {

    private static PayoutDestination con(BreBKeyType tipo, String llave) {
        return PayoutDestination.of(tipo, llave, IdDocumentType.CC, "1.020.304.050", "  María   Gómez ");
    }

    @Test
    void elCelularSonDiezDigitosQueEmpiezanPorTres() {
        assertThat(con(BreBKeyType.PHONE, "+57 300 123 4567").keyValue()).isEqualTo("3001234567");
        assertThat(con(BreBKeyType.PHONE, "300-123-4567").keyValue()).isEqualTo("3001234567");
        assertThatThrownBy(() -> con(BreBKeyType.PHONE, "6012345678")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> con(BreBKeyType.PHONE, "300123456")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void laCedulaSonSoloDigitos() {
        assertThat(con(BreBKeyType.ID_NUMBER, "1.020.304.050").keyValue()).isEqualTo("1020304050");
        assertThatThrownBy(() -> con(BreBKeyType.ID_NUMBER, "12AB45")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void elCorreoSeGuardaEnMinusculas() {
        assertThat(con(BreBKeyType.EMAIL, " Maria.Gomez@Gmail.com ").keyValue()).isEqualTo("maria.gomez@gmail.com");
        assertThatThrownBy(() -> con(BreBKeyType.EMAIL, "maria@gmail")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void laAlfanumericaEmpiezaPorArroba() {
        assertThat(con(BreBKeyType.ALPHANUMERIC, "mariagomez").keyValue()).isEqualTo("@mariagomez");
        assertThat(con(BreBKeyType.ALPHANUMERIC, "@maria.gomez").keyValue()).isEqualTo("@maria.gomez");
        assertThatThrownBy(() -> con(BreBKeyType.ALPHANUMERIC, "maria gomez")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void elDocumentoYElTitularSeNormalizan() {
        PayoutDestination d = con(BreBKeyType.PHONE, "3001234567");
        assertThat(d.documentNumber()).isEqualTo("1020304050");
        assertThat(d.holderName()).isEqualTo("María Gómez");
        assertThat(PayoutDestination.of(BreBKeyType.PHONE, "3001234567", IdDocumentType.PAS, "ab 12345", "Ana Ruiz")
                .documentNumber()).isEqualTo("AB12345");
        assertThatThrownBy(() -> PayoutDestination.of(BreBKeyType.PHONE, "3001234567", IdDocumentType.CC, "123", "Ana"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PayoutDestination.of(BreBKeyType.PHONE, "3001234567", IdDocumentType.CC, "10203040", " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** Enmascarado nunca deja ver el dato completo, pero sí lo bastante para reconocerlo. */
    @Test
    void enmascarado() {
        assertThat(con(BreBKeyType.PHONE, "3001234567").maskedKey()).isEqualTo("••••4567");
        assertThat(con(BreBKeyType.ID_NUMBER, "1020304050").maskedKey()).isEqualTo("••••4050");
        assertThat(con(BreBKeyType.EMAIL, "maria.gomez@gmail.com").maskedKey()).isEqualTo("m•••@gmail.com");
        assertThat(con(BreBKeyType.ALPHANUMERIC, "@mariagomez").maskedKey()).isEqualTo("@ma•••");
        assertThat(con(BreBKeyType.PHONE, "3001234567").maskedDocument()).isEqualTo("••••050");
    }
}
