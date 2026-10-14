package dom4k.maxbridge.max.packets;

import dom4k.maxbridge.max.ProtoObject;
import dom4k.maxbridge.max.types.Profile;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@ProtoObject.Register(opcode = 32)
public class GetContactsResponse extends ProtoObject.ResponseObject {
  @Serializable private Profile.Contact[] contacts;
}
