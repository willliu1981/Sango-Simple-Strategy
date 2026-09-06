package idv.kuan.studio.sango.repository;

import java.util.List;

import idv.kuan.studio.sango.domain.definition.CampaignStartDefinition;
import idv.kuan.studio.sango.domain.definition.CityDefinition;
import idv.kuan.studio.sango.domain.definition.FactionDefinition;
import idv.kuan.studio.sango.domain.definition.ScenarioDefinition;
import idv.kuan.studio.sango.domain.definition.StrategicMapDefinition;

/**
 * 讀取固定劇本、勢力、城池與地圖資料的介面。
 */
public interface GameDefinitionRepository {
    ScenarioDefinition requireScenario(String scenarioId);

    FactionDefinition requireFaction(String factionId);

    CityDefinition requireCity(String cityId);

    StrategicMapDefinition requireMap(String mapId);

    CampaignStartDefinition requireCampaignStart(String scenarioId, String playerFactionId);

    List<FactionDefinition> findFactionsForScenario(String scenarioId);
}
