package dom4k.maxbridge.max.types;

import dom4k.maxbridge.max.ProtoObject;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
public class Chat extends ProtoObject {
    @Serializable private long id;
    @Serializable private String type = "DIALOG";
    @Serializable private String title;
    @Serializable private Message lastMessage;
    @Serializable private int newMessages;
    @Serializable private String status = "ACTIVE";
    @Serializable private long owner;
    @Serializable private Profile.Contact.Picture picture;

    @Serializable private long[] admins;
    @Serializable private Message pinnedMessage;
    @Serializable private long invitedBy;
    @Serializable private int participantsCount;
    @Serializable private String access;
    @Serializable private String baseIconUrl;
    @Serializable private String baseRawIconUrl;
    @Serializable private int messagesCount;

    @Serializable private Map<Long, Object> participants = new HashMap<>();
    @Serializable private long lastEventTime;
    @Serializable private int lastDelayedUpdateTime;
    @Serializable private int lastFireDelayedErrorTime;
    @Serializable private long created;
    @Serializable private long cid;
    @Serializable private long prevMessageId;
    @Serializable private int restrictions;
    @Serializable private Map<String, Object> options = new HashMap<>();
    @Serializable private long joinTime;
    @Serializable private boolean hasBots;
    @Serializable private long modified;
    @Serializable private Map<Long, Object> adminParticipants = new HashMap<>();
    @Serializable private String description;
    @Serializable private String link;
    @Serializable private Map<String, Object> reactions = new HashMap<>();
    @Serializable private Map<String, Object> videoConversation = new HashMap<>();
    @Serializable private Map<String, Object> subject = new HashMap<>();
    @Serializable private int participantSettings;
}
