package fr.wavy.tresorerie.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.YearMonth;

import org.junit.jupiter.api.Test;

class PeriodeSalaireServiceTest {
    private final PeriodeSalaireService service = new PeriodeSalaireService();

    @Test
    void conserveLeMoisPourUneOperationApresLe20() {
        assertThat(service.determiner(LocalDate.of(2026, 1, 28))).isEqualTo(YearMonth.of(2026, 1));
        assertThat(service.determiner(LocalDate.of(2026, 2, 21))).isEqualTo(YearMonth.of(2026, 2));
    }

    @Test
    void prendLeMoisPrecedentJusquAu20Inclus() {
        assertThat(service.determiner(LocalDate.of(2026, 2, 20))).isEqualTo(YearMonth.of(2026, 1));
    }

    @Test
    void gereLeChangementDAnnee() {
        assertThat(service.determiner(LocalDate.of(2026, 1, 10))).isEqualTo(YearMonth.of(2025, 12));
    }
}
