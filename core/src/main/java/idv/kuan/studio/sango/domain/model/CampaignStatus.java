package idv.kuan.studio.sango.domain.model;

/**
 * 0.3.0 GameState schema 2 的舊欄位型別。
 *
 * 只保留供存檔遷移使用；新戰局改用 ScenarioObjectiveStatus 與 GameplayStatus。
 */
@Deprecated
public enum CampaignStatus {
    IN_PROGRESS,
    VICTORY,
    DEFEAT
}
