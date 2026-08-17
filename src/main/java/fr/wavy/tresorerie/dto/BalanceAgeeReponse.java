package fr.wavy.tresorerie.dto;

import java.math.BigDecimal;

public record BalanceAgeeReponse(
        BigDecimal nonEchu,
        BigDecimal retard0A30,
        BigDecimal retard31A60,
        BigDecimal retard61A90,
        BigDecimal retardPlus90
) {
}
