package fr.wavy.tresorerie.service.importreleve;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;

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

    @Test
    void importe89OperationsCreditAgricoleAvecDatesTextuelles() throws Exception {
        byte[] fichier;
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Sheet0");
            Row header = sheet.createRow(0);
            String[] entetes = {"Date d'opération", "Libellé", "Débit euros", "Crédit euros"};
            for (int i = 0; i < entetes.length; i++) {
                header.createCell(i).setCellValue(entetes[i]);
            }
            for (int i = 1; i <= 89; i++) {
                Row row = sheet.createRow(i);
                row.createCell(0).setCellValue("02/01/2026");
                row.createCell(1).setCellValue(i == 1 ? "Cotisation bancaire" : "Operation " + i);
                row.createCell(2).setCellValue("109,00");
                row.createCell(3).setCellValue("");
            }
            workbook.write(output);
            fichier = output.toByteArray();
        }
        ResultatParsingReleve resultat = new ReleveBancaireExcelParser().parser(new ByteArrayInputStream(fichier));
        assertThat(resultat.nombreLignes()).isEqualTo(89);
        assertThat(resultat.erreurs()).isEmpty();
        assertThat(resultat.lignes()).hasSize(89).allSatisfy(ligne ->
                assertThat(ligne.dateOperation()).isEqualTo(LocalDate.of(2026, 1, 2)));
        assertThat(resultat.lignes().get(0).montant()).isEqualByComparingTo("-109.00");
        assertThat(support.normaliserEntete("Date d'opération")).isEqualTo("datedoperation");
    }

    @Test
    void refuseUnFichierSansColonneDateReconnue() {
        String csv = "Jour;Libellé;Débit euros;Crédit euros\n02/01/2026;Test;109,00;";
        assertThatThrownBy(() -> new ReleveBancaireCsvParser().parser(
                new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("colonne de date");
    }

    @Test
    void rejetteLesLignesSansDateOuAvecDateInvalide() throws Exception {
        String csv = "Date d'opération;Libellé;Débit euros;Crédit euros\n"
                + ";Sans date;109,00;\n31/02/2026;Date impossible;109,00;\n"
                + "invalide;Date invalide;109,00;\n02/01/2026;Valide;109,00;";
        ResultatParsingReleve resultat = new ReleveBancaireCsvParser().parser(
                new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));
        assertThat(resultat.lignes()).hasSize(1);
        assertThat(resultat.lignes().get(0).dateOperation()).isEqualTo(LocalDate.of(2026, 1, 2));
        assertThat(resultat.erreurs()).hasSize(3);
        assertThat(resultat.erreurs().get(0)).contains("Ligne 2", "date d’operation absente");
        assertThat(resultat.erreurs().get(1)).contains("Ligne 3", "date invalide");
        assertThat(resultat.erreurs().get(2)).contains("Ligne 4", "date invalide");
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
