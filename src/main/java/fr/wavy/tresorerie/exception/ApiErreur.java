package fr.wavy.tresorerie.exception;

import java.time.LocalDateTime;

public record ApiErreur(String code, String message, LocalDateTime date) {
}
