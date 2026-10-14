package dom4k.maxbridge;

import dom4k.maxbridge.max.MaxClient;
import dom4k.maxbridge.models.MessageRef;
import dom4k.maxbridge.models.User;
import dom4k.maxbridge.telegram.Bot;
import lombok.extern.slf4j.Slf4j;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
public class Main {
    private static final Properties properties = new Properties();

    public static void main(String[] args) throws TelegramApiException {
        loadConfig();

        if (get("token") == null) throw new RuntimeException("ТЫ ДОЛБАЕБ, BOT TOKEN IS NULL!!!");

        Bot bot = new Bot(get("token"));

        org.telegram.telegrambots.meta.api.objects.User me = bot.getMe();
        log.info("login as {}", me);

        TelegramBotsApi telegramBotsApi = new TelegramBotsApi(DefaultBotSession.class);
        telegramBotsApi.registerBot(bot);

        long ttlDays = getInt("refTtlDays", 30);
        ScheduledExecutorService cleaner = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "MessageRef cleaner");
            t.setDaemon(true);
            return t;
        });
        cleaner.scheduleWithFixedDelay(() -> {
            try {
                int n = MessageRef.deleteOlderThan(TimeUnit.DAYS.toMillis(ttlDays));
                log.info("MessageRef cleanup: removed {} records older than {} days", n, ttlDays);
            } catch (Exception e) {
                log.error("MessageRef cleanup failed", e);
            }
        }, 0, 6, TimeUnit.HOURS);

        User.forEach((user) -> {
            try {
                MaxClient client = new MaxClient(UUID.fromString(user.getDeviceUUID()), "api.oneme.ru", 443);
                bot.setupClient(user.getTelegramId(), client, user);
            } catch (Exception e) {
                log.error("Failed to set up client for user {}", user.getTelegramId(), e);
            }
        });
    }

    private static String get(String key) { return properties.getProperty(key); }
    private static String get(String key, String def) { return properties.getProperty(key, def); }
    private static int getInt(String key) { return Integer.parseInt(properties.getProperty(key)); }
    private static int getInt(String key, int def) { return Integer.parseInt(properties.getProperty(key, String.valueOf(def))); }
    private static void set(String key, String value) { properties.put(key, value); }
    private static void set(String key, int value) { properties.put(key, String.valueOf(value)); }

    private static void loadConfig() {
        try (FileInputStream fis = new FileInputStream("config.xml")) {
            properties.loadFromXML(fis);
        } catch (IOException ignore) {
        }
    }

}
