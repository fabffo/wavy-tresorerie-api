package fr.wavy.tresorerie.service.importreleve;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

@Component
public class ReleveBancaireCsvParser extends ReleveBancaireParserSupport {
    public ResultatParsingReleve parser(InputStream inputStream) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            if (header == null) {
                return new ResultatParsingReleve(List.of(), List.of("Fichier CSV vide."), 0);
            }
            char separateur = header.contains(";") ? ';' : ',';
            Map<String, Integer> index = indexColonnes(decouper(header, separateur));
            List<LigneReleveBancaire> lignes = new ArrayList<>();
            List<String> erreurs = new ArrayList<>();
            String line;
            int numero = 1;
            int nombreLignes = 0;
            while ((line = reader.readLine()) != null) {
                numero++;
                List<String> valeurs = decouper(line, separateur);
                if (ligneVide(valeurs)) {
                    continue;
                }
                nombreLignes++;
                try {
                    lignes.add(ligneDepuisValeurs(numero, valeurs, index));
                } catch (RuntimeException exception) {
                    erreurs.add("Ligne " + numero + " : " + exception.getMessage());
                }
            }
            return new ResultatParsingReleve(lignes, erreurs, nombreLignes);
        }
    }

    private List<String> decouper(String ligne, char separateur) {
        List<String> valeurs = new ArrayList<>();
        StringBuilder courant = new StringBuilder();
        boolean entreGuillemets = false;
        for (int i = 0; i < ligne.length(); i++) {
            char c = ligne.charAt(i);
            if (c == '"') {
                if (entreGuillemets && i + 1 < ligne.length() && ligne.charAt(i + 1) == '"') {
                    courant.append('"');
                    i++;
                } else {
                    entreGuillemets = !entreGuillemets;
                }
            } else if (c == separateur && !entreGuillemets) {
                valeurs.add(courant.toString());
                courant.setLength(0);
            } else {
                courant.append(c);
            }
        }
        valeurs.add(courant.toString());
        return valeurs;
    }
}
