package dom4k.maxbridge.max.packets;

import dom4k.maxbridge.max.ProtoObject;
import dom4k.maxbridge.max.ProtoObject.*;
import lombok.*;

import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@Register(opcode = 6)
public class InitRequest extends RequestObject {
    @Serializable private UserAgent userAgent = new UserAgent();
    @Serializable private UUID deviceId = UUID.randomUUID();

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class UserAgent extends ProtoObject {
        @Serializable private String deviceType = DeviceType.WEB;
        @Serializable private Locale locale = Locale.getDefault();
        @Serializable private Locale deviceLocale = Locale.getDefault();
        @Serializable private String osVersion = System.getProperty("os.name") + System.getProperty("os.version");
        @Serializable private String deviceName = System.getProperty("java.vendor");
        @Serializable private String headerUserAgent = "Mozilla/5.0";
        @Serializable private String appVersion = "26.10.4";
        @Serializable private String screen = "1080x1920 1.0x";
        @Serializable private TimeZone timezone = TimeZone.getDefault();

        @NoArgsConstructor(access = AccessLevel.PRIVATE)
        public static final class DeviceType { public static final String WEB = "WEB"; }
    }
}
