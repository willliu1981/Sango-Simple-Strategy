package idv.kuan.studio.sango.ui;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.Dialog;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.utils.Align;

import idv.kuan.studio.libgdx.simpleui.Sui;
import idv.kuan.studio.libgdx.simpleui.SuiScreen;
import idv.kuan.studio.libgdx.simpleui.builder.BuiltUI;
import idv.kuan.studio.sango.ui.support.ScreenMusic;
import idv.kuan.studio.sango.audio.SoundEffect;
import idv.kuan.studio.sango.domain.definition.CityDefinition;
import idv.kuan.studio.sango.domain.definition.FactionDefinition;
import idv.kuan.studio.sango.domain.model.BattleReport;
import idv.kuan.studio.sango.domain.model.BattleContribution;
import idv.kuan.studio.sango.domain.model.BattleOutcome;
import idv.kuan.studio.sango.domain.model.GameState;
import idv.kuan.studio.sango.domain.rule.BattleTactic;
import idv.kuan.studio.sango.domain.rule.DefensePolicy;
import idv.kuan.studio.sango.domain.rule.MilitaryRules;
import idv.kuan.studio.sango.runtime.SangoServices;
import idv.kuan.studio.sango.ui.id.ScreenId;
import idv.kuan.studio.sango.ui.support.ScreenBackground;
import idv.kuan.studio.sango.ui.support.BattleReportCatalog;
import idv.kuan.studio.sango.ui.theme.SangoUiStyles;

/**
 * 將持久化 BattleReport 以獨立戰報畫面呈現。
 */
public final class BattleReportScreen extends SuiScreen {
    private static final String BACKGROUND_PATH = "picture/lobby/sango_lobby_background.png";
    private static final Color PLAYER_WIN_COLOR = new Color(0.95f, 0.76f, 0.32f, 1f);
    private static final Color PLAYER_LOSS_COLOR = new Color(0.92f, 0.34f, 0.24f, 1f);
    private static final Color DRAW_COLOR = new Color(0.78f, 0.74f, 0.66f, 1f);

    private final ScreenBackground screenBackground = new ScreenBackground();
    private final NumberFormat numberFormat = NumberFormat.getIntegerInstance(Locale.TAIWAN);
    private Dialog contributionDialog;

    @Override
    protected BuiltUI buildUI(UIFactory uiFactory) {
        return uiFactory.begin()
            .skipToRegisterTemplate()
            .skipToRegisterXml()
            .registerXml("ui/battle_report.xml")
            .skipToBuild()
            .buildUI();
    }

    @Override
    protected void onUIBuilt(BuiltUI builtUI) {
        screenBackground.attach(stage, BACKGROUND_PATH);
        SangoUiStyles.applySecondaryButton(button("battle_report_return_button"));
        SangoUiStyles.applyPrimaryButton(button("battle_report_next_button"));
        ui.onClick("battle_report_return_button", this::returnFromReport);
        ui.onClick("battle_report_next_button", this::openNextReport);
        SangoUiStyles.applySecondaryButton(button("battle_report_contributions_button"));
        ui.onClick("battle_report_contributions_button", this::showContributions);
        Actor main = ui.getActor("main");
        main.getColor().a = 0f;
        main.addAction(Actions.fadeIn(0.22f));
    }

    @Override
    protected void afterShow() {
        ScreenMusic.play(ScreenId.BATTLE_REPORT);
        if (!SangoServices.session().hasCurrentState()) {
            Sui.screens.set(ScreenId.LOBBY);
            return;
        }
        BattleReport battleReport = requireSelectedReport();
        refreshReport(battleReport);
        playReportSound(battleReport);
        markCurrentReportRead(battleReport);
        refreshNextButton(battleReport);
    }

