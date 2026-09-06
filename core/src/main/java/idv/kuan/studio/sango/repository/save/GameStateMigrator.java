package idv.kuan.studio.sango.repository.save;

import idv.kuan.studio.sango.SangoVersion;
import idv.kuan.studio.sango.domain.model.BattleReport;
import idv.kuan.studio.sango.domain.model.CampaignStatus;
import idv.kuan.studio.sango.domain.model.GameState;
import idv.kuan.studio.sango.domain.model.GameplayStatus;
import idv.kuan.studio.sango.domain.model.ScenarioObjectiveStatus;

/**
 * 將舊版 GameState 升級為目前的存檔格式。
 */
@SuppressWarnings("deprecation")
public final class GameStateMigrator {
    public GameState migrate(GameState sourceState) {
        if (sourceState == null) {
            throw new IllegalArgumentException("GameState 不可為 null。");
        }
        if (sourceState.schemaVersion == SangoVersion.GAME_STATE_SCHEMA_VERSION) {
            normalizeCurrentState(sourceState);
            return sourceState;
        }
        if (sourceState.schemaVersion == SangoVersion.LEGACY_GAME_STATE_SCHEMA_VERSION) {
            return migrateSchemaTwoToThree(sourceState);
        }
        throw new IllegalArgumentException(
            "不支援的 GameState schemaVersion：" + sourceState.schemaVersion
        );
    }

    private GameState migrateSchemaTwoToThree(GameState sourceState) {
        CampaignStatus legacyStatus = sourceState.campaignStatus;
        if (legacyStatus == null) {
            legacyStatus = CampaignStatus.IN_PROGRESS;
        }

        sourceState.scenarioObjectiveStatus = switch (legacyStatus) {
            case IN_PROGRESS -> ScenarioObjectiveStatus.IN_PROGRESS;
            case VICTORY -> ScenarioObjectiveStatus.ACHIEVED;
            case DEFEAT -> ScenarioObjectiveStatus.FAILED;
        };
        sourceState.gameplayStatus = sourceState.requirePlayerFactionState().active
            ? GameplayStatus.ACTIVE
            : GameplayStatus.ELIMINATED;
        sourceState.nextBattleSequence = 1;
        sourceState.battleReports = new BattleReport[0];
        sourceState.campaignStatus = null;
        sourceState.schemaVersion = SangoVersion.GAME_STATE_SCHEMA_VERSION;

        if (sourceState.gameplayStatus == GameplayStatus.ACTIVE
            && sourceState.actionPointsRemaining == 0
            && legacyStatus != CampaignStatus.IN_PROGRESS) {
            sourceState.actionPointsRemaining = sourceState.actionPointsPerTurn;
        }
        return sourceState;
    }

    private void normalizeCurrentState(GameState sourceState) {
        if (sourceState.battleReports == null) {
            sourceState.battleReports = new BattleReport[0];
        }
        if (sourceState.nextBattleSequence < 1) {
            sourceState.nextBattleSequence = sourceState.battleReports.length + 1;
        }
        sourceState.campaignStatus = null;
    }
}
