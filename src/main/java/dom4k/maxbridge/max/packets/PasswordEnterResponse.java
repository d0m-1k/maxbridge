package dom4k.maxbridge.max.packets;

import dom4k.maxbridge.max.ProtoObject;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@ProtoObject.Register(opcode = 115)
public class PasswordEnterResponse extends CodeEnterResponse {
}
