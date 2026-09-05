package idv.kuan.studio.sango.repository.definition;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Json;

import idv.kuan.studio.sango.domain.definition.CityDefinition;
import idv.kuan.studio.sango.domain.definition.FactionDefinition;
import idv.kuan.studio.sango.domain.definition.ScenarioDefinition;
import idv.kuan.studio.sango.repository.GameDefinitionRepository;

/**
 * 從 assets JSON 載入 Definition，並在啟動時驗證所有 ID 關聯。
 */
public final class AssetJsonGameDefinitionRepository implements GameDefinitionRepository {
    private static final String SCENARIO_PATH = "data/scenarios/scenarios.json";
    private static final String FACTION_PATH = "data/factions/factions.json";
    private static final String CITY_PATH = "data/cities/cities.json";

    private final Map<String, ScenarioDefinition> scenariosById = new LinkedHashMap<>();
    private final Map<String, FactionDefinition> factionsById = new LinkedHashMap<>();
    private final Map<String, CityDefinition> citiesById = new LinkedHashMap<>();

    public AssetJsonGameDefinitionRepository() {
        this(
            Gdx.files.internal(SCENARIO_PATH),
            Gdx.files.internal(FACTION_PATH),
            Gdx.files.internal(CITY_PATH)
        );
    }

    public AssetJsonGameDefinitionRepository(
        FileHandle scenarioFile,
        FileHandle factionFile,
        FileHandle cityFile
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

        indexScenarios(scenarioDocument.scenarios);
        indexFactions(factionDocument.factions);
        indexCities(cityDocument.cities);
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
    public List<FactionDefinition> findFactionsForScenario(String scenarioId) {
        ScenarioDefinition scenarioDefinition = requireScenario(scenarioId);
        List<FactionDefinition> factionDefinitions = new ArrayList<>();
        for (String factionId : scenarioDefinition.factionIds) {
            factionDefinitions.add(requireFaction(factionId));
        }
        return java.util.Collections.unmodifiableList(factionDefinitions);
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
                throw new IllegalStateException("ScenarioDefinition.actionPointsPerTurn 必須大於或等於 1。");
            }
            if (scenarioDefinition.factionIds == null || scenarioDefinition.factionIds.length == 0) {
                throw new IllegalStateException("ScenarioDefinition.factionIds 不可為空。");
            }
            Set<String> factionIds = new HashSet<>();
            for (String factionId : scenarioDefinition.factionIds) {
                requireText(factionId, "ScenarioDefinition.factionIds[]");
                if (!factionIds.add(factionId)) {
                    throw new IllegalStateException(
                        "ScenarioDefinition.factionIds 重複：" + factionId
                    );
                }
            }
            putUnique(scenariosById, scenarioDefinition.id, scenarioDefinition, "劇本");
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
            requireText(factionDefinition.capitalCityId, "FactionDefinition.capitalCityId");
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
            requireNonNegative(cityDefinition.initialTroops, "CityDefinition.initialTroops");
            requireRange(cityDefinition.initialPublicOrder, "CityDefinition.initialPublicOrder");
            requireRange(cityDefinition.initialTraining, "CityDefinition.initialTraining");
            putUnique(citiesById, cityDefinition.id, cityDefinition, "城池");
        }
    }

    private void validateReferences() {
        for (ScenarioDefinition scenarioDefinition : scenariosById.values()) {
            for (String factionId : scenarioDefinition.factionIds) {
                requireFaction(factionId);
            }
        }
        for (FactionDefinition factionDefinition : factionsById.values()) {
            requireCity(factionDefinition.capitalCityId);
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
}
