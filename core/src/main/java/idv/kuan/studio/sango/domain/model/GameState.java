package idv.kuan.studio.sango.domain.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 可序列化的戰局狀態。存檔只保存 ID 與可變資料，不嵌入 Definition。
 */
public final class GameState {
    public int schemaVersion;
    public String scenarioId;
    public String mapId;
    public String playerFactionId;
    public String opponentFactionId;
    public String neutralFactionId;
    public String victoryTargetCityId;
    /** 每個存檔各自保留的戰略地圖最後焦點城池。 */
    public String strategicMapFocusedCityId;

    /**
     * 只供 schema 2 存檔遷移使用；schema 3 以上的新存檔應為 null。
     */
    @Deprecated
    public CampaignStatus campaignStatus;

    public ScenarioObjectiveStatus scenarioObjectiveStatus;
    public GameplayStatus gameplayStatus;
    public int currentTurn;
    public int currentYear;
    public int currentMonth;
    public int elapsedMonths;
    public int turnLimitMonths;
    public int actionPointsRemaining;
    /** 月初已結算的額度快照；本月民心變動只影響下個月，不由讀檔或 UI 重設。 */
    public int actionPointsPerTurn;
    public int enemyAttackCountdown;
    public int nextArmySequence;
    public int nextBattleSequence;
    public String lastActionCode;
    public FactionState[] factionStates;
    public CityState[] cityStates;
    public ArmyState[] armyStates;
    public BattleReport[] battleReports;

    public GameState() {
    }

    public GameState copy() {
        GameState copiedState = new GameState();
        copiedState.schemaVersion = schemaVersion;
        copiedState.scenarioId = scenarioId;
        copiedState.mapId = mapId;
        copiedState.playerFactionId = playerFactionId;
        copiedState.opponentFactionId = opponentFactionId;
        copiedState.neutralFactionId = neutralFactionId;
        copiedState.victoryTargetCityId = victoryTargetCityId;
        copiedState.strategicMapFocusedCityId = strategicMapFocusedCityId;
        copiedState.campaignStatus = campaignStatus;
        copiedState.scenarioObjectiveStatus = scenarioObjectiveStatus;
        copiedState.gameplayStatus = gameplayStatus;
        copiedState.currentTurn = currentTurn;
        copiedState.currentYear = currentYear;
        copiedState.currentMonth = currentMonth;
        copiedState.elapsedMonths = elapsedMonths;
        copiedState.turnLimitMonths = turnLimitMonths;
        copiedState.actionPointsRemaining = actionPointsRemaining;
        copiedState.actionPointsPerTurn = actionPointsPerTurn;
        copiedState.enemyAttackCountdown = enemyAttackCountdown;
        copiedState.nextArmySequence = nextArmySequence;
        copiedState.nextBattleSequence = nextBattleSequence;
        copiedState.lastActionCode = lastActionCode;
        copiedState.factionStates = copyFactionStates(factionStates);
        copiedState.cityStates = copyCityStates(cityStates);
        copiedState.armyStates = copyArmyStates(armyStates);
        copiedState.battleReports = copyBattleReports(battleReports);
        return copiedState;
    }

    public boolean isGameplayActive() {
        return gameplayStatus == GameplayStatus.ACTIVE;
    }

    public FactionState requirePlayerFactionState() {
        return requireFactionState(playerFactionId);
    }

    public FactionState requireOpponentFactionState() {
        return requireFactionState(opponentFactionId);
    }

    public FactionState requireFactionState(String factionId) {
        if (factionStates != null) {
            for (FactionState factionState : factionStates) {
                if (factionState != null && factionId.equals(factionState.factionId)) {
                    return factionState;
                }
            }
        }
        throw new IllegalStateException("找不到勢力狀態：" + factionId);
    }

    public CityState requireCapitalCityState() {
        FactionState playerFactionState = requirePlayerFactionState();
        return requireCityState(playerFactionState.capitalCityId);
    }

    public CityState requireCityState(String cityId) {
        CityState cityState = findCityState(cityId);
        if (cityState == null) {
            throw new IllegalStateException("找不到城池狀態：" + cityId);
        }
        return cityState;
    }

    public CityState findCityState(String cityId) {
        if (cityStates != null) {
            for (CityState cityState : cityStates) {
                if (cityState != null && cityId.equals(cityState.cityId)) {
                    return cityState;
                }
            }
        }
        return null;
    }

    public boolean ownsCity(String factionId, String cityId) {
        CityState cityState = findCityState(cityId);
        return cityState != null && factionId.equals(cityState.ownerFactionId);
    }

    public List<CityState> findCitiesOwnedBy(String factionId) {
        List<CityState> ownedCities = new ArrayList<>();
        if (cityStates != null) {
            for (CityState cityState : cityStates) {
                if (cityState != null && factionId.equals(cityState.ownerFactionId)) {
                    ownedCities.add(cityState);
                }
            }
        }
        return ownedCities;
    }

    public boolean hasArmyForFaction(String factionId) {
        if (armyStates != null) {
            for (ArmyState armyState : armyStates) {
                if (armyState != null && factionId.equals(armyState.factionId)) {
                    return true;
                }
            }
        }
        return false;
    }

    public String allocateArmyId() {
        String armyId = "army-" + nextArmySequence;
        nextArmySequence += 1;
        return armyId;
    }

