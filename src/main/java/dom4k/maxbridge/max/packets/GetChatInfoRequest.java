package dom4k.maxbridge.max.packets;

import dom4k.maxbridge.max.ProtoObject;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@ProtoObject.Register(opcode = 48)
public class GetChatInfoRequest extends ProtoObject.RequestObject {
  @Serializable private long[] chatIds;
}
