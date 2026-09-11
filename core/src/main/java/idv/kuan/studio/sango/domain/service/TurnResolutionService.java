package idv.kuan.studio.sango.domain.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
import idv.kuan.studio.sango.domain.rule.CampaignBalance;
import idv.kuan.studio.sango.domain.rule.FactionActionPointRules;
import idv.kuan.studio.sango.domain.rule.PopulationRules;
import idv.kuan.studio.sango.domain.rule.PublicOrderNaturalRecoveryRules;
import idv.kuan.studio.sango.domain.rule.SeasonalEconomyRules;
import idv.kuan.studio.sango.repository.GameDefinitionRepository;

/**
 * 先讓所有勢力依月初快照完成本月規劃，再統一結算軍糧、經濟、道路接戰與攻城。
 */
public final class TurnResolutionService {
    private final GameDefinitionRepository definitionRepository;
    private final DeterministicEventRoller eventRoller;
    private final BattleResolutionService battleResolutionService;
    private final EnemyTurnService enemyTurnService;
    private final RetreatResolutionService retreatResolutionService;
    private final RoadEncounterResolutionService roadEncounterResolutionService;

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
        this.retreatResolutionService = new RetreatResolutionService();
        this.roadEncounterResolutionService = new RoadEncounterResolutionService();
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
        Set<String> retreatArmyIdsAtTurnStart = retreatArmyIds(nextState);

