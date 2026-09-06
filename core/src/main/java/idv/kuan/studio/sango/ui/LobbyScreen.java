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
import idv.kuan.studio.sango.data.SangoPreferences;
import idv.kuan.studio.sango.domain.model.GameState;
import idv.kuan.studio.sango.repository.save.SaveGameException;
import idv.kuan.studio.sango.repository.save.SaveSlotInspection;
import idv.kuan.studio.sango.repository.save.SaveSlotState;
import idv.kuan.studio.sango.runtime.SangoServices;
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

    private Actor settingsMask;
    private Actor exitMask;

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
        settingsMask = attachModalMask("settings_mask");
        exitMask = attachModalMask("exit_mask");
        applyStyles();
        bindActions();
        refreshSettingsLabels();
        animateEntrance();
    }

    @Override
    protected void afterShow() {
        if (ui == null) {
            return;
        }
        closeAllModals();
        refreshSaveSlotStatus();
        refreshSettingsLabels();
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
        SangoUiStyles.applyMenuButton(button("continue_button"));
        SangoUiStyles.applyPrimaryButton(button("new_game_button"));
        SangoUiStyles.applyMenuButton(button("load_game_button"));
        SangoUiStyles.applyMenuButton(button("settings_button"));
        SangoUiStyles.applyMenuButton(button("exit_button"));

        SangoUiStyles.applySecondaryButton(button("settings_music_button"));
        SangoUiStyles.applySecondaryButton(button("settings_sound_button"));
        SangoUiStyles.applyPrimaryButton(button("settings_close_button"));
        SangoUiStyles.applySecondaryButton(button("exit_cancel_button"));
        SangoUiStyles.applyDangerButton(button("exit_confirm_button"));

        Label statusLabel = ui.getActor("lobby_status", Label.class);
        statusLabel.setAlignment(Align.center);
    }

    private void bindActions() {
        ui.onClick("continue_button", this::continueCampaign);
        ui.onClick("new_game_button", () -> Sui.screens.set(ScreenId.NEW_GAME));
        ui.onClick("settings_button", this::openSettings);
        ui.onClick("exit_button", () -> openModal(exitMask));

        ui.onClick("settings_music_button", this::toggleMusic);
        ui.onClick("settings_sound_button", this::toggleSound);
        ui.onClick("settings_close_button", this::closeAllModals);
        ui.onClick("exit_cancel_button", this::closeAllModals);
        ui.onClick("exit_confirm_button", Gdx.app::exit);
    }

    private void animateEntrance() {
        Actor main = ui.getActor("main");
        main.getColor().a = 0f;
        main.addAction(Actions.fadeIn(0.42f));
    }

    private void continueCampaign() {
        try {
            GameState gameState = SangoServices.saveGames().load(
                SangoServices.DEFAULT_SAVE_SLOT
            );
            SangoServices.session().setCurrentState(gameState);
            Sui.screens.set(ScreenId.STRATEGIC_MAP);
        } catch (SaveGameException | IllegalArgumentException exception) {
            Gdx.app.error("Lobby", "繼續遊戲時無法載入存檔。", exception);
            SangoServices.session().clear();
            setStatus(
                text("status_save_corrupt", "存檔無法讀取；可開始新局覆寫此存檔。"),
                STATUS_ERROR_COLOR
            );
            setButtonEnabled(button("continue_button"), false);
        }
    }

    private void refreshSaveSlotStatus() {
        SaveSlotInspection inspection = SangoServices.saveGames().inspect(
            SangoServices.DEFAULT_SAVE_SLOT
        );
        setButtonEnabled(button("load_game_button"), false);

        if (inspection.getState() == SaveSlotState.AVAILABLE) {
            setButtonEnabled(button("continue_button"), true);
            if (inspection.hasRecoveryCandidate()) {
                setStatus(
                    text(
                        "status_save_recovery_available",
                        "主要存檔異常，但仍可從暫存或備份繼續遊戲。"
                    ),
                    STATUS_ACTIVE_COLOR
                );
            } else {
                setStatus(
                    text("status_campaign_available", "已有戰局，可選擇「繼續遊戲」。"),
                    STATUS_ACTIVE_COLOR
                );
            }
            return;
        }

        setButtonEnabled(button("continue_button"), false);
        if (inspection.getState() == SaveSlotState.CORRUPT) {
            Gdx.app.error("Lobby", "偵測到損壞存檔：" + inspection.getDiagnosticMessage());
            setStatus(
                text("status_save_corrupt", "存檔無法讀取；可開始新局覆寫此存檔。"),
                STATUS_ERROR_COLOR
            );
        } else {
            setStatus(
                text("status_no_campaign", "尚無戰局存檔；請先開始新局。"),
                STATUS_READY_COLOR
            );
        }
    }

    private void openSettings() {
        refreshSettingsLabels();
        openModal(settingsMask);
    }

    private void toggleMusic() {
        SangoPreferences.setMusicEnabled(!SangoPreferences.isMusicEnabled());
        refreshSettingsLabels();
    }

    private void toggleSound() {
        SangoPreferences.setSoundEnabled(!SangoPreferences.isSoundEnabled());
        refreshSettingsLabels();
    }

    private void refreshSettingsLabels() {
        TextButton musicButton = button("settings_music_button");
        TextButton soundButton = button("settings_sound_button");
        musicButton.setText(
            SangoPreferences.isMusicEnabled()
                ? text("settings_music_on", "背景音樂：開啟")
                : text("settings_music_off", "背景音樂：關閉")
        );
        soundButton.setText(
            SangoPreferences.isSoundEnabled()
                ? text("settings_sound_on", "介面音效：開啟")
                : text("settings_sound_off", "介面音效：關閉")
        );
    }

    private void handleBackAction() {
        if (isAnyModalVisible()) {
            closeAllModals();
            return;
        }
        openModal(exitMask);
    }

    private boolean isAnyModalVisible() {
        return isVisible(settingsMask) || isVisible(exitMask);
    }

    private boolean isVisible(Actor actor) {
        return actor != null && actor.isVisible();
    }

    private void openModal(Actor mask) {
        closeAllModals();
        mask.setVisible(true);
        mask.getColor().a = 0f;
        mask.toFront();
        mask.addAction(Actions.fadeIn(0.16f));
    }

    private void closeAllModals() {
        setVisible(settingsMask, false);
        setVisible(exitMask, false);
    }

    private void setVisible(Actor actor, boolean visible) {
        if (actor != null) {
            actor.clearActions();
            actor.setVisible(visible);
            actor.getColor().a = 1f;
        }
    }

    private void setButtonEnabled(TextButton textButton, boolean enabled) {
        textButton.setDisabled(!enabled);
        textButton.setTouchable(enabled ? Touchable.enabled : Touchable.disabled);
    }

    private void setStatus(String message, Color color) {
        Label statusLabel = ui.getActor("lobby_status", Label.class);
        statusLabel.setText(message);
        statusLabel.setColor(color);
    }

    private TextButton button(String actorId) {
        return ui.getActor(actorId, TextButton.class);
    }

    private String text(String entryName, String fallback, Object... arguments) {
        return Sui.i18n.manager().getText("literal", entryName, fallback, arguments);
    }
}
