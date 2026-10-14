package dom4k.maxbridge.max.pushes;

import dom4k.maxbridge.max.ProtoObject;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@ProtoObject.Register(opcode = 129)
public class NotifTyping extends ProtoObject.ResponseObject {
  @Serializable private long chatId;
  @Serializable private long userId;
}
