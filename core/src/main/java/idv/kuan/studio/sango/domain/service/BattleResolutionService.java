package idv.kuan.studio.sango.domain.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import idv.kuan.studio.sango.application.result.TurnEvent;
import idv.kuan.studio.sango.application.result.TurnEventType;
import idv.kuan.studio.sango.application.result.TurnResolutionReport;
import idv.kuan.studio.sango.domain.definition.StrategicMapDefinition;
import idv.kuan.studio.sango.domain.model.ArmyState;
import idv.kuan.studio.sango.domain.model.BattleContribution;
import idv.kuan.studio.sango.domain.model.BattleOutcome;
import idv.kuan.studio.sango.domain.model.BattleReport;
import idv.kuan.studio.sango.domain.model.CityState;
import idv.kuan.studio.sango.domain.model.FactionState;
import idv.kuan.studio.sango.domain.model.GameState;
import idv.kuan.studio.sango.domain.model.GameplayStatus;
import idv.kuan.studio.sango.domain.model.ScenarioObjectiveStatus;
import idv.kuan.studio.sango.domain.rule.BattleTactic;
import idv.kuan.studio.sango.domain.rule.MilitaryRules;
import idv.kuan.studio.sango.domain.rule.DefensePolicy;
import idv.kuan.studio.sango.domain.rule.TroopQualityRules;

/** 處理單軍或聯合軍抵達，並保存作戰當下的戰報快照。 */
public final class BattleResolutionService {
    private final RetreatRoutePlanner retreatRoutePlanner = new RetreatRoutePlanner();

    public boolean resolveArrival(GameState gameState, ArmyState armyState,
        StrategicMapDefinition mapDefinition, TurnResolutionReport turnResolutionReport) {
        return resolveArrival(gameState, Collections.singletonList(armyState), mapDefinition,
            turnResolutionReport);
    }

