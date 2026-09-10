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
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;

import idv.kuan.studio.libgdx.simpleui.Sui;
import idv.kuan.studio.libgdx.simpleui.SuiScreen;
import idv.kuan.studio.libgdx.simpleui.builder.BuiltUI;
import idv.kuan.studio.sango.application.result.DomesticActionResult;
import idv.kuan.studio.sango.application.result.TurnResolutionReport;
import idv.kuan.studio.sango.application.result.TurnResolutionResult;
import idv.kuan.studio.sango.ui.support.ScreenMusic;
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
import idv.kuan.studio.sango.domain.rule.CampaignBalance;
import idv.kuan.studio.sango.domain.rule.OfficerCommandProfile;
import idv.kuan.studio.sango.domain.rule.PopulationRules;
import idv.kuan.studio.sango.domain.rule.PublicOrderNaturalRecoveryRules;
import idv.kuan.studio.sango.domain.rule.PublicOrderRules;
import idv.kuan.studio.sango.domain.rule.NationalActionPointRules;
import idv.kuan.studio.sango.domain.rule.RecruitmentRules;
import idv.kuan.studio.sango.domain.rule.TroopQualityRules;
import idv.kuan.studio.sango.ui.support.TroopQualityTextFormatter;
import idv.kuan.studio.sango.runtime.SangoServices;
import idv.kuan.studio.sango.ui.flow.MonthEndFlowController;
import idv.kuan.studio.sango.ui.id.ScreenId;
import idv.kuan.studio.sango.ui.support.ContextHelpOverlay;
import idv.kuan.studio.sango.ui.support.ScreenBackground;
import idv.kuan.studio.sango.ui.support.NationalOrderTextFormatter;
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
    private Actor recruitmentMask;
    private ContextHelpOverlay contextHelpOverlay;
    private Slider recruitmentSlider;
    private TextField recruitmentInput;
    private String recruitmentCityId;
    private boolean updatingRecruitmentControls;
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
        recruitmentMask = attachModalMask("recruitment_mask");
        configureOverviewScrollPane();
        initializeRecruitmentControls();
        applyStyles();
        button("city_context_help_button").setText(text("context_help_button", "操作說明"));
        button("recruitment_help_button").setText(text("help_recruit_title", "徵兵說明"));
        contextHelpOverlay = new ContextHelpOverlay(
            stage,
            label("city_name_heading_label").getStyle(),
            label("season_forecast_label").getStyle(),
            button("city_context_help_button").getStyle(),
            text("context_help_close", "關閉")
        );
        applyDomesticActionButtonTexts();
        bindActions();
        currentStatusMessage = text(
            "city_status_ready",
            "內政投資不會立即產生金糧；收益會在季末或秋收結算。"
        );
        animateEntrance();
    }

    private void configureOverviewScrollPane() {
        ScrollPane overview = ui.getActor("city_overview_scroll", ScrollPane.class);
        overview.setStyle(new ScrollPane.ScrollPaneStyle());
        overview.setScrollingDisabled(true, false);
        overview.setOverscroll(false, false);
        overview.setFadeScrollBars(false);
        overview.setScrollbarsVisible(false);
    }

    @Override
    protected void afterShow() {
        if (ui == null) {
            return;
        }
        closeModals();
        contextHelpOverlay.hide();
        currentStatusMessage = text(
            "city_status_ready",
            "內政投資不會立即產生金糧；收益會在季末或秋收結算。"
        );
        currentStatusColor = STATUS_NORMAL_COLOR;
        ensureCurrentGameState();
        ScreenMusic.play(ScreenId.CITY);
        ensureSelectedCity();
        refreshView();
    }

    @Override
    protected InputProcessor createInputProcessor(Stage stage) {
        InputAdapter navigationInput = new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                if (keycode == Input.Keys.BACK || keycode == Input.Keys.ESCAPE) {
                    if (contextHelpOverlay != null && contextHelpOverlay.isVisible()) {
                        contextHelpOverlay.hide();
                    } else if (isAnyModalVisible()) {
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
    protected void afterResize(int width, int height) {
        screenBackground.resize(stage);
    }

    @Override
    protected void beforeDispose() {
        screenBackground.remove();
        if (contextHelpOverlay != null) {
            contextHelpOverlay.remove();
        }
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
        SangoUiStyles.applySecondaryButton(button("recruitment_min_button"));
        SangoUiStyles.applySecondaryButton(button("recruitment_minus_button"));
        SangoUiStyles.applySecondaryButton(button("recruitment_plus_button"));
        SangoUiStyles.applySecondaryButton(button("recruitment_max_button"));
        SangoUiStyles.applySecondaryButton(button("recruitment_cancel_button"));
        SangoUiStyles.applyPrimaryButton(button("recruitment_confirm_button"));
        SangoUiStyles.applySecondaryButton(button("agriculture_button"));
        SangoUiStyles.applySecondaryButton(button("commerce_button"));
        SangoUiStyles.applySecondaryButton(button("water_control_button"));
        SangoUiStyles.applySecondaryButton(button("pacify_button"));
        SangoUiStyles.applySecondaryButton(button("fortify_button"));
        SangoUiStyles.applySecondaryButton(button("recruit_button"));
        SangoUiStyles.applySecondaryButton(button("train_button"));
        SangoUiStyles.applySecondaryButton(button("return_map_button"));
        SangoUiStyles.applySecondaryButton(button("city_settings_button"));
        SangoUiStyles.applySecondaryButton(button("city_context_help_button"));
        SangoUiStyles.applyPrimaryButton(button("city_end_month_button"));
        SangoUiStyles.applySecondaryButton(button("city_end_month_cancel_button"));
        SangoUiStyles.applyPrimaryButton(button("city_end_month_confirm_button"));
        SangoUiStyles.applySecondaryButton(button("city_battle_prompt_later_button"));
        SangoUiStyles.applyDangerButton(button("city_battle_prompt_view_button"));
        SangoUiStyles.applySecondaryButton(button("recruitment_help_button"));
    }

    private void applyDomesticActionButtonTexts() {
        button("pacify_button").setText(text("button_pacify", "巡查｜100 金、50 糧"));
        button("recruit_button").setText(text("button_recruit", "徵兵"));
        button("train_button").setText(text("button_train_format", "訓練｜{0} 金",
            DomesticActionType.TRAIN.getGoldCost()));
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
        ui.onClick("pacify_button", () -> executeDomesticAction(DomesticActionType.PACIFY));
        ui.onClick("fortify_button", () -> executeDomesticAction(DomesticActionType.FORTIFY));
        ui.onClick("recruit_button", this::openRecruitment);
        ui.onClick("recruitment_cancel_button", this::closeModals);
        ui.onClick("recruitment_help_button", this::showRecruitmentHelp);
        ui.onClick("recruitment_confirm_button", this::confirmRecruitment);
        ui.onClick("recruitment_min_button", () -> setRecruitmentAmount(recruitmentMaximum() > 0 ? 1 : 0));
        ui.onClick("recruitment_max_button", () -> setRecruitmentAmount(recruitmentMaximum()));
        ui.onClick("recruitment_minus_button", () -> setRecruitmentAmount(Math.max(0, requestedRecruitmentAmount() - 100)));
        ui.onClick("recruitment_plus_button", () -> setRecruitmentAmount(Math.min(recruitmentMaximum(), requestedRecruitmentAmount() + 100)));
        ui.onClick("train_button", () -> executeDomesticAction(DomesticActionType.TRAIN));
        ui.onClick("return_map_button", this::returnToMap);
        ui.onClick("city_settings_button", this::openSettings);
        ui.onClick("city_context_help_button", this::showCityHelp);
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
            GameState gameState = SangoServices.saveGames().loadNewest(slotNumber);
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
        executeDomesticAction(actionType, CampaignBalance.DEFAULT_RECRUIT_AMOUNT);
    }

    private boolean executeDomesticAction(DomesticActionType actionType, int recruitmentAmount) {
        if (!SangoServices.session().hasCurrentState()) {
            showNoGameStateError();
            return false;
        }
        boolean successful = false;
        try {
            int slotNumber = SangoServices.session().getCurrentSaveSlot();
            GameState previousState = SangoServices.session().requireCurrentState();
            String cityId = actionType == DomesticActionType.RECRUIT
                ? recruitmentCityId : SangoServices.session().getSelectedCityId();
            CityState previousCity = previousState.requireCityState(cityId);
            DomesticActionResult result = SangoServices.domesticActionCommand().execute(
                slotNumber, previousState, cityId, actionType, recruitmentAmount);
            if (result.isSuccessful()) {
                SangoServices.session().setCurrentState(slotNumber, result.getGameState());
                currentStatusMessage = actionResultMessage(actionType, previousCity,
                    result.getGameState().requireCityState(cityId), recruitmentAmount);
                currentStatusColor = STATUS_SUCCESS_COLOR;
                SangoServices.audio().playSound(SoundEffect.COMMAND_SUCCESS);
                successful = true;
            } else {
                currentStatusMessage = failureMessage(result.getFailureReason());
                currentStatusColor = STATUS_ERROR_COLOR;
                SangoServices.audio().playSound(SoundEffect.COMMAND_ERROR);
            }
        } catch (RuntimeException exception) {
            Gdx.app.error("City", "執行內政命令失敗。", exception);
            currentStatusMessage = text("city_status_action_failed", "內政命令執行失敗，戰局狀態未更新。");
            currentStatusColor = STATUS_ERROR_COLOR;
            SangoServices.audio().playSound(SoundEffect.COMMAND_ERROR);
        }
        refreshView();
        return successful;
    }

    private String actionResultMessage(DomesticActionType actionType, CityState before, CityState after, int recruitmentAmount) {
        if (actionType == DomesticActionType.RECRUIT) {
            if (after.publicOrder < before.publicOrder) {
                return text("city_recruit_forced_result_format",
                    "強徵 {0} 人；人口 {1}，民心 {2} → {3}。金 -{4}、糧 -{5}。",
                    numberFormat.format(recruitmentAmount), numberFormat.format(after.population),
                    before.publicOrder, after.publicOrder,
                    numberFormat.format(RecruitmentRules.goldCost(recruitmentAmount)),
                    numberFormat.format(RecruitmentRules.foodCost(recruitmentAmount)));
            }
            return text("city_recruit_result_format", "徵兵 {0} 人；金 -{1}、糧 -{2}。全軍訓練 {3}、士氣 {4}。",
                numberFormat.format(recruitmentAmount), numberFormat.format(RecruitmentRules.goldCost(recruitmentAmount)),
                numberFormat.format(RecruitmentRules.foodCost(recruitmentAmount)),
                quality(TroopQualityRules.training(after)), quality(TroopQualityRules.morale(after)));
        }
        if (actionType == DomesticActionType.TRAIN) {
            return text("city_train_result_format", "訓練覆蓋 {0}/{1} 人；全軍訓練 {2} → {3}、士氣 {4} → {5}。金 -{6}。",
                numberFormat.format(Math.min(before.troops, OfficerCommandProfile.DEFAULT.trainingCoverage())),
                numberFormat.format(before.troops), quality(TroopQualityRules.training(before)), quality(TroopQualityRules.training(after)),
                quality(TroopQualityRules.morale(before)), quality(TroopQualityRules.morale(after)), DomesticActionType.TRAIN.getGoldCost());
        }
        if (actionType == DomesticActionType.PACIFY) {
            return text(
                "city_status_pacify_result_format",
                "完成巡查：民心 {0} → {1}。金 -100、糧 -50。",
                before.publicOrder,
                after.publicOrder
            );
        }
        return successMessage(actionType);
    }

    private void initializeRecruitmentControls() {
        recruitmentInput = ui.getActor("recruitment_amount_input", TextField.class);
        recruitmentInput.setTextFieldFilter(new TextField.TextFieldFilter.DigitsOnlyFilter());
        recruitmentInput.setMaxLength(5);
        Slider.SliderStyle sliderStyle = new Slider.SliderStyle();
        Drawable track = Sui.resources.manager().getSkin().newDrawable("white", new Color(0.19f, 0.16f, 0.12f, 1f));
        track.setMinHeight(12f);
        Drawable knob = Sui.resources.manager().getSkin().newDrawable("white", new Color(0.94f, 0.76f, 0.43f, 1f));
        knob.setMinWidth(36f);
        knob.setMinHeight(42f);
        Drawable filledTrack = Sui.resources.manager().getSkin().newDrawable("white", new Color(0.59f, 0.39f, 0.16f, 1f));
        filledTrack.setMinHeight(12f);
        sliderStyle.background = track;
        sliderStyle.knob = knob;
        sliderStyle.knobBefore = filledTrack;
        recruitmentSlider = new Slider(0f, 1f, 1f, false, sliderStyle);
        ui.getActor("recruitment_slider_host", Table.class).add(recruitmentSlider).growX().height(70f);
        recruitmentSlider.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (!updatingRecruitmentControls) {
                    setRecruitmentAmount(Math.round(recruitmentSlider.getValue()));
                }
            }
        });
        recruitmentInput.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (!updatingRecruitmentControls && recruitmentCityId != null) {
                    updatingRecruitmentControls = true;
                    recruitmentSlider.setValue(Math.min(recruitmentMaximum(), requestedRecruitmentAmount()));
                    updatingRecruitmentControls = false;
                    refreshRecruitmentPreview();
                }
            }
        });
    }

    private void openRecruitment() {
        if (!SangoServices.session().hasCurrentState()) {
            showNoGameStateError();
            return;
        }
        recruitmentCityId = SangoServices.session().getSelectedCityId();
        int maximum = recruitmentMaximum();
        updatingRecruitmentControls = true;
        recruitmentSlider.setRange(0f, Math.max(1, maximum));
        recruitmentSlider.setDisabled(maximum == 0);
        updatingRecruitmentControls = false;
        setRecruitmentAmount(Math.min(CampaignBalance.DEFAULT_RECRUIT_AMOUNT, maximum));
        openModal(recruitmentMask);
    }

    private int recruitmentMaximum() {
        if (recruitmentCityId == null || !SangoServices.session().hasCurrentState()) {
            return 0;
        }
        GameState gameState = SangoServices.session().requireCurrentState();
        return RecruitmentRules.maximumRecruitable(gameState.requireCityState(recruitmentCityId),
            gameState.requirePlayerFactionState(), OfficerCommandProfile.DEFAULT);
    }

    private int requestedRecruitmentAmount() {
        try {
            return Integer.parseInt(recruitmentInput.getText());
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private void setRecruitmentAmount(int amount) {
        updatingRecruitmentControls = true;
        int boundedAmount = Math.max(0, Math.min(recruitmentMaximum(), amount));
        recruitmentInput.setText(Integer.toString(boundedAmount));
        recruitmentSlider.setValue(boundedAmount);
        updatingRecruitmentControls = false;
        refreshRecruitmentPreview();
    }

    private void refreshRecruitmentPreview() {
        GameState gameState = SangoServices.session().requireCurrentState();
        CityState cityState = gameState.requireCityState(recruitmentCityId);
        int amount = requestedRecruitmentAmount();
        int previewAmount = Math.max(0, Math.min(CampaignBalance.MAXIMUM_RECRUITMENT, amount));
        RecruitmentRules.Quote quote = RecruitmentRules.quote(gameState, cityState, previewAmount, OfficerCommandProfile.DEFAULT);
        FactionState factionState = gameState.requirePlayerFactionState();
        NationalActionPointRules.PublicOrderSummary national = NationalActionPointRules.summarizePlayer(gameState);
        label("recruitment_title_label").setText(text("recruitment_title_format", "{0} · 徵兵", cityName(cityState.cityId)));
        label("recruitment_limit_label").setText(text("recruitment_limit_format", "人口 {0}｜正常保留 {1}｜硬下限 {2}｜本次最多 {3} 人",
            numberFormat.format(cityState.population), numberFormat.format(CampaignBalance.RECRUIT_SAFE_POPULATION_RESERVE),
            numberFormat.format(CampaignBalance.RECRUIT_POPULATION_RESERVE),
            numberFormat.format(quote.maximum())));
        label("recruitment_order_label").setText(text("recruitment_order_format", "本城民心 {0}｜全勢力平均 {1}｜有效民心 {2}｜新兵訓練 {3}、士氣 {4}",
            cityState.publicOrder, NationalOrderTextFormatter.formatAverage(national.averagePublicOrderTenths()),
            NationalOrderTextFormatter.formatAverage(PublicOrderRules.effectiveOrderHundredths(gameState, cityState) / 10),
            quote.recruitTraining(), quote.recruitMorale()));
        label("recruitment_preview_label").setText(text("recruitment_preview_format", "徵兵 {0} 人；人口剩餘 {1}\\n金 -{2}、糧 -{3}；剩餘：金 {4}、糧 {5}；消耗 1 AP\\n全軍訓練 {6} → {7}；士氣 {8} → {9}",
            numberFormat.format(previewAmount), numberFormat.format(cityState.population - previewAmount),
            numberFormat.format(quote.goldCost()), numberFormat.format(quote.foodCost()),
            numberFormat.format(factionState.gold - quote.goldCost()), numberFormat.format(factionState.food - quote.foodCost()),
            quality(TroopQualityRules.training(cityState)), quality(quote.resultingTraining()),
            quality(TroopQualityRules.morale(cityState)), quality(quote.resultingMorale())));
        DomesticActionFailureReason failure = DomesticActionRules.evaluate(gameState, recruitmentCityId, DomesticActionType.RECRUIT, amount);
        label("recruitment_status_label").setText(failure == DomesticActionFailureReason.NONE
            ? quote.forced()
                ? text("recruitment_forced_warning", "人口不足：本次屬於強徵，民心將降低 {0}。",
                    quote.publicOrderLoss())
                : text("recruitment_ready", "確認後才會扣除人口、金糧與行動力；取消不消耗資源。")
            : failureMessage(failure));
        setButtonEnabled(button("recruitment_confirm_button"), failure == DomesticActionFailureReason.NONE);
    }

    private void confirmRecruitment() {
        int amount = requestedRecruitmentAmount();
        if (executeDomesticAction(DomesticActionType.RECRUIT, amount)) {
            closeModals();
        } else {
            label("recruitment_status_label").setText(currentStatusMessage);
        }
    }

    private String quality(int scaledValue) {
        return TroopQualityTextFormatter.format(scaledValue);
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
                "剩餘行動力：{0} / {1}\n月底先完成敵軍命令，再依序結算軍糧、洪災與季節收入、道路接戰、行軍與攻城、撤退、民心與人口，最後建立下月快照。未使用的行動力不會保留。",
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
        SangoServices.audio().playSound(SoundEffect.UI_CLICK);
        Sui.screens.set(ScreenId.STRATEGIC_MAP);
    }

    private void openSettings() {
        closeModals();
        SangoServices.audio().playSound(SoundEffect.UI_CLICK);
        SangoServices.session().openSettings(ScreenId.CITY);
        Sui.screens.set(ScreenId.SETTINGS);
    }

    private void showCityHelp() {
        contextHelpOverlay.show(
            text("help_city_title", "內政操作說明"),
            text(
                "help_city_body",
                "金、糧是全勢力共用資源；人口屬於本城，會限制徵兵並受人口容量影響。農業提高秋收，商業提高季末商稅，治水降低夏季洪災風險與損失，城防影響守城。訓練與士氣會影響部隊作戰表現。\n\n巡查消耗 1 AP、100 金與 50 糧，可提升民心但最高只到 90；低於正常保留人口仍可強徵，但會降低民心，硬下限後不能再徵；訓練消耗 1 AP 與 50 金，單次最多覆蓋 20,000 兵。\n\n民心至少 90，且沒有缺糧、洪災或易主時，每次月底累計一個合格月；連續第 3 次月底起回復 1 點，之後每個合格月底再回復 1 點，最高 100。\n\n例：1 月巡查到 90，若 1、2、3 月月底都符合條件，3 月月底升到 91；期間跌破 90 或發生不合格事件，累計會歸零。\n\n缺軍糧會造成逃兵並降低士氣；同時使該勢力每座城的民心依缺口比例下降 1～3。缺一半軍糧時每城民心扣 2，完全缺糧時每城民心扣 3；民心最低 0。"
            )
        );
        SangoServices.audio().playSound(SoundEffect.UI_CLICK);
    }

    private void showRecruitmentHelp() {
        contextHelpOverlay.show(
            text("help_recruit_title", "徵兵說明"),
            text(
                "help_recruit_body",
                "徵兵會立即減少本城人口，並消耗 1 AP；金與糧依實際徵兵人數計算，預覽會顯示確認後的完整費用。\n\n人口低於 200 屬於強徵並降低 5 民心；人口 100 是不能突破的硬下限。AI 平時不會強徵。\n\n新兵的訓練與士氣依有效民心決定，加入現有部隊後會按新舊兵力重新加權，因此大量徵兵可能拉低全軍平均素質。"
            )
        );
        SangoServices.audio().playSound(SoundEffect.UI_CLICK);
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
        label("national_order_label").setText(NationalOrderTextFormatter.formatPreview(gameState));
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
        label("city_faction_resources_label").setText(text(
            "city_faction_resources_format", "金 {0}｜糧 {1}",
            numberFormat.format(factionState.gold), numberFormat.format(factionState.food)
        ));
        label("population_value_label").setText(numberFormat.format(cityState.population));
        label("troops_value_label").setText(numberFormat.format(cityState.troops));
        label("agriculture_value_label").setText(cityState.agriculture + " / 100");
        label("commerce_value_label").setText(cityState.commerce + " / 100");
        label("water_control_value_label").setText(cityState.waterControl + " / 100");
        label("defense_value_label").setText(cityState.defense + " / 100");
        label("public_order_value_label").setText(cityState.publicOrder + " / 100");
        label("public_order_recovery_label").setText(
            buildPublicOrderRecoveryText(gameState, cityState)
        );
        label("training_value_label").setText(quality(TroopQualityRules.training(cityState)) + " / 100");
        label("morale_value_label").setText(quality(TroopQualityRules.morale(cityState)) + " / 100");
        label("tax_estimate_value_label").setText(
            numberFormat.format(SeasonalEconomyRules.calculateQuarterlyTax(cityState))
        );
        label("harvest_estimate_value_label").setText(
            numberFormat.format(SeasonalEconomyRules.calculateEstimatedHarvest(cityState))
        );
        label("season_forecast_label").setText(buildSeasonForecast(gameState, cityState));
        PopulationRules.Projection populationProjection = PopulationRules.project(gameState, cityState, cityDefinition.populationCapacity);
        label("population_projection_label").setText(text("population_projection_format", "人口容量 {0}｜年底預估 {1} 人（{2}%）\\n依當時民心與人口重新結算；本次僅為預估。",
            numberFormat.format(populationProjection.capacity()),
            (populationProjection.delta() > 0 ? "+" : "") + numberFormat.format(populationProjection.delta()),
            populationProjection.annualRatePercent()));

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

    private String buildPublicOrderRecoveryText(GameState gameState, CityState cityState) {
        if (!gameState.playerFactionId.equals(cityState.ownerFactionId)) {
            return text("city_public_order_recovery_not_owned", "民心穩定：僅適用我方城池");
        }
        if (cityState.publicOrder >= PublicOrderNaturalRecoveryRules.MAXIMUM_PUBLIC_ORDER) {
            return text("city_public_order_recovery_maximum", "民心穩定：已達上限，無需自然恢復");
        }
        if (cityState.publicOrder < PublicOrderNaturalRecoveryRules.MINIMUM_QUALIFYING_PUBLIC_ORDER) {
            return text(
                "city_public_order_recovery_below_threshold",
                "民心穩定：民心達 {0} 後開始累計",
                PublicOrderNaturalRecoveryRules.MINIMUM_QUALIFYING_PUBLIC_ORDER
            );
        }
        return text(
            "city_public_order_recovery_progress",
            "民心穩定：{0}/{1} 月",
            cityState.publicOrderRecoveryStreakMonths,
            PublicOrderNaturalRecoveryRules.REQUIRED_STREAK_MONTHS
        );
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
            "pacify_button",
            gameState,
            cityId,
            DomesticActionType.PACIFY
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
            DomesticActionRules.evaluate(gameState, cityId, actionType,
                actionType == DomesticActionType.RECRUIT ? 1 : 0) == DomesticActionFailureReason.NONE
        );
    }

    private void setActionButtonsEnabled(boolean enabled) {
        setButtonEnabled(button("agriculture_button"), enabled);
        setButtonEnabled(button("commerce_button"), enabled);
        setButtonEnabled(button("water_control_button"), enabled);
        setButtonEnabled(button("pacify_button"), enabled);
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
            case PACIFY -> text(
                "city_status_pacify_success",
                "完成巡查，民心已提升。"
            );
            case FORTIFY -> text(
                "city_status_fortify_success",
                "完成城防修築：城防 +5。"
            );
            case RECRUIT -> text(
                "city_status_recruit_success",
                "完成徵兵，部隊素質依兵力重新加權。"
            );
            case TRAIN -> text(
                "city_status_train_success",
                "完成覆蓋訓練，成果已回算全軍平均。"
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
            case INVALID_RECRUIT_AMOUNT -> text("recruitment_invalid_amount", "請輸入 1 到 1000 之間的徵兵數量。");
            case RECRUIT_LIMIT_EXCEEDED -> text("recruitment_limit_exceeded", "超過本城本次可徵兵上限。");
            case NO_TROOPS -> text("training_no_troops", "城內沒有士兵，不能執行訓練。");
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
        setVisible(recruitmentMask, false);
        stage.setKeyboardFocus(null);
        Gdx.input.setOnscreenKeyboardVisible(false);
    }

    private boolean isAnyModalVisible() {
        return isVisible(endMonthConfirmMask) || isVisible(battlePromptMask) || isVisible(recruitmentMask);
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
        return Sui.i18n.manager().getText("literal", entryName, fallback, arguments).replace("\\n", "\n");
    }
}
