package idv.kuan.studio.sango.domain.service;

import idv.kuan.studio.sango.application.result.TurnEvent;
import idv.kuan.studio.sango.application.result.TurnEventType;
import idv.kuan.studio.sango.application.result.TurnResolutionReport;
import idv.kuan.studio.sango.domain.model.ArmyState;
import idv.kuan.studio.sango.domain.model.CampaignStatus;
import idv.kuan.studio.sango.domain.model.CityState;
import idv.kuan.studio.sango.domain.model.FactionState;
import idv.kuan.studio.sango.domain.model.GameState;

/**
 * 決定軍隊抵達城池時的簡化攻城結果。
 */
public final class BattleResolutionService {
    public void resolveArrival(
        GameState gameState,
        ArmyState armyState,
        TurnResolutionReport report
    ) {
        CityState targetCityState = gameState.requireCityState(armyState.targetCityId);
        if (armyState.factionId.equals(targetCityState.ownerFactionId)) {
            targetCityState.troops += armyState.troops;
            targetCityState.training = weightedTraining(
                targetCityState.troops - armyState.troops,
                targetCityState.training,
                armyState.troops,
                armyState.training
            );
            report.add(new TurnEvent(
                TurnEventType.ARMY_REINFORCED,
                armyState.factionId,
                targetCityState.cityId,
                armyState.originCityId,
                armyState.troops,
                0
            ));
            return;
        }

        int attackerStrength = calculateAttackerStrength(armyState);
        int defenderStrength = calculateDefenderStrength(targetCityState);
        boolean attackerWon = attackerStrength >= defenderStrength;
        int attackerLosses = calculateAttackerLosses(
            armyState,
            attackerStrength,
            defenderStrength
        );
        int defenderLosses = calculateDefenderLosses(
            targetCityState,
            attackerStrength,
            defenderStrength
        );
        int attackerSurvivors = Math.max(0, armyState.troops - attackerLosses);
        int defenderSurvivors = Math.max(0, targetCityState.troops - defenderLosses);
        String defendingFactionId = targetCityState.ownerFactionId;
        boolean capturedDefendingCapital = isDefendingCapital(
            gameState,
            defendingFactionId,
            targetCityState.cityId
        );

        if (attackerWon) {
            captureCity(
                gameState,
                armyState,
                targetCityState,
                defendingFactionId,
                attackerSurvivors
            );
            report.add(new TurnEvent(
                TurnEventType.BATTLE_ATTACKER_WON,
                armyState.factionId,
                targetCityState.cityId,
                armyState.originCityId,
                attackerLosses,
                defenderLosses
            ));
            report.add(new TurnEvent(
                TurnEventType.CITY_CAPTURED,
                armyState.factionId,
                targetCityState.cityId,
                defendingFactionId,
                attackerSurvivors,
                0
            ));
            evaluateCampaignAfterCapture(
                gameState,
                armyState.factionId,
                defendingFactionId,
                targetCityState.cityId,
                capturedDefendingCapital,
                report
            );
            return;
        }

        targetCityState.troops = Math.max(1, defenderSurvivors);
        returnSurvivors(gameState, armyState, attackerSurvivors);
        report.add(new TurnEvent(
            TurnEventType.BATTLE_DEFENDER_WON,
            defendingFactionId,
            targetCityState.cityId,
            armyState.originCityId,
            attackerLosses,
            defenderLosses
        ));
    }

    private int calculateAttackerStrength(ArmyState armyState) {
        long trainedStrength = (long) armyState.troops * (100 + armyState.training);
        long tacticStrength = trainedStrength * armyState.tactic.getStrengthPercent();
        return safeStrength(tacticStrength / 10_000L);
    }

    private int calculateDefenderStrength(CityState cityState) {
        long trainedStrength = (long) cityState.troops * (100 + cityState.training);
        long fortifiedStrength = trainedStrength * (100 + cityState.defense / 2);
        return safeStrength(fortifiedStrength / 10_000L);
    }

