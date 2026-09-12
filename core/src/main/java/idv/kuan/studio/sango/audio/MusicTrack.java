package idv.kuan.studio.sango.audio;

/** 音樂資產與顯示名稱分離；鑑賞曲目均使用完整 MP3，不裁切來源。 */
public enum MusicTrack {
    LOBBY("audio/music/lobby_theme.mp3", "music_lobby", "music_menu", 208.760f, 1f),
    VICTORY("audio/music/strategy_theme.mp3", "music_victory", "music_victory_scene", 117.600f, 1f),
    SPRING("audio/music/spring_wind_enters_city.mp3", "music_spring", "season_spring", 239.440f, 0.70f),
    SUMMER("audio/music/summer_counsel.mp3", "music_summer", "season_summer", 239.840f, 0.70f),
    AUTUMN("audio/music/autumn_battlefield.mp3", "music_autumn", "season_autumn", 239.840f, 0.70f),
    WINTER("audio/music/winter_stratagem.mp3", "music_winter", "season_winter", 233.600f, 0.70f);

    private final String assetPath;
    private final String titleKey;
    private final String seasonKey;
    private final float durationSeconds;
    private final float outputGain;

    MusicTrack(String assetPath, String titleKey, String seasonKey, float durationSeconds, float outputGain) {
        this.assetPath = assetPath;
        this.titleKey = titleKey;
        this.seasonKey = seasonKey;
        this.durationSeconds = durationSeconds;
        this.outputGain = outputGain;
    }

    public String getAssetPath() {
        return assetPath;
    }

    public String getTitleKey() {
        return titleKey;
    }

    public String getEnglishTitleKey() {
        return titleKey + "_en";
    }

    public String getSeasonKey() {
        return seasonKey;
    }

    public float getDurationSeconds() {
        return durationSeconds;
    }

    public float getOutputGain() {
        return outputGain;
    }

    public static MusicTrack forMonth(int month) {
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("月份必須介於 1 到 12。");
        }
        return switch ((month - 1) / 3) {
            case 0 -> SPRING;
            case 1 -> SUMMER;
            case 2 -> AUTUMN;
            default -> WINTER;
        };
    }

    public static MusicTrack[] seasonalTracks() {
        return new MusicTrack[] {SPRING, SUMMER, AUTUMN, WINTER};
    }

    public static MusicTrack[] galleryTracks() {
        return new MusicTrack[] {LOBBY, VICTORY, SPRING, SUMMER, AUTUMN, WINTER};
    }
}
