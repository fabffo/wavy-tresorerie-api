package fr.wavy.tresorerie.service.importreleve;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class ReleveBancaireParserSupportTest {
    private final TestSupport support = new TestSupport();

    @Test
    void parseLesFormatsDeDatesFrancaisCourants() {
        assertThat(support.parseDate("1/1/26")).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(support.parseDate("01/01/26")).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(support.parseDate("1/01/2026")).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(support.parseDate("01/01/2026")).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(support.parseDate("2026-01-01")).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(support.parseDate("01-01-2026")).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(support.parseDate("1-1-2026")).isEqualTo(LocalDate.of(2026, 1, 1));
    }

    @Test
    void importeUneCelluleExcelDateSansConversionTextePrealable() throws Exception {
        byte[] fichier;
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            CreationHelper helper = workbook.getCreationHelper();
            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(helper.createDataFormat().getFormat("m/d/yy"));

            Sheet sheet = workbook.createSheet("Releve");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Date");
            header.createCell(1).setCellValue("Libellé");
            header.createCell(2).setCellValue("Débit euros");
            header.createCell(3).setCellValue("Crédit euros");

            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue(java.sql.Date.valueOf(LocalDate.of(2026, 1, 1)));
            row.getCell(0).setCellStyle(dateStyle);
            row.createCell(1).setCellValue("CARTE TEST");
            row.createCell(2).setCellValue("1,00");
            row.createCell(3).setCellValue("");

            workbook.write(output);
            fichier = output.toByteArray();
        }

        ResultatParsingReleve resultat = new ReleveBancaireExcelParser().parser(new ByteArrayInputStream(fichier));

        assertThat(resultat.erreurs()).isEmpty();
        assertThat(resultat.lignes()).hasSize(1);
        assertThat(resultat.lignes().get(0).dateOperation()).isEqualTo(LocalDate.of(2026, 1, 1));
    }

    @Test
    void parseLesMontantsFrancaisAvecEspacesEtSymboles() {
        assertThat(support.parseMontant("1 234,56")).isEqualByComparingTo(new BigDecimal("1234.56"));
        assertThat(support.parseMontant("1\u00a0234,56")).isEqualByComparingTo(new BigDecimal("1234.56"));
        assertThat(support.parseMontant("1\u202f234,56")).isEqualByComparingTo(new BigDecimal("1234.56"));
        assertThat(support.parseMontant("1234,56")).isEqualByComparingTo(new BigDecimal("1234.56"));
        assertThat(support.parseMontant("-1 234,56")).isEqualByComparingTo(new BigDecimal("-1234.56"));
        assertThat(support.parseMontant("1.234,56")).isEqualByComparingTo(new BigDecimal("1234.56"));
        assertThat(support.parseMontant("1 234.56")).isEqualByComparingTo(new BigDecimal("1234.56"));
        assertThat(support.parseMontant("0,00")).isEqualByComparingTo(new BigDecimal("0.00"));
        assertThat(support.parseMontant("\t1\u202f234,56 €\n")).isEqualByComparingTo(new BigDecimal("1234.56"));
        assertThat(support.parseMontant("")).isNull();
    }

    private static class TestSupport extends ReleveBancaireParserSupport {
        LocalDate parseDate(String valeur) {
            return date(valeur);
        }

        BigDecimal parseMontant(String valeur) {
            return parseMontantFrancais(valeur);
        }
    }
}
