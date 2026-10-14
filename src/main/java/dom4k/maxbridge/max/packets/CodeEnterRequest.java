package dom4k.maxbridge.max.packets;

import dom4k.maxbridge.max.ProtoObject;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@ProtoObject.Register(opcode = 18)
public class CodeEnterRequest extends ProtoObject.RequestObject {
    @Serializable private String token;
    @Serializable private String verifyCode;
    @Serializable private String authTokenType = "CHECK_CODE";
}
