package idv.kuan.studio.sango.domain.service;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import idv.kuan.studio.sango.application.result.TurnResolutionReport;
import idv.kuan.studio.sango.domain.definition.StrategicMapDefinition;
import idv.kuan.studio.sango.domain.model.ArmyState;
import idv.kuan.studio.sango.domain.model.BattleContribution;
import idv.kuan.studio.sango.domain.model.BattleOutcome;
import idv.kuan.studio.sango.domain.model.BattleReport;
import idv.kuan.studio.sango.domain.model.GameState;
import idv.kuan.studio.sango.domain.rule.BattleTactic;
import idv.kuan.studio.sango.domain.rule.ExpeditionRules;
import idv.kuan.studio.sango.domain.rule.MilitaryRules;
import idv.kuan.studio.sango.domain.rule.PostEncounterOrder;

/** 在軍隊進城前，先結算同一道路上方向相反且於本月交會的部隊。 */
public final class RoadEncounterResolutionService {
    public void resolve(GameState state, StrategicMapDefinition map,
        TurnResolutionReport turnReport) {
        ArmyState[] snapshot = state.armyStates.clone();
        Arrays.sort(snapshot, (left, right) -> left.armyId.compareTo(right.armyId));
        Set<String> engaged = new HashSet<>();
        for (int leftIndex = 0; leftIndex < snapshot.length; leftIndex++) {
            ArmyState left = snapshot[leftIndex];
            if (!eligible(left, map) || engaged.contains(left.armyId)) {
                continue;
            }
            for (int rightIndex = leftIndex + 1; rightIndex < snapshot.length; rightIndex++) {
                ArmyState right = snapshot[rightIndex];
                if (!eligible(right, map) || engaged.contains(right.armyId)
                    || left.factionId.equals(right.factionId)
                    || !oppositeRoute(left, right) || !crossThisMonth(left, right)) {
                    continue;
                }
                resolvePair(state, left, right, turnReport);
                engaged.add(left.armyId);
                engaged.add(right.armyId);
                break;
            }
        }
    }

    private boolean eligible(ArmyState army, StrategicMapDefinition map) {
        return army != null && !army.isRetreating() && army.remainingTravelMonths > 0
            && map.findConnection(army.originCityId, army.targetCityId) != null;
    }

    private boolean oppositeRoute(ArmyState left, ArmyState right) {
        return left.originCityId.equals(right.targetCityId)
            && left.targetCityId.equals(right.originCityId);
    }

    private boolean crossThisMonth(ArmyState left, ArmyState right) {
        double leftStart = routePosition(left, false);
        double leftEnd = routePosition(left, true);
        double rightStart = routePosition(right, false);
        double rightEnd = routePosition(right, true);
        return leftStart <= rightStart && leftEnd >= rightEnd
            || rightStart <= leftStart && rightEnd >= leftEnd;
    }

    private double routePosition(ArmyState army, boolean afterMovement) {
        int total = Math.max(1, army.totalTravelMonths);
        int travelled = Math.max(0, total - army.remainingTravelMonths);
        if (afterMovement) {
            travelled = Math.min(total, travelled + 1);
        }
        double progress = travelled / (double) total;
        return army.originCityId.compareTo(army.targetCityId) < 0 ? progress : 1d - progress;
    }

