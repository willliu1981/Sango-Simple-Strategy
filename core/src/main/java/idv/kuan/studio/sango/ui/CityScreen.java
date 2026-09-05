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
import idv.kuan.studio.sango.domain.definition.CityDefinition;
import idv.kuan.studio.sango.domain.definition.FactionDefinition;
import idv.kuan.studio.sango.domain.definition.ScenarioDefinition;
import idv.kuan.studio.sango.domain.model.CityState;
import idv.kuan.studio.sango.domain.model.FactionState;
import idv.kuan.studio.sango.domain.model.GameState;
import idv.kuan.studio.sango.domain.rule.DomesticActionFailureReason;
import idv.kuan.studio.sango.domain.rule.DomesticActionRules;
import idv.kuan.studio.sango.domain.rule.DomesticActionType;
import idv.kuan.studio.sango.repository.save.SaveGameException;
import idv.kuan.studio.sango.runtime.SangoServices;
import idv.kuan.studio.sango.ui.id.ScreenId;
import idv.kuan.studio.sango.ui.support.ScreenBackground;
import idv.kuan.studio.sango.ui.theme.SangoUiStyles;

/**
 * 第一個真正可玩的單城內政回合畫面。
 */
public final class CityScreen extends SuiScreen {
    private static final String BACKGROUND_PATH = "picture/lobby/sango_lobby_background.png";
    private static final Color STATUS_NORMAL_COLOR = new Color(0.79f, 0.72f, 0.61f, 1f);
    private static final Color STATUS_SUCCESS_COLOR = new Color(0.94f, 0.76f, 0.38f, 1f);
    private static final Color STATUS_ERROR_COLOR = new Color(0.95f, 0.43f, 0.30f, 1f);

