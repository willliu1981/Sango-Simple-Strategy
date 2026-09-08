package idv.kuan.studio.sango.domain.model;

import idv.kuan.studio.sango.domain.rule.DefensePolicy;

/** 某一勢力在偵察當下取得的城池資料；之後不隨真實城況自動更新。 */
public final class CityIntelligenceSnapshot {
    public String cityId;
    public int observedTurn;
    public int observedYear;
    public int observedMonth;
    /** false 代表由舊存檔轉換，無法可靠還原原偵察年月。 */
    public boolean observationDateRecorded;
    public int validThroughTurn;
    public String ownerFactionId;
    public int troops;
    public int population;
    public int agriculture;
    public int commerce;
    public int waterControl;
    public int defense;
    public int training;
    public int morale;
    public int publicOrder;
    public DefensePolicy defensePolicy;

    public CityIntelligenceSnapshot copy() {
        CityIntelligenceSnapshot copied = new CityIntelligenceSnapshot();
        copied.cityId = cityId;
        copied.observedTurn = observedTurn;
        copied.observedYear = observedYear;
        copied.observedMonth = observedMonth;
        copied.observationDateRecorded = observationDateRecorded;
        copied.validThroughTurn = validThroughTurn;
        copied.ownerFactionId = ownerFactionId;
        copied.troops = troops;
        copied.population = population;
        copied.agriculture = agriculture;
        copied.commerce = commerce;
        copied.waterControl = waterControl;
        copied.defense = defense;
        copied.training = training;
        copied.morale = morale;
        copied.publicOrder = publicOrder;
        copied.defensePolicy = defensePolicy;
        return copied;
    }
}