    @Override
    protected InputProcessor createInputProcessor(Stage stage) {
        InputAdapter navigationInput = new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                if (keycode == Input.Keys.BACK || keycode == Input.Keys.ESCAPE) {
                    if (contributionDialog != null && contributionDialog.getStage() != null) {
                        contributionDialog.hide();
                        return true;
                    }
                    returnFromReport();
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

    private void refreshReport(BattleReport battleReport) {
        setButtonEnabled(button("battle_report_contributions_button"),
            !battleReport.routeEncounter && battleReport.attackerContributions != null
                && battleReport.attackerContributions.length > 0);
        GameState gameState = SangoServices.session().requireCurrentState();
        String targetCityName = cityName(battleReport.targetCityId);
        String attackerName = factionName(battleReport.attackerFactionId);
        String defenderName = factionName(battleReport.defenderFactionId);
        boolean playerWon = gameState.playerFactionId.equals(battleReport.winnerFactionId);

        label("battle_report_title_label").setText(
            battleReport.routeEncounter
                ? text("battle_report_encounter_title", "{0}—{1} 道路接戰",
                    cityName(battleReport.originCityId), targetCityName)
                : battleReport.outcome == BattleOutcome.UNOPPOSED_OCCUPATION
                ? text("battle_report_occupation_title", "", targetCityName)
                : text("battle_report_title_format", "{0}攻防戰", targetCityName)
        );
        label("battle_report_period_label").setText(
            text(
                "battle_report_period_format",
                "{0} 年 {1} 月｜第 {2} 回合",
                battleReport.resolvedYear,
                battleReport.resolvedMonth,
                battleReport.resolvedTurn
            )
        );
        label("battle_report_attacker_name_label").setText(attackerName);
        label("battle_report_defender_name_label").setText(defenderName);
        label("battle_report_route_label").setText(
            text(
                "battle_report_route_format",
                "{0} → {1}",
                cityName(battleReport.originCityId),
                targetCityName
            )
        );
        label("battle_report_tactic_label").setText(battleReport.routeEncounter
            ? text("battle_report_encounter_tactics", "{0}：{1}｜{2}：{3}",
                attackerName, tacticName(battleReport.attackerTactic,
                    battleReport.battleRulesVersion),
                defenderName, tacticName(battleReport.defenderTactic,
                    battleReport.battleRulesVersion))
            : text("battle_report_tactic_prefix", "攻方戰術：")
                + reportTacticName(battleReport)
                + text(
                    "battle_report_defense_format",
                    "｜守方城防：{0}｜防守方針：{1}",
                    battleReport.defenderDefense,
                    battleReport.defenderPolicyRecorded
                        ? defensePolicyName(battleReport.defenderPolicy, battleReport.battleRulesVersion)
                        : text("battle_report_defense_policy_legacy", "未記錄")
                ));

        label("attacker_before_value_label").setText(
            numberFormat.format(battleReport.attackerTroopsBefore)
        );
        label("defender_before_value_label").setText(
            numberFormat.format(battleReport.defenderTroopsBefore)
        );
        label("attacker_loss_value_label").setText(
            "-" + numberFormat.format(battleReport.attackerLosses)
        );
        label("defender_loss_value_label").setText(
            "-" + numberFormat.format(battleReport.defenderLosses)
        );
        label("attacker_after_value_label").setText(
            numberFormat.format(battleReport.attackerSurvivors)
        );
        label("defender_after_value_label").setText(
            numberFormat.format(battleReport.defenderSurvivors)
        );
        label("battle_report_training_label").setText(
            text(
                "battle_report_training_format",
                "攻方訓練 {0}｜守方訓練 {1}",
                battleReport.attackerTraining,
                battleReport.defenderTraining
            )
        );

        label("battle_report_morale_label").setText(
            battleReport.moraleRecorded
                ? text("battle_report_morale_format", "", battleReport.attackerMorale, battleReport.defenderMorale)
                : text("battle_report_morale_legacy", "")
        );
        Label resultLabel = label("battle_report_result_label");
        resultLabel.setText(buildResultText(gameState, battleReport, targetCityName));
        resultLabel.setColor(battleReport.outcome == BattleOutcome.DRAW ? DRAW_COLOR
            : playerWon || !battleReport.involvesFaction(gameState.playerFactionId)
                ? PLAYER_WIN_COLOR : PLAYER_LOSS_COLOR);
        label("battle_report_capture_label").setText(battleReport.routeEncounter
            ? encounterDisposition(battleReport)
            : battleReport.cityCaptured
                ? text(
                    "battle_report_capture_success",
                    "城池控制權已轉移至 {0}。",
                    factionName(battleReport.winnerFactionId)
                )
                : text("battle_report_capture_failed", "守方仍控制此城。"));
        refreshSequenceLabel(gameState, battleReport);
    }

    private String buildResultText(
        GameState gameState,
        BattleReport battleReport,
        String targetCityName
    ) {
        if (battleReport.routeEncounter) {
            if (battleReport.outcome == BattleOutcome.DRAW) {
                return text("battle_report_encounter_draw", "雙方戰力相當，脫離接戰並返城。");
            }
            return text("battle_report_encounter_result", "{0}贏得道路接戰。",
                factionName(battleReport.winnerFactionId));
        }
        if (battleReport.outcome == BattleOutcome.UNOPPOSED_OCCUPATION) {
            return text("battle_report_occupation_result", "", factionName(battleReport.winnerFactionId), targetCityName);
        }
        if (!battleReport.involvesFaction(gameState.playerFactionId)) {
            return text("battle_report_other_factions_result", "", factionName(battleReport.winnerFactionId), targetCityName);
        }
        boolean playerAttacker = gameState.playerFactionId.equals(battleReport.attackerFactionId);
        boolean playerWinner = gameState.playerFactionId.equals(battleReport.winnerFactionId);
        if (playerWinner && playerAttacker) {
            return text("battle_report_player_attack_win", "我軍攻下 {0}", targetCityName);
        }
        if (playerWinner) {
            return text("battle_report_player_defense_win", "我軍守住 {0}", targetCityName);
        }
        if (playerAttacker) {
            return text("battle_report_player_attack_loss", "我軍進攻 {0} 失敗", targetCityName);
        }
        return text("battle_report_player_defense_loss", "我軍失守 {0}", targetCityName);
    }

    private String encounterDisposition(BattleReport report) {
        if (report.outcome == BattleOutcome.DRAW) {
            return text("battle_report_encounter_both_return", "雙方均已返城。");
        }
        boolean attackerWon = report.outcome == BattleOutcome.ATTACKER_VICTORY;
        String winner = factionName(attackerWon ? report.attackerFactionId : report.defenderFactionId);
        boolean continued = attackerWon ? report.attackerContinued : report.defenderContinued;
        return text(continued ? "battle_report_encounter_continue" : "battle_report_encounter_return",
            continued ? "{0}擊退敵軍後繼續攻城。" : "{0}擊退敵軍後選擇返城。", winner);
    }

    private void refreshSequenceLabel(GameState gameState, BattleReport currentReport) {
        int currentIndex = 0;
        List<BattleReport> monthReports = findScopedReports(gameState);
        int reportCount = monthReports.isEmpty()
            ? (gameState.battleReports == null ? 0 : gameState.battleReports.length)
            : monthReports.size();
        if (!monthReports.isEmpty()) {
            for (int i = 0; i < monthReports.size(); i++) {
                if (currentReport.battleId.equals(monthReports.get(i).battleId)) {
                    currentIndex = i + 1;
                    break;
                }
            }
        } else if (gameState.battleReports != null) {
            for (int i = 0; i < gameState.battleReports.length; i++) {
                if (currentReport.battleId.equals(gameState.battleReports[i].battleId)) {
                    currentIndex = i + 1;
                    break;
                }
            }
        }
        label("battle_report_sequence_label").setText(
            text(
                "battle_report_sequence_format",
                "戰報 {0} / {1}",
                currentIndex,
                reportCount
            )
        );
    }

    private void markCurrentReportRead(BattleReport battleReport) {
        if (battleReport.read) {
            return;
        }
        try {
            int slotNumber = SangoServices.session().getCurrentSaveSlot();
            GameState updatedState = SangoServices.markBattleReportReadCommand().execute(
                slotNumber,
                SangoServices.session().requireCurrentState(),
                battleReport.battleId
            );
            SangoServices.session().setCurrentState(slotNumber, updatedState);
        } catch (RuntimeException exception) {
            Gdx.app.error("BattleReport", "標記戰報已讀時保存失敗。", exception);
        }
    }

    private void openNextReport() {
        GameState gameState = SangoServices.session().requireCurrentState();
        BattleReport nextReport = findNextReport(gameState, requireSelectedReport());
        if (nextReport == null) {
            return;
        }
        SangoServices.audio().playSound(SoundEffect.UI_CLICK);
        SangoServices.session().openBattleReport(
            nextReport.battleId,
            SangoServices.session().getBattleReportReturnScreen()
        );
        refreshReport(nextReport);
        playReportSound(nextReport);
        markCurrentReportRead(nextReport);
        refreshNextButton(nextReport);
    }

    private void playReportSound(BattleReport battleReport) {
        if (battleReport.outcome != BattleOutcome.UNOPPOSED_OCCUPATION) {
            SangoServices.audio().playSound(SoundEffect.BATTLE_IMPACT);
        }
        if (battleReport.cityCaptured) {
            SangoServices.audio().playSound(SoundEffect.CITY_CAPTURED);
        }
    }

    private void refreshNextButton(BattleReport currentReport) {
        ScreenId returnScreen = SangoServices.session().getBattleReportReturnScreen();
        boolean latestOnly = returnScreen == ScreenId.STRATEGIC_MAP || returnScreen == ScreenId.WORLD_BATTLE_REPORT;
        button("battle_report_next_button").setVisible(!latestOnly);
        GameState gameState = SangoServices.session().requireCurrentState();
        List<BattleReport> monthReports = findScopedReports(gameState);
        BattleReport nextReport = findNextReport(gameState, currentReport);
        if (!monthReports.isEmpty()) {
            int currentIndex = indexOfBattleReport(monthReports, currentReport.battleId);
            int remainingCount = currentIndex < 0
                ? 0
                : Math.max(0, monthReports.size() - currentIndex - 1);
            button("battle_report_next_button").setText(
                nextReport != null
                    ? text("button_next_scoped_battle_format", "下一份戰報（{0}）", remainingCount)
                    : text("button_no_next_scoped_battle", "已到最後一份戰報")
            );
        } else {
            int unreadCount = gameState.countUnreadBattleReports();
            button("battle_report_next_button").setText(
                nextReport != null
                    ? text("button_next_battle_format", "下一份未讀戰報（{0}）", unreadCount)
                    : text("button_no_unread_battle", "沒有其他未讀戰報")
            );
        }
        setButtonEnabled(button("battle_report_next_button"), nextReport != null);
    }

    private BattleReport findNextReport(GameState gameState, BattleReport currentReport) {
        List<BattleReport> monthReports = findScopedReports(gameState);
        if (!monthReports.isEmpty()) {
            int currentIndex = indexOfBattleReport(monthReports, currentReport.battleId);
            return currentIndex >= 0 && currentIndex + 1 < monthReports.size()
                ? monthReports.get(currentIndex + 1) : null;
        }
        List<BattleReport> unreadReports = gameState.findUnreadBattleReports();
        return unreadReports.isEmpty() ? null : unreadReports.get(0);
    }

    private int indexOfBattleReport(List<BattleReport> reports, String battleId) {
        for (int i = 0; i < reports.size(); i++) {
            if (battleId.equals(reports.get(i).battleId)) {
                return i;
            }
        }
        return -1;
    }

    private List<BattleReport> findScopedReports(GameState gameState) {
        if (SangoServices.session().getBattleReportReturnScreen() == ScreenId.WORLD_BATTLE_REPORT) {
            return BattleReportCatalog.world(gameState);
        }
        if (SangoServices.session().getBattleReportReturnScreen() == ScreenId.STRATEGIC_MAP) {
            BattleReport selectedReport = requireSelectedReport();
            BattleReport latest = BattleReportCatalog.latestForCity(
                gameState, selectedReport.targetCityId);
            return latest == null ? List.of() : List.of(latest);
        }
        if (SangoServices.session().getBattleReportReturnScreen() != ScreenId.MONTH_REPORT
            || SangoServices.session().getLastTurnReport() == null) {
            return List.of();
        }
        return BattleReportCatalog.playerMonth(gameState, SangoServices.session().getLastTurnReport());
    }

    private void showContributions() {
        BattleReport report = requireSelectedReport();
        if (report.attackerContributions == null || report.attackerContributions.length == 0) return;
        StringBuilder content = new StringBuilder();
        if (report.battleRulesVersion >= 2 && report.outcome != BattleOutcome.UNOPPOSED_OCCUPATION) {
            content.append(text("battle_matchup_strength",
                "相剋後戰力：攻方 {0}｜守方 {1}（守方加成 {2}%）",
                numberFormat.format(report.attackerStrength),
                numberFormat.format(report.defenderStrength), report.defenderMatchupPercent - 100));
        }
        for (BattleContribution contribution : report.attackerContributions) {
            if (content.length() > 0) content.append("\n\n");
            content.append(text("battle_contribution_row",
                "{0}｜{1}\n開戰 {2} 兵，戰損 {3}，生還 {4}\n訓練 {5}｜士氣 {6}",
                cityName(contribution.originCityId), factionName(contribution.factionId),
                numberFormat.format(contribution.troopsBefore), numberFormat.format(contribution.losses),
                numberFormat.format(contribution.survivors), contribution.training, contribution.morale)
                .replace("\\n", "\n"));
            if (report.battleRulesVersion >= 2 && contribution.attackerTactic != null) {
                content.append("\n").append(text("battle_contribution_tactic",
                    "戰術：{0}｜{1}",
                    tacticName(contribution.attackerTactic, report.battleRulesVersion),
                    contributionMatchupName(report, contribution)));
            }
        }
        Label body = new Label(content, label("battle_report_training_label").getStyle());
        body.setWrap(true);
        body.setAlignment(Align.topLeft);
        Window.WindowStyle style = new Window.WindowStyle();
        style.titleFont = body.getStyle().font;
        style.titleFontColor = PLAYER_WIN_COLOR;
        style.background = Sui.resources.manager().getSkin().newDrawable("white", new Color(0.06f, 0.045f, 0.03f, 1f));
        contributionDialog = new Dialog(text("battle_contributions_title", "各城參戰明細"), style);
        contributionDialog.setModal(true);
        contributionDialog.setMovable(false);
        ScrollPane pane = new ScrollPane(body);
        pane.setScrollingDisabled(true, false);
        pane.setOverscroll(false, false);
        contributionDialog.getContentTable().add(pane).width(1000f).height(570f).pad(24f);
        TextButton close = new TextButton(text("context_help_close", "返回"), button("battle_report_return_button").getStyle());
        contributionDialog.button(close);
        contributionDialog.getButtonTable().getCell(close).width(360f).height(64f).pad(12f);
        contributionDialog.show(stage);
        stage.setScrollFocus(pane);
    }

    private BattleReport requireSelectedReport() {
        String battleReportId = SangoServices.session().getSelectedBattleReportId();
        if (battleReportId == null) {
            List<BattleReport> unreadReports = SangoServices.session().requireCurrentState()
                .findUnreadBattleReports();
            if (unreadReports.isEmpty()) {
                throw new IllegalStateException("目前沒有可顯示的戰報。");
            }
            battleReportId = unreadReports.get(0).battleId;
            SangoServices.session().openBattleReport(
                battleReportId,
                SangoServices.session().getBattleReportReturnScreen()
            );
        }
        return SangoServices.session().requireCurrentState().requireBattleReport(battleReportId);
    }

    private void returnFromReport() {
        SangoServices.audio().playSound(SoundEffect.CANCEL);
        Sui.screens.set(SangoServices.session().getBattleReportReturnScreen());
    }

    private String cityName(String cityId) {
        CityDefinition cityDefinition = SangoServices.definitions().requireCity(cityId);
        return text(cityDefinition.nameKey, cityDefinition.id);
    }

    private String factionName(String factionId) {
        FactionDefinition factionDefinition = SangoServices.definitions().requireFaction(factionId);
        return text(factionDefinition.nameKey, factionDefinition.id);
    }

    private String reportTacticName(BattleReport report) {
        if (report.battleRulesVersion >= 2 && report.attackerContributions != null) {
            for (BattleContribution contribution : report.attackerContributions) {
                if (contribution.attackerTactic != report.attackerTactic) {
                    return text("battle_tactic_mixed", "混合戰術");
                }
            }
        }
        return tacticName(report.attackerTactic, report.battleRulesVersion);
    }

    private String contributionMatchupName(BattleReport report, BattleContribution contribution) {
        if (report.outcome == BattleOutcome.UNOPPOSED_OCCUPATION) {
            return text("battle_matchup_unopposed", "空城，不計相剋");
        }
        BattleTactic attack = contribution.attackerTactic;
        DefensePolicy defense = report.defenderPolicy;
        if (attack.name().equals(defense.name())) {
            return text("battle_matchup_equal", "同種，無加成");
        }
        boolean attackWins = MilitaryRules.attackerMatchupPercent(attack, defense) > 100;
        return attackWins ? text("battle_matchup_attack", "克制守方，戰力加成 10%")
            : text("battle_matchup_defense", "被守方克制");
    }

    private String tacticName(BattleTactic battleTactic, int rulesVersion) {
        return switch (battleTactic) {
            case BALANCED -> text("button_tactic_balanced", "穩健");
            case ASSAULT -> text("tactic_assault", "強攻");
            case FEINT -> text("tactic_feint", "誘敵");
            case HOLD -> rulesVersion >= 2 ? text("tactic_hold", "穩進")
                : text("battle_tactic_hold_legacy", "固守");
            case CAUTIOUS -> text("button_tactic_cautious", "保守");
        };
    }

    private String defensePolicyName(DefensePolicy policy, int rulesVersion) {
        return switch (policy) {
            case BALANCED -> text("defense_policy_balanced", "均衡防守");
            case AGGRESSIVE -> text("defense_policy_aggressive", "積極迎戰");
            case HOLD -> rulesVersion >= 2 ? text("defense_policy_hold", "據守")
                : text("defense_policy_hold_legacy", "固守城池");
            case ASSAULT -> rulesVersion >= 2 ? text("defense_policy_assault", "迎擊")
                : text("tactic_assault", "強攻");
            case FEINT -> rulesVersion >= 2 ? text("defense_policy_feint", "設伏")
                : text("tactic_feint", "誘敵");
        };
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
