package idv.kuan.studio.sango.audio;

import java.util.EnumMap;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

import com.badlogic.gdx.audio.Music;

/**
 * 可用假 Music 測試的播放狀態機。淡化在應用程式層更新，不綁定任何 Screen。
 * 快速連續換季從各串流當下音量接續轉場，不重新把舊曲音量拉回 100%。
 */
public final class MusicPlaybackController {
    private final Function<MusicTrack, Music> loader;
    private final float fadeSeconds;
    private final Map<MusicTrack, Voice> voices = new EnumMap<>(MusicTrack.class);
    private volatile MusicTrack requestedTrack;
    private volatile boolean requestedLooping = true;
    private volatile Consumer<MusicTrack> requestedCompletionListener;
    private MusicTrack failedTrack;
    private boolean transitionPending;
    private boolean platformPaused;
    private boolean userPaused;
    private boolean disposed;
    private float transitionElapsed;

    public MusicPlaybackController(Function<MusicTrack, Music> loader, float fadeSeconds) {
        this.loader = Objects.requireNonNull(loader, "loader");
        if (!Float.isFinite(fadeSeconds) || fadeSeconds <= 0f) {
            throw new IllegalArgumentException("淡化時間必須大於零。");
        }
        this.fadeSeconds = fadeSeconds;
    }

    public void request(MusicTrack track) {
        request(track, true, null);
    }

    public void request(MusicTrack track, boolean looping, Consumer<MusicTrack> completionListener) {
        if (disposed || track == null) {
            return;
        }
        boolean trackChanged = requestedTrack != track;
        requestedTrack = track;
        requestedLooping = looping;
        requestedCompletionListener = completionListener;
        failedTrack = null;
        if (trackChanged || !voices.containsKey(track)) {
            transitionPending = true;
        } else {
            configureRequestedVoice();
            Voice voice = voices.get(requestedTrack);
            if (voice != null && voice.completed) {
                restartRequestedTrack();
            }
        }
    }

    public void setRequestedTrackBehavior(boolean looping, Consumer<MusicTrack> completionListener) {
        if (disposed || requestedTrack == null) {
            return;
        }
        requestedLooping = looping;
        requestedCompletionListener = completionListener;
        configureRequestedVoice();
    }

    public void restartRequestedTrack() {
        if (disposed || requestedTrack == null) {
            return;
        }
        Voice voice = voices.get(requestedTrack);
        if (voice == null) {
            transitionPending = true;
            return;
        }
        try {
            voice.music.setPosition(0f);
            voice.completed = false;
        } catch (RuntimeException exception) {
            failRequestedTrack();
        }
    }

    public void retryRequestedTrack() {
        if (!disposed && requestedTrack != null && failedTrack != null) {
            failedTrack = null;
            transitionPending = true;
        }
    }

