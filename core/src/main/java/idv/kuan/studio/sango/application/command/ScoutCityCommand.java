package idv.kuan.studio.sango.application.command;

import idv.kuan.studio.sango.application.result.StrategicActionResult;
import idv.kuan.studio.sango.domain.definition.StrategicMapDefinition;
import idv.kuan.studio.sango.domain.model.CityState;
import idv.kuan.studio.sango.domain.model.FactionState;
import idv.kuan.studio.sango.domain.model.GameState;
import idv.kuan.studio.sango.domain.model.GameplayStatus;
import idv.kuan.studio.sango.domain.model.GameStateValidator;
import idv.kuan.studio.sango.domain.rule.StrategicActionFailureReason;
import idv.kuan.studio.sango.domain.rule.FactionActionPointRules;
import idv.kuan.studio.sango.domain.service.CityIntelligenceService;
import idv.kuan.studio.sango.repository.GameDefinitionRepository;

/**
 * 從己方相鄰城池偵察目標，情報維持三個回合。
 */
public final class ScoutCityCommand {
    public static final int ACTION_POINT_COST = 1;
    public static final int GOLD_COST = 20;
    public static final int INTELLIGENCE_DURATION_TURNS = CityIntelligenceService.DURATION_TURNS;

    private final GameDefinitionRepository definitionRepository;
    private final CityIntelligenceService intelligenceService = new CityIntelligenceService();

    public ScoutCityCommand(GameDefinitionRepository definitionRepository) {
        this.definitionRepository = definitionRepository;
    }

    public StrategicActionResult execute(
        int slotNumber,
        GameState currentState,
        String originCityId,
        String targetCityId
    ) {
        StrategicActionFailureReason failureReason = evaluate(
            currentState,
            currentState.playerFactionId,
            originCityId,
            targetCityId,
            definitionRepository.requireMap(currentState.mapId)
        );
        if (failureReason != StrategicActionFailureReason.NONE) {
            return StrategicActionResult.failure("SCOUT", failureReason);
        }

        GameState nextState = currentState.copy();
        applyInPlace(nextState, nextState.playerFactionId, targetCityId);

        GameStateValidator.validate(nextState);
        return StrategicActionResult.success("SCOUT", nextState, 0);
    }

    public StrategicActionFailureReason executeInPlace(
        GameState gameState,
        StrategicMapDefinition mapDefinition,
        String observerFactionId,
        String originCityId,
        String targetCityId
    ) {
        StrategicActionFailureReason failureReason = evaluate(gameState, observerFactionId,
            originCityId, targetCityId, mapDefinition);
        if (failureReason == StrategicActionFailureReason.NONE) {
            applyInPlace(gameState, observerFactionId, targetCityId);
        }
        return failureReason;
    }

    public StrategicActionFailureReason evaluate(
        GameState gameState,
        String observerFactionId,
        String originCityId,
        String targetCityId,
        StrategicMapDefinition mapDefinition
    ) {
        GameStateValidator.validate(gameState);
        CityState originCityState = gameState.requireCityState(originCityId);
        CityState targetCityState = gameState.requireCityState(targetCityId);
        if (gameState.gameplayStatus != GameplayStatus.ACTIVE) {
            return StrategicActionFailureReason.PLAYER_ELIMINATED;
        }
        if (!observerFactionId.equals(originCityState.ownerFactionId)) {
            return StrategicActionFailureReason.ORIGIN_NOT_OWNED;
        }
        if (observerFactionId.equals(targetCityState.ownerFactionId)) {
            return StrategicActionFailureReason.TARGET_ALREADY_OWNED;
        }
        if (mapDefinition.findConnection(originCityId, targetCityId) == null) {
            return StrategicActionFailureReason.TARGET_NOT_CONNECTED;
        }
        if (FactionActionPointRules.remaining(gameState, observerFactionId) < ACTION_POINT_COST) {
            return StrategicActionFailureReason.NO_ACTION_POINTS;
        }
        if (gameState.requireFactionState(observerFactionId).gold < GOLD_COST) {
            return StrategicActionFailureReason.INSUFFICIENT_GOLD;
        }
        return StrategicActionFailureReason.NONE;
    }

    private void applyInPlace(GameState gameState, String observerFactionId, String targetCityId) {
        FactionState observer = gameState.requireFactionState(observerFactionId);
        FactionActionPointRules.spend(gameState, observerFactionId, ACTION_POINT_COST);
        observer.gold -= GOLD_COST;
        intelligenceService.observe(gameState, observerFactionId, targetCityId);
        gameState.lastActionCode = "SCOUT_CITY";
    }
}
