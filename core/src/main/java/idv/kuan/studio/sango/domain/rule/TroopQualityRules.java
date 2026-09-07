package idv.kuan.studio.sango.domain.rule;

import idv.kuan.studio.sango.domain.model.ArmyState;
import idv.kuan.studio.sango.domain.model.CityState;

/**
 * 素質使用百萬分之一點的固定小數，保留不足一點的訓練及合併成果。
 * 不逐一保存士兵；訓練覆蓋視為從同質平均部隊中抽取的代表性樣本。
 */
public final class TroopQualityRules {
    public static final int SCALE = 1_000_000;
    public static final int MAXIMUM_SCALED_QUALITY = 100 * SCALE;

    private TroopQualityRules() {
    }

    public static int training(CityState cityState) {
        return cityState.training * SCALE + cityState.trainingFraction;
    }

    public static int morale(CityState cityState) {
        return cityState.morale * SCALE + cityState.moraleFraction;
    }

    public static int training(ArmyState armyState) {
        return armyState.training * SCALE + armyState.trainingFraction;
    }

    public static int morale(ArmyState armyState) {
        return armyState.morale * SCALE + armyState.moraleFraction;
    }

    public static int weightedAverage(int oldTroops, int oldQuality, int addedTroops, int addedQuality) {
        long totalTroops = (long) oldTroops + addedTroops;
        if (oldTroops < 0 || addedTroops < 0 || totalTroops <= 0) {
            throw new IllegalArgumentException("加權平均需要非負兵數，且合計大於零。");
        }
        return (int) (((long) oldTroops * oldQuality + (long) addedTroops * addedQuality) / totalTroops);
    }

    public static void set(CityState cityState, int trainingScaled, int moraleScaled) {
        validateScaled(trainingScaled);
        validateScaled(moraleScaled);
        cityState.training = trainingScaled / SCALE;
        cityState.trainingFraction = trainingScaled % SCALE;
        cityState.morale = moraleScaled / SCALE;
        cityState.moraleFraction = moraleScaled % SCALE;
    }

    public static void merge(CityState cityState, int addedTroops, int trainingScaled, int moraleScaled) {
        int totalTroops = Math.addExact(cityState.troops, addedTroops);
        int newTraining = weightedAverage(cityState.troops, training(cityState), addedTroops, trainingScaled);
        int newMorale = weightedAverage(cityState.troops, morale(cityState), addedTroops, moraleScaled);
        set(cityState, newTraining, newMorale);
        cityState.troops = totalTroops;
    }

    public static TrainingProjection projectTraining(CityState cityState, OfficerCommandProfile officer) {
        int coveredTroops = Math.min(cityState.troops, officer.trainingCoverage());
        return new TrainingProjection(
            coveredTroops,
            afterCoveredTraining(cityState.troops, coveredTroops, training(cityState), officer.trainingGain()),
            afterCoveredTraining(cityState.troops, coveredTroops, morale(cityState), officer.moraleGain())
        );
    }

    public static void train(CityState cityState, OfficerCommandProfile officer) {
        TrainingProjection projection = projectTraining(cityState, officer);
        set(cityState, projection.trainingScaled(), projection.moraleScaled());
    }

    private static int afterCoveredTraining(int totalTroops, int coveredTroops, int currentScaled, int gain) {
        if (totalTroops <= 0) {
            return currentScaled;
        }
        int coveredGain = Math.min(MAXIMUM_SCALED_QUALITY - currentScaled, gain * SCALE);
        // 先限制受訓部分到 100，再將提升按覆蓋比例攤回全軍。
        int aggregateGain = (int) ((long) coveredTroops * coveredGain / totalTroops);
        if (coveredTroops > 0 && coveredGain > 0 && aggregateGain == 0) {
            aggregateGain = 1;
        }
        return Math.min(MAXIMUM_SCALED_QUALITY, currentScaled + aggregateGain);
    }

    private static void validateScaled(int quality) {
        if (quality < 0 || quality > MAXIMUM_SCALED_QUALITY) {
            throw new IllegalArgumentException("部隊素質必須介於 0 到 100。");
        }
    }

    public record TrainingProjection(int coveredTroops, int trainingScaled, int moraleScaled) {
    }
}