    public void update(float deltaSeconds, boolean enabled, float masterVolume) {
        if (disposed) {
            return;
        }
        if (!enabled || platformPaused || userPaused) {
            pauseVoices();
            return;
        }
        if (transitionPending) {
            beginTransition();
        }
        float validDelta = Float.isFinite(deltaSeconds) ? Math.max(0f, deltaSeconds) : 0f;
        transitionElapsed = Math.min(fadeSeconds, transitionElapsed + validDelta);
        float progress = transitionElapsed / fadeSeconds;
        float volume = Float.isFinite(masterVolume) ? Math.max(0f, Math.min(1f, masterVolume)) : 0f;
        Iterator<Map.Entry<MusicTrack, Voice>> iterator = voices.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<MusicTrack, Voice> entry = iterator.next();
            Voice voice = entry.getValue();
            voice.gain = voice.startGain + (voice.targetGain - voice.startGain) * progress;
            if (progress >= 1f && voice.targetGain == 0f) {
                release(voice.music);
                iterator.remove();
                continue;
            }
            try {
                voice.music.setVolume(Math.max(0f, Math.min(1f,
                    voice.gain * volume * entry.getKey().getOutputGain())));
                if (!voice.completed && !voice.music.isPlaying()) {
                    voice.music.play();
                }
            } catch (RuntimeException exception) {
                if (entry.getKey() == requestedTrack) {
                    failRequestedTrack();
                    return;
                }
                iterator.remove();
                release(voice.music);
            }
        }
    }

    private void beginTransition() {
        transitionPending = false;
        Voice requestedVoice = voices.get(requestedTrack);
        if (requestedVoice == null) {
            Music loadedMusic = null;
            try {
                loadedMusic = loader.apply(requestedTrack);
                if (loadedMusic == null) {
                    failRequestedTrack();
                    return;
                }
                loadedMusic.setVolume(0f);
                requestedVoice = new Voice(requestedTrack, loadedMusic);
                voices.put(requestedTrack, requestedVoice);
            } catch (RuntimeException exception) {
                failRequestedTrack();
                if (loadedMusic != null) {
                    release(loadedMusic);
                }
                return;
            }
        }
        configureRequestedVoice();
        if (hasRequestedTrackFailure()) {
            return;
        }
        transitionElapsed = 0f;
        for (Map.Entry<MusicTrack, Voice> entry : voices.entrySet()) {
            Voice voice = entry.getValue();
            voice.startGain = voice.gain;
            voice.targetGain = entry.getKey() == requestedTrack ? 1f : 0f;
        }
    }

    private void configureRequestedVoice() {
        Voice voice = voices.get(requestedTrack);
        if (voice == null) {
            return;
        }
        try {
            voice.music.setLooping(requestedLooping);
            voice.music.setOnCompletionListener(music -> {
                if (disposed || failedTrack == voice.track || voices.get(voice.track) != voice
                    || requestedTrack != voice.track || requestedLooping) {
                    return;
                }
                voice.completed = true;
                if (requestedCompletionListener != null) {
                    requestedCompletionListener.accept(voice.track);
                }
            });
        } catch (RuntimeException exception) {
            failRequestedTrack();
        }
    }

    /** 失敗不回退舊曲；先移除身份，再釋放，避免後端的延遲完成事件繼續切歌。 */
    private void failRequestedTrack() {
        failedTrack = requestedTrack;
        transitionPending = false;
        var failedVoices = new ArrayList<>(voices.values());
        voices.clear();
        for (Voice voice : failedVoices) {
            release(voice.music);
        }
    }

    public void setUserPaused(boolean paused) {
        userPaused = paused;
        if (paused) {
            pauseVoices();
        }
    }

    public boolean isUserPaused() {
        return userPaused;
    }

    public void pause() {
        platformPaused = true;
        pauseVoices();
    }

    public void resume() {
        platformPaused = false;
        // 使用者手動暫停的狀態不會被平台 resume 覆蓋。
    }

    public MusicTrack requestedTrack() {
        return requestedTrack;
    }

    public boolean hasRequestedTrackFailure() {
        return failedTrack != null && failedTrack == requestedTrack;
    }

    public float positionSeconds() {
        Voice voice = voices.get(requestedTrack);
        if (voice == null) {
            return 0f;
        }
        try {
            float position = voice.music.getPosition();
            return Float.isFinite(position) ? Math.max(0f, position) : 0f;
        } catch (RuntimeException exception) {
            return 0f;
        }
    }

    public int activeStreamCount() {
        return voices.size();
    }

    public float gain(MusicTrack track) {
        Voice voice = voices.get(track);
        return voice == null ? 0f : voice.gain;
    }

    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        for (Voice voice : voices.values()) {
            release(voice.music);
        }
        voices.clear();
        requestedTrack = null;
    }

    private void pauseVoices() {
        for (Voice voice : voices.values()) {
            try {
                if (voice.music.isPlaying()) {
                    voice.music.pause();
                }
            } catch (RuntimeException exception) {
                // 裝置暫停時不讓音訊錯誤中斷存檔及 Application 生命週期。
            }
        }
    }

    private void release(Music music) {
        try {
            music.stop();
        } catch (RuntimeException exception) {
            // 即使 stop 失敗，仍嘗試釋放解碼器。
        }
        try {
            music.dispose();
        } catch (RuntimeException exception) {
            // 裝置資源已失效時仍需釋放其餘曲目。
        }
    }

    private static final class Voice {
        private final MusicTrack track;
        private final Music music;
        private volatile boolean completed;
        private float gain;
        private float startGain;
        private float targetGain;

        private Voice(MusicTrack track, Music music) {
            this.track = track;
            this.music = music;
        }
    }
}
