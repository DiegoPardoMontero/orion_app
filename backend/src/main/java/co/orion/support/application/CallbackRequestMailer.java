package co.orion.support.application;

import co.orion.support.domain.CallbackRequest;

/** Avisa a la academia de que alguien pidió que le escriban. Detrás de interfaz para los tests. */
public interface CallbackRequestMailer {

    void avisar(CallbackRequest solicitud);
}
