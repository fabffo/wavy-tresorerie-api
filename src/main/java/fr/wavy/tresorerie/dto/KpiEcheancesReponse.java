package fr.wavy.tresorerie.dto;

import java.math.BigDecimal;

public record KpiEcheancesReponse(BigDecimal aEncaisser, BigDecimal aPayer, BigDecimal retardClients,
                                  BigDecimal retardFournisseurs, long nombreFacturesEnRetard,
                                  BigDecimal echeancesA7Jours, BigDecimal echeancesA30Jours) {}
