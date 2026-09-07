package idv.kuan.studio.sango.ui;

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
import com.badlogic.gdx.utils.Align;

import idv.kuan.studio.libgdx.simpleui.Sui;
import idv.kuan.studio.libgdx.simpleui.SuiScreen;
import idv.kuan.studio.libgdx.simpleui.builder.BuiltUI;
import idv.kuan.studio.sango.audio.MusicTrack;
import idv.kuan.studio.sango.audio.SoundEffect;
import idv.kuan.studio.sango.data.SangoPreferences;
import idv.kuan.studio.sango.domain.model.GameState;
import idv.kuan.studio.sango.repository.save.SaveSlotInspection;
import idv.kuan.studio.sango.repository.save.SaveSlotState;
import idv.kuan.studio.sango.runtime.SangoServices;
import idv.kuan.studio.sango.runtime.SaveLoadMode;
import idv.kuan.studio.sango.ui.id.ScreenId;
import idv.kuan.studio.sango.ui.support.ScreenBackground;
import idv.kuan.studio.sango.ui.theme.SangoUiStyles;

/**
 * Sango 啟動後的 Lobby／主選單畫面。
 */
public final class LobbyScreen extends SuiScreen {
    private static final String BACKGROUND_PATH = "picture/lobby/sango_lobby_background.png";
    private static final Color STATUS_READY_COLOR = new Color(0.83f, 0.73f, 0.53f, 1f);
    private static final Color STATUS_ACTIVE_COLOR = new Color(0.95f, 0.78f, 0.30f, 1f);
    private static final Color STATUS_ERROR_COLOR = new Color(0.95f, 0.43f, 0.30f, 1f);

    private final ScreenBackground screenBackground = new ScreenBackground();

    private Actor exitMask;
    private int continueSlot;

    @Override
    protected BuiltUI buildUI(UIFactory uiFactory) {
        return uiFactory.begin()
            .skipToRegisterTemplate()
            .skipToRegisterXml()
            .registerXml("ui/lobby.xml")
            .skipToBuild()
            .buildUI();
    }

    @Override
    protected void onUIBuilt(BuiltUI builtUI) {
        screenBackground.attach(stage, BACKGROUND_PATH);
        exitMask = attachModalMask("exit_mask");
        applyStyles();
        bindActions();
        animateEntrance();
    }

    @Override
    protected void afterShow() {
        if (ui == null) {
            return;
        }
        closeExitMask();
        SangoServices.audio().playMusic(MusicTrack.LOBBY);
        refreshSaveSlotStatus();
    }

