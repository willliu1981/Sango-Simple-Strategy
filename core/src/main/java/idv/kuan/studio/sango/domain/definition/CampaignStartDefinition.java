package idv.kuan.studio.sango.domain.definition;

import idv.kuan.studio.sango.domain.model.ScenarioObjectiveType;

/**
 * 同一劇本中，各可選勢力對應的起始城與勝利目標。
 */
public final class CampaignStartDefinition {
    public String playerFactionId;
    public String startCityId;
    /** 未指定時視為舊版的 CAPTURE_CITY。 */
    public ScenarioObjectiveType objectiveType;
    public String targetCityId;
    public String targetFactionId;

    public CampaignStartDefinition() {
    }
}