    private void resolvePair(GameState state, ArmyState attacker, ArmyState defender,
        TurnResolutionReport turnReport) {
        int attackerBefore = attacker.troops;
        int defenderBefore = defender.troops;
        int attackerMoraleBefore = attacker.morale;
        int defenderMoraleBefore = defender.morale;
        int attackerBase = MilitaryRules.calculateAttackerStrength(attacker);
        int defenderBase = MilitaryRules.calculateAttackerStrength(defender);
        int attackerPercent = defeats(attacker.tactic, defender.tactic) ? 110 : 100;
        int defenderPercent = defeats(defender.tactic, attacker.tactic) ? 110 : 100;
        int attackerStrength = MilitaryRules.applyMatchupPercent(attackerBase, attackerPercent);
        int defenderStrength = MilitaryRules.applyMatchupPercent(defenderBase, defenderPercent);
        BattleOutcome outcome = attackerStrength == defenderStrength ? BattleOutcome.DRAW
            : attackerStrength > defenderStrength
                ? BattleOutcome.ATTACKER_VICTORY : BattleOutcome.DEFENDER_VICTORY;
        int attackerLosses = losses(attackerBefore, defenderStrength, attackerStrength,
            outcome == BattleOutcome.ATTACKER_VICTORY, outcome == BattleOutcome.DRAW);
        int defenderLosses = losses(defenderBefore, attackerStrength, defenderStrength,
            outcome == BattleOutcome.DEFENDER_VICTORY, outcome == BattleOutcome.DRAW);
        attacker.troops -= attackerLosses;
        defender.troops -= defenderLosses;
        attacker.morale = Math.max(0, attacker.morale - moraleLoss(outcome,
            BattleOutcome.ATTACKER_VICTORY));
        defender.morale = Math.max(0, defender.morale - moraleLoss(outcome,
            BattleOutcome.DEFENDER_VICTORY));
        attacker.moraleFraction = 0;
        defender.moraleFraction = 0;
        boolean attackerContinues = outcome == BattleOutcome.ATTACKER_VICTORY
            && shouldContinue(attacker);
        boolean defenderContinues = outcome == BattleOutcome.DEFENDER_VICTORY
            && shouldContinue(defender);
        finishArmy(state, attacker, attackerContinues);
        finishArmy(state, defender, defenderContinues);

        BattleReport report = new BattleReport();
        report.battleRulesVersion = 3;
        report.routeEncounter = true;
        report.battleId = state.allocateBattleReportId();
        report.resolvedTurn = state.currentTurn;
        report.resolvedYear = state.currentYear;
        report.resolvedMonth = state.currentMonth;
        report.originCityId = attacker.originCityId;
        report.targetCityId = attacker.targetCityId;
        report.attackerFactionId = attacker.factionId;
        report.defenderFactionId = defender.factionId;
        report.attackerTactic = attacker.tactic.normalized();
        report.defenderTactic = defender.tactic.normalized();
        report.attackerPostEncounterOrder = attacker.postEncounterOrder;
        report.defenderPostEncounterOrder = defender.postEncounterOrder;
        report.attackerContinued = attackerContinues;
        report.defenderContinued = defenderContinues;
        report.attackerTroopsBefore = attackerBefore;
        report.defenderTroopsBefore = defenderBefore;
        report.attackerTraining = attacker.training;
        report.defenderTraining = defender.training;
        report.attackerMorale = attackerMoraleBefore;
        report.defenderMorale = defenderMoraleBefore;
        report.moraleRecorded = true;
        report.attackerStrength = attackerStrength;
        report.defenderBaseStrength = defenderBase;
        report.defenderStrength = defenderStrength;
        report.defenderMatchupPercent = defenderPercent;
        report.attackerLosses = attackerLosses;
        report.defenderLosses = defenderLosses;
        report.attackerSurvivors = attackerBefore - attackerLosses;
        report.defenderSurvivors = defenderBefore - defenderLosses;
        report.outcome = outcome;
        report.winnerFactionId = outcome == BattleOutcome.DRAW ? null
            : outcome == BattleOutcome.ATTACKER_VICTORY ? attacker.factionId : defender.factionId;
        report.attackerContributions = new BattleContribution[0];
        state.addBattleReport(report);
        turnReport.addBattleReportId(report.battleId);
    }

    private int losses(int troops, int opposingStrength, int ownStrength,
        boolean won, boolean draw) {
        long total = Math.max(1L, (long) opposingStrength + ownStrength);
        int severity = draw ? 60 : won ? 50 : 75;
        long calculated = (long) troops * opposingStrength * severity;
        return Math.min(troops, Math.max(1,
            (int) ((calculated + total * 100 - 1) / (total * 100))));
    }

    private int moraleLoss(BattleOutcome outcome, BattleOutcome winner) {
        return outcome == BattleOutcome.DRAW ? 10 : outcome == winner ? 5 : 15;
    }

    private boolean defeats(BattleTactic first, BattleTactic second) {
        BattleTactic left = first.normalized();
        BattleTactic right = second.normalized();
        return left == BattleTactic.ASSAULT && right == BattleTactic.HOLD
            || left == BattleTactic.HOLD && right == BattleTactic.FEINT
            || left == BattleTactic.FEINT && right == BattleTactic.ASSAULT;
    }

    boolean shouldContinue(ArmyState army) {
        if (army.postEncounterOrder == PostEncounterOrder.RETURN
            || army.troops < ExpeditionRules.MINIMUM_EXPEDITION) {
            return false;
        }
        if (army.postEncounterOrder == PostEncounterOrder.CONTINUE) {
            return true;
        }
        int survivorPercent = army.troops * 100 / Math.max(1, army.initialTroops);
        return survivorPercent >= 50 && army.morale >= 20
            && (survivorPercent > 70 || army.morale >= 40);
    }

    private void finishArmy(GameState state, ArmyState army, boolean continues) {
        if (army.troops <= 0) {
            state.removeArmy(army.armyId);
        } else if (!continues) {
            int returnMonths = Math.max(1,
                Math.max(1, army.totalTravelMonths) - army.remainingTravelMonths + 1);
            army.expeditionGroupId = army.armyId;
            army.returningFromRoad = true;
            army.retreatRouteCityIds = null;
            army.retreatRouteIndex = 0;
            army.remainingTravelMonths = returnMonths;
            army.totalTravelMonths = returnMonths;
            army.initialTroops = army.troops;
        }
    }
}
