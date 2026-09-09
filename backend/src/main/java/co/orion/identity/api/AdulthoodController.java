package co.orion.identity.api;

import java.time.Clock;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import co.orion.identity.domain.User;
import co.orion.identity.persistence.UserRepository;
import co.orion.shared.security.OrionUserDetails;

/**
 * La declaración de mayoría de edad de las cuentas anteriores al Bloque 9.
 *
 * <p>Las cuentas nuevas la dan en el registro. Las de antes nunca la dieron, y marcarlas por
 * nuestra cuenta habría sido fabricar una constancia que nadie firmó (art. 9 de la Ley 1581 de
 * 2012: el responsable debe poder <em>probar</em> la autorización). Así que se les pide al entrar,
 * sin cerrarles la cuenta ni tocarles el saldo — solo sin poder reservar hasta que la den.
 *
 * <p>No hay endpoint para retirarla: no existe "dejar de ser mayor de edad", y permitirlo sería
 * ofrecer un botón para volverse menor y seguir usando Orión.
 */
@RestController
@RequestMapping("/api/v1/me/account/adulthood")
public class AdulthoodController {

    private final UserRepository users;
    private final Clock clock;

    public AdulthoodController(UserRepository users, Clock clock) {
        this.users = users;
        this.clock = clock;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void confirm(@AuthenticationPrincipal OrionUserDetails principal) {
        User user = users.findById(principal.user().getId()).orElseThrow();
        user.confirmAdulthood(clock.instant());
        users.save(user);
    }
}
