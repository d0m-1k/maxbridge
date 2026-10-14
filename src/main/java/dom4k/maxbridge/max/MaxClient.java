package dom4k.maxbridge.max;

import dom4k.maxbridge.max.ProtoObject.*;
import dom4k.maxbridge.max.packets.*;
import dom4k.maxbridge.max.pushes.*;
import dom4k.maxbridge.max.types.Chat;
import dom4k.maxbridge.max.types.Message;
import dom4k.maxbridge.max.types.Profile;
import dom4k.maxbridge.max.types.ReactionInfo;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.msgpack.core.MessageBufferPacker;
import org.msgpack.core.MessagePack;
import org.msgpack.core.MessageUnpacker;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Consumer;

@Slf4j
public final class MaxClient {
    private final String host;
    private final int port;
    private final UUID deviceId;
    private final ThreadGroup threadGroup = new ThreadGroup("MAX Client");

    private final ConcurrentMap<Integer, CompletableFuture<ResponseObject>> receiveFutures = new ConcurrentHashMap<>();
    private final ConcurrentMap<Class<? extends EventListener>, CopyOnWriteArrayList<EventListener>> listeners = new ConcurrentHashMap<>();

    private volatile Socket socket;
    private int seq = 0;
    private volatile OutputStream os;
    @Getter private volatile State state = State.DISCONNECTED;

    private String verificationToken;
    @Getter private String authToken;

    private String passwordTrackID;
    @Getter private String passwordHint;
    @Getter private String passwordEmail;
    @Getter private Profile profile;

    private final ConcurrentMap<Long, String> contactNames = new ConcurrentHashMap<>();
    @Getter private ConcurrentMap<Long, Chat> chats = new ConcurrentHashMap<>();

    public MaxClient(UUID deviceId, String host, int port) {
        this.host = host;
        this.port = port;
        this.deviceId = deviceId;
        this.socket = new Socket();
    }

    public <T extends EventListener> void registerEventListener(Class<T> type, T listener) {
        listeners.computeIfAbsent(type, k -> new CopyOnWriteArrayList<>()).add(listener);
    }

    public <T extends EventListener> void unregisterEventListener(Class<T> type, T listener) {
        listeners.computeIfPresent(type, (k, list) -> {
            list.remove(listener);
            return list.isEmpty() ? null : list;
        });
    }

    @SuppressWarnings("unchecked")
    public <T extends EventListener> List<T> listenersOf(Class<T> type) {
        CopyOnWriteArrayList<T> ls = (CopyOnWriteArrayList<T>) listeners.get(type);
        return (ls == null) ? List.of() : ls;
    }

    public <T extends EventListener> void fire(Class<T> type, Consumer<T> action) {
        for (T l : listenersOf(type)) {
            try { action.accept(l); }
            catch (Throwable t) { log.error("listener run failed", t); }
        }
    }

    public synchronized void start() throws IOException, NoSuchAlgorithmException {
        if (state != State.DISCONNECTED) return;

        SSLSocketFactory socketFactory = SSLContext.getDefault().getSocketFactory();
        Socket newSocket = socketFactory.createSocket(host, port);
        InputStream in = newSocket.getInputStream();
        this.socket = newSocket;
        this.os = newSocket.getOutputStream();
        new Thread(threadGroup, () -> receiver(newSocket, in), "MAX receiver").start();
        state = State.WAIT_LOGIN;
    }

    public void stop() {
        Socket current = this.socket;
        state = State.DISCONNECTED;
        if (current != null) {
            try {
                current.close();
            } catch (IOException ignored) {}
        }
        failPending(new IOException("Client stopped"));
    }

    public void sendMessage(long chatId, String text) throws MaxException {
        sendMessage(chatId, text, null);
    }

    public void sendMessage(long chatId, String text, String replyToMessageId) throws MaxException {
        assert state == State.READY : "Require state READY";
        SendMessageRequest sendMessageRequest = new SendMessageRequest();
        sendMessageRequest.setChatId(chatId);
        sendMessageRequest.getMsg().setText(text);
        if (replyToMessageId != null) {
            Message.Link link = new Message.Link();
            link.setType("REPLY");
            link.setMessageId(Long.parseLong(replyToMessageId));
            sendMessageRequest.getMsg().setLink(link);
        }
        SendMessageResponse sendMessageResponse = invoke(sendMessageRequest, SendMessageResponse.class);
        sendMessageResponse.throwIsError();
    }

