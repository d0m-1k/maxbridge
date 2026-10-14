package dom4k.maxbridge.max.packets;

import dom4k.maxbridge.max.ProtoObject;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@ProtoObject.Register(opcode = 50)
public class ChatReadRequest extends ProtoObject.RequestObject {
    @Serializable private String type = "READ_MESSAGE";
    @Serializable private long chatId;
    @Serializable private long messageId;
    @Serializable private long mark;
}