    public boolean resolveArrival(GameState gameState, List<ArmyState> armyStates,
        StrategicMapDefinition mapDefinition, TurnResolutionReport turnResolutionReport) {
        validateGroup(armyStates);
        ArmyState firstArmy = armyStates.get(0);
        CityState targetCityState = gameState.requireCityState(firstArmy.targetCityId);
        int attackerTroopsBefore = totalTroops(armyStates);
        if (firstArmy.factionId.equals(targetCityState.ownerFactionId)) {
            TroopQualityRules.merge(targetCityState, attackerTroopsBefore,
                weightedScaledQuality(armyStates, null, true),
                weightedScaledQuality(armyStates, null, false));
            turnResolutionReport.add(new TurnEvent(TurnEventType.ARMY_REINFORCED,
                firstArmy.factionId, targetCityState.cityId, firstArmy.originCityId,
                attackerTroopsBefore, 0));
            return false;
        }

        CityState defenderBefore = targetCityState.copy();
        String defendingFactionId = defenderBefore.ownerFactionId;
        boolean capturedDefendingCapital = isDefendingCapital(
            gameState, defendingFactionId, targetCityState.cityId);
        boolean unopposedOccupation = defenderBefore.troops == 0;
        StrengthSnapshot strengthSnapshot = calculateStrengths(
            armyStates, defenderBefore, unopposedOccupation);
        boolean attackerWon = unopposedOccupation
            || strengthSnapshot.attackerStrength >= strengthSnapshot.defenderStrength;
        int attackerLosses = unopposedOccupation ? 0 : calculateAttackerLosses(
            attackerTroopsBefore, strengthSnapshot.attackerStrength,
            strengthSnapshot.defenderStrength);
        int defenderLosses = unopposedOccupation ? 0 : calculateDefenderLosses(
            defenderBefore.troops, strengthSnapshot.attackerStrength,
            strengthSnapshot.defenderStrength);
        int[] lossesByArmy = distributeLosses(armyStates, attackerLosses, attackerTroopsBefore);
        BattleContribution[] contributions = createContributions(
            armyStates, lossesByArmy, strengthSnapshot);
        int attackerSurvivors = attackerTroopsBefore - attackerLosses;
        int defenderSurvivors = defenderBefore.troops - defenderLosses;

        if (attackerWon) {
            targetCityState.ownerFactionId = firstArmy.factionId;
            targetCityState.defensePolicy = DefensePolicy.HOLD;
            targetCityState.publicOrderRecoveryStreakMonths = 0;
            targetCityState.troops = attackerSurvivors;
            setScaledQuality(targetCityState,
                weightedScaledQuality(armyStates, lossesByArmy, true),
                weightedScaledQuality(armyStates, lossesByArmy, false));
            int occupationDamage = unopposedOccupation ? 5 : 10;
            targetCityState.publicOrder = Math.max(0, targetCityState.publicOrder - occupationDamage);
            targetCityState.defense = Math.max(0, targetCityState.defense - occupationDamage);
            refreshFactionCapital(gameState, defendingFactionId, turnResolutionReport);
            refreshFactionCapital(gameState, firstArmy.factionId, turnResolutionReport);
            if (unopposedOccupation) {
                turnResolutionReport.add(new TurnEvent(TurnEventType.CITY_OCCUPIED_UNOPPOSED,
                    firstArmy.factionId, targetCityState.cityId, firstArmy.originCityId,
                    attackerSurvivors, 0));
            } else {
                turnResolutionReport.add(new TurnEvent(TurnEventType.BATTLE_ATTACKER_WON,
                    firstArmy.factionId, targetCityState.cityId, firstArmy.originCityId,
                    attackerLosses, defenderLosses));
                turnResolutionReport.add(new TurnEvent(TurnEventType.CITY_CAPTURED,
                    firstArmy.factionId, targetCityState.cityId, null, attackerSurvivors, 0));
            }
        } else {
            targetCityState.troops = defenderSurvivors;
            for (int i = 0; i < armyStates.size(); i++) {
                startRetreat(gameState, armyStates.get(i),
                    armyStates.get(i).troops - lossesByArmy[i], mapDefinition,
                    turnResolutionReport);
            }
            turnResolutionReport.add(new TurnEvent(TurnEventType.BATTLE_DEFENDER_WON,
                defendingFactionId, targetCityState.cityId, firstArmy.originCityId,
                attackerLosses, defenderLosses));
        }

        BattleReport battleReport = createBattleReport(gameState, firstArmy, armyStates,
            contributions, defenderBefore, attackerTroopsBefore, attackerLosses,
            defenderLosses, attackerWon, unopposedOccupation, strengthSnapshot);
        gameState.addBattleReport(battleReport);
        turnResolutionReport.addBattleReportId(battleReport.battleId);
        if (attackerWon) {
            evaluateScenarioAfterCapture(gameState, firstArmy.factionId, defendingFactionId,
                targetCityState.cityId, capturedDefendingCapital, turnResolutionReport);
        }
        return attackerWon;
    }

    private void validateGroup(List<ArmyState> armyStates) {
        if (armyStates == null || armyStates.isEmpty() || armyStates.get(0) == null) {
            throw new IllegalArgumentException("抵達軍隊不可為空。");
        }
        ArmyState firstArmy = armyStates.get(0);
        for (ArmyState armyState : armyStates) {
            if (armyState == null || !firstArmy.factionId.equals(armyState.factionId)
                || !firstArmy.targetCityId.equals(armyState.targetCityId)) {
                throw new IllegalArgumentException("聯合抵達軍隊必須屬於同一勢力與目標。");
            }
        }
    }

    private int weightedScaledQuality(List<ArmyState> armyStates, int[] lossesByArmy,
        boolean training) {
        long weightedTotal = 0;
        long troops = 0;
        for (int i = 0; i < armyStates.size(); i++) {
            ArmyState armyState = armyStates.get(i);
            int weight = armyState.troops - (lossesByArmy == null ? 0 : lossesByArmy[i]);
            int quality = training ? TroopQualityRules.training(armyState)
                : TroopQualityRules.morale(armyState);
            weightedTotal += (long) weight * quality;
            troops += weight;
        }
        return troops == 0 ? 0 : (int) (weightedTotal / troops);
    }

