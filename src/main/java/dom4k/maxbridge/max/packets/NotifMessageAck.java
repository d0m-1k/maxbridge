package dom4k.maxbridge.max.packets;

import dom4k.maxbridge.max.ProtoObject;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@ProtoObject.Register(opcode = 128)
public class NotifMessageAck extends ProtoObject.RequestObject {
    @Serializable private long chatId;
    @Serializable private long messageId;
}
