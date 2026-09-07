package idv.kuan.studio.sango.ui.support;

import idv.kuan.studio.sango.domain.rule.TroopQualityRules;

/** 顯示到小數兩位並向下截斷；內部六位小數不因格式化而消失。 */
public final class TroopQualityTextFormatter {
    private TroopQualityTextFormatter() {
    }

    public static String format(int scaledQuality) {
        int whole = scaledQuality / TroopQualityRules.SCALE;
        int hundredths = scaledQuality % TroopQualityRules.SCALE / (TroopQualityRules.SCALE / 100);
        if (hundredths == 0) {
            return Integer.toString(whole);
        }
        return whole + "." + (hundredths < 10 ? "0" : "") + hundredths;
    }
}
