package dom4k.maxbridge.max.pushes;

import dom4k.maxbridge.max.ProtoObject;
import dom4k.maxbridge.max.types.Presence;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@ProtoObject.Register(opcode = 132)
public class NotifPresence extends ProtoObject.ResponseObject {
  @Serializable private long userId;
  @Serializable private Presence presence;
}
