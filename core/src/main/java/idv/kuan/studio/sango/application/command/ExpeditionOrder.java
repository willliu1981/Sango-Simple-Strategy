package idv.kuan.studio.sango.application.command;

/** 聯合出征中單一來源城池的派兵命令。 */
public record ExpeditionOrder(String originCityId, int troops) {
}
