package idv.kuan.studio.sango.domain.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import idv.kuan.studio.sango.application.result.TurnEvent;
import idv.kuan.studio.sango.application.result.TurnEventType;
import idv.kuan.studio.sango.application.result.TurnResolutionReport;
import idv.kuan.studio.sango.application.result.TurnResolutionResult;
import idv.kuan.studio.sango.domain.definition.StrategicMapDefinition;
import idv.kuan.studio.sango.domain.model.ArmyState;
import idv.kuan.studio.sango.domain.model.CityState;
import idv.kuan.studio.sango.domain.model.FactionState;
import idv.kuan.studio.sango.domain.model.GameState;
import idv.kuan.studio.sango.domain.model.GameplayStatus;
import idv.kuan.studio.sango.domain.model.ScenarioObjectiveStatus;
import idv.kuan.studio.sango.domain.model.GameStateValidator;
import idv.kuan.studio.sango.domain.rule.NationalActionPointRules;
import idv.kuan.studio.sango.domain.rule.FactionActionPointRules;
import idv.kuan.studio.sango.domain.rule.PopulationRules;
import idv.kuan.studio.sango.domain.rule.PublicOrderNaturalRecoveryRules;
import idv.kuan.studio.sango.domain.rule.SeasonalEconomyRules;
import idv.kuan.studio.sango.repository.GameDefinitionRepository;

/**
 * 依固定順序處理軍糧、洪災、季節經濟、行軍、敵方 AI、民心恢復與期限。
 */
public final class TurnResolutionService {
    private final GameDefinitionRepository definitionRepository;
    private final DeterministicEventRoller eventRoller;
    private final BattleResolutionService battleResolutionService;
    private final EnemyTurnService enemyTurnService;

    public TurnResolutionService(GameDefinitionRepository definitionRepository) {
        this(
            definitionRepository,
            new DeterministicEventRoller(),
            new BattleResolutionService(),
            new EnemyTurnService()
        );
    }

    public TurnResolutionService(
        GameDefinitionRepository definitionRepository,
        DeterministicEventRoller eventRoller,
        BattleResolutionService battleResolutionService,
        EnemyTurnService enemyTurnService
    ) {
        this.definitionRepository = definitionRepository;
        this.eventRoller = eventRoller;
        this.battleResolutionService = battleResolutionService;
        this.enemyTurnService = enemyTurnService;
    }

    public TurnResolutionResult resolve(GameState currentState) {
        GameStateValidator.validate(currentState);
        if (currentState.gameplayStatus != GameplayStatus.ACTIVE) {
            throw new IllegalStateException("玩家勢力已滅亡，不能繼續推進月份。");
        }

        GameState nextState = currentState.copy();
        TurnResolutionReport report = new TurnResolutionReport(
            currentState.currentYear,
            currentState.currentMonth
        );
        StrategicMapDefinition mapDefinition = definitionRepository.requireMap(
            nextState.mapId
        );

        Set<String> foodShortageFactionIds = resolveMilitaryUpkeep(nextState, report);
        Set<String> floodedCityIds = resolveFloodSeason(nextState, report);
        resolveQuarterlyTax(nextState, report);
        resolveHarvest(nextState, report);
        Set<String> changedOwnerCityIds = resolveArmyMovement(nextState, report);
        enemyTurnService.execute(nextState, mapDefinition, report);
        resolvePublicOrderNaturalRecovery(
            nextState,
            report,
            foodShortageFactionIds,
            floodedCityIds,
            changedOwnerCityIds
        );
        resolveAnnualPopulation(nextState, report);
        advanceCampaignClock(nextState, report);

        nextState.lastActionCode = "END_TURN";
        GameStateValidator.validate(nextState);
        return new TurnResolutionResult(nextState, report);
    }

