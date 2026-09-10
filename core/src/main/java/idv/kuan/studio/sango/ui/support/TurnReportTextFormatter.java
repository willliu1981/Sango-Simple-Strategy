package idv.kuan.studio.sango.ui.support;

import java.text.NumberFormat;
import java.util.Locale;

import idv.kuan.studio.libgdx.simpleui.Sui;
import idv.kuan.studio.sango.application.result.TurnEvent;
import idv.kuan.studio.sango.application.result.TurnEventType;
import idv.kuan.studio.sango.application.result.TurnResolutionReport;
import idv.kuan.studio.sango.domain.definition.CityDefinition;
import idv.kuan.studio.sango.domain.model.BattleReport;
import idv.kuan.studio.sango.domain.model.GameState;
import idv.kuan.studio.sango.runtime.SangoServices;

/**
 * 將 Domain 回合事件轉為目前語系可讀文字。
 */
public final class TurnReportTextFormatter {
    private final NumberFormat numberFormat = NumberFormat.getIntegerInstance(Locale.TAIWAN);

    public String format(TurnResolutionReport report) {
        if (report == null) {
            return text("report_empty", "本月沒有特殊事件。");
        }
        StringBuilder reportBuilder = new StringBuilder();
        GameState state = SangoServices.session().requireCurrentState();
        for (TurnEvent turnEvent : report.getEvents()) {
            if (!isPlayerEvent(turnEvent, state.playerFactionId)) continue;
            if (reportBuilder.length() > 0) {
                reportBuilder.append('\n');
            }
            reportBuilder.append("• ").append(formatEvent(turnEvent));
        }
        for (BattleReport battle : BattleReportCatalog.playerMonth(state, report)) {
            if (reportBuilder.length() > 0) reportBuilder.append('\n');
            boolean attacking = state.playerFactionId.equals(battle.attackerFactionId);
            if (battle.routeEncounter) {
                String result = battle.outcome == idv.kuan.studio.sango.domain.model.BattleOutcome.DRAW
                    ? text("report_player_encounter_draw", "平手，雙方返城")
                    : text(state.playerFactionId.equals(battle.winnerFactionId)
                        ? "report_player_encounter_win" : "report_player_encounter_loss",
                        state.playerFactionId.equals(battle.winnerFactionId)
                            ? "我方獲勝" : "我方戰敗");
                reportBuilder.append("• ").append(text("report_player_encounter_summary",
                    "{0}—{1} 道路接戰：{2}；我方戰損 {3}，戰後兵力 {4}。",
                    optionalCityName(battle.originCityId), optionalCityName(battle.targetCityId),
                    result,
                    numberFormat.format(attacking ? battle.attackerLosses : battle.defenderLosses),
                    numberFormat.format(attacking ? battle.attackerSurvivors : battle.defenderSurvivors)));
                continue;
            }
            reportBuilder.append("• ").append(text("report_player_battle_summary",
                "{0}：{1}；我方戰損 {2}，戰後兵力 {3}。可由下方查看詳細戰報。",
                optionalCityName(battle.targetCityId),
                text(state.playerFactionId.equals(battle.winnerFactionId)
                    ? "report_player_battle_win" : "report_player_battle_loss",
                    state.playerFactionId.equals(battle.winnerFactionId) ? "我方獲勝" : "我方戰敗"),
                numberFormat.format(attacking ? battle.attackerLosses : battle.defenderLosses),
                numberFormat.format(attacking ? battle.attackerSurvivors : battle.defenderSurvivors)));
        }
        return reportBuilder.length() == 0 ? text("report_empty", "本月沒有特殊事件。") : reportBuilder.toString();
    }

    private boolean isPlayerEvent(TurnEvent event, String playerFactionId) {
        return switch (event.getType()) {
            case BATTLE_ATTACKER_WON, BATTLE_DEFENDER_WON, CITY_OCCUPIED_UNOPPOSED,
                CITY_CAPTURED, AI_ACTIONS_USED, ENEMY_PREPARING, ENEMY_REINFORCING, ENEMY_MARCHING -> false;
            default -> event.getFactionId() == null || playerFactionId.equals(event.getFactionId());
        };
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
            case FOOD_SHORTAGE_MORALE -> text(
                turnEvent.getOtherCityId() == null ? "report_shortage_city_morale" : "report_shortage_army_morale",
                turnEvent.getOtherCityId() == null
                    ? "{0} 駐軍缺糧，士氣 -{1}；目前士氣 {2}。"
                    : "{0} 前往 {3} 的部隊缺糧，士氣 -{1}；目前士氣 {2}。",
                optionalCityName(turnEvent.getCityId()), turnEvent.getPrimaryValue(),
                turnEvent.getSecondaryValue(), optionalCityName(turnEvent.getOtherCityId()));
            case PUBLIC_ORDER_NATURALLY_RECOVERED -> text(
                "report_event_public_order_recovered",
                "{0} 民心自然恢復 +{1}；目前民心 {2}。",
                optionalCityName(turnEvent.getCityId()),
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
            case ARMY_RETREAT_STARTED -> text("report_retreat_started",
                "{0} 戰敗後，{2} 名生還士兵撤往 {1}，行程 {3} 個月，下月開始移動。",
                optionalCityName(turnEvent.getCityId()), optionalCityName(turnEvent.getOtherCityId()),
                numberFormat.format(turnEvent.getPrimaryValue()), turnEvent.getSecondaryValue());
            case ARMY_RETREAT_ADVANCED -> text("report_retreat_advanced",
                "撤退部隊 {2} 兵由 {0} 前往 {1}，此段道路尚需 {3} 個月。",
                optionalCityName(turnEvent.getCityId()), optionalCityName(turnEvent.getOtherCityId()),
                numberFormat.format(turnEvent.getPrimaryValue()), turnEvent.getSecondaryValue());
            case ARMY_RETREAT_REROUTED -> text("report_retreat_rerouted",
                "原撤退路線通往 {0}，現已失效；{2} 兵改撤往 {1}，新行程 {3} 個月。",
                optionalCityName(turnEvent.getCityId()), optionalCityName(turnEvent.getOtherCityId()),
                numberFormat.format(turnEvent.getPrimaryValue()), turnEvent.getSecondaryValue());
            case ARMY_RETREAT_ARRIVED -> text("report_retreat_arrived",
                "由 {1} 戰場撤回的 {2} 兵已抵達 {0}，併入守軍。",
                optionalCityName(turnEvent.getCityId()), optionalCityName(turnEvent.getOtherCityId()),
                numberFormat.format(turnEvent.getPrimaryValue()));
            case ARMY_RETREAT_DISBANDED -> text("report_retreat_disbanded",
                "{0} 一帶部隊無可撤往的安全我方城池，{1} 兵潰散。",
                optionalCityName(turnEvent.getCityId()), numberFormat.format(turnEvent.getPrimaryValue()));
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
