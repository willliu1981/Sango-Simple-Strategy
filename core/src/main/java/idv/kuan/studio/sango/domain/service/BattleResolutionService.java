package idv.kuan.studio.sango.domain.service;

import java.util.ArrayList;
import java.util.List;

import idv.kuan.studio.sango.application.result.TurnEvent;
import idv.kuan.studio.sango.application.result.TurnEventType;
import idv.kuan.studio.sango.application.result.TurnResolutionReport;
import idv.kuan.studio.sango.domain.model.ArmyState;
import idv.kuan.studio.sango.domain.model.BattleOutcome;
import idv.kuan.studio.sango.domain.model.BattleReport;
import idv.kuan.studio.sango.domain.model.CityState;
import idv.kuan.studio.sango.domain.model.FactionState;
import idv.kuan.studio.sango.domain.model.GameState;
import idv.kuan.studio.sango.domain.model.GameplayStatus;
import idv.kuan.studio.sango.domain.model.ScenarioObjectiveStatus;
import idv.kuan.studio.sango.domain.rule.MilitaryRules;
import idv.kuan.studio.sango.domain.rule.TroopQualityRules;

/**
 * 處理抵達、增援、攻城與無抵抗佔領，並保存作戰當下的戰報快照。
 */
public final class BattleResolutionService {
    public boolean resolveArrival(
        GameState gameState,
        ArmyState armyState,
        TurnResolutionReport turnResolutionReport
    ) {
        CityState targetCityState = gameState.requireCityState(armyState.targetCityId);
        if (armyState.factionId.equals(targetCityState.ownerFactionId)) {
            TroopQualityRules.merge(targetCityState, armyState.troops, TroopQualityRules.training(armyState), TroopQualityRules.morale(armyState));
            turnResolutionReport.add(new TurnEvent(
                TurnEventType.ARMY_REINFORCED, armyState.factionId,
                targetCityState.cityId, armyState.originCityId, armyState.troops, 0
            ));
            return false;
        }

        CityState defenderBefore = targetCityState.copy();
        String defendingFactionId = defenderBefore.ownerFactionId;
        boolean capturedDefendingCapital = isDefendingCapital(
            gameState, defendingFactionId, targetCityState.cityId
        );
        boolean unopposedOccupation = defenderBefore.troops == 0;
        int attackerStrength = MilitaryRules.calculateAttackerStrength(armyState);
        int defenderStrength = MilitaryRules.calculateDefenderStrength(defenderBefore);
        boolean attackerWon = unopposedOccupation || attackerStrength >= defenderStrength;
        int attackerLosses = unopposedOccupation ? 0 : calculateAttackerLosses(
            armyState, attackerStrength, defenderStrength
        );
        int defenderLosses = unopposedOccupation ? 0 : calculateDefenderLosses(
            defenderBefore, attackerStrength, defenderStrength
        );
        int attackerSurvivors = armyState.troops - attackerLosses;
        int defenderSurvivors = defenderBefore.troops - defenderLosses;

        if (attackerWon) {
            targetCityState.ownerFactionId = armyState.factionId;
            targetCityState.publicOrderRecoveryStreakMonths = 0;
            targetCityState.troops = attackerSurvivors;
            targetCityState.training = armyState.training;
            targetCityState.morale = armyState.morale;
            targetCityState.trainingFraction = armyState.trainingFraction;
            targetCityState.moraleFraction = armyState.moraleFraction;
            int occupationDamage = unopposedOccupation ? 5 : 10;
            targetCityState.publicOrder = Math.max(0, targetCityState.publicOrder - occupationDamage);
            targetCityState.defense = Math.max(0, targetCityState.defense - occupationDamage);
            refreshFactionCapital(gameState, defendingFactionId);
            refreshFactionCapital(gameState, armyState.factionId);
            if (unopposedOccupation) {
                turnResolutionReport.add(new TurnEvent(
                    TurnEventType.CITY_OCCUPIED_UNOPPOSED, armyState.factionId,
                    targetCityState.cityId, armyState.originCityId, attackerSurvivors, 0
                ));
            } else {
                turnResolutionReport.add(new TurnEvent(
                    TurnEventType.BATTLE_ATTACKER_WON, armyState.factionId,
                    targetCityState.cityId, armyState.originCityId, attackerLosses, defenderLosses
                ));
                turnResolutionReport.add(new TurnEvent(
                    TurnEventType.CITY_CAPTURED, armyState.factionId,
                    targetCityState.cityId, null, attackerSurvivors, 0
                ));
            }
        } else {
            targetCityState.troops = defenderSurvivors;
            returnSurvivors(gameState, armyState, attackerSurvivors);
            turnResolutionReport.add(new TurnEvent(
                TurnEventType.BATTLE_DEFENDER_WON, defendingFactionId,
                targetCityState.cityId, armyState.originCityId, attackerLosses, defenderLosses
            ));
        }

        BattleReport battleReport = createBattleReport(
            gameState, armyState, defenderBefore,
            attackerLosses, defenderLosses, attackerWon, unopposedOccupation
        );
        gameState.addBattleReport(battleReport);
        turnResolutionReport.addBattleReportId(battleReport.battleId);
        if (attackerWon) {
            evaluateScenarioAfterCapture(
                gameState, armyState.factionId, defendingFactionId,
                targetCityState.cityId, capturedDefendingCapital, turnResolutionReport
            );
        }
        return attackerWon;
    }

