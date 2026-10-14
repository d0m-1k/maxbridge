package dom4k.maxbridge.telegram;

import dom4k.maxbridge.max.MaxClient;
import dom4k.maxbridge.max.MaxException;
import dom4k.maxbridge.max.packets.GetChatsRequest;
import dom4k.maxbridge.max.packets.GetChatsResponse;
import dom4k.maxbridge.max.types.Chat;
import dom4k.maxbridge.max.types.Profile;
import dom4k.maxbridge.models.User;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.UUID;

@Slf4j @AllArgsConstructor
public class CommandHandler {
    private static final int TELEGRAM_MESSAGE_LIMIT = 4096;

    private final Bot bot;

    public void onMessage(Message message) throws TelegramApiException {
        if (message.getText() == null || message.getFrom() == null) return;

        String[] args = message.getText().trim().split(" +");

        MaxClient client = bot.getClientById(message.getFrom().getId());

        log.debug("Client: {}", client);
        if ("отмена".equals(message.getText().trim().toLowerCase(Locale.ROOT)) && isAuthInProgress(client)) {
            cancelCommand(message, args);
        } else if (client != null) {
            log.debug("State: {}", client.getState());
            switch (client.getState()) {
                case WAIT_LOGIN -> phoneHandler(message, args);
                case WAIT_SMS -> codeHandler(message, args);
                case WAIT_PASSWORD -> passwordHandler(message, args);
                default -> onCommand(message, args);
            }
        } else onCommand(message, args);
    }

    private static boolean isAuthInProgress(MaxClient client) {
        if (client == null) return false;
        MaxClient.State state = client.getState();
        return state == MaxClient.State.WAIT_LOGIN
                || state == MaxClient.State.WAIT_SMS
                || state == MaxClient.State.WAIT_PASSWORD;
    }

