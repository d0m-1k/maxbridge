package dom4k.maxbridge.max.packets;

import dom4k.maxbridge.max.ProtoObject.*;
import dom4k.maxbridge.max.types.Message;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@Register(opcode = 64)
public class SendMessageResponse extends ResponseObject {
    @Serializable private long chatId;
    @Serializable private int unread = 1;
    @Serializable private long mark;
    @Serializable(name = "message") private Message msg = new Message();
}
