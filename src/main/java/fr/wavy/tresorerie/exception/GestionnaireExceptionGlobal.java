package fr.wavy.tresorerie.exception;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GestionnaireExceptionGlobal {

    private static final Logger LOGGER = LoggerFactory.getLogger(GestionnaireExceptionGlobal.class);

    @ExceptionHandler(RegleMetierException.class)
    public ResponseEntity<ApiErreur> gererRegleMetier(RegleMetierException exception) {
        return ResponseEntity.badRequest().body(erreur(exception.getCode(), exception.getMessage()));
    }

    @ExceptionHandler(RessourceNonTrouveeException.class)
    public ResponseEntity<ApiErreur> gererRessourceNonTrouvee(RessourceNonTrouveeException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(erreur("RESSOURCE_NON_TROUVEE", exception.getMessage()));
    }

    @ExceptionHandler(CreationPieceInterditeException.class)
    public ResponseEntity<ApiErreur> gererCreationPieceInterdite(CreationPieceInterditeException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(erreur("PIECE_RAPPROCHEMENT_INTERDITE", exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErreur> gererValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getDefaultMessage())
                .collect(Collectors.joining(" "));
        return ResponseEntity.badRequest().body(erreur("VALIDATION", message));
    }

    @ExceptionHandler({org.springframework.http.converter.HttpMessageNotReadableException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
            org.springframework.validation.BindException.class})
    public ResponseEntity<ApiErreur> gererRequeteInvalide(Exception exception) {
        return ResponseEntity.badRequest().body(erreur("REQUETE_INVALIDE","Corps ou paramètres invalides ; vérifiez les champs de la demande."));
    }

    @ExceptionHandler(ErreurInterserviceException.class)
    public ResponseEntity<ApiErreur> gererErreurInterservice(ErreurInterserviceException exception) {
        return ResponseEntity.status(exception.getStatut())
                .body(erreur("ERREUR_INTERSERVICE", exception.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErreur> gererErreurTechnique(Exception exception) {
        LOGGER.error("Erreur technique non geree", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(erreur("ERREUR_TECHNIQUE", "Une erreur technique est survenue."));
    }

    private ApiErreur erreur(String code, String message) {
        return new ApiErreur(code, message, LocalDateTime.now());
    }
}
