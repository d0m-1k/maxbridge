package dom4k.maxbridge.max.packets;

import dom4k.maxbridge.max.ProtoObject;
import dom4k.maxbridge.max.types.Folder;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@ProtoObject.Register(opcode = 272)
public class GetFoldersResponse extends ProtoObject.ResponseObject {
  @Serializable private long folderSync;
  @Serializable private Folder[] folders;
  @Serializable private String[] foldersOrder;
}
