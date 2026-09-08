package idv.kuan.studio.sango.domain.definition;

/**
 * 同一劇本中，各可選勢力對應的起始城與勝利目標。
 */
public final class CampaignStartDefinition {
    public String playerFactionId;
    public String startCityId;
    public String targetCityId;

    public CampaignStartDefinition() {
    }
}
