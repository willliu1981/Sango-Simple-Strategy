package idv.kuan.studio.sango.domain.model;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import idv.kuan.studio.sango.SangoVersion;

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
            throw new IllegalArgumentException("不支援的 GameState schemaVersion：" + gameState.schemaVersion);
        }
        requireText(gameState.scenarioId, "scenarioId");
        requireText(gameState.playerFactionId, "playerFactionId");
        requireText(gameState.lastActionCode, "lastActionCode");
        if (gameState.currentTurn < 1) {
            throw new IllegalArgumentException("currentTurn 必須大於或等於 1。");
        }
        if (gameState.currentYear < 1) {
            throw new IllegalArgumentException("currentYear 必須大於或等於 1。");
        }
        if (gameState.currentMonth < 1 || gameState.currentMonth > 12) {
            throw new IllegalArgumentException("currentMonth 必須介於 1 到 12。");
        }
        if (gameState.actionPointsPerTurn < 1) {
            throw new IllegalArgumentException("actionPointsPerTurn 必須大於或等於 1。");
        }
        if (gameState.actionPointsRemaining < 0
            || gameState.actionPointsRemaining > gameState.actionPointsPerTurn) {
            throw new IllegalArgumentException("actionPointsRemaining 超出合法範圍。");
        }
        if (gameState.factionStates == null || gameState.factionStates.length == 0) {
            throw new IllegalArgumentException("factionStates 不可為空。");
        }
        if (gameState.cityStates == null || gameState.cityStates.length == 0) {
            throw new IllegalArgumentException("cityStates 不可為空。");
        }

        Set<String> factionIds = new HashSet<>();
        for (FactionState factionState : gameState.factionStates) {
            validateFactionState(factionState);
            if (!factionIds.add(factionState.factionId)) {
                throw new IllegalArgumentException(
                    "FactionState ID 重複：" + factionState.factionId
                );
            }
        }
        if (!factionIds.contains(gameState.playerFactionId)) {
            throw new IllegalArgumentException(
                "playerFactionId 沒有對應的 FactionState：" + gameState.playerFactionId
            );
        }

        Map<String, CityState> cityStatesById = new HashMap<>();
        for (CityState cityState : gameState.cityStates) {
            validateCityState(cityState);
            if (cityStatesById.put(cityState.cityId, cityState) != null) {
                throw new IllegalArgumentException("CityState ID 重複：" + cityState.cityId);
            }
            if (!factionIds.contains(cityState.ownerFactionId)) {
                throw new IllegalArgumentException(
                    "CityState.ownerFactionId 沒有對應勢力：" + cityState.ownerFactionId
                );
            }
        }

        for (FactionState factionState : gameState.factionStates) {
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

        gameState.requireCapitalCityState();
    }

    private static void validateFactionState(FactionState factionState) {
        if (factionState == null) {
            throw new IllegalArgumentException("FactionState 不可為 null。");
        }
        requireText(factionState.factionId, "factionState.factionId");
        requireText(factionState.capitalCityId, "factionState.capitalCityId");
        requireNonNegative(factionState.gold, "factionState.gold");
        requireNonNegative(factionState.food, "factionState.food");
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
        requireNonNegative(cityState.troops, "cityState.troops");
        requireRange(cityState.publicOrder, 0, 100, "cityState.publicOrder");
        requireRange(cityState.training, 0, 100, "cityState.training");
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
