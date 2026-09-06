package idv.kuan.studio.sango.audio;

/**
 * 可循環播放的背景音樂。
 */
public enum MusicTrack {
    LOBBY("audio/music/lobby_theme.ogg"),
    STRATEGY("audio/music/strategy_theme.ogg");

    private final String assetPath;

    MusicTrack(String assetPath) {
        this.assetPath = assetPath;
    }

    public String getAssetPath() {
        return assetPath;
    }
}