    public void onCommand(Message message, String[] args) {
        try {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "/start" -> startCommand(message, args);
                case "/login" -> loginCommand(message, args);
                case "/logout" -> logoutCommand(message, args);
                case "/status" -> statusCommand(message, args);
                case "/chats" -> chatsCommand(message, args);
                default -> unknownCommand(message, args);
            }
        } catch (TelegramApiException e) {
            log.error("Failed to handle command", e);
        } catch (RuntimeException e) {
            log.error("Command '{}' failed", args[0], e);
            try {
                replyPlain(message, "Ошибка при выполнении команды, попробуй позже");
            } catch (TelegramApiException ignored) {}
        }
    }

    private void replyPlain(Message message, String text) throws TelegramApiException {
        bot.execute(SendMessage.builder()
                .chatId(message.getChatId())
                .text(text)
                .build());
    }

    private void replyMarkdown(Message message, String text) throws TelegramApiException {
        bot.execute(SendMessage.builder()
                .chatId(message.getChatId())
                .text(text)
                .parseMode(ParseMode.MARKDOWN)
                .build());
    }

    private static String truncate(String text) {
        if (text.length() <= TELEGRAM_MESSAGE_LIMIT) return text;
        return text.substring(0, TELEGRAM_MESSAGE_LIMIT - 3) + "...";
    }

    private void chatsCommand(Message message, String[] args) throws TelegramApiException {
        MaxClient client = bot.getClientById(message.getFrom().getId());
        if (client == null || client.getState() != MaxClient.State.READY) {
            replyPlain(message, "Ты не авторизован, используй /login");
            return;
        }

        StringBuilder sb = new StringBuilder();

        client.getChats().forEach((id, chat) -> {
            sb.append(chat.getId());
            if (chat.getTitle() != null) sb.append(" — ").append(chat.getTitle());
            sb.append(" (").append(chat.getType()).append(", непрочитанных: ").append(chat.getNewMessages()).append(")\n");
        });

        if (client.getChats().isEmpty()) {
            replyPlain(message, "Чатов нет");
            return;
        }

        replyPlain(message, truncate("Chat list (" + client.getChats().size() + "):\n" + sb));
    }

    private void statusCommand(Message message, String[] args) throws TelegramApiException {
        MaxClient client = bot.getClientById(message.getFrom().getId());
        if (client == null || client.getState() != MaxClient.State.READY) {
            replyPlain(message, "Ты не авторизован, используй /login");
            return;
        }

        StringBuilder out = new StringBuilder("Status:\n");

        Profile profile = client.getProfile();
        if (profile != null) {
            out.append("  Profile:\n");

            Profile.Contact contact = profile.getContact();
            if (contact != null) {
                out.append("    Contact:\n");

                out.append("      ID: ").append(contact.getId()).append('\n');
                out.append("      About: ").append(contact.getAbout()).append('\n');
                out.append("      Description: ").append(contact.getDescription()).append('\n');
                out.append("      Picture: ").append(contact.getPicture()).append('\n');
                out.append("      Registration Date: ").append(contact.getRegistrationDate()).append('\n');

                Profile.Contact.Name[] names = contact.getNames();
                if (names != null && names.length != 0) {
                    out.append("      Names:\n");
                    for (Profile.Contact.Name name : names) {
                        String display = name.getName() != null
                                ? name.getName()
                                : ((name.getFirstName() != null ? name.getFirstName() : "")
                                + " " + (name.getLastName() != null ? name.getLastName() : "")).trim();
                        out.append("        - ").append(display).append(" (").append(name.getType()).append(")\n");
                    }
                }

                Profile.Contact.Phone[] phones = contact.getPhones();
                if (phones != null && phones.length != 0) {
                    out.append("      Phones:\n");
                    for (Profile.Contact.Phone phone : phones) {
                        out.append("        - ").append(phone.getNumber()).append(" (").append(phone.getType()).append(")\n");
                    }
                }
            }
        }

        replyPlain(message, truncate(out.toString()));
    }

    public void startCommand(Message message, String[] args) throws TelegramApiException {
        User user = User.getById(message.getFrom().getId());
        if (user == null) {
            user = User.builder()
                    .authToken(null)
                    .telegramId(message.getFrom().getId())
                    .deviceUUID(UUID.randomUUID().toString())
                    .build();
            user.save();
        }

        replyMarkdown(message, "Привет, если ты не разраб то ты не разберёшься!\nМне лень писать /help");
    }

    public void cancelCommand(Message message, String[] args) throws TelegramApiException {
        MaxClient client = bot.getClientById(message.getFrom().getId());
        if (client == null) return;
        client.stop();

        replyPlain(message, "Действие отменено");
    }

    public void phoneHandler(Message message, String[] args) throws TelegramApiException {
        MaxClient client = bot.getClientById(message.getFrom().getId());
        if (client == null) return;
        String regex = "^\\+7\\d{10}$";
        String phone = message.getText()
                .replace(" ", "")
                .replace("(", "")
                .replace(")", "")
                .replace("-", "");

        if (!phone.matches(regex)) {
            replyPlain(message, "Неверный формат номера. Введи номер в формате +79991234567 или ОТМЕНА для отмены");
            return;
        }

        try {
            client.sendCode(phone);
            replyMarkdown(message, "Введи код или `ОТМЕНА` для отмены");
        } catch (MaxException | RuntimeException e) {
            log.error("Failed to send verification code", e);
            client.stop();
            replyPlain(message, "Не удалось отправить код. Попробуй ещё раз: /login");
        }
    }

    public void codeHandler(Message message, String[] args) throws TelegramApiException {
        MaxClient client = bot.getClientById(message.getFrom().getId());
        if (client == null) return;

        try {
            client.enterCode(message.getText().trim());
        } catch (MaxException e) {
            replyPlain(message, "Неверный код. Попробуй ещё раз или напиши ОТМЕНА для отмены");
            return;
        } catch (RuntimeException e) {
            log.error("Failed to enter code", e);
            client.stop();
            replyPlain(message, "Ошибка соединения. Попробуй ещё раз: /login");
            return;
        }

        if (client.getState() == MaxClient.State.WAIT_PASSWORD) {
            replyPlain(message, "Введи пароль или ОТМЕНА для отмены\nПодсказка: " + client.getPasswordHint());
        } else {
            saveToken(message, client);
            replyPlain(message, "Авторизация прошла успешно");
        }
    }

    public void passwordHandler(Message message, String[] args) throws TelegramApiException {
        MaxClient client = bot.getClientById(message.getFrom().getId());
        if (client == null) return;
        try {
            client.enterPassword(message.getText());
        } catch (MaxException e) {
            replyMarkdown(message, "*Неверный пароль!* Попробуй еще раз или напиши `ОТМЕНА` для отмены");
            return;
        } catch (RuntimeException e) {
            log.error("Failed to enter password", e);
            client.stop();
            replyPlain(message, "Ошибка соединения. Попробуй ещё раз: /login");
            return;
        }

        bot.execute(DeleteMessage.builder()
                .chatId(message.getChatId())
                .messageId(message.getMessageId())
                .build());

        saveToken(message, client);
        replyPlain(message, "Авторизация прошла успешно");
    }

    private void saveToken(Message message, MaxClient client) {
        User user = User.getById(message.getFrom().getId());
        if (user == null) {
            log.warn("User {} is not registered, token not saved", message.getFrom().getId());
            return;
        }
        user.setAuthToken(client.getAuthToken());
        user.save();
    }

    public void logoutCommand(Message message, String[] args) throws TelegramApiException {
        MaxClient client = bot.getClientById(message.getFrom().getId());
        if (client == null) return;
        client.stop();

        User user = User.getById(message.getFrom().getId());
        if (user != null) {
            user.setAuthToken(null);
            user.save();
        }

        replyPlain(message, "Ты вышел из аккаунта. Для входа используй /login");
    }

    public void loginCommand(Message message, String[] args) throws TelegramApiException {
        User user = User.getById(message.getFrom().getId());
        if (user == null) {
            replyPlain(message, "Сначала выполни /start");
            return;
        }

        MaxClient client = bot.getClientById(message.getFrom().getId());

        if (client != null && client.getState() == MaxClient.State.READY) {
            replyPlain(message, "Вы уже авторизованы, для выхода используйте /logout");
            return;
        }

        if (client == null) {
            client = new MaxClient(UUID.fromString(user.getDeviceUUID()), "api.oneme.ru", 443);
            bot.addClient(message.getFrom().getId(), client, user);
        }

        try {
            client.start();
        } catch (IOException | NoSuchAlgorithmException e) {
            log.error("Failed to connect to MAX", e);
            replyPlain(message, "Не удалось подключиться к MAX, попробуй позже");
            return;
        }

        replyMarkdown(message, "Введи номер телефона или `ОТМЕНА` для отмены");
    }

    public void unknownCommand(Message message, String[] args) throws TelegramApiException {
        replyMarkdown(message, "Я тебя не понял, поясни по русски");
    }
}
