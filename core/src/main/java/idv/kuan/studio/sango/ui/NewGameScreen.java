package idv.kuan.studio.sango.ui;

import java.text.NumberFormat;
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

import idv.kuan.studio.libgdx.simpleui.Sui;
import idv.kuan.studio.libgdx.simpleui.SuiScreen;
import idv.kuan.studio.libgdx.simpleui.builder.BuiltUI;
import idv.kuan.studio.sango.application.request.NewGameRequest;
import idv.kuan.studio.sango.domain.definition.CityDefinition;
import idv.kuan.studio.sango.domain.definition.FactionDefinition;
import idv.kuan.studio.sango.domain.definition.ScenarioDefinition;
import idv.kuan.studio.sango.domain.model.GameState;
import idv.kuan.studio.sango.repository.save.SaveSlotState;
import idv.kuan.studio.sango.runtime.SangoServices;
import idv.kuan.studio.sango.ui.id.ScreenId;
import idv.kuan.studio.sango.ui.support.ScreenBackground;
import idv.kuan.studio.sango.ui.theme.SangoUiStyles;

/**
 * 選擇劇本勢力並建立真正 GameState 的新局畫面。
 */
public final class NewGameScreen extends SuiScreen {
    private static final String BACKGROUND_PATH = "picture/lobby/sango_lobby_background.png";
    private static final String[] FACTION_BUTTON_IDS = {
        "faction_1_button",
        "faction_2_button",
        "faction_3_button"
    };
    private static final Color STATUS_NORMAL_COLOR = new Color(0.77f, 0.70f, 0.59f, 1f);
    private static final Color STATUS_ERROR_COLOR = new Color(0.95f, 0.43f, 0.30f, 1f);

    private final ScreenBackground screenBackground = new ScreenBackground();
    private final NumberFormat numberFormat = NumberFormat.getIntegerInstance(Locale.TAIWAN);

    private Actor overwriteMask;
    private ScenarioDefinition scenarioDefinition;
    private List<FactionDefinition> factionDefinitions = java.util.Collections.emptyList();
    private String selectedFactionId;

    @Override
    protected BuiltUI buildUI(UIFactory uiFactory) {
        return uiFactory.begin()
            .skipToRegisterTemplate()
            .skipToRegisterXml()
            .registerXml("ui/new_game.xml")
            .skipToBuild()
            .buildUI();
    }

    @Override
    protected void onUIBuilt(BuiltUI builtUI) {
        screenBackground.attach(stage, BACKGROUND_PATH);
        overwriteMask = attachModalMask("overwrite_mask");
        applyStyles();
        bindActions();
        animateEntrance();
    }

    @Override
    protected void afterShow() {
        if (ui == null) {
            return;
        }
        closeOverwriteMask();
        loadDefinitionsAndSelectDefault();
    }

