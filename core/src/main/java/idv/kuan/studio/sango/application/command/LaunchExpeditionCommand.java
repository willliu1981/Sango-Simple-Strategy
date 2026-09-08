package idv.kuan.studio.sango.application.command;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import idv.kuan.studio.sango.application.result.StrategicActionResult;
import idv.kuan.studio.sango.domain.definition.CityConnectionDefinition;
import idv.kuan.studio.sango.domain.definition.StrategicMapDefinition;
import idv.kuan.studio.sango.domain.model.ArmyState;
import idv.kuan.studio.sango.domain.model.CityState;
import idv.kuan.studio.sango.domain.model.FactionState;
import idv.kuan.studio.sango.domain.model.GameState;
import idv.kuan.studio.sango.domain.model.GameplayStatus;
import idv.kuan.studio.sango.domain.model.GameStateValidator;
import idv.kuan.studio.sango.domain.rule.BattleTactic;
import idv.kuan.studio.sango.domain.rule.ExpeditionRules;
import idv.kuan.studio.sango.domain.rule.FactionActionPointRules;
import idv.kuan.studio.sango.domain.rule.StrategicActionFailureReason;
import idv.kuan.studio.sango.repository.GameDefinitionRepository;

/** 由己方城池派出一或多支軍隊；抵達後依目的地當下所有權運兵或攻城。 */
public final class LaunchExpeditionCommand {
    public static final int ACTION_POINT_COST = ExpeditionRules.ACTION_POINT_COST;
    public static final int FOOD_COST = ExpeditionRules.FOOD_COST;
    public static final int MINIMUM_GARRISON = ExpeditionRules.MINIMUM_GARRISON;
    public static final int MINIMUM_EXPEDITION = ExpeditionRules.MINIMUM_EXPEDITION;
    public static final int MAXIMUM_EXPEDITION = ExpeditionRules.MAXIMUM_EXPEDITION;

    private final GameDefinitionRepository definitionRepository;

    public LaunchExpeditionCommand(GameDefinitionRepository definitionRepository) {
        this.definitionRepository = definitionRepository;
    }

    public StrategicActionResult execute(
        int slotNumber,
        GameState currentState,
        String originCityId,
        String targetCityId,
        BattleTactic battleTactic
    ) {
        return execute(
            slotNumber,
            currentState,
            originCityId,
            targetCityId,
            calculateDispatchTroops(currentState.requireCityState(originCityId)),
            battleTactic
        );
    }

    public StrategicActionResult execute(
        int slotNumber,
        GameState currentState,
        String originCityId,
        String targetCityId,
        int amount,
        BattleTactic battleTactic
    ) {
        StrategicActionFailureReason failureReason = evaluate(
            currentState,
            originCityId,
            targetCityId,
            amount,
            battleTactic
        );
        if (failureReason != StrategicActionFailureReason.NONE) {
            return StrategicActionResult.failure("EXPEDITION", failureReason);
        }

        GameState nextState = currentState.copy();
        FactionState playerFactionState = nextState.requirePlayerFactionState();
        CityState originCityState = nextState.requireCityState(originCityId);
        StrategicMapDefinition mapDefinition = definitionRepository.requireMap(nextState.mapId);
        int travelMonths = mapDefinition.shortestTravelMonths(originCityId, targetCityId);
        int dispatchedTroops = amount;

        originCityState.troops -= dispatchedTroops;
        playerFactionState.food -= FOOD_COST;
        FactionActionPointRules.spend(nextState, nextState.playerFactionId, ACTION_POINT_COST);

        String armyId = nextState.allocateArmyId();
        ArmyState armyState = createArmy(
            armyId,
            armyId,
            nextState.playerFactionId,
            originCityState,
            targetCityId,
            travelMonths,
            dispatchedTroops,
            battleTactic
        );
        nextState.addArmy(armyState);
        nextState.lastActionCode = "LAUNCH_EXPEDITION";

        GameStateValidator.validate(nextState);
        return StrategicActionResult.success("EXPEDITION", nextState, dispatchedTroops);
    }

