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
import idv.kuan.studio.sango.application.result.TurnEvent;
import idv.kuan.studio.sango.application.result.TurnEventType;
import idv.kuan.studio.sango.application.result.TurnResolutionReport;
import idv.kuan.studio.sango.application.result.TurnResolutionResult;
import idv.kuan.studio.sango.domain.definition.CityDefinition;
import idv.kuan.studio.sango.domain.definition.FactionDefinition;
import idv.kuan.studio.sango.domain.definition.MapCityNodeDefinition;
import idv.kuan.studio.sango.domain.definition.ScenarioDefinition;
import idv.kuan.studio.sango.domain.definition.StrategicMapDefinition;
import idv.kuan.studio.sango.domain.model.ArmyState;
import idv.kuan.studio.sango.domain.model.CampaignStatus;
import idv.kuan.studio.sango.domain.model.CityState;
import idv.kuan.studio.sango.domain.model.GameState;
import idv.kuan.studio.sango.domain.rule.BattleTactic;
import idv.kuan.studio.sango.domain.rule.StrategicActionFailureReason;
import idv.kuan.studio.sango.runtime.SangoServices;
import idv.kuan.studio.sango.ui.id.ScreenId;
import idv.kuan.studio.sango.ui.support.ScreenBackground;
import idv.kuan.studio.sango.ui.theme.MapNodeTone;
import idv.kuan.studio.sango.ui.theme.SangoUiStyles;
import idv.kuan.studio.sango.ui.widget.StrategicMapWidget;

/**
 * 六城節點式戰略地圖，也是內政、偵察、出征與月份推進的主畫面。
 */
public final class StrategicMapScreen extends SuiScreen {
    private static final String BACKGROUND_PATH = "picture/lobby/sango_lobby_background.png";
    private static final Color STATUS_NORMAL_COLOR = new Color(0.79f, 0.72f, 0.61f, 1f);
    private static final Color STATUS_SUCCESS_COLOR = new Color(0.94f, 0.76f, 0.38f, 1f);
    private static final Color STATUS_ERROR_COLOR = new Color(0.95f, 0.43f, 0.30f, 1f);
    private static final float MAP_FALLBACK_WIDTH = 1030f;
    private static final float MAP_FALLBACK_HEIGHT = 505f;

    private final ScreenBackground screenBackground = new ScreenBackground();
    private final NumberFormat numberFormat = NumberFormat.getIntegerInstance(Locale.TAIWAN);