    private void setScaledQuality(CityState cityState, int training, int morale) {
        cityState.training = training / TroopQualityRules.SCALE;
        cityState.trainingFraction = training % TroopQualityRules.SCALE;
        cityState.morale = morale / TroopQualityRules.SCALE;
        cityState.moraleFraction = morale % TroopQualityRules.SCALE;
    }

    private BattleContribution[] createContributions(List<ArmyState> armyStates,
        int[] lossesByArmy, StrengthSnapshot strengthSnapshot) {
        BattleContribution[] contributions = new BattleContribution[armyStates.size()];
        for (int i = 0; i < armyStates.size(); i++) {
            ArmyState armyState = armyStates.get(i);
            BattleContribution contribution = new BattleContribution();
            contribution.armyId = armyState.armyId;
            contribution.factionId = armyState.factionId;
            contribution.originCityId = armyState.originCityId;
            contribution.troopsBefore = armyState.troops;
            contribution.losses = lossesByArmy[i];
            contribution.survivors = armyState.troops - lossesByArmy[i];
            contribution.training = armyState.training;
            contribution.morale = armyState.morale;
            contribution.attackerTactic = armyState.tactic.normalized();
            contribution.baseStrength = strengthSnapshot.attackerBaseStrengths[i];
            contribution.strength = strengthSnapshot.attackerStrengths[i];
            contributions[i] = contribution;
        }
        return contributions;
    }

    private BattleReport createBattleReport(GameState gameState, ArmyState firstArmy,
        List<ArmyState> armyStates, BattleContribution[] contributions,
        CityState defenderBefore, int attackerTroopsBefore, int attackerLosses,
        int defenderLosses, boolean attackerWon, boolean unopposedOccupation,
        StrengthSnapshot strengthSnapshot) {
        BattleReport battleReport = new BattleReport();
        battleReport.battleRulesVersion = 2;
        battleReport.battleId = gameState.allocateBattleReportId();
        battleReport.resolvedTurn = gameState.currentTurn;
        battleReport.resolvedYear = gameState.currentYear;
        battleReport.resolvedMonth = gameState.currentMonth;
        battleReport.originCityId = firstArmy.originCityId;
        battleReport.targetCityId = firstArmy.targetCityId;
        battleReport.attackerFactionId = firstArmy.factionId;
        battleReport.defenderFactionId = defenderBefore.ownerFactionId;
        battleReport.attackerTactic = firstArmy.tactic.normalized();
        battleReport.defenderPolicy = defenderBefore.defensePolicy.normalized();
        battleReport.defenderPolicyRecorded = true;
        battleReport.attackerTroopsBefore = attackerTroopsBefore;
        battleReport.defenderTroopsBefore = defenderBefore.troops;
        battleReport.attackerTraining = weightedIntegerQuality(armyStates, true);
        battleReport.defenderTraining = defenderBefore.training;
        battleReport.defenderDefense = defenderBefore.defense;
        battleReport.attackerMorale = weightedIntegerQuality(armyStates, false);
        battleReport.defenderMorale = defenderBefore.morale;
        battleReport.moraleRecorded = true;
        battleReport.attackerStrength = strengthSnapshot.attackerStrength;
        battleReport.defenderBaseStrength = strengthSnapshot.defenderBaseStrength;
        battleReport.defenderStrength = strengthSnapshot.defenderStrength;
        battleReport.defenderMatchupPercent = strengthSnapshot.defenderMatchupPercent;
        battleReport.attackerLosses = attackerLosses;
        battleReport.defenderLosses = defenderLosses;
        battleReport.attackerSurvivors = attackerTroopsBefore - attackerLosses;
        battleReport.defenderSurvivors = defenderBefore.troops - defenderLosses;
        battleReport.attackerContributions = contributions;
        battleReport.outcome = unopposedOccupation ? BattleOutcome.UNOPPOSED_OCCUPATION
            : attackerWon ? BattleOutcome.ATTACKER_VICTORY : BattleOutcome.DEFENDER_VICTORY;
        battleReport.cityCaptured = attackerWon;
        battleReport.winnerFactionId = attackerWon ? firstArmy.factionId
            : defenderBefore.ownerFactionId;
        battleReport.read = false;
        return battleReport;
    }