    /**
     * 從多座直接相鄰的己方城池聯合進攻同一座敵方或中立城池。
     * 所有來源會先在原狀態完成驗證，再於單一副本扣款。
     */
    public StrategicActionResult execute(
        int slot,
        GameState state,
        String targetCityId,
        List<ExpeditionOrder> orders,
        BattleTactic tactic
    ) {
        StrategicActionFailureReason failureReason = evaluateJoint(
            state,
            targetCityId,
            orders,
            tactic
        );
        if (failureReason != StrategicActionFailureReason.NONE) {
            return StrategicActionResult.failure("EXPEDITION", failureReason);
        }

        GameState nextState = state.copy();
        StrategicMapDefinition mapDefinition = definitionRepository.requireMap(nextState.mapId);
        FactionState playerFactionState = nextState.requirePlayerFactionState();
        String groupId = null;
        int totalTroops = 0;
        for (ExpeditionOrder order : orders) {
            CityState originCityState = nextState.requireCityState(order.originCityId());
            String armyId = nextState.allocateArmyId();
            if (groupId == null) {
                groupId = armyId;
            }
            int travelMonths = mapDefinition.findConnection(
                order.originCityId(), targetCityId
            ).travelMonths;
            ArmyState armyState = createArmy(
                armyId,
                groupId,
                nextState.playerFactionId,
                originCityState,
                targetCityId,
                travelMonths,
                order.troops(),
                tactic
            );
            originCityState.troops -= order.troops();
            totalTroops = Math.addExact(totalTroops, order.troops());
            nextState.addArmy(armyState);
        }
        int orderCount = orders.size();
        playerFactionState.food -= Math.multiplyExact(FOOD_COST, orderCount);
        FactionActionPointRules.spend(
            nextState,
            nextState.playerFactionId,
            Math.multiplyExact(ACTION_POINT_COST, orderCount)
        );
        nextState.lastActionCode = "LAUNCH_EXPEDITION";

        GameStateValidator.validate(nextState);
        return StrategicActionResult.success("EXPEDITION", nextState, totalTroops);
    }

    public int calculateDispatchTroops(CityState originCityState) {
        return ExpeditionRules.calculateDispatchTroops(originCityState);
    }

    private StrategicActionFailureReason evaluate(
        GameState gameState,
        String originCityId,
        String targetCityId,
        int amount,
        BattleTactic battleTactic
    ) {
        GameStateValidator.validate(gameState);
        if (battleTactic == null) {
            throw new IllegalArgumentException("battleTactic 不可為 null。");
        }
        CityState originCityState = gameState.requireCityState(originCityId);
        CityState targetCityState = gameState.requireCityState(targetCityId);
        if (gameState.gameplayStatus != GameplayStatus.ACTIVE) {
            return StrategicActionFailureReason.PLAYER_ELIMINATED;
        }
        if (!gameState.playerFactionId.equals(originCityState.ownerFactionId)) {
            return StrategicActionFailureReason.ORIGIN_NOT_OWNED;
        }
        StrategicMapDefinition mapDefinition = definitionRepository.requireMap(gameState.mapId);
        boolean transfer = gameState.playerFactionId.equals(targetCityState.ownerFactionId);
        if ((!transfer && mapDefinition.findConnection(originCityId, targetCityId) == null)
            || (transfer && mapDefinition.shortestTravelMonths(originCityId, targetCityId) < 1)) {
            return StrategicActionFailureReason.TARGET_NOT_CONNECTED;
        }
        if (gameState.actionPointsRemaining < ACTION_POINT_COST) {
            return StrategicActionFailureReason.NO_ACTION_POINTS;
        }
        if (gameState.requirePlayerFactionState().food < FOOD_COST) {
            return StrategicActionFailureReason.INSUFFICIENT_FOOD;
        }
        if (amount < MINIMUM_EXPEDITION || amount % 100 != 0) {
            return StrategicActionFailureReason.INVALID_EXPEDITION_AMOUNT;
        }
        if (amount > calculateDispatchTroops(originCityState)) {
            return StrategicActionFailureReason.INSUFFICIENT_TROOPS;
        }
        return StrategicActionFailureReason.NONE;
    }

