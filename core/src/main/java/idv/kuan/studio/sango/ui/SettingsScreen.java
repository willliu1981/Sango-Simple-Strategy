package idv.kuan.studio.sango.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.InputProcessor;
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
import idv.kuan.studio.sango.runtime.SangoServices;
import idv.kuan.studio.sango.runtime.SaveLoadMode;
import idv.kuan.studio.sango.ui.id.ScreenId;
import idv.kuan.studio.sango.ui.support.ScreenBackground;
import idv.kuan.studio.sango.ui.theme.SangoUiStyles;

/**
 * Lobby 與遊戲畫面共用的設定／暫停選單。
 */
public final class SettingsScreen extends SuiScreen {
    private static final String BACKGROUND_PATH = "picture/lobby/sango_lobby_background.png";
    private static final float VOLUME_STEP = 0.10f;

    private final ScreenBackground screenBackground = new ScreenBackground();

    private Actor returnLobbyMask;
    private Actor exitMask;

    @Override
    protected BuiltUI buildUI(UIFactory uiFactory) {
        return uiFactory.begin()
            .skipToRegisterTemplate()
            .skipToRegisterXml()
            .registerXml("ui/settings.xml")
            .skipToBuild()
            .buildUI();
    }

    @Override
    protected void onUIBuilt(BuiltUI builtUI) {
        screenBackground.attach(stage, BACKGROUND_PATH);
        returnLobbyMask = attachModalMask("settings_return_lobby_mask");
        exitMask = attachModalMask("settings_exit_mask");
        applyStyles();
        bindActions();
        Actor main = ui.getActor("main");
        main.getColor().a = 0f;
        main.addAction(Actions.fadeIn(0.22f));
    }

    @Override
    protected void afterShow() {
        closeModals();
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
        SangoUiStyles.applySecondaryButton(button("settings_music_toggle_button"));
        SangoUiStyles.applySecondaryButton(button("settings_music_down_button"));
        SangoUiStyles.applySecondaryButton(button("settings_music_up_button"));
        SangoUiStyles.applySecondaryButton(button("settings_sound_toggle_button"));
        SangoUiStyles.applySecondaryButton(button("settings_sound_down_button"));
        SangoUiStyles.applySecondaryButton(button("settings_sound_up_button"));
        SangoUiStyles.applySecondaryButton(button("settings_save_game_button"));
        SangoUiStyles.applyDangerButton(button("settings_return_lobby_button"));
        SangoUiStyles.applyDangerButton(button("settings_exit_game_button"));
        SangoUiStyles.applyPrimaryButton(button("settings_back_button"));
        SangoUiStyles.applySecondaryButton(button("settings_return_cancel_button"));
        SangoUiStyles.applyDangerButton(button("settings_return_confirm_button"));
        SangoUiStyles.applySecondaryButton(button("settings_exit_cancel_button"));
        SangoUiStyles.applyDangerButton(button("settings_exit_confirm_button"));
    }

    private void bindActions() {
        ui.onClick("settings_music_toggle_button", this::toggleMusic);
        ui.onClick("settings_music_down_button", () -> changeMusicVolume(-VOLUME_STEP));
        ui.onClick("settings_music_up_button", () -> changeMusicVolume(VOLUME_STEP));
        ui.onClick("settings_sound_toggle_button", this::toggleSound);
        ui.onClick("settings_sound_down_button", () -> changeSoundVolume(-VOLUME_STEP));
        ui.onClick("settings_sound_up_button", () -> changeSoundVolume(VOLUME_STEP));
        ui.onClick("settings_save_game_button", this::openSaveScreen);
        ui.onClick("settings_return_lobby_button", () -> openModal(returnLobbyMask));
        ui.onClick("settings_exit_game_button", () -> openModal(exitMask));
        ui.onClick("settings_back_button", this::returnToPreviousScreen);
        ui.onClick("settings_return_cancel_button", this::closeModals);
        ui.onClick("settings_return_confirm_button", this::saveAndReturnToLobby);
        ui.onClick("settings_exit_cancel_button", this::closeModals);
        ui.onClick("settings_exit_confirm_button", this::saveAndExit);
    }

