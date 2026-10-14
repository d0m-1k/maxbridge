package dom4k.maxbridge.max.packets;

import dom4k.maxbridge.max.ProtoObject;
import dom4k.maxbridge.max.types.Message;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@ProtoObject.Register(opcode = 71)
public class GetMessageResponse extends ProtoObject.ResponseObject {
  @Serializable private long chatId;
  @Serializable private Message[] messages;
}