    private int weightedIntegerQuality(List<ArmyState> armyStates, boolean training) {
        long weightedTotal = 0;
        long troops = 0;
        for (ArmyState armyState : armyStates) {
            weightedTotal += (long) armyState.troops
                * (training ? armyState.training : armyState.morale);
            troops += armyState.troops;
        }
        return troops == 0 ? 0 : (int) (weightedTotal / troops);
    }

    private int totalTroops(List<ArmyState> armyStates) {
        int total = 0;
        for (ArmyState armyState : armyStates) {
            total = Math.addExact(total, armyState.troops);
        }
        return total;
    }

    private StrengthSnapshot calculateStrengths(List<ArmyState> armyStates,
        CityState defenderBefore, boolean unopposedOccupation) {
        int[] attackerBaseStrengths = new int[armyStates.size()];
        int[] attackerStrengths = new int[armyStates.size()];
        long totalAttackerBaseStrength = 0;
        long attackerStrength = 0;
        long counteredAttackerBaseStrength = 0;
        DefensePolicy defenderPolicy = defenderBefore.defensePolicy.normalized();
        for (int i = 0; i < armyStates.size(); i++) {
            ArmyState armyState = armyStates.get(i);
            BattleTactic tactic = armyState.tactic.normalized();
            int baseStrength = MilitaryRules.calculateAttackerStrength(armyState);
            int matchupPercent = unopposedOccupation ? 100
                : MilitaryRules.attackerMatchupPercent(tactic, defenderPolicy);
            int effectiveStrength = MilitaryRules.applyMatchupPercent(
                baseStrength, matchupPercent);
            attackerBaseStrengths[i] = baseStrength;
            attackerStrengths[i] = effectiveStrength;
            totalAttackerBaseStrength = saturatingAdd(totalAttackerBaseStrength, baseStrength);
            attackerStrength = saturatingAdd(attackerStrength, effectiveStrength);
            if (!unopposedOccupation && defenderPolicy.defeats(tactic)) {
                counteredAttackerBaseStrength = saturatingAdd(
                    counteredAttackerBaseStrength, baseStrength);
            }
        }
        int defenderBaseStrength = MilitaryRules.calculateDefenderStrength(defenderBefore);
        int defenderMatchupPercent = unopposedOccupation ? 100
            : MilitaryRules.defenderMatchupPercent(counteredAttackerBaseStrength,
                totalAttackerBaseStrength);
        int defenderStrength = MilitaryRules.applyMatchupPercent(
            defenderBaseStrength, defenderMatchupPercent);
        return new StrengthSnapshot(attackerBaseStrengths, attackerStrengths,
            attackerStrength, defenderBaseStrength, defenderStrength,
            defenderMatchupPercent);
    }

    private int[] distributeLosses(List<ArmyState> armyStates, int totalLosses,
        int totalTroops) {
        int[] losses = new int[armyStates.size()];
        long[] remainders = new long[armyStates.size()];
        int allocated = 0;
        for (int i = 0; i < armyStates.size(); i++) {
            long weightedLosses = (long) totalLosses * armyStates.get(i).troops;
            losses[i] = (int) (weightedLosses / totalTroops);
            remainders[i] = weightedLosses % totalTroops;
            allocated += losses[i];
        }
        for (int remaining = totalLosses - allocated; remaining > 0; remaining--) {
            int selected = 0;
            for (int i = 1; i < armyStates.size(); i++) {
                if (remainders[i] > remainders[selected]
                    || remainders[i] == remainders[selected]
                    && armyStates.get(i).armyId.compareTo(armyStates.get(selected).armyId) < 0) {
                    selected = i;
                }
            }
            losses[selected] += 1;
            remainders[selected] = -1;
        }
        return losses;
    }

