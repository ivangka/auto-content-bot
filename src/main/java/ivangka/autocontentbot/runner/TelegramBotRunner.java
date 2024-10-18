package ivangka.autocontentbot.runner;

import ivangka.autocontentbot.config.TelegramBotConfig;
import ivangka.autocontentbot.controller.TelegramBotController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class TelegramBotRunner implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(TelegramBotRunner.class);

    private final TelegramBotsLongPollingApplication botsApplication;
    private final TelegramBotController telegramBotController;
    private final TelegramBotConfig telegramBotConfig;

    @Autowired
    public TelegramBotRunner(TelegramBotsLongPollingApplication botsApplication,
                             TelegramBotController telegramBotController,
                             TelegramBotConfig telegramBotConfig) {
        this.botsApplication = botsApplication;
        this.telegramBotController = telegramBotController;
        this.telegramBotConfig = telegramBotConfig;
    }

    @Override
    public void run(String... args) {

        logger.info("Starting TelegramBotRunner...");
        int maxRetries = 3;
        int attempt = 0;
        boolean registered = false;

        while (!registered) {
            try {
                logger.debug("Registering bot, attempt " + (attempt + 1));
                botsApplication.registerBot(telegramBotConfig.getBotToken(), telegramBotController);
                logger.info("Bot registered successfully");
                registered = true;
                Thread.currentThread().join();
            } catch (TelegramApiException e) {
                attempt++;
                logger.error("Error occurred during bot registration: attempt " + attempt, e);
                if (attempt >= maxRetries) {
                    throw new RuntimeException("Failed to register bot after " + maxRetries + " attempts", e);
                }
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ie) {
                    logger.warn("Retry thread was interrupted", ie);
                    Thread.currentThread().interrupt();
                    break;
                }
            } catch (InterruptedException e) {
                logger.warn("Thread was interrupted", e);
                Thread.currentThread().interrupt();
            }
        }

        if (!registered) {
            logger.error("Bot registration failed after all retry attempts.");
        }

    }

}