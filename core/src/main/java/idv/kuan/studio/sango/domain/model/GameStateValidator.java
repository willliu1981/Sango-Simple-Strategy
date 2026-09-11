package idv.kuan.studio.sango.domain.model;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import idv.kuan.studio.sango.SangoVersion;
import idv.kuan.studio.sango.domain.rule.BattleTactic;
import idv.kuan.studio.sango.domain.rule.DefensePolicy;
import idv.kuan.studio.sango.domain.rule.MilitaryRules;
import idv.kuan.studio.sango.domain.rule.NationalActionPointRules;
import idv.kuan.studio.sango.domain.rule.TroopQualityRules;

/**
 * 對新局與讀取後的戰局做結構驗證，避免損壞資料進入 UI。
 */
public final class GameStateValidator {
    private GameStateValidator() {
    }

    public static void validate(GameState gameState) {
        if (gameState == null) {
            throw new IllegalArgumentException("gameState 不可為 null。");
        }
        if (gameState.schemaVersion != SangoVersion.GAME_STATE_SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                "不支援的 GameState schemaVersion：" + gameState.schemaVersion
            );
        }
        requireText(gameState.scenarioId, "scenarioId");
        requireText(gameState.mapId, "mapId");
        requireText(gameState.playerFactionId, "playerFactionId");
        requireText(gameState.opponentFactionId, "opponentFactionId");
        requireText(gameState.neutralFactionId, "neutralFactionId");
        if (gameState.scenarioObjectiveType == null) {
            throw new IllegalArgumentException("scenarioObjectiveType 不可為 null。");
        }
        requireText(gameState.campaignInstanceId, "campaignInstanceId");
        requireText(gameState.lastActionCode, "lastActionCode");
        if (gameState.scenarioObjectiveStatus == null) {
            throw new IllegalArgumentException("scenarioObjectiveStatus 不可為 null。");
        }
        if (gameState.gameplayStatus == null) {
            throw new IllegalArgumentException("gameplayStatus 不可為 null。");
        }
        if (gameState.currentTurn < 1) {
            throw new IllegalArgumentException("currentTurn 必須大於或等於 1。");
        }
        if (gameState.currentYear < 1) {
            throw new IllegalArgumentException("currentYear 必須大於或等於 1。");
        }
        if (gameState.currentMonth < 1 || gameState.currentMonth > 12) {
            throw new IllegalArgumentException("currentMonth 必須介於 1 到 12。");
        }
        requireNonNegative(gameState.elapsedMonths, "elapsedMonths");
        if (gameState.turnLimitMonths < 1) {
            throw new IllegalArgumentException("turnLimitMonths 必須大於或等於 1。");
        }
        if (gameState.actionPointsPerTurn < NationalActionPointRules.MINIMUM_ACTION_POINTS
            || gameState.actionPointsPerTurn > NationalActionPointRules.MAXIMUM_ACTION_POINTS) {
            throw new IllegalArgumentException("actionPointsPerTurn 必須介於 3 到 9。");
        }
        if (gameState.actionPointsRemaining < 0
            || gameState.actionPointsRemaining > gameState.actionPointsPerTurn) {
            throw new IllegalArgumentException("actionPointsRemaining 超出合法範圍。");
        }
        requireNonNegative(gameState.enemyAttackCountdown, "enemyAttackCountdown");
        if (gameState.nextArmySequence < 1) {
            throw new IllegalArgumentException("nextArmySequence 必須大於或等於 1。");
        }
        if (gameState.nextBattleSequence < 1) {
            throw new IllegalArgumentException("nextBattleSequence 必須大於或等於 1。");
        }
        if (gameState.factionStates == null || gameState.factionStates.length == 0) {
            throw new IllegalArgumentException("factionStates 不可為空。");
        }
        if (gameState.cityStates == null || gameState.cityStates.length == 0) {
            throw new IllegalArgumentException("cityStates 不可為空。");
        }
        if (gameState.turnStartCityStates == null
            || gameState.turnStartCityStates.length != gameState.cityStates.length) {
            throw new IllegalArgumentException("turnStartCityStates 必須完整對應目前城池。");
        }
        if (gameState.armyStates == null) {
            throw new IllegalArgumentException("armyStates 不可為 null。");
        }
        if (gameState.battleReports == null) {
            throw new IllegalArgumentException("battleReports 不可為 null。");
        }

