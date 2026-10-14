package dom4k.maxbridge.max.pushes;

import dom4k.maxbridge.max.ProtoObject;
import dom4k.maxbridge.max.types.ReactionInfo;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@ProtoObject.Register(opcode = 156)
public class NotifReaction extends ProtoObject.ResponseObject {
    @Serializable private long chatId;
    @Serializable private String messageId;
    @Serializable private ReactionInfo reactionInfo;
}
