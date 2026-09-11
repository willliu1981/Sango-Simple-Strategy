package idv.kuan.studio.sango.ui;

import java.text.NumberFormat;
import java.util.Collections;
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
import idv.kuan.studio.sango.audio.MusicTrack;
import idv.kuan.studio.sango.audio.SoundEffect;
import idv.kuan.studio.sango.data.SangoPreferences;
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
 * 選擇劇本、勢力與存檔槽，建立新的 GameState。
 */
public final class NewGameScreen extends SuiScreen {
    private static final String BACKGROUND_PATH = "picture/lobby/sango_lobby_background.png";
    private static final String CHINA_SCENARIO_ID = "warlords_china";
    private static final String WORLD_SCENARIO_ID = "world_convergence";
    private static final String[] FACTION_BUTTON_IDS = {
        "faction_1_button",
        "faction_2_button",
        "faction_3_button",
        "faction_4_button",
        "faction_5_button",
        "faction_6_button"
    };
    private static final String[] SLOT_BUTTON_IDS = {
        "new_game_slot_1_button",
        "new_game_slot_2_button",
        "new_game_slot_3_button"
    };
    private static final Color STATUS_NORMAL_COLOR = new Color(0.77f, 0.70f, 0.59f, 1f);
    private static final Color STATUS_ERROR_COLOR = new Color(0.95f, 0.43f, 0.30f, 1f);

    private final ScreenBackground screenBackground = new ScreenBackground();
    private final NumberFormat numberFormat = NumberFormat.getIntegerInstance(Locale.TAIWAN);

