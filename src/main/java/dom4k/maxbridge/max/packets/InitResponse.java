package dom4k.maxbridge.max.packets;

import dom4k.maxbridge.max.ProtoObject.*;
import lombok.*;

@Getter @Setter @NoArgsConstructor
@Register(opcode = 6)
public class InitResponse extends ResponseObject {
    @Serializable(name = "web-pwa-promo")
    private boolean webPWAPromo = true;

    @Serializable(name = "phone-auth-enabled")
    private boolean phoneAuthEnabled = true;

    @Serializable(name = "lang")
    private boolean lang = true;

    @Serializable(name = "location")
    private String location = "RU";

    @Serializable(name = "reg-country-code")
    private String[] regCountryCode = new String[]{"AZ", "AM", "KZ", "KG", "MD", "TJ", "UZ", "GE"};
}
