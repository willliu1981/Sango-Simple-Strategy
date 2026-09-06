package idv.kuan.studio.sango.repository.definition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Json;

import idv.kuan.studio.sango.domain.definition.CampaignStartDefinition;
import idv.kuan.studio.sango.domain.definition.CityConnectionDefinition;
import idv.kuan.studio.sango.domain.definition.CityDefinition;
import idv.kuan.studio.sango.domain.definition.FactionDefinition;
import idv.kuan.studio.sango.domain.definition.MapCityNodeDefinition;
import idv.kuan.studio.sango.domain.definition.ScenarioDefinition;
import idv.kuan.studio.sango.domain.definition.StrategicMapDefinition;
import idv.kuan.studio.sango.repository.GameDefinitionRepository;

/**
 * 從 assets JSON 載入 Definition，並在啟動時驗證所有 ID 關聯。
 */
public final class AssetJsonGameDefinitionRepository implements GameDefinitionRepository {
    private static final String SCENARIO_PATH = "data/scenarios/scenarios.json";
    private static final String FACTION_PATH = "data/factions/factions.json";
    private static final String CITY_PATH = "data/cities/cities.json";
    private static final String MAP_PATH = "data/maps/maps.json";

    private final Map<String, ScenarioDefinition> scenariosById = new LinkedHashMap<>();
    private final Map<String, FactionDefinition> factionsById = new LinkedHashMap<>();
    private final Map<String, CityDefinition> citiesById = new LinkedHashMap<>();
    private final Map<String, StrategicMapDefinition> mapsById = new LinkedHashMap<>();

    public AssetJsonGameDefinitionRepository() {
        this(
            Gdx.files.internal(SCENARIO_PATH),
            Gdx.files.internal(FACTION_PATH),
            Gdx.files.internal(CITY_PATH),
            Gdx.files.internal(MAP_PATH)
        );
    }

    public AssetJsonGameDefinitionRepository(
        FileHandle scenarioFile,
        FileHandle factionFile,
        FileHandle cityFile,
        FileHandle mapFile
    ) {
        Json json = createJson();
        ScenarioDocument scenarioDocument = readRequired(
            json,
            ScenarioDocument.class,
            scenarioFile,
            "劇本"
        );
        FactionDocument factionDocument = readRequired(
            json,
            FactionDocument.class,
            factionFile,
            "勢力"
        );
        CityDocument cityDocument = readRequired(
            json,
            CityDocument.class,
            cityFile,
            "城池"
        );
        MapDocument mapDocument = readRequired(
            json,
            MapDocument.class,
            mapFile,
            "戰略地圖"
        );

        indexScenarios(scenarioDocument.scenarios);
        indexFactions(factionDocument.factions);
        indexCities(cityDocument.cities);
        indexMaps(mapDocument.maps);
        validateReferences();
    }

    @Override
    public ScenarioDefinition requireScenario(String scenarioId) {
        ScenarioDefinition scenarioDefinition = scenariosById.get(scenarioId);
        if (scenarioDefinition == null) {
            throw new IllegalArgumentException("找不到劇本 Definition：" + scenarioId);
        }
        return scenarioDefinition;
    }

    @Override
    public FactionDefinition requireFaction(String factionId) {
        FactionDefinition factionDefinition = factionsById.get(factionId);
        if (factionDefinition == null) {
            throw new IllegalArgumentException("找不到勢力 Definition：" + factionId);
        }
        return factionDefinition;
    }

    @Override
    public CityDefinition requireCity(String cityId) {
        CityDefinition cityDefinition = citiesById.get(cityId);
        if (cityDefinition == null) {
            throw new IllegalArgumentException("找不到城池 Definition：" + cityId);
        }
        return cityDefinition;
    }

    @Override
    public StrategicMapDefinition requireMap(String mapId) {
        StrategicMapDefinition mapDefinition = mapsById.get(mapId);
        if (mapDefinition == null) {
            throw new IllegalArgumentException("找不到戰略地圖 Definition：" + mapId);
        }
        return mapDefinition;
    }

    @Override
    public CampaignStartDefinition requireCampaignStart(
        String scenarioId,
        String playerFactionId
    ) {
        return requireScenario(scenarioId).requirePlayerStart(playerFactionId);
    }

    @Override
    public List<FactionDefinition> findFactionsForScenario(String scenarioId) {
        ScenarioDefinition scenarioDefinition = requireScenario(scenarioId);
        List<FactionDefinition> factionDefinitions = new ArrayList<>();
        for (String factionId : scenarioDefinition.factionIds) {
            FactionDefinition factionDefinition = requireFaction(factionId);
            if (factionDefinition.playable) {
                factionDefinitions.add(factionDefinition);
            }
        }
        return Collections.unmodifiableList(factionDefinitions);
    }

