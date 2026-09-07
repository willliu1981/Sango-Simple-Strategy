package idv.kuan.studio.sango.domain.model;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import idv.kuan.studio.sango.SangoVersion;
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
        requireText(gameState.victoryTargetCityId, "victoryTargetCityId");
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
        if (!cityStatesById.containsKey(gameState.victoryTargetCityId)) {
            throw new IllegalArgumentException(
                "victoryTargetCityId 沒有對應城池：" + gameState.victoryTargetCityId
            );
        }
        requireCityReference(cityStatesById, gameState.strategicMapFocusedCityId,
            "strategicMapFocusedCityId");

        for (FactionState factionState : gameState.factionStates) {
            validateCapitalReference(factionState, cityStatesById);
        }

        Set<String> armyIds = new HashSet<>();
        for (ArmyState armyState : gameState.armyStates) {
            validateArmyState(armyState, factionIds, cityStatesById);
            if (!armyIds.add(armyState.armyId)) {
                throw new IllegalArgumentException("ArmyState ID 重複：" + armyState.armyId);
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
        requireText(armyState.factionId, "armyState.factionId");
        requireText(armyState.originCityId, "armyState.originCityId");
        requireText(armyState.targetCityId, "armyState.targetCityId");
        requireFactionReference(factionIds, armyState.factionId, "armyState.factionId");
        requireCityReference(cityStatesById, armyState.originCityId, "armyState.originCityId");
        requireCityReference(cityStatesById, armyState.targetCityId, "armyState.targetCityId");
        if (armyState.originCityId.equals(armyState.targetCityId)) {
            throw new IllegalArgumentException("軍隊起點與目標不可相同。");
        }
        if (armyState.remainingTravelMonths < 1) {
            throw new IllegalArgumentException("remainingTravelMonths 必須大於或等於 1。");
        }
        if (armyState.troops < 1) {
            throw new IllegalArgumentException("armyState.troops 必須大於或等於 1。");
        }
        requireRange(armyState.training, 0, 100, "armyState.training");
        requireRange(armyState.morale, 0, 100, "armyState.morale");
        validateQualityFraction(armyState.training, armyState.trainingFraction, "armyState.trainingFraction");
        validateQualityFraction(armyState.morale, armyState.moraleFraction, "armyState.moraleFraction");
        if (armyState.tactic == null) {
            throw new IllegalArgumentException("armyState.tactic 不可為 null。");
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
        requireText(battleReport.winnerFactionId, "battleReport.winnerFactionId");
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
        requireFactionReference(
            factionIds,
            battleReport.winnerFactionId,
            "battleReport.winnerFactionId"
        );
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
        if (battleReport.outcome == null) {
            throw new IllegalArgumentException("battleReport.outcome 不可為 null。");
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

    private static void requireRange(int value, int minimum, int maximum, String fieldName) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(
                fieldName + " 必須介於 " + minimum + " 到 " + maximum + "。"
            );
        }
    }
}