        Set<String> factionIds = new HashSet<>();
        Map<String, FactionState> factionStatesById = new HashMap<>();
        for (FactionState factionState : gameState.factionStates) {
            validateFactionState(factionState);
            if (!factionIds.add(factionState.factionId)) {
                throw new IllegalArgumentException(
                    "FactionState ID 重複：" + factionState.factionId
                );
            }
            factionStatesById.put(factionState.factionId, factionState);
        }
        requireFactionReference(factionIds, gameState.playerFactionId, "playerFactionId");
        requireFactionReference(factionIds, gameState.opponentFactionId, "opponentFactionId");
        requireFactionReference(factionIds, gameState.neutralFactionId, "neutralFactionId");

        Map<String, CityState> cityStatesById = new HashMap<>();
        for (CityState cityState : gameState.cityStates) {
            validateCityState(cityState);
            if (cityStatesById.put(cityState.cityId, cityState) != null) {
                throw new IllegalArgumentException("CityState ID 重複：" + cityState.cityId);
            }
            requireFactionReference(
                factionIds,
                cityState.ownerFactionId,
                "CityState.ownerFactionId"
            );
        }
        if (gameState.scenarioObjectiveType == ScenarioObjectiveType.CAPTURE_CITY) {
            requireText(gameState.victoryTargetCityId, "victoryTargetCityId");
            if (!cityStatesById.containsKey(gameState.victoryTargetCityId)) {
                throw new IllegalArgumentException(
                    "victoryTargetCityId 沒有對應城池：" + gameState.victoryTargetCityId
                );
            }
        } else {
            requireText(gameState.victoryTargetFactionId, "victoryTargetFactionId");
            requireFactionReference(factionIds, gameState.victoryTargetFactionId,
                "victoryTargetFactionId");
            if (gameState.playerFactionId.equals(gameState.victoryTargetFactionId)
                || gameState.neutralFactionId.equals(gameState.victoryTargetFactionId)) {
                throw new IllegalArgumentException("消滅勢力目標不可指向玩家或中立勢力。");
            }
        }
        requireCityReference(cityStatesById, gameState.strategicMapFocusedCityId,
            "strategicMapFocusedCityId");
        validateTurnStartCities(gameState.turnStartCityStates, factionIds, cityStatesById);

        for (FactionState factionState : gameState.factionStates) {
            validateCapitalReference(factionState, cityStatesById);
            validateIntelligence(factionState, factionIds, cityStatesById);
        }

        Set<String> armyIds = new HashSet<>();
        Map<String, ArmyGroupValidation> armyGroups = new HashMap<>();
        for (ArmyState armyState : gameState.armyStates) {
            validateArmyState(armyState, factionIds, cityStatesById);
            if (!armyIds.add(armyState.armyId)) {
                throw new IllegalArgumentException("ArmyState ID 重複：" + armyState.armyId);
            }
            String groupId = effectiveGroupId(armyState);
            ArmyGroupValidation group = armyGroups.get(groupId);
            if (group == null) {
                group = new ArmyGroupValidation(armyState.factionId, armyState.targetCityId);
                armyGroups.put(groupId, group);
            } else if (!group.factionId.equals(armyState.factionId)
                || !group.targetCityId.equals(armyState.targetCityId)) {
                throw new IllegalArgumentException("同一出征群組必須屬於同勢力且前往同一目標：" + groupId);
            }
            group.hasTravellingArmy |= armyState.remainingTravelMonths > 0;
        }
        for (Map.Entry<String, ArmyGroupValidation> entry : armyGroups.entrySet()) {
            if (!entry.getValue().hasTravellingArmy) {
                throw new IllegalArgumentException("出征群組不可整組以 0 月停留而未結算：" + entry.getKey());
            }
        }

        Set<String> battleIds = new HashSet<>();
        for (BattleReport battleReport : gameState.battleReports) {
            validateBattleReport(battleReport, factionIds, cityStatesById);
            if (!battleIds.add(battleReport.battleId)) {
                throw new IllegalArgumentException(
                    "BattleReport ID 重複：" + battleReport.battleId
                );
            }
        }

