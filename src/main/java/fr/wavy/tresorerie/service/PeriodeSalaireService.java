package fr.wavy.tresorerie.service;

import java.time.LocalDate;
import java.time.YearMonth;

import org.springframework.stereotype.Component;

@Component
public class PeriodeSalaireService {

    public YearMonth determiner(LocalDate dateOperation) {
        YearMonth moisOperation = YearMonth.from(dateOperation);
        return dateOperation.getDayOfMonth() <= 20 ? moisOperation.minusMonths(1) : moisOperation;
    }
}
