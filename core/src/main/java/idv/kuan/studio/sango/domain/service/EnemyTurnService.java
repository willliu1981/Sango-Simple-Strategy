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
import idv.kuan.studio.sango.domain.model.GameplayStatus;
import idv.kuan.studio.sango.domain.rule.BattleTactic;
import idv.kuan.studio.sango.domain.rule.DomesticActionType;
import idv.kuan.studio.sango.domain.rule.MilitaryRules;

/**
 * 多勢力最小 AI：共用集結週期，各自選擇前線、支付徵兵資源並進攻相鄰非己方城池。
 * 每勢力至多一支野戰軍；中立勢力不主動出兵，未選取的可玩勢力會正常行動。
 */
public final class EnemyTurnService {
    private static final int MINIMUM_GARRISON = 300;
    private static final int MINIMUM_EXPEDITION = 400;
    private static final int MAXIMUM_EXPEDITION = 700;
    private static final int MARCH_FOOD_COST = 100;
    private static final int ASSEMBLY_MONTHS = 3;

    public void execute(
        GameState gameState,
        StrategicMapDefinition mapDefinition,
        TurnResolutionReport report
    ) {
        if (gameState.gameplayStatus != GameplayStatus.ACTIVE) {
            return;
        }
        gameState.enemyAttackCountdown = Math.max(0, gameState.enemyAttackCountdown - 1);
        for (FactionState factionState : gameState.factionStates) {
            if (!factionState.active
                || gameState.playerFactionId.equals(factionState.factionId)
                || gameState.neutralFactionId.equals(factionState.factionId)
                || gameState.hasArmyForFaction(factionState.factionId)) {
                continue;
            }
            if (gameState.enemyAttackCountdown > 0) {
                report.add(new TurnEvent(
                    TurnEventType.ENEMY_PREPARING, factionState.factionId,
                    factionState.capitalCityId, null, gameState.enemyAttackCountdown, 0
                ));
                continue;
            }
            executeFaction(gameState, mapDefinition, factionState, report);
        }
        if (gameState.enemyAttackCountdown == 0) {
            gameState.enemyAttackCountdown = ASSEMBLY_MONTHS;
        }
    }

    private void executeFaction(
        GameState gameState,
        StrategicMapDefinition mapDefinition,
        FactionState factionState,
        TurnResolutionReport report
    ) {
        AttackPlan attackPlan = findAttackPlan(gameState, mapDefinition, factionState.factionId);
        if (attackPlan == null) {
            return;
        }
        CityState originCity = attackPlan.originCity();
        int dispatchedTroops = calculateDispatchTroops(originCity);
        if (dispatchedTroops < MINIMUM_EXPEDITION) {
            recruit(factionState, originCity, report);
            return;
        }
        if (factionState.food < MARCH_FOOD_COST) {
            return;
        }
        CityState targetCity = attackPlan.targetCity();
        CityConnectionDefinition connection = mapDefinition.findConnection(originCity.cityId, targetCity.cityId);
        originCity.troops -= dispatchedTroops;
        factionState.food -= MARCH_FOOD_COST;
        ArmyState armyState = new ArmyState();
        armyState.armyId = gameState.allocateArmyId();
        armyState.factionId = factionState.factionId;
        armyState.originCityId = originCity.cityId;
        armyState.targetCityId = targetCity.cityId;
        armyState.remainingTravelMonths = connection.travelMonths;
        armyState.troops = dispatchedTroops;
        armyState.training = originCity.training;
        armyState.morale = originCity.morale;
        armyState.tactic = BattleTactic.BALANCED;
        gameState.addArmy(armyState);
        report.add(new TurnEvent(
            TurnEventType.ENEMY_MARCHING, factionState.factionId,
            originCity.cityId, targetCity.cityId, dispatchedTroops, connection.travelMonths
        ));
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
                int dispatchTroops = calculateDispatchTroops(originCity);
                long score = (long) MilitaryRules.calculateDefenderStrength(targetCity) * 1_000
                    / Math.max(1, dispatchTroops);
                if (dispatchTroops < MINIMUM_EXPEDITION) {
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

    private void recruit(FactionState factionState, CityState originCity, TurnResolutionReport report) {
        DomesticActionType recruitment = DomesticActionType.RECRUIT;
        if (factionState.gold < recruitment.getGoldCost()
            || factionState.food < recruitment.getFoodCost()
            || originCity.population + recruitment.getPopulationDelta() < 1_000) {
            return;
        }
        factionState.gold -= recruitment.getGoldCost();
        factionState.food -= recruitment.getFoodCost();
        originCity.population += recruitment.getPopulationDelta();
        originCity.troops += recruitment.getTroopGain();
        originCity.morale = Math.max(0, originCity.morale + recruitment.getMoraleDelta());
        report.add(new TurnEvent(
            TurnEventType.ENEMY_REINFORCING, factionState.factionId,
            originCity.cityId, null, recruitment.getTroopGain(), 0
        ));
    }

    private int calculateDispatchTroops(CityState originCityState) {
        int availableTroops = originCityState.troops - MINIMUM_GARRISON;
        return Math.max(0, Math.min(MAXIMUM_EXPEDITION, availableTroops) / 100 * 100);
    }

    private record AttackPlan(CityState originCity, CityState targetCity) {
    }
}
