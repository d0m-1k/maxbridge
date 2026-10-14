package dom4k.maxbridge.max.packets;

import dom4k.maxbridge.max.ProtoObject;
import dom4k.maxbridge.max.types.Chat;
import dom4k.maxbridge.max.types.Message;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@ProtoObject.Register(opcode = 49)
public class GetHistoryResponse extends ProtoObject.ResponseObject {
  @Serializable private Message[] messages;
  @Serializable private Chat chat;
}
