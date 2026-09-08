package idv.kuan.studio.sango.ui;

import java.util.Locale;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;

import idv.kuan.studio.libgdx.simpleui.Sui;
import idv.kuan.studio.libgdx.simpleui.SuiScreen;
import idv.kuan.studio.libgdx.simpleui.builder.BuiltUI;
import idv.kuan.studio.sango.audio.MusicTrack;
import idv.kuan.studio.sango.audio.SoundEffect;
import idv.kuan.studio.sango.data.SangoPreferences;
import idv.kuan.studio.sango.runtime.SangoServices;
import idv.kuan.studio.sango.ui.id.ScreenId;
import idv.kuan.studio.sango.ui.support.ScreenBackground;
import idv.kuan.studio.sango.ui.support.ScreenMusic;
import idv.kuan.studio.sango.ui.theme.SangoUiStyles;

/** 不推進月份、不消耗 AP 的音樂鑑賞。 */
public final class MusicPlayerScreen extends SuiScreen {
    private final ScreenBackground background = new ScreenBackground();
    private final MusicTrack[] tracks = MusicTrack.galleryTracks();
    private PlaybackMode playbackMode = PlaybackMode.REPEAT_ONE;
    private int selectedIndex;
    private float refreshTimer;

    @Override
    protected BuiltUI buildUI(UIFactory uiFactory) {
        return uiFactory.begin().skipToRegisterTemplate().skipToRegisterXml()
            .registerXml("ui/music_player.xml").skipToBuild().buildUI();
    }

    @Override
    protected void onUIBuilt(BuiltUI builtUI) {
        background.attach(stage, "picture/lobby/sango_lobby_background.png");
        for (int i = 0; i < tracks.length; i++) {
            int trackIndex = i;
            ui.onClick("music_track_" + i + "_button", () -> selectTrack(trackIndex));
        }
        SangoUiStyles.applySecondaryButton(button("music_previous_button"));
        SangoUiStyles.applyPrimaryButton(button("music_play_pause_button"));
        SangoUiStyles.applySecondaryButton(button("music_next_button"));
        SangoUiStyles.applySecondaryButton(button("music_back_button"));
        SangoUiStyles.applySecondaryButton(button("music_mode_button"));
        ui.onClick("music_previous_button", () -> selectTrack((selectedIndex + tracks.length - 1) % tracks.length));
        ui.onClick("music_next_button", () -> selectTrack((selectedIndex + 1) % tracks.length));
        ui.onClick("music_play_pause_button", () -> {
            SangoServices.audio().toggleMusicPlayerPause();
            refreshView();
        });
        ui.onClick("music_mode_button", () -> {
            playbackMode = playbackMode.next();
            SangoServices.audio().setMusicPlayerLooping(playbackMode == PlaybackMode.REPEAT_ONE);
            refreshView();
        });
        ui.onClick("music_back_button", this::returnToPreviousScreen);
    }

    @Override
    protected void afterShow() {
        selectTrack(selectedIndex);
    }

    @Override
    protected void beforeActDraw(float delta) {
        MusicTrack completedTrack = SangoServices.audio().pollCompletedMusicPlayerTrack();
        if (completedTrack != null && playbackMode == PlaybackMode.REPEAT_ALL
            && completedTrack == tracks[selectedIndex]) {
            selectTrack((selectedIndex + 1) % tracks.length);
        }
        refreshTimer += delta;
        if (refreshTimer >= 0.2f) {
            refreshTimer = 0f;
            refreshView();
        }
    }

    private void selectTrack(int trackIndex) {
        selectedIndex = trackIndex;
        SangoServices.audio().enterMusicPlayer(
            tracks[selectedIndex], playbackMode == PlaybackMode.REPEAT_ONE
        );
        for (int i = 0; i < tracks.length; i++) {
            TextButton trackButton = button("music_track_" + i + "_button");
            if (i == selectedIndex) {
                SangoUiStyles.applySelectedButton(trackButton);
            } else {
                SangoUiStyles.applySecondaryButton(trackButton);
            }
        }
        refreshView();
    }

