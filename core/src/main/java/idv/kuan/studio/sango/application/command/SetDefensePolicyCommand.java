package idv.kuan.studio.sango.application.command;

import idv.kuan.studio.sango.domain.model.CityState;
import idv.kuan.studio.sango.domain.model.GameState;
import idv.kuan.studio.sango.domain.model.GameStateValidator;
import idv.kuan.studio.sango.domain.rule.DefensePolicy;

/** 更新己方城池的持久防守方針；此設定不消耗行動力。 */
public final class SetDefensePolicyCommand {
    public GameState execute(GameState currentState, String factionId, String cityId,
        DefensePolicy policy) {
        if (policy == null) {
            throw new IllegalArgumentException("防守方針不可為空。");
        }
        GameStateValidator.validate(currentState);
        GameState nextState = currentState.copy();
        CityState city = nextState.requireCityState(cityId);
        if (!factionId.equals(city.ownerFactionId)) {
            throw new IllegalArgumentException("只能設定己方城池的防守方針。");
        }
        city.defensePolicy = policy.normalized();
        nextState.lastActionCode = "SET_DEFENSE_POLICY";
        GameStateValidator.validate(nextState);
        return nextState;
    }
}
