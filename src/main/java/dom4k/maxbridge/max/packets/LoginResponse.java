package dom4k.maxbridge.max.packets;

import dom4k.maxbridge.max.ProtoObject;
import dom4k.maxbridge.max.types.*;
import lombok.AllArgsConstructor;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@ProtoObject.Register(opcode = 19)
public class LoginResponse extends ProtoObject.ResponseObject {
    @Serializable private Profile profile = new Profile();
    @Serializable private Chat[] chats;
    @Serializable private long chatMarker;
    @Serializable private Map<Long, Presence> presence;
    @Serializable private Map<String, Object> config;
}
