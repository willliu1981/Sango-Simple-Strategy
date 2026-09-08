package idv.kuan.studio.sango.domain.service;

import idv.kuan.studio.sango.domain.rule.DefensePolicy;

/** 某勢力可合法用於畫面或決策的城池資訊。 */
public record KnownCityView(
    String cityId,
    String ownerFactionId,
    boolean exact,
    int observedTurn,
    int observedYear,
    int observedMonth,
    boolean observationDateRecorded,
    int validThroughTurn,
    int troops,
    int population,
    int agriculture,
    int commerce,
    int waterControl,
    int defense,
    int training,
    int morale,
    int publicOrder,
    DefensePolicy defensePolicy
) {
}
