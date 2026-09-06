package idv.kuan.studio.sango.application.command;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import idv.kuan.studio.sango.SangoVersion;
import idv.kuan.studio.sango.application.request.NewGameRequest;
import idv.kuan.studio.sango.domain.definition.CampaignStartDefinition;
import idv.kuan.studio.sango.domain.definition.CityDefinition;
import idv.kuan.studio.sango.domain.definition.FactionDefinition;
import idv.kuan.studio.sango.domain.definition.FactionPlacementDefinition;
import idv.kuan.studio.sango.domain.definition.MapCityNodeDefinition;
import idv.kuan.studio.sango.domain.definition.ScenarioDefinition;
import idv.kuan.studio.sango.domain.definition.StrategicMapDefinition;
import idv.kuan.studio.sango.domain.model.ArmyState;
import idv.kuan.studio.sango.domain.model.BattleReport;
import idv.kuan.studio.sango.domain.model.CityState;
import idv.kuan.studio.sango.domain.model.FactionState;
import idv.kuan.studio.sango.domain.model.GameState;
import idv.kuan.studio.sango.domain.model.GameplayStatus;
import idv.kuan.studio.sango.domain.model.ScenarioObjectiveStatus;
import idv.kuan.studio.sango.domain.model.GameStateValidator;
import idv.kuan.studio.sango.repository.GameDefinitionRepository;
import idv.kuan.studio.sango.repository.SaveGameRepository;

