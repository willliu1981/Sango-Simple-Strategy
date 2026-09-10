package idv.kuan.studio.sango.ui;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;

import idv.kuan.studio.libgdx.simpleui.Sui;
import idv.kuan.studio.libgdx.simpleui.SuiScreen;
import idv.kuan.studio.libgdx.simpleui.builder.BuiltUI;
import idv.kuan.studio.sango.application.command.ExpeditionOrder;
import idv.kuan.studio.sango.application.command.LaunchExpeditionCommand;
import idv.kuan.studio.sango.application.command.SetDefensePolicyCommand;
import idv.kuan.studio.sango.application.result.StrategicActionResult;
import idv.kuan.studio.sango.application.result.TurnResolutionReport;
import idv.kuan.studio.sango.application.result.TurnResolutionResult;
import idv.kuan.studio.sango.ui.support.ScreenMusic;
import idv.kuan.studio.sango.audio.SoundEffect;
import idv.kuan.studio.sango.data.SangoPreferences;
import idv.kuan.studio.sango.domain.definition.CityConnectionDefinition;
import idv.kuan.studio.sango.domain.definition.CityDefinition;
import idv.kuan.studio.sango.domain.definition.FactionDefinition;
import idv.kuan.studio.sango.domain.definition.MapCityNodeDefinition;
import idv.kuan.studio.sango.domain.definition.ScenarioDefinition;
import idv.kuan.studio.sango.domain.definition.StrategicMapDefinition;
import idv.kuan.studio.sango.domain.model.ArmyState;
import idv.kuan.studio.sango.domain.model.BattleReport;
import idv.kuan.studio.sango.ui.support.BattleReportCatalog;
import idv.kuan.studio.sango.domain.model.CityState;
import idv.kuan.studio.sango.domain.model.FactionState;
import idv.kuan.studio.sango.domain.model.GameState;
import idv.kuan.studio.sango.domain.model.GameplayStatus;
import idv.kuan.studio.sango.domain.model.ScenarioObjectiveStatus;
import idv.kuan.studio.sango.domain.rule.BattleTactic;
import idv.kuan.studio.sango.domain.rule.DefensePolicy;
import idv.kuan.studio.sango.domain.rule.PostEncounterOrder;
import idv.kuan.studio.sango.domain.service.CityIntelligenceService;
import idv.kuan.studio.sango.domain.service.FactionIntelligenceEstimateService;
import idv.kuan.studio.sango.domain.service.KnownCityView;
import idv.kuan.studio.sango.domain.rule.SeasonalEconomyRules;
import idv.kuan.studio.sango.domain.rule.StrategicActionFailureReason;
import idv.kuan.studio.sango.runtime.SangoServices;
import idv.kuan.studio.sango.ui.flow.MonthEndFlowController;
import idv.kuan.studio.sango.ui.id.ScreenId;
import idv.kuan.studio.sango.ui.support.BattleReportCatalog;
import idv.kuan.studio.sango.ui.support.ContextHelpOverlay;
import idv.kuan.studio.sango.ui.support.DispatchPanelRules;
import idv.kuan.studio.sango.ui.support.ScreenBackground;
import idv.kuan.studio.sango.ui.support.MapTerrainBackground;
import idv.kuan.studio.sango.ui.support.NationalOrderTextFormatter;
import idv.kuan.studio.sango.ui.support.TransferOriginOrder;
import idv.kuan.studio.sango.ui.theme.MapNodeTone;
import idv.kuan.studio.sango.ui.theme.SangoUiStyles;
import idv.kuan.studio.sango.ui.widget.StrategicMapWidget;

/**
 * 可平移縮放的戰略地圖，也是偵察、出征與月份推進的主畫面。
 */
public final class StrategicMapScreen extends SuiScreen {
    private static final String BACKGROUND_PATH = "picture/lobby/sango_lobby_background.png";
    private static final Color STATUS_NORMAL_COLOR = new Color(0.79f, 0.72f, 0.61f, 1f);
    private static final Color STATUS_SUCCESS_COLOR = new Color(0.94f, 0.76f, 0.38f, 1f);
    private static final Color STATUS_ERROR_COLOR = new Color(0.95f, 0.43f, 0.30f, 1f);

    private final ScreenBackground screenBackground = new ScreenBackground();
    private final MapTerrainBackground mapTerrainBackground = new MapTerrainBackground();
    private final NumberFormat numberFormat = NumberFormat.getIntegerInstance(Locale.TAIWAN);
    private final MonthEndFlowController monthEndFlowController = new MonthEndFlowController();
    private final CityIntelligenceService intelligenceService = new CityIntelligenceService();
    private final FactionIntelligenceEstimateService factionIntelligenceEstimateService =
        new FactionIntelligenceEstimateService();
    private final SetDefensePolicyCommand setDefensePolicyCommand = new SetDefensePolicyCommand();

    private Actor endMonthConfirmMask;
    private Actor battlePromptMask;
    private Actor expeditionDispatchMask;
    private Actor mapFullscreenMask;
    private Group mapHost;
    private Group mapFullscreenHost;
    private StrategicMapWidget strategicMapWidget;
    private ContextHelpOverlay contextHelpOverlay;
    private ScrollPane dispatchOriginsPane;
    private boolean mapFullscreen;
    private String currentStatusMessage;
    private Color currentStatusColor = STATUS_NORMAL_COLOR;
    private String pendingOriginCityId;
    private String pendingTargetCityId;
    private TransferOriginOrder pendingTransferOriginOrder = TransferOriginOrder.SHORTEST_TRAVEL;
    private final Map<String, Integer> pendingDispatchAmounts = new LinkedHashMap<>();
    private final List<String> pendingSelectedOriginCityIds = new ArrayList<>();
    private BattleTactic pendingBattleTactic = BattleTactic.FEINT;
    private PostEncounterOrder pendingPostEncounterOrder = PostEncounterOrder.AUTO;

    @Override
    protected BuiltUI buildUI(UIFactory uiFactory) {
        return uiFactory.begin()
            .skipToRegisterTemplate()
            .skipToRegisterXml()
            .registerXml("ui/strategic_map.xml")
            .skipToBuild()
            .buildUI();
    }