    private Actor turnReportMask;
    private Group mapHost;
    private StrategicMapWidget strategicMapWidget;
    private TurnResolutionReport lastTurnReport;
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
        turnReportMask = attachModalMask("turn_report_mask");
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
        closeTurnReport();
        ensureCurrentGameState();
        refreshView();
    }

    @Override
    protected InputProcessor createInputProcessor(Stage stage) {
        InputAdapter navigationInput = new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                if (keycode == Input.Keys.BACK || keycode == Input.Keys.ESCAPE) {
                    if (turnReportMask != null && turnReportMask.isVisible()) {
                        closeTurnReport();
                    } else {
                        returnToLobby();
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
        SangoUiStyles.applySecondaryButton(button("manage_city_button"));
        SangoUiStyles.applySecondaryButton(button("scout_city_button"));
        SangoUiStyles.applyPrimaryButton(button("launch_expedition_button"));
        SangoUiStyles.applySecondaryButton(button("map_save_button"));
        SangoUiStyles.applySecondaryButton(button("map_return_lobby_button"));
        SangoUiStyles.applySecondaryButton(button("show_last_report_button"));
        SangoUiStyles.applyPrimaryButton(button("end_month_button"));
        SangoUiStyles.applySecondaryButton(button("report_close_button"));
        SangoUiStyles.applySecondaryButton(button("report_lobby_button"));
        refreshTacticStyles();
    }

    private void bindActions() {
        ui.onClick("manage_city_button", this::openSelectedCity);
        ui.onClick("scout_city_button", this::scoutSelectedCity);
        ui.onClick("launch_expedition_button", this::launchExpedition);
        ui.onClick("tactic_balanced_button", () -> selectTactic(BattleTactic.BALANCED));
        ui.onClick("tactic_assault_button", () -> selectTactic(BattleTactic.ASSAULT));
        ui.onClick("tactic_cautious_button", () -> selectTactic(BattleTactic.CAUTIOUS));
        ui.onClick("map_save_button", this::saveManually);
        ui.onClick("map_return_lobby_button", this::returnToLobby);
        ui.onClick("show_last_report_button", this::showLastTurnReport);
        ui.onClick("end_month_button", this::endMonth);
        ui.onClick("report_close_button", this::closeTurnReport);
        ui.onClick("report_lobby_button", this::returnToLobby);
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
        try {
            GameState gameState = SangoServices.saveGames().load(
                SangoServices.DEFAULT_SAVE_SLOT
            );
            SangoServices.session().setCurrentState(gameState);
        } catch (RuntimeException exception) {
            Gdx.app.error("StrategicMap", "無法載入目前戰局。", exception);
            currentStatusMessage = text(
                "map_status_load_failed",
                "無法載入戰局；請返回 Lobby 並建立新局。"
            );
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
        refreshView();
    }

    private void selectTactic(BattleTactic battleTactic) {
        SangoServices.session().setSelectedBattleTactic(battleTactic);
        refreshTacticStyles();
        refreshSelectedCityPanel(SangoServices.session().requireCurrentState());
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
        label("map_name_label").setText(
            localized(mapDefinition.nameKey, mapDefinition.id)
        );
        refreshObjectiveLabel(gameState);
        refreshMapWidget(gameState, mapDefinition, selectedCityId);
        refreshSelectedCityPanel(gameState);
        refreshArmySummary(gameState);
        refreshStatusLabel();
        setButtonEnabled(
            button("show_last_report_button"),
            lastTurnReport != null && !lastTurnReport.isEmpty()
        );
        setButtonEnabled(
            button("end_month_button"),
            gameState.campaignStatus == CampaignStatus.IN_PROGRESS
        );
    }

    private String ensureSelectedCity(GameState gameState) {
        String selectedCityId = SangoServices.session().getSelectedCityId();
        if (selectedCityId == null || gameState.findCityState(selectedCityId) == null) {
            selectedCityId = gameState.requirePlayerFactionState().active
                ? gameState.requirePlayerFactionState().capitalCityId
                : gameState.victoryTargetCityId;
            SangoServices.session().setSelectedCityId(selectedCityId);
        }
        return selectedCityId;
    }

    private void refreshObjectiveLabel(GameState gameState) {
        String targetCityName = cityName(gameState.victoryTargetCityId);
        int remainingMonths = Math.max(0, gameState.turnLimitMonths - gameState.elapsedMonths);
        if (gameState.campaignStatus == CampaignStatus.VICTORY) {
            label("map_objective_label").setText(
                text("map_objective_victory", "勝利：已攻下目標城池。")
            );
        } else if (gameState.campaignStatus == CampaignStatus.DEFEAT) {
            label("map_objective_label").setText(
                text("map_objective_defeat", "戰役失敗；可查看最後局勢或建立新局。")
            );
        } else {
            label("map_objective_label").setText(
                text("map_objective_format", "目標：攻下 {0}｜期限剩餘 {1} 個月",
                    targetCityName,
                    remainingMonths)
            );
        }
    }

    private void refreshMapWidget(
        GameState gameState,
        StrategicMapDefinition mapDefinition,
        String selectedCityId
    ) {
        Map<String, String> captionsByCityId = new LinkedHashMap<>();
        Map<String, MapNodeTone> tonesByCityId = new LinkedHashMap<>();
        for (MapCityNodeDefinition nodeDefinition : mapDefinition.nodes) {
            CityState cityState = gameState.requireCityState(nodeDefinition.cityId);
            MapNodeTone nodeTone = toneForOwner(gameState, cityState.ownerFactionId);
            String marker = markerForTone(nodeTone);
            if (gameState.victoryTargetCityId.equals(cityState.cityId)) {
                marker += "・" + text("map_marker_target", "目標");
            }
            captionsByCityId.put(
                cityState.cityId,
                cityName(cityState.cityId) + "\n" + marker
            );
            tonesByCityId.put(cityState.cityId, nodeTone);
        }
        strategicMapWidget.setMapData(
            mapDefinition,
            captionsByCityId,
            tonesByCityId,
            selectedCityId
        );
        resizeMapWidget();
    }

    private MapNodeTone toneForOwner(GameState gameState, String ownerFactionId) {
        if (gameState.playerFactionId.equals(ownerFactionId)) {
            return MapNodeTone.PLAYER;
        }
        if (gameState.opponentFactionId.equals(ownerFactionId)) {
            return MapNodeTone.ENEMY;
        }
        return MapNodeTone.NEUTRAL;
    }

    private String markerForTone(MapNodeTone nodeTone) {
        if (nodeTone == MapNodeTone.PLAYER) {
            return text("map_marker_player", "我方");
        }
        if (nodeTone == MapNodeTone.ENEMY) {
            return text("map_marker_enemy", "敵軍");
        }
        return text("map_marker_neutral", "中立");
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

        boolean playerOwned = gameState.playerFactionId.equals(
            selectedCityState.ownerFactionId
        );
        boolean campaignActive = gameState.campaignStatus == CampaignStatus.IN_PROGRESS;
        setButtonEnabled(button("manage_city_button"), playerOwned);
        setButtonEnabled(
            button("scout_city_button"),
            campaignActive && !playerOwned && originCityState != null
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
        boolean expeditionEnabled = campaignActive
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
            return text(
                "map_stats_unknown",
                "城防、訓練與內政狀態尚未掌握。"
            );
        }
        return text(
            "map_stats_format",
            "城防 {0}｜訓練 {1}｜農業 {2}｜商業 {3}｜治水 {4}",
            cityState.defense,
            cityState.training,
            cityState.agriculture,
            cityState.commerce,
            cityState.waterControl
        );
    }

    private String buildRouteText(
        GameState gameState,
        CityState selectedCityState,
        CityState originCityState
    ) {
        if (gameState.playerFactionId.equals(selectedCityState.ownerFactionId)) {
            return text(
                "map_route_owned",
                "此城屬於我方；可進入內政畫面投資或整備。"
            );
        }
        if (originCityState == null) {
            return text(
                "map_route_not_adjacent",
                "目前沒有與此城直接相鄰的我方城池，無法偵察或出征。"
            );
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
        StrategicMapDefinition mapDefinition = SangoServices.definitions().requireMap(
            gameState.mapId
        );
        List<CityState> playerCities = gameState.findCitiesOwnedBy(gameState.playerFactionId);
        String capitalCityId = gameState.requirePlayerFactionState().capitalCityId;
        for (CityState playerCityState : playerCities) {
            if (playerCityState.cityId.equals(capitalCityId)
                && mapDefinition.findConnection(playerCityState.cityId, targetCityId) != null) {
                return playerCityState;
            }
        }
        for (CityState playerCityState : playerCities) {
            if (mapDefinition.findConnection(playerCityState.cityId, targetCityId) != null) {
                return playerCityState;
            }
        }
        return null;
    }

    private void refreshArmySummary(GameState gameState) {
        if (gameState.armyStates.length == 0) {
            label("map_army_summary_label").setText(
                text(
                    "map_army_none_format",
                    "目前沒有行軍中的部隊。敵軍預估 {0} 個月後完成下一次集結。",
                    gameState.enemyAttackCountdown
                )
            );
            return;
        }
        StringBuilder summaryBuilder = new StringBuilder();
        for (ArmyState armyState : gameState.armyStates) {
            if (summaryBuilder.length() > 0) {
                summaryBuilder.append("　｜　");
            }
            boolean playerArmy = gameState.playerFactionId.equals(armyState.factionId);
            summaryBuilder.append(playerArmy
                ? text("map_army_player_prefix", "我軍")
                : text("map_army_enemy_prefix", "敵軍"));
            summaryBuilder.append(' ')
                .append(numberFormat.format(armyState.troops))
                .append(' ')
                .append(text("map_army_route_word", "兵："))
                .append(cityName(armyState.originCityId))
                .append(" → ")
                .append(cityName(armyState.targetCityId))
                .append("（")
                .append(armyState.remainingTravelMonths)
                .append(text("map_month_remaining_suffix", " 月抵達）"));
        }
        label("map_army_summary_label").setText(summaryBuilder.toString());
    }

    private void openSelectedCity() {
        if (!SangoServices.session().hasCurrentState()) {
            return;
        }
        CityState selectedCityState = SangoServices.session().requireCurrentState()
            .requireCityState(SangoServices.session().getSelectedCityId());
        if (!SangoServices.session().requireCurrentState().playerFactionId.equals(
            selectedCityState.ownerFactionId
        )) {
            return;
        }
        Sui.screens.set(ScreenId.CITY);
    }

    private void scoutSelectedCity() {
        GameState currentState = requireCurrentStateOrReturn();
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
                SangoServices.DEFAULT_SAVE_SLOT,
                currentState,
                originCityState.cityId,
                targetCityId
            );
            applyStrategicActionResult(result, true);
        } catch (RuntimeException exception) {
            Gdx.app.error("StrategicMap", "偵察命令失敗。", exception);
            setStatus(text("map_status_action_failed", "戰略命令失敗，戰局未更新。"), STATUS_ERROR_COLOR);
        }
        refreshView();
    }

    private void launchExpedition() {
        GameState currentState = requireCurrentStateOrReturn();
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
                SangoServices.DEFAULT_SAVE_SLOT,
                currentState,
                originCityState.cityId,
                targetCityId,
                SangoServices.session().getSelectedBattleTactic()
            );
            applyStrategicActionResult(result, false);
        } catch (RuntimeException exception) {
            Gdx.app.error("StrategicMap", "出征命令失敗。", exception);
            setStatus(text("map_status_action_failed", "戰略命令失敗，戰局未更新。"), STATUS_ERROR_COLOR);
        }
        refreshView();
    }

    private void applyStrategicActionResult(
        StrategicActionResult actionResult,
        boolean scouting
    ) {
        if (actionResult.isSuccessful()) {
            SangoServices.session().setCurrentState(actionResult.getGameState());
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
            return;
        }
        setStatus(strategicFailureMessage(actionResult.getFailureReason()), STATUS_ERROR_COLOR);
    }

    private String strategicFailureMessage(StrategicActionFailureReason failureReason) {
        return switch (failureReason) {
            case CAMPAIGN_FINISHED -> text("map_status_campaign_finished", "戰役已結束，不能再下達命令。");
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

    private void endMonth() {
        GameState currentState = requireCurrentStateOrReturn();
        if (currentState == null) {
            return;
        }
        try {
            TurnResolutionResult resolutionResult = SangoServices.endTurnCommand().execute(
                SangoServices.DEFAULT_SAVE_SLOT,
                currentState
            );
            SangoServices.session().setCurrentState(resolutionResult.getGameState());
            lastTurnReport = resolutionResult.getReport();
            setStatus(text("map_status_month_ended", "月份結算完成並已自動存檔。"), STATUS_SUCCESS_COLOR);
            refreshView();
            openTurnReport(lastTurnReport);
        } catch (RuntimeException exception) {
            Gdx.app.error("StrategicMap", "結束月份失敗。", exception);
            setStatus(text("city_status_save_failed", "存檔失敗，因此月份沒有推進。"), STATUS_ERROR_COLOR);
            refreshView();
        }
    }

    private void saveManually() {
        GameState currentState = requireCurrentStateOrReturn();
        if (currentState == null) {
            return;
        }
        try {
            SangoServices.saveCurrentGameCommand().execute(
                SangoServices.DEFAULT_SAVE_SLOT,
                currentState
            );
            setStatus(text("city_status_saved", "戰局已保存。"), STATUS_SUCCESS_COLOR);
        } catch (RuntimeException exception) {
            Gdx.app.error("StrategicMap", "手動存檔失敗。", exception);
            setStatus(text("city_status_save_failed", "戰局保存失敗。"), STATUS_ERROR_COLOR);
        }
        refreshStatusLabel();
    }

    private void showLastTurnReport() {
        if (lastTurnReport != null) {
            openTurnReport(lastTurnReport);
        }
    }

    private void openTurnReport(TurnResolutionReport report) {
        label("report_period_label").setText(
            text(
                "report_period_format",
                "{0} 年 {1} 月結算",
                report.getResolvedYear(),
                report.getResolvedMonth()
            )
        );
        label("report_content_label").setText(formatTurnReport(report));
        turnReportMask.setVisible(true);
        turnReportMask.getColor().a = 0f;
        turnReportMask.toFront();
        turnReportMask.addAction(Actions.fadeIn(0.16f));
    }

    private String formatTurnReport(TurnResolutionReport report) {
        if (report.isEmpty()) {
            return text("report_empty", "本月沒有特殊事件。");
        }
        StringBuilder reportBuilder = new StringBuilder();
        for (TurnEvent turnEvent : report.getEvents()) {
            if (reportBuilder.length() > 0) {
                reportBuilder.append('\n');
            }
            reportBuilder.append("• ").append(formatTurnEvent(turnEvent));
        }
        return reportBuilder.toString();
    }

    private String formatTurnEvent(TurnEvent turnEvent) {
        TurnEventType eventType = turnEvent.getType();
        String cityName = optionalCityName(turnEvent.getCityId());
        String otherCityName = optionalCityName(turnEvent.getOtherCityId());
        boolean playerFactionEvent = SangoServices.session().requireCurrentState().playerFactionId
            .equals(turnEvent.getFactionId());

        return switch (eventType) {
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
                cityName,
                turnEvent.getSecondaryValue(),
                turnEvent.getPrimaryValue()
            );
            case FLOOD_AVOIDED -> text(
                "report_event_flood_avoided",
                "{0} 平安度過汛期（洪災風險 {1}%）。",
                cityName,
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
                cityName,
                otherCityName,
                turnEvent.getSecondaryValue()
            );
            case ARMY_REINFORCED -> text(
                "report_event_reinforced",
                "{0} 獲得 {1} 兵增援。",
                cityName,
                numberFormat.format(turnEvent.getPrimaryValue())
            );
            case BATTLE_ATTACKER_WON -> playerFactionEvent
                ? text(
                    "report_event_player_attack_won",
                    "我軍攻克 {0}；我軍損失 {1}，守軍損失 {2}。",
                    cityName,
                    numberFormat.format(turnEvent.getPrimaryValue()),
                    numberFormat.format(turnEvent.getSecondaryValue())
                )
                : text(
                    "report_event_enemy_attack_won",
                    "敵軍攻下 {0}；敵軍損失 {1}，守軍損失 {2}。",
                    cityName,
                    numberFormat.format(turnEvent.getPrimaryValue()),
                    numberFormat.format(turnEvent.getSecondaryValue())
                );
            case BATTLE_DEFENDER_WON -> playerFactionEvent
                ? text(
                    "report_event_player_defense_won",
                    "我軍守住 {0}；敵軍損失 {1}，我軍損失 {2}。",
                    cityName,
                    numberFormat.format(turnEvent.getPrimaryValue()),
                    numberFormat.format(turnEvent.getSecondaryValue())
                )
                : text(
                    "report_event_player_attack_lost",
                    "我軍進攻 {0} 失敗；我軍損失 {1}，守軍損失 {2}。",
                    cityName,
                    numberFormat.format(turnEvent.getPrimaryValue()),
                    numberFormat.format(turnEvent.getSecondaryValue())
                );
            case CITY_CAPTURED -> text(
                "report_event_city_captured",
                "{0} 的控制權已轉移，現有駐軍 {1}。",
                cityName,
                numberFormat.format(turnEvent.getPrimaryValue())
            );
            case ENEMY_PREPARING -> text(
                "report_event_enemy_preparing",
                "敵軍仍在 {0} 集結，預估 {1} 個月後出征。",
                cityName,
                turnEvent.getPrimaryValue()
            );
            case ENEMY_REINFORCING -> text(
                "report_event_enemy_reinforcing",
                "敵軍在 {0} 補充 {1} 兵，出征延後。",
                cityName,
                numberFormat.format(turnEvent.getPrimaryValue())
            );
            case ENEMY_MARCHING -> text(
                "report_event_enemy_marching",
                "敵軍 {0} 兵已由 {1} 向 {2} 出征，預計 {3} 個月抵達。",
                numberFormat.format(turnEvent.getPrimaryValue()),
                cityName,
                otherCityName,
                turnEvent.getSecondaryValue()
            );
            case CAMPAIGN_VICTORY -> text(
                "report_event_victory",
                "戰役勝利：已攻下目標城池 {0}。",
                cityName
            );
            case CAMPAIGN_DEFEAT_CAPITAL -> text(
                "report_event_defeat_capital",
                "戰役失敗：我方主城 {0} 已失守。",
                cityName
            );
            case CAMPAIGN_DEFEAT_TIMEOUT -> text(
                "report_event_defeat_timeout",
                "戰役失敗：未能在 {0} 個月內攻下目標城池。",
                turnEvent.getPrimaryValue()
            );
        };
    }

    private void closeTurnReport() {
        if (turnReportMask != null) {
            turnReportMask.clearActions();
            turnReportMask.setVisible(false);
            turnReportMask.getColor().a = 1f;
        }
    }

    private GameState requireCurrentStateOrReturn() {
        if (!SangoServices.session().hasCurrentState()) {
            setStatus(text("map_status_load_failed", "目前沒有可用戰局。"), STATUS_ERROR_COLOR);
            return null;
        }
        return SangoServices.session().requireCurrentState();
    }

    private void returnToLobby() {
        saveSilently();
        closeTurnReport();
        Sui.screens.set(ScreenId.LOBBY);
    }

    private void saveSilently() {
        if (!SangoServices.session().hasCurrentState()) {
            return;
        }
        try {
            SangoServices.saveCurrentGameCommand().execute(
                SangoServices.DEFAULT_SAVE_SLOT,
                SangoServices.session().requireCurrentState()
            );
        } catch (RuntimeException exception) {
            if (Gdx.app != null) {
                Gdx.app.error("StrategicMap", "背景保存戰局失敗。", exception);
            }
        }
    }

    private void setAllGameplayButtonsEnabled(boolean enabled) {
        setButtonEnabled(button("manage_city_button"), enabled);
        setButtonEnabled(button("scout_city_button"), enabled);
        setButtonEnabled(button("launch_expedition_button"), enabled);
        setButtonEnabled(button("end_month_button"), enabled);
        setButtonEnabled(button("map_save_button"), enabled);
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

    private String optionalCityName(String cityId) {
        return cityId == null || cityId.isEmpty() ? "" : cityName(cityId);
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
