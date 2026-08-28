package fr.wavy.tresorerie.exception;

import org.springframework.http.HttpStatusCode;

public class ErreurInterserviceException extends RuntimeException {
    private final HttpStatusCode statut;

    public ErreurInterserviceException(HttpStatusCode statut, String message, Throwable cause) {
        super(message, cause);
        this.statut = statut;
    }

    public HttpStatusCode getStatut() {
        return statut;
    }
}
