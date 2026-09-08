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
    /** 舊劇本資料相容欄位；0.6.0 由 FactionActionPointRules 發放首月三點與後續民心總和額度。 */
    public int actionPointsPerTurn;
    public String mapId;
    public String[] factionIds;
    public String opponentFactionId;
    public String neutralFactionId;
    public int turnLimitMonths;
    public int enemyAttackDelayMonths;
    public CampaignStartDefinition[] playerStarts;
    /** 新劇本的固定勢力配置；null 表示保留舊六城的依玩家配置模式。 */
    public FactionPlacementDefinition[] initialFactions;

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