    private Set<String> resolveMilitaryUpkeep(
        GameState gameState,
        TurnResolutionReport report
    ) {
        Set<String> foodShortageFactionIds = new HashSet<>();
        for (FactionState factionState : gameState.factionStates) {
            if (!factionState.active
                || gameState.neutralFactionId.equals(factionState.factionId)) {
                continue;
            }
            int troopCount = calculateFactionTroops(gameState, factionState.factionId);
            int foodCost = SeasonalEconomyRules.calculateMilitaryFoodUpkeep(troopCount);
            int paidFood = Math.min(factionState.food, foodCost);
            factionState.food -= paidFood;

            if (gameState.playerFactionId.equals(factionState.factionId)) {
                report.add(new TurnEvent(
                    TurnEventType.MILITARY_UPKEEP,
                    factionState.factionId,
                    null,
                    null,
                    paidFood,
                    troopCount
                ));
            }

            int shortage = foodCost - paidFood;
            if (shortage > 0) {
                foodShortageFactionIds.add(factionState.factionId);
                int deserters = applyFoodShortage(
                    gameState,
                    factionState.factionId,
                    shortage
                );
                if (gameState.playerFactionId.equals(factionState.factionId)) {
                    report.add(new TurnEvent(
                        TurnEventType.FOOD_SHORTAGE,
                        factionState.factionId,
                        null,
                        null,
                        shortage,
                        deserters
                    ));
                }
            }
        }
        return foodShortageFactionIds;
    }

    private int calculateFactionTroops(GameState gameState, String factionId) {
        int troopCount = 0;
        for (CityState cityState : gameState.cityStates) {
            if (factionId.equals(cityState.ownerFactionId)) {
                troopCount += cityState.troops;
            }
        }
        for (ArmyState armyState : gameState.armyStates) {
            if (factionId.equals(armyState.factionId)) {
                troopCount += armyState.troops;
            }
        }
        return troopCount;
    }

    private int applyFoodShortage(
        GameState gameState,
        String factionId,
        int shortage
    ) {
        int remainingDeserters = shortage * 4;
        int originalDeserters = remainingDeserters;

        for (ArmyState armyState : gameState.armyStates) {
            if (!factionId.equals(armyState.factionId) || remainingDeserters <= 0) {
                continue;
            }
            int armyLoss = Math.min(armyState.troops / 5, remainingDeserters);
            armyState.troops -= armyLoss;
            remainingDeserters -= armyLoss;
        }
        for (CityState cityState : gameState.cityStates) {
            if (!factionId.equals(cityState.ownerFactionId) || remainingDeserters <= 0) {
                continue;
            }
            int cityLoss = Math.min(cityState.troops / 5, remainingDeserters);
            cityState.troops -= cityLoss;
            remainingDeserters -= cityLoss;
        }
        removeEmptyArmies(gameState);
        return originalDeserters - remainingDeserters;
    }

    private void removeEmptyArmies(GameState gameState) {
        List<String> emptyArmyIds = new ArrayList<>();
        for (ArmyState armyState : gameState.armyStates) {
            if (armyState.troops <= 0) {
                emptyArmyIds.add(armyState.armyId);
            }
        }
        for (String armyId : emptyArmyIds) {
            gameState.removeArmy(armyId);
        }
    }

