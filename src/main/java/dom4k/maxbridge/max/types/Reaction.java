package dom4k.maxbridge.max.types;

import dom4k.maxbridge.max.ProtoObject;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class Reaction extends ProtoObject {
    @Serializable private String reactionType = "EMOJI";
    @Serializable private String id;
}
