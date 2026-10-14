package dom4k.maxbridge.max.packets;

import dom4k.maxbridge.max.ProtoObject;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@ProtoObject.Register(opcode = 35)
public class GetPresenceRequest extends ProtoObject.RequestObject {
  @Serializable private long[] contactIds;
}
