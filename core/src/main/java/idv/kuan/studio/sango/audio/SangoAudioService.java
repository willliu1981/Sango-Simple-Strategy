package idv.kuan.studio.sango.audio;

import java.util.EnumMap;
import java.util.Map;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;

import idv.kuan.studio.sango.data.SangoPreferences;

/**
 * 集中管理背景音樂與短音效，避免 Screen 各自重複載入及釋放資源。
 */
public final class SangoAudioService {
    private final Map<MusicTrack, Music> musicByTrack = new EnumMap<>(MusicTrack.class);
    private final Map<SoundEffect, Sound> soundByEffect = new EnumMap<>(SoundEffect.class);

    private MusicTrack desiredTrack;
    private MusicTrack currentTrack;
    private boolean paused;
    private boolean disposed;

    public void playMusic(MusicTrack musicTrack) {
        if (disposed || musicTrack == null) {
            return;
        }
        desiredTrack = musicTrack;
        if (!SangoPreferences.isMusicEnabled() || paused) {
            stopCurrentMusic();
            return;
        }
        if (currentTrack == musicTrack) {
            Music currentMusic = musicByTrack.get(currentTrack);
            if (currentMusic != null) {
                currentMusic.setVolume(SangoPreferences.getMusicVolume());
                if (!currentMusic.isPlaying()) {
                    currentMusic.play();
                }
            }
            return;
        }

        stopCurrentMusic();
        Music music = loadMusic(musicTrack);
        if (music == null) {
            return;
        }
        music.setLooping(true);
        music.setVolume(SangoPreferences.getMusicVolume());
        music.play();
        currentTrack = musicTrack;
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
        if (disposed) {
            return;
        }
        if (!SangoPreferences.isMusicEnabled() || paused) {
            stopCurrentMusic();
            return;
        }
        if (desiredTrack != null) {
            playMusic(desiredTrack);
        }
    }

    public void pause() {
        paused = true;
        if (currentTrack != null) {
            Music currentMusic = musicByTrack.get(currentTrack);
            if (currentMusic != null && currentMusic.isPlaying()) {
                currentMusic.pause();
            }
        }
    }

    public void resume() {
        paused = false;
        applyPreferences();
    }

    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        for (Music music : musicByTrack.values()) {
            music.dispose();
        }
        for (Sound sound : soundByEffect.values()) {
            sound.dispose();
        }
        musicByTrack.clear();
        soundByEffect.clear();
        currentTrack = null;
        desiredTrack = null;
    }

    private Music loadMusic(MusicTrack musicTrack) {
        Music cachedMusic = musicByTrack.get(musicTrack);
        if (cachedMusic != null) {
            return cachedMusic;
        }
        try {
            Music loadedMusic = Gdx.audio.newMusic(Gdx.files.internal(musicTrack.getAssetPath()));
            musicByTrack.put(musicTrack, loadedMusic);
            return loadedMusic;
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

    private void stopCurrentMusic() {
        if (currentTrack == null) {
            return;
        }
        Music currentMusic = musicByTrack.get(currentTrack);
        if (currentMusic != null) {
            currentMusic.stop();
        }
        currentTrack = null;
    }

    private void logAudioError(String message, RuntimeException exception) {
        if (Gdx.app != null) {
            Gdx.app.error("SangoAudio", message, exception);
        }
    }
}
