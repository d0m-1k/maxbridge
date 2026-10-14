package dom4k.maxbridge.max.packets;

import dom4k.maxbridge.max.ProtoObject;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@ProtoObject.Register(opcode = 49)
public class GetHistoryRequest extends ProtoObject.RequestObject {
  @Serializable private long chatId;
  @Serializable private int forward = 0;
  @Serializable private int backward = 10;
  @Serializable private long backwardTime = 0;
  @Serializable private long forwardTime = 0;
  @Serializable private boolean getChat = false;
  @Serializable private long from = System.currentTimeMillis();
  @Serializable private boolean getMessages = true;
  @Serializable private boolean interactive = false;
}
