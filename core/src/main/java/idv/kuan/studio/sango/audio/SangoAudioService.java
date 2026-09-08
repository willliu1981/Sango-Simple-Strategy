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
    private volatile MusicTrack completedGalleryTrack;
    private volatile boolean galleryCompletionPending;
    private boolean paused;
    private boolean disposed;

    public void playMusic(MusicTrack musicTrack) {
        if (!disposed && !galleryActive) {
            playback.request(musicTrack);
        }
    }

    public void enterMusicPlayer(MusicTrack musicTrack, boolean loopTrack) {
        if (disposed || musicTrack == null) {
            return;
        }
        galleryActive = true;
        completedGalleryTrack = null;
        galleryCompletionPending = false;
        playback.setUserPaused(false);
        playback.request(musicTrack, loopTrack, this::onGalleryTrackCompleted);
        playback.retryRequestedTrack();
    }

    public void setMusicPlayerLooping(boolean loopTrack) {
        if (galleryActive) {
            playback.setRequestedTrackBehavior(loopTrack, this::onGalleryTrackCompleted);
        }
    }

    public void restartMusicPlayerTrack() {
        if (!galleryActive) {
            return;
        }
        completedGalleryTrack = null;
        galleryCompletionPending = false;
        playback.setUserPaused(false);
        playback.restartRequestedTrack();
        playback.retryRequestedTrack();
    }

    public void exitMusicPlayer(MusicTrack restoreTrack) {
        galleryActive = false;
        completedGalleryTrack = null;
        galleryCompletionPending = false;
        playback.setUserPaused(false);
        playback.request(restoreTrack);
    }

    public void toggleMusicPlayerPause() {
        if (galleryActive) {
            boolean resumePlayback = isMusicPlayerPaused();
            if (resumePlayback && completedGalleryTrack != null) {
                completedGalleryTrack = null;
                galleryCompletionPending = false;
                playback.restartRequestedTrack();
            }
            playback.setUserPaused(!resumePlayback);
            if (!playback.isUserPaused()) {
                playback.retryRequestedTrack();
            }
        }
    }

    public MusicTrack pollCompletedMusicPlayerTrack() {
        if (!galleryCompletionPending) {
            return null;
        }
        galleryCompletionPending = false;
        return completedGalleryTrack;
    }

    public boolean hasMusicPlayerCompleted() {
        return completedGalleryTrack != null;
    }

    public boolean isMusicPlayerPaused() {
        return playback.isUserPaused() || completedGalleryTrack != null;
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

    private void onGalleryTrackCompleted(MusicTrack musicTrack) {
        if (!galleryActive || playback.requestedTrack() != musicTrack) {
            return;
        }
        completedGalleryTrack = musicTrack;
        galleryCompletionPending = true;
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
