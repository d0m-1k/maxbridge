package dom4k.maxbridge.max.packets;

import dom4k.maxbridge.max.ProtoObject;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@ProtoObject.Register(opcode = 71)
public class GetMessageRequest extends ProtoObject.RequestObject {
  @Serializable private long chatId;
  @Serializable private long[] messageIds;
}