    private BattleReport createBattleReport(
        GameState gameState,
        ArmyState armyState,
        CityState defenderBefore,
        int attackerLosses,
        int defenderLosses,
        boolean attackerWon,
        boolean unopposedOccupation
    ) {
        BattleReport battleReport = new BattleReport();
        battleReport.battleId = gameState.allocateBattleReportId();
        battleReport.resolvedTurn = gameState.currentTurn;
        battleReport.resolvedYear = gameState.currentYear;
        battleReport.resolvedMonth = gameState.currentMonth;
        battleReport.originCityId = armyState.originCityId;
        battleReport.targetCityId = armyState.targetCityId;
        battleReport.attackerFactionId = armyState.factionId;
        battleReport.defenderFactionId = defenderBefore.ownerFactionId;
        battleReport.attackerTactic = armyState.tactic;
        battleReport.attackerTroopsBefore = armyState.troops;
        battleReport.defenderTroopsBefore = defenderBefore.troops;
        battleReport.attackerTraining = armyState.training;
        battleReport.defenderTraining = defenderBefore.training;
        battleReport.defenderDefense = defenderBefore.defense;
        battleReport.attackerMorale = armyState.morale;
        battleReport.defenderMorale = defenderBefore.morale;
        battleReport.moraleRecorded = true;
        battleReport.attackerLosses = attackerLosses;
        battleReport.defenderLosses = defenderLosses;
        battleReport.attackerSurvivors = armyState.troops - attackerLosses;
        battleReport.defenderSurvivors = defenderBefore.troops - defenderLosses;
        if (unopposedOccupation) {
            battleReport.outcome = BattleOutcome.UNOPPOSED_OCCUPATION;
        } else {
            battleReport.outcome = attackerWon
                ? BattleOutcome.ATTACKER_VICTORY : BattleOutcome.DEFENDER_VICTORY;
        }
        battleReport.cityCaptured = attackerWon;
        battleReport.winnerFactionId = attackerWon
            ? armyState.factionId : defenderBefore.ownerFactionId;
        battleReport.read = false;
        return battleReport;
    }

    private int calculateAttackerLosses(
        ArmyState armyState,
        int attackerStrength,
        int defenderStrength
    ) {
        int baseLossPercent = clamp((long) defenderStrength * 50 / Math.max(1, attackerStrength), 15, 80);
        int adjustedLossPercent = clamp(
            (long) baseLossPercent * armyState.tactic.getCasualtyPercent() / 100, 10, 90
        );
        return (int) ((long) armyState.troops * adjustedLossPercent / 100);
    }

    private int calculateDefenderLosses(
        CityState cityState,
        int attackerStrength,
        int defenderStrength
    ) {
        int lossPercent = clamp((long) attackerStrength * 60 / Math.max(1, defenderStrength), 20, 95);
        return (int) ((long) cityState.troops * lossPercent / 100);
    }

