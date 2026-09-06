package idv.kuan.studio.sango.domain.service;

import java.util.List;

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

/**
 * 單一敵對勢力的最小 AI：集結、補兵並向相鄰玩家城池出征。
 */
public final class EnemyTurnService {
    private static final int MINIMUM_GARRISON = 300;
    private static final int MINIMUM_EXPEDITION = 400;
    private static final int MAXIMUM_EXPEDITION = 700;
    private static final int MARCH_FOOD_COST = 80;

    public void execute(
        GameState gameState,
        StrategicMapDefinition mapDefinition,
        TurnResolutionReport report
    ) {
        if (gameState.gameplayStatus != GameplayStatus.ACTIVE) {
            return;
        }
        FactionState opponentFactionState = gameState.requireOpponentFactionState();
        if (!opponentFactionState.active
            || gameState.hasArmyForFaction(gameState.opponentFactionId)) {
            return;
        }

        gameState.enemyAttackCountdown = Math.max(0, gameState.enemyAttackCountdown - 1);
        if (gameState.enemyAttackCountdown > 0) {
            report.add(new TurnEvent(
                TurnEventType.ENEMY_PREPARING,
                gameState.opponentFactionId,
                opponentFactionState.capitalCityId,
                null,
                gameState.enemyAttackCountdown,
                0
            ));
            return;
        }

        CityState originCityState = gameState.requireCityState(
            opponentFactionState.capitalCityId
        );
        CityState targetCityState = findAttackTarget(gameState, mapDefinition, originCityState);
        if (targetCityState == null) {
            gameState.enemyAttackCountdown = 1;
            return;
        }

        int dispatchedTroops = calculateDispatchTroops(originCityState);
        if (dispatchedTroops < MINIMUM_EXPEDITION) {
            originCityState.troops += 120;
            opponentFactionState.food = Math.max(0, opponentFactionState.food - 40);
            gameState.enemyAttackCountdown = 1;
            report.add(new TurnEvent(
                TurnEventType.ENEMY_REINFORCING,
                gameState.opponentFactionId,
                originCityState.cityId,
                null,
                120,
                0
            ));
            return;
        }

        CityConnectionDefinition connectionDefinition = mapDefinition.findConnection(
            originCityState.cityId,
            targetCityState.cityId
        );
        originCityState.troops -= dispatchedTroops;
        opponentFactionState.food = Math.max(
            0,
            opponentFactionState.food - MARCH_FOOD_COST
        );

        ArmyState armyState = new ArmyState();
        armyState.armyId = gameState.allocateArmyId();
        armyState.factionId = gameState.opponentFactionId;
        armyState.originCityId = originCityState.cityId;
        armyState.targetCityId = targetCityState.cityId;
        armyState.remainingTravelMonths = connectionDefinition.travelMonths;
        armyState.troops = dispatchedTroops;
        armyState.training = originCityState.training;
        armyState.morale = Math.max(50, originCityState.publicOrder);
        armyState.tactic = BattleTactic.BALANCED;
        gameState.addArmy(armyState);
        gameState.enemyAttackCountdown = 3;

        report.add(new TurnEvent(
            TurnEventType.ENEMY_MARCHING,
            gameState.opponentFactionId,
            originCityState.cityId,
            targetCityState.cityId,
            dispatchedTroops,
            connectionDefinition.travelMonths
        ));
    }

    private CityState findAttackTarget(
        GameState gameState,
        StrategicMapDefinition mapDefinition,
        CityState originCityState
    ) {
        FactionState playerFactionState = gameState.requirePlayerFactionState();
        if (playerFactionState.active
            && mapDefinition.findConnection(
                originCityState.cityId,
                playerFactionState.capitalCityId
            ) != null) {
            return gameState.requireCityState(playerFactionState.capitalCityId);
        }

        List<CityState> playerCities = gameState.findCitiesOwnedBy(gameState.playerFactionId);
        for (CityState playerCityState : playerCities) {
            if (mapDefinition.findConnection(
                originCityState.cityId,
                playerCityState.cityId
            ) != null) {
                return playerCityState;
            }
        }
        return null;
    }

    private int calculateDispatchTroops(CityState originCityState) {
        int availableTroops = originCityState.troops - MINIMUM_GARRISON;
        int dispatchedTroops = Math.min(MAXIMUM_EXPEDITION, availableTroops);
        return Math.max(0, dispatchedTroops / 100 * 100);
    }
}
