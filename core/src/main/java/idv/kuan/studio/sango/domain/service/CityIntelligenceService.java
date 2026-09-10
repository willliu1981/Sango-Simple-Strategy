package idv.kuan.studio.sango.domain.service;

import java.util.ArrayList;
import java.util.List;

import idv.kuan.studio.sango.domain.model.CityIntelligenceSnapshot;
import idv.kuan.studio.sango.domain.model.CityState;
import idv.kuan.studio.sango.domain.model.FactionState;
import idv.kuan.studio.sango.domain.model.GameState;

/** 集中建立、更新及讀取各勢力彼此隔離的偵察快照。 */
public final class CityIntelligenceService {
    public static final int DURATION_TURNS = 3;

    public CityIntelligenceSnapshot observe(GameState gameState, String observerFactionId,
        String cityId) {
        CityState city = observableCity(gameState, observerFactionId, cityId);
        CityIntelligenceSnapshot snapshot = snapshot(gameState, city);
        FactionState observer = gameState.requireFactionState(observerFactionId);
        List<CityIntelligenceSnapshot> intelligence = new ArrayList<>();
        if (observer.cityIntelligence != null) {
            for (CityIntelligenceSnapshot existing : observer.cityIntelligence) {
                if (existing != null && !cityId.equals(existing.cityId)) {
                    intelligence.add(existing);
                }
            }
        }
        intelligence.add(snapshot);
        observer.cityIntelligence = intelligence.toArray(new CityIntelligenceSnapshot[0]);
        return snapshot;
    }

    public CityIntelligenceSnapshot restoreLegacy(GameState gameState,
        String observerFactionId, String cityId, int validThroughTurn) {
        if (validThroughTurn < gameState.currentTurn) {
            return null;
        }
        CityIntelligenceSnapshot snapshot = snapshot(
            gameState, gameState.requireCityState(cityId));
        snapshot.observationDateRecorded = false;
        snapshot.validThroughTurn = validThroughTurn;
        replace(gameState.requireFactionState(observerFactionId), snapshot);
        return snapshot;
    }

    public CityIntelligenceSnapshot findSnapshot(GameState gameState, String observerFactionId,
        String cityId) {
        FactionState observer = gameState.requireFactionState(observerFactionId);
        if (observer.cityIntelligence == null) {
            return null;
        }
        for (CityIntelligenceSnapshot snapshot : observer.cityIntelligence) {
            if (snapshot != null && cityId.equals(snapshot.cityId)
                && snapshot.validThroughTurn >= gameState.currentTurn) {
                return snapshot;
            }
        }
        return null;
    }

    public KnownCityView knownView(GameState gameState, String observerFactionId, String cityId) {
        CityState city = gameState.requireCityState(cityId);
        if (observerFactionId.equals(city.ownerFactionId)) {
            return exactView(city, gameState.currentTurn, gameState.currentYear,
                gameState.currentMonth, Integer.MAX_VALUE);
        }
        CityIntelligenceSnapshot snapshot = findSnapshot(gameState, observerFactionId, cityId);
        if (snapshot != null) {
            return new KnownCityView(snapshot.cityId, snapshot.ownerFactionId, true,
                snapshot.observedTurn, snapshot.observedYear, snapshot.observedMonth,
                snapshot.observationDateRecorded, snapshot.validThroughTurn,
                snapshot.troops, snapshot.population,
                snapshot.agriculture, snapshot.commerce, snapshot.waterControl,
                snapshot.defense, snapshot.training, snapshot.morale, snapshot.publicOrder);
        }
        int estimate = 800 + Math.floorMod(cityId.hashCode(), 13) * 100;
        return new KnownCityView(cityId, city.ownerFactionId, false, 0, 0, 0, false, 0,
            estimate, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    private CityIntelligenceSnapshot snapshot(GameState gameState, CityState city) {
        CityIntelligenceSnapshot snapshot = new CityIntelligenceSnapshot();
        snapshot.cityId = city.cityId;
        snapshot.observedTurn = gameState.currentTurn;
        snapshot.observedYear = gameState.currentYear;
        snapshot.observedMonth = gameState.currentMonth;
        snapshot.observationDateRecorded = true;
        snapshot.validThroughTurn = gameState.currentTurn + DURATION_TURNS - 1;
        snapshot.ownerFactionId = city.ownerFactionId;
        snapshot.troops = city.troops;
        snapshot.population = city.population;
        snapshot.agriculture = city.agriculture;
        snapshot.commerce = city.commerce;
        snapshot.waterControl = city.waterControl;
        snapshot.defense = city.defense;
        snapshot.training = city.training;
        snapshot.morale = city.morale;
        snapshot.publicOrder = city.publicOrder;
        return snapshot;
    }

    private KnownCityView exactView(CityState city, int turn, int year, int month,
        int validThrough) {
        return new KnownCityView(city.cityId, city.ownerFactionId, true, turn, year, month,
            true, validThrough, city.troops, city.population, city.agriculture, city.commerce,
            city.waterControl, city.defense, city.training, city.morale, city.publicOrder);
    }

    private CityState observableCity(GameState gameState, String observerFactionId,
        String cityId) {
        CityState current = gameState.requireCityState(cityId);
        if (observerFactionId.equals(current.ownerFactionId)
            || gameState.turnStartCityStates == null) {
            return current;
        }
        for (CityState snapshot : gameState.turnStartCityStates) {
            if (snapshot != null && cityId.equals(snapshot.cityId)) {
                return snapshot;
            }
        }
        return current;
    }

    private void replace(FactionState observer, CityIntelligenceSnapshot snapshot) {
        List<CityIntelligenceSnapshot> intelligence = new ArrayList<>();
        if (observer.cityIntelligence != null) {
            for (CityIntelligenceSnapshot existing : observer.cityIntelligence) {
                if (existing != null && !snapshot.cityId.equals(existing.cityId)) {
                    intelligence.add(existing);
                }
            }
        }
        intelligence.add(snapshot);
        observer.cityIntelligence = intelligence.toArray(new CityIntelligenceSnapshot[0]);
    }
}
