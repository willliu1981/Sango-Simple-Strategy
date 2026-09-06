package idv.kuan.studio.sango.domain.definition;

/**
 * 劇本固定資料。這些內容來自 assets，不應直接寫入存檔。
 */
public final class ScenarioDefinition {
    public String id;
    public String nameKey;
    public String descriptionKey;
    public int startYear;
    public int startMonth;
    public int initialTurn;
    public int actionPointsPerTurn;
    public String mapId;
    public String[] factionIds;
    public String opponentFactionId;
    public String neutralFactionId;
    public int turnLimitMonths;
    public int enemyAttackDelayMonths;
    public CampaignStartDefinition[] playerStarts;

    public ScenarioDefinition() {
    }

    public CampaignStartDefinition requirePlayerStart(String playerFactionId) {
        if (playerStarts != null) {
            for (CampaignStartDefinition playerStartDefinition : playerStarts) {
                if (playerStartDefinition != null
                    && playerFactionId.equals(playerStartDefinition.playerFactionId)) {
                    return playerStartDefinition;
                }
            }
        }
        throw new IllegalArgumentException("劇本缺少玩家起始設定：" + playerFactionId);
    }
}
