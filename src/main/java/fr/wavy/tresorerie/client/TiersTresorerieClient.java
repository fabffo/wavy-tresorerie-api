package fr.wavy.tresorerie.client;

import java.util.List;

import fr.wavy.tresorerie.configuration.ContexteRequete;
import fr.wavy.tresorerie.dto.TiersTresorerieDto;

public interface TiersTresorerieClient {
    List<TiersTresorerieDto> listerTiersActifs(ContexteRequete contexte);
    List<TiersTresorerieDto> listerSalaries(ContexteRequete contexte);
}
