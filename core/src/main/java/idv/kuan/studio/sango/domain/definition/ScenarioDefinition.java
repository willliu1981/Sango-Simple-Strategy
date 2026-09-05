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
    public String[] factionIds;

    public ScenarioDefinition() {
    }
}
