package dom4k.maxbridge.max.packets;

import dom4k.maxbridge.max.ProtoObject;
import dom4k.maxbridge.max.types.ReactionInfo;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@ProtoObject.Register(opcode = 179)
public class CancelReactionResponse extends ProtoObject.ResponseObject {
    @Serializable private ReactionInfo reactionInfo;
}
