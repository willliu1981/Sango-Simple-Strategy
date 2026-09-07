package idv.kuan.studio.sango.repository.save;

import idv.kuan.studio.sango.SangoVersion;
import idv.kuan.studio.sango.domain.model.BattleReport;
import idv.kuan.studio.sango.domain.model.ArmyState;
import idv.kuan.studio.sango.domain.rule.FactionActionPointRules;
import idv.kuan.studio.sango.domain.rule.TroopQualityRules;
import idv.kuan.studio.sango.domain.model.CampaignStatus;
import idv.kuan.studio.sango.domain.model.CityState;
import idv.kuan.studio.sango.domain.model.GameState;
import idv.kuan.studio.sango.domain.model.GameplayStatus;
import idv.kuan.studio.sango.domain.model.ScenarioObjectiveStatus;

/**
 * 逐版遷移 2 -> 3 -> 4 -> 5 -> 6 -> 7。僅遷移狀態結構，不替換舊劇本或憑空增加領地。
 */
@SuppressWarnings("deprecation")
public final class GameStateMigrator {
    public GameState migrate(GameState sourceState) {
        if (sourceState == null) {
            throw new IllegalArgumentException("GameState 不可為 null。");
        }
        GameState migratedState = sourceState.copy();
        if (migratedState.schemaVersion == 2) {
            migrateSchemaTwoToThree(migratedState);
        }
        if (migratedState.schemaVersion == 3) {
            migrateSchemaThreeToFour(migratedState);
        }
        if (migratedState.schemaVersion == 4) {
            migrateSchemaFourToFive(migratedState);
        }
        if (migratedState.schemaVersion == 5) {
            migrateSchemaFiveToSix(migratedState);
        }
        if (migratedState.schemaVersion == 6) {
            migrateSchemaSixToSeven(migratedState);
        }
        if (migratedState.schemaVersion != SangoVersion.GAME_STATE_SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                "不支援的 GameState schemaVersion：" + migratedState.schemaVersion
            );
        }
        normalizeCurrentState(migratedState);
        return migratedState;
    }

    private void migrateSchemaTwoToThree(GameState gameState) {
        CampaignStatus legacyStatus = gameState.campaignStatus;
        if (legacyStatus == null) {
            legacyStatus = CampaignStatus.IN_PROGRESS;
        }
        gameState.scenarioObjectiveStatus = switch (legacyStatus) {
            case IN_PROGRESS -> ScenarioObjectiveStatus.IN_PROGRESS;
            case VICTORY -> ScenarioObjectiveStatus.ACHIEVED;
            case DEFEAT -> ScenarioObjectiveStatus.FAILED;
        };
        gameState.gameplayStatus = gameState.requirePlayerFactionState().active
            ? GameplayStatus.ACTIVE : GameplayStatus.ELIMINATED;
        gameState.nextBattleSequence = 1;
        gameState.battleReports = new BattleReport[0];
        gameState.campaignStatus = null;
        gameState.schemaVersion = 3;
        if (gameState.gameplayStatus == GameplayStatus.ACTIVE
            && gameState.actionPointsRemaining == 0
            && legacyStatus != CampaignStatus.IN_PROGRESS) {
            gameState.actionPointsRemaining = gameState.actionPointsPerTurn;
        }
    }

    private void migrateSchemaThreeToFour(GameState gameState) {
        if (gameState.cityStates != null) {
            for (CityState cityState : gameState.cityStates) {
                if (cityState != null) {
                    // 只在舊 schema 缺少城市士氣時初始化，不覆寫 schema 4 的合法零士氣。
                    cityState.morale = Math.max(50, Math.min(100, cityState.publicOrder));
                }
            }
        }
        if (gameState.battleReports != null) {
            for (BattleReport battleReport : gameState.battleReports) {
                if (battleReport != null) {
                    battleReport.moraleRecorded = false;
                }
            }
        }
        gameState.schemaVersion = 4;
    }

    private void migrateSchemaFourToFive(GameState gameState) {
        for (CityState cityState : gameState.cityStates) {
            cityState.trainingFraction = 0;
            cityState.moraleFraction = 0;
        }
        for (ArmyState armyState : gameState.armyStates) {
            armyState.trainingFraction = 0;
            armyState.moraleFraction = 0;
        }
        // 只補上原本不存在的 AI 快照，不回補玩家月中已花掉的 AP。
        FactionActionPointRules.initializeMigratedAi(gameState);
        gameState.schemaVersion = 5;
    }

    private void migrateSchemaFiveToSix(GameState gameState) {
        for (CityState cityState : gameState.cityStates) {
            TroopQualityRules.set(cityState,
                TroopQualityRules.training(cityState), TroopQualityRules.morale(cityState));
        }
        for (ArmyState armyState : gameState.armyStates) {
            armyState.training = Math.min(100, Math.max(1,
                (TroopQualityRules.training(armyState) + TroopQualityRules.SCALE - 1)
                    / TroopQualityRules.SCALE));
            armyState.trainingFraction = 0;
            armyState.morale = Math.min(100, Math.max(1,
                (TroopQualityRules.morale(armyState) + TroopQualityRules.SCALE - 1)
                    / TroopQualityRules.SCALE));
            armyState.moraleFraction = 0;
        }
        gameState.strategicMapFocusedCityId = gameState.requirePlayerFactionState().active
            ? gameState.requirePlayerFactionState().capitalCityId
            : gameState.victoryTargetCityId;
        gameState.schemaVersion = 6;
    }

    private void migrateSchemaSixToSeven(GameState gameState) {
        for (CityState cityState : gameState.cityStates) {
            cityState.publicOrderRecoveryStreakMonths = 0;
        }
        gameState.schemaVersion = 7;
    }

    private void normalizeCurrentState(GameState gameState) {
        if (gameState.battleReports == null) {
            gameState.battleReports = new BattleReport[0];
        }
        if (gameState.nextBattleSequence < 1) {
            gameState.nextBattleSequence = gameState.battleReports.length + 1;
        }
        gameState.campaignStatus = null;
    }
}