        FactionState playerFactionState = factionStatesById.get(gameState.playerFactionId);
        boolean playerOwnsAnyCity = !gameState.findCitiesOwnedBy(gameState.playerFactionId).isEmpty();
        if (gameState.gameplayStatus == GameplayStatus.ACTIVE) {
            if (!playerFactionState.active || !playerOwnsAnyCity) {
                throw new IllegalArgumentException(
                    "ACTIVE 戰局必須保有至少一座玩家城池與 active 玩家勢力。"
                );
            }
            gameState.requireCapitalCityState();
        } else {
            if (playerFactionState.active || playerOwnsAnyCity) {
                throw new IllegalArgumentException(
                    "ELIMINATED 戰局不可仍保有玩家城池或 active 玩家勢力。"
                );
            }
            if (gameState.actionPointsRemaining != 0) {
                throw new IllegalArgumentException("ELIMINATED 戰局的行動力必須為 0。");
            }
        }
    }

    private static void validateFactionState(FactionState factionState) {
        if (factionState == null) {
            throw new IllegalArgumentException("FactionState 不可為 null。");
        }
        requireText(factionState.factionId, "factionState.factionId");
        requireNonNegative(factionState.gold, "factionState.gold");
        requireNonNegative(factionState.food, "factionState.food");
        requireRange(factionState.aiActionPointsPerTurn, 0, 9, "factionState.aiActionPointsPerTurn");
        requireRange(factionState.aiActionPointsRemaining, 0, factionState.aiActionPointsPerTurn,
            "factionState.aiActionPointsRemaining");
        if (factionState.cityIntelligence == null) {
            throw new IllegalArgumentException("factionState.cityIntelligence 不可為 null。");
        }
        if (factionState.active) {
            requireText(factionState.capitalCityId, "factionState.capitalCityId");
        }
    }

    private static void validateCityState(CityState cityState) {
        if (cityState == null) {
            throw new IllegalArgumentException("CityState 不可為 null。");
        }
        requireText(cityState.cityId, "cityState.cityId");
        requireText(cityState.ownerFactionId, "cityState.ownerFactionId");
        requireNonNegative(cityState.population, "cityState.population");
        requireRange(cityState.agriculture, 0, 100, "cityState.agriculture");
        requireRange(cityState.commerce, 0, 100, "cityState.commerce");
        requireRange(cityState.waterControl, 0, 100, "cityState.waterControl");
        requireRange(cityState.defense, 0, 100, "cityState.defense");
        requireNonNegative(cityState.troops, "cityState.troops");
        requireRange(cityState.publicOrder, 0, 100, "cityState.publicOrder");
        requireRange(
            cityState.publicOrderRecoveryStreakMonths,
            0,
            3,
            "cityState.publicOrderRecoveryStreakMonths"
        );
        requireRange(cityState.training, 0, 100, "cityState.training");
        requireRange(cityState.morale, 0, 100, "cityState.morale");
        validateQualityFraction(cityState.training, cityState.trainingFraction, "cityState.trainingFraction");
        validateQualityFraction(cityState.morale, cityState.moraleFraction, "cityState.moraleFraction");
        requireRange(
            cityState.harvestModifierPercent,
            0,
            100,
            "cityState.harvestModifierPercent"
        );
        if (cityState.defensePolicy == null || !cityState.defensePolicy.isActive()) {
            throw new IllegalArgumentException("cityState.defensePolicy 必須是目前有效方針。");
        }
        requireNonNegative(cityState.scoutedUntilTurn, "cityState.scoutedUntilTurn");
    }

    private static void validateArmyState(
        ArmyState armyState,
        Set<String> factionIds,
        Map<String, CityState> cityStatesById
    ) {
        if (armyState == null) {
            throw new IllegalArgumentException("ArmyState 不可為 null。");
        }
        requireText(armyState.armyId, "armyState.armyId");
        if (armyState.expeditionGroupId != null) {
            requireText(armyState.expeditionGroupId, "armyState.expeditionGroupId");
        }
        requireText(armyState.factionId, "armyState.factionId");
        requireText(armyState.originCityId, "armyState.originCityId");
        requireText(armyState.targetCityId, "armyState.targetCityId");
        requireFactionReference(factionIds, armyState.factionId, "armyState.factionId");
        requireCityReference(cityStatesById, armyState.originCityId, "armyState.originCityId");
        requireCityReference(cityStatesById, armyState.targetCityId, "armyState.targetCityId");
        if (armyState.originCityId.equals(armyState.targetCityId)) {
            throw new IllegalArgumentException("軍隊起點與目標不可相同。");
        }
        if (armyState.remainingTravelMonths < 0) {
            throw new IllegalArgumentException("remainingTravelMonths 不可小於 0。");
        }
        if (armyState.totalTravelMonths < 1
            || armyState.remainingTravelMonths > armyState.totalTravelMonths) {
            throw new IllegalArgumentException("軍隊總行程與剩餘行程不一致。");
        }
        if (armyState.initialTroops < armyState.troops || armyState.initialTroops < 1) {
            throw new IllegalArgumentException("軍隊初始兵力不可小於目前兵力。");
        }
        if (armyState.postEncounterOrder == null) {
            throw new IllegalArgumentException("軍隊接戰後命令不可為 null。");
        }
        if (armyState.isRetreating()) {
            if (armyState.returningFromRoad) {
                if (armyState.retreatRouteCityIds != null || armyState.remainingTravelMonths <= 0
                    || !armyState.armyId.equals(armyState.expeditionGroupId)) {
                    throw new IllegalArgumentException("道路返城軍狀態不完整。");
                }
            } else if (armyState.retreatRouteCityIds.length < 2
                || !armyState.targetCityId.equals(armyState.retreatRouteCityIds[0])) {
                throw new IllegalArgumentException("退卻路線必須由原戰場開始並包含目的地。");
            }
            if (!armyState.returningFromRoad && (armyState.retreatRouteIndex < 0
                || armyState.retreatRouteIndex >= armyState.retreatRouteCityIds.length - 1)) {
                throw new IllegalArgumentException("retreatRouteIndex 超出尚未抵達的退卻路線範圍。");
            }
            if (armyState.remainingTravelMonths <= 0) {
                throw new IllegalArgumentException("退卻軍目前道路倒數必須大於 0。");
            }
            if (!armyState.armyId.equals(armyState.expeditionGroupId)) {
                throw new IllegalArgumentException("退卻軍必須使用獨立 armyId 作為群組 ID。");
            }
            for (String routeCityId : armyState.returningFromRoad
                ? new String[0] : armyState.retreatRouteCityIds) {
                requireText(routeCityId, "armyState.retreatRouteCityIds");
                requireCityReference(cityStatesById, routeCityId,
                    "armyState.retreatRouteCityIds");
            }
        } else if (armyState.retreatRouteIndex != 0) {
            throw new IllegalArgumentException("一般行軍的 retreatRouteIndex 必須為 0。");
        }
        if (armyState.troops < 1) {
            throw new IllegalArgumentException("armyState.troops 必須大於或等於 1。");
        }
        requireRange(armyState.training, 0, 100, "armyState.training");
        requireRange(armyState.morale, 0, 100, "armyState.morale");
        validateQualityFraction(armyState.training, armyState.trainingFraction, "armyState.trainingFraction");
        validateQualityFraction(armyState.morale, armyState.moraleFraction, "armyState.moraleFraction");
        if (armyState.tactic == null || !armyState.tactic.isActive()) {
            throw new IllegalArgumentException("armyState.tactic 必須是目前有效戰術。");
        }
    }

    private static void validateBattleReport(
        BattleReport battleReport,
        Set<String> factionIds,
        Map<String, CityState> cityStatesById
    ) {
        if (battleReport == null) {
            throw new IllegalArgumentException("BattleReport 不可為 null。");
        }
        requireText(battleReport.battleId, "battleReport.battleId");
        requireText(battleReport.originCityId, "battleReport.originCityId");
        requireText(battleReport.targetCityId, "battleReport.targetCityId");
        requireText(battleReport.attackerFactionId, "battleReport.attackerFactionId");
        requireText(battleReport.defenderFactionId, "battleReport.defenderFactionId");
        requireFactionReference(
            factionIds,
            battleReport.attackerFactionId,
            "battleReport.attackerFactionId"
        );
        requireFactionReference(
            factionIds,
            battleReport.defenderFactionId,
            "battleReport.defenderFactionId"
        );
        if (battleReport.outcome != BattleOutcome.DRAW) {
            requireText(battleReport.winnerFactionId, "battleReport.winnerFactionId");
            requireFactionReference(factionIds, battleReport.winnerFactionId,
                "battleReport.winnerFactionId");
        } else if (battleReport.winnerFactionId != null) {
            throw new IllegalArgumentException("平手戰報不可指定勝方。");
        }
        requireCityReference(
            cityStatesById,
            battleReport.originCityId,
            "battleReport.originCityId"
        );
        requireCityReference(
            cityStatesById,
            battleReport.targetCityId,
            "battleReport.targetCityId"
        );
        if (battleReport.resolvedTurn < 1) {
            throw new IllegalArgumentException("battleReport.resolvedTurn 必須大於或等於 1。");
        }
        if (battleReport.resolvedYear < 1) {
            throw new IllegalArgumentException("battleReport.resolvedYear 必須大於或等於 1。");
        }
        if (battleReport.resolvedMonth < 1 || battleReport.resolvedMonth > 12) {
            throw new IllegalArgumentException("battleReport.resolvedMonth 超出合法範圍。");
        }
        if (battleReport.attackerTactic == null) {
            throw new IllegalArgumentException("battleReport.attackerTactic 不可為 null。");
        }
        if (battleReport.battleRulesVersion != 0 && battleReport.battleRulesVersion != 2
            && battleReport.battleRulesVersion != 3) {
            throw new IllegalArgumentException("battleReport.battleRulesVersion 只支援 0、2 或 3。");
        }
        if (battleReport.defenderPolicyRecorded && battleReport.defenderPolicy == null) {
            throw new IllegalArgumentException("已記錄的守方方針不可為 null。");
        }
        if (battleReport.outcome == null) {
            throw new IllegalArgumentException("battleReport.outcome 不可為 null。");
        }
        if (battleReport.attackerContributions == null) {
            throw new IllegalArgumentException("battleReport.attackerContributions 不可為 null。");
        }
        if (battleReport.attackerStrength < 0) {
            throw new IllegalArgumentException("battleReport.attackerStrength 不可小於 0。");
        }
        requireNonNegative(battleReport.defenderBaseStrength,
            "battleReport.defenderBaseStrength");
        requireNonNegative(battleReport.defenderStrength, "battleReport.defenderStrength");
        if (battleReport.battleRulesVersion == 2) {
            validateCurrentBattleSnapshot(battleReport);
        }
        if (battleReport.routeEncounter) {
            validateRoadEncounterSnapshot(battleReport);
        } else if (battleReport.battleRulesVersion == 3) {
            throw new IllegalArgumentException("第三版戰報必須是道路接戰。");
        }
        requireNonNegative(battleReport.attackerTroopsBefore, "battleReport.attackerTroopsBefore");
        requireNonNegative(battleReport.defenderTroopsBefore, "battleReport.defenderTroopsBefore");
        requireRange(battleReport.attackerTraining, 0, 100, "battleReport.attackerTraining");
        requireRange(battleReport.defenderTraining, 0, 100, "battleReport.defenderTraining");
        requireRange(battleReport.defenderDefense, 0, 100, "battleReport.defenderDefense");
        requireRange(battleReport.attackerMorale, 0, 100, "battleReport.attackerMorale");
        requireRange(battleReport.defenderMorale, 0, 100, "battleReport.defenderMorale");
        if (battleReport.outcome == BattleOutcome.UNOPPOSED_OCCUPATION
            && (battleReport.defenderTroopsBefore != 0
                || battleReport.attackerLosses != 0
                || battleReport.defenderLosses != 0
                || !battleReport.cityCaptured
                || !battleReport.attackerFactionId.equals(battleReport.winnerFactionId))) {
            throw new IllegalArgumentException("無抵抗佔領不可有守軍或戰鬥傷亡，且必須由攻方佔領。");
        }
        requireNonNegative(battleReport.attackerLosses, "battleReport.attackerLosses");
        requireNonNegative(battleReport.defenderLosses, "battleReport.defenderLosses");
        requireNonNegative(battleReport.attackerSurvivors, "battleReport.attackerSurvivors");
        requireNonNegative(battleReport.defenderSurvivors, "battleReport.defenderSurvivors");
        if ((long) battleReport.attackerLosses + battleReport.attackerSurvivors
            != battleReport.attackerTroopsBefore) {
            throw new IllegalArgumentException("戰報攻方損失與生還數不等於開戰兵力。");
        }
        if ((long) battleReport.defenderLosses + battleReport.defenderSurvivors
            != battleReport.defenderTroopsBefore) {
            throw new IllegalArgumentException("戰報守方損失與生還數不等於開戰兵力。");
        }
        validateBattleContributions(battleReport, factionIds, cityStatesById);
    }

    private static void validateCurrentBattleSnapshot(BattleReport battleReport) {
        if (!battleReport.attackerTactic.isActive()
            || !battleReport.defenderPolicyRecorded
            || battleReport.defenderPolicy == null
            || !battleReport.defenderPolicy.isActive()) {
            throw new IllegalArgumentException("目前戰報必須保存有效戰術與防守方針。");
        }
        if (battleReport.attackerContributions.length == 0) {
            throw new IllegalArgumentException("目前戰報必須保存攻方來源戰力快照。");
        }
        if (battleReport.attackerTactic
            != battleReport.attackerContributions[0].attackerTactic) {
            throw new IllegalArgumentException("戰報摘要戰術必須等於第一個攻方來源戰術。");
        }
        int expectedDefenderBaseStrength = MilitaryRules.calculateDefenderStrength(
            battleReport.defenderTroopsBefore, battleReport.defenderTraining,
            battleReport.defenderMorale, battleReport.defenderDefense);
        if (battleReport.defenderBaseStrength != expectedDefenderBaseStrength) {
            throw new IllegalArgumentException("戰報守方基礎戰力快照不一致。");
        }
    }

    private static void validateRoadEncounterSnapshot(BattleReport report) {
        if (report.battleRulesVersion != 3 || report.defenderTactic == null
            || !report.defenderTactic.isActive()
            || report.attackerPostEncounterOrder == null
            || report.defenderPostEncounterOrder == null
            || report.defenderPolicyRecorded || report.defenderPolicy != null
            || report.cityCaptured || report.outcome == BattleOutcome.UNOPPOSED_OCCUPATION) {
            throw new IllegalArgumentException("道路接戰快照不完整。");
        }
        if (report.outcome == BattleOutcome.ATTACKER_VICTORY && report.defenderContinued
            || report.outcome == BattleOutcome.DEFENDER_VICTORY && report.attackerContinued
            || report.outcome == BattleOutcome.DRAW
                && (report.attackerContinued || report.defenderContinued)) {
            throw new IllegalArgumentException("道路接戰後續行軍與勝負不一致。");
        }
    }

    private static void validateBattleContributions(
        BattleReport battleReport,
        Set<String> factionIds,
        Map<String, CityState> cityStatesById
    ) {
        if (battleReport.attackerContributions.length == 0) {
            return;
        }
        Set<String> armyIds = new HashSet<>();
        long troopsBefore = 0;
        long losses = 0;
        long survivors = 0;
        long totalBaseStrength = 0;
        long totalEffectiveStrength = 0;
        long counteredBaseStrength = 0;
        for (BattleContribution contribution : battleReport.attackerContributions) {
            if (contribution == null) {
                throw new IllegalArgumentException("BattleContribution 不可為 null。");
            }
            requireText(contribution.armyId, "battleContribution.armyId");
            requireText(contribution.factionId, "battleContribution.factionId");
            requireText(contribution.originCityId, "battleContribution.originCityId");
            if (!armyIds.add(contribution.armyId)) {
                throw new IllegalArgumentException("BattleContribution armyId 重複：" + contribution.armyId);
            }
            requireFactionReference(factionIds, contribution.factionId, "battleContribution.factionId");
            requireCityReference(cityStatesById, contribution.originCityId, "battleContribution.originCityId");
            if (!battleReport.attackerFactionId.equals(contribution.factionId)) {
                throw new IllegalArgumentException("BattleContribution 勢力與戰報攻方不一致。");
            }
            requireNonNegative(contribution.troopsBefore, "battleContribution.troopsBefore");
            requireNonNegative(contribution.losses, "battleContribution.losses");
            requireNonNegative(contribution.survivors, "battleContribution.survivors");
            requireRange(contribution.training, 0, 100, "battleContribution.training");
            requireRange(contribution.morale, 0, 100, "battleContribution.morale");
            requireNonNegative(contribution.baseStrength, "battleContribution.baseStrength");
            requireNonNegative(contribution.strength, "battleContribution.strength");
            if (battleReport.battleRulesVersion == 2) {
                validateCurrentContribution(battleReport, contribution);
                totalBaseStrength = saturatingAdd(totalBaseStrength,
                    contribution.baseStrength);
                totalEffectiveStrength = saturatingAdd(totalEffectiveStrength,
                    contribution.strength);
                if (battleReport.outcome != BattleOutcome.UNOPPOSED_OCCUPATION
                    && battleReport.defenderPolicy.defeats(contribution.attackerTactic)) {
                    counteredBaseStrength = saturatingAdd(counteredBaseStrength,
                        contribution.baseStrength);
                }
            }
            if ((long) contribution.losses + contribution.survivors != contribution.troopsBefore) {
                throw new IllegalArgumentException("BattleContribution 傷亡不守恆。");
            }
            troopsBefore += contribution.troopsBefore;
            losses += contribution.losses;
            survivors += contribution.survivors;
        }
        if (troopsBefore != battleReport.attackerTroopsBefore
            || losses != battleReport.attackerLosses
            || survivors != battleReport.attackerSurvivors) {
            throw new IllegalArgumentException("BattleContribution 合計與戰報攻方總數不一致。");
        }
        if (battleReport.battleRulesVersion == 2) {
            int expectedDefenderPercent = battleReport.outcome
                == BattleOutcome.UNOPPOSED_OCCUPATION ? 100
                : MilitaryRules.defenderMatchupPercent(
                    counteredBaseStrength, totalBaseStrength);
            int expectedDefenderStrength = MilitaryRules.applyMatchupPercent(
                battleReport.defenderBaseStrength, expectedDefenderPercent);
            if (battleReport.attackerStrength != totalEffectiveStrength
                || battleReport.defenderMatchupPercent != expectedDefenderPercent
                || battleReport.defenderStrength != expectedDefenderStrength) {
                throw new IllegalArgumentException("目前戰報的相剋戰力快照不一致。");
            }
        }
    }

    private static void validateCurrentContribution(BattleReport battleReport,
        BattleContribution contribution) {
        if (contribution.attackerTactic == null || !contribution.attackerTactic.isActive()) {
            throw new IllegalArgumentException("目前 BattleContribution 必須保存有效戰術。");
        }
        int expectedBaseStrength = MilitaryRules.calculateAttackerStrength(
            contribution.troopsBefore, contribution.training, contribution.morale);
        int expectedMatchupPercent = battleReport.outcome
            == BattleOutcome.UNOPPOSED_OCCUPATION ? 100
            : MilitaryRules.attackerMatchupPercent(contribution.attackerTactic,
                battleReport.defenderPolicy);
        int expectedStrength = MilitaryRules.applyMatchupPercent(
            expectedBaseStrength, expectedMatchupPercent);
        if (contribution.baseStrength != expectedBaseStrength
            || contribution.strength != expectedStrength) {
            throw new IllegalArgumentException("目前 BattleContribution 戰力快照不一致。");
        }
    }

    private static String effectiveGroupId(ArmyState armyState) {
        return armyState.expeditionGroupId == null ? armyState.armyId : armyState.expeditionGroupId;
    }

    private static void validateIntelligence(FactionState factionState, Set<String> factionIds,
        Map<String, CityState> cityStatesById) {
        Set<String> cityIds = new HashSet<>();
        for (CityIntelligenceSnapshot snapshot : factionState.cityIntelligence) {
            if (snapshot == null) {
                throw new IllegalArgumentException("CityIntelligenceSnapshot 不可為 null。");
            }
            requireText(snapshot.cityId, "cityIntelligence.cityId");
            requireText(snapshot.ownerFactionId, "cityIntelligence.ownerFactionId");
            requireCityReference(cityStatesById, snapshot.cityId, "cityIntelligence.cityId");
            requireFactionReference(factionIds, snapshot.ownerFactionId,
                "cityIntelligence.ownerFactionId");
            if (!cityIds.add(snapshot.cityId)) {
                throw new IllegalArgumentException("同一勢力的城池情報不可重複：" + snapshot.cityId);
            }
            if (snapshot.observedTurn < 1 || snapshot.validThroughTurn < snapshot.observedTurn) {
                throw new IllegalArgumentException("城池情報回合範圍無效：" + snapshot.cityId);
            }
            if (snapshot.observedYear < 1 || snapshot.observedMonth < 1
                || snapshot.observedMonth > 12) {
                throw new IllegalArgumentException("城池情報年月無效：" + snapshot.cityId);
            }
            requireNonNegative(snapshot.troops, "cityIntelligence.troops");
            requireNonNegative(snapshot.population, "cityIntelligence.population");
            requireRange(snapshot.agriculture, 0, 100, "cityIntelligence.agriculture");
            requireRange(snapshot.commerce, 0, 100, "cityIntelligence.commerce");
            requireRange(snapshot.waterControl, 0, 100, "cityIntelligence.waterControl");
            requireRange(snapshot.defense, 0, 100, "cityIntelligence.defense");
            requireRange(snapshot.training, 0, 100, "cityIntelligence.training");
            requireRange(snapshot.morale, 0, 100, "cityIntelligence.morale");
            requireRange(snapshot.publicOrder, 0, 100, "cityIntelligence.publicOrder");
            if (snapshot.defensePolicy != null) {
                throw new IllegalArgumentException("城池情報不可保存防守方針。");
            }
        }
    }

    private static void validateTurnStartCities(CityState[] snapshots,
        Set<String> factionIds, Map<String, CityState> currentById) {
        Set<String> snapshotIds = new HashSet<>();
        for (CityState snapshot : snapshots) {
            validateCityState(snapshot);
            requireFactionReference(factionIds, snapshot.ownerFactionId,
                "turnStartCityStates.ownerFactionId");
            if (!currentById.containsKey(snapshot.cityId) || !snapshotIds.add(snapshot.cityId)) {
                throw new IllegalArgumentException("月初城池快照與目前地圖不一致：" + snapshot.cityId);
            }
        }
    }

    private static final class ArmyGroupValidation {
        private final String factionId;
        private final String targetCityId;
        private boolean hasTravellingArmy;

        private ArmyGroupValidation(String factionId, String targetCityId) {
            this.factionId = factionId;
            this.targetCityId = targetCityId;
        }
    }

    private static void validateCapitalReference(
        FactionState factionState,
        Map<String, CityState> cityStatesById
    ) {
        if (!factionState.active) {
            return;
        }
        CityState capitalCityState = cityStatesById.get(factionState.capitalCityId);
        if (capitalCityState == null) {
            throw new IllegalArgumentException(
                "找不到勢力主城狀態：" + factionState.capitalCityId
            );
        }
        if (!factionState.factionId.equals(capitalCityState.ownerFactionId)) {
            throw new IllegalArgumentException(
                "勢力主城的 ownerFactionId 不一致：" + factionState.capitalCityId
            );
        }
    }

    private static void requireFactionReference(
        Set<String> factionIds,
        String factionId,
        String fieldName
    ) {
        if (!factionIds.contains(factionId)) {
            throw new IllegalArgumentException(fieldName + " 沒有對應勢力：" + factionId);
        }
    }

    private static void requireCityReference(
        Map<String, CityState> cityStatesById,
        String cityId,
        String fieldName
    ) {
        if (!cityStatesById.containsKey(cityId)) {
            throw new IllegalArgumentException(fieldName + " 沒有對應城池：" + cityId);
        }
    }

    private static void validateQualityFraction(int quality, int fraction, String fieldName) {
        requireRange(fraction, 0, TroopQualityRules.SCALE - 1, fieldName);
        if (quality == 100 && fraction != 0) {
            throw new IllegalArgumentException(fieldName + " 不可讓素質超過 100。");
        }
    }

    private static void requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " 不可為空。");
        }
    }

    private static void requireNonNegative(int value, String fieldName) {
        if (value < 0) {
            throw new IllegalArgumentException(fieldName + " 不可小於 0。");
        }
    }

    private static long saturatingAdd(long left, long right) {
        return Long.MAX_VALUE - left < right ? Long.MAX_VALUE : left + right;
    }

    private static void requireRange(int value, int minimum, int maximum, String fieldName) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(
                fieldName + " 必須介於 " + minimum + " 到 " + maximum + "。"
            );
        }
    }
}
