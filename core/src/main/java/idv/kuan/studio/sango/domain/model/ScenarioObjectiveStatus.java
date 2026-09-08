package idv.kuan.studio.sango.domain.model;

/**
 * 劇本最初指定目標的完成狀態。
 * 目標成功或失敗後，玩家仍可進入自由征戰並繼續遊玩。
 */
public enum ScenarioObjectiveStatus {
    IN_PROGRESS,
    ACHIEVED,
    FAILED
}
