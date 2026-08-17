package fr.wavy.tresorerie.exception;

public class RegleMetierException extends RuntimeException {
    private final String code;

    public RegleMetierException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
