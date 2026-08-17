package fr.wavy.tresorerie.service.importreleve;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;

@Component
public class ReleveBancaireExcelParser extends ReleveBancaireParserSupport {
    private final DataFormatter formatter = new DataFormatter(java.util.Locale.FRANCE);

    public ResultatParsingReleve parser(InputStream inputStream) throws IOException {
        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            Row header = sheet.getRow(sheet.getFirstRowNum());
            if (header == null) {
                return new ResultatParsingReleve(List.of(), List.of("Fichier Excel vide."), 0);
            }
            Map<String, Integer> index = indexColonnes(valeursTexte(header));
            List<LigneReleveBancaire> lignes = new ArrayList<>();
            List<String> erreurs = new ArrayList<>();
            int nombreLignes = 0;
            for (int i = sheet.getFirstRowNum() + 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }
                List<Object> valeurs = valeurs(row);
                if (ligneVide(valeurs)) {
                    continue;
                }
                nombreLignes++;
                try {
                    lignes.add(ligneDepuisValeursObjets(i + 1, valeurs, index));
                } catch (RuntimeException exception) {
                    erreurs.add("Ligne " + (i + 1) + " : " + exception.getMessage());
                }
            }
            return new ResultatParsingReleve(lignes, erreurs, nombreLignes);
        }
    }

    private List<String> valeursTexte(Row row) {
        List<String> valeurs = new ArrayList<>();
        short lastCellNum = row.getLastCellNum();
        for (int i = 0; i < Math.max(lastCellNum, 0); i++) {
            valeurs.add(formatter.formatCellValue(row.getCell(i)));
        }
        return valeurs;
    }

    private List<Object> valeurs(Row row) {
        List<Object> valeurs = new ArrayList<>();
        short lastCellNum = row.getLastCellNum();
        for (int i = 0; i < Math.max(lastCellNum, 0); i++) {
            Cell cell = row.getCell(i);
            if (cell != null && cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
                valeurs.add(cell.getLocalDateTimeCellValue().toLocalDate());
            } else {
                valeurs.add(formatter.formatCellValue(cell));
            }
        }
        return valeurs;
    }
}
