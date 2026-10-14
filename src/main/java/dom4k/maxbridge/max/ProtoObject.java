package dom4k.maxbridge.max;

import dom4k.maxbridge.max.packets.*;
import dom4k.maxbridge.max.pushes.*;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.msgpack.core.MessageFormat;
import org.msgpack.core.MessagePacker;
import org.msgpack.core.MessageUnpacker;
import org.msgpack.core.MessagePack;
import org.msgpack.value.ExtensionValue;
import org.msgpack.value.Value;
import org.msgpack.value.ValueType;

import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
public class ProtoObject {
    private static final Map<Class<?>, Map<String, Field>> FIELD_CACHE = new ConcurrentHashMap<>();
    private static final Map<Integer, Class<? extends ProtoObject>> REQUESTS = new ConcurrentHashMap<>();
    private static final Map<Integer, Class<? extends ProtoObject>> RESPONSES = new ConcurrentHashMap<>();

    @Getter @Setter private ResponseError responseError = null;
    public void throwIsError() throws MaxException {
        ResponseError error = getResponseError();
        if (error != null) {
            log.error("Received error:");
            log.error("    Error: {}", error.getError());
            log.error("    Message: {}", error.getMessage());
            log.error("    Localized Message: {}", error.getLocalizedMessage());
            log.error("    Title: {}", error.getTitle());
            log.error("    Description: {}", error.getDescription());
            throw new MaxException(error.getError());
        }
    }

    static {
        registerClass(AddReactionRequest.class);
        registerClass(AddReactionResponse.class);
        registerClass(CancelReactionRequest.class);
        registerClass(CancelReactionResponse.class);
        registerClass(CaptchaRequest.class);
        registerClass(CaptchaResponse.class);
        registerClass(CodeEnterRequest.class);
        registerClass(CodeEnterResponse.class);
        registerClass(ChatReadRequest.class);
        registerClass(ChatReadResponse.class);
        registerClass(MsgDeleteRequest.class);
        registerClass(MsgDeleteResponse.class);
        registerClass(GetChatsBulkRequest.class);
        registerClass(GetChatsBulkResponse.class);
        registerClass(GetHistoryRequest.class);
        registerClass(GetHistoryResponse.class);
        registerClass(GetMessageRequest.class);
        registerClass(GetMessageResponse.class);
        registerClass(GetContactsRequest.class);
        registerClass(GetContactsResponse.class);
        registerClass(GetFoldersRequest.class);
        registerClass(GetFoldersResponse.class);
        registerClass(GetPresenceRequest.class);
        registerClass(GetPresenceResponse.class);
        registerClass(GetChatsRequest.class);
        registerClass(GetChatsResponse.class);
        registerClass(InitRequest.class);
        registerClass(InitResponse.class);
        registerClass(LoginRequest.class);
        registerClass(LoginResponse.class);
        registerClass(PasswordEnterRequest.class);
        registerClass(PasswordEnterResponse.class);
        registerClass(SendMessageRequest.class);
        registerClass(SendMessageResponse.class);
        registerClass(VerificationRequest.class);
        registerClass(VerificationResponse.class);

        registerClass(NotifMessageAck.class);
        registerClass(Ignored.CallHistory.class);
        registerClass(Ignored.Transcription.class);
        registerClass(NotifMessageEdit.class);
        registerClass(NotifReaction.class);
        registerClass(NotifTyping.class);
        registerClass(NotifPresence.class);
        registerClass(NotifRead.class);
        registerClass(Ping.class);
        registerClass(Pong.class);
    }

    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Register {
        int opcode();
    }

    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Serializable {
        String name() default "";
    }

    public abstract static class RequestObject extends ProtoObject {}
    public abstract static class ResponseObject extends ProtoObject {}

    public static final class ResponseError extends ProtoObject {
        @Getter @Serializable private String error;
        @Getter @Serializable private String message;
        @Getter @Serializable private String localizedMessage;
        @Getter @Serializable private String title;
        @Getter @Serializable private String description;
    }