    private Set<String> resolveFloodSeason(
        GameState gameState,
        TurnResolutionReport report
    ) {
        Set<String> floodedCityIds = new HashSet<>();
        if (gameState.currentMonth != SeasonalEconomyRules.FLOOD_RESOLUTION_MONTH) {
            return floodedCityIds;
        }
        for (CityState cityState : gameState.cityStates) {
            int riskPercent = SeasonalEconomyRules.calculateFloodRiskPercent(cityState);
            int rolledPercent = eventRoller.rollPercent(
                gameState.scenarioId,
                cityState.cityId,
                gameState.currentYear,
                "FLOOD"
            );
            boolean floodOccurred = rolledPercent < riskPercent;
            if (floodOccurred) {
                floodedCityIds.add(cityState.cityId);
                int harvestLossPercent = SeasonalEconomyRules
                    .calculateFloodHarvestLossPercent(cityState);
                cityState.harvestModifierPercent = 100 - harvestLossPercent;
                cityState.agriculture = Math.max(0, cityState.agriculture - 3);
                cityState.publicOrder = Math.max(0, cityState.publicOrder - 5);
                cityState.population = Math.max(1000, cityState.population - 200);
                if (gameState.playerFactionId.equals(cityState.ownerFactionId)) {
                    report.add(new TurnEvent(
                        TurnEventType.FLOOD_OCCURRED,
                        cityState.ownerFactionId,
                        cityState.cityId,
                        null,
                        riskPercent,
                        harvestLossPercent
                    ));
                }
            } else {
                cityState.harvestModifierPercent = 100;
                if (gameState.playerFactionId.equals(cityState.ownerFactionId)) {
                    report.add(new TurnEvent(
                        TurnEventType.FLOOD_AVOIDED,
                        cityState.ownerFactionId,
                        cityState.cityId,
                        null,
                        riskPercent,
                        rolledPercent
                    ));
                }
            }
        }
        return floodedCityIds;
    }

    private void resolveQuarterlyTax(
        GameState gameState,
        TurnResolutionReport report
    ) {
        if (!SeasonalEconomyRules.isQuarterEnd(gameState.currentMonth)) {
            return;
        }
        for (FactionState factionState : gameState.factionStates) {
            if (!factionState.active
                || gameState.neutralFactionId.equals(factionState.factionId)) {
                continue;
            }
            int totalTax = 0;
            for (CityState cityState : gameState.findCitiesOwnedBy(factionState.factionId)) {
                totalTax += SeasonalEconomyRules.calculateQuarterlyTax(cityState);
            }
            factionState.gold += totalTax;
            if (gameState.playerFactionId.equals(factionState.factionId)) {
                report.add(new TurnEvent(
                    TurnEventType.QUARTERLY_TAX,
                    factionState.factionId,
                    null,
                    null,
                    totalTax,
                    gameState.findCitiesOwnedBy(factionState.factionId).size()
                ));
            }
        }
    }

    private void resolveHarvest(GameState gameState, TurnResolutionReport report) {
        if (gameState.currentMonth != SeasonalEconomyRules.HARVEST_MONTH) {
            return;
        }
        for (FactionState factionState : gameState.factionStates) {
            if (!factionState.active
                || gameState.neutralFactionId.equals(factionState.factionId)) {
                continue;
            }
            int totalHarvest = 0;
            for (CityState cityState : gameState.findCitiesOwnedBy(factionState.factionId)) {
                totalHarvest += SeasonalEconomyRules.calculateHarvest(cityState);
                cityState.harvestModifierPercent = 100;
            }
            factionState.food += totalHarvest;
            if (gameState.playerFactionId.equals(factionState.factionId)) {
                report.add(new TurnEvent(
                    TurnEventType.HARVEST,
                    factionState.factionId,
                    null,
                    null,
                    totalHarvest,
                    gameState.findCitiesOwnedBy(factionState.factionId).size()
                ));
            }
        }
    }

    private Set<String> resolveArmyMovement(
        GameState gameState,
        TurnResolutionReport report
    ) {
        Set<String> changedOwnerCityIds = new HashSet<>();
        ArmyState[] movementSnapshot = gameState.armyStates.clone();
        for (ArmyState armyState : movementSnapshot) {
            if (!containsArmy(gameState, armyState.armyId)) {
                continue;
            }
            armyState.remainingTravelMonths -= 1;
            if (armyState.remainingTravelMonths > 0) {
                if (gameState.playerFactionId.equals(armyState.factionId)) {
                    report.add(new TurnEvent(
                        TurnEventType.ARMY_ADVANCED,
                        armyState.factionId,
                        armyState.originCityId,
                        armyState.targetCityId,
                        armyState.troops,
                        armyState.remainingTravelMonths
                    ));
                }
                continue;
            }
            if (battleResolutionService.resolveArrival(gameState, armyState, report)) {
                changedOwnerCityIds.add(armyState.targetCityId);
            }
            gameState.removeArmy(armyState.armyId);
        }
        return changedOwnerCityIds;
    }

