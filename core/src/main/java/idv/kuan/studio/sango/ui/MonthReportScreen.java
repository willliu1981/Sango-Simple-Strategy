package idv.kuan.studio.sango.ui;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;

import idv.kuan.studio.libgdx.simpleui.Sui;
import idv.kuan.studio.libgdx.simpleui.SuiScreen;
import idv.kuan.studio.libgdx.simpleui.builder.BuiltUI;
import idv.kuan.studio.sango.application.result.TurnResolutionReport;
import idv.kuan.studio.sango.application.result.TurnEventType;
import idv.kuan.studio.sango.ui.support.ScreenMusic;
import idv.kuan.studio.sango.audio.SoundEffect;
import idv.kuan.studio.sango.domain.model.BattleReport;
import idv.kuan.studio.sango.domain.model.CityState;
import idv.kuan.studio.sango.domain.model.GameState;
import idv.kuan.studio.sango.runtime.SangoServices;
import idv.kuan.studio.sango.ui.id.ScreenId;
import idv.kuan.studio.sango.ui.support.ScreenBackground;
import idv.kuan.studio.sango.ui.support.TurnReportTextFormatter;
import idv.kuan.studio.sango.ui.support.BattleReportCatalog;
import idv.kuan.studio.sango.ui.theme.SangoUiStyles;

/**
 * 顯示月底結算事件；戰鬥則由獨立戰報畫面呈現。
 */
public final class MonthReportScreen extends SuiScreen {
    private static final String BACKGROUND_PATH = "picture/lobby/sango_lobby_background.png";

    private final ScreenBackground screenBackground = new ScreenBackground();
    private final TurnReportTextFormatter reportFormatter = new TurnReportTextFormatter();
    private Group reportScrollHost;
    private Table reportContent;
    private ScrollPane reportScrollPane;

    @Override
    protected BuiltUI buildUI(UIFactory uiFactory) {
        return uiFactory.begin()
            .skipToRegisterTemplate()
            .skipToRegisterXml()
            .registerXml("ui/month_report.xml")
            .skipToBuild()
            .buildUI();
    }

    @Override
    protected void onUIBuilt(BuiltUI builtUI) {
        screenBackground.attach(stage, BACKGROUND_PATH);
        reportScrollHost = ui.getActor("month_report_scroll_host", Group.class);
        Label reportLabel = label("month_report_content_label");
        reportLabel.remove();
        reportLabel.setAlignment(Align.topLeft);
        reportContent = new Table();
        reportContent.top().left();
        reportContent.add(reportLabel).top().left().growX();
        reportScrollPane = new ScrollPane(reportContent);
        reportScrollPane.setStyle(new ScrollPane.ScrollPaneStyle());
        reportScrollPane.setScrollingDisabled(true, false);
        reportScrollPane.setOverscroll(false, false);
        reportScrollPane.setFadeScrollBars(false);
        reportScrollHost.addActor(reportScrollPane);
        resizeReportScrollPane();
        SangoUiStyles.applySecondaryButton(button("month_report_close_button"));
        SangoUiStyles.applyPrimaryButton(button("month_report_battle_button"));
        SangoUiStyles.applySelectedButton(button("month_report_player_filter_button"));
        SangoUiStyles.applySecondaryButton(button("month_report_world_filter_button"));
        ui.onClick("month_report_close_button", this::closeReport);
        ui.onClick("month_report_battle_button", this::openFirstBattleReport);
        ui.onClick("month_report_player_filter_button", () -> setWorldView(false));
        ui.onClick("month_report_world_filter_button", () -> setWorldView(true));
        Actor main = ui.getActor("main");
        main.getColor().a = 0f;
        main.addAction(Actions.fadeIn(0.22f));
    }

    @Override
    protected void afterShow() {
        ScreenMusic.play(ScreenId.MONTH_REPORT);
        stage.setScrollFocus(reportScrollPane);
        TurnResolutionReport report = SangoServices.session().getLastTurnReport();
        if (report == null) {
            label("month_report_period_label").setText(
                text("report_period_placeholder", "結算月份尚未載入。")
            );
            label("month_report_content_label").setText(
                text("report_empty", "本月沒有特殊事件。")
            );
            setButtonEnabled(button("month_report_battle_button"), false);
            return;
        }

        label("month_report_period_label").setText(
            text(
                "report_period_format",
                "{0} 年 {1} 月結算",
                report.getResolvedYear(),
                report.getResolvedMonth()
            )
        );
        refreshReportContent(report);
        resizeReportScrollPane();
        reportScrollPane.setScrollY(0f);
        reportScrollPane.updateVisualScroll();
        int battleCount = currentBattleReports(report).size();
        SangoServices.audio().playSound(SoundEffect.END_MONTH);
        if (battleCount > 0) {
            SangoServices.audio().playSound(SoundEffect.BATTLE_ALERT);
        }
    }

    private void setWorldView(boolean worldView) {
        SangoServices.session().setMonthReportWorldView(worldView);
        refreshReportContent(SangoServices.session().getLastTurnReport());
        SangoServices.audio().playSound(SoundEffect.UI_CLICK);
    }