    public static void registerClass(Class<? extends ProtoObject> clazz) {
        Register annotation = clazz.getAnnotation(Register.class);
        if (annotation != null) {
            Class<? extends ProtoObject> previous = registryFor(clazz).putIfAbsent(annotation.opcode(), clazz);
            if (previous != null && previous != clazz) {
                throw new IllegalStateException("Opcode %d is already registered for %s (tried to register %s)"
                        .formatted(annotation.opcode(), previous.getName(), clazz.getName()));
            }
        }

        for (Class<?> nested : clazz.getDeclaredClasses())
            if (ProtoObject.class.isAssignableFrom(nested)) registerClass(nested.asSubclass(ProtoObject.class));
    }

    public static int getOpcodeByClass(Class<? extends ProtoObject> clazz) {
        Register annotation = clazz.getAnnotation(Register.class);
        if (annotation == null) throw new IllegalArgumentException(clazz.getName() + " is not annotated with @Register");
        return annotation.opcode();
    }

    public static Class<? extends ProtoObject> getClassByOpcode(int opcode) throws ClassNotFoundException {
        Class<? extends ProtoObject> clazz = RESPONSES.get(opcode);
        if (clazz == null) clazz = REQUESTS.get(opcode);
        if (clazz == null) throw new ClassNotFoundException("Unknown opcode: " + opcode);
        return clazz;
    }

    public static Class<? extends ProtoObject> getPushClassByOpcode(int opcode) throws ClassNotFoundException {
        Class<? extends ProtoObject> clazz = RESPONSES.get(opcode);
        if (clazz == null) throw new ClassNotFoundException("Unknown push opcode: " + opcode);
        return clazz;
    }

    private static Map<Integer, Class<? extends ProtoObject>> registryFor(Class<? extends ProtoObject> clazz) {
        if (RequestObject.class.isAssignableFrom(clazz)) return REQUESTS;
        if (ResponseObject.class.isAssignableFrom(clazz)) return RESPONSES;
        throw new IllegalArgumentException(clazz.getName() + " must extend RequestObject or ResponseObject");
    }

    public void pack(MessagePacker packer) throws IOException {
        Map<String, Field> fields = fieldsOf(getClass());
        packer.packMapHeader(fields.size());

        for (Map.Entry<String, Field> entry : fields.entrySet()) {
            packer.packString(entry.getKey());
            packValue(packer, readField(entry.getValue()));
        }
    }