    public void markRead(long chatId, long messageId) throws MaxException {
        assert state == State.READY : "Require state READY";
        invoke(new ChatReadRequest("READ_MESSAGE", chatId, messageId, System.currentTimeMillis()),
                ChatReadResponse.class).throwIsError();
    }

    public void deleteMessage(long chatId, long messageId) throws MaxException {
        assert state == State.READY : "Require state READY";
        invoke(new MsgDeleteRequest(chatId, new long[]{messageId}, true), MsgDeleteResponse.class).throwIsError();
    }

    public void forwardMessage(long srcChatId, long messageId, long dstChatId) throws MaxException {
        assert state == State.READY : "Require state READY";
        SendMessageRequest request = new SendMessageRequest();
        request.setChatId(dstChatId);
        request.getMsg().setText("");
        request.getMsg().setCid(-System.currentTimeMillis());
        Message.Link link = new Message.Link();
        link.setType("FORWARD");
        link.setChatId(srcChatId);
        link.setMessageId(messageId);
        request.getMsg().setLink(link);
        invoke(request, SendMessageResponse.class).throwIsError();
    }

    public void setReaction(long chatId, String messageId, String emoji) throws MaxException {
        assert state == State.READY : "Require state READY";
        if (emoji == null || emoji.isEmpty()) {
            invoke(new CancelReactionRequest(chatId, messageId), CancelReactionResponse.class).throwIsError();
        } else {
            invoke(new AddReactionRequest(chatId, messageId, new dom4k.maxbridge.max.types.Reaction("EMOJI", emoji)),
                    AddReactionResponse.class).throwIsError();
        }
    }

    public String getContactName(long userId) {
        if (profile != null && profile.getContact() != null && profile.getContact().getId() == userId) return "Вы";
        String cached = contactNames.get(userId);
        if (cached != null) return cached;

        String name = null;
        try {
            GetContactsResponse response = invoke(new GetContactsRequest(new long[]{userId}), GetContactsResponse.class);
            response.throwIsError();
            if (response.getContacts() != null) {
                for (Profile.Contact c : response.getContacts()) {
                    if (c == null || c.getId() != userId || c.getNames() == null) continue;
                    for (Profile.Contact.Name n : c.getNames()) {
                        if (n == null) continue;
                        String full = n.getName();
                        if (full == null || full.isBlank()) {
                            full = ((n.getFirstName() == null ? "" : n.getFirstName()) + " "
                                    + (n.getLastName() == null ? "" : n.getLastName())).trim();
                        }
                        if (!full.isBlank()) { name = full; break; }
                    }
                    if (name != null) break;
                }
            }
        } catch (Exception e) {
            log.warn("Failed to resolve contact {}", userId, e);
        }
        if (name == null) return "ID " + userId;
        contactNames.put(userId, name);
        return name;
    }

    public void loginWithToken(String authToken) throws MaxException {
        assert state == State.WAIT_LOGIN : "Require state WAIT LOGIN";
        this.authToken = authToken;

        InitRequest initRequest = new InitRequest();
        initRequest.getUserAgent().setOsVersion("Linux");
        initRequest.getUserAgent().setDeviceName("Max Bridge");
        initRequest.getUserAgent().setTimezone(TimeZone.getTimeZone("Asia/Yekaterinburg"));
        initRequest.setDeviceId(deviceId);
        InitResponse initResponse = invoke(initRequest, InitResponse.class);
        initResponse.throwIsError();

        login();

        state = State.READY;
    }

    private void login() throws MaxException {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setToken(authToken);
        LoginResponse loginResponse = invoke(loginRequest, LoginResponse.class);
        loginResponse.throwIsError();

        if (loginResponse.getProfile() != null) profile = loginResponse.getProfile();
        chats.clear();
        processChats(loginResponse.getChats());
    }

    public void sendCode(String phone) throws MaxException {
        assert state == State.WAIT_LOGIN : "Require state WAIT LOGIN";
        InitRequest initRequest = new InitRequest();
        initRequest.getUserAgent().setOsVersion("Linux");
        initRequest.getUserAgent().setDeviceName("Max Bridge");
        initRequest.getUserAgent().setTimezone(TimeZone.getTimeZone("Asia/Yekaterinburg"));
        initRequest.setDeviceId(deviceId);
        InitResponse initResponse = invoke(initRequest, InitResponse.class);
        initResponse.throwIsError();

        VerificationRequest verificationRequest = new VerificationRequest();
        verificationRequest.setPhone(phone);
        VerificationResponse verificationResponse = invoke(verificationRequest, VerificationResponse.class);
        verificationResponse.throwIsError();

        verificationToken = verificationResponse.getToken();
        state = State.WAIT_SMS;
    }

