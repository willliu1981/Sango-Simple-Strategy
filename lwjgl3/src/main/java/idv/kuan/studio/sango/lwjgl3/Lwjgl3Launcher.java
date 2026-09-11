package idv.kuan.studio.sango.lwjgl3;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;

import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import idv.kuan.studio.sango.Main;

/**
 * 啟動 Desktop（LWJGL3）版本。
 */
public final class Lwjgl3Launcher {
    private static final int DEFAULT_WINDOW_WIDTH = 1280;
    private static final int DEFAULT_WINDOW_HEIGHT = 720;
    private static final int MIN_WINDOW_WIDTH = 960;
    private static final int MIN_WINDOW_HEIGHT = 540;
    private static final String WINDOW_PREFERENCES_NODE = "window";
    private static final String WINDOW_WIDTH_KEY = "width";
    private static final String WINDOW_HEIGHT_KEY = "height";

    private Lwjgl3Launcher() {
    }

    public static void main(String[] arguments) {
        if (StartupHelper.startNewJvmIfRequired()) {
            return;
        }
        createApplication();
    }

    private static Lwjgl3Application createApplication() {
        return new Lwjgl3Application(
            new RememberingWindowSizeListener(new Main()),
            getDefaultConfiguration()
        );
    }

    private static Lwjgl3ApplicationConfiguration getDefaultConfiguration() {
        Lwjgl3ApplicationConfiguration configuration = new Lwjgl3ApplicationConfiguration();
        configuration.setTitle("Sango — 征服世界");
        configuration.useVsync(true);
        configuration.setForegroundFPS(
            Lwjgl3ApplicationConfiguration.getDisplayMode().refreshRate
        );
        int[] windowSize = loadWindowSize();
        configuration.setWindowedMode(windowSize[0], windowSize[1]);
        configuration.setWindowSizeLimits(MIN_WINDOW_WIDTH, MIN_WINDOW_HEIGHT, -1, -1);
        configuration.setWindowIcon(
            "sango128.png",
            "sango64.png",
            "sango32.png",
            "sango16.png"
        );
        return configuration;
    }

    private static int[] loadWindowSize() {
        int width = DEFAULT_WINDOW_WIDTH;
        int height = DEFAULT_WINDOW_HEIGHT;
        try {
            Preferences preferences = windowPreferences();
            width = preferences.getInt(WINDOW_WIDTH_KEY, width);
            height = preferences.getInt(WINDOW_HEIGHT_KEY, height);
        } catch (RuntimeException ignored) {
            // 偏好不可用時仍以預設尺寸啟動，不阻擋遊戲。
        }
        com.badlogic.gdx.Graphics.DisplayMode displayMode =
            Lwjgl3ApplicationConfiguration.getDisplayMode();
        return new int[] {
            clamp(width, MIN_WINDOW_WIDTH, displayMode.width),
            clamp(height, MIN_WINDOW_HEIGHT, displayMode.height)
        };
    }

    private static void saveWindowSize(int width, int height) {
        if (width < MIN_WINDOW_WIDTH || height < MIN_WINDOW_HEIGHT) {
            return;
        }
        try {
            Preferences preferences = windowPreferences();
            preferences.putInt(WINDOW_WIDTH_KEY, width);
            preferences.putInt(WINDOW_HEIGHT_KEY, height);
            preferences.flush();
        } catch (BackingStoreException | RuntimeException ignored) {
            // 偏好保存失敗不應妨礙正常離開遊戲。
        }
    }

    private static Preferences windowPreferences() {
        return Preferences.userNodeForPackage(Lwjgl3Launcher.class).node(WINDOW_PREFERENCES_NODE);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(value, Math.max(minimum, maximum)));
    }

    private static final class RememberingWindowSizeListener implements ApplicationListener {
        private final ApplicationListener delegate;
        private int width;
        private int height;

        private RememberingWindowSizeListener(ApplicationListener delegate) {
            this.delegate = delegate;
        }

        @Override public void create() { delegate.create(); }
        @Override public void render() { delegate.render(); }
        @Override public void pause() { delegate.pause(); }
        @Override public void resume() { delegate.resume(); }

        @Override
        public void resize(int width, int height) {
            this.width = width;
            this.height = height;
            delegate.resize(width, height);
        }

        @Override
        public void dispose() {
            saveWindowSize(width, height);
            delegate.dispose();
        }
    }
}
