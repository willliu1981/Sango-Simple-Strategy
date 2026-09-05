package idv.kuan.studio.sango.application.command;

import java.util.Arrays;

import idv.kuan.studio.sango.SangoVersion;
import idv.kuan.studio.sango.application.request.NewGameRequest;
import idv.kuan.studio.sango.domain.definition.CityDefinition;
import idv.kuan.studio.sango.domain.definition.FactionDefinition;
import idv.kuan.studio.sango.domain.definition.ScenarioDefinition;
import idv.kuan.studio.sango.domain.model.CityState;
import idv.kuan.studio.sango.domain.model.FactionState;
import idv.kuan.studio.sango.domain.model.GameState;
import idv.kuan.studio.sango.domain.model.GameStateValidator;
import idv.kuan.studio.sango.repository.GameDefinitionRepository;
import idv.kuan.studio.sango.repository.SaveGameRepository;

/**
 * 依 Definition 建立第一份 GameState，驗證後再保存。
 */
public final class NewGameCommand {
    private final GameDefinitionRepository definitionRepository;
    private final SaveGameRepository saveGameRepository;

    public NewGameCommand(
        GameDefinitionRepository definitionRepository,
        SaveGameRepository saveGameRepository
    ) {
        this.definitionRepository = definitionRepository;
        this.saveGameRepository = saveGameRepository;
    }

    public GameState execute(int slotNumber, NewGameRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request 不可為 null。");
        }

        ScenarioDefinition scenarioDefinition = definitionRepository.requireScenario(
            request.getScenarioId()
        );
        if (!Arrays.asList(scenarioDefinition.factionIds).contains(request.getPlayerFactionId())) {
            throw new IllegalArgumentException(
                "所選勢力不屬於劇本：" + request.getPlayerFactionId()
            );
        }

        FactionDefinition factionDefinition = definitionRepository.requireFaction(
            request.getPlayerFactionId()
        );
        CityDefinition cityDefinition = definitionRepository.requireCity(
            factionDefinition.capitalCityId
        );

        GameState gameState = createGameState(
            scenarioDefinition,
            factionDefinition,
            cityDefinition
        );
        GameStateValidator.validate(gameState);
        saveGameRepository.save(slotNumber, gameState);
        return gameState;
    }

    private GameState createGameState(
        ScenarioDefinition scenarioDefinition,
        FactionDefinition factionDefinition,
        CityDefinition cityDefinition
    ) {
        FactionState factionState = new FactionState();
        factionState.factionId = factionDefinition.id;
        factionState.capitalCityId = factionDefinition.capitalCityId;
        factionState.gold = factionDefinition.initialGold;
        factionState.food = factionDefinition.initialFood;

        CityState cityState = new CityState();
        cityState.cityId = cityDefinition.id;
        cityState.ownerFactionId = factionDefinition.id;
        cityState.population = cityDefinition.initialPopulation;
        cityState.agriculture = cityDefinition.initialAgriculture;
        cityState.commerce = cityDefinition.initialCommerce;
        cityState.troops = cityDefinition.initialTroops;
        cityState.publicOrder = cityDefinition.initialPublicOrder;
        cityState.training = cityDefinition.initialTraining;

        GameState gameState = new GameState();
        gameState.schemaVersion = SangoVersion.GAME_STATE_SCHEMA_VERSION;
        gameState.scenarioId = scenarioDefinition.id;
        gameState.playerFactionId = factionDefinition.id;
        gameState.currentTurn = scenarioDefinition.initialTurn;
        gameState.currentYear = scenarioDefinition.startYear;
        gameState.currentMonth = scenarioDefinition.startMonth;
        gameState.actionPointsPerTurn = scenarioDefinition.actionPointsPerTurn;
        gameState.actionPointsRemaining = scenarioDefinition.actionPointsPerTurn;
        gameState.lastActionCode = "NEW_GAME";
        gameState.factionStates = new FactionState[] { factionState };
        gameState.cityStates = new CityState[] { cityState };
        return gameState;
    }
}
