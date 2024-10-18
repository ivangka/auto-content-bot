package ivangka.autocontentbot.config;

import ivangka.autocontentbot.model.UserState;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.concurrent.ConcurrentHashMap;

@PropertySource("classpath:application.properties")
@Configuration
public class TelegramBotConfig {

    @Value("${bot.token}")
    private String botToken;

    public String getBotToken() {
        return botToken;
    }

    @Bean
    public TelegramClient telegramClient() {
        return new OkHttpTelegramClient(botToken);
    }

    @Bean
    public TelegramBotsLongPollingApplication botsApplication() {
        return  new TelegramBotsLongPollingApplication();
    }

    @Bean
    public ConcurrentHashMap<Long, UserState> concurrentHashMap() {
        return  new ConcurrentHashMap<>();
    }

}