    private void refreshView() {
        button("settings_music_toggle_button").setText(
            SangoPreferences.isMusicEnabled()
                ? text("settings_music_on", "背景音樂：開啟")
                : text("settings_music_off", "背景音樂：關閉")
        );
        button("settings_sound_toggle_button").setText(
            SangoPreferences.isSoundEnabled()
                ? text("settings_sound_on", "遊戲音效：開啟")
                : text("settings_sound_off", "遊戲音效：關閉")
        );
        label("settings_music_volume_label").setText(
            text(
                "settings_music_volume_format",
                "音樂音量：{0}%",
                Math.round(SangoPreferences.getMusicVolume() * 100f)
            )
        );
        label("settings_sound_volume_label").setText(
            text(
                "settings_sound_volume_format",
                "音效音量：{0}%",
                Math.round(SangoPreferences.getSoundVolume() * 100f)
            )
        );

        boolean hasCurrentGame = SangoServices.session().hasCurrentState();
        setButtonEnabled(button("settings_save_game_button"), hasCurrentGame);
        setButtonEnabled(
            button("settings_return_lobby_button"),
            hasCurrentGame && SangoServices.session().getSettingsReturnScreen() != ScreenId.LOBBY
        );
        label("settings_context_label").setText(
            hasCurrentGame
                ? text(
                    "settings_context_game",
                    "目前戰局位於存檔槽 {0}；返回 Lobby 前會先保存。",
                    SangoServices.session().getCurrentSaveSlot()
                )
                : text("settings_context_lobby", "目前位於 Lobby，沒有載入中的戰局。")
        );
    }

    private void toggleMusic() {
        SangoPreferences.setMusicEnabled(!SangoPreferences.isMusicEnabled());
        SangoServices.audio().applyPreferences();
        SangoServices.audio().playSound(SoundEffect.UI_CLICK);
        refreshView();
    }

    private void toggleSound() {
        SangoPreferences.setSoundEnabled(!SangoPreferences.isSoundEnabled());
        SangoServices.audio().applyPreferences();
        SangoServices.audio().playSound(SoundEffect.UI_CLICK);
        refreshView();
    }

    private void changeMusicVolume(float delta) {
        SangoPreferences.setMusicVolume(SangoPreferences.getMusicVolume() + delta);
        SangoServices.audio().applyPreferences();
        SangoServices.audio().playSound(SoundEffect.UI_CLICK);
        refreshView();
    }

    private void changeSoundVolume(float delta) {
        SangoPreferences.setSoundVolume(SangoPreferences.getSoundVolume() + delta);
        SangoServices.audio().playSound(SoundEffect.UI_CLICK);
        refreshView();
    }

    private void openSaveScreen() {
        if (!SangoServices.session().hasCurrentState()) {
            return;
        }
        SangoServices.audio().playSound(SoundEffect.CONFIRM);
        SangoServices.session().openSaveLoad(SaveLoadMode.SAVE, ScreenId.SETTINGS);
        Sui.screens.set(ScreenId.SAVE_LOAD);
    }

    private void saveAndReturnToLobby() {
        closeModals();
        if (!saveCurrentState()) {
            return;
        }
        SangoServices.audio().playSound(SoundEffect.CONFIRM);
        SangoServices.session().clear();
        Sui.screens.set(ScreenId.LOBBY);
    }

    private void saveAndExit() {
        closeModals();
        if (SangoServices.session().hasCurrentState() && !saveCurrentState()) {
            return;
        }
        Gdx.app.exit();
    }

    private boolean saveCurrentState() {
        if (!SangoServices.session().hasCurrentState()) {
            return true;
        }
        try {
            SangoServices.saveCurrentGameCommand().execute(
                SangoServices.session().getCurrentSaveSlot(),
                SangoServices.session().requireCurrentState()
            );
            return true;
        } catch (RuntimeException exception) {
            Gdx.app.error("Settings", "返回或退出前保存失敗。", exception);
            label("settings_context_label").setText(
                text("settings_save_before_exit_failed", "保存失敗，已取消返回或退出。")
            );
            SangoServices.audio().playSound(SoundEffect.COMMAND_ERROR);
            return false;
        }
    }

    private void returnToPreviousScreen() {
        SangoServices.audio().playSound(SoundEffect.CANCEL);
        Sui.screens.set(SangoServices.session().getSettingsReturnScreen());
    }

    private void playExpectedMusic() {
        SangoServices.audio().playMusic(
            SangoServices.session().getSettingsReturnScreen() == ScreenId.LOBBY
                ? MusicTrack.LOBBY
                : MusicTrack.STRATEGY
        );
    }

    private void openModal(Actor mask) {
        closeModals();
        mask.setVisible(true);
        mask.getColor().a = 0f;
        mask.toFront();
        mask.addAction(Actions.fadeIn(0.16f));
        SangoServices.audio().playSound(SoundEffect.UI_CLICK);
    }

    private void closeModals() {
        setVisible(returnLobbyMask, false);
        setVisible(exitMask, false);
    }

    private boolean isModalVisible() {
        return isVisible(returnLobbyMask) || isVisible(exitMask);
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
