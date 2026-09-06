package idv.kuan.studio.sango.ui;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;

import idv.kuan.studio.libgdx.simpleui.Sui;
import idv.kuan.studio.libgdx.simpleui.SuiScreen;
import idv.kuan.studio.libgdx.simpleui.builder.BuiltUI;
import idv.kuan.studio.sango.application.result.TurnResolutionReport;
import idv.kuan.studio.sango.audio.MusicTrack;
import idv.kuan.studio.sango.audio.SoundEffect;
import idv.kuan.studio.sango.domain.model.BattleReport;
import idv.kuan.studio.sango.domain.model.CityState;
import idv.kuan.studio.sango.domain.model.GameState;
import idv.kuan.studio.sango.runtime.SangoServices;
import idv.kuan.studio.sango.ui.id.ScreenId;
import idv.kuan.studio.sango.ui.support.ScreenBackground;
import idv.kuan.studio.sango.ui.support.TurnReportTextFormatter;
import idv.kuan.studio.sango.ui.theme.SangoUiStyles;

/**
 * 顯示月底結算事件；戰鬥則由獨立戰報畫面呈現。
 */
public final class MonthReportScreen extends SuiScreen {
    private static final String BACKGROUND_PATH = "picture/lobby/sango_lobby_background.png";

    private final ScreenBackground screenBackground = new ScreenBackground();
    private final TurnReportTextFormatter reportFormatter = new TurnReportTextFormatter();

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
        SangoUiStyles.applySecondaryButton(button("month_report_close_button"));
        SangoUiStyles.applyPrimaryButton(button("month_report_battle_button"));
        ui.onClick("month_report_close_button", this::closeReport);
        ui.onClick("month_report_battle_button", this::openFirstBattleReport);
        Actor main = ui.getActor("main");
        main.getColor().a = 0f;
        main.addAction(Actions.fadeIn(0.22f));
    }

    @Override
    protected void afterShow() {
        SangoServices.audio().playMusic(MusicTrack.STRATEGY);
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
        label("month_report_content_label").setText(reportFormatter.format(report));
        int battleCount = report.getBattleReportIds().size();
        button("month_report_battle_button").setText(
            text("button_view_battles_format", "查看戰報（{0}）", battleCount)
        );
        setButtonEnabled(button("month_report_battle_button"), battleCount > 0);
        SangoServices.audio().playSound(SoundEffect.END_MONTH);
        if (battleCount > 0) {
            SangoServices.audio().playSound(SoundEffect.BATTLE_ALERT);
        }
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
    }

    @Override
    protected void beforeDispose() {
        screenBackground.remove();
        super.beforeDispose();
    }

    private void openFirstBattleReport() {
        TurnResolutionReport report = SangoServices.session().getLastTurnReport();
        if (report == null || report.getBattleReportIds().isEmpty()) {
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
        for (String battleReportId : report.getBattleReportIds()) {
            BattleReport battleReport = gameState.findBattleReport(battleReportId);
            if (battleReport != null && !battleReport.read) {
                return battleReportId;
            }
        }
        for (String battleReportId : report.getBattleReportIds()) {
            if (gameState.findBattleReport(battleReportId) != null) {
                return battleReportId;
            }
        }
        return null;
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
