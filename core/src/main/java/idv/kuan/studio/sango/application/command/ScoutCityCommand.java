package idv.kuan.studio.sango.application.command;

import idv.kuan.studio.sango.application.result.StrategicActionResult;
import idv.kuan.studio.sango.domain.definition.StrategicMapDefinition;
import idv.kuan.studio.sango.domain.model.CityState;
import idv.kuan.studio.sango.domain.model.FactionState;
import idv.kuan.studio.sango.domain.model.GameState;
import idv.kuan.studio.sango.domain.model.GameplayStatus;
import idv.kuan.studio.sango.domain.model.GameStateValidator;
import idv.kuan.studio.sango.domain.rule.StrategicActionFailureReason;
import idv.kuan.studio.sango.repository.GameDefinitionRepository;

/**
 * 從己方相鄰城池偵察目標，情報維持三個回合。
 */
public final class ScoutCityCommand {
    public static final int ACTION_POINT_COST = 1;
    public static final int GOLD_COST = 20;
    public static final int INTELLIGENCE_DURATION_TURNS = 3;

    private final GameDefinitionRepository definitionRepository;

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
            originCityId,
            targetCityId
        );
        if (failureReason != StrategicActionFailureReason.NONE) {
            return StrategicActionResult.failure("SCOUT", failureReason);
        }

        GameState nextState = currentState.copy();
        FactionState playerFactionState = nextState.requirePlayerFactionState();
        CityState targetCityState = nextState.requireCityState(targetCityId);
        nextState.actionPointsRemaining -= ACTION_POINT_COST;
        playerFactionState.gold -= GOLD_COST;
        targetCityState.scoutedUntilTurn = nextState.currentTurn
            + INTELLIGENCE_DURATION_TURNS;
        nextState.lastActionCode = "SCOUT_CITY";

        GameStateValidator.validate(nextState);
        return StrategicActionResult.success("SCOUT", nextState, 0);
    }

    private StrategicActionFailureReason evaluate(
        GameState gameState,
        String originCityId,
        String targetCityId
    ) {
        GameStateValidator.validate(gameState);
        CityState originCityState = gameState.requireCityState(originCityId);
        CityState targetCityState = gameState.requireCityState(targetCityId);
        if (gameState.gameplayStatus != GameplayStatus.ACTIVE) {
            return StrategicActionFailureReason.PLAYER_ELIMINATED;
        }
        if (!gameState.playerFactionId.equals(originCityState.ownerFactionId)) {
            return StrategicActionFailureReason.ORIGIN_NOT_OWNED;
        }
        if (gameState.playerFactionId.equals(targetCityState.ownerFactionId)) {
            return StrategicActionFailureReason.TARGET_ALREADY_OWNED;
        }
        StrategicMapDefinition mapDefinition = definitionRepository.requireMap(gameState.mapId);
        if (mapDefinition.findConnection(originCityId, targetCityId) == null) {
            return StrategicActionFailureReason.TARGET_NOT_CONNECTED;
        }
        if (gameState.actionPointsRemaining < ACTION_POINT_COST) {
            return StrategicActionFailureReason.NO_ACTION_POINTS;
        }
        if (gameState.requirePlayerFactionState().gold < GOLD_COST) {
            return StrategicActionFailureReason.INSUFFICIENT_GOLD;
        }
        return StrategicActionFailureReason.NONE;
    }
}
