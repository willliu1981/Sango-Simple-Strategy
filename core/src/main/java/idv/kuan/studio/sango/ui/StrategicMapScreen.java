package idv.kuan.studio.sango.ui;

import java.text.NumberFormat;
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
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;

import idv.kuan.studio.libgdx.simpleui.Sui;
import idv.kuan.studio.libgdx.simpleui.SuiScreen;
import idv.kuan.studio.libgdx.simpleui.builder.BuiltUI;
import idv.kuan.studio.sango.application.command.LaunchExpeditionCommand;
import idv.kuan.studio.sango.application.result.StrategicActionResult;
import idv.kuan.studio.sango.application.result.TurnResolutionReport;
import idv.kuan.studio.sango.application.result.TurnResolutionResult;
import idv.kuan.studio.sango.ui.support.ScreenMusic;
import idv.kuan.studio.sango.audio.SoundEffect;
import idv.kuan.studio.sango.data.SangoPreferences;
import idv.kuan.studio.sango.domain.definition.CityDefinition;
import idv.kuan.studio.sango.domain.definition.FactionDefinition;
import idv.kuan.studio.sango.domain.definition.MapCityNodeDefinition;
import idv.kuan.studio.sango.domain.definition.ScenarioDefinition;
import idv.kuan.studio.sango.domain.definition.StrategicMapDefinition;
import idv.kuan.studio.sango.domain.model.ArmyState;
import idv.kuan.studio.sango.domain.model.BattleReport;
import idv.kuan.studio.sango.domain.model.CityState;
import idv.kuan.studio.sango.domain.model.GameState;
import idv.kuan.studio.sango.domain.model.GameplayStatus;
import idv.kuan.studio.sango.domain.model.ScenarioObjectiveStatus;
import idv.kuan.studio.sango.domain.rule.BattleTactic;
import idv.kuan.studio.sango.domain.rule.StrategicActionFailureReason;
import idv.kuan.studio.sango.runtime.SangoServices;
import idv.kuan.studio.sango.ui.flow.MonthEndFlowController;
import idv.kuan.studio.sango.ui.id.ScreenId;
import idv.kuan.studio.sango.ui.support.ScreenBackground;
import idv.kuan.studio.sango.ui.support.MapTerrainBackground;
import idv.kuan.studio.sango.ui.support.NationalOrderTextFormatter;
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
    private static final float MAP_FALLBACK_WIDTH = 1030f;
    private static final float MAP_FALLBACK_HEIGHT = 505f;

    private final ScreenBackground screenBackground = new ScreenBackground();
    private final MapTerrainBackground mapTerrainBackground = new MapTerrainBackground();
    private final NumberFormat numberFormat = NumberFormat.getIntegerInstance(Locale.TAIWAN);
    private final MonthEndFlowController monthEndFlowController = new MonthEndFlowController();

    private Actor endMonthConfirmMask;
    private Actor battlePromptMask;
    private Group mapHost;
    private StrategicMapWidget strategicMapWidget;
    private String currentStatusMessage;
    private Color currentStatusColor = STATUS_NORMAL_COLOR;

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
        mapHost = ui.getActor("map_host", Group.class);
        strategicMapWidget = new StrategicMapWidget(
            label("map_font_probe").getStyle().font,
            this::selectCity
        );
        mapHost.addActor(strategicMapWidget);
        resizeMapWidget();
        applyStyles();
        bindActions();
        currentStatusMessage = text("map_status_ready", "選擇城池以管理、偵察或出征。");
        animateEntrance();
    }

    @Override
    protected void afterShow() {
        if (ui == null) {
            return;
        }
        closeModals();
        ensureCurrentGameState();
        ScreenMusic.play(ScreenId.STRATEGIC_MAP);
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
    public void pause() {
        saveSilently();
    }

    @Override
    protected void afterResize(int width, int height) {
        screenBackground.resize(stage);
        resizeMapWidget();
    }

    @Override
    protected void beforeDispose() {
        saveSilently();
        screenBackground.remove();
        if (strategicMapWidget != null) {
            strategicMapWidget.setTerrainDrawable(null);
        }
        mapTerrainBackground.dispose();
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
        float mapWidth = mapHost.getWidth() > 0f ? mapHost.getWidth() : MAP_FALLBACK_WIDTH;
        float mapHeight = mapHost.getHeight() > 0f ? mapHost.getHeight() : MAP_FALLBACK_HEIGHT;
        strategicMapWidget.setBounds(0f, 0f, mapWidth, mapHeight);
        strategicMapWidget.invalidateHierarchy();
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
        SangoUiStyles.applySecondaryButton(button("show_last_report_button"));
        SangoUiStyles.applyDangerButton(button("show_unread_battle_button"));
        SangoUiStyles.applyPrimaryButton(button("end_month_button"));
        SangoUiStyles.applySecondaryButton(button("end_month_cancel_button"));
        SangoUiStyles.applyPrimaryButton(button("end_month_confirm_button"));
        SangoUiStyles.applySecondaryButton(button("battle_prompt_later_button"));
        SangoUiStyles.applyDangerButton(button("battle_prompt_view_button"));
        refreshTacticStyles();
    }

    private void bindActions() {
        ui.onClick("map_zoom_in_button", () -> strategicMapWidget.zoomBy(1.25f));
        ui.onClick("map_zoom_out_button", () -> strategicMapWidget.zoomBy(0.8f));
        ui.onClick("map_fit_button", strategicMapWidget::fitAll);
        ui.onClick("map_focus_button", strategicMapWidget::focusSelectedCity);
        ui.onClick("manage_city_button", this::openSelectedCity);
        ui.onClick("scout_city_button", this::scoutSelectedCity);
        ui.onClick("launch_expedition_button", this::launchExpedition);
        ui.onClick("view_city_battle_button", this::viewSelectedCityBattle);
        ui.onClick("tactic_balanced_button", () -> selectTactic(BattleTactic.BALANCED));
        ui.onClick("tactic_assault_button", () -> selectTactic(BattleTactic.ASSAULT));
        ui.onClick("tactic_cautious_button", () -> selectTactic(BattleTactic.CAUTIOUS));
        ui.onClick("map_settings_button", this::openSettings);
        ui.onClick("show_last_report_button", this::showLastTurnReport);
        ui.onClick("show_unread_battle_button", this::viewFirstUnreadBattle);
        ui.onClick("end_month_button", this::requestEndMonth);
        ui.onClick("end_month_cancel_button", this::closeModals);
        ui.onClick("end_month_confirm_button", this::confirmEndMonth);
        ui.onClick("battle_prompt_later_button", this::openMonthReportAfterBattlePrompt);
        ui.onClick("battle_prompt_view_button", this::openPromptedBattleReport);
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
            GameState gameState = SangoServices.saveGames().load(slotNumber);
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

    private void selectTactic(BattleTactic battleTactic) {
        SangoServices.session().setSelectedBattleTactic(battleTactic);
        SangoServices.audio().playSound(SoundEffect.UI_CLICK);
        refreshTacticStyles();
        if (SangoServices.session().hasCurrentState()) {
            refreshSelectedCityPanel(SangoServices.session().requireCurrentState());
        }
    }

    private void refreshTacticStyles() {
        applyTacticStyle("tactic_balanced_button", BattleTactic.BALANCED);
        applyTacticStyle("tactic_assault_button", BattleTactic.ASSAULT);
        applyTacticStyle("tactic_cautious_button", BattleTactic.CAUTIOUS);
    }

    private void applyTacticStyle(String actorId, BattleTactic battleTactic) {
        TextButton tacticButton = button(actorId);
        if (SangoServices.session().getSelectedBattleTactic() == battleTactic) {
            SangoUiStyles.applySelectedButton(tacticButton);
        } else {
            SangoUiStyles.applySecondaryButton(tacticButton);
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
        int unreadBattleCount = gameState.countUnreadBattleReports();
        button("show_unread_battle_button").setText(
            text("button_unread_battles_format", "未讀戰報（{0}）", unreadBattleCount)
        );
        setButtonEnabled(button("show_unread_battle_button"), unreadBattleCount > 0);
        setButtonEnabled(
            button("end_month_button"),
            gameState.gameplayStatus == GameplayStatus.ACTIVE
        );
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
        int unreadBattleCount = gameState.countUnreadBattleReports();
        label("map_unread_battle_label").setText(
            unreadBattleCount > 0
                ? text(
                    "map_unread_battle_alert_format",
                    "戰事通報：有 {0} 份未讀戰報；發生戰鬥的城池正在閃爍。",
                    unreadBattleCount
                )
                : text("map_unread_battle_none", "戰事通報：目前沒有未讀戰報。")
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
        for (BattleReport battleReport : gameState.battleReports) {
            if (!battleReport.read) {
                unreadBattlesByCityId.merge(battleReport.targetCityId, 1, Integer::sum);
            }
        }
        for (MapCityNodeDefinition nodeDefinition : mapDefinition.nodes) {
            CityState cityState = gameState.requireCityState(nodeDefinition.cityId);
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
        boolean exactIntel = hasExactIntel(gameState, selectedCityState);
        CityState originCityState = findAdjacentPlayerCity(gameState, selectedCityState.cityId);

        label("selected_city_name_label").setText(cityName(selectedCityState.cityId));
        label("selected_city_owner_label").setText(
            text("map_owner_prefix", "所屬：")
                + localized(ownerDefinition.nameKey, ownerDefinition.id)
        );
        label("selected_city_intel_label").setText(
            buildIntelligenceText(gameState, selectedCityState, exactIntel)
        );
        label("selected_city_stats_label").setText(
            buildSelectedCityStats(selectedCityState, exactIntel)
        );
        label("selected_city_route_label").setText(
            buildRouteText(gameState, selectedCityState, originCityState)
        );

        int cityUnreadCount = gameState.countUnreadBattleReportsForCity(selectedCityState.cityId);
        button("view_city_battle_button").setText(
            text("button_city_battles_format", "查看此城戰報（{0}）", cityUnreadCount)
        );
        setButtonEnabled(button("view_city_battle_button"), cityUnreadCount > 0);

        boolean playerOwned = gameState.playerFactionId.equals(selectedCityState.ownerFactionId);
        boolean gameplayActive = gameState.gameplayStatus == GameplayStatus.ACTIVE;
        setButtonEnabled(button("manage_city_button"), gameplayActive && playerOwned);
        setButtonEnabled(
            button("scout_city_button"),
            gameplayActive && !playerOwned && originCityState != null
                && gameState.actionPointsRemaining >= 1
        );

        int dispatchTroops = originCityState == null
            ? 0
            : SangoServices.launchExpeditionCommand().calculateDispatchTroops(originCityState);
        button("launch_expedition_button").setText(
            dispatchTroops > 0
                ? text("button_launch_expedition_format", "出征（{0} 兵）", dispatchTroops)
                : text("button_launch_expedition", "出征")
        );
        boolean expeditionEnabled = gameplayActive
            && !playerOwned
            && originCityState != null
            && dispatchTroops >= LaunchExpeditionCommand.MINIMUM_EXPEDITION
            && gameState.actionPointsRemaining >= 1
            && gameState.requirePlayerFactionState().food >= LaunchExpeditionCommand.FOOD_COST
            && !gameState.hasArmyForFaction(gameState.playerFactionId);
        setButtonEnabled(button("launch_expedition_button"), expeditionEnabled);
    }

    private String buildIntelligenceText(
        GameState gameState,
        CityState cityState,
        boolean exactIntel
    ) {
        if (exactIntel) {
            return text(
                "map_intel_exact_format",
                "兵力：{0}｜人口：{1}｜情報：已掌握",
                numberFormat.format(cityState.troops),
                numberFormat.format(cityState.population)
            );
        }
        int lowerBound = Math.max(0, cityState.troops * 75 / 100 / 100 * 100);
        int upperBound = Math.max(100, (cityState.troops * 125 / 100 + 99) / 100 * 100);
        return text(
            "map_intel_estimate_format",
            "兵力：約 {0}～{1}｜情報不足；偵察可取得三回合精確情報。",
            numberFormat.format(lowerBound),
            numberFormat.format(upperBound)
        );
    }

    private String buildSelectedCityStats(CityState cityState, boolean exactIntel) {
        if (!exactIntel) {
            return text("map_stats_unknown", "城防、訓練、士氣與內政狀態尚未掌握。");
        }
        return text(
            "map_stats_format",
            "城防 {0}｜訓練 {1}｜農業 {2}｜商業 {3}｜治水 {4}｜士氣 {5}",
            cityState.defense,
            cityState.training,
            cityState.agriculture,
            cityState.commerce,
            cityState.waterControl,
            cityState.morale
        );
    }

    private String buildRouteText(
        GameState gameState,
        CityState selectedCityState,
        CityState originCityState
    ) {
        if (gameState.playerFactionId.equals(selectedCityState.ownerFactionId)) {
            return text("map_route_owned", "此城屬於我方；可進入內政畫面投資或整備。");
        }
        if (originCityState == null) {
            return text("map_route_not_adjacent", "目前沒有與此城直接相鄰的我方城池。");
        }
        return text(
            "map_route_format",
            "可由 {0} 沿道路進軍，行程 1 個月。出征消耗 1 行動力與 100 糧。",
            cityName(originCityState.cityId)
        );
    }

    private boolean hasExactIntel(GameState gameState, CityState cityState) {
        return gameState.playerFactionId.equals(cityState.ownerFactionId)
            || cityState.scoutedUntilTurn >= gameState.currentTurn;
    }

    private CityState findAdjacentPlayerCity(GameState gameState, String targetCityId) {
        StrategicMapDefinition mapDefinition = SangoServices.definitions().requireMap(gameState.mapId);
        CityState bestOrigin = null;
        int largestDispatch = -1;
        for (CityState playerCity : gameState.findCitiesOwnedBy(gameState.playerFactionId)) {
            if (mapDefinition.findConnection(playerCity.cityId, targetCityId) == null) {
                continue;
            }
            int dispatch = SangoServices.launchExpeditionCommand().calculateDispatchTroops(playerCity);
            if (dispatch > largestDispatch) {
                bestOrigin = playerCity;
                largestDispatch = dispatch;
            }
        }
        return bestOrigin;
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
        CityState originCityState = findAdjacentPlayerCity(currentState, targetCityId);
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

    private void launchExpedition() {
        GameState currentState = requireCurrentState();
        if (currentState == null) {
            return;
        }
        String targetCityId = SangoServices.session().getSelectedCityId();
        CityState originCityState = findAdjacentPlayerCity(currentState, targetCityId);
        if (originCityState == null) {
            setStatus(text("map_status_not_adjacent", "沒有可出征的相鄰我方城池。"), STATUS_ERROR_COLOR);
            return;
        }
        try {
            StrategicActionResult result = SangoServices.launchExpeditionCommand().execute(
                SangoServices.session().getCurrentSaveSlot(),
                currentState,
                originCityState.cityId,
                targetCityId,
                SangoServices.session().getSelectedBattleTactic()
            );
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
                    text("map_status_scout_success", "偵察完成；精確情報可維持三個回合。"),
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
            case INSUFFICIENT_TROOPS -> text("map_status_insufficient_troops", "至少需保留 400 守軍並派出 400 兵。");
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
                "剩餘行動力：{0} / {1}\n月底將結算軍糧、季節收入、行軍、戰鬥與敵軍行動。未使用的行動力不會保留。",
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
        label("battle_prompt_title_label").setText(
            text("battle_prompt_title_format", "{0} 發生戰鬥", cityName(battleReport.targetCityId))
        );
        label("battle_prompt_description_label").setText(
            text("battle_prompt_description", "本月所選城池發生戰事。是否立即觀看完整戰報？")
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
        List<BattleReport> reports = gameState.findUnreadBattleReportsForCity(
            SangoServices.session().getSelectedCityId()
        );
        if (reports.isEmpty()) {
            return;
        }
        SangoServices.session().openBattleReport(reports.get(0).battleId, ScreenId.STRATEGIC_MAP);
        Sui.screens.set(ScreenId.BATTLE_REPORT);
    }

    private void viewFirstUnreadBattle() {
        GameState gameState = requireCurrentState();
        if (gameState == null) {
            return;
        }
        List<BattleReport> reports = gameState.findUnreadBattleReports();
        if (reports.isEmpty()) {
            return;
        }
        SangoServices.session().openBattleReport(reports.get(0).battleId, ScreenId.STRATEGIC_MAP);
        Sui.screens.set(ScreenId.BATTLE_REPORT);
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
                Gdx.app.error("StrategicMap", "背景保存戰局失敗。", exception);
            }
        }
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

    private void setAllGameplayButtonsEnabled(boolean enabled) {
        setButtonEnabled(button("manage_city_button"), enabled);
        setButtonEnabled(button("scout_city_button"), enabled);
        setButtonEnabled(button("launch_expedition_button"), enabled);
        setButtonEnabled(button("view_city_battle_button"), enabled);
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