    private int calculateAttackerLosses(int attackerTroops,
        long attackerStrength, int defenderStrength) {
        int lossPercent = clamp(scaledRatio(defenderStrength, 50,
            Math.max(1L, attackerStrength)), 15, 80);
        return (int) ((long) attackerTroops * lossPercent / 100);
    }

    private int calculateDefenderLosses(int defenderTroops, long attackerStrength,
        int defenderStrength) {
        int lossPercent = clamp(scaledRatio(attackerStrength, 60,
            Math.max(1, defenderStrength)), 20, 95);
        return (int) ((long) defenderTroops * lossPercent / 100);
    }

    private void refreshFactionCapital(GameState gameState, String factionId,
        TurnResolutionReport turnResolutionReport) {
        FactionState factionState = gameState.requireFactionState(factionId);
        List<CityState> ownedCities = gameState.findCitiesOwnedBy(factionId);
        if (ownedCities.isEmpty()) {
            factionState.active = false;
            factionState.capitalCityId = "";
            List<String> disbandedArmyIds = new ArrayList<>();
            for (ArmyState armyState : gameState.armyStates) {
                if (factionId.equals(armyState.factionId)) {
                    disbandedArmyIds.add(armyState.armyId);
                }
            }
            for (String armyId : disbandedArmyIds) {
                ArmyState armyState = findArmy(gameState, armyId);
                if (armyState != null) {
                    turnResolutionReport.add(new TurnEvent(TurnEventType.ARMY_RETREAT_DISBANDED,
                        armyState.factionId,
                        armyState.isRetreating() ? armyState.retreatCurrentCityId() : armyState.targetCityId,
                        armyState.retreatDestinationCityId(), armyState.troops, 0));
                }
                gameState.removeArmy(armyId);
            }
            return;
        }
        factionState.active = true;
        if (!gameState.ownsCity(factionId, factionState.capitalCityId)) {
            factionState.capitalCityId = ownedCities.get(0).cityId;
        }
    }

    private void startRetreat(GameState gameState, ArmyState armyState, int survivors,
        StrategicMapDefinition mapDefinition, TurnResolutionReport turnResolutionReport) {
        if (survivors <= 0) {
            gameState.removeArmy(armyState.armyId);
            return;
        }
        RetreatRoutePlanner.RoutePlan plan = retreatRoutePlanner.findRoute(gameState, mapDefinition,
            armyState.factionId, armyState.targetCityId, armyState.originCityId);
        if (plan == null) {
            gameState.removeArmy(armyState.armyId);
            turnResolutionReport.add(new TurnEvent(TurnEventType.ARMY_RETREAT_DISBANDED,
                armyState.factionId, armyState.targetCityId, null, survivors, 0));
            return;
        }
        armyState.troops = survivors;
        armyState.expeditionGroupId = armyState.armyId;
        armyState.retreatRouteCityIds = plan.cityIds();
        armyState.retreatRouteIndex = 0;
        armyState.returningFromRoad = false;
        armyState.remainingTravelMonths = mapDefinition.findConnection(
            armyState.retreatRouteCityIds[0], armyState.retreatRouteCityIds[1]).travelMonths;
        armyState.totalTravelMonths = Math.max(1, armyState.remainingTravelMonths);
        armyState.initialTroops = survivors;
        if (findArmy(gameState, armyState.armyId) == null) {
            gameState.addArmy(armyState);
        }
        turnResolutionReport.add(new TurnEvent(TurnEventType.ARMY_RETREAT_STARTED,
            armyState.factionId, armyState.targetCityId, armyState.retreatDestinationCityId(),
            survivors, plan.totalTravelMonths()));
    }