    private void resolvePublicOrderNaturalRecovery(
        GameState gameState,
        TurnResolutionReport report,
        Set<String> foodShortageFactionIds,
        Set<String> floodedCityIds,
        Set<String> changedOwnerCityIds
    ) {
        for (CityState cityState : gameState.cityStates) {
            FactionState ownerFactionState = gameState.requireFactionState(
                cityState.ownerFactionId
            );
            boolean ownerIsActiveAndNonNeutral = ownerFactionState.active
                && !gameState.neutralFactionId.equals(ownerFactionState.factionId);
            int recoveredPublicOrder = PublicOrderNaturalRecoveryRules.applyMonthEnd(
                cityState,
                ownerIsActiveAndNonNeutral,
                foodShortageFactionIds.contains(ownerFactionState.factionId),
                floodedCityIds.contains(cityState.cityId),
                changedOwnerCityIds.contains(cityState.cityId)
            );
            if (recoveredPublicOrder > 0
                && gameState.playerFactionId.equals(ownerFactionState.factionId)) {
                report.add(new TurnEvent(
                    TurnEventType.PUBLIC_ORDER_NATURALLY_RECOVERED,
                    ownerFactionState.factionId,
                    cityState.cityId,
                    null,
                    recoveredPublicOrder,
                    cityState.publicOrder
                ));
            }
        }
    }

    private boolean containsArmy(GameState gameState, String armyId) {
        for (ArmyState armyState : gameState.armyStates) {
            if (armyId.equals(armyState.armyId)) {
                return true;
            }
        }
        return false;
    }

    private void resolveAnnualPopulation(GameState gameState, TurnResolutionReport report) {
        if (gameState.currentMonth != 12) {
            return;
        }
        for (CityState cityState : gameState.cityStates) {
            int capacity = definitionRepository.requireCity(cityState.cityId).populationCapacity;
            PopulationRules.Projection projection = PopulationRules.project(gameState, cityState, capacity);
            cityState.population += projection.delta();
            if (gameState.playerFactionId.equals(cityState.ownerFactionId)) {
                report.add(new TurnEvent(TurnEventType.POPULATION_CHANGED, cityState.ownerFactionId,
                    cityState.cityId, null, projection.delta(), cityState.population));
            }
        }
    }

    private void advanceCampaignClock(
        GameState gameState,
        TurnResolutionReport report
    ) {
        gameState.elapsedMonths += 1;
        if (gameState.scenarioObjectiveStatus == ScenarioObjectiveStatus.IN_PROGRESS
            && gameState.elapsedMonths >= gameState.turnLimitMonths) {
            gameState.scenarioObjectiveStatus = ScenarioObjectiveStatus.FAILED;
            report.add(new TurnEvent(
                TurnEventType.CAMPAIGN_DEFEAT_TIMEOUT,
                gameState.playerFactionId,
                gameState.victoryTargetCityId,
                null,
                gameState.turnLimitMonths,
                0
            ));
        }

        gameState.currentTurn += 1;
        gameState.currentMonth += 1;
        if (gameState.currentMonth > 12) {
            gameState.currentMonth = 1;
            gameState.currentYear += 1;
        }
        FactionActionPointRules.refreshAll(gameState, false);
        if (gameState.gameplayStatus == GameplayStatus.ACTIVE) {
            // 先完成災害、攻佔、遷都、AI 與人口，再以結算後領地計算下月額度。
            NationalActionPointRules.PublicOrderSummary publicOrderSummary =
                NationalActionPointRules.summarizePlayer(gameState);
            report.add(new TurnEvent(
                TurnEventType.ACTION_POINTS_REFRESHED,
                gameState.playerFactionId,
                null,
                null,
                gameState.actionPointsPerTurn,
                (int) publicOrderSummary.totalPublicOrder()
            ));
        } else {
            gameState.actionPointsRemaining = 0;
        }
    }
}
