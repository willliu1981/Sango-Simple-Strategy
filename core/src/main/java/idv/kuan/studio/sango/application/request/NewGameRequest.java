package idv.kuan.studio.sango.application.request;

/**
 * 建立新局所需的最小輸入。
 */
public final class NewGameRequest {
    private final String scenarioId;
    private final String playerFactionId;

    public NewGameRequest(String scenarioId, String playerFactionId) {
        if (scenarioId == null || scenarioId.trim().isEmpty()) {
            throw new IllegalArgumentException("scenarioId 不可為空。");
        }
        if (playerFactionId == null || playerFactionId.trim().isEmpty()) {
            throw new IllegalArgumentException("playerFactionId 不可為空。");
        }
        this.scenarioId = scenarioId;
        this.playerFactionId = playerFactionId;
    }

    public String getScenarioId() {
        return scenarioId;
    }

    public String getPlayerFactionId() {
        return playerFactionId;
    }
}
