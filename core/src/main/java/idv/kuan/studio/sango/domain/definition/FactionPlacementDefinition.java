package idv.kuan.studio.sango.domain.definition;

/**
 * 同一全國劇本中同時存在的勢力與初始領地；選擇玩家不會重排其他勢力。
 */
public final class FactionPlacementDefinition {
    public String factionId;
    public String capitalCityId;
    public String[] cityIds;

    public FactionPlacementDefinition() {
    }
}
