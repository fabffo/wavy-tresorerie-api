package fr.wavy.tresorerie.exception;

public class CreationPieceInterditeException extends RuntimeException {
    public CreationPieceInterditeException() {
        super("La création d'une pièce de rapprochement n'est pas autorisée pour ce tiers.");
    }
}
