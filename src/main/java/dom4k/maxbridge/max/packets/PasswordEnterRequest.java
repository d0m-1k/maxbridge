package dom4k.maxbridge.max.packets;

import dom4k.maxbridge.max.ProtoObject;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@ProtoObject.Register(opcode = 115)
public class PasswordEnterRequest extends ProtoObject.RequestObject {
    @Serializable private String trackId;
    @Serializable private String password;
}