    private Actor overwriteMask;
    private ScenarioDefinition scenarioDefinition;
    private List<FactionDefinition> factionDefinitions = Collections.emptyList();
    private String selectedFactionId;
    private int selectedSaveSlot = 1;

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
        SangoServices.audio().playMusic(MusicTrack.LOBBY);
        selectedSaveSlot = chooseDefaultSlot();
        loadDefinitionsAndSelectDefault();
        refreshSlotButtons();
    }

    @Override
    protected InputProcessor createInputProcessor(Stage stage) {
        InputAdapter navigationInput = new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                if (keycode == Input.Keys.BACK || keycode == Input.Keys.ESCAPE) {
                    if (overwriteMask != null && overwriteMask.isVisible()) {
                        closeOverwriteMask();
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
        SangoUiStyles.applySelectedButton(button("scenario_china_button"));
        SangoUiStyles.applySecondaryButton(button("scenario_world_button"));
        for (String factionButtonId : FACTION_BUTTON_IDS) {
            SangoUiStyles.applySecondaryButton(button(factionButtonId));
        }
        for (String slotButtonId : SLOT_BUTTON_IDS) {
            SangoUiStyles.applySecondaryButton(button(slotButtonId));
        }
        SangoUiStyles.applySecondaryButton(button("new_game_back_button"));
        SangoUiStyles.applyPrimaryButton(button("start_game_button"));
        SangoUiStyles.applySecondaryButton(button("overwrite_cancel_button"));
        SangoUiStyles.applyDangerButton(button("overwrite_confirm_button"));
    }

    private void bindActions() {
        ui.onClick("scenario_china_button", () -> selectScenario(CHINA_SCENARIO_ID));
        ui.onClick("scenario_world_button", () -> selectScenario(WORLD_SCENARIO_ID));
        ui.onClick("faction_1_button", () -> selectFactionByIndex(0));
        ui.onClick("faction_2_button", () -> selectFactionByIndex(1));
        ui.onClick("faction_3_button", () -> selectFactionByIndex(2));
        ui.onClick("faction_4_button", () -> selectFactionByIndex(3));
        ui.onClick("faction_5_button", () -> selectFactionByIndex(4));
        ui.onClick("faction_6_button", () -> selectFactionByIndex(5));
        ui.onClick("new_game_slot_1_button", () -> selectSaveSlot(1));
        ui.onClick("new_game_slot_2_button", () -> selectSaveSlot(2));
        ui.onClick("new_game_slot_3_button", () -> selectSaveSlot(3));
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

    private int chooseDefaultSlot() {
        for (int slotNumber = 1; slotNumber <= SangoServices.SAVE_SLOT_COUNT; slotNumber++) {
            if (SangoServices.saveGames().inspectNewest(slotNumber).getState() == SaveSlotState.EMPTY) {
                return slotNumber;
            }
        }
        return SangoPreferences.getLastUsedSaveSlot();
    }

    private void loadDefinitionsAndSelectDefault() {
        try {
            selectScenario(SangoServices.DEFAULT_SCENARIO_ID);
            setStatus(
                text("new_game_status_ready", "選擇勢力與存檔槽後即可建立新局。"),
                STATUS_NORMAL_COLOR
            );
        } catch (RuntimeException exception) {
            Gdx.app.error("NewGame", "載入 Definition 失敗。", exception);
            selectedFactionId = null;
            setButtonEnabled(button("start_game_button"), false);
            setStatus(
                text("new_game_status_definition_error", "無法載入劇本資料，請檢查 assets/data。"),
                STATUS_ERROR_COLOR
            );
        }
    }

    private void selectScenario(String scenarioId) {
        scenarioDefinition = SangoServices.definitions().requireScenario(scenarioId);
        factionDefinitions = SangoServices.definitions().findFactionsForScenario(scenarioId);
        configureFactionButtons();
        SangoUiStyles.applySelectedButton(button("scenario_china_button"));
        SangoUiStyles.applySecondaryButton(button("scenario_world_button"));
        if (WORLD_SCENARIO_ID.equals(scenarioId)) {
            SangoUiStyles.applySecondaryButton(button("scenario_china_button"));
            SangoUiStyles.applySelectedButton(button("scenario_world_button"));
        }
        if (factionDefinitions.isEmpty()) {
            selectedFactionId = null;
            setButtonEnabled(button("start_game_button"), false);
            setStatus(text("new_game_status_definition_error", "劇本沒有可選勢力。"),
                STATUS_ERROR_COLOR);
            return;
        }
        selectFactionByIndex(0);
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
        SangoServices.audio().playSound(SoundEffect.UI_CLICK);
        refreshFactionSelection();
        setButtonEnabled(button("start_game_button"), true);
    }

    private void selectSaveSlot(int slotNumber) {
        selectedSaveSlot = slotNumber;
        SangoServices.audio().playSound(SoundEffect.UI_CLICK);
        refreshSlotButtons();
    }

    private void refreshSlotButtons() {
        for (int slotNumber = 1; slotNumber <= SangoServices.SAVE_SLOT_COUNT; slotNumber++) {
            TextButton slotButton = button(SLOT_BUTTON_IDS[slotNumber - 1]);
            SaveSlotState slotState = SangoServices.saveGames().inspectNewest(slotNumber).getState();
            String stateText = switch (slotState) {
                case EMPTY -> text("new_game_slot_empty", "空白");
                case AVAILABLE -> text("new_game_slot_occupied", "已有存檔");
                case CORRUPT -> text("new_game_slot_corrupt", "損壞資料");
            };
            slotButton.setText(
                text("new_game_slot_format", "槽位 {0}｜{1}", slotNumber, stateText)
            );
            if (slotNumber == selectedSaveSlot) {
                SangoUiStyles.applySelectedButton(slotButton);
            } else {
                SangoUiStyles.applySecondaryButton(slotButton);
            }
        }
        label("selected_slot_label").setText(
            text("new_game_selected_slot_format", "新局將保存至槽位 {0}", selectedSaveSlot)
        );
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
            scenarioDefinition.requirePlayerStart(factionDefinition.id).startCityId
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
            .inspectNewest(selectedSaveSlot)
            .getState();
        if (saveSlotState == SaveSlotState.EMPTY) {
            createNewGame();
        } else {
            label("overwrite_description_label").setText(
                text(
                    "overwrite_description_format",
                    "存檔槽 {0} 已有資料。確定建立備份並以新局覆寫？",
                    selectedSaveSlot
                )
            );
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
                selectedSaveSlot,
                request
            );
            SangoServices.session().clear();
            SangoServices.session().setCurrentState(selectedSaveSlot, gameState);
            SangoServices.session().requestStrategicMapFocus();
            SangoPreferences.setLastUsedSaveSlot(selectedSaveSlot);
            SangoServices.audio().playSound(SoundEffect.CONFIRM);
            Sui.screens.set(ScreenId.STRATEGIC_MAP);
        } catch (RuntimeException exception) {
            Gdx.app.error("NewGame", "建立新局失敗。", exception);
            setStatus(
                text("new_game_status_create_failed", "建立新局失敗；原存檔不會被不完整資料取代。"),
                STATUS_ERROR_COLOR
            );
            SangoServices.audio().playSound(SoundEffect.COMMAND_ERROR);
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

    private void returnToLobby() {
        SangoServices.audio().playSound(SoundEffect.CANCEL);
        Sui.screens.set(ScreenId.LOBBY);
    }

    private void openOverwriteMask() {
        overwriteMask.setVisible(true);
        overwriteMask.getColor().a = 0f;
        overwriteMask.toFront();
        overwriteMask.addAction(Actions.fadeIn(0.16f));
        SangoServices.audio().playSound(SoundEffect.UI_CLICK);
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
