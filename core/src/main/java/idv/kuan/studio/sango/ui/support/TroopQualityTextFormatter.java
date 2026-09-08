package idv.kuan.studio.sango.ui.support;

import idv.kuan.studio.sango.domain.rule.TroopQualityRules;

/** 素質顯示為整數，舊存檔中的小數也一律向上取整。 */
public final class TroopQualityTextFormatter {
    private TroopQualityTextFormatter() {
    }

    public static String format(int scaledQuality) {
        int rounded = Math.min(100, Math.max(1,
            (scaledQuality + TroopQualityRules.SCALE - 1) / TroopQualityRules.SCALE));
        return Integer.toString(rounded);
    }
}