    private void refreshFactionCapital(GameState gameState, String factionId) {
        FactionState factionState = gameState.requireFactionState(factionId);
        List<CityState> ownedCities = gameState.findCitiesOwnedBy(factionId);
        if (ownedCities.isEmpty()) {
            factionState.active = false;
            factionState.capitalCityId = "";
            // 勢力滅亡後撤銷尚在行軍的部隊，避免同月後續抵達又把滅亡狀態反轉。
            List<String> disbandedArmyIds = new ArrayList<>();
            for (ArmyState armyState : gameState.armyStates) {
                if (factionId.equals(armyState.factionId)) {
                    disbandedArmyIds.add(armyState.armyId);
                }
            }
            for (String armyId : disbandedArmyIds) {
                gameState.removeArmy(armyId);
            }
            return;
        }
        factionState.active = true;
        if (!gameState.ownsCity(factionId, factionState.capitalCityId)) {
            factionState.capitalCityId = ownedCities.get(0).cityId;
        }
    }

    private void returnSurvivors(GameState gameState, ArmyState armyState, int survivors) {
        if (survivors <= 0) {
            return;
        }
        CityState originCityState = gameState.findCityState(armyState.originCityId);
        if (originCityState != null && armyState.factionId.equals(originCityState.ownerFactionId)) {
            TroopQualityRules.merge(originCityState, survivors, TroopQualityRules.training(armyState), TroopQualityRules.morale(armyState));
        }
    }

    private boolean isDefendingCapital(
        GameState gameState,
        String defendingFactionId,
        String targetCityId
    ) {
        FactionState defendingFactionState = gameState.requireFactionState(defendingFactionId);
        return defendingFactionState.active
            && targetCityId.equals(defendingFactionState.capitalCityId);
    }

    private void evaluateScenarioAfterCapture(
        GameState gameState,
        String attackingFactionId,
        String defendingFactionId,
        String capturedCityId,
        boolean capturedDefendingCapital,
        TurnResolutionReport turnResolutionReport
    ) {
        if (gameState.playerFactionId.equals(attackingFactionId)
            && gameState.victoryTargetCityId.equals(capturedCityId)
            && gameState.scenarioObjectiveStatus == ScenarioObjectiveStatus.IN_PROGRESS) {
            gameState.scenarioObjectiveStatus = ScenarioObjectiveStatus.ACHIEVED;
            turnResolutionReport.add(new TurnEvent(
                TurnEventType.CAMPAIGN_VICTORY,
                attackingFactionId,
                capturedCityId,
                null,
                0,
                0
            ));
        }

        if (!gameState.playerFactionId.equals(defendingFactionId)) {
            return;
        }

        FactionState playerFactionState = gameState.requirePlayerFactionState();
        if (!playerFactionState.active) {
            gameState.gameplayStatus = GameplayStatus.ELIMINATED;
            gameState.actionPointsRemaining = 0;
            if (gameState.scenarioObjectiveStatus == ScenarioObjectiveStatus.IN_PROGRESS) {
                gameState.scenarioObjectiveStatus = ScenarioObjectiveStatus.FAILED;
            }
            turnResolutionReport.add(new TurnEvent(
                TurnEventType.PLAYER_ELIMINATED,
                attackingFactionId,
                capturedCityId,
                null,
                0,
                0
            ));
            return;
        }

        if (capturedDefendingCapital) {
            if (gameState.scenarioObjectiveStatus == ScenarioObjectiveStatus.IN_PROGRESS) {
                gameState.scenarioObjectiveStatus = ScenarioObjectiveStatus.FAILED;
                turnResolutionReport.add(new TurnEvent(
                    TurnEventType.CAMPAIGN_DEFEAT_CAPITAL,
                    attackingFactionId,
                    capturedCityId,
                    null,
                    0,
                    0
                ));
            }
            turnResolutionReport.add(new TurnEvent(
                TurnEventType.CAPITAL_RELOCATED,
                defendingFactionId,
                playerFactionState.capitalCityId,
                capturedCityId,
                0,
                0
            ));
        }
    }

    private int clamp(long value, int minimum, int maximum) {
        return (int) Math.max(minimum, Math.min(maximum, value));
    }
}
