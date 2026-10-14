package dom4k.maxbridge.max.packets;

import dom4k.maxbridge.max.ProtoObject;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@ProtoObject.Register(opcode = 50)
public class ChatReadResponse extends ProtoObject.ResponseObject {
    @Serializable private int unread;
    @Serializable private long mark;
}
