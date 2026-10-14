package dom4k.maxbridge;

import dom4k.maxbridge.max.MaxClient;
import dom4k.maxbridge.max.MaxException;
import dom4k.maxbridge.telegram.Bot;
import lombok.extern.slf4j.Slf4j;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Properties;
import java.util.Scanner;
import java.util.UUID;
import dom4k.maxbridge.max.MaxClient.*;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

@Slf4j
public class OldMain {
    private static final Scanner scanner = new Scanner(System.in);
    private static final Properties properties = new Properties();

    public static void main(String[] args) throws IOException, NoSuchAlgorithmException, MaxException {
        loadConfig();

        if (get("uuid") == null) set("uuid", UUID.randomUUID().toString());
        if (get("phone") == null) set("phone", readline("Enter phone number"));
        if (get("token") == null) set("token", readline("Enter bot token"));
        saveConfig();

        MaxClient client = new MaxClient(UUID.fromString(get("uuid")), get("host", "api.oneme.ru"), getInt("port", 443));
        TelegramBotsApi bot = createBot(get("token"));

        client.registerEventListener(OnReactionListener.class, (chatId, messageId, reactionInfo) -> {
            log.info("new reaction on message {} in chat {}: {}", messageId, chatId, reactionInfo);
        });
        client.registerEventListener(OnMessageListener.class, ((chatId, message) -> {
            log.info("new message in chat {} (sender {}): {}", chatId, message.getSender(), message.getText());
        }));

        client.start();
        loginClient(client);

        log.info("Login with: {}", client.getProfile().getContact().getNames()[0].getName());

        client.sendMessage(-70953885177088L, "Hello world");
    }

    private static TelegramBotsApi createBot(String token) {
        try {
            TelegramBotsApi telegramBotsApi = new TelegramBotsApi(DefaultBotSession.class);
            telegramBotsApi.registerBot(new Bot(token));
            return telegramBotsApi;
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
    }

    private static void loginClient(MaxClient client) throws MaxException {
        if (get("authToken") == null) {
            client.sendCode(get("phone"));
            client.enterCode(readline("Enter code from SMS"));

            if (client.getState() == State.WAIT_PASSWORD) {
                String password;
                if (get("password") == null) password = readline("Enter password");
                else password = get("password");

                client.enterPassword(password);
            }

            set("authToken", client.getAuthToken());
            saveConfig();
        } else {
            client.loginWithToken(get("authToken"));
        }
    }

    private static String get(String key) { return properties.getProperty(key); }
    private static String get(String key, String def) { return properties.getProperty(key, def); }
    private static int getInt(String key) { return Integer.parseInt(properties.getProperty(key)); }
    private static int getInt(String key, int def) { return Integer.parseInt(properties.getProperty(key, String.valueOf(def))); }
    private static void set(String key, String value) { properties.put(key, value); }
    private static void set(String key, int value) { properties.put(key, String.valueOf(value)); }

    private static String readline(String prompt) {
        System.out.printf("\033[32m%s: \033[0m", prompt);
        return scanner.nextLine();
    }

    private static void loadConfig() {
        try (FileInputStream fis = new FileInputStream("config.xml")) {
            properties.loadFromXML(fis);
        } catch (IOException ignore) {}
    }

    private static void saveConfig() {
        try (FileOutputStream fos = new FileOutputStream("config.xml")) {
            properties.storeToXML(fos, LocalDateTime.now().toString());
        } catch (IOException ignore) {}
    }
}
