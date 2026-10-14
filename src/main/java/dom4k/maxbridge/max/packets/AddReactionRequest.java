package dom4k.maxbridge.max.packets;

import dom4k.maxbridge.max.ProtoObject;
import dom4k.maxbridge.max.types.Reaction;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@ProtoObject.Register(opcode = 178)
public class AddReactionRequest extends ProtoObject.RequestObject {
    @Serializable private long chatId;
    @Serializable private String messageId;
    @Serializable private Reaction reaction;
}
