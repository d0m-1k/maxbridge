package dom4k.maxbridge.max.packets;

import dom4k.maxbridge.max.ProtoObject;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@ProtoObject.Register(opcode = 17)
public class VerificationResponse extends ProtoObject.ResponseObject {
    @Serializable private int requestMaxDuration = 60000;
    @Serializable private int requestCountLeft = 10;
    @Serializable private int altActionDuration = 60000;
    @Serializable private int codeLength = 6;
    @Serializable private String token;
}