    @Override
    protected InputProcessor createInputProcessor(Stage stage) {
        InputAdapter navigationInput = new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                if (keycode == Input.Keys.BACK || keycode == Input.Keys.ESCAPE) {
                    if (exitMask != null && exitMask.isVisible()) {
                        closeExitMask();
                    } else {
                        openExitMask();
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
        SangoUiStyles.applySecondaryButton(button("music_player_button"));
        SangoUiStyles.applyMenuButton(button("continue_button"));
        SangoUiStyles.applyPrimaryButton(button("new_game_button"));
        SangoUiStyles.applyMenuButton(button("load_game_button"));
        SangoUiStyles.applyMenuButton(button("settings_button"));
        SangoUiStyles.applyMenuButton(button("exit_button"));
        SangoUiStyles.applySecondaryButton(button("exit_cancel_button"));
        SangoUiStyles.applyDangerButton(button("exit_confirm_button"));
        label("lobby_status").setAlignment(Align.center);
    }

    private void bindActions() {
        ui.onClick("music_player_button", () -> {
            SangoServices.session().openMusicPlayer(ScreenId.LOBBY);
            Sui.screens.set(ScreenId.MUSIC_PLAYER);
        });
        ui.onClick("continue_button", this::continueCampaign);
        ui.onClick("new_game_button", this::openNewGame);
        ui.onClick("load_game_button", this::openLoadGame);
        ui.onClick("settings_button", this::openSettings);
        ui.onClick("exit_button", this::openExitMask);
        ui.onClick("exit_cancel_button", this::closeExitMask);
        ui.onClick("exit_confirm_button", Gdx.app::exit);
    }

    private void animateEntrance() {
        Actor main = ui.getActor("main");
        main.getColor().a = 0f;
        main.addAction(Actions.fadeIn(0.42f));
    }

    private void continueCampaign() {
        if (continueSlot < 1) {
            return;
        }
        try {
            GameState gameState = SangoServices.saveGames().load(continueSlot);
            SangoServices.session().clear();
            SangoServices.session().setCurrentState(continueSlot, gameState);
            SangoPreferences.setLastUsedSaveSlot(continueSlot);
            SangoServices.audio().playSound(SoundEffect.CONFIRM);
            Sui.screens.set(ScreenId.STRATEGIC_MAP);
        } catch (RuntimeException exception) {
            Gdx.app.error("Lobby", "繼續遊戲時無法載入存檔。", exception);
            SangoServices.session().clear();
            setStatus(
                text("status_save_corrupt", "存檔無法讀取；可從讀取存檔畫面檢查其他槽位。"),
                STATUS_ERROR_COLOR
            );
            setButtonEnabled(button("continue_button"), false);
        }
    }

    private void openNewGame() {
        SangoServices.audio().playSound(SoundEffect.UI_CLICK);
        Sui.screens.set(ScreenId.NEW_GAME);
    }

    private void openLoadGame() {
        SangoServices.audio().playSound(SoundEffect.UI_CLICK);
        SangoServices.session().openSaveLoad(SaveLoadMode.LOAD, ScreenId.LOBBY);
        Sui.screens.set(ScreenId.SAVE_LOAD);
    }

    private void openSettings() {
        SangoServices.audio().playSound(SoundEffect.UI_CLICK);
        SangoServices.session().openSettings(ScreenId.LOBBY);
        Sui.screens.set(ScreenId.SETTINGS);
    }

    private void refreshSaveSlotStatus() {
        continueSlot = findContinueSlot();
        int availableCount = countSlots(SaveSlotState.AVAILABLE);
        int corruptCount = countSlots(SaveSlotState.CORRUPT);
        setButtonEnabled(button("continue_button"), continueSlot > 0);
        setButtonEnabled(button("load_game_button"), availableCount > 0 || corruptCount > 0);

        if (continueSlot > 0) {
            SaveSlotInspection inspection = SangoServices.saveGames().inspect(continueSlot);
            if (inspection.hasRecoveryCandidate()) {
                setStatus(
                    text(
                        "status_save_recovery_available",
                        "存檔槽 {0} 的主要檔異常，可由暫存或備份繼續。",
                        continueSlot
                    ),
                    STATUS_ACTIVE_COLOR
                );
            } else {
                setStatus(
                    text(
                        "status_campaign_available_slot",
                        "找到 {0} 個可用存檔；繼續遊戲將讀取槽位 {1}。",
                        availableCount,
                        continueSlot
                    ),
                    STATUS_ACTIVE_COLOR
                );
            }
            return;
        }

        if (corruptCount > 0) {
            setStatus(
                text("status_save_corrupt_slots", "偵測到損壞槽位；可進入讀取存檔查看診斷或刪除。"),
                STATUS_ERROR_COLOR
            );
        } else {
            setStatus(
                text("status_no_campaign", "尚無戰局存檔；請先開始新局。"),
                STATUS_READY_COLOR
            );
        }
    }

    private int findContinueSlot() {
        int preferredSlot = SangoPreferences.getLastUsedSaveSlot();
        if (SangoServices.saveGames().inspect(preferredSlot).isAvailable()) {
            return preferredSlot;
        }
        for (int slotNumber = 1; slotNumber <= SangoServices.SAVE_SLOT_COUNT; slotNumber++) {
            if (SangoServices.saveGames().inspect(slotNumber).isAvailable()) {
                return slotNumber;
            }
        }
        return 0;
    }

    private int countSlots(SaveSlotState requestedState) {
        int count = 0;
        for (int slotNumber = 1; slotNumber <= SangoServices.SAVE_SLOT_COUNT; slotNumber++) {
            if (SangoServices.saveGames().inspect(slotNumber).getState() == requestedState) {
                count += 1;
            }
        }
        return count;
    }

    private void openExitMask() {
        exitMask.setVisible(true);
        exitMask.getColor().a = 0f;
        exitMask.toFront();
        exitMask.addAction(Actions.fadeIn(0.16f));
        SangoServices.audio().playSound(SoundEffect.UI_CLICK);
    }

    private void closeExitMask() {
        if (exitMask != null) {
            exitMask.clearActions();
            exitMask.setVisible(false);
            exitMask.getColor().a = 1f;
        }
    }

    private void setButtonEnabled(TextButton textButton, boolean enabled) {
        textButton.setDisabled(!enabled);
        textButton.setTouchable(enabled ? Touchable.enabled : Touchable.disabled);
    }

    private void setStatus(String message, Color color) {
        Label statusLabel = label("lobby_status");
        statusLabel.setText(message);
        statusLabel.setColor(color);
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
