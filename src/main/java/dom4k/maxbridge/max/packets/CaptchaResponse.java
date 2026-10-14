package dom4k.maxbridge.max.packets;

import dom4k.maxbridge.max.ProtoObject.*;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@Register(opcode = 224)
public class CaptchaResponse extends ResponseObject {
    @Serializable private String link = "https://id.vk.ru/not_robot_captcha?domain=web.max.ru&session_token=";
}
