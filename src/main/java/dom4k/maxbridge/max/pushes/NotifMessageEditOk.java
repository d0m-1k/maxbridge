package dom4k.maxbridge.max.pushes;

import dom4k.maxbridge.max.ProtoObject;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@ProtoObject.Register(opcode = 128)
public class NotifMessageEditOk extends ProtoObject.RequestObject {
    @Serializable private long chatId;
    @Serializable private long messageId;
}