    private void refreshView() {
        if (ui == null) {
            return;
        }
        MusicTrack track = tracks[selectedIndex];
        label("music_title_label").setText(text(track.getTitleKey(), track.name()));
        label("music_season_label").setText(text(track.getSeasonKey(), ""));
        label("music_time_label").setText(clock(SangoServices.audio().getMusicPositionSeconds())
            + " / " + clock(track.getDurationSeconds()));
        boolean paused = SangoServices.audio().isMusicPlayerPaused();
        button("music_play_pause_button").setText(paused
            ? text("music_play", "播放") : text("music_pause", "暫停"));
        boolean enabled = SangoPreferences.isMusicEnabled();
        button("music_mode_button").setText(text(playbackMode.textKey, playbackMode.fallbackText));
        if (SangoServices.audio().hasMusicLoadFailure()) {
            label("music_status_label").setText(text("music_load_failed", "音樂載入失敗，請重新選曲。"));
        } else if (!enabled || SangoPreferences.getMusicVolume() == 0f) {
            label("music_status_label").setText(text("music_muted_note", "音樂已關閉或音量為零，可在設定調整。"));
        } else if (SangoServices.audio().hasMusicPlayerCompleted()) {
            label("music_status_label").setText(text("music_completed_note", "目前曲目已播放完畢。"));
        } else if (paused) {
            label("music_status_label").setText(text("music_paused_note", "已暫停；播放會由目前位置繼續。"));
        } else {
            label("music_status_label").setText(text(
                playbackMode.statusKey, playbackMode.fallbackStatus
            ));
        }
    }

    private void returnToPreviousScreen() {
        ScreenId returnScreen = SangoServices.session().getMusicPlayerReturnScreen();
        SangoServices.audio().exitMusicPlayer(ScreenMusic.resolve(returnScreen));
        SangoServices.audio().playSound(SoundEffect.CANCEL);
        Sui.screens.set(returnScreen);
    }

    @Override
    protected InputProcessor createInputProcessor(Stage stage) {
        InputAdapter navigation = new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                if (keycode == Input.Keys.BACK || keycode == Input.Keys.ESCAPE) {
                    returnToPreviousScreen();
                    return true;
                }
                return false;
            }
        };
        return new InputMultiplexer(navigation, stage);
    }

    @Override
    protected void afterResize(int width, int height) {
        background.resize(stage);
    }

    @Override
    protected void beforeDispose() {
        background.remove();
        super.beforeDispose();
    }

    private String clock(float seconds) {
        int wholeSeconds = Math.max(0, (int) seconds);
        return String.format(Locale.ROOT, "%02d:%02d", wholeSeconds / 60, wholeSeconds % 60);
    }

    private TextButton button(String actorId) {
        return ui.getActor(actorId, TextButton.class);
    }

    private Label label(String actorId) {
        return ui.getActor(actorId, Label.class);
    }

    private String text(String key, String fallback) {
        return Sui.i18n.manager().getText("literal", key, fallback);
    }

    private enum PlaybackMode {
        PLAY_ONCE("music_mode_play_once", "單曲播放", "music_play_once_note", "播放完目前曲目後停止。"),
        REPEAT_ONE("music_mode_repeat_one", "單曲循環", "music_repeat_one_note", "單曲循環播放；切換曲目以兩秒淡入淡出銜接。"),
        REPEAT_ALL("music_mode_repeat_all", "全部循環", "music_repeat_all_note", "依清單順序播放，最後一首結束後回到第一首。");

        private final String textKey;
        private final String fallbackText;
        private final String statusKey;
        private final String fallbackStatus;

        PlaybackMode(String textKey, String fallbackText, String statusKey, String fallbackStatus) {
            this.textKey = textKey;
            this.fallbackText = fallbackText;
            this.statusKey = statusKey;
            this.fallbackStatus = fallbackStatus;
        }

        private PlaybackMode next() {
            PlaybackMode[] modes = values();
            return modes[(ordinal() + 1) % modes.length];
        }
    }
}
