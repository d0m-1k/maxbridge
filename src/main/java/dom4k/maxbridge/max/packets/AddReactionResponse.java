package dom4k.maxbridge.max.packets;

import dom4k.maxbridge.max.ProtoObject;
import dom4k.maxbridge.max.types.ReactionInfo;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@ProtoObject.Register(opcode = 178)
public class AddReactionResponse extends ProtoObject.ResponseObject {
    @Serializable private ReactionInfo reactionInfo;
}
