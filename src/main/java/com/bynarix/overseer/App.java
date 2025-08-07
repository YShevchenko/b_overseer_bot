package com.bynarix.overseer;

import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

public class App {
    public static void main(String[] args) throws Exception {
        // Start a small health server on 0.0.0.0:8000
        HealthServer.start(8000);

        TelegramBotsApi api = new TelegramBotsApi(DefaultBotSession.class);
        api.registerBot(new OverseerBot());
        System.out.println("bynarix-overseer started.");
    }
}