    public void enterCode(String code) throws MaxException {
        assert state == State.WAIT_SMS : "Require state WAIT SMS";
        CodeEnterRequest codeEnterRequest = new CodeEnterRequest();
        codeEnterRequest.setToken(verificationToken);
        codeEnterRequest.setVerifyCode(code);
        CodeEnterResponse codeEnterResponse = invoke(codeEnterRequest, CodeEnterResponse.class);
        codeEnterResponse.throwIsError();

        authToken = extractLoginToken(codeEnterResponse);

        if (authToken == null) {
            CodeEnterResponse.PasswordChallenge challenge = codeEnterResponse.getPasswordChallenge();
            if (challenge == null || challenge.getTrackId() == null)
                throw new MaxException("Server returned neither a token nor a password challenge");
            passwordTrackID = challenge.getTrackId();
            passwordHint = challenge.getHint();
            passwordEmail = challenge.getEmail();
            state = State.WAIT_PASSWORD;
        } else {
            if (codeEnterResponse.getProfile() != null) profile = codeEnterResponse.getProfile();
            login();
            state = State.READY;
        }
    }

    public void enterPassword(String password) throws MaxException {
        assert state == State.WAIT_PASSWORD : "Require state WAIT PASSWORD";
        PasswordEnterRequest passwordEnterRequest = new PasswordEnterRequest(passwordTrackID, password);
        PasswordEnterResponse passwordEnterResponse = invoke(passwordEnterRequest, PasswordEnterResponse.class);
        passwordEnterResponse.throwIsError();

        String token = extractLoginToken(passwordEnterResponse);
        if (token == null) throw new MaxException("Server did not return a token");
        authToken = token;
        if (passwordEnterResponse.getProfile() != null) profile = passwordEnterResponse.getProfile();
        login();

        state = State.READY;
    }

    private void processChats(Chat[] chats) {
        if (chats == null) return;
        for (Chat chat : chats) {
            if (chat == null || chat.getId() == 0) continue;
            this.chats.put(chat.getId(), chat);
        }
    }

    private static String extractLoginToken(CodeEnterResponse response) {
        CodeEnterResponse.TokenAttrs attrs = response.getTokenAttrs();
        if (attrs == null || attrs.getLogin() == null) return null;
        return attrs.getLogin().getToken();
    }

    private void objectHandler(Frame frame, ProtoObject object) throws MaxException {
        object.throwIsError();
        if (object instanceof Ping) {
            invoke(new Pong(), Ping.class);
        } else if (object instanceof NotifTyping || object instanceof Ignored.CallHistory || object instanceof Ignored.Transcription) {
            log.debug("Ignored push {}", object.getClass().getSimpleName());
        } else if (object instanceof NotifRead o) {
            fire(OnMessageReadListener.class, l -> {
                l.onMessageRead(o.getChatId(), o.getUserId());
            });
        } else if (object instanceof NotifReaction o) {
            fire(OnReactionListener.class, l -> {
                l.onReaction(o.getChatId(), o.getMessageId(), o.getReactionInfo());
            });
        } else if (object instanceof NotifMessageEdit o) {
            if (o.getMsg() != null && o.getMsg().getId() != 0) {
                try {
                    send(new NotifMessageAck(o.getChatId(), o.getMsg().getId()), frame.getSeq(), 1);
                } catch (RuntimeException e) {
                    log.warn("Failed to ack message {}", o.getMsg().getId(), e);
                }
            }
            fire(OnMessageListener.class, l -> {
                l.onMessage(o.getChatId(), o.getMsg());
            });
        } else {
            log.warn("Orphan object received: {}", object);
        }
    }

    @SuppressWarnings("unchecked")
    public synchronized <R extends ResponseObject> R invoke(RequestObject object, Class<R> clazz) {
        ResponseObject response = invoke(object);
        if (clazz.isInstance(response)) return (R) response;
        throw new IllegalStateException("Failed to receive " + clazz.getSimpleName() + ", got " + response.getClass().getSimpleName());
    }

