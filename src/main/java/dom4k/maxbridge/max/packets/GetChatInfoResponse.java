package dom4k.maxbridge.max.packets;

import dom4k.maxbridge.max.ProtoObject;
import dom4k.maxbridge.max.types.Chat;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@ProtoObject.Register(opcode = 48)
public class GetChatInfoResponse extends ProtoObject.ResponseObject {
  @Serializable private Chat[] chats;
}
