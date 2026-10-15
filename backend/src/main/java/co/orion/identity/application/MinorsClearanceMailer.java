package co.orion.identity.application;

import java.time.LocalDate;

/** El correo al profe cuando queda habilitado para clases con adolescentes. */
public interface MinorsClearanceMailer {

    void sendGranted(String toEmail, String firstName, LocalDate until);
}