    private synchronized ResponseObject invoke(RequestObject object) {
        if (os == null || state == State.DISCONNECTED) throw new IllegalStateException("Client is not connected");

        int seq = getSeq();
        CompletableFuture<ResponseObject> future = new CompletableFuture<>();
        receiveFutures.put(seq, future);

        try {
            send(object, seq);
            return future.get(5000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException | TimeoutException e) {
            log.error("Failed to receive result", e);
            throw new RuntimeException(e);
        } finally {
            receiveFutures.remove(seq);
        }
    }

    private synchronized void send(ProtoObject object) { send(object, getSeq()); }
    private synchronized void send(ProtoObject object, int seq) { send(object, seq, 0); }
    private synchronized void send(ProtoObject object, int seq, int cmd) {
        try (MessageBufferPacker packer = MessagePack.newDefaultBufferPacker()) {
            object.pack(packer);

            Frame frame = new Frame();
            frame.setSeq(seq);
            frame.setCmd(cmd);
            if (object instanceof RequestObject requestObject) {
                frame.setOpcode(RequestObject.getOpcodeByClass(requestObject.getClass()));
            } else if (object instanceof ResponseObject responseObject) {
                frame.setOpcode(ResponseObject.getOpcodeByClass(responseObject.getClass()));
            } else {
                log.error("Invalid object class: {}. Need: RequestObject or ResponseObject", object.getClass().getSimpleName());
                throw new RuntimeException();
            }
            frame.setData(packer.toByteArray());
            frame.write(os);
            log.debug("Sent {} (seq={})", object, seq);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void receiver(Socket mySocket, InputStream in) {
        Frame frame = new Frame();
        try {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    frame.read(in);
                } catch (IOException e) {
                    if (socket == mySocket && state != State.DISCONNECTED) log.error("Connection lost", e);
                    break;
                }

                int frameSeq = frame.getSeq();
                int cmd = frame.getCmd();
                try {
                    handleFrame(frame);
                } catch (ClassNotFoundException e) {
                    log.warn("Failed to receive object: {}", e.getMessage());
                } catch (Exception e) {
                    log.error("Failed to process frame (opcode={}, seq={})", frame.getOpcode(), frameSeq, e);
                    if (cmd != 0) {
                        CompletableFuture<ResponseObject> future = receiveFutures.get(frameSeq);
                        if (future != null) future.completeExceptionally(e);
                    }
                }
            }
        } finally {
            if (socket == mySocket) {
                state = State.DISCONNECTED;
                failPending(new IOException("Connection closed"));
            }
            try {
                mySocket.close();
            } catch (IOException ignored) {}
        }
    }

    private void handleFrame(Frame frame) throws Exception {
        Class<? extends ProtoObject> clazz;
        if (frame.getCmd() == 0) clazz = ProtoObject.getPushClassByOpcode(frame.getOpcode());
        else clazz = ProtoObject.getClassByOpcode(frame.getOpcode());

        ProtoObject instance = clazz.getConstructor().newInstance();
        if (frame.getData().length != 0) {
            MessageUnpacker unpacker = MessagePack.newDefaultUnpacker(frame.getData());
            if (frame.getCmd() == 3) {
                ResponseError err = new ResponseError();
                err.unpack(unpacker);
                instance.setResponseError(err);
            } else instance.unpack(unpacker);
        }
        log.debug("Received {} (seq={})", instance, frame.getSeq());

        if (frame.getCmd() != 0) {
            CompletableFuture<ResponseObject> future = receiveFutures.get(frame.getSeq());
            if (instance instanceof ResponseObject responseObject && future != null) {
                future.complete(responseObject);
                return;
            }
        }
        runHandler(frame, instance);
    }

    private void runHandler(Frame frame, ProtoObject instance) {
        new Thread(threadGroup, () -> {
            try {
                objectHandler(frame, instance);
            } catch (MaxException e) {
                log.error("Failed to handle {}", instance.getClass().getSimpleName(), e);
            } catch (RuntimeException e) {
                log.error("Failed to handle {}", instance.getClass().getSimpleName(), e);
            }
        }).start();
    }

    private void failPending(Throwable cause) {
        for (CompletableFuture<ResponseObject> future : receiveFutures.values()) future.completeExceptionally(cause);
        receiveFutures.clear();
    }

    private synchronized int getSeq() { return seq++ & 0xFFFF; }

    public interface EventListener {};

    @FunctionalInterface public interface OnMessageListener extends EventListener {
        void onMessage(long chatId, Message message);
    }

    @FunctionalInterface public interface OnMessageReadListener extends EventListener {
        void onMessageRead(long chatId, long userId);
    }

    @FunctionalInterface public interface OnReactionListener extends EventListener {
        void onReaction(long chatId, String messageId, ReactionInfo reactionInfo);
    }

    public enum State {
        DISCONNECTED,
        WAIT_LOGIN,
        WAIT_SMS,
        WAIT_PASSWORD,
        READY,
    }
}
