package idv.kuan.studio.sango.repository;

import java.util.List;

import idv.kuan.studio.sango.domain.definition.CityDefinition;
import idv.kuan.studio.sango.domain.definition.FactionDefinition;
import idv.kuan.studio.sango.domain.definition.ScenarioDefinition;

/**
 * 讀取固定劇本、勢力與城池資料的介面。
 */
public interface GameDefinitionRepository {
    ScenarioDefinition requireScenario(String scenarioId);

    FactionDefinition requireFaction(String factionId);

    CityDefinition requireCity(String cityId);

    List<FactionDefinition> findFactionsForScenario(String scenarioId);
}
