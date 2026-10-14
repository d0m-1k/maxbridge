package dom4k.maxbridge.max.pushes;

import dom4k.maxbridge.max.ProtoObject;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@ProtoObject.Register(opcode = 130)
public class NotifRead extends ProtoObject.ResponseObject {
   @Serializable private boolean setAsUnread;
    @Serializable private long chatId;
    @Serializable private long userId;
    @Serializable private long mark;
}