    @Override
    protected InputProcessor createInputProcessor(Stage stage) {
        InputAdapter navigationInput = new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                if (keycode == Input.Keys.BACK || keycode == Input.Keys.ESCAPE) {
                    handleBackAction();
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

    private Actor attachModalMask(String actorId) {
        Actor mask = ui.getActor(actorId);
        if (mask.getStage() == null) {
            stage.addActor(mask);
        }
        mask.setVisible(false);
        return mask;
    }

    private void applyStyles() {
        for (String factionButtonId : FACTION_BUTTON_IDS) {
            SangoUiStyles.applySecondaryButton(button(factionButtonId));
        }
        SangoUiStyles.applySecondaryButton(button("new_game_back_button"));
        SangoUiStyles.applyPrimaryButton(button("start_game_button"));
        SangoUiStyles.applySecondaryButton(button("overwrite_cancel_button"));
        SangoUiStyles.applyDangerButton(button("overwrite_confirm_button"));
    }

    private void bindActions() {
        ui.onClick("faction_1_button", () -> selectFactionByIndex(0));
        ui.onClick("faction_2_button", () -> selectFactionByIndex(1));
        ui.onClick("faction_3_button", () -> selectFactionByIndex(2));
        ui.onClick("new_game_back_button", this::returnToLobby);
        ui.onClick("start_game_button", this::requestStartGame);
        ui.onClick("overwrite_cancel_button", this::closeOverwriteMask);
        ui.onClick("overwrite_confirm_button", this::createNewGame);
    }

    private void animateEntrance() {
        Actor main = ui.getActor("main");
        main.getColor().a = 0f;
        main.addAction(Actions.fadeIn(0.32f));
    }

    private void loadDefinitionsAndSelectDefault() {
        try {
            scenarioDefinition = SangoServices.definitions().requireScenario(
                SangoServices.DEFAULT_SCENARIO_ID
            );
            factionDefinitions = SangoServices.definitions().findFactionsForScenario(
                scenarioDefinition.id
            );
            configureFactionButtons();
            if (factionDefinitions.isEmpty()) {
                selectedFactionId = null;
                setStatus(
                    text("new_game_status_definition_error", "劇本沒有可選勢力。"),
                    STATUS_ERROR_COLOR
                );
                setButtonEnabled(button("start_game_button"), false);
                return;
            }
            selectFactionByIndex(0);
            setStatus(
                text("new_game_status_ready", "選擇勢力後即可建立新局。"),
                STATUS_NORMAL_COLOR
            );
        } catch (RuntimeException exception) {
            Gdx.app.error("NewGame", "載入 Definition 失敗。", exception);
            selectedFactionId = null;
            setButtonEnabled(button("start_game_button"), false);
            setStatus(
                text(
                    "new_game_status_definition_error",
                    "無法載入劇本資料，請檢查 assets/data。"
                ),
                STATUS_ERROR_COLOR
            );
        }
    }

    private void configureFactionButtons() {
        for (int i = 0; i < FACTION_BUTTON_IDS.length; i++) {
            TextButton factionButton = button(FACTION_BUTTON_IDS[i]);
            boolean hasFaction = i < factionDefinitions.size();
            factionButton.setVisible(hasFaction);
            factionButton.setTouchable(hasFaction ? Touchable.enabled : Touchable.disabled);
            if (hasFaction) {
                FactionDefinition factionDefinition = factionDefinitions.get(i);
                factionButton.setText(localized(factionDefinition.nameKey, factionDefinition.id));
            }
        }
    }

    private void selectFactionByIndex(int factionIndex) {
        if (factionIndex < 0 || factionIndex >= factionDefinitions.size()) {
            return;
        }
        selectedFactionId = factionDefinitions.get(factionIndex).id;
        refreshFactionSelection();
        setButtonEnabled(button("start_game_button"), true);
    }

    private void refreshFactionSelection() {
        for (int i = 0; i < FACTION_BUTTON_IDS.length; i++) {
            if (i >= factionDefinitions.size()) {
                continue;
            }
            TextButton factionButton = button(FACTION_BUTTON_IDS[i]);
            FactionDefinition factionDefinition = factionDefinitions.get(i);
            if (factionDefinition.id.equals(selectedFactionId)) {
                SangoUiStyles.applySelectedButton(factionButton);
            } else {
                SangoUiStyles.applySecondaryButton(factionButton);
            }
        }

        FactionDefinition factionDefinition = requireSelectedFaction();
        CityDefinition cityDefinition = SangoServices.definitions().requireCity(
            factionDefinition.capitalCityId
        );

        label("scenario_name_label").setText(
            localized(scenarioDefinition.nameKey, scenarioDefinition.id)
        );
        label("scenario_description_label").setText(
            localized(scenarioDefinition.descriptionKey, scenarioDefinition.id)
        );
        label("selected_faction_label").setText(
            text("new_game_faction_prefix", "勢力：")
                + localized(factionDefinition.nameKey, factionDefinition.id)
        );
        label("selected_ruler_label").setText(
            text("new_game_ruler_prefix", "君主：")
                + localized(factionDefinition.rulerNameKey, factionDefinition.id)
        );
        label("selected_city_label").setText(
            text("new_game_city_prefix", "主城：")
                + localized(cityDefinition.nameKey, cityDefinition.id)
        );
        label("selected_resources_label").setText(
            text("new_game_resources_prefix", "初始資源：")
                + numberFormat.format(factionDefinition.initialGold)
                + text("resource_gold_suffix", " 金 · ")
                + numberFormat.format(factionDefinition.initialFood)
                + text("resource_food_suffix", " 糧")
        );
        label("selected_summary_label").setText(
            localized(factionDefinition.summaryKey, factionDefinition.id)
        );
    }

    private void requestStartGame() {
        if (selectedFactionId == null) {
            setStatus(
                text("new_game_status_select_faction", "請先選擇勢力。"),
                STATUS_ERROR_COLOR
            );
            return;
        }

        SaveSlotState saveSlotState = SangoServices.saveGames()
            .inspect(SangoServices.DEFAULT_SAVE_SLOT)
            .getState();
        if (saveSlotState == SaveSlotState.EMPTY) {
            createNewGame();
        } else {
            openOverwriteMask();
        }
    }

    private void createNewGame() {
        closeOverwriteMask();
        try {
            NewGameRequest request = new NewGameRequest(
                scenarioDefinition.id,
                selectedFactionId
            );
            GameState gameState = SangoServices.newGameCommand().execute(
                SangoServices.DEFAULT_SAVE_SLOT,
                request
            );
            SangoServices.session().setCurrentState(gameState);
            Sui.screens.set(ScreenId.CITY);
        } catch (RuntimeException exception) {
            Gdx.app.error("NewGame", "建立新局失敗。", exception);
            setStatus(
                text(
                    "new_game_status_create_failed",
                    "建立新局失敗；若已有舊存檔，可回 Lobby 再嘗試繼續。"
                ),
                STATUS_ERROR_COLOR
            );
        }
    }

    private FactionDefinition requireSelectedFaction() {
        for (FactionDefinition factionDefinition : factionDefinitions) {
            if (factionDefinition.id.equals(selectedFactionId)) {
                return factionDefinition;
            }
        }
        throw new IllegalStateException("找不到目前選取的勢力：" + selectedFactionId);
    }

    private void handleBackAction() {
        if (overwriteMask != null && overwriteMask.isVisible()) {
            closeOverwriteMask();
            return;
        }
        returnToLobby();
    }

    private void returnToLobby() {
        Sui.screens.set(ScreenId.LOBBY);
    }

    private void openOverwriteMask() {
        overwriteMask.setVisible(true);
        overwriteMask.getColor().a = 0f;
        overwriteMask.toFront();
        overwriteMask.addAction(Actions.fadeIn(0.16f));
    }

    private void closeOverwriteMask() {
        if (overwriteMask != null) {
            overwriteMask.clearActions();
            overwriteMask.setVisible(false);
            overwriteMask.getColor().a = 1f;
        }
    }

    private void setButtonEnabled(TextButton textButton, boolean enabled) {
        textButton.setDisabled(!enabled);
        textButton.setTouchable(enabled ? Touchable.enabled : Touchable.disabled);
    }

    private void setStatus(String message, Color color) {
        Label statusLabel = label("new_game_status_label");
        statusLabel.setText(message);
        statusLabel.setColor(color);
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
