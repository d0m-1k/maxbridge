package dom4k.maxbridge.max.types;

import dom4k.maxbridge.max.ProtoObject;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
public class Message extends ProtoObject {
  @Serializable private long id;
  @Serializable private long cid = System.currentTimeMillis();
  @Serializable private long sender;
  @Serializable private long time;
  @Serializable private String type = "USER";
  @Serializable private String text;

  @Serializable private Attach[] attaches = new Attach[0];
  @Serializable private int options;
  @Serializable private Element[] elements = new Element[0];
  @Serializable private long updateTime;
  @Serializable private String status;
  @Serializable private Link link;
  @Serializable private ReactionInfo reactionInfo;
  @Serializable private Stats stats;
  @Serializable private boolean deleted;

  @Getter
  @Setter
  @NoArgsConstructor
  public static class Link extends ProtoObject {
    @Serializable private String type;
    @Serializable private long chatId;
    @Serializable private String chatAccessType;
    @Serializable private String chatName;
    @Serializable private String chatLink;
    @Serializable private String chatIconUrl;
    @Serializable private long messageId;
    @Serializable private Message message;

    @Override
    public void pack(org.msgpack.core.MessagePacker packer) throws java.io.IOException {
      boolean forward = "FORWARD".equals(type);
      packer.packMapHeader(forward ? 3 : 2);
      packer.packString("type");
      packer.packString(type);
      packer.packString("messageId");
      packer.packLong(messageId);
      if (forward) {
        packer.packString("chatId");
        packer.packLong(chatId);
      }
    }
  }

  @Getter
  @Setter
  @NoArgsConstructor
  public static class Stats extends ProtoObject {
    @Serializable private int views;
  }

  @Getter
  @Setter
  @NoArgsConstructor
  public static class Attach extends ProtoObject {
    @Serializable(name = "_type") private String type;

    @Serializable private String baseUrl;
    @Serializable private String photoToken;
    @Serializable private long photoId;
    @Serializable private int width;
    @Serializable private int height;
    @Serializable private byte[] previewData;
    @Serializable private String thumbnail;
    @Serializable private long videoId;
    @Serializable private int videoType;
    @Serializable private long duration;

    @Serializable private String name;
    @Serializable private long size;
    @Serializable private long fileId;
    @Serializable private String token;

    @Serializable private Keyboard keyboard;
    @Serializable private String callbackId;

    @Serializable private String event;
    @Serializable private long userId;
    @Serializable private long[] userIds;
    @Serializable private String title;
    @Serializable private String chatType;

    @Serializable private long contactId;

    @Serializable private String url;
    @Serializable private long audioId;
    @Serializable private long stickerId;
    @Serializable private String stickerType;
    @Serializable private String callType;
    @Serializable private String hangupType;
  }

  @Getter
  @Setter
  @NoArgsConstructor
  public static class Keyboard extends ProtoObject {
    @Serializable private Button[][] buttons;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Button extends ProtoObject {
      @Serializable private String type;
      @Serializable private String text;
      @Serializable private String url;
      @Serializable private String payload;
      @Serializable private String intent;
      @Serializable private String webApp;
      @Serializable private long contactId;
    }
  }

  @Getter
  @Setter
  @NoArgsConstructor
  public static class Element extends ProtoObject {
    @Serializable private String type;
    @Serializable private int from;
    @Serializable private int length;
    @Serializable private long entityId;
    @Serializable private Map<String, Object> attributes;
  }
}
