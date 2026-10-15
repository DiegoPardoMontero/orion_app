package co.orion.catalog.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import co.orion.TestcontainersConfiguration;
import co.orion.support.ApiIntegrationSupport;

/**
 * El catálogo público de idiomas es lo que decide qué se puede enseñar, buscar y diagnosticar. La V42
 * lo dejó en inglés; la V82 (14/10/2026) reabre el francés. El español sigue apagado.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(TestcontainersConfiguration.class)
class CatalogoDeIdiomasIT extends ApiIntegrationSupport {

    @Test
    @DisplayName("Orión enseña inglés y francés, en ese orden, y no español")
    void inglesYFrances() {
        ResponseEntity<LanguageResponse[]> r = rest.getForEntity("/api/v1/catalog/languages", LanguageResponse[].class);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(Arrays.stream(r.getBody()).map(LanguageResponse::code).toList()).containsExactly("EN", "FR");
    }
}
