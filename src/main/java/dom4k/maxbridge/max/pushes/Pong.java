package dom4k.maxbridge.max.pushes;

import dom4k.maxbridge.max.ProtoObject;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor
@ProtoObject.Register(opcode = 1)
public class Pong extends ProtoObject.RequestObject {
    @Serializable private boolean interactive = true;
}
