package dom4k.maxbridge.max.pushes;

import dom4k.maxbridge.max.ProtoObject;
import lombok.*;

public final class Ignored {
    private Ignored() {}

    @Getter @Setter @NoArgsConstructor
    @ProtoObject.Register(opcode = 165)
    public static class CallHistory extends ProtoObject.ResponseObject {}

    @Getter @Setter @NoArgsConstructor
    @ProtoObject.Register(opcode = 293)
    public static class Transcription extends ProtoObject.ResponseObject {
        @Serializable private long chatId;
        @Serializable private String messageId;
        @Serializable private String transcription;
        @Serializable private String transcriptionStatus;
    }
}