/**
 * 依 Definition 建立戰局；全國劇本採固定多勢力配置，舊六城劇本維持原配置。
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

        FactionDefinition playerFactionDefinition = definitionRepository.requireFaction(
            request.getPlayerFactionId()
        );
        FactionDefinition opponentFactionDefinition = definitionRepository.requireFaction(
            scenarioDefinition.opponentFactionId
        );
        FactionDefinition neutralFactionDefinition = definitionRepository.requireFaction(
            scenarioDefinition.neutralFactionId
        );
        CampaignStartDefinition campaignStartDefinition = scenarioDefinition.requirePlayerStart(
            request.getPlayerFactionId()
        );
        StrategicMapDefinition mapDefinition = definitionRepository.requireMap(
            scenarioDefinition.mapId
        );

        GameState gameState = createGameState(
            scenarioDefinition,
            mapDefinition,
            campaignStartDefinition,
            playerFactionDefinition,
            opponentFactionDefinition,
            neutralFactionDefinition
        );
        GameStateValidator.validate(gameState);
        saveGameRepository.save(slotNumber, gameState);
        return gameState;
    }

    private GameState createGameState(
        ScenarioDefinition scenarioDefinition,
        StrategicMapDefinition mapDefinition,
        CampaignStartDefinition campaignStartDefinition,
        FactionDefinition playerFactionDefinition,
        FactionDefinition opponentFactionDefinition,
        FactionDefinition neutralFactionDefinition
    ) {
        if (scenarioDefinition.initialFactions != null) {
            return createFixedPlacementGame(
                scenarioDefinition, mapDefinition, campaignStartDefinition, playerFactionDefinition
            );
        }
        CityState[] cityStates = new CityState[mapDefinition.nodes.length];
        String neutralCapitalCityId = null;
        for (int i = 0; i < mapDefinition.nodes.length; i++) {
            MapCityNodeDefinition nodeDefinition = mapDefinition.nodes[i];
            CityDefinition cityDefinition = definitionRepository.requireCity(nodeDefinition.cityId);
            String ownerFactionId;
            if (cityDefinition.id.equals(campaignStartDefinition.startCityId)) {
                ownerFactionId = playerFactionDefinition.id;
            } else if (cityDefinition.id.equals(campaignStartDefinition.targetCityId)) {
                ownerFactionId = opponentFactionDefinition.id;
            } else {
                ownerFactionId = neutralFactionDefinition.id;
                if (neutralCapitalCityId == null) {
                    neutralCapitalCityId = cityDefinition.id;
                }
            }
            cityStates[i] = createCityState(cityDefinition, ownerFactionId);
        }
        if (neutralCapitalCityId == null) {
            throw new IllegalStateException("劇本至少需要一座中立城池。");
        }

        FactionState playerFactionState = createFactionState(
            playerFactionDefinition,
            campaignStartDefinition.startCityId,
            true
        );
        FactionState opponentFactionState = createFactionState(
            opponentFactionDefinition,
            campaignStartDefinition.targetCityId,
            true
        );
        FactionState neutralFactionState = createFactionState(
            neutralFactionDefinition,
            neutralCapitalCityId,
            true
        );

        GameState gameState = new GameState();
        gameState.schemaVersion = SangoVersion.GAME_STATE_SCHEMA_VERSION;
        gameState.scenarioId = scenarioDefinition.id;
        gameState.mapId = scenarioDefinition.mapId;
        gameState.playerFactionId = playerFactionDefinition.id;
        gameState.opponentFactionId = opponentFactionDefinition.id;
        gameState.neutralFactionId = neutralFactionDefinition.id;
        gameState.victoryTargetCityId = campaignStartDefinition.targetCityId;
        gameState.scenarioObjectiveStatus = ScenarioObjectiveStatus.IN_PROGRESS;
        gameState.gameplayStatus = GameplayStatus.ACTIVE;
        gameState.currentTurn = scenarioDefinition.initialTurn;
        gameState.currentYear = scenarioDefinition.startYear;
        gameState.currentMonth = scenarioDefinition.startMonth;
        gameState.elapsedMonths = 0;
        gameState.turnLimitMonths = scenarioDefinition.turnLimitMonths;
        gameState.actionPointsPerTurn = scenarioDefinition.actionPointsPerTurn;
        gameState.actionPointsRemaining = scenarioDefinition.actionPointsPerTurn;
        gameState.enemyAttackCountdown = scenarioDefinition.enemyAttackDelayMonths;
        gameState.nextArmySequence = 1;
        gameState.nextBattleSequence = 1;
        gameState.lastActionCode = "NEW_GAME";
        gameState.factionStates = new FactionState[] {
            playerFactionState,
            opponentFactionState,
            neutralFactionState
        };
        gameState.cityStates = cityStates;
        gameState.armyStates = new ArmyState[0];
        gameState.battleReports = new BattleReport[0];
        return gameState;
    }

    private GameState createFixedPlacementGame(
        ScenarioDefinition scenarioDefinition,
        StrategicMapDefinition mapDefinition,
        CampaignStartDefinition playerStart,
        FactionDefinition playerFaction
    ) {
        Map<String, String> ownersByCityId = new LinkedHashMap<>();
        List<FactionState> factionStates = new ArrayList<>();
        for (FactionPlacementDefinition placement : scenarioDefinition.initialFactions) {
            for (String cityId : placement.cityIds) {
                ownersByCityId.put(cityId, placement.factionId);
            }
            factionStates.add(createFactionState(
                definitionRepository.requireFaction(placement.factionId),
                placement.capitalCityId,
                true
            ));
        }
        CityState[] cityStates = new CityState[mapDefinition.nodes.length];
        for (int i = 0; i < mapDefinition.nodes.length; i++) {
            CityDefinition cityDefinition = definitionRepository.requireCity(
                mapDefinition.nodes[i].cityId
            );
            cityStates[i] = createCityState(cityDefinition, ownersByCityId.get(cityDefinition.id));
        }
        GameState gameState = new GameState();
        gameState.schemaVersion = SangoVersion.GAME_STATE_SCHEMA_VERSION;
        gameState.scenarioId = scenarioDefinition.id;
        gameState.mapId = mapDefinition.id;
        gameState.playerFactionId = playerFaction.id;
        gameState.opponentFactionId = scenarioDefinition.opponentFactionId;
        gameState.neutralFactionId = scenarioDefinition.neutralFactionId;
        gameState.victoryTargetCityId = playerStart.targetCityId;
        gameState.scenarioObjectiveStatus = ScenarioObjectiveStatus.IN_PROGRESS;
        gameState.gameplayStatus = GameplayStatus.ACTIVE;
        gameState.currentTurn = scenarioDefinition.initialTurn;
        gameState.currentYear = scenarioDefinition.startYear;
        gameState.currentMonth = scenarioDefinition.startMonth;
        gameState.turnLimitMonths = scenarioDefinition.turnLimitMonths;
        gameState.actionPointsPerTurn = scenarioDefinition.actionPointsPerTurn;
        gameState.actionPointsRemaining = scenarioDefinition.actionPointsPerTurn;
        gameState.enemyAttackCountdown = scenarioDefinition.enemyAttackDelayMonths;
        gameState.nextArmySequence = 1;
        gameState.nextBattleSequence = 1;
        gameState.lastActionCode = "NEW_GAME";
        gameState.factionStates = factionStates.toArray(new FactionState[0]);
        gameState.cityStates = cityStates;
        gameState.armyStates = new ArmyState[0];
        gameState.battleReports = new BattleReport[0];
        return gameState;
    }

    private FactionState createFactionState(
        FactionDefinition factionDefinition,
        String capitalCityId,
        boolean active
    ) {
        FactionState factionState = new FactionState();
        factionState.factionId = factionDefinition.id;
        factionState.capitalCityId = capitalCityId;
        factionState.gold = factionDefinition.initialGold;
        factionState.food = factionDefinition.initialFood;
        factionState.active = active;
        return factionState;
    }

    private CityState createCityState(
        CityDefinition cityDefinition,
        String ownerFactionId
    ) {
        CityState cityState = new CityState();
        cityState.cityId = cityDefinition.id;
        cityState.ownerFactionId = ownerFactionId;
        cityState.population = cityDefinition.initialPopulation;
        cityState.agriculture = cityDefinition.initialAgriculture;
        cityState.commerce = cityDefinition.initialCommerce;
        cityState.waterControl = cityDefinition.initialWaterControl;
        cityState.defense = cityDefinition.initialDefense;
        cityState.troops = cityDefinition.initialTroops;
        cityState.publicOrder = cityDefinition.initialPublicOrder;
        cityState.training = cityDefinition.initialTraining;
        cityState.morale = cityDefinition.initialMorale;
        cityState.harvestModifierPercent = 100;
        cityState.scoutedUntilTurn = 0;
        return cityState;
    }
}