        enemyTurnService.execute(nextState, mapDefinition, report);
        Set<String> foodShortageFactionIds = resolveMilitaryUpkeep(nextState, report);
        Set<String> floodedCityIds = resolveFloodSeason(nextState, report);
        resolveQuarterlyTax(nextState, report);
        resolveHarvest(nextState, report);
        Set<String> changedOwnerCityIds = resolveArmyMovement(nextState, mapDefinition, report);
        retreatResolutionService.resolve(nextState, mapDefinition, retreatArmyIdsAtTurnStart, report);
        resolvePublicOrderNaturalRecovery(
            nextState,
            report,
            foodShortageFactionIds,
            floodedCityIds,
            changedOwnerCityIds
        );
        resolveAnnualPopulation(nextState, report);
        advanceCampaignClock(nextState, report);
        nextState.turnStartCityStates = copyCityStates(nextState.cityStates);

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
                    shortage,
                    foodCost,
                    report
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
        int shortage,
        int requiredFood,
        TurnResolutionReport report
    ) {
        if (shortage <= 0 || requiredFood <= 0) {
            return 0;
        }
        int moraleLoss = (int) (((long) 20 * shortage + requiredFood - 1L) / requiredFood);
        int publicOrderLoss = Math.min(3,
            (int) (((long) 3 * shortage + requiredFood - 1L) / requiredFood));
        boolean playerFaction = gameState.playerFactionId.equals(factionId);
        for (CityState cityState : gameState.cityStates) {
            if (!factionId.equals(cityState.ownerFactionId)) {
                continue;
            }
            int actualLoss = Math.min(cityState.morale, moraleLoss);
            cityState.morale -= actualLoss;
            cityState.moraleFraction = 0;
            int actualPublicOrderLoss = Math.min(cityState.publicOrder, publicOrderLoss);
            cityState.publicOrder -= actualPublicOrderLoss;
            cityState.publicOrderRecoveryStreakMonths = 0;
            if (playerFaction && actualLoss > 0) {
                report.add(new TurnEvent(TurnEventType.FOOD_SHORTAGE_MORALE,
                    factionId, cityState.cityId, null, actualLoss, cityState.morale));
            }
            if (playerFaction && actualPublicOrderLoss > 0) {
                report.add(new TurnEvent(TurnEventType.FOOD_SHORTAGE_PUBLIC_ORDER,
                    factionId, cityState.cityId, null,
                    actualPublicOrderLoss, cityState.publicOrder));
            }
        }
        for (ArmyState armyState : gameState.armyStates) {
            if (!factionId.equals(armyState.factionId)) {
                continue;
            }
            int actualLoss = Math.min(armyState.morale, moraleLoss);
            armyState.morale -= actualLoss;
            armyState.moraleFraction = 0;
            if (playerFaction && actualLoss > 0) {
                report.add(new TurnEvent(TurnEventType.FOOD_SHORTAGE_MORALE,
                    factionId, armyState.originCityId, armyState.targetCityId,
                    actualLoss, armyState.morale));
            }
        }

        long remainingDeserters = (long) shortage * 4;
        long originalDeserters = remainingDeserters;

        for (ArmyState armyState : gameState.armyStates) {
            if (!factionId.equals(armyState.factionId) || remainingDeserters <= 0) {
                continue;
            }
            int armyLoss = (int) Math.min(armyState.troops / 5, remainingDeserters);
            armyState.troops -= armyLoss;
            remainingDeserters -= armyLoss;
        }
        for (CityState cityState : gameState.cityStates) {
            if (!factionId.equals(cityState.ownerFactionId) || remainingDeserters <= 0) {
                continue;
            }
            int cityLoss = (int) Math.min(cityState.troops / 5, remainingDeserters);
            cityState.troops -= cityLoss;
            remainingDeserters -= cityLoss;
        }
        removeEmptyArmies(gameState);
        return Math.toIntExact(originalDeserters - remainingDeserters);
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
                int agricultureBefore = cityState.agriculture;
                int publicOrderBefore = cityState.publicOrder;
                int populationBefore = cityState.population;
                cityState.agriculture = Math.max(0,
                    cityState.agriculture - CampaignBalance.FLOOD_AGRICULTURE_LOSS);
                cityState.publicOrder = Math.max(0,
                    cityState.publicOrder - CampaignBalance.FLOOD_PUBLIC_ORDER_LOSS);
                cityState.population = Math.max(CampaignBalance.POPULATION_FLOOR,
                    cityState.population - CampaignBalance.FLOOD_POPULATION_LOSS);
                if (gameState.playerFactionId.equals(cityState.ownerFactionId)) {
                    report.add(new TurnEvent(
                        TurnEventType.FLOOD_OCCURRED,
                        cityState.ownerFactionId,
                        cityState.cityId,
                        null,
                        riskPercent,
                        harvestLossPercent,
                        agricultureBefore - cityState.agriculture,
                        publicOrderBefore - cityState.publicOrder,
                        populationBefore - cityState.population
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
        StrategicMapDefinition mapDefinition,
        TurnResolutionReport report
    ) {
        Set<String> changedOwnerCityIds = new HashSet<>();
        roadEncounterResolutionService.resolve(gameState, mapDefinition, report);
        ArmyState[] movementSnapshot = gameState.armyStates.clone();
        for (ArmyState armyState : movementSnapshot) {
            if (armyState.isRetreating() || !containsArmy(gameState, armyState.armyId)) {
                continue;
            }
            if (armyState.remainingTravelMonths > 0) {
                armyState.remainingTravelMonths -= 1;
            }
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
            }
        }

        Set<String> groupIds = new LinkedHashSet<>();
        for (ArmyState armyState : movementSnapshot) {
            if (!armyState.isRetreating() && containsArmy(gameState, armyState.armyId)) {
                groupIds.add(effectiveGroupId(armyState));
            }
        }
        Map<String, List<ArmyState>> arrivalsByBattle = new LinkedHashMap<>();
        for (String groupId : groupIds) {
            List<ArmyState> groupArmies = findGroupArmies(gameState, groupId);
            if (groupArmies.isEmpty() || !allArrived(groupArmies)) {
                continue;
            }
            ArmyState firstArmy = groupArmies.get(0);
            String battleKey = firstArmy.factionId + "\u0000" + firstArmy.targetCityId;
            arrivalsByBattle.computeIfAbsent(battleKey, ignored -> new ArrayList<>())
                .addAll(groupArmies);
        }
        for (List<ArmyState> arrivingArmies : arrivalsByBattle.values()) {
            String targetCityId = arrivingArmies.get(0).targetCityId;
            if (battleResolutionService.resolveArrival(gameState, arrivingArmies, mapDefinition, report)) {
                changedOwnerCityIds.add(targetCityId);
            }
            for (ArmyState armyState : arrivingArmies) {
                if (containsArmy(gameState, armyState.armyId) && !armyState.isRetreating()) {
                    gameState.removeArmy(armyState.armyId);
                }
            }
        }
        return changedOwnerCityIds;
    }

    private CityState[] copyCityStates(CityState[] source) {
        CityState[] copied = new CityState[source.length];
        for (int i = 0; i < source.length; i++) {
            copied[i] = source[i].copy();
        }
        return copied;
    }

    private List<ArmyState> findGroupArmies(GameState gameState, String groupId) {
        List<ArmyState> groupArmies = new ArrayList<>();
        for (ArmyState armyState : gameState.armyStates) {
            if (!armyState.isRetreating() && groupId.equals(effectiveGroupId(armyState))) {
                groupArmies.add(armyState);
            }
        }
        return groupArmies;
    }

    private boolean allArrived(List<ArmyState> armyStates) {
        for (ArmyState armyState : armyStates) {
            if (armyState.remainingTravelMonths > 0) {
                return false;
            }
        }
        return true;
    }

    private String effectiveGroupId(ArmyState armyState) {
        return armyState.expeditionGroupId == null
            ? armyState.armyId : armyState.expeditionGroupId;
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

    private Set<String> retreatArmyIds(GameState gameState) {
        Set<String> ids = new LinkedHashSet<>();
        for (ArmyState armyState : gameState.armyStates) {
            if (armyState.isRetreating()) {
                ids.add(armyState.armyId);
            }
        }
        return ids;
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
                gameState.scenarioObjectiveType
                    == idv.kuan.studio.sango.domain.model.ScenarioObjectiveType.CAPTURE_CITY
                    ? gameState.victoryTargetCityId : null,
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