    private static void packValue(MessagePacker packer, Object value) throws IOException {
        if (value == null) packer.packNil();
        else if (value instanceof Integer number) packer.packInt(number);
        else if (value instanceof Long number) packer.packLong(number);
        else if (value instanceof Boolean flag) packer.packBoolean(flag);
        else if (value instanceof ProtoObject object) object.pack(packer);
        else if (value instanceof Map<?, ?> map) {
            packer.packMapHeader(map.size());
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                packValue(packer, entry.getKey());
                packValue(packer, entry.getValue());
            }
        }
        else if (isWireString(value)) packer.packString(toWireString(value));
        else if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            packer.packArrayHeader(length);
            for (int i = 0; i < length; i++) packValue(packer, Array.get(value, i));
        } else throw new IllegalArgumentException("Unsupported field type: " + value.getClass().getName());
    }

    public void unpack(MessageUnpacker unpacker) throws IOException {
        Map<String, Field> fields = fieldsOf(getClass());
        int mapSize = unpacker.unpackMapHeader();

        for (int i = 0; i < mapSize; i++) {
            String key = unpacker.unpackString();
            Field field = fields.get(key);

            if (field == null) {
                log.warn("Skipped value '{}' with type '{}' ({})", key, unpacker.getNextFormat(), getClass().getSimpleName());
                unpacker.skipValue();
                continue;
            }

            try {
                Object value = unpackValue(unpacker, field.getGenericType());
                if (value != null || !field.getType().isPrimitive()) writeField(field, value);
            } catch (ReflectiveOperationException | RuntimeException e) {
                throw new IllegalStateException("Failed to unpack field: " + field.getName(), e);
            }
        }
    }

    private static Object unpackValue(MessageUnpacker unpacker, Type type) throws IOException, ReflectiveOperationException {
        if (unpacker.getNextFormat() == MessageFormat.NIL) {
            unpacker.unpackNil();
            return null;
        }

        Class<?> raw = rawClass(type);
        ValueType wireType = unpacker.getNextFormat().getValueType();
        boolean stringOnWire = wireType == ValueType.STRING;

        if (raw == Object.class) return toJava(unpacker.unpackValue());

        if (wireType == ValueType.EXTENSION
                && (raw == int.class || raw == Integer.class || raw == long.class || raw == Long.class)) {
            Long number = extensionToLong(unpacker.unpackValue().asExtensionValue());
            if (number == null) return null;
            if (raw == int.class || raw == Integer.class) return Integer.valueOf((int) (long) number);
            return number;
        }
        if (raw == byte[].class && wireType == ValueType.BINARY)
            return unpacker.unpackValue().asBinaryValue().asByteArray();

        if (!isCompatible(wireType, raw)) {
            log.warn("Skipped value with wire type '{}': expected {}", wireType, type.getTypeName());
            unpacker.skipValue();
            return null;
        }

        if (raw == int.class || raw == Integer.class)
            return stringOnWire ? Integer.parseInt(unpacker.unpackString()) : (int) unpacker.unpackLong();
        if (raw == long.class || raw == Long.class)
            return stringOnWire ? Long.parseLong(unpacker.unpackString()) : unpacker.unpackLong();
        if (raw == boolean.class || raw == Boolean.class) return unpacker.unpackBoolean();
        if (raw == String.class) return unpacker.unpackString();
        if (raw == UUID.class) return UUID.fromString(unpacker.unpackString());
        if (raw == Locale.class) return Locale.forLanguageTag(unpacker.unpackString());
        if (raw == TimeZone.class) return TimeZone.getTimeZone(unpacker.unpackString());

        if (raw.isArray()) {
            Type componentType = componentTypeOf(type);
            int length = unpacker.unpackArrayHeader();
            Class<?> componentClass = rawClass(componentType);
            Object array = Array.newInstance(componentClass, length);
            for (int i = 0; i < length; i++) {
                Object element = unpackValue(unpacker, componentType);
                if (element == null && componentClass.isPrimitive()) continue;
                Array.set(array, i, element);
            }
            return array;
        }

        if (Map.class.isAssignableFrom(raw)) {
            if (!(type instanceof ParameterizedType parameterized))
                throw new IllegalArgumentException("Map field must be parameterized, e.g. Map<Long, Long>: " + type.getTypeName());

            Type keyType = parameterized.getActualTypeArguments()[0];
            Type valueType = parameterized.getActualTypeArguments()[1];

            @SuppressWarnings("unchecked")
            Map<Object, Object> map = (Map<Object, Object>) createMap(raw);

            int size = unpacker.unpackMapHeader();
            for (int i = 0; i < size; i++) {
                Object key = unpackValue(unpacker, keyType);
                Object value = unpackValue(unpacker, valueType);

                if (key == null || (value == null && map instanceof ConcurrentMap)) continue;
                map.put(key, value);
            }
            return map;
        }

        if (ProtoObject.class.isAssignableFrom(raw)) {
            Constructor<?> constructor = raw.getDeclaredConstructor();
            constructor.setAccessible(true);
            ProtoObject nested = (ProtoObject) constructor.newInstance();
            nested.unpack(unpacker);
            return nested;
        }

        throw new IllegalArgumentException("Unsupported field type for unpacking: " + type.getTypeName());
    }

    private static boolean isCompatible(ValueType wire, Class<?> raw) {
        if (raw == int.class || raw == Integer.class || raw == long.class || raw == Long.class)
            return wire == ValueType.INTEGER || wire == ValueType.STRING || wire == ValueType.EXTENSION;
        if (raw == boolean.class || raw == Boolean.class) return wire == ValueType.BOOLEAN;
        if (raw == String.class || raw == UUID.class || raw == Locale.class || raw == TimeZone.class)
            return wire == ValueType.STRING;
        if (raw == byte[].class) return wire == ValueType.BINARY;
        if (raw.isArray()) return wire == ValueType.ARRAY;
        if (Map.class.isAssignableFrom(raw) || ProtoObject.class.isAssignableFrom(raw)) return wire == ValueType.MAP;
        return true;
    }

    private static Long extensionToLong(ExtensionValue ext) {
        if (ext.getType() != 1) return null;
        try (MessageUnpacker inner = MessagePack.newDefaultUnpacker(ext.getData())) {
            Value value = inner.unpackValue();
            if (value.isIntegerValue() && value.asIntegerValue().isInLongRange())
                return value.asIntegerValue().toLong();
        } catch (Exception e) {
            log.warn("Failed to decode extension value: {}", e.getMessage());
        }
        return null;
    }

    private static Object toJava(Value value) {
        switch (value.getValueType()) {
            case NIL: return null;
            case BOOLEAN: return value.asBooleanValue().getBoolean();
            case INTEGER:
                return value.asIntegerValue().isInLongRange()
                        ? (Object) value.asIntegerValue().toLong()
                        : value.asIntegerValue().toBigInteger();
            case FLOAT: return value.asFloatValue().toDouble();
            case STRING: return value.asStringValue().asString();
            case BINARY: return value.asBinaryValue().asByteArray();
            case EXTENSION: {
                ExtensionValue ext = value.asExtensionValue();
                Long number = extensionToLong(ext);
                return number != null ? (Object) number : ext.getData();
            }
            case ARRAY: {
                List<Object> list = new ArrayList<>();
                for (Value element : value.asArrayValue()) list.add(toJava(element));
                return list;
            }
            case MAP: {
                Map<Object, Object> map = new LinkedHashMap<>();
                for (Map.Entry<Value, Value> entry : value.asMapValue().entrySet())
                    map.put(toJava(entry.getKey()), toJava(entry.getValue()));
                return map;
            }
            default: return value.toJson();
        }
    }

    private static Object createMap(Class<?> raw) throws ReflectiveOperationException {
        if (raw.isInterface() || Modifier.isAbstract(raw.getModifiers()))
            return ConcurrentMap.class.isAssignableFrom(raw) ? new ConcurrentHashMap<>() : new LinkedHashMap<>();
        return raw.getDeclaredConstructor().newInstance();
    }

    private static Class<?> rawClass(Type type) {
        if (type instanceof Class<?> clazz) return clazz;
        if (type instanceof ParameterizedType parameterized) return (Class<?>) parameterized.getRawType();
        if (type instanceof GenericArrayType array)
            return Array.newInstance(rawClass(array.getGenericComponentType()), 0).getClass();
        throw new IllegalArgumentException("Unsupported type: " + type.getTypeName());
    }

    private static Type componentTypeOf(Type arrayType) {
        if (arrayType instanceof GenericArrayType array) return array.getGenericComponentType();
        return ((Class<?>) arrayType).getComponentType();
    }

    private static Map<String, Field> fieldsOf(Class<?> clazz) {
        return FIELD_CACHE.computeIfAbsent(clazz, ProtoObject::inspectFields);
    }

    private static Map<String, Field> inspectFields(Class<?> clazz) {
        List<Class<?>> hierarchy = new ArrayList<>();
        for (Class<?> c = clazz; c != null && c != ProtoObject.class && c != Object.class; c = c.getSuperclass())
            hierarchy.add(0, c);

        Map<String, Field> fields = new LinkedHashMap<>();
        for (Class<?> c : hierarchy) {
            for (Field field : c.getDeclaredFields()) {
                Serializable annotation = field.getAnnotation(Serializable.class);
                if (annotation == null) continue;

                String name = annotation.name().isEmpty() ? field.getName() : annotation.name();
                if (fields.putIfAbsent(name, field) != null)
                    throw new IllegalStateException("Duplicate serializable field name '%s' in %s".formatted(name, clazz.getName()));
                field.setAccessible(true);
            }
        }
        return Collections.unmodifiableMap(fields);
    }

    private Object readField(Field field) {
        try {
            return field.get(this);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Failed to read field: " + field.getName(), e);
        }
    }

    private void writeField(Field field, Object value) {
        try {
            field.set(this, value);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Failed to write field: " + field.getName(), e);
        }
    }

    private static boolean isWireString(Object value) {
        return value instanceof String || value instanceof UUID || value instanceof Locale || value instanceof TimeZone;
    }

    private static String toWireString(Object value) {
        if (value instanceof Locale locale) return locale.getCountry().toLowerCase(Locale.ROOT);
        if (value instanceof TimeZone timeZone) return timeZone.toZoneId().getId();
        return value.toString();
    }

    private static final Set<String> SECRET_FIELDS = Set.of("token", "password", "verifyCode", "trackId");

    private static final int LOG_MAX_STRING = 40;
    private static final int LOG_MAX_ITEMS = 3;
    private static final int LOG_MAX_DEPTH = 3;
    private static final int LOG_MAX_TOTAL = 300;

    @Override
    public String toString() {
        String text = toString(0);
        if (text.length() <= LOG_MAX_TOTAL) return text;
        return text.substring(0, LOG_MAX_TOTAL) + "…(+" + (text.length() - LOG_MAX_TOTAL) + " chars)>";
    }

    public String toString(int depth) {
        if (depth >= LOG_MAX_DEPTH) return getClass().getSimpleName() + "<…>";
        StringJoiner joiner = new StringJoiner(" ", getClass().getSimpleName() + "<", ">");
        fieldsOf(getClass()).forEach((name, field) -> {
            Object value = readField(field);
            if (value == null) return;
            joiner.add(name + "=" + (SECRET_FIELDS.contains(name) ? "***" : formatValue(value, depth)));
        });
        return joiner.toString();
    }

    private static String formatValue(Object value, int depth) {
        if (value == null) return "null";
        if (value instanceof ProtoObject obj) return obj.toString(depth + 1);
        if (value instanceof byte[] bytes) return "bytes[" + bytes.length + "]";

        if (value instanceof Map<?, ?> map) {
            StringJoiner joiner = new StringJoiner(", ", "{", "}");
            int shown = 0;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (shown++ == LOG_MAX_ITEMS) {
                    joiner.add("…(" + map.size() + " items)");
                    break;
                }
                joiner.add(formatValue(entry.getKey(), depth) + ": " + formatValue(entry.getValue(), depth));
            }
            return joiner.toString();
        }

        if (value instanceof Collection<?> collection) {
            return formatValue(collection.toArray(), depth);
        }

        if (value.getClass().isArray()) {
            StringJoiner joiner = new StringJoiner(", ", "[", "]");
            int length = Array.getLength(value);
            for (int i = 0; i < Math.min(length, LOG_MAX_ITEMS); i++) joiner.add(formatValue(Array.get(value, i), depth));
            if (length > LOG_MAX_ITEMS) joiner.add("…(" + length + " items)");
            return joiner.toString();
        }

        String text = toWireString(value);
        if (text.length() > LOG_MAX_STRING) text = text.substring(0, LOG_MAX_STRING) + "…(+" + (text.length() - LOG_MAX_STRING) + ")";
        return needsQuotes(text) ? quote(text) : text;
    }

    private static boolean needsQuotes(String text) {
        return text.isEmpty() || text.chars().anyMatch(c -> Character.isWhitespace(c) || "\"'*=\\".indexOf(c) >= 0);
    }

    private static String quote(String text) {
        return '"' + text.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }
}
