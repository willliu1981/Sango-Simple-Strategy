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
        return calculateAttackerStrength(
            armyState.troops, armyState.training, armyState.morale);
    }

    public static int calculateAttackerStrength(int troops, int training, int morale) {
        long strength = (long) troops * (100 + training) * (150 + morale);
        return safeStrength(strength / 20_000L, troops);
    }

    public static int calculateDefenderStrength(CityState cityState) {
        return calculateDefenderStrength(cityState.troops, cityState.training,
            cityState.morale, cityState.defense);
    }

    public static int calculateDefenderStrength(int troops, int training, int morale,
        int defense) {
        // 城防沿用既有整數除法規則，避免悄悄改動奇數城防的加成。
        long strength = (long) troops * (100 + training)
            * (150 + morale) * (100 + defense / 2);
        return safeStrength(strength / 2_000_000L, troops);
    }

    public static int attackerMatchupPercent(BattleTactic tactic, DefensePolicy policy) {
        requireMatchup(tactic, policy);
        return tactic.defeats(policy) ? 110 : 100;
    }

    public static int defenderMatchupPercent(BattleTactic tactic, DefensePolicy policy) {
        requireMatchup(tactic, policy);
        return policy.defeats(tactic) ? 110 : 100;
    }

    /** 混合戰術時，守方只依其克制到的攻方基礎戰力比例取得 0–10%。 */
    public static int defenderMatchupPercent(long counteredAttackerBaseStrength,
        long totalAttackerBaseStrength) {
        if (counteredAttackerBaseStrength < 0 || totalAttackerBaseStrength < 0
            || counteredAttackerBaseStrength > totalAttackerBaseStrength) {
            throw new IllegalArgumentException("攻方基礎戰力比例無效。");
        }
        if (totalAttackerBaseStrength == 0) {
            return 100;
        }
        if (counteredAttackerBaseStrength == totalAttackerBaseStrength) {
            return 110;
        }
        for (int bonus = 9; bonus >= 1; bonus--) {
            long threshold = totalAttackerBaseStrength / 10 * bonus
                + (totalAttackerBaseStrength % 10 * bonus + 9) / 10;
            if (counteredAttackerBaseStrength >= threshold) {
                return 100 + bonus;
            }
        }
        return 100;
    }

    public static int applyMatchupPercent(int baseStrength, int matchupPercent) {
        if (baseStrength < 0) {
            throw new IllegalArgumentException("基礎戰力不可為負數。");
        }
        if (matchupPercent < 100 || matchupPercent > 110) {
            throw new IllegalArgumentException("相剋倍率必須介於 100 到 110。");
        }
        long effective = (long) baseStrength * matchupPercent / 100;
        return (int) Math.min(Integer.MAX_VALUE, effective);
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

    private static void requireMatchup(BattleTactic tactic, DefensePolicy policy) {
        if (tactic == null || policy == null) {
            throw new IllegalArgumentException("戰術與防守方針不可為 null。");
        }
    }
}
