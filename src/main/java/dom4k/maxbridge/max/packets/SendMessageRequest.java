package dom4k.maxbridge.max.packets;

import dom4k.maxbridge.max.ProtoObject.*;
import dom4k.maxbridge.max.types.Message;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@Register(opcode = 64)
public class SendMessageRequest extends RequestObject {
    @Serializable private long chatId;
    @Serializable private boolean notify = true;
    @Serializable(name = "message") private Message msg = new Message();
}
