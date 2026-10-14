package dom4k.maxbridge.telegram;

import dom4k.maxbridge.max.MaxClient;
import dom4k.maxbridge.max.MaxException;
import dom4k.maxbridge.max.types.Chat;
import dom4k.maxbridge.models.ChatTopic;
import dom4k.maxbridge.models.MessageRef;
import dom4k.maxbridge.models.User;
import lombok.extern.slf4j.Slf4j;
import org.telegram.telegrambots.bots.DefaultBotOptions;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import org.telegram.telegrambots.meta.api.objects.Message;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
public class Bot extends TelegramLongPollingBot {
    private static final String CB_PIN = "pin_";
    private static final String CB_READ = "read_";
    private static final String CB_DEL = "del_";
    private final ConcurrentMap<String, Long> pendingDelete = new ConcurrentHashMap<>();

    private final String token;
    private final CommandHandler handler = new CommandHandler(this);
    private final ConcurrentMap<Long, MaxClient> clients = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, Long> topicsUnavailableUntil = new ConcurrentHashMap<>();
    private static final long TOPICS_RETRY_MS = 10 * 60 * 1000L;

    @Override
    public String getBotUsername() {
        return "xyz";
    }

    public Bot(String token) {
        super(buildOptions());
        this.token = token;
    }

    private static DefaultBotOptions buildOptions() {
        DefaultBotOptions options = new DefaultBotOptions();
        options.setAllowedUpdates(java.util.List.of("message", "message_reaction", "callback_query", "inline_query"));
        return options;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.getMessageReaction() != null) {
            try {
                handleTelegramReaction(update.getMessageReaction());
            } catch (Exception e) {
                log.error("Failed to handle reaction update {}", update.getUpdateId(), e);
            }
            return;
        }
        if (update.hasCallbackQuery()) {
            try {
                handleCallback(update.getCallbackQuery());
            } catch (Exception e) {
                log.error("Failed to handle callback {}", update.getUpdateId(), e);
            }
            return;
        }
        if (update.hasInlineQuery()) {
            try {
                handleInlineQuery(update.getInlineQuery());
            } catch (Exception e) {
                log.error("Failed to handle inline query {}", update.getUpdateId(), e);
            }
            return;
        }
        if (update.hasMessage() && update.getMessage().hasText()) {
            try {
                String incoming = update.getMessage().getText();
                if (incoming.startsWith("/fwd ")) {
                    handleForwardCommand(update.getMessage());
                    return;
                }
                if (incoming.equals("/topics") || incoming.startsWith("/topics ")) {
                    handleTopicsCommand(update.getMessage());
                    return;
                }
                if (handleReplyToMax(update.getMessage())) return;
                if (handleTopicMessage(update.getMessage())) return;
                handler.onMessage(update.getMessage());
            } catch (Exception e) {
                log.error("Failed to handle update {}", update.getUpdateId(), e);
            }
        }
    }

    private static dom4k.maxbridge.max.types.Message forwardedOf(dom4k.maxbridge.max.types.Message message) {
        dom4k.maxbridge.max.types.Message.Link link = message.getLink();
        return link != null && "FORWARD".equals(link.getType()) ? link.getMessage() : null;
    }

    private String composeText(MaxClient client, dom4k.maxbridge.max.types.Message message) {
        String own = message.getText() == null ? "" : message.getText();
        dom4k.maxbridge.max.types.Message fwd = forwardedOf(message);
        if (fwd == null) return own;

        String fwdText = fwd.getText() == null ? "" : fwd.getText();
        String from = fwd.getSender() != 0 ? client.getContactName(fwd.getSender()) : null;
        String header = "↪ Переслано" + (from != null && !from.isBlank() ? " от " + from : "");
        StringBuilder sb = new StringBuilder(own);
        if (sb.length() > 0) sb.append("\n\n");
        sb.append(header);
        if (!fwdText.isEmpty()) sb.append(":\n").append(fwdText);
        return sb.toString();
    }

    private static dom4k.maxbridge.max.types.Message.Attach[] allAttaches(dom4k.maxbridge.max.types.Message message) {
        java.util.List<dom4k.maxbridge.max.types.Message.Attach> all = new java.util.ArrayList<>();
        if (message.getAttaches() != null) all.addAll(java.util.Arrays.asList(message.getAttaches()));
        dom4k.maxbridge.max.types.Message fwd = forwardedOf(message);
        if (fwd != null && fwd.getAttaches() != null) all.addAll(java.util.Arrays.asList(fwd.getAttaches()));
        return all.toArray(dom4k.maxbridge.max.types.Message.Attach[]::new);
    }

    private static boolean hasPhotos(dom4k.maxbridge.max.types.Message message) {
        for (dom4k.maxbridge.max.types.Message.Attach a : allAttaches(message))
            if (a != null && "PHOTO".equals(a.getType()) && a.getBaseUrl() != null && !a.getBaseUrl().isBlank()) return true;
        return false;
    }

    private static boolean isThreadError(Exception e) {
        return e.getMessage() != null && e.getMessage().toLowerCase(java.util.Locale.ROOT).contains("thread");
    }

    private int deliver(MaxClient client, long tgUserId, long chatId, dom4k.maxbridge.max.types.Message message,
                        String sender, String chatTitle, String text, String attaches, Long thread) throws TelegramApiException {
        try {
            return buildRichMessage(client, message, chatId, sender, chatTitle, text, attaches, true)
                    .thread(thread).send(token, tgUserId);
        } catch (IOException | RuntimeException e) {
            if (isThreadError(e)) throw new TelegramApiException(e.getMessage());
            log.warn("Rich message with buttons failed for user {}: {}", tgUserId, e.getMessage());
        }
        try {
            return buildRichMessage(client, message, chatId, sender, chatTitle, text, attaches, false)
                    .thread(thread).send(token, tgUserId);
        } catch (IOException | RuntimeException e) {
            if (isThreadError(e)) throw new TelegramApiException(e.getMessage());
            log.warn("Rich message failed for user {}, falling back to plain message: {}", tgUserId, e.getMessage());
        }
        return sendPlain(client, tgUserId, message, sender, chatTitle, text, attaches, thread);
    }

    private Long ensureTopic(MaxClient client, long tgUserId, long maxChatId) {
        if (maxChatId == 0) return null;
        Long blockedUntil = topicsUnavailableUntil.get(tgUserId);
        if (blockedUntil != null && System.currentTimeMillis() < blockedUntil) return null;

        ChatTopic existing = ChatTopic.findByChat(tgUserId, maxChatId);
        if (existing != null) return existing.getThreadId();

        Chat chat = client.getChats().get(maxChatId);
        String title = chat != null ? chatTitleFor(client, chat) : "Чат " + maxChatId;
        if (title.length() > 128) title = title.substring(0, 128);
        try {
            com.fasterxml.jackson.databind.node.ObjectNode params = TelegramRaw.params();
            params.put("chat_id", tgUserId);
            params.put("name", title);
            long threadId = TelegramRaw.call(token, "createForumTopic", params).path("message_thread_id").asLong(0);
            if (threadId == 0) throw new IOException("createForumTopic: no message_thread_id in response");
            ChatTopic.builder().telegramUserId(tgUserId).maxChatId(maxChatId).threadId(threadId).title(title).build().save();
            log.info("Created topic '{}' (thread {}) for MAX chat {}", title, threadId, maxChatId);
            topicsUnavailableUntil.remove(tgUserId);
            return threadId;
        } catch (IOException | RuntimeException e) {
            log.warn("Topics unavailable for user {} (retry in 10 min): {}", tgUserId, e.getMessage());
            topicsUnavailableUntil.put(tgUserId, System.currentTimeMillis() + TOPICS_RETRY_MS);
            return null;
        }
    }

    private boolean handleTopicMessage(Message message) throws TelegramApiException {
        Integer thread = message.getMessageThreadId();
        if (thread == null || message.getFrom() == null) return false;
        String text = message.getText();
        if (text == null || text.startsWith("/")) return false;

        long userId = message.getFrom().getId();
        ChatTopic topic = ChatTopic.findByThread(userId, thread);
        if (topic == null) return false;

        MaxClient client = clients.get(userId);
        if (client == null || client.getState() != MaxClient.State.READY) return false;

        try {
            client.sendMessage(topic.getMaxChatId(), text);
        } catch (MaxException | RuntimeException e) {
            log.error("Failed to send topic message to MAX for user {}", userId, e);
            this.execute(SendMessage.builder()
                    .chatId(message.getChatId())
                    .messageThreadId(thread)
                    .replyToMessageId(message.getMessageId())
                    .text("Не удалось отправить сообщение в MAX: "+e.toString())
                    .build());
        }
        return true;
    }

    private void handleTopicsCommand(Message message) throws TelegramApiException {
        long userId = message.getFrom().getId();
        String[] parts = message.getText().trim().split("\\s+");
        String reply;
        if (parts.length > 1 && parts[1].equalsIgnoreCase("reset")) {
            int n = ChatTopic.deleteAll(userId);
            topicsUnavailableUntil.remove(userId);
            reply = "Список тем сброшен (" + n + ").";
        } else {
            long count = ChatTopic.count(userId);
            Long blockedUntil = topicsUnavailableUntil.get(userId);
            boolean blocked = blockedUntil != null && System.currentTimeMillis() < blockedUntil;
            reply = "Тем создано: " + count + "\n\n/topics reset — пересоздать темы.";
        }
        SendMessage.SendMessageBuilder b = SendMessage.builder().chatId(message.getChatId()).text(reply);
        if (message.getMessageThreadId() != null) b.messageThreadId(message.getMessageThreadId());
        this.execute(b.build());
    }

    private void handleCallback(org.telegram.telegrambots.meta.api.objects.CallbackQuery query) throws TelegramApiException {
        String data = query.getData() == null ? "" : query.getData();
        long userId = query.getFrom().getId();
        MessageRef ref = query.getMessage() == null ? null
                : MessageRef.find(userId, query.getMessage().getMessageId());
        MaxClient client = clients.get(userId);

        String answer;
        boolean alert = false;
        if (ref == null || client == null) {
            answer = "Сообщение устарело или нет подключения к MAX";
        } else {
            try {
                long maxMessageId = Long.parseLong(ref.getMaxMessageId());
                if (data.startsWith(CB_READ)) {
                    client.markRead(ref.getMaxChatId(), maxMessageId);
                    answer = "Отмечено прочитанным";
                } else if (data.startsWith(CB_DEL)) {
                    String key = userId + ":" + query.getMessage().getMessageId();
                    long now = System.currentTimeMillis();
                    Long first = pendingDelete.remove(key);
                    if (first != null && now - first < 10_000) {
                        client.deleteMessage(ref.getMaxChatId(), maxMessageId);
                        answer = "Удалено в MAX (только у вас)";
                        try {
                            this.execute(org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage.builder()
                                    .chatId(String.valueOf(userId))
                                    .messageId(query.getMessage().getMessageId())
                                    .build());
                        } catch (TelegramApiException e) {
                            log.warn("Failed to delete Telegram message: {}", e.getMessage());
                        }
                    } else {
                        pendingDelete.put(key, now);
                        answer = "Нажмите «Удалить» ещё раз в течение 10 секунд, чтобы подтвердить";
                        alert = true;
                    }
                } else if (data.startsWith(CB_PIN)) {
                    answer = "Закрепление еще не реализованно";
                } else {
                    answer = "Неизвестная команда";
                }
            } catch (MaxException | RuntimeException e) {
                log.error("Callback '{}' failed for user {}", data, userId, e);
                answer = "Ошибка MAX: " + e.getMessage();
                alert = true;
            }
        }
        this.execute(org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery.builder()
                .callbackQueryId(query.getId())
                .text(answer)
                .showAlert(alert)
                .build());
    }

    private String chatTitleFor(MaxClient client, Chat chat) {
        if (chat.getTitle() != null && !chat.getTitle().isBlank()) return chat.getTitle();
        if (chat.getParticipants() != null) {
            for (Long participant : chat.getParticipants().keySet()) {
                String name = client.getContactName(participant);
                if (name != null && !"Вы".equals(name) && !name.isBlank()) return name;
            }
        }
        return chat.getParticipants() != null && !chat.getParticipants().isEmpty() ? "Избранное" : "Чат " + chat.getId();
    }

    private void handleInlineQuery(org.telegram.telegrambots.meta.api.objects.inlinequery.InlineQuery query) throws TelegramApiException {
        MaxClient client = clients.get(query.getFrom().getId());
        java.util.List<org.telegram.telegrambots.meta.api.objects.inlinequery.result.InlineQueryResult> results = new java.util.ArrayList<>();

        String[] parts = query.getQuery() == null ? new String[0] : query.getQuery().trim().split("\\s+", 4);
        if (client != null && parts.length >= 3 && parts[0].equalsIgnoreCase("forward")
                && parts[1].matches("-?\\d+") && parts[2].matches("\\d+")) {
            String filter = parts.length == 4 ? parts[3].toLowerCase() : "";
            java.util.List<Chat> chats = new java.util.ArrayList<>(client.getChats().values());
            chats.sort(java.util.Comparator.comparingLong(Chat::getLastEventTime).reversed());
            for (Chat chat : chats) {
                if (results.size() >= 25) break;
                String title = chatTitleFor(client, chat);
                if (!filter.isEmpty() && !title.toLowerCase().contains(filter)) continue;
                results.add(org.telegram.telegrambots.meta.api.objects.inlinequery.result.InlineQueryResultArticle.builder()
                        .id(String.valueOf(chat.getId()))
                        .title(title)
                        .description("Переслать сюда")
                        .inputMessageContent(org.telegram.telegrambots.meta.api.objects.inlinequery.inputmessagecontent.InputTextMessageContent.builder()
                                .messageText("/fwd " + chat.getId() + " " + parts[1] + " " + parts[2])
                                .build())
                        .build());
            }
        }
        this.execute(org.telegram.telegrambots.meta.api.methods.AnswerInlineQuery.builder()
                .inlineQueryId(query.getId())
                .results(results)
                .cacheTime(0)
                .isPersonal(true)
                .build());
    }

    private void handleForwardCommand(Message telegramMessage) throws TelegramApiException {
        long userId = telegramMessage.getFrom().getId();
        String[] parts = telegramMessage.getText().trim().split("\\s+");
        String result;
        MaxClient client = clients.get(userId);
        if (client == null || parts.length != 4) {
            result = "Не удалось переслать: нет подключения к MAX";
        } else {
            try {
                long dst = Long.parseLong(parts[1]);
                long src = Long.parseLong(parts[2]);
                long msg = Long.parseLong(parts[3]);
                client.forwardMessage(src, msg, dst);
                Chat chat = client.getChats().get(dst);
                result = "↪ Переслано" + (chat != null ? " в «" + chatTitleFor(client, chat) + "»" : "");
            } catch (MaxException | RuntimeException e) {
                log.error("Forward failed for user {}", userId, e);
                result = "Не удалось переслать: " + e.getMessage();
            }
        }
        try {
            this.execute(org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage.builder()
                    .chatId(String.valueOf(userId)).messageId(telegramMessage.getMessageId()).build());
        } catch (TelegramApiException e) {
            log.warn("Failed to delete /fwd command message: {}", e.getMessage());
        }
        SendMessage.SendMessageBuilder confirm = SendMessage.builder().chatId(userId).text(result);
        if (telegramMessage.getMessageThreadId() != null) confirm.messageThreadId(telegramMessage.getMessageThreadId());
        this.execute(confirm.build());
    }

    private void handleTelegramReaction(org.telegram.telegrambots.meta.api.objects.reactions.MessageReactionUpdated upd) {
        if (upd.getUser() == null) return;
        long userId = upd.getUser().getId();
        MessageRef ref = MessageRef.find(userId, upd.getMessageId());
        MaxClient client = clients.get(userId);
        if (ref == null || client == null || client.getState() != MaxClient.State.READY) return;

        String emoji = null;
        if (upd.getNewReaction() != null) {
            for (var r : upd.getNewReaction()) {
                if (r instanceof org.telegram.telegrambots.meta.api.objects.reactions.ReactionTypeEmoji e) {
                    emoji = e.getEmoji();
                    break;
                }
            }
        }
        try {
            client.setReaction(ref.getMaxChatId(), ref.getMaxMessageId(), emoji);
        } catch (MaxException | RuntimeException e) {
            log.error("Failed to send reaction to MAX for user {}", userId, e);
        }
    }

    private void handleMaxReaction(long tgUserId, long chatId, String messageId, dom4k.maxbridge.max.types.ReactionInfo info) {
        MessageRef ref = MessageRef.findByMax(tgUserId, chatId, messageId);
        if (ref == null) return;

        String emoji = null;
        if (info != null && info.getCounters() != null) {
            for (var c : info.getCounters()) {
                if (c != null && c.getCount() > 0 && c.getReaction() != null) { emoji = c.getReaction(); break; }
            }
        }

        try {
            this.execute(org.telegram.telegrambots.meta.api.methods.reactions.SetMessageReaction.builder()
                    .chatId(tgUserId)
                    .messageId((int) ref.getTelegramMessageId())
                    .reactionTypes(emoji == null ? java.util.List.of()
                            : java.util.List.of(new org.telegram.telegrambots.meta.api.objects.reactions.ReactionTypeEmoji("emoji", emoji)))
                    .build());
        } catch (TelegramApiException e) {
            log.warn("Telegram rejected reaction {} ({}), falling back to text", emoji, e.getMessage());
            if (emoji == null) return;
            try {
                this.execute(SendMessage.builder()
                        .chatId(tgUserId)
                        .replyToMessageId((int) ref.getTelegramMessageId())
                        .text("Реакция в MAX: " + emoji)
                        .build());
            } catch (TelegramApiException e2) {
                log.error("Failed to notify about reaction", e2);
            }
        }
    }

    private boolean handleReplyToMax(Message message) throws TelegramApiException {
        Message replied = message.getReplyToMessage();
        if (replied == null || message.getFrom() == null) return false;

        long userId = message.getFrom().getId();
        MessageRef ref = MessageRef.find(userId, replied.getMessageId());
        if (ref == null) return false;

        MaxClient client = clients.get(userId);
        if (client == null || client.getState() != MaxClient.State.READY) return false;

        String text = message.getText();
        if (text.startsWith("/")) return false;

        try {
            client.sendMessage(ref.getMaxChatId(), text, ref.getMaxMessageId());
        } catch (MaxException | RuntimeException e) {
            log.error("Failed to send reply to MAX for user {}", userId, e);
            this.execute(SendMessage.builder()
                    .chatId(message.getChatId())
                    .replyToMessageId(message.getMessageId())
                    .text("Не удалось отправить ответ в MAX")
                    .build());
        }
        return true;
    }

    @Override
    public String getBotToken() {
        return token;
    }

    public void setupClient(long id, MaxClient client, User user) throws TelegramApiException {
        addClient(id, client, user);

        try {
            if (user.getAuthToken() != null && !user.getAuthToken().isEmpty()) {
                client.start();
                client.loginWithToken(user.getAuthToken());
                log.debug("client #{} login success", user.getTelegramId());
            } else log.debug("client #{} login requested", user.getTelegramId());
        } catch (MaxException | RuntimeException e) {
            log.error("client #{} login failed", user.getTelegramId(), e);
            client.stop();
            this.execute(SendMessage.builder()
                    .chatId(id)
                    .text("Тебе надо авторизоваться заново, используй /login")
                    .build());
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public void addClient(long id, MaxClient client, User user) {
        clients.put(id, client);

        client.registerEventListener(MaxClient.OnMessageListener.class, (chatId, message) -> {
            String text = composeText(client, message);
            String attaches = describeAttaches(allAttaches(message));
            if (text.isEmpty() && attaches.isEmpty() && !hasPhotos(message)) return;

            try {
                String sender = client.getContactName(message.getSender());
                Chat chat = client.getChats().get(chatId);
                String chatTitle = (chat != null && chat.getTitle() != null && !chat.getTitle().isBlank()
                        && !"DIALOG".equals(chat.getType())) ? chat.getTitle() : null;

                Long thread = ensureTopic(client, id, chatId);
                int telegramMessageId;
                try {
                    telegramMessageId = deliver(client, id, chatId, message, sender, thread != null ? null : chatTitle, text, attaches, thread);
                } catch (TelegramApiException e) {
                    if (thread == null || !isThreadError(e)) throw e;
                    log.warn("Topic {} for MAX chat {} is gone, recreating", thread, chatId);
                    ChatTopic.deleteByChat(id, chatId);
                    thread = ensureTopic(client, id, chatId);
                    telegramMessageId = deliver(client, id, chatId, message, sender, thread != null ? null : chatTitle, text, attaches, thread);
                }

                MessageRef.builder()
                        .telegramUserId(id)
                        .telegramMessageId(telegramMessageId)
                        .maxChatId(chatId)
                        .maxMessageId(String.valueOf(message.getId()))
                        .build()
                        .save();
            } catch (TelegramApiException e) {
                log.error("Failed to forward message to Telegram user {}", id, e);
            }
        });

        client.registerEventListener(MaxClient.OnReactionListener.class,
                (chatId, messageId, info) -> handleMaxReaction(id, chatId, messageId, info));

        log.debug("Added MaxClient for {}", user.getTelegramId());
    }

    private RichMessage buildRichMessage(MaxClient client, dom4k.maxbridge.max.types.Message message, long chatId,
                                         String sender, String chatTitle, String text, String attaches,
                                         boolean withControls) {
        RichMessage rich = RichMessage.create();

        rich.heading("👤 " + sender + (chatTitle != null ? " · " + chatTitle : ""), 3);

        if (allAttaches(message) != null) {
            for (dom4k.maxbridge.max.types.Message.Attach a : allAttaches(message)) {
                if (a != null && "PHOTO".equals(a.getType()) && a.getBaseUrl() != null && !a.getBaseUrl().isBlank()) {
                    rich.photo(a.getBaseUrl());
                }
            }
        }

        dom4k.maxbridge.max.types.Message.Link link = message.getLink();
        if (link != null && "REPLY".equals(link.getType()) && link.getMessage() != null) {
            String quoted = link.getMessage().getText() == null ? "" : link.getMessage().getText();
            if (quoted.length() > 100) quoted = quoted.substring(0, 100) + "…";
            if (quoted.isEmpty()) quoted = describeAttaches(link.getMessage().getAttaches());
            rich.paragraph("↩ " + client.getContactName(link.getMessage().getSender()) + ": " + quoted);
        }

        if (!text.isEmpty()) rich.paragraph(text);

        String otherAttaches = describeAttaches(nonPhotoAttaches(allAttaches(message)));
        if (!otherAttaches.isEmpty()) rich.paragraph(otherAttaches);

        rich.divider();

        if (withControls) {
            String msgId = String.valueOf(message.getId());
            String senderId = String.valueOf(message.getSender());
            String date = java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
                    .format(java.time.LocalDateTime.ofInstant(
                            java.time.Instant.ofEpochMilli(message.getTime()), java.time.ZoneId.systemDefault()));
            rich.details("Управление", controls -> {
                controls.buttons(
                        RichMessage.buttonCallback("Удалить", CB_DEL + msgId),
                        RichMessage.buttonCallback("Закрепить", CB_PIN + msgId));
                controls.buttons(
                        RichMessage.buttonInline("Переслать", "forward " + chatId + " " + msgId),
                        RichMessage.buttonCallback("Прочитать", CB_READ + msgId));
                controls.details("Информация", info -> {
                    info.buttons(RichMessage.buttonCopy("Message ID: " + msgId, msgId));
                    info.buttons(RichMessage.buttonCopy("Sender ID: " + senderId, senderId));
                    info.buttons(RichMessage.buttonCopy("Date: " + date, date));
                });
            });
        }

        StringBuilder footer = new StringBuilder("Status: unread");
        String reactions = formatReactions(message.getReactionInfo());
        if (!reactions.isEmpty()) footer.append("\nReactions: ").append(reactions);
        rich.footer(footer.toString());

        return rich;
    }

    private int sendPlain(MaxClient client, long tgChatId, dom4k.maxbridge.max.types.Message message,
                          String sender, String chatTitle, String text, String attaches, Long thread) throws TelegramApiException {
        StringBuilder sb = new StringBuilder("<b>").append(escapeHtml(sender)).append("</b>");
        if (chatTitle != null) sb.append(" · ").append(escapeHtml(chatTitle));

        dom4k.maxbridge.max.types.Message.Link link = message.getLink();
        if (link != null && "REPLY".equals(link.getType()) && link.getMessage() != null) {
            String quoted = link.getMessage().getText() == null ? "" : link.getMessage().getText();
            if (quoted.length() > 100) quoted = quoted.substring(0, 100) + "…";
            String quotedAttaches = describeAttaches(link.getMessage().getAttaches());
            if (quoted.isEmpty()) quoted = quotedAttaches;
            sb.append("\n<blockquote>↩ ")
                    .append(escapeHtml(client.getContactName(link.getMessage().getSender())))
                    .append(": ").append(escapeHtml(quoted)).append("</blockquote>");
        }
        if (!text.isEmpty()) sb.append("\n").append(escapeHtml(text));
        if (!attaches.isEmpty()) sb.append("\n").append(escapeHtml(attaches));

        SendMessage.SendMessageBuilder builder = SendMessage.builder()
                .chatId(tgChatId)
                .text(sb.toString())
                .parseMode(ParseMode.HTML);
        if (thread != null) builder.messageThreadId(thread.intValue());
        Message sent = this.execute(builder.build());
        return sent.getMessageId();
    }

    private static dom4k.maxbridge.max.types.Message.Attach[] nonPhotoAttaches(dom4k.maxbridge.max.types.Message.Attach[] attaches) {
        if (attaches == null) return null;
        return java.util.Arrays.stream(attaches)
                .filter(a -> a != null && !("PHOTO".equals(a.getType()) && a.getBaseUrl() != null && !a.getBaseUrl().isBlank()))
                .toArray(dom4k.maxbridge.max.types.Message.Attach[]::new);
    }

    private static String formatReactions(dom4k.maxbridge.max.types.ReactionInfo info) {
        if (info == null || info.getCounters() == null) return "";
        StringBuilder sb = new StringBuilder();
        for (var c : info.getCounters()) {
            if (c == null || c.getCount() <= 0 || c.getReaction() == null) continue;
            if (sb.length() > 0) sb.append(" | ");
            sb.append(c.getReaction()).append(" - ").append(c.getCount());
        }
        return sb.toString();
    }

    private static String describeAttaches(dom4k.maxbridge.max.types.Message.Attach[] attaches) {
        if (attaches == null || attaches.length == 0) return "";
        StringBuilder sb = new StringBuilder();
        for (dom4k.maxbridge.max.types.Message.Attach a : attaches) {
            if (a == null || a.getType() == null) continue;
            if (sb.length() > 0) sb.append("\n");
            switch (a.getType()) {
                case "PHOTO" -> sb.append("📷 Фото");
                case "VIDEO" -> sb.append("🎬 Видео");
                case "FILE" -> sb.append("📎 ").append(a.getName() == null ? "Файл" : a.getName())
                        .append(a.getSize() > 0 ? " (" + formatSize(a.getSize()) + ")" : "");
                case "AUDIO" -> sb.append("🎤 Голосовое").append(a.getDuration() > 0 ? " (" + (a.getDuration() / 1000) + " с)" : "");
                case "STICKER" -> sb.append("🙂 Стикер");
                case "CALL" -> sb.append("📞 Звонок").append(a.getHangupType() != null ? " (" + a.getHangupType() + ")" : "");
                default -> sb.append("[").append(a.getType()).append("]");
            }
        }
        return sb.toString();
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " Б";
        if (bytes < 1024 * 1024) return (bytes / 1024) + " КБ";
        return String.format("%.1f МБ", bytes / 1024.0 / 1024.0);
    }

    private static String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    public MaxClient getClientById(long id) {
        return clients.get(id);
    }
}
