package dom4k.maxbridge.max.types;

import dom4k.maxbridge.max.ProtoObject;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class ReactionInfo extends ProtoObject {
    @Serializable private ReactionCounter[] counters;
    @Serializable private String yourReaction;
    @Serializable private int totalCount;

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class ReactionCounter extends ProtoObject {
        @Serializable private int count;
        @Serializable private String reaction;
    }

}
