package idv.kuan.studio.sango.domain.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import idv.kuan.studio.sango.application.result.TurnEvent;
import idv.kuan.studio.sango.application.result.TurnEventType;
import idv.kuan.studio.sango.application.result.TurnResolutionReport;
import idv.kuan.studio.sango.domain.definition.CityConnectionDefinition;
import idv.kuan.studio.sango.domain.definition.StrategicMapDefinition;
import idv.kuan.studio.sango.domain.model.ArmyState;
import idv.kuan.studio.sango.domain.model.CityState;
import idv.kuan.studio.sango.domain.model.GameState;
import idv.kuan.studio.sango.domain.rule.TroopQualityRules;

/** 在一般戰鬥完成後重驗路線，並只移動本月開始前已在退卻的軍隊。 */
public final class RetreatResolutionService {
    private final RetreatRoutePlanner routePlanner;

    public RetreatResolutionService() {
        this(new RetreatRoutePlanner());
    }

    public RetreatResolutionService(RetreatRoutePlanner routePlanner) {
        this.routePlanner = routePlanner;
    }

    public void resolve(GameState gameState, StrategicMapDefinition mapDefinition,
        Set<String> retreatArmyIdsAtTurnStart, TurnResolutionReport report) {
        reconcileRoutes(gameState, mapDefinition, report);
        moveEligibleArmies(gameState, mapDefinition,
            retreatArmyIdsAtTurnStart == null ? Set.of() : retreatArmyIdsAtTurnStart, report);
    }

    private void reconcileRoutes(GameState gameState, StrategicMapDefinition mapDefinition,
        TurnResolutionReport report) {
        ArmyState[] snapshot = gameState.armyStates.clone();
        for (ArmyState armyState : snapshot) {
            if (!armyState.isRetreating() || !containsArmy(gameState, armyState.armyId)
                || routePlanner.isRouteValid(gameState, mapDefinition, armyState)) {
                continue;
            }
            String oldDestination = armyState.retreatDestinationCityId();
            String currentCityId = armyState.retreatCurrentCityId();
            if (currentCityId == null) {
                currentCityId = armyState.targetCityId;
            }
            if (gameState.ownsCity(armyState.factionId, currentCityId)) {
                arrive(gameState, armyState, currentCityId, report);
                continue;
            }
            RetreatRoutePlanner.RoutePlan plan = routePlanner.findRoute(gameState, mapDefinition,
                armyState.factionId, currentCityId, armyState.originCityId);
            if (plan == null) {
                disband(gameState, armyState, currentCityId, oldDestination, report);
                continue;
            }
            String[] prefix = Arrays.copyOf(armyState.retreatRouteCityIds,
                Math.max(1, armyState.retreatRouteIndex + 1));
            String[] suffix = plan.cityIds();
            String[] rerouted = Arrays.copyOf(prefix, prefix.length + suffix.length - 1);
            System.arraycopy(suffix, 1, rerouted, prefix.length, suffix.length - 1);
            armyState.retreatRouteCityIds = rerouted;
            armyState.retreatRouteIndex = prefix.length - 1;
            armyState.remainingTravelMonths = requireEdge(mapDefinition, currentCityId,
                suffix[1]).travelMonths;
            report.add(new TurnEvent(TurnEventType.ARMY_RETREAT_REROUTED,
                armyState.factionId, oldDestination, armyState.retreatDestinationCityId(),
                armyState.troops, plan.totalTravelMonths()));
        }
    }

    private void moveEligibleArmies(GameState gameState, StrategicMapDefinition mapDefinition,
        Set<String> eligibleIds, TurnResolutionReport report) {
        for (String armyId : new HashSet<>(eligibleIds)) {
            ArmyState armyState = findArmy(gameState, armyId);
            if (armyState == null || !armyState.isRetreating()) {
                continue;
            }
            if (!routePlanner.isRouteValid(gameState, mapDefinition, armyState)) {
                String current = armyState.retreatCurrentCityId();
                disband(gameState, armyState, current == null ? armyState.targetCityId : current,
                    armyState.retreatDestinationCityId(), report);
                continue;
            }
            String edgeStart = armyState.retreatRouteCityIds[armyState.retreatRouteIndex];
            String edgeEnd = armyState.retreatRouteCityIds[armyState.retreatRouteIndex + 1];
            armyState.remainingTravelMonths -= 1;
            if (armyState.remainingTravelMonths > 0) {
                report.add(new TurnEvent(TurnEventType.ARMY_RETREAT_ADVANCED,
                    armyState.factionId, edgeStart, edgeEnd, armyState.troops,
                    armyState.remainingTravelMonths));
                continue;
            }
            armyState.retreatRouteIndex += 1;
            if (armyState.retreatRouteIndex == armyState.retreatRouteCityIds.length - 1) {
                arrive(gameState, armyState, edgeEnd, report);
                continue;
            }
            report.add(new TurnEvent(TurnEventType.ARMY_RETREAT_ADVANCED,
                armyState.factionId, edgeStart, edgeEnd, armyState.troops, 0));
            armyState.remainingTravelMonths = requireEdge(mapDefinition, edgeEnd,
                armyState.retreatRouteCityIds[armyState.retreatRouteIndex + 1]).travelMonths;
        }
    }

    private void arrive(GameState gameState, ArmyState armyState, String destinationCityId,
        TurnResolutionReport report) {
        CityState destination = gameState.requireCityState(destinationCityId);
        int mergedTroops = armyState.troops;
        TroopQualityRules.merge(destination, mergedTroops, TroopQualityRules.training(armyState),
            TroopQualityRules.morale(armyState));
        gameState.removeArmy(armyState.armyId);
        report.add(new TurnEvent(TurnEventType.ARMY_RETREAT_ARRIVED,
            armyState.factionId, destinationCityId, armyState.targetCityId, mergedTroops, 0));
    }

    private void disband(GameState gameState, ArmyState armyState, String lastCityId,
        String oldDestination, TurnResolutionReport report) {
        int troops = armyState.troops;
        gameState.removeArmy(armyState.armyId);
        report.add(new TurnEvent(TurnEventType.ARMY_RETREAT_DISBANDED,
            armyState.factionId, lastCityId, oldDestination, troops, 0));
    }

    private CityConnectionDefinition requireEdge(StrategicMapDefinition mapDefinition,
        String firstCityId, String secondCityId) {
        CityConnectionDefinition connection = mapDefinition.findConnection(firstCityId, secondCityId);
        if (connection == null || connection.travelMonths <= 0) {
            throw new IllegalStateException("退卻路線道路無效：" + firstCityId + " -> " + secondCityId);
        }
        return connection;
    }

    private ArmyState findArmy(GameState gameState, String armyId) {
        for (ArmyState armyState : gameState.armyStates) {
            if (armyId.equals(armyState.armyId)) {
                return armyState;
            }
        }
        return null;
    }

    private boolean containsArmy(GameState gameState, String armyId) {
        return findArmy(gameState, armyId) != null;
    }
}
