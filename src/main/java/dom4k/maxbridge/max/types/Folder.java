package dom4k.maxbridge.max.types;

import dom4k.maxbridge.max.ProtoObject;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor
public class Folder extends ProtoObject {
  @Serializable private String id;
  @Serializable private String title;
  @Serializable private long[] include;
  @Serializable private Object[] filters;
  @Serializable private Object[] favorites;
  @Serializable private long updateTime;
  @Serializable private Object[] options;
  @Serializable private long sourceId;
}
