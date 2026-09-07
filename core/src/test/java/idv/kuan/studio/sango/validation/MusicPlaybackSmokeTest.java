package idv.kuan.studio.sango.validation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.badlogic.gdx.audio.Music;

import idv.kuan.studio.sango.audio.MusicPlaybackController;
import idv.kuan.studio.sango.audio.MusicTrack;

/** 使用假 Music 驗證轉場及生命週期；不宣稱測試 OpenAL 或 Android 解碼器。 */
public final class MusicPlaybackSmokeTest {
    private static int checks;

    private MusicPlaybackSmokeTest() {
    }

    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 1) {
            throw new IllegalArgumentException("需要 assets 目錄路徑。");
        }
        for (int i = 1; i <= 12; i++) {
            check(MusicTrack.forMonth(i) == MusicTrack.seasonalTracks()[(i - 1) / 3], "全部月份對應四季");
        }
        for (MusicTrack track : MusicTrack.seasonalTracks()) {
            check(Files.size(Path.of(arguments[0]).resolve(track.getAssetPath())) > 100000, "四首來源資產存在且非空");
            check(track.getDurationSeconds() > 200f && track.getDurationSeconds() < 220f, "曲長中繼資料");
        }
        testCrossfade();
        testRapidSwitchAndPause();
        testFailures();
        System.out.println("Sango music state-machine regression: PASS; checks=" + checks);
    }

    private static void testCrossfade() {
        FakeLoader loader = new FakeLoader();
        MusicPlaybackController controller = new MusicPlaybackController(loader::load, 2f);
        controller.request(MusicTrack.SPRING);
        controller.update(0f, true, 0.8f);
        check(controller.activeStreamCount() == 1 && close(controller.gain(MusicTrack.SPRING), 0f), "初次播放由零音量開始");
        controller.update(1f, true, 0.8f);
        check(close(loader.latest(MusicTrack.SPRING).volume, 0.4f), "初次一秒淡入到主音量一半");
        controller.update(1f, true, 0.8f);
        FakeMusic spring = loader.latest(MusicTrack.SPRING);
        spring.position = 30f;
        check(close(spring.volume, 0.8f) && spring.looping, "兩秒達主音量且循環");
        controller.request(MusicTrack.SPRING);
        controller.update(1f, true, 0.8f);
        check(loader.count(MusicTrack.SPRING) == 1 && spring.playCalls == 1 && spring.position == 30f,
            "同季切畫面不重建或從頭播放");
        controller.request(MusicTrack.SUMMER);
        controller.update(0f, true, 0.8f);
        check(controller.activeStreamCount() == 2 && close(spring.volume, 0.8f), "換季不硬切舊曲");
        controller.update(1f, true, 0.8f);
        check(close(spring.volume, 0.4f) && close(loader.latest(MusicTrack.SUMMER).volume, 0.4f), "中點兩曲各半");
        controller.update(1f, true, 0.8f);
        check(spring.disposed && spring.stopCalls == 1 && controller.activeStreamCount() == 1, "轉場後釋放舊串流");
        check(close(loader.latest(MusicTrack.SUMMER).volume, 0.8f), "新季音量到位");
        controller.dispose();
        controller.dispose();
        check(loader.allDisposedExactlyOnce(), "重複 dispose 不重複釋放");
        controller.request(MusicTrack.AUTUMN);
        controller.update(2f, true, 1f);
        check(controller.activeStreamCount() == 0, "已釋放的播放器不重建資源");
    }

    private static void testRapidSwitchAndPause() {
        FakeLoader loader = new FakeLoader();
        MusicPlaybackController controller = new MusicPlaybackController(loader::load, 2f);
        controller.request(MusicTrack.SPRING);
        controller.update(2f, true, 1f);
        controller.request(MusicTrack.SUMMER);
        controller.update(0.5f, true, 1f);
        check(close(controller.gain(MusicTrack.SPRING), 0.75f), "第一段轉場當下音量");
        controller.request(MusicTrack.AUTUMN);
        controller.update(0f, true, 1f);
        check(close(controller.gain(MusicTrack.SPRING), 0.75f)
            && close(controller.gain(MusicTrack.SUMMER), 0.25f), "快速換曲沿用當下音量，不跳回全音量");
        controller.update(0.5f, true, 1f);
        float gainSum = controller.gain(MusicTrack.SPRING) + controller.gain(MusicTrack.SUMMER)
            + controller.gain(MusicTrack.AUTUMN);
        check(close(gainSum, 1f), "多曲快速轉場不疊加為超額總音量");
        controller.setUserPaused(true);
        float pausedGain = controller.gain(MusicTrack.AUTUMN);
        controller.update(100f, true, 1f);
        check(close(controller.gain(MusicTrack.AUTUMN), pausedGain), "手動暫停凍結轉場時間");
        for (FakeMusic music : loader.instances) {
            check(!music.playing, "暫停涵蓋所有轉場串流");
        }
        controller.pause();
        controller.resume();
        // 模擬某些後端在 resume 自動恢復全部音樂。
        loader.latest(MusicTrack.AUTUMN).playing = true;
        controller.update(0f, true, 1f);
        check(controller.isUserPaused() && !loader.latest(MusicTrack.AUTUMN).playing,
            "平台恢復不能覆蓋使用者暫停");
        controller.setUserPaused(false);
        controller.update(2f, true, 1f);
        check(controller.activeStreamCount() == 1 && loader.latest(MusicTrack.AUTUMN).playing, "解除暫停後轉場完成");
        controller.pause();
        controller.update(10f, true, 1f);
        check(!loader.latest(MusicTrack.AUTUMN).playing, "平台 pause 停止播放");
        controller.resume();
        controller.update(0f, true, 1f);
        check(loader.latest(MusicTrack.AUTUMN).playing, "平台 resume 恢復非手動暫停曲目");
        controller.update(0f, false, 1f);
        controller.request(MusicTrack.WINTER);
        controller.update(10f, false, 1f);
        check(loader.count(MusicTrack.WINTER) == 0, "音樂關閉時換季只記錄目標");
        controller.update(2f, true, 0.5f);
        check(controller.requestedTrack() == MusicTrack.WINTER && controller.activeStreamCount() == 1,
            "重新開啟直接前往最新季節");
        check(close(loader.latest(MusicTrack.WINTER).volume, 0.5f), "重新開啟沿用音量設定");
        controller.update(Float.NaN, true, Float.NaN);
        check(close(loader.latest(MusicTrack.WINTER).volume, 0f), "非法浮點音量不傳入後端");
        for (int i = 0; i < 1000; i++) {
            controller.request(MusicTrack.seasonalTracks()[i % 4]);
            controller.update(0.01f, true, 1f);
            check(controller.activeStreamCount() <= 4, "連續換曲串流數有界");
        }
        controller.update(2f, true, 1f);
        check(controller.activeStreamCount() == 1, "大量換曲後只留當前串流");
        controller.dispose();
        check(loader.allDisposedExactlyOnce(), "快速換曲後無未釋放串流");
    }

    private static void testFailures() {
        FakeLoader loader = new FakeLoader();
        MusicPlaybackController controller = new MusicPlaybackController(loader::load, 2f);
        controller.request(MusicTrack.SPRING);
        controller.update(2f, true, 1f);
        loader.failedTrack = MusicTrack.SUMMER;
        controller.request(MusicTrack.SUMMER);
        controller.update(2f, true, 1f);
        check(controller.hasRequestedTrackFailure() && controller.activeStreamCount() == 1, "載入失敗保留舊曲不崩潰");
        check(close(loader.latest(MusicTrack.SPRING).volume, 1f), "失敗回退不使現有曲目消失");
        int attempts = loader.attempts;
        for (int i = 0; i < 10; i++) {
            controller.update(1f, true, 1f);
        }
        check(loader.attempts == attempts, "載入失敗不每幀重試");
        loader.failedTrack = null;
        controller.retryRequestedTrack();
        controller.update(2f, true, 1f);
        check(!controller.hasRequestedTrackFailure() && loader.latest(MusicTrack.SUMMER).playing, "顯式重新選曲可重試");
        FakeMusic broken = loader.latest(MusicTrack.SUMMER);
        broken.failVolume = true;
        controller.update(0f, true, 1f);
        check(controller.activeStreamCount() == 0 && controller.hasRequestedTrackFailure() && broken.disposed,
            "串流執行中失敗仍釋放並回報");
        controller.dispose();
        check(loader.allDisposedExactlyOnce(), "失敗流程無資源遺留");
    }

    private static boolean close(float actual, float expected) {
        return Math.abs(actual - expected) < 0.00001f;
    }

    private static void check(boolean condition, String description) {
        checks += 1;
        if (!condition) {
            throw new AssertionError(description);
        }
    }

    private static final class FakeLoader {
        private final Map<MusicTrack, List<FakeMusic>> perTrack = new EnumMap<>(MusicTrack.class);
        private final List<FakeMusic> instances = new ArrayList<>();
        private MusicTrack failedTrack;
        private int attempts;

        private Music load(MusicTrack track) {
            attempts += 1;
            if (track == failedTrack) {
                throw new IllegalStateException("模擬載入失敗");
            }
            FakeMusic music = new FakeMusic();
            instances.add(music);
            perTrack.computeIfAbsent(track, ignored -> new ArrayList<>()).add(music);
            return music;
        }

        private FakeMusic latest(MusicTrack track) {
            List<FakeMusic> list = perTrack.get(track);
            return list.get(list.size() - 1);
        }

        private int count(MusicTrack track) {
            return perTrack.getOrDefault(track, List.of()).size();
        }

        private boolean allDisposedExactlyOnce() {
            for (FakeMusic music : instances) {
                if (music.disposeCalls != 1) {
                    return false;
                }
            }
            return true;
        }
    }

    private static final class FakeMusic implements Music {
        private boolean playing;
        private boolean looping;
        private boolean disposed;
        private boolean failVolume;
        private float volume;
        private float position;
        private int playCalls;
        private int stopCalls;
        private int disposeCalls;

        @Override
        public void play() {
            if (disposed) {
                throw new IllegalStateException("已釋放串流");
            }
            playing = true;
            playCalls += 1;
        }

        @Override
        public void pause() {
            playing = false;
        }

        @Override
        public void stop() {
            playing = false;
            stopCalls += 1;
            position = 0f;
        }

        @Override
        public boolean isPlaying() {
            return playing;
        }

        @Override
        public void setLooping(boolean looping) {
            this.looping = looping;
        }

        @Override
        public boolean isLooping() {
            return looping;
        }

        @Override
        public void setVolume(float volume) {
            if (failVolume) {
                throw new IllegalStateException("模擬輸出裝置失敗");
            }
            this.volume = volume;
        }

        @Override
        public float getVolume() {
            return volume;
        }

        @Override
        public void setPan(float pan, float volume) {
            this.volume = volume;
        }

        @Override
        public void setPosition(float position) {
            this.position = position;
        }

        @Override
        public float getPosition() {
            return position;
        }

        @Override
        public void dispose() {
            disposed = true;
            playing = false;
            disposeCalls += 1;
        }

        @Override
        public void setOnCompletionListener(OnCompletionListener listener) {
            // 測試使用循環模式，不模擬歌曲自然結束事件。
        }
    }
}
