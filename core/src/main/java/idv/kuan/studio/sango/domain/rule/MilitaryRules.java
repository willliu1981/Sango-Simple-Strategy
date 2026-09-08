package idv.kuan.studio.sango.domain.rule;

import idv.kuan.studio.sango.domain.model.ArmyState;
import idv.kuan.studio.sango.domain.model.CityState;

/**
 * 純 Java 的軍事公式；以 long 保留中間精度，最後才向下取整。
 * 士氣倍率為 (150 + morale) / 200，50 士氣即為基準戰力。
 */
public final class MilitaryRules {
    private MilitaryRules() {
    }

    public static int calculateAttackerStrength(ArmyState armyState) {
        long strength = (long) armyState.troops * (100 + armyState.training)
            * (150 + armyState.morale) * armyState.tactic.getStrengthPercent();
        return safeStrength(strength / 2_000_000L, armyState.troops);
    }

    public static int calculateDefenderStrength(CityState cityState) {
        // 城防沿用既有整數除法規則，避免悄悄改動奇數城防的加成。
        long strength = (long) cityState.troops * (100 + cityState.training)
            * (150 + cityState.morale) * (100 + cityState.defense / 2)
            * cityState.defensePolicy.getStrengthPercent();
        return safeStrength(strength / 200_000_000L, cityState.troops);
    }

    public static int weightedQuality(
        int firstTroops,
        int firstQuality,
        int secondTroops,
        int secondQuality
    ) {
        long totalTroops = (long) firstTroops + secondTroops;
        if (totalTroops <= 0) {
            return 0;
        }
        return (int) (((long) firstTroops * firstQuality
            + (long) secondTroops * secondQuality) / totalTroops);
    }

    private static int safeStrength(long strength, int troops) {
        if (troops <= 0) {
            return 0;
        }
        return (int) Math.max(1L, Math.min(Integer.MAX_VALUE, strength));
    }
}
