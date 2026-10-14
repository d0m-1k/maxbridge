package dom4k.maxbridge.max.packets;

import dom4k.maxbridge.max.ProtoObject;
import dom4k.maxbridge.max.types.*;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@ProtoObject.Register(opcode = 18)
public class CodeEnterResponse extends ProtoObject.ResponseObject {
    @Serializable private TokenAttrs tokenAttrs = new TokenAttrs();
    @Serializable private PasswordChallenge passwordChallenge = new PasswordChallenge();
    @Serializable private Profile profile = new Profile();
    @Serializable private Chat[] chats;

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class TokenAttrs extends ProtoObject {
        @Serializable(name = "LOGIN") private Login login = new Login();

        @Getter @Setter @NoArgsConstructor @AllArgsConstructor
        public static class Login extends ProtoObject {
            @Serializable private String token;
        }
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class PasswordChallenge extends ProtoObject {
        @Serializable private String trackId;
        @Serializable private String hint;
        @Serializable private String email;
        @Serializable private Config config = new Config();

        @Getter @Setter @NoArgsConstructor @AllArgsConstructor
        public static class Config extends ProtoObject {
            @Serializable private int passMinLen = 6;
            @Serializable private int maxLen = 64;
            @Serializable private int hintMaxLen = 30;
        }
    }
}