    @Override
    protected void onUIBuilt(BuiltUI builtUI) {
        screenBackground.attach(stage, BACKGROUND_PATH);
        endMonthConfirmMask = attachModalMask("end_month_confirm_mask");
        battlePromptMask = attachModalMask("battle_prompt_mask");
        expeditionDispatchMask = attachModalMask("expedition_dispatch_mask");
        mapFullscreenMask = attachModalMask("map_fullscreen_mask");
        mapHost = ui.getActor("map_host", Group.class);
        mapFullscreenHost = ui.getActor("map_fullscreen_host", Group.class);
        strategicMapWidget = new StrategicMapWidget(
            label("map_font_probe").getStyle().font,
            this::selectCity,
            () -> setMapFullscreen(true),
            () -> setMapFullscreen(false)
        );
        mapHost.addActor(strategicMapWidget);
        resizeMapWidget();
        applyStyles();
        Label selectedOrigins = label("dispatch_selected_origins_label");
        selectedOrigins.remove();
        selectedOrigins.setVisible(true);
        selectedOrigins.setAlignment(Align.topLeft);
        Table selectedContent = new Table();
        selectedContent.top().left();
        selectedContent.add(selectedOrigins).growX().top().left().padRight(18f);
        dispatchOriginsPane = new ScrollPane(selectedContent);
        dispatchOriginsPane.setFillParent(true);
        dispatchOriginsPane.setScrollingDisabled(true, false);
        dispatchOriginsPane.setOverscroll(false, false);
        ui.getActor("dispatch_selected_origins_host", Group.class).addActor(dispatchOriginsPane);
        button("context_help_button").setText(text("context_help_button", "操作說明"));
        contextHelpOverlay = new ContextHelpOverlay(
            stage,
            label("selected_city_name_label").getStyle(),
            label("selected_city_route_label").getStyle(),
            button("context_help_button").getStyle(),
            text("context_help_close", "關閉")
        );
        bindActions();
        label("selected_city_owner_label").setTouchable(Touchable.enabled);
        label("selected_city_owner_label").addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                showSelectedFactionIntelligence();
            }
        });
        currentStatusMessage = text("map_status_ready", "選擇城池以管理、偵察或出征。");
        animateEntrance();
    }

    @Override
    protected void afterShow() {
        if (ui == null) {
            return;
        }
        closeModals();
        contextHelpOverlay.hide();
        setMapFullscreen(false);
        ensureCurrentGameState();
        ScreenMusic.play(ScreenId.STRATEGIC_MAP);
        refreshView();
        if (SangoServices.session().consumeStrategicMapFocusRequest()) {
            strategicMapWidget.focusSelectedCity();
        }
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
                    } else if (mapFullscreen) {
                        setMapFullscreen(false);
                    } else {
                        openSettings();
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
        resizeMapWidget();
    }

    @Override
    public void hide() {
        if (strategicMapWidget != null) {
            strategicMapWidget.cancelPendingFactionHighlightTimers();
        }
        super.hide();
    }

    @Override
    protected void beforeDispose() {
        screenBackground.remove();
        if (strategicMapWidget != null) {
            strategicMapWidget.cancelPendingFactionHighlightTimers();
            strategicMapWidget.setTerrainDrawable(null);
        }
        mapTerrainBackground.dispose();
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

    private void resizeMapWidget() {
        if (mapHost == null || strategicMapWidget == null) {
            return;
        }
        // The widget fills its current host during validation, after parent layout.
        strategicMapWidget.invalidate();
    }

    private void setMapFullscreen(boolean fullscreen) {
        if (mapFullscreen == fullscreen || mapFullscreenMask == null || strategicMapWidget == null) {
            return;
        }
        mapFullscreen = fullscreen;
        strategicMapWidget.setFullscreen(fullscreen);
        Group activeHost = fullscreen ? mapFullscreenHost : mapHost;
        activeHost.addActor(strategicMapWidget);
        mapFullscreenMask.setVisible(fullscreen);
        if (fullscreen) {
            mapFullscreenMask.toFront();
        }
        resizeMapWidget();
        SangoServices.audio().playSound(SoundEffect.UI_CLICK);
    }

    private void applyStyles() {
        SangoUiStyles.applySecondaryButton(button("map_zoom_in_button"));
        SangoUiStyles.applySecondaryButton(button("map_zoom_out_button"));
        SangoUiStyles.applySecondaryButton(button("map_fit_button"));
        SangoUiStyles.applySecondaryButton(button("map_focus_button"));
        SangoUiStyles.applySecondaryButton(button("manage_city_button"));
        SangoUiStyles.applySecondaryButton(button("scout_city_button"));
        SangoUiStyles.applyPrimaryButton(button("launch_expedition_button"));
        SangoUiStyles.applySecondaryButton(button("view_city_battle_button"));
        SangoUiStyles.applySecondaryButton(button("map_settings_button"));
        SangoUiStyles.applySecondaryButton(button("context_help_button"));
        SangoUiStyles.applySecondaryButton(button("show_last_report_button"));
        SangoUiStyles.applyDangerButton(button("show_unread_battle_button"));
        SangoUiStyles.applyPrimaryButton(button("end_month_button"));
        SangoUiStyles.applySecondaryButton(button("end_month_cancel_button"));
        SangoUiStyles.applyPrimaryButton(button("end_month_confirm_button"));
        SangoUiStyles.applySecondaryButton(button("battle_prompt_later_button"));
        SangoUiStyles.applyDangerButton(button("battle_prompt_view_button"));
        SangoUiStyles.applySecondaryButton(button("dispatch_decrease_button"));
        SangoUiStyles.applySecondaryButton(button("dispatch_increase_button"));
        SangoUiStyles.applySecondaryButton(button("dispatch_minimum_button"));
        SangoUiStyles.applySecondaryButton(button("dispatch_maximum_button"));
        SangoUiStyles.applySecondaryButton(button("dispatch_origin_previous_button"));
        SangoUiStyles.applySecondaryButton(button("dispatch_origin_next_button"));
        SangoUiStyles.applySecondaryButton(button("dispatch_origin_toggle_button"));
        SangoUiStyles.applySecondaryButton(button("dispatch_help_button"));
        SangoUiStyles.applySecondaryButton(button("dispatch_cancel_button"));
        SangoUiStyles.applyPrimaryButton(button("dispatch_confirm_button"));
        refreshDefensePolicyStyles(null);
        refreshDispatchTacticStyles();
        refreshPostEncounterStyles();
    }

    private void bindActions() {
        ui.onClick("map_zoom_in_button", () -> strategicMapWidget.zoomBy(1.25f));
        ui.onClick("map_zoom_out_button", () -> strategicMapWidget.zoomBy(0.8f));
        ui.onClick("map_fit_button", strategicMapWidget::fitAll);
        ui.onClick("map_focus_button", strategicMapWidget::focusSelectedCity);
        ui.onClick("manage_city_button", this::openSelectedCity);
        ui.onClick("scout_city_button", this::scoutSelectedCity);
        ui.onClick("launch_expedition_button", this::requestDispatch);
        ui.onClick("view_city_battle_button", this::viewSelectedCityBattle);
        ui.onClick("defense_feint_button", () -> selectDefensePolicy(DefensePolicy.FEINT));
        ui.onClick("defense_assault_button", () -> selectDefensePolicy(DefensePolicy.ASSAULT));
        ui.onClick("defense_hold_button", () -> selectDefensePolicy(DefensePolicy.HOLD));
        ui.onClick("dispatch_tactic_feint_button", () -> selectDispatchTactic(BattleTactic.FEINT));
        ui.onClick("dispatch_tactic_assault_button", () -> selectDispatchTactic(BattleTactic.ASSAULT));
        ui.onClick("dispatch_tactic_hold_button", () -> selectDispatchTactic(BattleTactic.HOLD));
        ui.onClick("dispatch_post_continue_button", () -> selectPostEncounterOrder(PostEncounterOrder.CONTINUE));
        ui.onClick("dispatch_post_auto_button", () -> selectPostEncounterOrder(PostEncounterOrder.AUTO));
        ui.onClick("dispatch_post_return_button", () -> selectPostEncounterOrder(PostEncounterOrder.RETURN));
        ui.onClick("map_settings_button", this::openSettings);
        ui.onClick("context_help_button", this::showMapHelp);
        ui.onClick("show_last_report_button", this::showLastTurnReport);
        ui.onClick("show_unread_battle_button", this::viewWorldBattleReports);
        ui.onClick("end_month_button", this::requestEndMonth);
        ui.onClick("end_month_cancel_button", this::closeModals);
        ui.onClick("end_month_confirm_button", this::confirmEndMonth);
        ui.onClick("battle_prompt_later_button", this::openMonthReportAfterBattlePrompt);
        ui.onClick("battle_prompt_view_button", this::openPromptedBattleReport);
        ui.onClick("dispatch_decrease_button", () -> adjustDispatchAmount(-100));
        ui.onClick("dispatch_increase_button", () -> adjustDispatchAmount(100));
        ui.onClick("dispatch_minimum_button", () -> setDispatchAmount(LaunchExpeditionCommand.MINIMUM_EXPEDITION));
        ui.onClick("dispatch_maximum_button", this::setMaximumDispatchAmount);
        ui.onClick("dispatch_origin_previous_button", () -> cycleDispatchOrigin(-1));
        ui.onClick("dispatch_origin_next_button", () -> cycleDispatchOrigin(1));
        ui.onClick("dispatch_origin_toggle_button", this::toggleDispatchOrigin);
        ui.onClick("dispatch_help_button", this::showDispatchHelp);
        ui.onClick("dispatch_cancel_button", this::closeModals);
        ui.onClick("dispatch_confirm_button", this::confirmDispatch);
    }

    private void animateEntrance() {
        Actor main = ui.getActor("main");
        main.getColor().a = 0f;
        main.addAction(Actions.fadeIn(0.28f));
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
            Gdx.app.error("StrategicMap", "無法載入目前戰局。", exception);
            currentStatusMessage = text("map_status_load_failed", "無法載入戰局；請從 Lobby 選擇存檔。");
            currentStatusColor = STATUS_ERROR_COLOR;
        }
    }

    private void selectCity(String cityId) {
        if (!SangoServices.session().hasCurrentState()) {
            return;
        }
        SangoServices.session().setSelectedCityId(cityId);
        currentStatusMessage = text("map_status_city_selected", "已選取城池。");
        currentStatusColor = STATUS_NORMAL_COLOR;
        SangoServices.audio().playSound(SoundEffect.UI_CLICK);
        refreshView();
    }

    private void selectDefensePolicy(DefensePolicy policy) {
        GameState currentState = requireCurrentState();
        if (currentState == null) {
            return;
        }
        String cityId = SangoServices.session().getSelectedCityId();
        try {
            GameState nextState = setDefensePolicyCommand.execute(
                currentState, currentState.playerFactionId, cityId, policy);
            SangoServices.session().setCurrentState(
                SangoServices.session().getCurrentSaveSlot(), nextState);
            setStatus(text("map_status_defense_policy_updated", "已更新此城的防守方針。"),
                STATUS_SUCCESS_COLOR);
        } catch (IllegalArgumentException exception) {
            setStatus(text("map_status_defense_policy_unavailable", "只能設定我方城池的防守方針。"),
                STATUS_ERROR_COLOR);
        }
        SangoServices.audio().playSound(SoundEffect.UI_CLICK);
        refreshView();
    }

    private void refreshDefensePolicyStyles(DefensePolicy selected) {
        applySelectionStyle("defense_feint_button", selected == DefensePolicy.FEINT);
        applySelectionStyle("defense_assault_button", selected == DefensePolicy.ASSAULT);
        applySelectionStyle("defense_hold_button", selected == DefensePolicy.HOLD);
    }

    private void selectDispatchTactic(BattleTactic tactic) {
        pendingBattleTactic = tactic;
        refreshDispatchTacticStyles();
        SangoServices.audio().playSound(SoundEffect.UI_CLICK);
    }

    private void refreshDispatchTacticStyles() {
        applySelectionStyle("dispatch_tactic_feint_button",
            pendingBattleTactic == BattleTactic.FEINT);
        applySelectionStyle("dispatch_tactic_assault_button",
            pendingBattleTactic == BattleTactic.ASSAULT);
        applySelectionStyle("dispatch_tactic_hold_button",
            pendingBattleTactic == BattleTactic.HOLD);
    }

    private void selectPostEncounterOrder(PostEncounterOrder order) {
        pendingPostEncounterOrder = order;
        refreshPostEncounterStyles();
        SangoServices.audio().playSound(SoundEffect.UI_CLICK);
    }

    private void refreshPostEncounterStyles() {
        applySelectionStyle("dispatch_post_continue_button",
            pendingPostEncounterOrder == PostEncounterOrder.CONTINUE);
        applySelectionStyle("dispatch_post_auto_button",
            pendingPostEncounterOrder == PostEncounterOrder.AUTO);
        applySelectionStyle("dispatch_post_return_button",
            pendingPostEncounterOrder == PostEncounterOrder.RETURN);
    }

    private void applySelectionStyle(String actorId, boolean selected) {
        TextButton target = button(actorId);
        if (selected) {
            SangoUiStyles.applySelectedButton(target);
        } else {
            SangoUiStyles.applySecondaryButton(target);
        }
    }

    private void refreshView() {
        if (!SangoServices.session().hasCurrentState()) {
            setAllGameplayButtonsEnabled(false);
            refreshStatusLabel();
            return;
        }
        GameState gameState = SangoServices.session().requireCurrentState();
        ScenarioDefinition scenarioDefinition = SangoServices.definitions().requireScenario(
            gameState.scenarioId
        );
        StrategicMapDefinition mapDefinition = SangoServices.definitions().requireMap(
            gameState.mapId
        );
        String selectedCityId = ensureSelectedCity(gameState);
        strategicMapWidget.setTerrainDrawable(
            mapTerrainBackground.getOrLoad(mapDefinition.backgroundAssetPath)
        );

        label("map_scenario_label").setText(
            text("map_scenario_prefix", "劇本：")
                + localized(scenarioDefinition.nameKey, scenarioDefinition.id)
        );
        label("map_date_label").setText(
            gameState.currentYear + text("city_year_suffix", " 年 ")
                + gameState.currentMonth + text("city_month_suffix", " 月")
        );
        label("map_turn_label").setText(
            text("city_turn_prefix", "回合：第 ") + gameState.currentTurn
                + text("city_turn_suffix", " 回合") + "　"
                + text("city_action_points_prefix", "行動力：")
                + gameState.actionPointsRemaining + " / " + gameState.actionPointsPerTurn
        );
        label("map_national_order_label").setText(NationalOrderTextFormatter.formatPreview(gameState));
        refreshResourceLabel(gameState);
        label("map_name_label").setText(localized(mapDefinition.nameKey, mapDefinition.id));
        refreshObjectiveLabel(gameState);
        refreshUnreadBattleLabel(gameState);
        refreshMapWidget(gameState, mapDefinition, selectedCityId);
        refreshSelectedCityPanel(gameState);
        refreshArmySummary(gameState);
        refreshStatusLabel();

        TurnResolutionReport lastTurnReport = SangoServices.session().getLastTurnReport();
        setButtonEnabled(
            button("show_last_report_button"),
            lastTurnReport != null
        );
        int unreadBattleCount = (int) BattleReportCatalog.world(gameState).stream()
            .filter(report -> !report.read)
            .count();
        button("show_unread_battle_button").setText(
            text("button_world_battle_reports_format", "天下戰報（未讀{0}）", unreadBattleCount)
        );
        setButtonEnabled(button("show_unread_battle_button"), true);
        setButtonEnabled(
            button("end_month_button"),
            gameState.gameplayStatus == GameplayStatus.ACTIVE
        );
    }

    private void refreshResourceLabel(GameState gameState) {
        FactionState factionState = gameState.requirePlayerFactionState();
        int troopCount = 0;
        for (CityState cityState : gameState.cityStates) {
            if (gameState.playerFactionId.equals(cityState.ownerFactionId)) {
                troopCount += cityState.troops;
            }
        }
        for (ArmyState armyState : gameState.armyStates) {
            if (gameState.playerFactionId.equals(armyState.factionId)) {
                troopCount += armyState.troops;
            }
        }
        int foodDemand = SeasonalEconomyRules.calculateMilitaryFoodUpkeep(troopCount);
        int shortage = Math.max(0, foodDemand - factionState.food);
        Label resourceLabel = label("map_resources_label");
        if (shortage > 0) {
            resourceLabel.setText(text(
                "map_resources_shortage_format",
                "金 {0}｜糧 {1}｜本月軍糧 {2}｜缺糧警示：尚缺 {3}",
                numberFormat.format(factionState.gold),
                numberFormat.format(factionState.food),
                numberFormat.format(foodDemand),
                numberFormat.format(shortage)
            ));
            resourceLabel.setColor(STATUS_ERROR_COLOR);
            return;
        }
        resourceLabel.setText(text(
            "map_resources_format",
            "金 {0}｜糧 {1}｜本月軍糧需求 {2}",
            numberFormat.format(factionState.gold),
            numberFormat.format(factionState.food),
            numberFormat.format(foodDemand)
        ));
        resourceLabel.setColor(STATUS_SUCCESS_COLOR);
    }

    private String ensureSelectedCity(GameState gameState) {
        String selectedCityId = SangoServices.session().getSelectedCityId();
        if (selectedCityId != null && gameState.findCityState(selectedCityId) != null) {
            return selectedCityId;
        }
        if (gameState.requirePlayerFactionState().active) {
            selectedCityId = gameState.requirePlayerFactionState().capitalCityId;
        } else {
            selectedCityId = gameState.cityStates[0].cityId;
        }
        SangoServices.session().setSelectedCityId(selectedCityId);
        return selectedCityId;
    }

    private void refreshObjectiveLabel(GameState gameState) {
        if (gameState.gameplayStatus == GameplayStatus.ELIMINATED) {
            label("map_objective_label").setText(
                text("map_gameplay_eliminated", "我方已失去全部城池｜只能查看局勢或讀取存檔")
            );
            return;
        }
        String targetCityName = cityName(gameState.victoryTargetCityId);
        int remainingMonths = Math.max(0, gameState.turnLimitMonths - gameState.elapsedMonths);
        if (gameState.scenarioObjectiveStatus == ScenarioObjectiveStatus.ACHIEVED) {
            label("map_objective_label").setText(
                text("map_objective_achieved_free", "劇本目標達成｜自由征戰中")
            );
        } else if (gameState.scenarioObjectiveStatus == ScenarioObjectiveStatus.FAILED) {
            label("map_objective_label").setText(
                text("map_objective_failed_free", "劇本目標失敗｜自由征戰中")
            );
        } else {
            label("map_objective_label").setText(
                text(
                    "map_objective_format",
                    "目標：攻下 {0}｜期限剩餘 {1} 個月",
                    targetCityName,
                    remainingMonths
                )
            );
        }
    }

    private void refreshUnreadBattleLabel(GameState gameState) {
        int unreadBattleCount = (int) java.util.Arrays.stream(gameState.battleReports)
            .filter(report -> !report.read && report.resolvedTurn == gameState.currentTurn - 1)
            .count();
        label("map_unread_battle_label").setText(
            unreadBattleCount > 0
                ? text(
                    "map_unread_battle_alert_format",
                    "上個月戰事：有 {0} 份新戰報；發生戰鬥的城池正在閃爍。",
                    unreadBattleCount
                )
                : text("map_unread_battle_none", "上個月沒有新的戰報提示。")
        );
    }

    private void refreshMapWidget(
        GameState gameState,
        StrategicMapDefinition mapDefinition,
        String selectedCityId
    ) {
        Map<String, String> captionsByCityId = new LinkedHashMap<>();
        Map<String, MapNodeTone> tonesByCityId = new LinkedHashMap<>();
        Map<String, Integer> unreadBattlesByCityId = new LinkedHashMap<>();
        Map<String, String> factionIdsByCityId = new LinkedHashMap<>();
        for (BattleReport battleReport : BattleReportCatalog.latestCities(gameState)) {
            if (!battleReport.read && battleReport.resolvedTurn == gameState.currentTurn - 1) {
                unreadBattlesByCityId.merge(battleReport.targetCityId, 1, Integer::sum);
                if (battleReport.routeEncounter) {
                    unreadBattlesByCityId.merge(battleReport.originCityId, 1, Integer::sum);
                }
            }
        }
        for (MapCityNodeDefinition nodeDefinition : mapDefinition.nodes) {
            CityState cityState = gameState.requireCityState(nodeDefinition.cityId);
            factionIdsByCityId.put(cityState.cityId, cityState.ownerFactionId);
            MapNodeTone nodeTone = toneForOwner(gameState, cityState.ownerFactionId);
            FactionDefinition ownerDefinition = SangoServices.definitions().requireFaction(cityState.ownerFactionId);
            String marker = localized(ownerDefinition.nameKey, ownerDefinition.id);
            if (gameState.playerFactionId.equals(cityState.ownerFactionId)) {
                marker += text("map_player_suffix", "");
            }
            if (gameState.scenarioObjectiveStatus == ScenarioObjectiveStatus.IN_PROGRESS
                && gameState.victoryTargetCityId.equals(cityState.cityId)) {
                marker += "・" + text("map_marker_target", "目標");
            }
            int unreadCount = unreadBattlesByCityId.getOrDefault(cityState.cityId, 0);
            if (unreadCount > 0) {
                marker += "\n" + text("map_marker_battle_format", "戰事 {0}", unreadCount);
            }
            captionsByCityId.put(cityState.cityId, cityName(cityState.cityId) + "\n" + marker);
            tonesByCityId.put(cityState.cityId, nodeTone);
            unreadBattlesByCityId.put(cityState.cityId, unreadCount);
        }
        strategicMapWidget.setMapData(
            mapDefinition,
            captionsByCityId,
            tonesByCityId,
            unreadBattlesByCityId,
            factionIdsByCityId,
            gameState.playerFactionId,
            gameState.neutralFactionId,
            selectedCityId
        );
        resizeMapWidget();
    }

    private MapNodeTone toneForOwner(GameState gameState, String ownerFactionId) {
        if (gameState.playerFactionId.equals(ownerFactionId)) {
            return MapNodeTone.PLAYER;
        }
        if (gameState.neutralFactionId.equals(ownerFactionId)) {
            return MapNodeTone.NEUTRAL;
        }
        return MapNodeTone.ENEMY;
    }

    private void refreshSelectedCityPanel(GameState gameState) {
        CityState selectedCityState = gameState.requireCityState(
            SangoServices.session().getSelectedCityId()
        );
        FactionDefinition ownerDefinition = SangoServices.definitions().requireFaction(
            selectedCityState.ownerFactionId
        );
        boolean playerOwned = gameState.playerFactionId.equals(selectedCityState.ownerFactionId);
        KnownCityView knownCity = intelligenceService.knownView(
            gameState, gameState.playerFactionId, selectedCityState.cityId);
        List<CityState> dispatchOrigins = playerOwned
            ? orderedTransferOrigins(
                gameState, selectedCityState.cityId, TransferOriginOrder.SHORTEST_TRAVEL
            )
            : findAdjacentPlayerCities(gameState, selectedCityState.cityId);
        CityState dispatchOriginCityState = playerOwned
            ? (dispatchOrigins.isEmpty() ? null : dispatchOrigins.get(0))
            : findBestDispatchOrigin(dispatchOrigins);
        CityState scoutOriginCityState = playerOwned
            ? null : findAdjacentPlayerCityForScout(gameState, selectedCityState.cityId);
        CityState routeOriginCityState = scoutOriginCityState == null
            ? dispatchOriginCityState : scoutOriginCityState;

        label("selected_city_name_label").setText(cityName(selectedCityState.cityId));
        Label ownerLabel = label("selected_city_owner_label");
        ownerLabel.setText(
            text("map_owner_prefix", "所屬：")
                + localized(ownerDefinition.nameKey, ownerDefinition.id)
                + (playerOwned ? "" : text("map_owner_intelligence_hint", "（點擊查看勢力情報）"))
        );
        ownerLabel.setTouchable(playerOwned ? Touchable.disabled : Touchable.enabled);
        label("selected_city_intel_label").setText(
            buildIntelligenceText(gameState, knownCity, playerOwned)
        );
        label("selected_city_stats_label").setText(
            buildSelectedCityStats(knownCity)
        );
        label("selected_city_route_label").setText(
            buildRouteText(gameState, selectedCityState, routeOriginCityState)
        );

        int cityBattleCount = BattleReportCatalog.city(
            gameState, selectedCityState.cityId).size();
        button("view_city_battle_button").setText(
            text("button_city_battles_format", "查看此城戰報（{0}）", cityBattleCount)
        );
        setButtonEnabled(button("view_city_battle_button"), cityBattleCount > 0);

        boolean gameplayActive = gameState.gameplayStatus == GameplayStatus.ACTIVE;
        Actor defensePolicyPanel = ui.getActor("defense_policy_panel");
        DefensePolicy visibleDefensePolicy = playerOwned ? selectedCityState.defensePolicy : null;
        boolean showDefensePolicy = playerOwned;
        defensePolicyPanel.setVisible(playerOwned);
        defensePolicyPanel.setTouchable(playerOwned ? Touchable.enabled : Touchable.disabled);
        refreshDefensePolicyStyles(visibleDefensePolicy);
        label("defense_policy_result_label").setText(showDefensePolicy
            ? text(
                "map_defense_policy_current_format",
                "目前設定：{0}",
                defensePolicyName(visibleDefensePolicy))
            : "");
        setButtonEnabled(button("defense_feint_button"), playerOwned && gameplayActive);
        setButtonEnabled(button("defense_assault_button"), playerOwned && gameplayActive);
        setButtonEnabled(button("defense_hold_button"), playerOwned && gameplayActive);
        if (!playerOwned) {
            button("defense_feint_button").setDisabled(false);
            button("defense_assault_button").setDisabled(false);
            button("defense_hold_button").setDisabled(false);
        }
        setButtonEnabled(button("manage_city_button"), gameplayActive && playerOwned);
        setButtonEnabled(
            button("scout_city_button"),
            gameplayActive && !playerOwned && scoutOriginCityState != null
                && gameState.actionPointsRemaining >= 1
        );

        int dispatchTroops = dispatchOriginCityState == null
            ? 0
            : SangoServices.launchExpeditionCommand().calculateDispatchTroops(dispatchOriginCityState);
        button("launch_expedition_button").setText(
            playerOwned
                ? text("button_transfer_troops", "運兵")
                : text("button_launch_expedition", "出征")
        );
        boolean expeditionEnabled = gameplayActive
            && dispatchOriginCityState != null
            && dispatchTroops >= LaunchExpeditionCommand.MINIMUM_EXPEDITION
            && gameState.actionPointsRemaining >= 1
            && gameState.requirePlayerFactionState().food >= LaunchExpeditionCommand.FOOD_COST;
        setButtonEnabled(button("launch_expedition_button"), expeditionEnabled);
    }

    private String buildIntelligenceText(
        GameState gameState,
        KnownCityView city,
        boolean playerOwned
    ) {
        if (city.exact()) {
            String source = playerOwned
                ? text("map_intel_own_city", "本城即時資料")
                : city.observationDateRecorded()
                    ? text("map_intel_scout_date_format", "{0} 年 {1} 月偵察",
                        city.observedYear(), city.observedMonth())
                    : text("map_intel_legacy", "舊存檔情報");
            return text(
                "map_intel_exact_format",
                "兵力：{0}｜人口：{1}｜情報：{2}",
                numberFormat.format(city.troops()),
                numberFormat.format(city.population()),
                source
            );
        }
        int lowerBound = Math.max(0, city.troops() * 75 / 100 / 100 * 100);
        int upperBound = Math.max(100, (city.troops() * 125 / 100 + 99) / 100 * 100);
        return text(
            "map_intel_estimate_format",
            "兵力：約 {0}～{1}｜尚未取得偵察情報。",
            numberFormat.format(lowerBound),
            numberFormat.format(upperBound)
        );
    }

    private void showSelectedFactionIntelligence() {
        GameState gameState = requireCurrentState();
        if (gameState == null || !SangoServices.session().hasCurrentState()) {
            return;
        }
        CityState selectedCity = gameState.requireCityState(SangoServices.session().getSelectedCityId());
        if (gameState.playerFactionId.equals(selectedCity.ownerFactionId)) {
            return;
        }
        FactionDefinition faction = SangoServices.definitions().requireFaction(selectedCity.ownerFactionId);
        FactionIntelligenceEstimateService.FactionEstimate estimate = factionIntelligenceEstimateService.estimate(
            gameState, gameState.playerFactionId, selectedCity.ownerFactionId);
        String intelligenceState = estimate.allCitiesExact()
            ? text("faction_intelligence_complete", "全部城池已有有效偵察快照。")
            : text("faction_intelligence_partial_format", "已掌握 {0}／{1} 座城池；未掌握城池會擴大估算範圍。",
                estimate.exactCityCount(), estimate.cityCount());
        contextHelpOverlay.show(
            text("faction_intelligence_title_format", "{0}・勢力情報", localized(faction.nameKey, faction.id)),
            text(
                "faction_intelligence_body_format",
                "{0}\n\n兵力估算：{1}～{2}\n國庫估算：金 {3}～{4}｜糧 {5}～{6}\n下月行動力估算：{7}～{8} AP\n\n估算依有效偵察快照、已知城池數與公開規則推算；不會讀取敵方實際國庫、行軍部隊或本月剩餘行動力。",
                intelligenceState,
                numberFormat.format(estimate.troopsLower()), numberFormat.format(estimate.troopsUpper()),
                numberFormat.format(estimate.goldLower()), numberFormat.format(estimate.goldUpper()),
                numberFormat.format(estimate.foodLower()), numberFormat.format(estimate.foodUpper()),
                estimate.actionPointsLower(), estimate.actionPointsUpper()
            )
        );
        SangoServices.audio().playSound(SoundEffect.UI_CLICK);
    }

    private String buildSelectedCityStats(KnownCityView city) {
        if (!city.exact()) {
            return text("map_stats_unknown", "城防、訓練、士氣、民心與內政尚未掌握。");
        }
        return text(
            "map_stats_format",
            "城防 {0}｜訓練 {1}｜士氣 {2}｜民心 {3}　農業 {4}｜商業 {5}｜治水 {6}",
            city.defense(),
            city.training(),
            city.morale(),
            city.publicOrder(),
            city.agriculture(),
            city.commerce(),
            city.waterControl()
        );
    }

    private String buildRouteText(
        GameState gameState,
        CityState selectedCityState,
        CityState originCityState
    ) {
        if (gameState.playerFactionId.equals(selectedCityState.ownerFactionId)) {
            if (originCityState == null) {
                return text("map_route_owned", "此城屬於我方；目前沒有可運兵的我方城池。");
            }
            return text(
                "map_route_owned_transfer_format",
                "可由 {0} 運兵至此，行程 {1} 個月。派兵消耗 1 行動力與 100 糧。",
                cityName(originCityState.cityId),
                travelMonths(gameState, originCityState.cityId, selectedCityState.cityId)
            );
        }
        if (originCityState == null) {
            return text("map_route_not_adjacent", "目前沒有與此城直接相鄰的我方城池。");
        }
        return text(
            "map_route_format",
            "可由 {0} 沿道路進軍，行程 {1} 個月。出征消耗 1 行動力與 100 糧。",
            cityName(originCityState.cityId),
            travelMonths(gameState, originCityState.cityId, selectedCityState.cityId)
        );
    }

    private int travelMonths(GameState gameState, String originCityId, String targetCityId) {
        return Math.max(0, SangoServices.definitions().requireMap(gameState.mapId)
            .shortestTravelMonths(originCityId, targetCityId));
    }

    private CityState findAdjacentPlayerCity(GameState gameState, String targetCityId) {
        return findBestDispatchOrigin(findAdjacentPlayerCities(gameState, targetCityId));
    }

    private CityState findAdjacentPlayerCityForScout(GameState gameState, String targetCityId) {
        StrategicMapDefinition mapDefinition = SangoServices.definitions().requireMap(gameState.mapId);
        for (CityState playerCity : gameState.findCitiesOwnedBy(gameState.playerFactionId)) {
            if (mapDefinition.findConnection(playerCity.cityId, targetCityId) != null) {
                return playerCity;
            }
        }
        return null;
    }

    private CityState findBestDispatchOrigin(List<CityState> candidates) {
        CityState bestOrigin = null;
        int largestDispatch = -1;
        for (CityState playerCity : candidates) {
            int dispatch = SangoServices.launchExpeditionCommand().calculateDispatchTroops(playerCity);
            if (dispatch > largestDispatch) {
                bestOrigin = playerCity;
                largestDispatch = dispatch;
            }
        }
        return bestOrigin;
    }

    private List<CityState> findAdjacentPlayerCities(GameState gameState, String targetCityId) {
        StrategicMapDefinition mapDefinition = SangoServices.definitions().requireMap(gameState.mapId);
        List<CityState> candidates = new ArrayList<>();
        for (CityState playerCity : gameState.findCitiesOwnedBy(gameState.playerFactionId)) {
            if (mapDefinition.findConnection(playerCity.cityId, targetCityId) != null
                && SangoServices.launchExpeditionCommand().calculateDispatchTroops(playerCity)
                    >= LaunchExpeditionCommand.MINIMUM_EXPEDITION) {
                candidates.add(playerCity);
            }
        }
        return candidates;
    }

    private List<CityState> findTransferPlayerCities(GameState gameState, String targetCityId) {
        List<CityState> candidates = new ArrayList<>();
        StrategicMapDefinition mapDefinition = SangoServices.definitions().requireMap(gameState.mapId);
        for (CityState playerCity : gameState.findCitiesOwnedBy(gameState.playerFactionId)) {
            if (!playerCity.cityId.equals(targetCityId)
                && mapDefinition.shortestTravelMonths(playerCity.cityId, targetCityId) > 0
                && SangoServices.launchExpeditionCommand().calculateDispatchTroops(playerCity)
                    >= LaunchExpeditionCommand.MINIMUM_EXPEDITION) {
                candidates.add(playerCity);
            }
        }
        return candidates;
    }

    private List<CityState> orderedTransferOrigins(
        GameState gameState,
        String targetCityId,
        TransferOriginOrder order
    ) {
        return order.sort(
            findTransferPlayerCities(gameState, targetCityId),
            city -> travelMonths(gameState, city.cityId, targetCityId),
            city -> SangoServices.launchExpeditionCommand().calculateDispatchTroops(city)
        );
    }

    private void refreshArmySummary(GameState gameState) {
        if (gameState.armyStates.length == 0) {
            label("map_army_summary_label").setText(text(
                "map_army_none_multi", "", gameState.enemyAttackCountdown
            ));
            return;
        }
        ArmyState shownArmy = gameState.armyStates[0];
        for (ArmyState armyState : gameState.armyStates) {
            if (gameState.playerFactionId.equals(armyState.factionId)) {
                shownArmy = armyState;
                break;
            }
        }
        FactionDefinition armyFaction = SangoServices.definitions().requireFaction(shownArmy.factionId);
        String summary = text(
            "map_army_summary_multi", "", localized(armyFaction.nameKey, armyFaction.id),
            shownArmy.troops, shownArmy.morale, cityName(shownArmy.originCityId),
            cityName(shownArmy.targetCityId), shownArmy.remainingTravelMonths
        );
        if (shownArmy.retreatRouteCityIds != null) {
            String[] route = shownArmy.retreatRouteCityIds;
            int months = shownArmy.remainingTravelMonths;
            StrategicMapDefinition definition = SangoServices.definitions().requireMap(gameState.mapId);
            for (int i = shownArmy.retreatRouteIndex + 1; i < route.length - 1; i++) {
                CityConnectionDefinition connection = definition.findConnection(route[i], route[i + 1]);
                if (connection != null) months += connection.travelMonths;
            }
            summary = text("map_army_retreat_summary",
                "{0} {1} 兵・士氣 {2}｜撤往 {3}（尚需 {4} 個月）",
                localized(armyFaction.nameKey, armyFaction.id), numberFormat.format(shownArmy.troops),
                shownArmy.morale, cityName(route[route.length - 1]), months);
        }
        if (gameState.armyStates.length > 1) {
            summary += "\n" + text("map_army_more", "", gameState.armyStates.length - 1);
        }
        label("map_army_summary_label").setText(summary);
    }

    private void openSelectedCity() {
        if (!SangoServices.session().hasCurrentState()) {
            return;
        }
        GameState gameState = SangoServices.session().requireCurrentState();
        CityState selectedCityState = gameState.requireCityState(
            SangoServices.session().getSelectedCityId()
        );
        if (!gameState.playerFactionId.equals(selectedCityState.ownerFactionId)) {
            return;
        }
        SangoServices.audio().playSound(SoundEffect.CONFIRM);
        Sui.screens.set(ScreenId.CITY);
    }

    private void scoutSelectedCity() {
        GameState currentState = requireCurrentState();
        if (currentState == null) {
            return;
        }
        String targetCityId = SangoServices.session().getSelectedCityId();
        boolean transfer = currentState.playerFactionId.equals(
            currentState.requireCityState(targetCityId).ownerFactionId
        );
        List<CityState> transferOrigins = transfer
            ? findTransferPlayerCities(currentState, targetCityId) : new ArrayList<>();
        CityState originCityState = transfer
            ? (transferOrigins.isEmpty() ? null : transferOrigins.get(0))
            : findAdjacentPlayerCityForScout(currentState, targetCityId);
        if (originCityState == null) {
            setStatus(text("map_status_not_adjacent", "沒有可執行偵察的相鄰我方城池。"), STATUS_ERROR_COLOR);
            return;
        }
        try {
            StrategicActionResult result = SangoServices.scoutCityCommand().execute(
                SangoServices.session().getCurrentSaveSlot(),
                currentState,
                originCityState.cityId,
                targetCityId
            );
            applyStrategicActionResult(result, true);
        } catch (RuntimeException exception) {
            Gdx.app.error("StrategicMap", "偵察命令失敗。", exception);
            setStatus(text("map_status_action_failed", "戰略命令失敗，戰局未更新。"), STATUS_ERROR_COLOR);
            SangoServices.audio().playSound(SoundEffect.COMMAND_ERROR);
        }
        refreshView();
    }

    private void requestDispatch() {
        GameState currentState = requireCurrentState();
        if (currentState == null) {
            return;
        }
        String targetCityId = SangoServices.session().getSelectedCityId();
        boolean transfer = currentState.playerFactionId.equals(
            currentState.requireCityState(targetCityId).ownerFactionId
        );
        if (transfer) {
            pendingTransferOriginOrder = TransferOriginOrder.SHORTEST_TRAVEL;
        }
        pendingBattleTactic = BattleTactic.FEINT;
        pendingPostEncounterOrder = PostEncounterOrder.AUTO;
        List<CityState> candidates = transfer
            ? orderedTransferOrigins(currentState, targetCityId, pendingTransferOriginOrder)
            : findAdjacentPlayerCities(currentState, targetCityId);
        CityState originCityState = transfer
            ? (candidates.isEmpty() ? null : candidates.get(0))
            : findBestDispatchOrigin(candidates);
        if (originCityState == null) {
            setStatus(
                transfer
                    ? text("map_status_no_transfer_origin", "沒有可沿道路運兵至此的我方城池。")
                    : text("map_status_not_adjacent", "沒有可派兵的相鄰我方城池。"),
                STATUS_ERROR_COLOR
            );
            return;
        }
        pendingDispatchAmounts.clear();
        pendingSelectedOriginCityIds.clear();
        for (CityState candidate : candidates) {
            pendingDispatchAmounts.put(
                candidate.cityId,
                SangoServices.launchExpeditionCommand().calculateDispatchTroops(candidate)
            );
        }
        pendingOriginCityId = originCityState.cityId;
        pendingTargetCityId = targetCityId;
        pendingSelectedOriginCityIds.add(originCityState.cityId);
        refreshDispatchTacticStyles();
        refreshPostEncounterStyles();
        updateDispatchTacticPanel(transfer);
        refreshDispatchDetails();
        openModal(expeditionDispatchMask);
        dispatchOriginsPane.setScrollY(0f);
        stage.setScrollFocus(dispatchOriginsPane);
    }

    private void cycleDispatchOrigin(int direction) {
        GameState currentState = requireCurrentState();
        if (currentState == null || pendingOriginCityId == null || pendingTargetCityId == null) {
            return;
        }
        boolean transfer = isPendingTransfer(currentState);
        updateDispatchTacticPanel(transfer);
        List<CityState> candidates = pendingOriginCandidates(currentState);
        if (candidates.size() < 2) {
            return;
        }
        int currentIndex = 0;
        for (int index = 0; index < candidates.size(); index++) {
            if (pendingOriginCityId.equals(candidates.get(index).cityId)) {
                currentIndex = index;
                break;
            }
        }
        int nextIndex = Math.floorMod(currentIndex + direction, candidates.size());
        pendingOriginCityId = candidates.get(nextIndex).cityId;
        if (transfer) {
            pendingSelectedOriginCityIds.clear();
            pendingSelectedOriginCityIds.add(pendingOriginCityId);
        }
        refreshDispatchDetails();
        SangoServices.audio().playSound(SoundEffect.UI_CLICK);
    }

    private void toggleDispatchOrigin() {
        GameState currentState = requireCurrentState();
        if (currentState == null || pendingOriginCityId == null) {
            return;
        }
        if (isPendingTransfer(currentState)) {
            pendingTransferOriginOrder = pendingTransferOriginOrder.next();
            List<CityState> origins = pendingOriginCandidates(currentState);
            if (!origins.isEmpty()) {
                pendingOriginCityId = origins.get(0).cityId;
                pendingSelectedOriginCityIds.clear();
                pendingSelectedOriginCityIds.add(pendingOriginCityId);
            }
            refreshDispatchDetails();
            SangoServices.audio().playSound(SoundEffect.UI_CLICK);
            return;
        }
        if (pendingSelectedOriginCityIds.contains(pendingOriginCityId)) {
            pendingSelectedOriginCityIds.remove(pendingOriginCityId);
        } else {
            pendingSelectedOriginCityIds.add(pendingOriginCityId);
        }
        refreshDispatchDetails();
        SangoServices.audio().playSound(SoundEffect.UI_CLICK);
    }

    private void refreshDispatchDetails() {
        GameState currentState = requireCurrentState();
        if (currentState == null || pendingOriginCityId == null || pendingTargetCityId == null) {
            return;
        }
        boolean transfer = isPendingTransfer(currentState);
        List<CityState> origins = pendingOriginCandidates(currentState);
        int originIndex = indexOfCity(origins, pendingOriginCityId);
        label("dispatch_title_label").setText(transfer
            ? text("dispatch_transfer_title", "運兵")
            : text("dispatch_expedition_title", "聯合出征"));
        label("dispatch_route_label").setText(text(
            transfer ? "dispatch_transfer_route_format" : "dispatch_route_format",
            transfer
                ? "目標：{1}｜目前來源：{0}｜路程：{2} 個月｜可派兵：{3}"
                : "目標：{1}｜目前來源：{0}｜路程：{2} 個月",
            cityName(pendingOriginCityId), cityName(pendingTargetCityId),
            travelMonths(currentState, pendingOriginCityId, pendingTargetCityId),
            numberFormat.format(SangoServices.launchExpeditionCommand().calculateDispatchTroops(
                currentState.requireCityState(pendingOriginCityId)
            ))
        ));
        label("dispatch_origin_label").setText(text(
            "dispatch_origin_selection_format", "來源：{0}（{1}/{2}）",
            cityName(pendingOriginCityId), originIndex + 1, origins.size()
        ));
        setButtonEnabled(button("dispatch_origin_previous_button"), origins.size() >= 2);
        setButtonEnabled(button("dispatch_origin_next_button"), origins.size() >= 2);
        TextButton toggleButton = button("dispatch_origin_toggle_button");
        if (transfer) {
            toggleButton.setText(pendingTransferOriginOrder == TransferOriginOrder.SHORTEST_TRAVEL
                ? text("dispatch_sort_shortest", "排序：路程最短")
                : text("dispatch_sort_most_troops", "排序：可派兵最多"));
            SangoUiStyles.applySecondaryButton(toggleButton);
            setButtonEnabled(toggleButton, origins.size() >= 2);
        } else {
            boolean selected = pendingSelectedOriginCityIds.contains(pendingOriginCityId);
            toggleButton.setText(selected
                ? text("dispatch_remove_origin", "移除此城")
                : text("dispatch_add_origin", "加入此城"));
            if (selected) {
                SangoUiStyles.applySelectedButton(toggleButton);
            } else {
                SangoUiStyles.applySecondaryButton(toggleButton);
            }
            setButtonEnabled(toggleButton, true);
        }
        button("dispatch_confirm_button").setText(transfer
            ? text("button_confirm_transfer", "確認運兵")
            : text("button_confirm_expedition", "確認出征"));
        button("dispatch_help_button").setText(transfer
            ? text("dispatch_transfer_help_button", "運兵說明")
            : text("dispatch_expedition_help_button", "出征說明"));
        refreshDispatchAmount();
    }

    private void adjustDispatchAmount(int delta) {
        setDispatchAmount(currentDispatchAmount() + delta);
    }

    private void updateDispatchTacticPanel(boolean transfer) {
        Actor tacticPanel = ui.getActor("dispatch_tactic_panel");
        boolean visible = DispatchPanelRules.showsTacticSelection(transfer);
        tacticPanel.setVisible(visible);
        tacticPanel.setTouchable(visible ? Touchable.enabled : Touchable.disabled);
        Actor postPanel = ui.getActor("dispatch_post_encounter_panel");
        postPanel.setVisible(visible);
        postPanel.setTouchable(visible ? Touchable.enabled : Touchable.disabled);
    }

    private void setDispatchAmount(int amount) {
        GameState currentState = requireCurrentState();
        if (currentState == null || pendingOriginCityId == null) {
            return;
        }
        int maximumAmount = SangoServices.launchExpeditionCommand().calculateDispatchTroops(
            currentState.requireCityState(pendingOriginCityId)
        );
        pendingDispatchAmounts.put(pendingOriginCityId, Math.max(
            LaunchExpeditionCommand.MINIMUM_EXPEDITION,
            Math.min(maximumAmount, amount)
        ));
        refreshDispatchAmount();
    }

    private void setMaximumDispatchAmount() {
        GameState currentState = requireCurrentState();
        if (currentState == null || pendingOriginCityId == null) {
            return;
        }
        setDispatchAmount(SangoServices.launchExpeditionCommand().calculateDispatchTroops(
            currentState.requireCityState(pendingOriginCityId)
        ));
    }

    private void refreshDispatchAmount() {
        label("dispatch_amount_label").setText(
            text("dispatch_amount_format", "目前來源派兵：{0}", numberFormat.format(currentDispatchAmount()))
        );
        refreshDispatchSummary();
    }

    private void refreshDispatchSummary() {
        GameState currentState = requireCurrentState();
        if (currentState == null || pendingTargetCityId == null) {
            return;
        }
        List<String> selectedOrigins = effectiveSelectedOrigins(currentState);
        int totalTroops = 0;
        int slowestTravelMonths = 0;
        StringBuilder selectedLines = new StringBuilder();
        for (String originCityId : selectedOrigins) {
            int amount = pendingDispatchAmounts.getOrDefault(originCityId, 0);
            int months = travelMonths(currentState, originCityId, pendingTargetCityId);
            totalTroops += amount;
            slowestTravelMonths = Math.max(slowestTravelMonths, months);
            if (selectedLines.length() > 0) {
                selectedLines.append('\n');
            }
            selectedLines.append(text(
                "dispatch_selected_origin_line_format",
                "{0}：{1} 兵｜{2} 個月",
                cityName(originCityId), numberFormat.format(amount), months
            ));
        }
        label("dispatch_selected_origins_label").setText(selectedOrigins.isEmpty()
            ? text("dispatch_selected_origins_empty", "已選來源：尚未加入任何城池。")
            : text("dispatch_selected_origins_format", "已選來源：\n{0}", selectedLines));
        int orderCount = selectedOrigins.size();
        boolean transfer = isPendingTransfer(currentState);
        label("dispatch_summary_label").setText(transfer
            ? text(
                "dispatch_transfer_summary_format",
                "本次運兵 {0} 兵｜費用 1 AP、100 糧｜行程 {1} 個月",
                numberFormat.format(totalTroops), slowestTravelMonths
            )
            : text(
                "dispatch_summary_format",
                "合計 {0} 城／{1} 兵｜費用 {2} AP、{3} 糧｜集結 {4} 個月（最慢路程）",
                orderCount,
                numberFormat.format(totalTroops),
                orderCount,
                numberFormat.format(orderCount * LaunchExpeditionCommand.FOOD_COST),
                slowestTravelMonths
            ));
        String validationMessage = dispatchValidationMessage(currentState, selectedOrigins);
        label("dispatch_status_label").setText(validationMessage == null
            ? transfer
                ? text("dispatch_transfer_status_ready", "資源足夠；確認後將由目前來源出發。")
                : text("dispatch_status_ready", "資源足夠；確認後所有來源將編入同一批行動。")
            : validationMessage);
        label("dispatch_status_label").setColor(
            validationMessage == null ? STATUS_SUCCESS_COLOR : STATUS_ERROR_COLOR
        );
        setButtonEnabled(button("dispatch_confirm_button"), validationMessage == null);
    }

    private String dispatchValidationMessage(GameState gameState, List<String> selectedOrigins) {
        if (selectedOrigins.isEmpty()) {
            return text("dispatch_status_select_origin", "請至少加入一座來源城。 ");
        }
        for (String originCityId : selectedOrigins) {
            int amount = pendingDispatchAmounts.getOrDefault(originCityId, 0);
            int maximum = SangoServices.launchExpeditionCommand().calculateDispatchTroops(
                gameState.requireCityState(originCityId)
            );
            if (amount < LaunchExpeditionCommand.MINIMUM_EXPEDITION
                || amount % 100 != 0 || amount > maximum) {
                return text(
                    "dispatch_status_invalid_origin_amount_format",
                    "{0} 的派兵數需至少 200、以 100 遞增，並保留 200 守軍。",
                    cityName(originCityId)
                );
            }
        }
        int orderCount = selectedOrigins.size();
        if (gameState.actionPointsRemaining < orderCount) {
            return text(
                "dispatch_status_insufficient_ap_format",
                "行動力不足：本次需 {0} AP，目前剩餘 {1}。",
                orderCount, gameState.actionPointsRemaining
            );
        }
        int foodCost = orderCount * LaunchExpeditionCommand.FOOD_COST;
        if (gameState.requirePlayerFactionState().food < foodCost) {
            return text(
                "dispatch_status_insufficient_food_format",
                "糧不足：本次需 {0} 糧，目前只有 {1}。",
                numberFormat.format(foodCost),
                numberFormat.format(gameState.requirePlayerFactionState().food)
            );
        }
        return null;
    }

    private int currentDispatchAmount() {
        return pendingOriginCityId == null
            ? 0 : pendingDispatchAmounts.getOrDefault(pendingOriginCityId, 0);
    }

    private boolean isPendingTransfer(GameState gameState) {
        return pendingTargetCityId != null && gameState.playerFactionId.equals(
            gameState.requireCityState(pendingTargetCityId).ownerFactionId
        );
    }

    private List<CityState> pendingOriginCandidates(GameState gameState) {
        return isPendingTransfer(gameState)
            ? orderedTransferOrigins(gameState, pendingTargetCityId, pendingTransferOriginOrder)
            : findAdjacentPlayerCities(gameState, pendingTargetCityId);
    }

    private List<String> effectiveSelectedOrigins(GameState gameState) {
        List<String> selected = new ArrayList<>();
        if (isPendingTransfer(gameState)) {
            if (pendingOriginCityId != null) {
                selected.add(pendingOriginCityId);
            }
            return selected;
        }
        selected.addAll(pendingSelectedOriginCityIds);
        return selected;
    }

    private int indexOfCity(List<CityState> cities, String cityId) {
        for (int index = 0; index < cities.size(); index++) {
            if (cities.get(index).cityId.equals(cityId)) {
                return index;
            }
        }
        return 0;
    }

    private void confirmDispatch() {
        GameState currentState = requireCurrentState();
        if (currentState == null || pendingOriginCityId == null || pendingTargetCityId == null) {
            closeModals();
            return;
        }
        String originCityId = pendingOriginCityId;
        String targetCityId = pendingTargetCityId;
        boolean transfer = isPendingTransfer(currentState);
        List<String> selectedOrigins = effectiveSelectedOrigins(currentState);
        String validationMessage = dispatchValidationMessage(currentState, selectedOrigins);
        if (validationMessage != null) {
            label("dispatch_status_label").setText(validationMessage);
            label("dispatch_status_label").setColor(STATUS_ERROR_COLOR);
            return;
        }
        try {
            StrategicActionResult result;
            if (transfer) {
                result = SangoServices.launchExpeditionCommand().execute(
                    SangoServices.session().getCurrentSaveSlot(),
                    currentState,
                    originCityId,
                    targetCityId,
                    pendingDispatchAmounts.get(originCityId),
                    BattleTactic.HOLD
                );
            } else {
                List<ExpeditionOrder> orders = new ArrayList<>();
                for (String selectedOriginCityId : selectedOrigins) {
                    orders.add(new ExpeditionOrder(
                        selectedOriginCityId,
                        pendingDispatchAmounts.get(selectedOriginCityId)
                    ));
                }
                result = SangoServices.launchExpeditionCommand().execute(
                    SangoServices.session().getCurrentSaveSlot(),
                    currentState,
                    targetCityId,
                    orders,
                    pendingBattleTactic,
                    pendingPostEncounterOrder
                );
            }
            closeModals();
            applyStrategicActionResult(result, false);
        } catch (RuntimeException exception) {
            Gdx.app.error("StrategicMap", "出征命令失敗。", exception);
            setStatus(text("map_status_action_failed", "戰略命令失敗，戰局未更新。"), STATUS_ERROR_COLOR);
            SangoServices.audio().playSound(SoundEffect.COMMAND_ERROR);
        }
        refreshView();
    }

    private void applyStrategicActionResult(
        StrategicActionResult actionResult,
        boolean scouting
    ) {
        if (actionResult.isSuccessful()) {
            int slotNumber = SangoServices.session().getCurrentSaveSlot();
            SangoServices.session().setCurrentState(slotNumber, actionResult.getGameState());
            if (scouting) {
                setStatus(
                    text("map_status_scout_success", "偵察完成；情報快照維持三個月（含當月）。"),
                    STATUS_SUCCESS_COLOR
                );
            } else {
                setStatus(
                    text(
                        "map_status_expedition_success",
                        "已派出 {0} 兵；部隊將在月底結算時行軍。",
                        numberFormat.format(actionResult.getDispatchedTroops())
                    ),
                    STATUS_SUCCESS_COLOR
                );
            }
            SangoServices.audio().playSound(SoundEffect.COMMAND_SUCCESS);
            return;
        }
        setStatus(strategicFailureMessage(actionResult.getFailureReason()), STATUS_ERROR_COLOR);
        SangoServices.audio().playSound(SoundEffect.COMMAND_ERROR);
    }

    private String strategicFailureMessage(StrategicActionFailureReason failureReason) {
        return switch (failureReason) {
            case PLAYER_ELIMINATED -> text("map_status_player_eliminated", "我方已失去全部城池，不能再下達命令。");
            case NO_ACTION_POINTS -> text("city_status_no_action_points", "行動力不足；請結束月份。");
            case ORIGIN_NOT_OWNED -> text("map_status_origin_not_owned", "出發城不屬於我方。");
            case TARGET_ALREADY_OWNED -> text("map_status_target_owned", "目標已屬於我方。");
            case TARGET_NOT_CONNECTED -> text("map_status_not_adjacent", "目標與我方城池不相鄰。");
            case INSUFFICIENT_GOLD -> text("city_status_insufficient_gold", "金不足，無法執行此命令。");
            case INSUFFICIENT_FOOD -> text("city_status_insufficient_food", "糧不足，無法執行此命令。");
            case INVALID_EXPEDITION_AMOUNT -> text("map_status_invalid_expedition_amount", "派兵數量須至少 200，且為 100 的倍數。");
            case INSUFFICIENT_TROOPS -> text("map_status_insufficient_troops", "至少需保留 200 守軍並派出 200 兵。");
            case ARMY_ALREADY_ACTIVE -> text("map_status_army_active", "我方已有一支部隊行軍中，需等待其抵達。");
            case NONE -> text("map_status_action_failed", "命令未完成。");
        };
    }

    private void requestEndMonth() {
        GameState gameState = requireCurrentState();
        if (gameState == null || gameState.gameplayStatus != GameplayStatus.ACTIVE) {
            return;
        }
        label("end_month_confirm_title_label").setText(
            text(
                "end_month_confirm_title_format",
                "確定結束 {0} 年 {1} 月？",
                gameState.currentYear,
                gameState.currentMonth
            )
        );
        label("end_month_confirm_description_label").setText(
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
        try {
            String selectedCityId = SangoServices.session().getSelectedCityId();
            TurnResolutionResult resolutionResult = monthEndFlowController.endCurrentMonth();
            TurnResolutionReport report = resolutionResult.getReport();
            SangoServices.session().openMonthReport(report, ScreenId.STRATEGIC_MAP);
            SangoServices.audio().playSound(SoundEffect.END_MONTH);
            refreshView();
            String battleReportId = findBattleAtCity(report, selectedCityId);
            if (battleReportId != null) {
                prepareBattlePrompt(battleReportId);
            } else {
                Sui.screens.set(ScreenId.MONTH_REPORT);
            }
        } catch (RuntimeException exception) {
            Gdx.app.error("StrategicMap", "結束月份失敗。", exception);
            setStatus(text("city_status_save_failed", "存檔失敗，因此月份沒有推進。"), STATUS_ERROR_COLOR);
            SangoServices.audio().playSound(SoundEffect.COMMAND_ERROR);
            refreshView();
        }
    }

    private String findBattleAtCity(TurnResolutionReport report, String cityId) {
        GameState gameState = SangoServices.session().requireCurrentState();
        for (String battleReportId : report.getBattleReportIds()) {
            BattleReport battleReport = gameState.findBattleReport(battleReportId);
            if (battleReport != null
                && battleReport.involvesFaction(gameState.playerFactionId)
                && cityId.equals(battleReport.targetCityId)) {
                return battleReportId;
            }
        }
        return null;
    }

    private void prepareBattlePrompt(String battleReportId) {
        BattleReport battleReport = SangoServices.session().requireCurrentState()
            .requireBattleReport(battleReportId);
        SangoServices.session().openBattleReport(battleReportId, ScreenId.MONTH_REPORT);
        label("battle_prompt_title_label").setText(
            text("battle_prompt_title_format", "{0} 發生戰鬥", cityName(battleReport.targetCityId))
        );
        label("battle_prompt_description_label").setText(
            text("battle_prompt_description", "上個月所選城池發生戰事。是否立即觀看完整戰報？")
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

    private void showLastTurnReport() {
        if (SangoServices.session().getLastTurnReport() == null) {
            return;
        }
        SangoServices.session().openMonthReport(
            SangoServices.session().getLastTurnReport(),
            ScreenId.STRATEGIC_MAP
        );
        Sui.screens.set(ScreenId.MONTH_REPORT);
    }

    private void viewSelectedCityBattle() {
        GameState gameState = requireCurrentState();
        if (gameState == null) {
            return;
        }
        BattleReport latestReport = BattleReportCatalog.latestForCity(
            gameState,
            SangoServices.session().getSelectedCityId()
        );
        if (latestReport == null) {
            return;
        }
        SangoServices.session().openBattleReport(latestReport.battleId, ScreenId.STRATEGIC_MAP);
        Sui.screens.set(ScreenId.BATTLE_REPORT);
    }

    private void viewWorldBattleReports() {
        Sui.screens.set(ScreenId.WORLD_BATTLE_REPORT);
    }

    private void showMapHelp() {
        contextHelpOverlay.show(
            text("help_map_title", "戰略圖操作說明"),
            text(
                "help_map_body",
                "金與糧由整個勢力共用；行動力（AP）限制本月命令。各勢力同月規劃，本方操作立即反映，其他勢力只看上月底城市快照。\n\n城池之間必須有道路才能偵察、出征或運兵。偵察取得三個月且包含當月的快照，但敵方目前的防守方針始終不可見。\n\n攻方使用強攻、誘敵、穩進；守方使用迎擊、設伏、據守。強攻剋據守、穩進剋設伏、誘敵剋迎擊。\n\n桌機滑鼠停在敵城約 0.45 秒才顯示同勢力提示。雙擊城池切換同勢力持續高亮，改選其他勢力會取消；雙擊空白處才切換全螢幕。"
            )
        );
        SangoServices.audio().playSound(SoundEffect.UI_CLICK);
    }

    private void showDispatchHelp() {
        GameState currentState = requireCurrentState();
        if (currentState == null || pendingTargetCityId == null) {
            return;
        }
        if (isPendingTransfer(currentState)) {
            contextHelpOverlay.show(
                text("help_transfer_title", "運兵說明"),
                text(
                    "help_transfer_body",
                    "運兵只在我方城池之間進行；任何沿道路可到達目標的我方城都能作為來源。\n\n使用前一座／後一座切換來源，再以 100 人調整兵數。每次至少派 200 兵，來源城必須保留 200 守軍。\n\n運兵消耗 1 AP 與 100 糧；路程依道路最短時間計算，部隊抵達後才加入目標城。"
                )
            );
        } else {
            contextHelpOverlay.show(
                text("help_expedition_title", "聯合出征說明"),
                text(
                    "help_expedition_body",
                    "選擇敵方或中立目標城，再逐城加入直接相鄰的我方來源城。每城至少派 200 兵並保留 200 守軍，每個來源消耗 1 AP 與 100 糧。\n\n出征方針可選強攻、誘敵或穩進；另設定道路接戰獲勝後繼續攻城、自動判斷或返城。士氣低於 40 仍可出兵，只是自動判斷條件之一。\n\n敵對部隊在同一道路相向且本月路程交會時先接戰；敗方與平手返城，勝方依命令行動。"
                )
            );
        }
        SangoServices.audio().playSound(SoundEffect.UI_CLICK);
    }

    private void openSettings() {
        SangoServices.audio().playSound(SoundEffect.UI_CLICK);
        SangoServices.session().openSettings(ScreenId.STRATEGIC_MAP);
        Sui.screens.set(ScreenId.SETTINGS);
    }

    private GameState requireCurrentState() {
        if (!SangoServices.session().hasCurrentState()) {
            setStatus(text("map_status_load_failed", "目前沒有可用戰局。"), STATUS_ERROR_COLOR);
            return null;
        }
        return SangoServices.session().requireCurrentState();
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
        setVisible(expeditionDispatchMask, false);
    }

    private boolean isAnyModalVisible() {
        return isVisible(endMonthConfirmMask)
            || isVisible(battlePromptMask)
            || isVisible(expeditionDispatchMask);
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

    private void setAllGameplayButtonsEnabled(boolean enabled) {
        setButtonEnabled(button("defense_feint_button"), enabled);
        setButtonEnabled(button("defense_assault_button"), enabled);
        setButtonEnabled(button("defense_hold_button"), enabled);
        setButtonEnabled(button("manage_city_button"), enabled);
        setButtonEnabled(button("scout_city_button"), enabled);
        setButtonEnabled(button("launch_expedition_button"), enabled);
        setButtonEnabled(button("view_city_battle_button"), enabled);
        setButtonEnabled(button("show_unread_battle_button"), enabled);
        setButtonEnabled(button("end_month_button"), enabled);
    }

    private void setStatus(String message, Color color) {
        currentStatusMessage = message;
        currentStatusColor = color;
        refreshStatusLabel();
    }

    private void refreshStatusLabel() {
        Label statusLabel = label("map_status_label");
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

    private String defensePolicyName(DefensePolicy policy) {
        if (policy == null) {
            return text("defense_policy_unknown", "未知");
        }
        return switch (policy) {
            case BALANCED, FEINT -> text("defense_policy_feint", "設伏");
            case AGGRESSIVE, ASSAULT -> text("defense_policy_assault", "迎擊");
            case HOLD -> text("defense_policy_hold", "據守");
        };
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
        return Sui.i18n.manager().getText("literal", entryName, fallback, arguments)
            .replace("\\n", "\n");
    }
}
