package idv.kuan.studio.sango.ui.support;

import java.text.NumberFormat;
import java.util.Locale;

import idv.kuan.studio.libgdx.simpleui.Sui;
import idv.kuan.studio.sango.application.result.TurnEvent;
import idv.kuan.studio.sango.application.result.TurnEventType;
import idv.kuan.studio.sango.application.result.TurnResolutionReport;
import idv.kuan.studio.sango.domain.definition.CityDefinition;
import idv.kuan.studio.sango.runtime.SangoServices;

/**
 * 將 Domain 回合事件轉為目前語系可讀文字。
 */
public final class TurnReportTextFormatter {
    private final NumberFormat numberFormat = NumberFormat.getIntegerInstance(Locale.TAIWAN);

    public String format(TurnResolutionReport report) {
        if (report == null || report.isEmpty()) {
            return text("report_empty", "本月沒有特殊事件。");
        }
        StringBuilder reportBuilder = new StringBuilder();
        for (TurnEvent turnEvent : report.getEvents()) {
            if (reportBuilder.length() > 0) {
                reportBuilder.append('\n');
            }
            reportBuilder.append("• ").append(formatEvent(turnEvent));
        }
        return reportBuilder.toString();
    }

    private String formatEvent(TurnEvent turnEvent) {
        TurnEventType eventType = turnEvent.getType();
        return switch (eventType) {
            case ACTION_POINTS_REFRESHED -> text(
                "report_event_action_points", "",
                numberFormat.format(turnEvent.getSecondaryValue()),
                turnEvent.getPrimaryValue()
            );
            case POPULATION_CHANGED -> text("report_event_population", "{0} 年底人口變化 {1}；目前人口 {2}。",
                optionalCityName(turnEvent.getCityId()),
                (turnEvent.getPrimaryValue() > 0 ? "+" : "") + numberFormat.format(turnEvent.getPrimaryValue()),
                numberFormat.format(turnEvent.getSecondaryValue()));
            case AI_ACTIONS_USED -> text("report_event_ai_actions", "{0} 本月使用 {1}/{2} AP。",
                factionName(turnEvent.getFactionId()), turnEvent.getPrimaryValue(), turnEvent.getSecondaryValue());
            case MILITARY_UPKEEP -> text(
                "report_event_upkeep",
                "軍糧支出：-{0} 糧，用於維持 {1} 兵。",
                numberFormat.format(turnEvent.getPrimaryValue()),
                numberFormat.format(turnEvent.getSecondaryValue())
            );
            case FOOD_SHORTAGE -> text(
                "report_event_shortage",
                "軍糧不足 {0}，共有 {1} 兵逃散。",
                numberFormat.format(turnEvent.getPrimaryValue()),
                numberFormat.format(turnEvent.getSecondaryValue())
            );
            case QUARTERLY_TAX -> text(
                "report_event_tax",
                "季末商稅：+{0} 金（{1} 座城池）。",
                numberFormat.format(turnEvent.getPrimaryValue()),
                turnEvent.getSecondaryValue()
            );
            case FLOOD_OCCURRED -> text(
                "report_event_flood",
                "{0} 發生洪災；秋收預估減少 {1}%（事前風險 {2}%）。",
                optionalCityName(turnEvent.getCityId()),
                turnEvent.getSecondaryValue(),
                turnEvent.getPrimaryValue()
            );
            case FLOOD_AVOIDED -> text(
                "report_event_flood_avoided",
                "{0} 平安度過汛期（洪災風險 {1}%）。",
                optionalCityName(turnEvent.getCityId()),
                turnEvent.getPrimaryValue()
            );
            case HARVEST -> text(
                "report_event_harvest",
                "秋收：+{0} 糧（{1} 座城池）。",
                numberFormat.format(turnEvent.getPrimaryValue()),
                turnEvent.getSecondaryValue()
            );
            case ARMY_ADVANCED -> text(
                "report_event_army_advanced",
                "我軍 {0} 兵由 {1} 向 {2} 行軍，尚需 {3} 個月。",
                numberFormat.format(turnEvent.getPrimaryValue()),
                optionalCityName(turnEvent.getCityId()),
                optionalCityName(turnEvent.getOtherCityId()),
                turnEvent.getSecondaryValue()
            );
            case ARMY_REINFORCED -> text(
                "report_event_reinforced",
                "{0} 獲得 {1} 兵增援。",
                optionalCityName(turnEvent.getCityId()),
                numberFormat.format(turnEvent.getPrimaryValue())
            );
            case BATTLE_ATTACKER_WON -> text(
                "report_event_attack_winner_named", "", factionName(turnEvent.getFactionId()),
                optionalCityName(turnEvent.getCityId())
            );
            case BATTLE_DEFENDER_WON -> text(
                "report_event_defense_winner_named", "", factionName(turnEvent.getFactionId()),
                optionalCityName(turnEvent.getCityId())
            );
            case CITY_OCCUPIED_UNOPPOSED -> text(
                "report_event_unopposed", "", factionName(turnEvent.getFactionId()),
                optionalCityName(turnEvent.getCityId()), numberFormat.format(turnEvent.getPrimaryValue())
            );
            case CITY_CAPTURED -> text(
                "report_event_city_captured",
                "{0} 的控制權已轉移，現有駐軍 {1}。",
                optionalCityName(turnEvent.getCityId()),
                numberFormat.format(turnEvent.getPrimaryValue())
            );
            case CAPITAL_RELOCATED -> text(
                "report_event_capital_relocated",
                "首都已由 {1} 遷往 {0}；遊戲可繼續。",
                optionalCityName(turnEvent.getCityId()),
                optionalCityName(turnEvent.getOtherCityId())
            );
            case ENEMY_PREPARING -> text(
                "report_event_preparing_named", "", factionName(turnEvent.getFactionId()),
                optionalCityName(turnEvent.getCityId()), turnEvent.getPrimaryValue()
            );
            case ENEMY_REINFORCING -> text(
                "report_event_reinforcing_named", "", factionName(turnEvent.getFactionId()),
                optionalCityName(turnEvent.getCityId()), numberFormat.format(turnEvent.getPrimaryValue())
            );
            case ENEMY_MARCHING -> text(
                "report_event_marching_named", "", factionName(turnEvent.getFactionId()),
                numberFormat.format(turnEvent.getPrimaryValue()), optionalCityName(turnEvent.getCityId()),
                optionalCityName(turnEvent.getOtherCityId()), turnEvent.getSecondaryValue()
            );
            case CAMPAIGN_VICTORY -> text(
                "report_event_victory",
                "劇本目標達成：已攻下 {0}。戰局進入自由征戰模式。",
                optionalCityName(turnEvent.getCityId())
            );
            case CAMPAIGN_DEFEAT_CAPITAL -> text(
                "report_event_defeat_capital",
                "劇本目標失敗：原首都 {0} 已失守，但仍可由其他領地繼續。",
                optionalCityName(turnEvent.getCityId())
            );
            case CAMPAIGN_DEFEAT_TIMEOUT -> text(
                "report_event_defeat_timeout",
                "劇本目標失敗：未能在 {0} 個月內完成目標。戰局進入自由征戰模式。",
                turnEvent.getPrimaryValue()
            );
            case PLAYER_ELIMINATED -> text(
                "report_event_player_eliminated",
                "我方已失去全部城池，無法再下達命令；仍可查看局勢或讀取其他存檔。"
            );
        };
    }

    private String factionName(String factionId) {
        String nameKey = SangoServices.definitions().requireFaction(factionId).nameKey;
        return text(nameKey, factionId);
    }

    private String optionalCityName(String cityId) {
        if (cityId == null || cityId.isEmpty()) {
            return "";
        }
        CityDefinition cityDefinition = SangoServices.definitions().requireCity(cityId);
        return text(cityDefinition.nameKey, cityDefinition.id);
    }

    private String text(String entryName, String fallback, Object... arguments) {
        return Sui.i18n.manager().getText("literal", entryName, fallback, arguments);
    }
}
