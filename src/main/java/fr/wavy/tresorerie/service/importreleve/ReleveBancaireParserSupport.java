package fr.wavy.tresorerie.service.importreleve;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.poi.ss.usermodel.DateUtil;

abstract class ReleveBancaireParserSupport {
    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("d/M/yyyy"),
            formatterDeuxChiffres("dd/MM/"),
            formatterDeuxChiffres("d/M/"),
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("d-M-yyyy")
    );

    protected Map<String, Integer> indexColonnes(List<String> entetes) {
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < entetes.size(); i++) {
            index.put(normaliserEntete(entetes.get(i)), i);
        }
        return index;
    }

    protected Integer colonne(Map<String, Integer> index, String... noms) {
        for (String nom : noms) {
            Integer position = index.get(normaliserEntete(nom));
            if (position != null) {
                return position;
            }
        }
        return null;
    }

    protected String valeur(List<String> valeurs, Integer index) {
        if (index == null || index < 0 || index >= valeurs.size()) {
            return null;
        }
        String valeur = valeurs.get(index);
        return valeur == null || valeur.isBlank() ? null : valeur.trim();
    }

    protected Object valeurObjet(List<?> valeurs, Integer index) {
        if (index == null || index < 0 || index >= valeurs.size()) {
            return null;
        }
        Object valeur = valeurs.get(index);
        if (valeur instanceof String texte && texte.isBlank()) {
            return null;
        }
        return valeur;
    }

    protected boolean ligneVide(List<?> valeurs) {
        return valeurs.stream().allMatch(v -> v == null || v.toString().isBlank());
    }

    protected BigDecimal montant(String valeur) {
        return parseMontantFrancais(valeur);
    }

    protected BigDecimal parseMontantFrancais(String valeur) {
        if (valeur == null || valeur.isBlank()) {
            return null;
        }
        String nettoye = valeur.trim();
        boolean negatifParentheses = nettoye.startsWith("(") && nettoye.endsWith(")");
        nettoye = nettoye
                .replace("(", "")
                .replace(")", "")
                .replace("€", "")
                .replace("+", "")
                .replace(" ", "")
                .replace("\u00a0", "")
                .replace("\u202f", "")
                .replace("\t", "")
                .replace("\n", "")
                .replace("\r", "");

        if (nettoye.contains(",") && nettoye.contains(".")) {
            nettoye = nettoye.replace(".", "").replace(",", ".");
        } else if (nettoye.contains(",")) {
            nettoye = nettoye.replace(",", ".");
        }
        if (nettoye.isBlank() || "-".equals(nettoye)) {
            return null;
        }
        BigDecimal montant = new BigDecimal(nettoye);
        return negatifParentheses ? montant.negate() : montant;
    }

    protected LocalDate date(String valeur) {
        return date((Object) valeur);
    }

    protected LocalDate date(Object valeur) {
        if (valeur == null) {
            return null;
        }
        if (valeur instanceof LocalDate localDate) {
            return localDate;
        }
        String texte = valeur.toString().trim();
        if (texte.isBlank()) {
            return null;
        }
        try {
            double excel = Double.parseDouble(texte);
            if (DateUtil.isValidExcelDate(excel)) {
                return DateUtil.getLocalDateTime(excel).toLocalDate();
            }
        } catch (NumberFormatException ignored) {
            // Format texte, on continue avec les parseurs usuels.
        }
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(texte, formatter);
            } catch (DateTimeParseException ignored) {
                // On teste le format suivant.
            }
        }
        throw new IllegalArgumentException("date invalide: " + valeur);
    }

    protected String normaliserEntete(String valeur) {
        if (valeur == null) {
            return "";
        }
        return java.text.Normalizer.normalize(valeur, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]", "");
    }

    protected LigneReleveBancaire ligneDepuisValeurs(int numeroLigne, List<String> valeurs, Map<String, Integer> index) {
        return ligneDepuisValeursObjets(numeroLigne, valeurs, index);
    }

    protected LigneReleveBancaire ligneDepuisValeursObjets(int numeroLigne, List<?> valeurs, Map<String, Integer> index) {
        Integer idxDateOperation = colonne(index, "Date opération", "Date operation", "Date");
        Integer idxDateValeur = colonne(index, "Date valeur");
        Integer idxLibelle = colonne(index, "Libellé", "Libelle", "Description");
        Integer idxReference = colonne(index, "Référence", "Reference", "Reference bancaire");
        Integer idxDebit = colonne(index, "Débit euros", "Debit euros", "Débit", "Debit");
        Integer idxCredit = colonne(index, "Crédit euros", "Credit euros", "Crédit", "Credit");
        Integer idxMontant = colonne(index, "Montant");

        LocalDate dateOperation = date(valeurObjet(valeurs, idxDateOperation));
        LocalDate dateValeur = date(valeurObjet(valeurs, idxDateValeur));
        String libelle = texte(valeurObjet(valeurs, idxLibelle));
        if (libelle == null) {
            throw new IllegalArgumentException("libelle absent");
        }

        BigDecimal debit = montant(texte(valeurObjet(valeurs, idxDebit)));
        BigDecimal credit = montant(texte(valeurObjet(valeurs, idxCredit)));
        BigDecimal montant = montant(texte(valeurObjet(valeurs, idxMontant)));

        if (debit != null && debit.signum() != 0) {
            debit = debit.abs();
            credit = BigDecimal.ZERO;
            montant = debit.negate();
        } else if (credit != null && credit.signum() != 0) {
            credit = credit.abs();
            debit = BigDecimal.ZERO;
            montant = credit;
        } else if (montant != null && montant.signum() != 0) {
            if (montant.signum() < 0) {
                debit = montant.abs();
                credit = BigDecimal.ZERO;
            } else {
                credit = montant;
                debit = BigDecimal.ZERO;
            }
        } else {
            throw new IllegalArgumentException("montant absent");
        }

        return new LigneReleveBancaire(
                numeroLigne,
                dateOperation,
                dateValeur,
                libelle,
                texte(valeurObjet(valeurs, idxReference)),
                debit,
                credit,
                montant
        );
    }

    private String texte(Object valeur) {
        if (valeur == null) {
            return null;
        }
        String texte = valeur.toString().trim();
        return texte.isBlank() ? null : texte;
    }

    private static DateTimeFormatter formatterDeuxChiffres(String prefixe) {
        return new DateTimeFormatterBuilder()
                .appendPattern(prefixe)
                .appendValueReduced(ChronoField.YEAR, 2, 2, 2000)
                .toFormatter();
    }
}
