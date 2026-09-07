package idv.kuan.studio.sango.domain.rule;

import idv.kuan.studio.sango.domain.model.CityState;

/**
 * 月底民心自然恢復規則。連續三個合格月底起每月恢復一點，民心與連續月數皆有上限。
 */
public final class PublicOrderNaturalRecoveryRules {
    public static final int MINIMUM_QUALIFYING_PUBLIC_ORDER = 95;
    public static final int REQUIRED_STREAK_MONTHS = 3;
    public static final int MAXIMUM_PUBLIC_ORDER = 100;

    private PublicOrderNaturalRecoveryRules() {
    }

    /**
     * 套用單一城池的月底判定，並回傳本月實際恢復的民心點數。
     */
    public static int applyMonthEnd(
        CityState cityState,
        boolean ownerIsActiveAndNonNeutral,
        boolean ownerHadFoodShortage,
        boolean cityFlooded,
        boolean cityChangedOwner
    ) {
        if (cityState == null) {
            throw new IllegalArgumentException("cityState 不可為 null。");
        }

        boolean qualifies = ownerIsActiveAndNonNeutral
            && cityState.publicOrder >= MINIMUM_QUALIFYING_PUBLIC_ORDER
            && !ownerHadFoodShortage
            && !cityFlooded
            && !cityChangedOwner;
        if (!qualifies) {
            cityState.publicOrderRecoveryStreakMonths = 0;
            return 0;
        }

        cityState.publicOrderRecoveryStreakMonths = Math.min(
            REQUIRED_STREAK_MONTHS,
            cityState.publicOrderRecoveryStreakMonths + 1
        );
        if (cityState.publicOrderRecoveryStreakMonths < REQUIRED_STREAK_MONTHS
            || cityState.publicOrder >= MAXIMUM_PUBLIC_ORDER) {
            return 0;
        }

        cityState.publicOrder += 1;
        return 1;
    }
}