    private StrategicActionFailureReason evaluateJoint(
        GameState gameState,
        String targetCityId,
        List<ExpeditionOrder> orders,
        BattleTactic battleTactic
    ) {
        GameStateValidator.validate(gameState);
        if (battleTactic == null) {
            throw new IllegalArgumentException("battleTactic 不可為 null。");
        }
        if (gameState.gameplayStatus != GameplayStatus.ACTIVE) {
            return StrategicActionFailureReason.PLAYER_ELIMINATED;
        }
        if (orders == null || orders.isEmpty()) {
            return StrategicActionFailureReason.INVALID_EXPEDITION_AMOUNT;
        }
        CityState targetCityState = gameState.requireCityState(targetCityId);
        if (gameState.playerFactionId.equals(targetCityState.ownerFactionId)) {
            return StrategicActionFailureReason.TARGET_ALREADY_OWNED;
        }
        long actionPointCost = (long) ACTION_POINT_COST * orders.size();
        if (actionPointCost > gameState.actionPointsRemaining) {
            return StrategicActionFailureReason.NO_ACTION_POINTS;
        }
        long foodCost = (long) FOOD_COST * orders.size();
        if (foodCost > gameState.requirePlayerFactionState().food) {
            return StrategicActionFailureReason.INSUFFICIENT_FOOD;
        }

        StrategicMapDefinition mapDefinition = definitionRepository.requireMap(gameState.mapId);
        Set<String> originCityIds = new HashSet<>();
        for (ExpeditionOrder order : orders) {
            if (order == null || order.originCityId() == null
                || !originCityIds.add(order.originCityId())) {
                return StrategicActionFailureReason.INVALID_EXPEDITION_AMOUNT;
            }
            CityState originCityState = gameState.requireCityState(order.originCityId());
            if (!gameState.playerFactionId.equals(originCityState.ownerFactionId)) {
                return StrategicActionFailureReason.ORIGIN_NOT_OWNED;
            }
            if (mapDefinition.findConnection(order.originCityId(), targetCityId) == null) {
                return StrategicActionFailureReason.TARGET_NOT_CONNECTED;
            }
            if (order.troops() < MINIMUM_EXPEDITION || order.troops() % 100 != 0) {
                return StrategicActionFailureReason.INVALID_EXPEDITION_AMOUNT;
            }
            if (order.troops() > calculateDispatchTroops(originCityState)) {
                return StrategicActionFailureReason.INSUFFICIENT_TROOPS;
            }
        }
        return StrategicActionFailureReason.NONE;
    }

    private ArmyState createArmy(
        String armyId,
        String expeditionGroupId,
        String factionId,
        CityState originCityState,
        String targetCityId,
        int travelMonths,
        int troops,
        BattleTactic tactic
    ) {
        ArmyState armyState = new ArmyState();
        armyState.armyId = armyId;
        armyState.expeditionGroupId = expeditionGroupId;
        armyState.factionId = factionId;
        armyState.originCityId = originCityState.cityId;
        armyState.targetCityId = targetCityId;
        armyState.remainingTravelMonths = travelMonths;
        armyState.troops = troops;
        armyState.training = originCityState.training;
        armyState.morale = originCityState.morale;
        armyState.trainingFraction = originCityState.trainingFraction;
        armyState.moraleFraction = originCityState.moraleFraction;
        armyState.tactic = tactic;
        return armyState;
    }
}
