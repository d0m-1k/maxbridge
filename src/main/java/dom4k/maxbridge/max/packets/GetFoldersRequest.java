package dom4k.maxbridge.max.packets;

import dom4k.maxbridge.max.ProtoObject;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@ProtoObject.Register(opcode = 272)
public class GetFoldersRequest extends ProtoObject.RequestObject {
  @Serializable private int folderSync = 0;
}
