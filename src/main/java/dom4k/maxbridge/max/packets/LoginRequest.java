package dom4k.maxbridge.max.packets;

import dom4k.maxbridge.max.ProtoObject;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor
@ProtoObject.Register(opcode = 19)
public class LoginRequest extends ProtoObject.RequestObject {
    @Serializable private boolean interactive = true;
    @Serializable private String token;
    @Serializable private int chatsCount = 100;
    @Serializable private int chatsSync = 100;
    @Serializable private int contactsSync = 0;
    @Serializable private int presenceSync = 0;
    @Serializable private int draftsSync = 0;
}
