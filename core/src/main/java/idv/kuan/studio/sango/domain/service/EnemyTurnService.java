package idv.kuan.studio.sango.domain.service;

import idv.kuan.studio.sango.application.result.TurnEvent;
import idv.kuan.studio.sango.application.result.TurnEventType;
import idv.kuan.studio.sango.application.result.TurnResolutionReport;
import idv.kuan.studio.sango.domain.definition.CityConnectionDefinition;
import idv.kuan.studio.sango.domain.definition.StrategicMapDefinition;
import idv.kuan.studio.sango.domain.model.ArmyState;
import idv.kuan.studio.sango.domain.model.CityState;
import idv.kuan.studio.sango.domain.model.FactionState;
import idv.kuan.studio.sango.domain.model.GameState;
import idv.kuan.studio.sango.domain.rule.BattleTactic;
import idv.kuan.studio.sango.domain.rule.DomesticActionFailureReason;
import idv.kuan.studio.sango.domain.rule.DomesticActionRules;
import idv.kuan.studio.sango.domain.rule.DomesticActionType;
import idv.kuan.studio.sango.domain.rule.ExpeditionRules;
import idv.kuan.studio.sango.domain.rule.FactionActionPointRules;
import idv.kuan.studio.sango.domain.rule.MilitaryRules;
import idv.kuan.studio.sango.domain.rule.OfficerCommandProfile;
import idv.kuan.studio.sango.domain.rule.RecruitmentRules;

/**
 * AI 使用與玩家相同的月額度、徵兵、訓練、資源及出征限制。
 * 集結三月是出征策略的節奏，不是額外免費行動；持有野戰軍時仍可處理內政。
 */
public final class EnemyTurnService {
    private static final int ASSEMBLY_MONTHS = 3;
    private static final int TARGET_GARRISON = 1_800;
    private final DomesticActionService domesticActionService = new DomesticActionService();

    public void execute(GameState gameState, StrategicMapDefinition mapDefinition, TurnResolutionReport report) {
        if (!gameState.isGameplayActive()) {
            return;
        }
        gameState.enemyAttackCountdown = Math.max(0, gameState.enemyAttackCountdown - 1);
        for (FactionState factionState : gameState.factionStates) {
            if (!factionState.active || gameState.playerFactionId.equals(factionState.factionId)
                || gameState.neutralFactionId.equals(factionState.factionId)) {
                continue;
            }
            int budget = FactionActionPointRules.remaining(gameState, factionState.factionId);
            int capacity = FactionActionPointRules.capacity(gameState, factionState.factionId);
            if (gameState.enemyAttackCountdown > 0) {
                report.add(new TurnEvent(TurnEventType.ENEMY_PREPARING, factionState.factionId,
                    factionState.capitalCityId, null, gameState.enemyAttackCountdown, 0));
            }
            for (int i = 0; i < budget; i++) {
                if (tryMarch(gameState, mapDefinition, factionState, report)) {
                    continue;
                }
                if (!tryDomesticAction(gameState, factionState, report)) {
                    break;
                }
            }
            int spent = budget - FactionActionPointRules.remaining(gameState, factionState.factionId);
            if (spent > 0) {
                report.add(new TurnEvent(TurnEventType.AI_ACTIONS_USED, factionState.factionId,
                    null, null, spent, capacity));
            }
        }
        if (gameState.enemyAttackCountdown == 0) {
            gameState.enemyAttackCountdown = ASSEMBLY_MONTHS;
        }
    }

    private boolean tryMarch(GameState gameState, StrategicMapDefinition mapDefinition,
        FactionState factionState, TurnResolutionReport report) {
        if (gameState.enemyAttackCountdown != 0 || gameState.hasArmyForFaction(factionState.factionId)
            || factionState.food < ExpeditionRules.FOOD_COST
            || FactionActionPointRules.remaining(gameState, factionState.factionId) < ExpeditionRules.ACTION_POINT_COST) {
            return false;
        }
        AttackPlan plan = findAttackPlan(gameState, mapDefinition, factionState.factionId);
        if (plan == null) {
            return false;
        }
        CityState originCity = plan.originCity();
        int dispatchedTroops = ExpeditionRules.calculateDispatchTroops(originCity);
        if (dispatchedTroops < ExpeditionRules.MINIMUM_EXPEDITION) {
            return false;
        }
        CityState targetCity = plan.targetCity();
        CityConnectionDefinition connection = mapDefinition.findConnection(originCity.cityId, targetCity.cityId);
        ArmyState armyState = new ArmyState();
        armyState.armyId = gameState.allocateArmyId();
        armyState.factionId = factionState.factionId;
        armyState.originCityId = originCity.cityId;
        armyState.targetCityId = targetCity.cityId;
        armyState.remainingTravelMonths = connection.travelMonths;
        armyState.troops = dispatchedTroops;
        armyState.training = originCity.training;
        armyState.morale = originCity.morale;
        armyState.trainingFraction = originCity.trainingFraction;
        armyState.moraleFraction = originCity.moraleFraction;
        armyState.tactic = BattleTactic.BALANCED;
        originCity.troops -= dispatchedTroops;
        factionState.food -= ExpeditionRules.FOOD_COST;
        FactionActionPointRules.spend(gameState, factionState.factionId, ExpeditionRules.ACTION_POINT_COST);
        gameState.addArmy(armyState);
        report.add(new TurnEvent(TurnEventType.ENEMY_MARCHING, factionState.factionId,
            originCity.cityId, targetCity.cityId, dispatchedTroops, connection.travelMonths));
        return true;
    }

