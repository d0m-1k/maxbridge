package dom4k.maxbridge.max.packets;

import dom4k.maxbridge.max.ProtoObject;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@ProtoObject.Register(opcode = 17)
public class VerificationRequest extends ProtoObject.RequestObject {
    @Serializable private String phone;
    @Serializable private String type = Type.START_AUTH;
    @Serializable private String language = "ru";
    @Serializable private String captchaToken;

    public static final class Type {
        public static final String START_AUTH = "START_AUTH";
        public static final String RESEND = "RESEND";
    }
}
