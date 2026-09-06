package idv.kuan.studio.sango.ui;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

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
import idv.kuan.studio.sango.audio.MusicTrack;
import idv.kuan.studio.sango.audio.SoundEffect;
import idv.kuan.studio.sango.data.SangoPreferences;
import idv.kuan.studio.sango.domain.definition.CityDefinition;
import idv.kuan.studio.sango.domain.definition.FactionDefinition;
import idv.kuan.studio.sango.domain.definition.ScenarioDefinition;
import idv.kuan.studio.sango.domain.model.GameState;
import idv.kuan.studio.sango.domain.model.ScenarioObjectiveStatus;
import idv.kuan.studio.sango.repository.save.SaveSlotInspection;
import idv.kuan.studio.sango.repository.save.SaveSlotMetadata;
import idv.kuan.studio.sango.repository.save.SaveSlotState;
import idv.kuan.studio.sango.runtime.SangoServices;
import idv.kuan.studio.sango.runtime.SaveLoadMode;
import idv.kuan.studio.sango.ui.id.ScreenId;
import idv.kuan.studio.sango.ui.support.ScreenBackground;
import idv.kuan.studio.sango.ui.theme.SangoUiStyles;

/**
 * 三槽存讀檔畫面。
 */
public final class SaveLoadScreen extends SuiScreen {
    private static final String BACKGROUND_PATH = "picture/lobby/sango_lobby_background.png";
    private static final String[] SLOT_BUTTON_IDS = {
        "save_slot_1_button",
        "save_slot_2_button",
        "save_slot_3_button"
    };
    private static final Color STATUS_NORMAL_COLOR = new Color(0.78f, 0.71f, 0.61f, 1f);
    private static final Color STATUS_SUCCESS_COLOR = new Color(0.94f, 0.76f, 0.38f, 1f);
    private static final Color STATUS_ERROR_COLOR = new Color(0.95f, 0.43f, 0.30f, 1f);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter
        .ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault());

    private final ScreenBackground screenBackground = new ScreenBackground();

    private Actor overwriteMask;
    private Actor deleteMask;
    private int selectedSlot = 1;
    private String statusMessage = "";
    private Color statusColor = STATUS_NORMAL_COLOR;

    @Override
    protected BuiltUI buildUI(UIFactory uiFactory) {
        return uiFactory.begin()
            .skipToRegisterTemplate()
            .skipToRegisterXml()
            .registerXml("ui/save_load.xml")
            .skipToBuild()
            .buildUI();
    }

    @Override
    protected void onUIBuilt(BuiltUI builtUI) {
        screenBackground.attach(stage, BACKGROUND_PATH);
        overwriteMask = attachModalMask("save_overwrite_mask");
        deleteMask = attachModalMask("save_delete_mask");
        applyStyles();
        bindActions();
        Actor main = ui.getActor("main");
        main.getColor().a = 0f;
        main.addAction(Actions.fadeIn(0.24f));
    }

    @Override
    protected void afterShow() {
        closeModals();
        selectedSlot = chooseInitialSlot();
        statusMessage = text("save_load_status_ready", "選擇存檔槽。");
        statusColor = STATUS_NORMAL_COLOR;
        playExpectedMusic();
        refreshView();
    }

    @Override
    protected InputProcessor createInputProcessor(Stage stage) {
        InputAdapter navigationInput = new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                if (keycode == Input.Keys.BACK || keycode == Input.Keys.ESCAPE) {
                    if (isModalVisible()) {
                        closeModals();
                    } else {
                        returnToPreviousScreen();
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
        for (String slotButtonId : SLOT_BUTTON_IDS) {
            SangoUiStyles.applySecondaryButton(button(slotButtonId));
        }
        SangoUiStyles.applyPrimaryButton(button("save_load_action_button"));
        SangoUiStyles.applyDangerButton(button("save_load_delete_button"));
        SangoUiStyles.applySecondaryButton(button("save_load_back_button"));
        SangoUiStyles.applySecondaryButton(button("save_overwrite_cancel_button"));
        SangoUiStyles.applyDangerButton(button("save_overwrite_confirm_button"));
        SangoUiStyles.applySecondaryButton(button("save_delete_cancel_button"));
        SangoUiStyles.applyDangerButton(button("save_delete_confirm_button"));
    }

    private void bindActions() {
        ui.onClick("save_slot_1_button", () -> selectSlot(1));
        ui.onClick("save_slot_2_button", () -> selectSlot(2));
        ui.onClick("save_slot_3_button", () -> selectSlot(3));
        ui.onClick("save_load_action_button", this::executePrimaryAction);
        ui.onClick("save_load_delete_button", this::requestDelete);
        ui.onClick("save_load_back_button", this::returnToPreviousScreen);
        ui.onClick("save_overwrite_cancel_button", this::closeModals);
        ui.onClick("save_overwrite_confirm_button", this::saveSelectedSlot);
        ui.onClick("save_delete_cancel_button", this::closeModals);
        ui.onClick("save_delete_confirm_button", this::deleteSelectedSlot);
    }

    private int chooseInitialSlot() {
        if (SangoServices.session().getSaveLoadMode() == SaveLoadMode.SAVE
            && SangoServices.session().hasCurrentState()) {
            return SangoServices.session().getCurrentSaveSlot();
        }
        int preferredSlot = SangoPreferences.getLastUsedSaveSlot();
        if (SangoServices.saveGames().inspect(preferredSlot).isAvailable()) {
            return preferredSlot;
        }
        for (int slotNumber = 1; slotNumber <= SangoServices.SAVE_SLOT_COUNT; slotNumber++) {
            if (SangoServices.saveGames().inspect(slotNumber).isAvailable()) {
                return slotNumber;
            }
        }
        return preferredSlot;
    }

    private void selectSlot(int slotNumber) {
        selectedSlot = slotNumber;
        statusMessage = text("save_load_status_selected", "已選擇存檔槽 {0}。", slotNumber);
        statusColor = STATUS_NORMAL_COLOR;
        SangoServices.audio().playSound(SoundEffect.UI_CLICK);
        refreshView();
    }

    private void refreshView() {
        SaveLoadMode mode = SangoServices.session().getSaveLoadMode();
        label("save_load_title_label").setText(
            mode == SaveLoadMode.LOAD
                ? text("save_load_title_load", "讀取存檔")
                : text("save_load_title_save", "儲存遊戲")
        );
        label("save_load_description_label").setText(
            mode == SaveLoadMode.LOAD
                ? text("save_load_description_load", "選擇一個有效槽位以載入戰局。")
                : text("save_load_description_save", "選擇槽位保存目前戰局；覆寫既有槽位前會再次確認。")
        );

        for (int slotNumber = 1; slotNumber <= SangoServices.SAVE_SLOT_COUNT; slotNumber++) {
            refreshSlotButton(slotNumber);
        }
        refreshSelectedSlotDetails();
        refreshActionButtons();
        Label statusLabel = label("save_load_status_label");
        statusLabel.setText(statusMessage);
        statusLabel.setColor(statusColor);
    }

    private void refreshSlotButton(int slotNumber) {
        TextButton slotButton = button(SLOT_BUTTON_IDS[slotNumber - 1]);
        SaveSlotInspection inspection = SangoServices.saveGames().inspect(slotNumber);
        slotButton.setText(buildSlotButtonText(slotNumber, inspection));
        if (slotNumber == selectedSlot) {
            SangoUiStyles.applySelectedButton(slotButton);
        } else {
            SangoUiStyles.applySecondaryButton(slotButton);
        }
    }

    private String buildSlotButtonText(int slotNumber, SaveSlotInspection inspection) {
        String heading = text("save_slot_heading_format", "存檔槽 {0}", slotNumber);
        if (inspection.getState() == SaveSlotState.EMPTY) {
            return heading + "\n" + text("save_slot_empty", "空白槽位");
        }
        if (inspection.getState() == SaveSlotState.CORRUPT) {
            return heading + "\n" + text("save_slot_corrupt", "存檔損壞");
        }
        SaveSlotMetadata metadata = inspection.getMetadata();
        return heading + "\n"
            + factionName(metadata.getPlayerFactionId()) + "｜"
            + metadata.getCurrentYear() + " 年 " + metadata.getCurrentMonth() + " 月｜"
            + metadata.getOwnedCityCount() + text("save_slot_city_count_suffix", " 城");
    }

    private void refreshSelectedSlotDetails() {
        SaveSlotInspection inspection = SangoServices.saveGames().inspect(selectedSlot);
        label("save_slot_detail_heading_label").setText(
            text("save_slot_heading_format", "存檔槽 {0}", selectedSlot)
        );
        if (inspection.getState() == SaveSlotState.EMPTY) {
            label("save_slot_detail_label").setText(
                text("save_slot_empty_detail", "此槽位沒有存檔。")
            );
            return;
        }
        if (inspection.getState() == SaveSlotState.CORRUPT) {
            label("save_slot_detail_label").setText(
                text(
                    "save_slot_corrupt_detail",
                    "此槽位沒有可用的主要、暫存或備份存檔。\n診斷：{0}",
                    inspection.getDiagnosticMessage()
                )
            );
            return;
        }
        SaveSlotMetadata metadata = inspection.getMetadata();
        String capitalName = metadata.getCapitalCityId().isEmpty()
            ? text("save_slot_no_capital", "無有效首都")
            : cityName(metadata.getCapitalCityId());
        String recoveryNotice = inspection.hasRecoveryCandidate()
            ? "\n" + text("save_slot_recovery_notice", "將由暫存或備份檔復原。")
            : "";
        label("save_slot_detail_label").setText(
            text("save_slot_scenario_prefix", "劇本：")
                + scenarioName(metadata.getScenarioId()) + "\n"
                + text("save_slot_faction_prefix", "勢力：")
                + factionName(metadata.getPlayerFactionId()) + "\n"
                + text("save_slot_capital_prefix", "首都：") + capitalName + "\n"
                + text("save_slot_date_prefix", "年代：")
                + metadata.getCurrentYear() + " 年 " + metadata.getCurrentMonth() + " 月｜第 "
                + metadata.getCurrentTurn() + " 回合\n"
                + text("save_slot_owned_cities_prefix", "領地：")
                + metadata.getOwnedCityCount() + text("save_slot_city_count_suffix", " 城") + "\n"
                + text("save_slot_objective_prefix", "劇本目標：")
                + objectiveName(metadata.getObjectiveStatus()) + "\n"
                + text("save_slot_saved_at_prefix", "保存時間：")
                + TIME_FORMATTER.format(Instant.ofEpochMilli(metadata.getSavedAtEpochMillis()))
                + recoveryNotice
        );
    }

    private void refreshActionButtons() {
        SaveLoadMode mode = SangoServices.session().getSaveLoadMode();
        SaveSlotInspection inspection = SangoServices.saveGames().inspect(selectedSlot);
        button("save_load_action_button").setText(
            mode == SaveLoadMode.LOAD
                ? text("button_load_selected_slot", "讀取此存檔")
                : text("button_save_selected_slot", "保存至此槽位")
        );
        boolean actionEnabled = mode == SaveLoadMode.LOAD
            ? inspection.isAvailable()
            : SangoServices.session().hasCurrentState();
        setButtonEnabled(button("save_load_action_button"), actionEnabled);

        boolean deletingCurrentActiveSlot = mode == SaveLoadMode.SAVE
            && SangoServices.session().hasCurrentState()
            && selectedSlot == SangoServices.session().getCurrentSaveSlot();
        setButtonEnabled(
            button("save_load_delete_button"),
            inspection.getState() != SaveSlotState.EMPTY && !deletingCurrentActiveSlot
        );
    }

    private void executePrimaryAction() {
        if (SangoServices.session().getSaveLoadMode() == SaveLoadMode.LOAD) {
            loadSelectedSlot();
        } else {
            requestSave();
        }
    }

    private void loadSelectedSlot() {
        try {
            GameState gameState = SangoServices.saveGames().load(selectedSlot);
            SangoServices.session().clear();
            SangoServices.session().setCurrentState(selectedSlot, gameState);
            SangoPreferences.setLastUsedSaveSlot(selectedSlot);
            SangoServices.audio().playSound(SoundEffect.CONFIRM);
            Sui.screens.set(ScreenId.STRATEGIC_MAP);
        } catch (RuntimeException exception) {
            Gdx.app.error("SaveLoad", "讀取存檔失敗。", exception);
            statusMessage = text("save_load_status_load_failed", "存檔讀取失敗，請檢查診斷資訊。");
            statusColor = STATUS_ERROR_COLOR;
            SangoServices.audio().playSound(SoundEffect.COMMAND_ERROR);
            refreshView();
        }
    }

    private void requestSave() {
        SaveSlotInspection inspection = SangoServices.saveGames().inspect(selectedSlot);
        boolean currentSlot = SangoServices.session().hasCurrentState()
            && selectedSlot == SangoServices.session().getCurrentSaveSlot();
        if (inspection.getState() == SaveSlotState.EMPTY || currentSlot) {
            saveSelectedSlot();
        } else {
            label("save_overwrite_description_label").setText(
                text("save_overwrite_description_format", "存檔槽 {0} 已有資料。確定以目前戰局覆寫？", selectedSlot)
            );
            openModal(overwriteMask);
        }
    }

    private void saveSelectedSlot() {
        closeModals();
        try {
            GameState currentState = SangoServices.session().requireCurrentState();
            SangoServices.saveCurrentGameCommand().execute(selectedSlot, currentState);
            SangoServices.session().setCurrentState(selectedSlot, currentState);
            SangoPreferences.setLastUsedSaveSlot(selectedSlot);
            statusMessage = text("save_load_status_saved", "已保存至存檔槽 {0}。", selectedSlot);
            statusColor = STATUS_SUCCESS_COLOR;
            SangoServices.audio().playSound(SoundEffect.SAVE_COMPLETE);
        } catch (RuntimeException exception) {
            Gdx.app.error("SaveLoad", "保存存檔失敗。", exception);
            statusMessage = text("save_load_status_save_failed", "保存失敗，原存檔未被替換。");
            statusColor = STATUS_ERROR_COLOR;
            SangoServices.audio().playSound(SoundEffect.COMMAND_ERROR);
        }
        refreshView();
    }

    private void requestDelete() {
        SaveSlotInspection inspection = SangoServices.saveGames().inspect(selectedSlot);
        if (inspection.getState() == SaveSlotState.EMPTY) {
            return;
        }
        label("save_delete_description_label").setText(
            text("save_delete_description_format", "確定刪除存檔槽 {0} 的主要、暫存與備份檔？", selectedSlot)
        );
        openModal(deleteMask);
    }

    private void deleteSelectedSlot() {
        closeModals();
        try {
            SangoServices.saveGames().delete(selectedSlot);
            statusMessage = text("save_load_status_deleted", "已刪除存檔槽 {0}。", selectedSlot);
            statusColor = STATUS_SUCCESS_COLOR;
            SangoServices.audio().playSound(SoundEffect.CONFIRM);
        } catch (RuntimeException exception) {
            Gdx.app.error("SaveLoad", "刪除存檔失敗。", exception);
            statusMessage = text("save_load_status_delete_failed", "刪除存檔失敗。");
            statusColor = STATUS_ERROR_COLOR;
            SangoServices.audio().playSound(SoundEffect.COMMAND_ERROR);
        }
        refreshView();
    }

    private void returnToPreviousScreen() {
        SangoServices.audio().playSound(SoundEffect.CANCEL);
        Sui.screens.set(SangoServices.session().getSaveLoadReturnScreen());
    }

    private void playExpectedMusic() {
        ScreenId returnScreen = SangoServices.session().getSaveLoadReturnScreen();
        SangoServices.audio().playMusic(
            returnScreen == ScreenId.LOBBY ? MusicTrack.LOBBY : MusicTrack.STRATEGY
        );
    }

    private void openModal(Actor mask) {
        closeModals();
        mask.setVisible(true);
        mask.getColor().a = 0f;
        mask.toFront();
        mask.addAction(Actions.fadeIn(0.15f));
        SangoServices.audio().playSound(SoundEffect.UI_CLICK);
    }

    private void closeModals() {
        setVisible(overwriteMask, false);
        setVisible(deleteMask, false);
    }

    private boolean isModalVisible() {
        return isVisible(overwriteMask) || isVisible(deleteMask);
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

    private String scenarioName(String scenarioId) {
        ScenarioDefinition definition = SangoServices.definitions().requireScenario(scenarioId);
        return text(definition.nameKey, definition.id);
    }

    private String factionName(String factionId) {
        FactionDefinition definition = SangoServices.definitions().requireFaction(factionId);
        return text(definition.nameKey, definition.id);
    }

    private String cityName(String cityId) {
        CityDefinition definition = SangoServices.definitions().requireCity(cityId);
        return text(definition.nameKey, definition.id);
    }

    private String objectiveName(ScenarioObjectiveStatus objectiveStatus) {
        return switch (objectiveStatus) {
            case IN_PROGRESS -> text("objective_in_progress", "進行中");
            case ACHIEVED -> text("objective_achieved", "已達成・自由征戰");
            case FAILED -> text("objective_failed", "已失敗・自由征戰");
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
