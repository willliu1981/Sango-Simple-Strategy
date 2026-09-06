package idv.kuan.studio.sango.ui;

import java.text.NumberFormat;
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

import idv.kuan.studio.libgdx.simpleui.Sui;
import idv.kuan.studio.libgdx.simpleui.SuiScreen;
import idv.kuan.studio.libgdx.simpleui.builder.BuiltUI;
import idv.kuan.studio.sango.application.result.DomesticActionResult;
import idv.kuan.studio.sango.application.result.TurnResolutionReport;
import idv.kuan.studio.sango.application.result.TurnResolutionResult;
import idv.kuan.studio.sango.audio.MusicTrack;
import idv.kuan.studio.sango.audio.SoundEffect;
import idv.kuan.studio.sango.data.SangoPreferences;
import idv.kuan.studio.sango.domain.definition.CityDefinition;
import idv.kuan.studio.sango.domain.definition.FactionDefinition;
import idv.kuan.studio.sango.domain.definition.ScenarioDefinition;
import idv.kuan.studio.sango.domain.model.BattleReport;
import idv.kuan.studio.sango.domain.model.CityState;
import idv.kuan.studio.sango.domain.model.FactionState;
import idv.kuan.studio.sango.domain.model.GameState;
import idv.kuan.studio.sango.domain.model.GameplayStatus;
import idv.kuan.studio.sango.domain.rule.DomesticActionFailureReason;
import idv.kuan.studio.sango.domain.rule.DomesticActionRules;
import idv.kuan.studio.sango.domain.rule.DomesticActionType;
import idv.kuan.studio.sango.domain.rule.SeasonalEconomyRules;
import idv.kuan.studio.sango.repository.save.SaveGameException;
import idv.kuan.studio.sango.runtime.SangoServices;
import idv.kuan.studio.sango.ui.flow.MonthEndFlowController;
import idv.kuan.studio.sango.ui.id.ScreenId;
import idv.kuan.studio.sango.ui.support.ScreenBackground;
import idv.kuan.studio.sango.ui.theme.SangoUiStyles;

/**
 * 玩家所選城池的內政投資與軍事整備畫面。
 */
public final class CityScreen extends SuiScreen {
    private static final String BACKGROUND_PATH = "picture/lobby/sango_lobby_background.png";
    private static final Color STATUS_NORMAL_COLOR = new Color(0.79f, 0.72f, 0.61f, 1f);
    private static final Color STATUS_SUCCESS_COLOR = new Color(0.94f, 0.76f, 0.38f, 1f);
    private static final Color STATUS_ERROR_COLOR = new Color(0.95f, 0.43f, 0.30f, 1f);

    private final ScreenBackground screenBackground = new ScreenBackground();
    private final NumberFormat numberFormat = NumberFormat.getIntegerInstance(Locale.TAIWAN);
    private final MonthEndFlowController monthEndFlowController = new MonthEndFlowController();

    private Actor endMonthConfirmMask;
    private Actor battlePromptMask;
    private String currentStatusMessage;
    private Color currentStatusColor = STATUS_NORMAL_COLOR;

    @Override
    protected BuiltUI buildUI(UIFactory uiFactory) {
        return uiFactory.begin()
            .skipToRegisterTemplate()
            .skipToRegisterXml()
            .registerXml("ui/city.xml")
            .skipToBuild()
            .buildUI();
    }

    @Override
    protected void onUIBuilt(BuiltUI builtUI) {
        screenBackground.attach(stage, BACKGROUND_PATH);
        endMonthConfirmMask = attachModalMask("city_end_month_mask");
        battlePromptMask = attachModalMask("city_battle_prompt_mask");
        applyStyles();
        bindActions();
        currentStatusMessage = text(
            "city_status_ready",
            "內政投資不會立即產生金糧；收益會在季末或秋收結算。"
        );
        animateEntrance();
    }

    @Override
    protected void afterShow() {
        if (ui == null) {
            return;
        }
        closeModals();
        SangoServices.audio().playMusic(MusicTrack.STRATEGY);
        currentStatusMessage = text(
            "city_status_ready",
            "內政投資不會立即產生金糧；收益會在季末或秋收結算。"
        );
        currentStatusColor = STATUS_NORMAL_COLOR;
        ensureCurrentGameState();
        ensureSelectedCity();
        refreshView();
    }