    private ArmyState findArmy(GameState gameState, String armyId) {
        for (ArmyState candidate : gameState.armyStates) {
            if (armyId.equals(candidate.armyId)) {
                return candidate;
            }
        }
        return null;
    }

    private boolean isDefendingCapital(GameState gameState, String defendingFactionId,
        String targetCityId) {
        FactionState defendingFactionState = gameState.requireFactionState(defendingFactionId);
        return defendingFactionState.active
            && targetCityId.equals(defendingFactionState.capitalCityId);
    }

    private void evaluateScenarioAfterCapture(GameState gameState, String attackingFactionId,
        String defendingFactionId, String capturedCityId, boolean capturedDefendingCapital,
        TurnResolutionReport turnResolutionReport) {
        if (gameState.playerFactionId.equals(attackingFactionId)
            && gameState.victoryTargetCityId.equals(capturedCityId)
            && gameState.scenarioObjectiveStatus == ScenarioObjectiveStatus.IN_PROGRESS) {
            gameState.scenarioObjectiveStatus = ScenarioObjectiveStatus.ACHIEVED;
            turnResolutionReport.add(new TurnEvent(TurnEventType.CAMPAIGN_VICTORY,
                attackingFactionId, capturedCityId, null, 0, 0));
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
            turnResolutionReport.add(new TurnEvent(TurnEventType.PLAYER_ELIMINATED,
                attackingFactionId, capturedCityId, null, 0, 0));
            return;
        }
        if (capturedDefendingCapital) {
            if (gameState.scenarioObjectiveStatus == ScenarioObjectiveStatus.IN_PROGRESS) {
                gameState.scenarioObjectiveStatus = ScenarioObjectiveStatus.FAILED;
                turnResolutionReport.add(new TurnEvent(TurnEventType.CAMPAIGN_DEFEAT_CAPITAL,
                    attackingFactionId, capturedCityId, null, 0, 0));
            }
            turnResolutionReport.add(new TurnEvent(TurnEventType.CAPITAL_RELOCATED,
                defendingFactionId, playerFactionState.capitalCityId, capturedCityId, 0, 0));
        }
    }

    private int clamp(long value, int minimum, int maximum) {
        return (int) Math.max(minimum, Math.min(maximum, value));
    }

    private long saturatingAdd(long left, long right) {
        return Long.MAX_VALUE - left < right ? Long.MAX_VALUE : left + right;
    }

    /** 以整數安全地計算 floor(numerator * scale / denominator)。 */
    private long scaledRatio(long numerator, int scale, long denominator) {
        if (numerator <= 0 || scale <= 0) {
            return 0;
        }
        long whole = numerator / denominator;
        if (whole > Long.MAX_VALUE / scale) {
            return Long.MAX_VALUE;
        }
        long result = whole * scale;
        long remainder = numerator % denominator;
        for (int candidate = scale - 1; candidate >= 1; candidate--) {
            long threshold = denominator / scale * candidate
                + (denominator % scale * candidate + scale - 1) / scale;
            if (remainder >= threshold) {
                return Long.MAX_VALUE - result < candidate
                    ? Long.MAX_VALUE : result + candidate;
            }
        }
        return result;
    }

    private static final class StrengthSnapshot {
        private final int[] attackerBaseStrengths;
        private final int[] attackerStrengths;
        private final long attackerStrength;
        private final int defenderBaseStrength;
        private final int defenderStrength;
        private final int defenderMatchupPercent;

        private StrengthSnapshot(int[] attackerBaseStrengths, int[] attackerStrengths,
            long attackerStrength, int defenderBaseStrength, int defenderStrength,
            int defenderMatchupPercent) {
            this.attackerBaseStrengths = attackerBaseStrengths;
            this.attackerStrengths = attackerStrengths;
            this.attackerStrength = attackerStrength;
            this.defenderBaseStrength = defenderBaseStrength;
            this.defenderStrength = defenderStrength;
            this.defenderMatchupPercent = defenderMatchupPercent;
        }
    }
}