    private void refreshReportContent(TurnResolutionReport report) {
        boolean worldView = SangoServices.session().isMonthReportWorldView();
        SangoUiStyles.applySecondaryButton(button("month_report_player_filter_button"));
        SangoUiStyles.applySecondaryButton(button("month_report_world_filter_button"));
        SangoUiStyles.applySelectedButton(button(worldView
            ? "month_report_world_filter_button" : "month_report_player_filter_button"));
        label("month_report_content_label").setText(reportFormatter.format(report,
            worldView ? TurnReportTextFormatter.Scope.WORLD : TurnReportTextFormatter.Scope.PLAYER));
        label("month_report_tip_label").setText(monthlyTip(report));
        int battleCount = currentBattleReports(report).size();
        button("month_report_battle_button").setText(
            text("button_view_battles_format", "查看戰報（{0}）", battleCount));
        setButtonEnabled(button("month_report_battle_button"), battleCount > 0);
        resizeReportScrollPane();
        reportScrollPane.setScrollY(0f);
        reportScrollPane.updateVisualScroll();
    }

    private String monthlyTip(TurnResolutionReport report) {
        if (report != null && report.getEvents().stream()
            .anyMatch(event -> event.getType() == TurnEventType.FOOD_SHORTAGE)) {
            return text("monthly_tip_shortage", "小提示：出征與徵兵都會增加每月軍糧壓力，缺糧會造成逃兵並降低民心。");
        }
        if (report != null && report.getEvents().stream()
            .anyMatch(event -> event.getType() == TurnEventType.FLOOD_OCCURRED)) {
            return text("monthly_tip_flood", "小提示：六月前提升治水，可降低洪災發生率與城池損失。");
        }
        if (report != null && report.hasBattles()) {
            return text("monthly_tip_battle", "小提示：完成後的戰報會揭露方針；敵軍只能依自己過往參戰紀錄推測你的習慣。");
        }
        String key = report == null || report.getResolvedMonth() % 2 == 0
            ? "monthly_tip_snapshot" : "monthly_tip_order";
        return text(key, key.endsWith("snapshot")
            ? "小提示：其他勢力本月看到的是上月底快照，不會直接讀取你的當月命令。"
            : "小提示：民心總和達到下一門檻後，會在下個月增加行動力。 ");
    }

    private java.util.List<BattleReport> currentBattleReports(TurnResolutionReport report) {
        GameState state = SangoServices.session().requireCurrentState();
        return SangoServices.session().isMonthReportWorldView()
            ? BattleReportCatalog.month(state, report) : BattleReportCatalog.playerMonth(state, report);
    }

    @Override
    protected InputProcessor createInputProcessor(Stage stage) {
        InputAdapter navigationInput = new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                if (keycode == Input.Keys.BACK || keycode == Input.Keys.ESCAPE) {
                    closeReport();
                    return true;
                }
                return false;
            }
        };
        return new InputMultiplexer(navigationInput, stage);
    }

    @Override
    protected void afterResize(int width, int height) {
        screenBackground.resize(stage);
        resizeReportScrollPane();
    }

    @Override
    protected void beforeDispose() {
        screenBackground.remove();
        super.beforeDispose();
    }

    private void resizeReportScrollPane() {
        if (reportScrollHost == null || reportScrollPane == null) {
            return;
        }
        float contentWidth = Math.max(1f, reportScrollHost.getWidth() - 24f);
        reportScrollPane.setBounds(0f, 0f, reportScrollHost.getWidth(), reportScrollHost.getHeight());
        reportContent.getCell(label("month_report_content_label")).width(contentWidth);
        reportContent.invalidateHierarchy();
        reportScrollPane.validate();
    }

    private void openFirstBattleReport() {
        TurnResolutionReport report = SangoServices.session().getLastTurnReport();
        if (report == null) {
            return;
        }
        String battleReportId = findFirstExistingBattleReport(report);
        if (battleReportId == null) {
            return;
        }
        SangoServices.audio().playSound(SoundEffect.CONFIRM);
        SangoServices.session().openBattleReport(battleReportId, ScreenId.MONTH_REPORT);
        Sui.screens.set(ScreenId.BATTLE_REPORT);
    }

    private String findFirstExistingBattleReport(TurnResolutionReport report) {
        GameState gameState = SangoServices.session().requireCurrentState();
        var reports = currentBattleReports(report);
        return reports.isEmpty() ? null : reports.get(0).battleId;
    }

    private void closeReport() {
        SangoServices.audio().playSound(SoundEffect.CANCEL);
        ScreenId returnScreen = SangoServices.session().getMonthReportReturnScreen();
        if (returnScreen == ScreenId.CITY && !canReturnToSelectedCity()) {
            returnScreen = ScreenId.STRATEGIC_MAP;
        }
        Sui.screens.set(returnScreen);
    }

    private boolean canReturnToSelectedCity() {
        if (!SangoServices.session().hasCurrentState()) {
            return false;
        }
        GameState gameState = SangoServices.session().requireCurrentState();
        CityState cityState = gameState.findCityState(SangoServices.session().getSelectedCityId());
        return cityState != null && gameState.playerFactionId.equals(cityState.ownerFactionId);
    }

    private void setButtonEnabled(TextButton textButton, boolean enabled) {
        textButton.setDisabled(!enabled);
        textButton.setTouchable(enabled ? Touchable.enabled : Touchable.disabled);
    }

    private TextButton button(String actorId) {
        return ui.getActor(actorId, TextButton.class);
    }

    private Label label(String actorId) {
        return ui.getActor(actorId, Label.class);
    }

    private String text(String entryName, String fallback, Object... arguments) {
        return Sui.i18n.manager().getText("literal", entryName, fallback, arguments);
    }
}
