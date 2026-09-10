package idv.kuan.studio.sango.application.result;

/**
 * 可由 UI 本地化的回合事件資料。
 */
public final class TurnEvent {
    private final TurnEventType type;
    private final String factionId;
    private final String cityId;
    private final String otherCityId;
    private final int primaryValue;
    private final int secondaryValue;
    private final int tertiaryValue;
    private final int quaternaryValue;
    private final int quinaryValue;

    public TurnEvent(
        TurnEventType type,
        String factionId,
        String cityId,
        String otherCityId,
        int primaryValue,
        int secondaryValue
    ) {
        this(type, factionId, cityId, otherCityId, primaryValue, secondaryValue, 0, 0, 0);
    }

    public TurnEvent(
        TurnEventType type,
        String factionId,
        String cityId,
        String otherCityId,
        int primaryValue,
        int secondaryValue,
        int tertiaryValue,
        int quaternaryValue,
        int quinaryValue
    ) {
        if (type == null) {
            throw new IllegalArgumentException("type 不可為 null。");
        }
        this.type = type;
        this.factionId = factionId;
        this.cityId = cityId;
        this.otherCityId = otherCityId;
        this.primaryValue = primaryValue;
        this.secondaryValue = secondaryValue;
        this.tertiaryValue = tertiaryValue;
        this.quaternaryValue = quaternaryValue;
        this.quinaryValue = quinaryValue;
    }

    public TurnEventType getType() {
        return type;
    }

    public String getFactionId() {
        return factionId;
    }

    public String getCityId() {
        return cityId;
    }

    public String getOtherCityId() {
        return otherCityId;
    }

    public int getPrimaryValue() {
        return primaryValue;
    }

    public int getSecondaryValue() {
        return secondaryValue;
    }

    public int getTertiaryValue() {
        return tertiaryValue;
    }

    public int getQuaternaryValue() {
        return quaternaryValue;
    }

    public int getQuinaryValue() {
        return quinaryValue;
    }
}
