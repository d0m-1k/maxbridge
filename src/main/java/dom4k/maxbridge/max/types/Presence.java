package dom4k.maxbridge.max.types;

import dom4k.maxbridge.max.ProtoObject;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor
public class Presence extends ProtoObject {
  @Serializable private long seen;
  @Serializable private int status;
}
