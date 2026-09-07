package idv.kuan.studio.sango.audio;

import java.util.EnumMap;
import java.util.Map;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;

import idv.kuan.studio.sango.data.SangoPreferences;

/** BGM 與音效的集中入口；鑑賞模式與遊戲季節模式共享串流管理，不同時搶播。 */
public final class SangoAudioService {
    public static final float CROSSFADE_SECONDS = 2f;
    private final MusicPlaybackController playback = new MusicPlaybackController(this::loadMusic, CROSSFADE_SECONDS);
    private final Map<SoundEffect, Sound> soundByEffect = new EnumMap<>(SoundEffect.class);
    private boolean galleryActive;
    private boolean paused;
    private boolean disposed;

    public void playMusic(MusicTrack musicTrack) {
        if (!disposed && !galleryActive) {
            playback.request(musicTrack);
        }
    }

    public void enterMusicPlayer(MusicTrack musicTrack) {
        if (disposed || musicTrack == null || musicTrack == MusicTrack.LOBBY) {
            return;
        }
        galleryActive = true;
        playback.setUserPaused(false);
        playback.request(musicTrack);
        playback.retryRequestedTrack();
    }

    public void exitMusicPlayer(MusicTrack restoreTrack) {
        galleryActive = false;
        playback.setUserPaused(false);
        playback.request(restoreTrack);
    }

    public void toggleMusicPlayerPause() {
        if (galleryActive) {
            playback.setUserPaused(!playback.isUserPaused());
            if (!playback.isUserPaused()) {
                playback.retryRequestedTrack();
            }
        }
    }

    public boolean isMusicPlayerPaused() {
        return playback.isUserPaused();
    }

    public boolean hasMusicLoadFailure() {
        return playback.hasRequestedTrackFailure();
    }

    public float getMusicPositionSeconds() {
        return playback.positionSeconds();
    }

    public void update(float deltaSeconds) {
        if (!disposed) {
            playback.update(deltaSeconds, SangoPreferences.isMusicEnabled(),
                SangoPreferences.getMusicVolume());
        }
    }

    public void playSound(SoundEffect soundEffect) {
        if (disposed || paused || soundEffect == null || !SangoPreferences.isSoundEnabled()) {
            return;
        }
        Sound sound = loadSound(soundEffect);
        if (sound != null) {
            sound.play(SangoPreferences.getSoundVolume());
        }
    }

    public void applyPreferences() {
        update(0f);
    }

    public void pause() {
        paused = true;
        playback.pause();
    }

    public void resume() {
        paused = false;
        playback.resume();
        applyPreferences();
    }

    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        playback.dispose();
        for (Sound sound : soundByEffect.values()) {
            sound.dispose();
        }
        soundByEffect.clear();
    }

    private Music loadMusic(MusicTrack musicTrack) {
        try {
            return Gdx.audio.newMusic(Gdx.files.internal(musicTrack.getAssetPath()));
        } catch (RuntimeException exception) {
            logAudioError("無法載入背景音樂：" + musicTrack.getAssetPath(), exception);
            return null;
        }
    }

    private Sound loadSound(SoundEffect soundEffect) {
        Sound cachedSound = soundByEffect.get(soundEffect);
        if (cachedSound != null) {
            return cachedSound;
        }
        try {
            Sound loadedSound = Gdx.audio.newSound(Gdx.files.internal(soundEffect.getAssetPath()));
            soundByEffect.put(soundEffect, loadedSound);
            return loadedSound;
        } catch (RuntimeException exception) {
            logAudioError("無法載入音效：" + soundEffect.getAssetPath(), exception);
            return null;
        }
    }

    private void logAudioError(String message, RuntimeException exception) {
        if (Gdx.app != null) {
            Gdx.app.error("SangoAudio", message, exception);
        }
    }
}
