package dom4k.maxbridge.max.packets;

import dom4k.maxbridge.max.ProtoObject;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@ProtoObject.Register(opcode = 53)
public class GetChatsRequest extends ProtoObject.RequestObject {
    @Serializable private int count = 100;
    @Serializable private long marker = 0;
}
