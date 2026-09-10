package idv.kuan.studio.sango.repository.save;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import idv.kuan.studio.sango.SangoVersion;
import idv.kuan.studio.sango.domain.model.BattleReport;
import idv.kuan.studio.sango.domain.model.ArmyState;
import idv.kuan.studio.sango.domain.rule.FactionActionPointRules;
import idv.kuan.studio.sango.domain.rule.TroopQualityRules;
import idv.kuan.studio.sango.domain.model.CampaignStatus;
import idv.kuan.studio.sango.domain.model.CityState;
import idv.kuan.studio.sango.domain.model.CityIntelligenceSnapshot;
import idv.kuan.studio.sango.domain.model.FactionState;
import idv.kuan.studio.sango.domain.model.GameState;
import idv.kuan.studio.sango.domain.model.GameplayStatus;
import idv.kuan.studio.sango.domain.model.ScenarioObjectiveStatus;
import idv.kuan.studio.sango.domain.rule.DefensePolicy;
import idv.kuan.studio.sango.domain.service.CityIntelligenceService;

/**
 * 逐版遷移 2 -> 3 -> 4 -> 5 -> 6 -> 7 -> 8 -> 9 -> 10 -> 11 -> 12 -> 13。
 * 僅遷移狀態結構，不替換舊劇本、刷新情報或回算既有戰報。
 */