    private boolean tryDomesticAction(GameState gameState, FactionState factionState, TurnResolutionReport report) {
        for (CityState cityState : gameState.findCitiesOwnedBy(factionState.factionId)) {
            if (cityState.publicOrder < 70 && applyDomestic(gameState, factionState, cityState,
                DomesticActionType.PACIFY, 0)) {
                return true;
            }
            int recruitmentAmount = Math.min(Math.max(0, TARGET_GARRISON - cityState.troops),
                RecruitmentRules.maximumRecruitable(cityState, factionState, OfficerCommandProfile.DEFAULT));
            if (recruitmentAmount > 0 && applyDomestic(gameState, factionState, cityState,
                DomesticActionType.RECRUIT, recruitmentAmount)) {
                report.add(new TurnEvent(TurnEventType.ENEMY_REINFORCING, factionState.factionId,
                    cityState.cityId, null, recruitmentAmount, 0));
                return true;
            }
            if ((cityState.training < 65 || cityState.morale < 65)
                && applyDomestic(gameState, factionState, cityState, DomesticActionType.TRAIN, 0)) {
                return true;
            }
            if (cityState.agriculture < 60 && applyDomestic(gameState, factionState, cityState,
                DomesticActionType.DEVELOP_AGRICULTURE, 0)) {
                return true;
            }
            if (cityState.waterControl < 55 && applyDomestic(gameState, factionState, cityState,
                DomesticActionType.IMPROVE_WATER_CONTROL, 0)) {
                return true;
            }
            if (cityState.commerce < 60 && applyDomestic(gameState, factionState, cityState,
                DomesticActionType.DEVELOP_COMMERCE, 0)) {
                return true;
            }
            if (cityState.defense < 50 && applyDomestic(gameState, factionState, cityState,
                DomesticActionType.FORTIFY, 0)) {
                return true;
            }
        }
        return false;
    }

    private boolean applyDomestic(GameState gameState, FactionState factionState, CityState cityState,
        DomesticActionType actionType, int recruitmentAmount) {
        if (DomesticActionRules.evaluateForFaction(gameState, factionState.factionId, cityState.cityId,
            actionType, recruitmentAmount, OfficerCommandProfile.DEFAULT) != DomesticActionFailureReason.NONE) {
            return false;
        }
        return domesticActionService.apply(gameState, factionState.factionId, cityState.cityId,
            actionType, recruitmentAmount, OfficerCommandProfile.DEFAULT) == DomesticActionFailureReason.NONE;
    }

    private AttackPlan findAttackPlan(GameState gameState, StrategicMapDefinition mapDefinition, String factionId) {
        AttackPlan bestPlan = null;
        long bestScore = Long.MAX_VALUE;
        for (CityState originCity : gameState.findCitiesOwnedBy(factionId)) {
            for (CityConnectionDefinition connection : mapDefinition.connections) {
                String targetCityId;
                if (originCity.cityId.equals(connection.fromCityId)) {
                    targetCityId = connection.toCityId;
                } else if (originCity.cityId.equals(connection.toCityId)) {
                    targetCityId = connection.fromCityId;
                } else {
                    continue;
                }
                CityState targetCity = gameState.requireCityState(targetCityId);
                if (factionId.equals(targetCity.ownerFactionId)) {
                    continue;
                }
                int dispatchTroops = ExpeditionRules.calculateDispatchTroops(originCity);
                long score = (long) MilitaryRules.calculateDefenderStrength(targetCity) * 1_000
                    / Math.max(1, dispatchTroops);
                if (dispatchTroops < ExpeditionRules.MINIMUM_EXPEDITION) {
                    score += 1_000_000L;
                }
                if (score < bestScore) {
                    bestScore = score;
                    bestPlan = new AttackPlan(originCity, targetCity);
                }
            }
        }
        return bestPlan;
    }

    private record AttackPlan(CityState originCity, CityState targetCity) {
    }
}