    public void addArmy(ArmyState armyState) {
        if (armyState == null) {
            throw new IllegalArgumentException("armyState 不可為 null。");
        }
        int existingCount = armyStates == null ? 0 : armyStates.length;
        ArmyState[] expandedStates = new ArmyState[existingCount + 1];
        if (existingCount > 0) {
            System.arraycopy(armyStates, 0, expandedStates, 0, existingCount);
        }
        expandedStates[existingCount] = armyState;
        armyStates = expandedStates;
    }

    public void removeArmy(String armyId) {
        if (armyStates == null || armyStates.length == 0) {
            return;
        }
        List<ArmyState> remainingStates = new ArrayList<>();
        for (ArmyState armyState : armyStates) {
            if (armyState != null && !armyId.equals(armyState.armyId)) {
                remainingStates.add(armyState);
            }
        }
        armyStates = remainingStates.toArray(new ArmyState[0]);
    }

    public String allocateBattleReportId() {
        String battleId = "battle-" + nextBattleSequence;
        nextBattleSequence += 1;
        return battleId;
    }

    public void addBattleReport(BattleReport battleReport) {
        if (battleReport == null) {
            throw new IllegalArgumentException("battleReport 不可為 null。");
        }
        int existingCount = battleReports == null ? 0 : battleReports.length;
        BattleReport[] expandedReports = new BattleReport[existingCount + 1];
        if (existingCount > 0) {
            System.arraycopy(battleReports, 0, expandedReports, 0, existingCount);
        }
        expandedReports[existingCount] = battleReport;
        battleReports = expandedReports;
    }

    public BattleReport requireBattleReport(String battleId) {
        BattleReport battleReport = findBattleReport(battleId);
        if (battleReport == null) {
            throw new IllegalStateException("找不到戰報：" + battleId);
        }
        return battleReport;
    }

    public BattleReport findBattleReport(String battleId) {
        if (battleReports != null) {
            for (BattleReport battleReport : battleReports) {
                if (battleReport != null && battleId.equals(battleReport.battleId)) {
                    return battleReport;
                }
            }
        }
        return null;
    }

    public List<BattleReport> findUnreadBattleReports() {
        List<BattleReport> unreadReports = new ArrayList<>();
        if (battleReports != null) {
            for (BattleReport battleReport : battleReports) {
                if (battleReport != null && !battleReport.read) {
                    unreadReports.add(battleReport);
                }
            }
        }
        return unreadReports;
    }

    public List<BattleReport> findUnreadBattleReportsForCity(String cityId) {
        List<BattleReport> unreadReports = new ArrayList<>();
        if (battleReports != null) {
            for (BattleReport battleReport : battleReports) {
                if (battleReport != null
                    && !battleReport.read
                    && cityId.equals(battleReport.targetCityId)) {
                    unreadReports.add(battleReport);
                }
            }
        }
        return unreadReports;
    }

    public List<BattleReport> findBattleReportsForCity(String cityId) {
        List<BattleReport> reports = new ArrayList<>();
        if (battleReports != null) {
            for (BattleReport battleReport : battleReports) {
                if (battleReport != null && cityId.equals(battleReport.targetCityId)) {
                    reports.add(battleReport);
                }
            }
        }
        return reports;
    }

    public BattleReport findLatestBattleReportForCity(String cityId) {
        if (battleReports != null) {
            for (int index = battleReports.length - 1; index >= 0; index--) {
                BattleReport battleReport = battleReports[index];
                if (battleReport != null && cityId.equals(battleReport.targetCityId)) {
                    return battleReport;
                }
            }
        }
        return null;
    }

    public int countUnreadBattleReports() {
        return findUnreadBattleReports().size();
    }

    public int countUnreadBattleReportsForCity(String cityId) {
        return findUnreadBattleReportsForCity(cityId).size();
    }

    public int countBattleReportsForCity(String cityId) {
        return findBattleReportsForCity(cityId).size();
    }

    private FactionState[] copyFactionStates(FactionState[] sourceStates) {
        if (sourceStates == null) {
            return null;
        }
        FactionState[] copiedStates = new FactionState[sourceStates.length];
        for (int i = 0; i < sourceStates.length; i++) {
            copiedStates[i] = sourceStates[i] == null ? null : sourceStates[i].copy();
        }
        return copiedStates;
    }

    private CityState[] copyCityStates(CityState[] sourceStates) {
        if (sourceStates == null) {
            return null;
        }
        CityState[] copiedStates = new CityState[sourceStates.length];
        for (int i = 0; i < sourceStates.length; i++) {
            copiedStates[i] = sourceStates[i] == null ? null : sourceStates[i].copy();
        }
        return copiedStates;
    }

    private ArmyState[] copyArmyStates(ArmyState[] sourceStates) {
        if (sourceStates == null) {
            return null;
        }
        ArmyState[] copiedStates = new ArmyState[sourceStates.length];
        for (int i = 0; i < sourceStates.length; i++) {
            copiedStates[i] = sourceStates[i] == null ? null : sourceStates[i].copy();
        }
        return copiedStates;
    }

    private BattleReport[] copyBattleReports(BattleReport[] sourceReports) {
        if (sourceReports == null) {
            return null;
        }
        BattleReport[] copiedReports = new BattleReport[sourceReports.length];
        for (int i = 0; i < sourceReports.length; i++) {
            copiedReports[i] = sourceReports[i] == null ? null : sourceReports[i].copy();
        }
        return copiedReports;
    }
}
