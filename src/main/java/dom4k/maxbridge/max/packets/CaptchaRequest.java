package dom4k.maxbridge.max.packets;

import dom4k.maxbridge.max.ProtoObject.*;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@Register(opcode = 224)
public class CaptchaRequest extends RequestObject {
    @Serializable private String source = "auth";
    @Serializable private String identifier;
}