    @Override
    protected InputProcessor createInputProcessor(Stage stage) {
        InputAdapter navigationInput = new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                if (keycode == Input.Keys.BACK || keycode == Input.Keys.ESCAPE) {
                    if (isAnyModalVisible()) {
                        closeModals();
                    } else {
                        returnToMap();
                    }
                    return true;
                }
                return false;
            }
        };
        return new InputMultiplexer(navigationInput, stage);
    }

    @Override
    public void pause() {
        saveSilently();
    }

    @Override
    protected void afterResize(int width, int height) {
        screenBackground.resize(stage);
    }

    @Override
    protected void beforeDispose() {
        saveSilently();
        screenBackground.remove();
        super.beforeDispose();
    }

    private Actor attachModalMask(String actorId) {
        Actor mask = ui.getActor(actorId);
        if (mask.getStage() == null) {
            stage.addActor(mask);
        }
        mask.setVisible(false);
        return mask;
    }

    private void applyStyles() {
        SangoUiStyles.applySecondaryButton(button("agriculture_button"));
        SangoUiStyles.applySecondaryButton(button("commerce_button"));
        SangoUiStyles.applySecondaryButton(button("water_control_button"));
        SangoUiStyles.applySecondaryButton(button("fortify_button"));
        SangoUiStyles.applySecondaryButton(button("recruit_button"));
        SangoUiStyles.applySecondaryButton(button("train_button"));
        SangoUiStyles.applySecondaryButton(button("return_map_button"));
        SangoUiStyles.applySecondaryButton(button("city_settings_button"));
        SangoUiStyles.applyPrimaryButton(button("city_end_month_button"));
        SangoUiStyles.applySecondaryButton(button("city_end_month_cancel_button"));
        SangoUiStyles.applyPrimaryButton(button("city_end_month_confirm_button"));
        SangoUiStyles.applySecondaryButton(button("city_battle_prompt_later_button"));
        SangoUiStyles.applyDangerButton(button("city_battle_prompt_view_button"));
    }

    private void bindActions() {
        ui.onClick(
            "agriculture_button",
            () -> executeDomesticAction(DomesticActionType.DEVELOP_AGRICULTURE)
        );
        ui.onClick(
            "commerce_button",
            () -> executeDomesticAction(DomesticActionType.DEVELOP_COMMERCE)
        );
        ui.onClick(
            "water_control_button",
            () -> executeDomesticAction(DomesticActionType.IMPROVE_WATER_CONTROL)
        );
        ui.onClick("fortify_button", () -> executeDomesticAction(DomesticActionType.FORTIFY));
        ui.onClick("recruit_button", () -> executeDomesticAction(DomesticActionType.RECRUIT));
        ui.onClick("train_button", () -> executeDomesticAction(DomesticActionType.TRAIN));
        ui.onClick("return_map_button", this::returnToMap);
        ui.onClick("city_settings_button", this::openSettings);
        ui.onClick("city_end_month_button", this::requestEndMonth);
        ui.onClick("city_end_month_cancel_button", this::closeModals);
        ui.onClick("city_end_month_confirm_button", this::confirmEndMonth);
        ui.onClick("city_battle_prompt_later_button", this::openMonthReportAfterBattlePrompt);
        ui.onClick("city_battle_prompt_view_button", this::openPromptedBattleReport);
    }

    private void animateEntrance() {
        Actor main = ui.getActor("main");
        main.getColor().a = 0f;
        main.addAction(Actions.fadeIn(0.24f));
    }

    private void ensureCurrentGameState() {
        if (SangoServices.session().hasCurrentState()) {
            return;
        }
        int slotNumber = SangoPreferences.getLastUsedSaveSlot();
        try {
            GameState gameState = SangoServices.saveGames().load(slotNumber);
            SangoServices.session().setCurrentState(slotNumber, gameState);
        } catch (RuntimeException exception) {
            Gdx.app.error("City", "無法載入目前戰局。", exception);
            currentStatusMessage = text(
                "city_status_load_failed",
                "無法載入戰局；請返回 Lobby 並選擇存檔。"
            );
            currentStatusColor = STATUS_ERROR_COLOR;
        }
    }

    private void ensureSelectedCity() {
        if (!SangoServices.session().hasCurrentState()) {
            return;
        }
        GameState gameState = SangoServices.session().requireCurrentState();
        String selectedCityId = SangoServices.session().getSelectedCityId();
        CityState selectedCityState = selectedCityId == null
            ? null
            : gameState.findCityState(selectedCityId);
        if (selectedCityState != null
            && gameState.playerFactionId.equals(selectedCityState.ownerFactionId)) {
            return;
        }
        FactionState playerFactionState = gameState.requirePlayerFactionState();
        if (playerFactionState.active
            && playerFactionState.capitalCityId != null
            && gameState.findCityState(playerFactionState.capitalCityId) != null) {
            SangoServices.session().setSelectedCityId(playerFactionState.capitalCityId);
        }
    }

    private void executeDomesticAction(DomesticActionType actionType) {
        if (!SangoServices.session().hasCurrentState()) {
            showNoGameStateError();
            return;
        }

        try {
            int slotNumber = SangoServices.session().getCurrentSaveSlot();
            DomesticActionResult result = SangoServices.domesticActionCommand().execute(
                slotNumber,
                SangoServices.session().requireCurrentState(),
                SangoServices.session().getSelectedCityId(),
                actionType
            );
            if (result.isSuccessful()) {
                SangoServices.session().setCurrentState(slotNumber, result.getGameState());
                currentStatusMessage = successMessage(result.getActionType());
                currentStatusColor = STATUS_SUCCESS_COLOR;
                SangoServices.audio().playSound(SoundEffect.COMMAND_SUCCESS);
            } else {
                currentStatusMessage = failureMessage(result.getFailureReason());
                currentStatusColor = STATUS_ERROR_COLOR;
                SangoServices.audio().playSound(SoundEffect.COMMAND_ERROR);
            }
        } catch (SaveGameException exception) {
            Gdx.app.error("City", "內政命令完成前存檔失敗。", exception);
            currentStatusMessage = text(
                "city_status_save_failed",
                "存檔失敗，因此本次命令沒有套用。"
            );
            currentStatusColor = STATUS_ERROR_COLOR;
            SangoServices.audio().playSound(SoundEffect.COMMAND_ERROR);
        } catch (RuntimeException exception) {
            Gdx.app.error("City", "執行內政命令失敗。", exception);
            currentStatusMessage = text(
                "city_status_action_failed",
                "內政命令執行失敗，戰局狀態未更新。"
            );
            currentStatusColor = STATUS_ERROR_COLOR;
            SangoServices.audio().playSound(SoundEffect.COMMAND_ERROR);
        }
        refreshView();
    }

    private void requestEndMonth() {
        if (!SangoServices.session().hasCurrentState()) {
            showNoGameStateError();
            return;
        }
        GameState gameState = SangoServices.session().requireCurrentState();
        if (gameState.gameplayStatus != GameplayStatus.ACTIVE) {
            setStatus(
                text("city_status_player_eliminated", "我方已失去全部城池，無法繼續推進月份。"),
                STATUS_ERROR_COLOR
            );
            return;
        }
        label("city_end_month_title_label").setText(
            text(
                "end_month_confirm_title_format",
                "確定結束 {0} 年 {1} 月？",
                gameState.currentYear,
                gameState.currentMonth
            )
        );
        label("city_end_month_description_label").setText(
            text(
                "end_month_confirm_description_format",
                "剩餘行動力：{0} / {1}\n月底將結算軍糧、季節收入、行軍、戰鬥與敵軍行動。未使用的行動力不會保留。",
                gameState.actionPointsRemaining,
                gameState.actionPointsPerTurn
            )
        );
        openModal(endMonthConfirmMask);
    }

    private void confirmEndMonth() {
        closeModals();
        String managedCityId = SangoServices.session().getSelectedCityId();
        try {
            TurnResolutionResult resolutionResult = monthEndFlowController.endCurrentMonth();
            TurnResolutionReport report = resolutionResult.getReport();
            GameState nextState = resolutionResult.getGameState();
            ScreenId reportReturnScreen = nextState.ownsCity(nextState.playerFactionId, managedCityId)
                ? ScreenId.CITY
                : ScreenId.STRATEGIC_MAP;
            SangoServices.session().openMonthReport(report, reportReturnScreen);
            SangoServices.audio().playSound(SoundEffect.END_MONTH);
            String battleReportId = findBattleAtCity(report, managedCityId);
            ensureSelectedCity();
            refreshView();
            if (battleReportId != null) {
                prepareBattlePrompt(battleReportId);
            } else {
                Sui.screens.set(ScreenId.MONTH_REPORT);
            }
        } catch (RuntimeException exception) {
            Gdx.app.error("City", "結束月份失敗。", exception);
            setStatus(
                text("city_status_save_failed", "存檔失敗，因此月份沒有推進。"),
                STATUS_ERROR_COLOR
            );
            SangoServices.audio().playSound(SoundEffect.COMMAND_ERROR);
            refreshView();
        }
    }

    private String findBattleAtCity(TurnResolutionReport report, String cityId) {
        GameState gameState = SangoServices.session().requireCurrentState();
        for (String battleReportId : report.getBattleReportIds()) {
            BattleReport battleReport = gameState.findBattleReport(battleReportId);
            if (battleReport != null && cityId.equals(battleReport.targetCityId)) {
                return battleReportId;
            }
        }
        return null;
    }

    private void prepareBattlePrompt(String battleReportId) {
        BattleReport battleReport = SangoServices.session().requireCurrentState()
            .requireBattleReport(battleReportId);
        SangoServices.session().openBattleReport(battleReportId, ScreenId.MONTH_REPORT);
        label("city_battle_prompt_title_label").setText(
            text("battle_prompt_title_format", "{0} 發生戰鬥", cityName(battleReport.targetCityId))
        );
        label("city_battle_prompt_description_label").setText(
            text(
                "battle_prompt_description",
                "本月正在管理的城池發生戰事。是否立即觀看完整戰報？"
            )
        );
        openModal(battlePromptMask);
        SangoServices.audio().playSound(SoundEffect.BATTLE_ALERT);
    }

    private void openMonthReportAfterBattlePrompt() {
        closeModals();
        Sui.screens.set(ScreenId.MONTH_REPORT);
    }

    private void openPromptedBattleReport() {
        closeModals();
        Sui.screens.set(ScreenId.BATTLE_REPORT);
    }

    private void returnToMap() {
        closeModals();
        saveSilently();
        SangoServices.audio().playSound(SoundEffect.UI_CLICK);
        Sui.screens.set(ScreenId.STRATEGIC_MAP);
    }

    private void openSettings() {
        closeModals();
        SangoServices.audio().playSound(SoundEffect.UI_CLICK);
        SangoServices.session().openSettings(ScreenId.CITY);
        Sui.screens.set(ScreenId.SETTINGS);
    }

    private void saveSilently() {
        if (!SangoServices.session().hasCurrentState()) {
            return;
        }
        try {
            SangoServices.saveCurrentGameCommand().execute(
                SangoServices.session().getCurrentSaveSlot(),
                SangoServices.session().requireCurrentState()
            );
        } catch (RuntimeException exception) {
            if (Gdx.app != null) {
                Gdx.app.error("City", "背景保存戰局失敗。", exception);
            }
        }
    }

    private void refreshView() {
        if (!SangoServices.session().hasCurrentState()) {
            setActionButtonsEnabled(false);
            setButtonEnabled(button("city_end_month_button"), false);
            refreshStatusLabel();
            return;
        }
        GameState gameState = SangoServices.session().requireCurrentState();
        CityState cityState = gameState.requireCityState(
            SangoServices.session().getSelectedCityId()
        );
        FactionState factionState = gameState.requirePlayerFactionState();
        ScenarioDefinition scenarioDefinition = SangoServices.definitions().requireScenario(
            gameState.scenarioId
        );
        FactionDefinition factionDefinition = SangoServices.definitions().requireFaction(
            gameState.playerFactionId
        );
        CityDefinition cityDefinition = SangoServices.definitions().requireCity(cityState.cityId);

        label("scenario_label").setText(
            text("city_scenario_prefix", "劇本：")
                + localized(scenarioDefinition.nameKey, scenarioDefinition.id)
        );
        label("date_label").setText(
            gameState.currentYear + text("city_year_suffix", " 年 ")
                + gameState.currentMonth + text("city_month_suffix", " 月")
        );
        label("turn_label").setText(
            text("city_turn_prefix", "回合：第 ") + gameState.currentTurn
                + text("city_turn_suffix", " 回合")
        );
        label("action_points_label").setText(
            text("city_action_points_prefix", "行動力：")
                + gameState.actionPointsRemaining + " / " + gameState.actionPointsPerTurn
        );
        label("city_name_heading_label").setText(
            localized(cityDefinition.nameKey, cityDefinition.id)
                + text("city_management_suffix", "內政")
        );
        label("faction_label").setText(
            text("city_faction_prefix", "勢力：")
                + localized(factionDefinition.nameKey, factionDefinition.id)
        );
        label("ruler_label").setText(
            text("city_ruler_prefix", "君主：")
                + localized(factionDefinition.rulerNameKey, factionDefinition.id)
        );

        label("gold_value_label").setText(numberFormat.format(factionState.gold));
        label("food_value_label").setText(numberFormat.format(factionState.food));
        label("population_value_label").setText(numberFormat.format(cityState.population));
        label("troops_value_label").setText(numberFormat.format(cityState.troops));
        label("agriculture_value_label").setText(cityState.agriculture + " / 100");
        label("commerce_value_label").setText(cityState.commerce + " / 100");
        label("water_control_value_label").setText(cityState.waterControl + " / 100");
        label("defense_value_label").setText(cityState.defense + " / 100");
        label("public_order_value_label").setText(cityState.publicOrder + " / 100");
        label("training_value_label").setText(cityState.training + " / 100");
        label("tax_estimate_value_label").setText(
            numberFormat.format(SeasonalEconomyRules.calculateQuarterlyTax(cityState))
        );
        label("harvest_estimate_value_label").setText(
            numberFormat.format(SeasonalEconomyRules.calculateEstimatedHarvest(cityState))
        );
        label("season_forecast_label").setText(buildSeasonForecast(gameState, cityState));

        boolean cityOwned = gameState.playerFactionId.equals(cityState.ownerFactionId);
        if (cityOwned && gameState.gameplayStatus == GameplayStatus.ACTIVE) {
            refreshActionButtonState(gameState, cityState.cityId);
        } else {
            setActionButtonsEnabled(false);
        }
        setButtonEnabled(
            button("city_end_month_button"),
            gameState.gameplayStatus == GameplayStatus.ACTIVE
        );
        refreshStatusLabel();
    }

    private String buildSeasonForecast(GameState gameState, CityState cityState) {
        int monthsUntilQuarter = 3 - (gameState.currentMonth - 1) % 3;
        int monthsUntilHarvest = SeasonalEconomyRules.HARVEST_MONTH - gameState.currentMonth + 1;
        if (monthsUntilHarvest <= 0) {
            monthsUntilHarvest += 12;
        }
        int floodRisk = SeasonalEconomyRules.calculateFloodRiskPercent(cityState);
        return text(
            "city_season_forecast_format",
            "距季末商稅 {0} 個月｜距秋收 {1} 個月｜夏季洪災風險 {2}%｜每月軍糧依總兵力結算",
            monthsUntilQuarter,
            monthsUntilHarvest,
            floodRisk
        );
    }

    private void refreshActionButtonState(GameState gameState, String cityId) {
        setActionButtonEnabled(
            "agriculture_button",
            gameState,
            cityId,
            DomesticActionType.DEVELOP_AGRICULTURE
        );
        setActionButtonEnabled(
            "commerce_button",
            gameState,
            cityId,
            DomesticActionType.DEVELOP_COMMERCE
        );
        setActionButtonEnabled(
            "water_control_button",
            gameState,
            cityId,
            DomesticActionType.IMPROVE_WATER_CONTROL
        );
        setActionButtonEnabled(
            "fortify_button",
            gameState,
            cityId,
            DomesticActionType.FORTIFY
        );
        setActionButtonEnabled(
            "recruit_button",
            gameState,
            cityId,
            DomesticActionType.RECRUIT
        );
        setActionButtonEnabled(
            "train_button",
            gameState,
            cityId,
            DomesticActionType.TRAIN
        );
    }

    private void setActionButtonEnabled(
        String actorId,
        GameState gameState,
        String cityId,
        DomesticActionType actionType
    ) {
        setButtonEnabled(
            button(actorId),
            DomesticActionRules.evaluate(gameState, cityId, actionType)
                == DomesticActionFailureReason.NONE
        );
    }

    private void setActionButtonsEnabled(boolean enabled) {
        setButtonEnabled(button("agriculture_button"), enabled);
        setButtonEnabled(button("commerce_button"), enabled);
        setButtonEnabled(button("water_control_button"), enabled);
        setButtonEnabled(button("fortify_button"), enabled);
        setButtonEnabled(button("recruit_button"), enabled);
        setButtonEnabled(button("train_button"), enabled);
    }

    private String successMessage(DomesticActionType actionType) {
        return switch (actionType) {
            case DEVELOP_AGRICULTURE -> text(
                "city_status_agriculture_success",
                "完成開墾投資：農業 +5。糧食將於秋收結算。"
            );
            case DEVELOP_COMMERCE -> text(
                "city_status_commerce_success",
                "完成商業投資：商業 +5。金錢將於季末結算。"
            );
            case IMPROVE_WATER_CONTROL -> text(
                "city_status_water_control_success",
                "完成治水：治水 +5，洪災風險與損失下降。"
            );
            case FORTIFY -> text(
                "city_status_fortify_success",
                "完成城防修築：城防 +5。"
            );
            case RECRUIT -> text(
                "city_status_recruit_success",
                "完成徵兵：兵力 +200、人口 -200，並扣除金 100、糧 100。"
            );
            case TRAIN -> text(
                "city_status_train_success",
                "完成訓練：訓練 +5，並扣除金 50。"
            );
        };
    }

    private String failureMessage(DomesticActionFailureReason failureReason) {
        return switch (failureReason) {
            case PLAYER_ELIMINATED -> text(
                "city_status_player_eliminated",
                "我方已失去全部城池，不能再下達內政命令。"
            );
            case CITY_NOT_OWNED -> text(
                "city_status_not_owned",
                "此城不屬於我方。"
            );
            case NO_ACTION_POINTS -> text(
                "city_status_no_action_points",
                "行動力不足；可直接使用下方的「結束本月」。"
            );
            case INSUFFICIENT_GOLD -> text(
                "city_status_insufficient_gold",
                "金不足，無法執行此命令。"
            );
            case INSUFFICIENT_FOOD -> text(
                "city_status_insufficient_food",
                "糧不足，無法執行此命令。"
            );
            case INSUFFICIENT_POPULATION -> text(
                "city_status_insufficient_population",
                "人口不足，無法繼續徵兵。"
            );
            case VALUE_AT_MAXIMUM -> text(
                "city_status_value_maximum",
                "此項能力已達上限。"
            );
            case NONE -> text("city_status_action_failed", "內政命令未完成。");
        };
    }

    private void showNoGameStateError() {
        setStatus(text("city_status_load_failed", "目前沒有可用戰局。"), STATUS_ERROR_COLOR);
    }

    private void openModal(Actor mask) {
        closeModals();
        mask.setVisible(true);
        mask.getColor().a = 0f;
        mask.toFront();
        mask.addAction(Actions.fadeIn(0.16f));
    }

    private void closeModals() {
        setVisible(endMonthConfirmMask, false);
        setVisible(battlePromptMask, false);
    }

    private boolean isAnyModalVisible() {
        return isVisible(endMonthConfirmMask) || isVisible(battlePromptMask);
    }

    private boolean isVisible(Actor actor) {
        return actor != null && actor.isVisible();
    }

    private void setVisible(Actor actor, boolean visible) {
        if (actor != null) {
            actor.clearActions();
            actor.setVisible(visible);
            actor.getColor().a = 1f;
        }
    }

    private void setStatus(String message, Color color) {
        currentStatusMessage = message;
        currentStatusColor = color;
        refreshStatusLabel();
    }

    private void refreshStatusLabel() {
        Label statusLabel = label("city_status_label");
        statusLabel.setText(currentStatusMessage == null ? "" : currentStatusMessage);
        statusLabel.setColor(currentStatusColor);
    }

    private void setButtonEnabled(TextButton textButton, boolean enabled) {
        textButton.setDisabled(!enabled);
        textButton.setTouchable(enabled ? Touchable.enabled : Touchable.disabled);
    }

    private String cityName(String cityId) {
        CityDefinition cityDefinition = SangoServices.definitions().requireCity(cityId);
        return localized(cityDefinition.nameKey, cityDefinition.id);
    }

    private String localized(String entryName, String fallback) {
        return text(entryName, fallback);
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
