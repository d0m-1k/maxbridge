package dom4k.maxbridge.max.packets;

import dom4k.maxbridge.max.ProtoObject;
import dom4k.maxbridge.max.types.Presence;
import java.util.Map;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@ProtoObject.Register(opcode = 35)
public class GetPresenceResponse extends ProtoObject.ResponseObject {
  @Serializable private Map<Long, Presence> presence;
  @Serializable private long time;
}
