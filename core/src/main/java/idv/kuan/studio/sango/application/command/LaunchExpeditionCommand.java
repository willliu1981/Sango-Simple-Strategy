package idv.kuan.studio.sango.application.command;

import idv.kuan.studio.sango.application.result.StrategicActionResult;
import idv.kuan.studio.sango.domain.definition.CityConnectionDefinition;
import idv.kuan.studio.sango.domain.definition.StrategicMapDefinition;
import idv.kuan.studio.sango.domain.model.ArmyState;
import idv.kuan.studio.sango.domain.model.CampaignStatus;
import idv.kuan.studio.sango.domain.model.CityState;
import idv.kuan.studio.sango.domain.model.FactionState;
import idv.kuan.studio.sango.domain.model.GameState;
import idv.kuan.studio.sango.domain.model.GameStateValidator;
import idv.kuan.studio.sango.domain.rule.BattleTactic;
import idv.kuan.studio.sango.domain.rule.StrategicActionFailureReason;
import idv.kuan.studio.sango.repository.GameDefinitionRepository;
import idv.kuan.studio.sango.repository.SaveGameRepository;

/**
 * 由相鄰己方城池派出一支軍隊。第一版每個勢力同時只保留一支野戰軍。
 */
public final class LaunchExpeditionCommand {
    public static final int ACTION_POINT_COST = 1;
    public static final int FOOD_COST = 100;
    public static final int MINIMUM_GARRISON = 400;
    public static final int MINIMUM_EXPEDITION = 400;
    public static final int MAXIMUM_EXPEDITION = 1000;

    private final GameDefinitionRepository definitionRepository;
    private final SaveGameRepository saveGameRepository;

    public LaunchExpeditionCommand(
        GameDefinitionRepository definitionRepository,
        SaveGameRepository saveGameRepository
    ) {
        this.definitionRepository = definitionRepository;
        this.saveGameRepository = saveGameRepository;
    }

    public StrategicActionResult execute(
        int slotNumber,
        GameState currentState,
        String originCityId,
        String targetCityId,
        BattleTactic battleTactic
    ) {
        StrategicActionFailureReason failureReason = evaluate(
            currentState,
            originCityId,
            targetCityId,
            battleTactic
        );
        if (failureReason != StrategicActionFailureReason.NONE) {
            return StrategicActionResult.failure("EXPEDITION", failureReason);
        }

        GameState nextState = currentState.copy();
        FactionState playerFactionState = nextState.requirePlayerFactionState();
        CityState originCityState = nextState.requireCityState(originCityId);
        StrategicMapDefinition mapDefinition = definitionRepository.requireMap(nextState.mapId);
        CityConnectionDefinition connectionDefinition = mapDefinition.findConnection(
            originCityId,
            targetCityId
        );
        int dispatchedTroops = calculateDispatchTroops(originCityState);

        originCityState.troops -= dispatchedTroops;
        playerFactionState.food -= FOOD_COST;
        nextState.actionPointsRemaining -= ACTION_POINT_COST;

        ArmyState armyState = new ArmyState();
        armyState.armyId = nextState.allocateArmyId();
        armyState.factionId = nextState.playerFactionId;
        armyState.originCityId = originCityId;
        armyState.targetCityId = targetCityId;
        armyState.remainingTravelMonths = connectionDefinition.travelMonths;
        armyState.troops = dispatchedTroops;
        armyState.training = originCityState.training;
        armyState.morale = Math.max(50, originCityState.publicOrder);
        armyState.tactic = battleTactic;
        nextState.addArmy(armyState);
        nextState.lastActionCode = "LAUNCH_EXPEDITION";

        GameStateValidator.validate(nextState);
        saveGameRepository.save(slotNumber, nextState);
        return StrategicActionResult.success("EXPEDITION", nextState, dispatchedTroops);
    }

    public int calculateDispatchTroops(CityState originCityState) {
        int availableTroops = originCityState.troops - MINIMUM_GARRISON;
        int dispatchedTroops = Math.min(MAXIMUM_EXPEDITION, availableTroops);
        return Math.max(0, dispatchedTroops / 100 * 100);
    }

    private StrategicActionFailureReason evaluate(
        GameState gameState,
        String originCityId,
        String targetCityId,
        BattleTactic battleTactic
    ) {
        GameStateValidator.validate(gameState);
        if (battleTactic == null) {
            throw new IllegalArgumentException("battleTactic 不可為 null。");
        }
        CityState originCityState = gameState.requireCityState(originCityId);
        CityState targetCityState = gameState.requireCityState(targetCityId);
        if (gameState.campaignStatus != CampaignStatus.IN_PROGRESS) {
            return StrategicActionFailureReason.CAMPAIGN_FINISHED;
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
        if (gameState.requirePlayerFactionState().food < FOOD_COST) {
            return StrategicActionFailureReason.INSUFFICIENT_FOOD;
        }
        if (gameState.hasArmyForFaction(gameState.playerFactionId)) {
            return StrategicActionFailureReason.ARMY_ALREADY_ACTIVE;
        }
        if (calculateDispatchTroops(originCityState) < MINIMUM_EXPEDITION) {
            return StrategicActionFailureReason.INSUFFICIENT_TROOPS;
        }
        return StrategicActionFailureReason.NONE;
    }
}
