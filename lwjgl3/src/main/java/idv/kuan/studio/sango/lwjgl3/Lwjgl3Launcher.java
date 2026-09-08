package idv.kuan.studio.sango.lwjgl3;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;

import idv.kuan.studio.sango.Main;

/**
 * 啟動 Desktop（LWJGL3）版本。
 */
public final class Lwjgl3Launcher {
    private Lwjgl3Launcher() {
    }

    public static void main(String[] arguments) {
        if (StartupHelper.startNewJvmIfRequired()) {
            return;
        }
        createApplication();
    }

    private static Lwjgl3Application createApplication() {
        return new Lwjgl3Application(new Main(), getDefaultConfiguration());
    }

    private static Lwjgl3ApplicationConfiguration getDefaultConfiguration() {
        Lwjgl3ApplicationConfiguration configuration = new Lwjgl3ApplicationConfiguration();
        configuration.setTitle("Sango — 簡易三國志");
        configuration.useVsync(true);
        configuration.setForegroundFPS(
            Lwjgl3ApplicationConfiguration.getDisplayMode().refreshRate
        );
        configuration.setWindowedMode(1280, 720);
        configuration.setWindowSizeLimits(960, 540, -1, -1);
        configuration.setWindowIcon(
            "sango128.png",
            "sango64.png",
            "sango32.png",
            "sango16.png"
        );
        return configuration;
    }
}