@SuppressWarnings("deprecation")
public final class GameStateMigrator {
    public GameState migrate(GameState sourceState) {
        if (sourceState == null) {
            throw new IllegalArgumentException("GameState 不可為 null。");
        }
        GameState migratedState = sourceState.copy();
        if (migratedState.schemaVersion == 2) {
            migrateSchemaTwoToThree(migratedState);
        }
        if (migratedState.schemaVersion == 3) {
            migrateSchemaThreeToFour(migratedState);
        }
        if (migratedState.schemaVersion == 4) {
            migrateSchemaFourToFive(migratedState);
        }
        if (migratedState.schemaVersion == 5) {
            migrateSchemaFiveToSix(migratedState);
        }
        if (migratedState.schemaVersion == 6) {
            migrateSchemaSixToSeven(migratedState);
        }
        if (migratedState.schemaVersion == 7) {
            migrateSchemaSevenToEight(migratedState);
        }
        if (migratedState.schemaVersion == 8) {
            migrateSchemaEightToNine(migratedState);
        }
        if (migratedState.schemaVersion == 9) {
            migrateSchemaNineToTen(migratedState);
        }
        if (migratedState.schemaVersion == 10) {
            migrateSchemaTenToEleven(migratedState);
        }
        if (migratedState.schemaVersion == 11) {
            migrateSchemaElevenToTwelve(migratedState);
        }
        if (migratedState.schemaVersion == 12) {
            migrateSchemaTwelveToThirteen(migratedState);
        }
        if (migratedState.schemaVersion != SangoVersion.GAME_STATE_SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                "不支援的 GameState schemaVersion：" + migratedState.schemaVersion
            );
        }
        normalizeCurrentState(migratedState);
        return migratedState;
    }

    private void migrateSchemaTwoToThree(GameState gameState) {
        CampaignStatus legacyStatus = gameState.campaignStatus;
        if (legacyStatus == null) {
            legacyStatus = CampaignStatus.IN_PROGRESS;
        }
        gameState.scenarioObjectiveStatus = switch (legacyStatus) {
            case IN_PROGRESS -> ScenarioObjectiveStatus.IN_PROGRESS;
            case VICTORY -> ScenarioObjectiveStatus.ACHIEVED;
            case DEFEAT -> ScenarioObjectiveStatus.FAILED;
        };
        gameState.gameplayStatus = gameState.requirePlayerFactionState().active
            ? GameplayStatus.ACTIVE : GameplayStatus.ELIMINATED;
        gameState.nextBattleSequence = 1;
        gameState.battleReports = new BattleReport[0];
        gameState.campaignStatus = null;
        gameState.schemaVersion = 3;
        if (gameState.gameplayStatus == GameplayStatus.ACTIVE
            && gameState.actionPointsRemaining == 0
            && legacyStatus != CampaignStatus.IN_PROGRESS) {
            gameState.actionPointsRemaining = gameState.actionPointsPerTurn;
        }
    }

    private void migrateSchemaThreeToFour(GameState gameState) {
        if (gameState.cityStates != null) {
            for (CityState cityState : gameState.cityStates) {
                if (cityState != null) {
                    // 只在舊 schema 缺少城市士氣時初始化，不覆寫 schema 4 的合法零士氣。
                    cityState.morale = Math.max(50, Math.min(100, cityState.publicOrder));
                }
            }
        }
        if (gameState.battleReports != null) {
            for (BattleReport battleReport : gameState.battleReports) {
                if (battleReport != null) {
                    battleReport.moraleRecorded = false;
                }
            }
        }
        gameState.schemaVersion = 4;
    }

    private void migrateSchemaFourToFive(GameState gameState) {
        for (CityState cityState : gameState.cityStates) {
            cityState.trainingFraction = 0;
            cityState.moraleFraction = 0;
        }
        for (ArmyState armyState : gameState.armyStates) {
            armyState.trainingFraction = 0;
            armyState.moraleFraction = 0;
        }
        // 只補上原本不存在的 AI 快照，不回補玩家月中已花掉的 AP。
        FactionActionPointRules.initializeMigratedAi(gameState);
        gameState.schemaVersion = 5;
    }

    private void migrateSchemaFiveToSix(GameState gameState) {
        for (CityState cityState : gameState.cityStates) {
            // 舊檔的零素質是合法狀態；無條件進位僅適用於玩家操作後的計算結果。
            cityState.training = migrateLegacyQuality(TroopQualityRules.training(cityState));
            cityState.trainingFraction = 0;
            cityState.morale = migrateLegacyQuality(TroopQualityRules.morale(cityState));
            cityState.moraleFraction = 0;
        }
        for (ArmyState armyState : gameState.armyStates) {
            armyState.training = Math.min(100, Math.max(0,
                (TroopQualityRules.training(armyState) + TroopQualityRules.SCALE - 1)
                    / TroopQualityRules.SCALE));
            armyState.trainingFraction = 0;
            armyState.morale = Math.min(100, Math.max(0,
                (TroopQualityRules.morale(armyState) + TroopQualityRules.SCALE - 1)
                    / TroopQualityRules.SCALE));
            armyState.moraleFraction = 0;
        }
        gameState.strategicMapFocusedCityId = gameState.requirePlayerFactionState().active
            ? gameState.requirePlayerFactionState().capitalCityId
            : gameState.victoryTargetCityId;
        gameState.schemaVersion = 6;
    }

    private void migrateSchemaSixToSeven(GameState gameState) {
        for (CityState cityState : gameState.cityStates) {
            cityState.publicOrderRecoveryStreakMonths = 0;
        }
        gameState.schemaVersion = 7;
    }

    private void migrateSchemaSevenToEight(GameState gameState) {
        if (gameState.armyStates != null) {
            for (ArmyState armyState : gameState.armyStates) {
                if (armyState != null) {
                    armyState.expeditionGroupId = armyState.armyId;
                }
            }
        }
        if (gameState.battleReports != null) {
            for (BattleReport battleReport : gameState.battleReports) {
                if (battleReport != null) {
                    battleReport.attackerContributions = new idv.kuan.studio.sango.domain.model.BattleContribution[0];
                }
            }
        }
        gameState.schemaVersion = 8;
    }

    private void migrateSchemaEightToNine(GameState gameState) {
        if (gameState.armyStates != null) {
            for (ArmyState armyState : gameState.armyStates) {
                if (armyState != null) {
                    armyState.retreatRouteCityIds = null;
                    armyState.retreatRouteIndex = 0;
                }
            }
        }
        gameState.schemaVersion = 9;
    }

    private void migrateSchemaNineToTen(GameState gameState) {
        for (FactionState factionState : gameState.factionStates) {
            factionState.cityIntelligence = new CityIntelligenceSnapshot[0];
        }
        CityIntelligenceService intelligenceService = new CityIntelligenceService();
        for (CityState cityState : gameState.cityStates) {
            cityState.defensePolicy = DefensePolicy.BALANCED;
            if (!gameState.playerFactionId.equals(cityState.ownerFactionId)
                && cityState.scoutedUntilTurn >= gameState.currentTurn) {
                intelligenceService.restoreLegacy(gameState, gameState.playerFactionId,
                    cityState.cityId, cityState.scoutedUntilTurn);
            }
            cityState.scoutedUntilTurn = 0;
        }
        if (gameState.battleReports != null) {
            for (BattleReport battleReport : gameState.battleReports) {
                if (battleReport != null) {
                    battleReport.defenderPolicy = null;
                    battleReport.defenderPolicyRecorded = false;
                }
            }
        }
        gameState.schemaVersion = 10;
    }

    private void migrateSchemaTenToEleven(GameState gameState) {
        if (gameState.armyStates != null) {
            for (ArmyState armyState : gameState.armyStates) {
                if (armyState != null) {
                    if (armyState.tactic != null) {
                        armyState.tactic = armyState.tactic.normalized();
                    }
                }
            }
        }
        if (gameState.cityStates != null) {
            for (CityState cityState : gameState.cityStates) {
                if (cityState != null) {
                    if (cityState.defensePolicy != null) {
                        cityState.defensePolicy = cityState.defensePolicy.normalized();
                    }
                }
            }
        }
        if (gameState.factionStates != null) {
            for (FactionState factionState : gameState.factionStates) {
                if (factionState == null || factionState.cityIntelligence == null) {
                    continue;
                }
                for (CityIntelligenceSnapshot snapshot : factionState.cityIntelligence) {
                    if (snapshot != null) {
                        if (snapshot.defensePolicy != null) {
                            snapshot.defensePolicy = snapshot.defensePolicy.normalized();
                        }
                    }
                }
            }
        }
        if (gameState.battleReports != null) {
            for (BattleReport battleReport : gameState.battleReports) {
                if (battleReport != null) {
                    // 歷史戰報保留原 enum、結果與舊 strength；新欄位的 0 代表 legacy。
                    battleReport.battleRulesVersion = 0;
                }
            }
        }
        gameState.schemaVersion = 11;
    }

    private int migrateLegacyQuality(int scaledQuality) {
        return Math.min(100, Math.max(0,
            (scaledQuality + TroopQualityRules.SCALE - 1) / TroopQualityRules.SCALE));
    }

    private void migrateSchemaElevenToTwelve(GameState gameState) {
        gameState.turnStartCityStates = copyCityStates(gameState.cityStates);
        if (gameState.armyStates != null) {
            for (ArmyState armyState : gameState.armyStates) {
                if (armyState == null) {
                    continue;
                }
                armyState.totalTravelMonths = Math.max(1, armyState.remainingTravelMonths);
                armyState.initialTroops = armyState.troops;
                armyState.postEncounterOrder = idv.kuan.studio.sango.domain.rule.PostEncounterOrder.AUTO;
            }
        }
        for (FactionState factionState : gameState.factionStates) {
            if (factionState.cityIntelligence == null) {
                continue;
            }
            for (CityIntelligenceSnapshot snapshot : factionState.cityIntelligence) {
                if (snapshot != null) {
                    snapshot.defensePolicy = null;
                }
            }
        }
        gameState.schemaVersion = 12;
    }

    /**
     * schema 13 將人口顯示尺度縮為原本的十分之一。只處理人口快照，
     * 不回算歷史戰報兵力、在途軍或任何既有戰鬥結果。
     */
    private void migrateSchemaTwelveToThirteen(GameState gameState) {
        gameState.campaignInstanceId = legacyCampaignInstanceId(gameState);
        scaleCityPopulations(gameState.cityStates);
        scaleCityPopulations(gameState.turnStartCityStates);
        if (gameState.factionStates != null) {
            for (FactionState factionState : gameState.factionStates) {
                if (factionState == null || factionState.cityIntelligence == null) {
                    continue;
                }
                for (CityIntelligenceSnapshot snapshot : factionState.cityIntelligence) {
                    if (snapshot != null) {
                        snapshot.population = scalePopulation(snapshot.population);
                    }
                }
            }
        }
        gameState.schemaVersion = 13;
    }

    private void scaleCityPopulations(CityState[] cityStates) {
        if (cityStates == null) {
            return;
        }
        for (CityState cityState : cityStates) {
            if (cityState != null) {
                cityState.population = scalePopulation(cityState.population);
            }
        }
    }

    private int scalePopulation(int population) {
        return population <= 0 ? population : Math.max(1, population / 10);
    }

    private String legacyCampaignInstanceId(GameState gameState) {
        String fingerprint = String.join("|",
            safe(gameState.scenarioId), safe(gameState.mapId), safe(gameState.playerFactionId),
            safe(gameState.opponentFactionId), safe(gameState.neutralFactionId),
            safe(gameState.victoryTargetCityId));
        return UUID.nameUUIDFromBytes(fingerprint.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private CityState[] copyCityStates(CityState[] source) {
        if (source == null) {
            return null;
        }
        CityState[] copied = new CityState[source.length];
        for (int i = 0; i < source.length; i++) {
            copied[i] = source[i] == null ? null : source[i].copy();
        }
        return copied;
    }

    private void normalizeCurrentState(GameState gameState) {
        if (gameState.battleReports == null) {
            gameState.battleReports = new BattleReport[0];
        }
        if (gameState.nextBattleSequence < 1) {
            gameState.nextBattleSequence = gameState.battleReports.length + 1;
        }
        if (gameState.turnStartCityStates == null) {
            gameState.turnStartCityStates = copyCityStates(gameState.cityStates);
        }
        for (FactionState factionState : gameState.factionStates) {
            if (factionState.cityIntelligence == null) {
                factionState.cityIntelligence = new CityIntelligenceSnapshot[0];
            }
        }
        gameState.campaignStatus = null;
    }
}