    private final ScreenBackground screenBackground = new ScreenBackground();
    private final NumberFormat numberFormat = NumberFormat.getIntegerInstance(Locale.TAIWAN);

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
        applyStyles();
        bindActions();
        currentStatusMessage = text(
            "city_status_ready",
            "選擇內政命令；每次成功操作都會自動存檔。"
        );
        animateEntrance();
    }

    @Override
    protected void afterShow() {
        if (ui == null) {
            return;
        }
        currentStatusMessage = text(
            "city_status_ready",
            "選擇內政命令；每次成功操作都會自動存檔。"
        );
        currentStatusColor = STATUS_NORMAL_COLOR;
        ensureCurrentGameState();
        refreshView();
    }

    @Override
    protected InputProcessor createInputProcessor(Stage stage) {
        InputAdapter navigationInput = new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                if (keycode == Input.Keys.BACK || keycode == Input.Keys.ESCAPE) {
                    returnToLobby();
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

    private void applyStyles() {
        SangoUiStyles.applySecondaryButton(button("agriculture_button"));
        SangoUiStyles.applySecondaryButton(button("commerce_button"));
        SangoUiStyles.applySecondaryButton(button("recruit_button"));
        SangoUiStyles.applySecondaryButton(button("train_button"));
        SangoUiStyles.applySecondaryButton(button("save_button"));
        SangoUiStyles.applySecondaryButton(button("return_lobby_button"));
        SangoUiStyles.applyPrimaryButton(button("end_turn_button"));
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
        ui.onClick("recruit_button", () -> executeDomesticAction(DomesticActionType.RECRUIT));
        ui.onClick("train_button", () -> executeDomesticAction(DomesticActionType.TRAIN));
        ui.onClick("save_button", this::saveManually);
        ui.onClick("return_lobby_button", this::returnToLobby);
        ui.onClick("end_turn_button", this::endTurn);
    }

    private void animateEntrance() {
        Actor main = ui.getActor("main");
        main.getColor().a = 0f;
        main.addAction(Actions.fadeIn(0.30f));
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
            Gdx.app.error("City", "無法載入目前戰局。", exception);
            currentStatusMessage = text(
                "city_status_load_failed",
                "無法載入戰局；請返回 Lobby 並建立新局。"
            );
            currentStatusColor = STATUS_ERROR_COLOR;
        }
    }

    private void executeDomesticAction(DomesticActionType actionType) {
        if (!SangoServices.session().hasCurrentState()) {
            showNoGameStateError();
            return;
        }

        try {
            DomesticActionResult result = SangoServices.domesticActionCommand().execute(
                SangoServices.DEFAULT_SAVE_SLOT,
                SangoServices.session().requireCurrentState(),
                actionType
            );
            if (result.isSuccessful()) {
                SangoServices.session().setCurrentState(result.getGameState());
                currentStatusMessage = successMessage(result.getActionType());
                currentStatusColor = STATUS_SUCCESS_COLOR;
            } else {
                currentStatusMessage = failureMessage(result.getFailureReason());
                currentStatusColor = STATUS_ERROR_COLOR;
            }
        } catch (SaveGameException exception) {
            Gdx.app.error("City", "內政命令完成前存檔失敗。", exception);
            currentStatusMessage = text(
                "city_status_save_failed",
                "存檔失敗，因此本次命令沒有套用。"
            );
            currentStatusColor = STATUS_ERROR_COLOR;
        } catch (RuntimeException exception) {
            Gdx.app.error("City", "執行內政命令失敗。", exception);
            currentStatusMessage = text(
                "city_status_action_failed",
                "內政命令執行失敗，戰局狀態未更新。"
            );
            currentStatusColor = STATUS_ERROR_COLOR;
        }
        refreshView();
    }

    private void endTurn() {
        if (!SangoServices.session().hasCurrentState()) {
            showNoGameStateError();
            return;
        }

        try {
            GameState nextState = SangoServices.endTurnCommand().execute(
                SangoServices.DEFAULT_SAVE_SLOT,
                SangoServices.session().requireCurrentState()
            );
            SangoServices.session().setCurrentState(nextState);
            currentStatusMessage = text(
                "city_status_turn_ended",
                "回合已推進一個月份，行動力已恢復並自動存檔。"
            );
            currentStatusColor = STATUS_SUCCESS_COLOR;
        } catch (RuntimeException exception) {
            Gdx.app.error("City", "結束回合失敗。", exception);
            currentStatusMessage = text(
                "city_status_save_failed",
                "存檔失敗，因此本次命令沒有套用。"
            );
            currentStatusColor = STATUS_ERROR_COLOR;
        }
        refreshView();
    }

    private void saveManually() {
        if (!SangoServices.session().hasCurrentState()) {
            showNoGameStateError();
            return;
        }

        try {
            SangoServices.saveCurrentGameCommand().execute(
                SangoServices.DEFAULT_SAVE_SLOT,
                SangoServices.session().requireCurrentState()
            );
            currentStatusMessage = text("city_status_saved", "戰局已保存。");
            currentStatusColor = STATUS_SUCCESS_COLOR;
        } catch (RuntimeException exception) {
            Gdx.app.error("City", "手動存檔失敗。", exception);
            currentStatusMessage = text("city_status_save_failed", "戰局保存失敗。");
            currentStatusColor = STATUS_ERROR_COLOR;
        }
        refreshStatusLabel();
    }

    private void returnToLobby() {
        if (SangoServices.session().hasCurrentState()) {
            try {
                SangoServices.saveCurrentGameCommand().execute(
                    SangoServices.DEFAULT_SAVE_SLOT,
                    SangoServices.session().requireCurrentState()
                );
            } catch (RuntimeException exception) {
                Gdx.app.error("City", "返回 Lobby 前存檔失敗。", exception);
                currentStatusMessage = text(
                    "city_status_return_save_failed",
                    "返回前無法保存戰局；請重試或使用手動存檔。"
                );
                currentStatusColor = STATUS_ERROR_COLOR;
                refreshStatusLabel();
                return;
            }
        }
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
                Gdx.app.error("City", "生命週期自動存檔失敗。", exception);
            }
        }
    }

    private void refreshView() {
        if (!SangoServices.session().hasCurrentState()) {
            setGameplayButtonsEnabled(false);
            refreshStatusLabel();
            return;
        }

        try {
            GameState gameState = SangoServices.session().requireCurrentState();
            FactionState factionState = gameState.requirePlayerFactionState();
            CityState cityState = gameState.requireCapitalCityState();
            ScenarioDefinition scenarioDefinition = SangoServices.definitions().requireScenario(
                gameState.scenarioId
            );
            FactionDefinition factionDefinition = SangoServices.definitions().requireFaction(
                gameState.playerFactionId
            );
            CityDefinition cityDefinition = SangoServices.definitions().requireCity(
                cityState.cityId
            );

            label("scenario_label").setText(
                text("city_scenario_prefix", "劇本：")
                    + localized(scenarioDefinition.nameKey, scenarioDefinition.id)
            );
            label("turn_label").setText(
                text("city_turn_prefix", "回合：第 ")
                    + gameState.currentTurn
                    + text("city_turn_suffix", " 回合")
            );
            label("date_label").setText(
                gameState.currentYear
                    + text("city_year_suffix", " 年 ")
                    + gameState.currentMonth
                    + text("city_month_suffix", " 月")
            );
            label("faction_label").setText(
                text("city_faction_prefix", "勢力：")
                    + localized(factionDefinition.nameKey, factionDefinition.id)
            );
            label("ruler_label").setText(
                text("city_ruler_prefix", "君主：")
                    + localized(factionDefinition.rulerNameKey, factionDefinition.id)
            );
            label("city_label").setText(
                text("city_name_prefix", "主城：")
                    + localized(cityDefinition.nameKey, cityDefinition.id)
            );
            label("action_points_label").setText(
                text("city_action_points_prefix", "行動力：")
                    + gameState.actionPointsRemaining
                    + " / "
                    + gameState.actionPointsPerTurn
            );

            label("gold_value_label").setText(numberFormat.format(factionState.gold));
            label("food_value_label").setText(numberFormat.format(factionState.food));
            label("population_value_label").setText(numberFormat.format(cityState.population));
            label("troops_value_label").setText(numberFormat.format(cityState.troops));
            label("agriculture_value_label").setText(cityState.agriculture + " / 100");
            label("commerce_value_label").setText(cityState.commerce + " / 100");
            label("public_order_value_label").setText(cityState.publicOrder + " / 100");
            label("training_value_label").setText(cityState.training + " / 100");

            refreshActionButtons(gameState);
            setButtonEnabled(button("save_button"), true);
            setButtonEnabled(button("return_lobby_button"), true);
            setButtonEnabled(button("end_turn_button"), true);
        } catch (RuntimeException exception) {
            Gdx.app.error("City", "刷新城池資料失敗。", exception);
            currentStatusMessage = text(
                "city_status_definition_error",
                "戰局引用的 Definition 不完整，無法顯示城池資料。"
            );
            currentStatusColor = STATUS_ERROR_COLOR;
            setGameplayButtonsEnabled(false);
        }
        refreshStatusLabel();
    }

    private void refreshActionButtons(GameState gameState) {
        setButtonEnabled(
            button("agriculture_button"),
            DomesticActionRules.evaluate(
                gameState,
                DomesticActionType.DEVELOP_AGRICULTURE
            ) == DomesticActionFailureReason.NONE
        );
        setButtonEnabled(
            button("commerce_button"),
            DomesticActionRules.evaluate(
                gameState,
                DomesticActionType.DEVELOP_COMMERCE
            ) == DomesticActionFailureReason.NONE
        );
        setButtonEnabled(
            button("recruit_button"),
            DomesticActionRules.evaluate(
                gameState,
                DomesticActionType.RECRUIT
            ) == DomesticActionFailureReason.NONE
        );
        setButtonEnabled(
            button("train_button"),
            DomesticActionRules.evaluate(
                gameState,
                DomesticActionType.TRAIN
            ) == DomesticActionFailureReason.NONE
        );
    }

    private void setGameplayButtonsEnabled(boolean enabled) {
        setButtonEnabled(button("agriculture_button"), enabled);
        setButtonEnabled(button("commerce_button"), enabled);
        setButtonEnabled(button("recruit_button"), enabled);
        setButtonEnabled(button("train_button"), enabled);
        setButtonEnabled(button("save_button"), enabled);
        setButtonEnabled(button("end_turn_button"), enabled);
        setButtonEnabled(button("return_lobby_button"), true);
    }

    private void showNoGameStateError() {
        currentStatusMessage = text(
            "city_status_load_failed",
            "無法載入戰局；請返回 Lobby 並建立新局。"
        );
        currentStatusColor = STATUS_ERROR_COLOR;
        refreshView();
    }

    private String successMessage(DomesticActionType actionType) {
        return switch (actionType) {
            case DEVELOP_AGRICULTURE -> text(
                "city_status_agriculture_success",
                "完成開墾：糧 +200、農業 +5。已自動存檔。"
            );
            case DEVELOP_COMMERCE -> text(
                "city_status_commerce_success",
                "完成商業整備：金 +150、商業 +5。已自動存檔。"
            );
            case RECRUIT -> text(
                "city_status_recruit_success",
                "完成徵兵：兵力 +200、人口 -200。已扣除金 100、糧 100 並自動存檔。"
            );
            case TRAIN -> text(
                "city_status_train_success",
                "完成訓練：訓練 +5。已扣除金 50 並自動存檔。"
            );
        };
    }

    private String failureMessage(DomesticActionFailureReason failureReason) {
        return switch (failureReason) {
            case NO_ACTION_POINTS -> text(
                "city_status_no_action_points",
                "行動力不足；請結束回合。"
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
            case NONE -> text("city_status_action_failed", "內政命令未執行。");
        };
    }

    private void refreshStatusLabel() {
        Label statusLabel = label("city_status_label");
        statusLabel.setText(
            currentStatusMessage == null
                ? text("city_status_ready", "選擇內政命令。")
                : currentStatusMessage
        );
        statusLabel.setColor(currentStatusColor);
    }

    private void setButtonEnabled(TextButton textButton, boolean enabled) {
        textButton.setDisabled(!enabled);
        textButton.setTouchable(enabled ? Touchable.enabled : Touchable.disabled);
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
