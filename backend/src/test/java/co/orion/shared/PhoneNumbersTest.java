package co.orion.shared;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PhoneNumbersTest {

    @Test
    void keepsAnAlreadyE164Number() {
        assertThat(PhoneNumbers.toE164("+573001112233")).isEqualTo("+573001112233");
    }

    @Test
    void addsColombianPrefixToA10DigitMobile() {
        assertThat(PhoneNumbers.toE164("3001112233")).isEqualTo("+573001112233");
    }

    @Test
    void stripsSpacesDashesAndParentheses() {
        assertThat(PhoneNumbers.toE164(" +57 300 111-2233 ")).isEqualTo("+573001112233");
        assertThat(PhoneNumbers.toE164("(300) 111 2233")).isEqualTo("+573001112233");
    }

    @Test
    void keepsAForeignNumberWithItsPlus() {
        assertThat(PhoneNumbers.toE164("+34 600 123 456")).isEqualTo("+34600123456");
    }

    @Test
    void doesNotInventAPrefixForAnUnknownFormat() {
        // No es celular CO ni trae '+': se deja como está, no se fabrica un indicativo.
        assertThat(PhoneNumbers.toE164("12345")).isEqualTo("12345");
    }

    @Test
    void returnsNullForBlankOrNull() {
        assertThat(PhoneNumbers.toE164(null)).isNull();
        assertThat(PhoneNumbers.toE164("   ")).isNull();
        assertThat(PhoneNumbers.toE164("+")).isNull();
    }
}
