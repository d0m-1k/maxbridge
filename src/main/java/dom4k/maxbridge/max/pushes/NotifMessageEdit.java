package dom4k.maxbridge.max.pushes;

import dom4k.maxbridge.max.ProtoObject;
import dom4k.maxbridge.max.types.Message;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@ProtoObject.Register(opcode = 128)
public class NotifMessageEdit extends ProtoObject.ResponseObject {
    @Serializable private long chatId;
    @Serializable private long mark;
    @Serializable(name = "message") private Message msg;
    @Serializable private long prevMessageId;
    @Serializable private boolean ttl = false;
    @Serializable private int unread;
}
