package idv.kuan.studio.sango.ui.support;

import idv.kuan.studio.libgdx.simpleui.Sui;
import idv.kuan.studio.sango.domain.model.GameState;
import idv.kuan.studio.sango.domain.rule.NationalActionPointRules;

/**
 * 地圖與內政共用的全城民心摘要。僅格式化預估值，不重設或回補行動力。
 */
public final class NationalOrderTextFormatter {
    private NationalOrderTextFormatter() {
    }

    public static String formatPreview(GameState gameState) {
        NationalActionPointRules.PublicOrderSummary publicOrderSummary =
            NationalActionPointRules.summarizePlayer(gameState);
        return Sui.i18n.manager().getText(
            "literal", "national_order_preview_format", "",
            formatAverage(publicOrderSummary.averagePublicOrderTenths()),
            publicOrderSummary.cityCount(),
            NationalActionPointRules.calculateMonthlyActionPoints(gameState)
        );
    }

    public static String formatAverage(int averageTenths) {
        return averageTenths / 10 + "." + averageTenths % 10;
    }
}
