package idv.kuan.studio.sango.domain.model;

import idv.kuan.studio.sango.domain.rule.BattleTactic;
import idv.kuan.studio.sango.domain.rule.DefensePolicy;

/**
 * 可持久化的單場戰鬥紀錄。
 */
public final class BattleReport {
    public String battleId;
    public int resolvedTurn;
    public int resolvedYear;
    public int resolvedMonth;
    public String originCityId;
    public String targetCityId;
    public String attackerFactionId;
    public String defenderFactionId;
    public BattleTactic attackerTactic;
    /** schema 10 起記錄守城當下的方針；舊戰報為 null。 */
    public DefensePolicy defenderPolicy;
    public boolean defenderPolicyRecorded;
    public int attackerTroopsBefore;
    public int defenderTroopsBefore;
    public int attackerTraining;
    public int defenderTraining;
    public int defenderDefense;
    public int attackerMorale;
    public int defenderMorale;
    /** 舊戰報沒有士氣快照，不能用目前城市資料偽造歷史數值。 */
    public boolean moraleRecorded;
    public int attackerLosses;
    public int defenderLosses;
    public int attackerSurvivors;
    public int defenderSurvivors;
    public BattleOutcome outcome;
    public boolean cityCaptured;
    public String winnerFactionId;
    public boolean read;
    /** schema 8 起保存各來源貢獻；舊戰報遷移為空陣列，不偽造歷史資料。 */
    public BattleContribution[] attackerContributions;

    public BattleReport() {
    }

    public BattleReport copy() {
        BattleReport copiedReport = new BattleReport();
        copiedReport.battleId = battleId;
        copiedReport.resolvedTurn = resolvedTurn;
        copiedReport.resolvedYear = resolvedYear;
        copiedReport.resolvedMonth = resolvedMonth;
        copiedReport.originCityId = originCityId;
        copiedReport.targetCityId = targetCityId;
        copiedReport.attackerFactionId = attackerFactionId;
        copiedReport.defenderFactionId = defenderFactionId;
        copiedReport.attackerTactic = attackerTactic;
        copiedReport.defenderPolicy = defenderPolicy;
        copiedReport.defenderPolicyRecorded = defenderPolicyRecorded;
        copiedReport.attackerTroopsBefore = attackerTroopsBefore;
        copiedReport.defenderTroopsBefore = defenderTroopsBefore;
        copiedReport.attackerTraining = attackerTraining;
        copiedReport.defenderTraining = defenderTraining;
        copiedReport.defenderDefense = defenderDefense;
        copiedReport.attackerMorale = attackerMorale;
        copiedReport.defenderMorale = defenderMorale;
        copiedReport.moraleRecorded = moraleRecorded;
        copiedReport.attackerLosses = attackerLosses;
        copiedReport.defenderLosses = defenderLosses;
        copiedReport.attackerSurvivors = attackerSurvivors;
        copiedReport.defenderSurvivors = defenderSurvivors;
        copiedReport.outcome = outcome;
        copiedReport.cityCaptured = cityCaptured;
        copiedReport.winnerFactionId = winnerFactionId;
        copiedReport.read = read;
        copiedReport.attackerContributions = copyContributions(attackerContributions);
        return copiedReport;
    }

    private BattleContribution[] copyContributions(BattleContribution[] sourceContributions) {
        if (sourceContributions == null) {
            return null;
        }
        BattleContribution[] copiedContributions = new BattleContribution[sourceContributions.length];
        for (int i = 0; i < sourceContributions.length; i++) {
            copiedContributions[i] = sourceContributions[i] == null
                ? null : sourceContributions[i].copy();
        }
        return copiedContributions;
    }

    public boolean involvesFaction(String factionId) {
        if (factionId == null) {
            return false;
        }
        return factionId.equals(attackerFactionId) || factionId.equals(defenderFactionId);
    }
}