    private int calculateAttackerLosses(
        ArmyState armyState,
        int attackerStrength,
        int defenderStrength
    ) {
        int baseLossPercent = clamp(
            defenderStrength * 50 / Math.max(1, attackerStrength),
            15,
            80
        );
        int adjustedLossPercent = clamp(
            baseLossPercent * armyState.tactic.getCasualtyPercent() / 100,
            10,
            90
        );
        return Math.min(
            armyState.troops,
            Math.max(1, armyState.troops * adjustedLossPercent / 100)
        );
    }

    private int calculateDefenderLosses(
        CityState cityState,
        int attackerStrength,
        int defenderStrength
    ) {
        int lossPercent = clamp(
            attackerStrength * 60 / Math.max(1, defenderStrength),
            20,
            95
        );
        return Math.min(
            cityState.troops,
            Math.max(1, cityState.troops * lossPercent / 100)
        );
    }

    private void captureCity(
        GameState gameState,
        ArmyState armyState,
        CityState targetCityState,
        String defendingFactionId,
        int attackerSurvivors
    ) {
        targetCityState.ownerFactionId = armyState.factionId;
        targetCityState.troops = Math.max(1, attackerSurvivors);
        targetCityState.training = armyState.training;
        targetCityState.publicOrder = Math.max(20, targetCityState.publicOrder - 10);
        targetCityState.defense = Math.max(0, targetCityState.defense - 10);
        refreshFactionCapital(gameState, defendingFactionId);
    }

    private void refreshFactionCapital(GameState gameState, String factionId) {
        FactionState factionState = gameState.requireFactionState(factionId);
        if (gameState.ownsCity(factionId, factionState.capitalCityId)) {
            return;
        }
        if (gameState.findCitiesOwnedBy(factionId).isEmpty()) {
            factionState.active = false;
            factionState.capitalCityId = "";
            return;
        }
        factionState.capitalCityId = gameState.findCitiesOwnedBy(factionId).get(0).cityId;
    }

    private void returnSurvivors(
        GameState gameState,
        ArmyState armyState,
        int attackerSurvivors
    ) {
        if (attackerSurvivors <= 0) {
            return;
        }
        CityState originCityState = gameState.findCityState(armyState.originCityId);
        if (originCityState != null
            && armyState.factionId.equals(originCityState.ownerFactionId)) {
            int existingTroops = originCityState.troops;
            originCityState.troops += attackerSurvivors;
            originCityState.training = weightedTraining(
                existingTroops,
                originCityState.training,
                attackerSurvivors,
                armyState.training
            );
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

    private void evaluateCampaignAfterCapture(
        GameState gameState,
        String attackingFactionId,
        String defendingFactionId,
        String capturedCityId,
        boolean capturedDefendingCapital,
        TurnResolutionReport report
    ) {
        if (gameState.playerFactionId.equals(attackingFactionId)
            && gameState.victoryTargetCityId.equals(capturedCityId)) {
            gameState.campaignStatus = CampaignStatus.VICTORY;
            report.add(new TurnEvent(
                TurnEventType.CAMPAIGN_VICTORY,
                attackingFactionId,
                capturedCityId,
                defendingFactionId,
                0,
                0
            ));
            return;
        }

        if (gameState.playerFactionId.equals(defendingFactionId)
            && capturedDefendingCapital) {
            gameState.campaignStatus = CampaignStatus.DEFEAT;
            report.add(new TurnEvent(
                TurnEventType.CAMPAIGN_DEFEAT_CAPITAL,
                attackingFactionId,
                capturedCityId,
                defendingFactionId,
                0,
                0
            ));
        }
    }

    private int weightedTraining(
        int firstTroops,
        int firstTraining,
        int secondTroops,
        int secondTraining
    ) {
        int totalTroops = firstTroops + secondTroops;
        if (totalTroops <= 0) {
            return 0;
        }
        return (firstTroops * firstTraining + secondTroops * secondTraining) / totalTroops;
    }

    private int safeStrength(long value) {
        return (int) Math.max(1L, Math.min(Integer.MAX_VALUE, value));
    }

    private int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
