package idv.kuan.studio.sango.domain.rule;

/**
 * 抽象執行武將的能力；目前使用 DEFAULT，日後由武將能力組合本值，不嵌入存檔。
 * 徵兵倍率與訓練覆蓋人數互相獨立，均不代表野戰軍帶兵上限。
 */
public record OfficerCommandProfile(
    int recruitmentCapacityPercent,
    int trainingCoverage,
    int trainingGain,
    int moraleGain,
    int recruitTraining
) {
    public static final OfficerCommandProfile DEFAULT =
        new OfficerCommandProfile(100, 20_000, 5, 5, 50);

    public OfficerCommandProfile {
        if (recruitmentCapacityPercent < 1 || recruitmentCapacityPercent > 500
            || trainingCoverage < 1 || trainingGain < 0 || trainingGain > 100
            || moraleGain < 0 || moraleGain > 100
            || recruitTraining < 0 || recruitTraining > 100) {
            throw new IllegalArgumentException("武將命令能力超出合法範圍。");
        }
    }
}