    private Json createJson() {
        Json json = new Json();
        json.setIgnoreUnknownFields(false);
        json.setUsePrototypes(false);
        return json;
    }

    private <T> T readRequired(
        Json json,
        Class<T> documentType,
        FileHandle fileHandle,
        String dataLabel
    ) {
        if (fileHandle == null || !fileHandle.exists()) {
            throw new IllegalStateException(dataLabel + " Definition 檔案不存在。");
        }
        try {
            T document = json.fromJson(documentType, fileHandle);
            if (document == null) {
                throw new IllegalStateException(dataLabel + " Definition 檔案內容為空。");
            }
            return document;
        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                "無法解析" + dataLabel + " Definition：" + fileHandle.path(),
                exception
            );
        }
    }

    private void indexScenarios(ScenarioDefinition[] scenarioDefinitions) {
        if (scenarioDefinitions == null || scenarioDefinitions.length == 0) {
            throw new IllegalStateException("至少需要一筆 ScenarioDefinition。");
        }
        for (ScenarioDefinition scenarioDefinition : scenarioDefinitions) {
            requireText(scenarioDefinition.id, "ScenarioDefinition.id");
            requireText(scenarioDefinition.nameKey, "ScenarioDefinition.nameKey");
            requireText(scenarioDefinition.descriptionKey, "ScenarioDefinition.descriptionKey");
            requireText(scenarioDefinition.mapId, "ScenarioDefinition.mapId");
            requireText(
                scenarioDefinition.opponentFactionId,
                "ScenarioDefinition.opponentFactionId"
            );
            requireText(
                scenarioDefinition.neutralFactionId,
                "ScenarioDefinition.neutralFactionId"
            );
            if (scenarioDefinition.startYear < 1) {
                throw new IllegalStateException("ScenarioDefinition.startYear 必須大於或等於 1。");
            }
            if (scenarioDefinition.startMonth < 1 || scenarioDefinition.startMonth > 12) {
                throw new IllegalStateException("ScenarioDefinition.startMonth 必須介於 1 到 12。");
            }
            if (scenarioDefinition.initialTurn < 1) {
                throw new IllegalStateException("ScenarioDefinition.initialTurn 必須大於或等於 1。");
            }
            if (scenarioDefinition.actionPointsPerTurn < 1) {
                throw new IllegalStateException(
                    "ScenarioDefinition.actionPointsPerTurn 必須大於或等於 1。"
                );
            }
            if (scenarioDefinition.turnLimitMonths < 1) {
                throw new IllegalStateException(
                    "ScenarioDefinition.turnLimitMonths 必須大於或等於 1。"
                );
            }
            if (scenarioDefinition.enemyAttackDelayMonths < 1) {
                throw new IllegalStateException(
                    "ScenarioDefinition.enemyAttackDelayMonths 必須大於或等於 1。"
                );
            }
            validateUniqueTextArray(
                scenarioDefinition.factionIds,
                "ScenarioDefinition.factionIds"
            );
            validatePlayerStarts(scenarioDefinition);
            putUnique(scenariosById, scenarioDefinition.id, scenarioDefinition, "劇本");
        }
    }

    private void validatePlayerStarts(ScenarioDefinition scenarioDefinition) {
        if (scenarioDefinition.playerStarts == null
            || scenarioDefinition.playerStarts.length != scenarioDefinition.factionIds.length) {
            throw new IllegalStateException(
                "ScenarioDefinition.playerStarts 必須與可選勢力數量一致。"
            );
        }
        Set<String> playerFactionIds = new HashSet<>();
        for (CampaignStartDefinition playerStartDefinition : scenarioDefinition.playerStarts) {
            if (playerStartDefinition == null) {
                throw new IllegalStateException("CampaignStartDefinition 不可為 null。");
            }
            requireText(
                playerStartDefinition.playerFactionId,
                "CampaignStartDefinition.playerFactionId"
            );
            requireText(playerStartDefinition.startCityId, "CampaignStartDefinition.startCityId");
            requireText(playerStartDefinition.targetCityId, "CampaignStartDefinition.targetCityId");
            if (!playerFactionIds.add(playerStartDefinition.playerFactionId)) {
                throw new IllegalStateException(
                    "CampaignStartDefinition.playerFactionId 重複："
                        + playerStartDefinition.playerFactionId
                );
            }
        }
    }

    private void indexFactions(FactionDefinition[] factionDefinitions) {
        if (factionDefinitions == null || factionDefinitions.length == 0) {
            throw new IllegalStateException("至少需要一筆 FactionDefinition。");
        }
        for (FactionDefinition factionDefinition : factionDefinitions) {
            requireText(factionDefinition.id, "FactionDefinition.id");
            requireText(factionDefinition.nameKey, "FactionDefinition.nameKey");
            requireText(factionDefinition.rulerNameKey, "FactionDefinition.rulerNameKey");
            requireText(factionDefinition.summaryKey, "FactionDefinition.summaryKey");
            if (factionDefinition.playable) {
                requireText(factionDefinition.capitalCityId, "FactionDefinition.capitalCityId");
            }
            requireNonNegative(factionDefinition.initialGold, "FactionDefinition.initialGold");
            requireNonNegative(factionDefinition.initialFood, "FactionDefinition.initialFood");
            putUnique(factionsById, factionDefinition.id, factionDefinition, "勢力");
        }
    }

    private void indexCities(CityDefinition[] cityDefinitions) {
        if (cityDefinitions == null || cityDefinitions.length == 0) {
            throw new IllegalStateException("至少需要一筆 CityDefinition。");
        }
        for (CityDefinition cityDefinition : cityDefinitions) {
            requireText(cityDefinition.id, "CityDefinition.id");
            requireText(cityDefinition.nameKey, "CityDefinition.nameKey");
            requireNonNegative(cityDefinition.initialPopulation, "CityDefinition.initialPopulation");
            requireRange(cityDefinition.initialAgriculture, "CityDefinition.initialAgriculture");
            requireRange(cityDefinition.initialCommerce, "CityDefinition.initialCommerce");
            requireRange(cityDefinition.initialWaterControl, "CityDefinition.initialWaterControl");
            requireRange(cityDefinition.initialDefense, "CityDefinition.initialDefense");
            requireNonNegative(cityDefinition.initialTroops, "CityDefinition.initialTroops");
            requireRange(cityDefinition.initialPublicOrder, "CityDefinition.initialPublicOrder");
            requireRange(cityDefinition.initialTraining, "CityDefinition.initialTraining");
            putUnique(citiesById, cityDefinition.id, cityDefinition, "城池");
        }
    }

    private void indexMaps(StrategicMapDefinition[] mapDefinitions) {
        if (mapDefinitions == null || mapDefinitions.length == 0) {
            throw new IllegalStateException("至少需要一筆 StrategicMapDefinition。");
        }
        for (StrategicMapDefinition mapDefinition : mapDefinitions) {
            requireText(mapDefinition.id, "StrategicMapDefinition.id");
            requireText(mapDefinition.nameKey, "StrategicMapDefinition.nameKey");
            if (mapDefinition.nodes == null || mapDefinition.nodes.length < 2) {
                throw new IllegalStateException("StrategicMapDefinition.nodes 至少需要兩座城。");
            }
            Set<String> nodeCityIds = new HashSet<>();
            for (MapCityNodeDefinition nodeDefinition : mapDefinition.nodes) {
                if (nodeDefinition == null) {
                    throw new IllegalStateException("MapCityNodeDefinition 不可為 null。");
                }
                requireText(nodeDefinition.cityId, "MapCityNodeDefinition.cityId");
                if (nodeDefinition.x < 0f || nodeDefinition.x > 1f
                    || nodeDefinition.y < 0f || nodeDefinition.y > 1f) {
                    throw new IllegalStateException("地圖節點座標必須介於 0 到 1。");
                }
                if (!nodeCityIds.add(nodeDefinition.cityId)) {
                    throw new IllegalStateException(
                        "StrategicMapDefinition 城池節點重複：" + nodeDefinition.cityId
                    );
                }
            }
            if (mapDefinition.connections == null || mapDefinition.connections.length == 0) {
                throw new IllegalStateException("StrategicMapDefinition.connections 不可為空。");
            }
            Set<String> connectionKeys = new HashSet<>();
            for (CityConnectionDefinition connectionDefinition : mapDefinition.connections) {
                validateConnection(connectionDefinition, nodeCityIds, connectionKeys);
            }
            putUnique(mapsById, mapDefinition.id, mapDefinition, "地圖");
        }
    }

    private void validateConnection(
        CityConnectionDefinition connectionDefinition,
        Set<String> nodeCityIds,
        Set<String> connectionKeys
    ) {
        if (connectionDefinition == null) {
            throw new IllegalStateException("CityConnectionDefinition 不可為 null。");
        }
        requireText(connectionDefinition.fromCityId, "CityConnectionDefinition.fromCityId");
        requireText(connectionDefinition.toCityId, "CityConnectionDefinition.toCityId");
        if (connectionDefinition.fromCityId.equals(connectionDefinition.toCityId)) {
            throw new IllegalStateException("道路兩端不可為同一座城。");
        }
        if (!nodeCityIds.contains(connectionDefinition.fromCityId)
            || !nodeCityIds.contains(connectionDefinition.toCityId)) {
            throw new IllegalStateException("道路引用了地圖中不存在的城池。");
        }
        if (connectionDefinition.travelMonths < 1) {
            throw new IllegalStateException("道路 travelMonths 必須大於或等於 1。");
        }
        String firstCityId = connectionDefinition.fromCityId.compareTo(
            connectionDefinition.toCityId
        ) <= 0 ? connectionDefinition.fromCityId : connectionDefinition.toCityId;
        String secondCityId = firstCityId.equals(connectionDefinition.fromCityId)
            ? connectionDefinition.toCityId
            : connectionDefinition.fromCityId;
        String connectionKey = firstCityId + "|" + secondCityId;
        if (!connectionKeys.add(connectionKey)) {
            throw new IllegalStateException("重複道路：" + connectionKey);
        }
    }

    private void validateReferences() {
        for (StrategicMapDefinition mapDefinition : mapsById.values()) {
            for (MapCityNodeDefinition nodeDefinition : mapDefinition.nodes) {
                requireCity(nodeDefinition.cityId);
            }
        }
        for (ScenarioDefinition scenarioDefinition : scenariosById.values()) {
            StrategicMapDefinition mapDefinition = requireMap(scenarioDefinition.mapId);
            for (String factionId : scenarioDefinition.factionIds) {
                FactionDefinition factionDefinition = requireFaction(factionId);
                if (!factionDefinition.playable) {
                    throw new IllegalStateException("劇本可選勢力必須標示 playable：" + factionId);
                }
            }
            requireFaction(scenarioDefinition.opponentFactionId);
            requireFaction(scenarioDefinition.neutralFactionId);
            for (CampaignStartDefinition playerStartDefinition : scenarioDefinition.playerStarts) {
                requireFaction(playerStartDefinition.playerFactionId);
                mapDefinition.requireNode(playerStartDefinition.startCityId);
                mapDefinition.requireNode(playerStartDefinition.targetCityId);
                if (mapDefinition.findConnection(
                    playerStartDefinition.startCityId,
                    playerStartDefinition.targetCityId
                ) == null) {
                    throw new IllegalStateException(
                        "玩家起始城與目標城必須直接相鄰："
                            + playerStartDefinition.playerFactionId
                    );
                }
            }
        }
    }

    private void validateUniqueTextArray(String[] values, String fieldName) {
        if (values == null || values.length == 0) {
            throw new IllegalStateException(fieldName + " 不可為空。");
        }
        Set<String> uniqueValues = new HashSet<>();
        for (String value : values) {
            requireText(value, fieldName + "[]");
            if (!uniqueValues.add(value)) {
                throw new IllegalStateException(fieldName + " 重複：" + value);
            }
        }
    }

    private <T> void putUnique(
        Map<String, T> valuesById,
        String valueId,
        T value,
        String dataLabel
    ) {
        if (valuesById.containsKey(valueId)) {
            throw new IllegalStateException(dataLabel + " Definition ID 重複：" + valueId);
        }
        valuesById.put(valueId, value);
    }

    private void requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException(fieldName + " 不可為空。");
        }
    }

    private void requireNonNegative(int value, String fieldName) {
        if (value < 0) {
            throw new IllegalStateException(fieldName + " 不可小於 0。");
        }
    }

    private void requireRange(int value, String fieldName) {
        if (value < 0 || value > 100) {
            throw new IllegalStateException(fieldName + " 必須介於 0 到 100。");
        }
    }

    public static final class ScenarioDocument {
        public ScenarioDefinition[] scenarios;

        public ScenarioDocument() {
        }
    }

    public static final class FactionDocument {
        public FactionDefinition[] factions;

        public FactionDocument() {
        }
    }

    public static final class CityDocument {
        public CityDefinition[] cities;

        public CityDocument() {
        }
    }

    public static final class MapDocument {
        public StrategicMapDefinition[] maps;

        public MapDocument() {
        }
    }
}
