package fr.wavy.tresorerie.dto;

import java.util.List;

public record PropositionsCarteResultat(int operationsExaminees, int piecesExaminees,
        int operationsSansMatch, List<PropositionCarteDto> propositions) {}
