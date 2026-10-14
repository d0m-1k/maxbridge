package dom4k.maxbridge.max.types;

import dom4k.maxbridge.max.ProtoObject;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor
public class Profile extends ProtoObject {
    @Serializable private Contact contact = new Contact();
    @Serializable private Object[] profileOptions;

    @Getter @Setter @NoArgsConstructor
    public static class Contact extends ProtoObject {
        @Serializable private long id;
        @Serializable private Name[] names;
        @Serializable private String about;
        @Serializable private Phone[] phones;
        @Serializable private String picture;
        @Serializable private String registrationDate;
        @Serializable private long updateTime;
        @Serializable private long registrationTime;
        @Serializable private long phone;
        @Serializable private String description;
        @Serializable private int accountStatus;
        @Serializable private Object[] options;
        @Serializable private String country;
        @Serializable private String baseUrl;
        @Serializable private String baseRawUrl;
        @Serializable private long photoId;

        @Getter @Setter @NoArgsConstructor
        public static class Name extends ProtoObject {
            @Serializable String name;
            @Serializable String firstName;
            @Serializable String lastName;
            @Serializable String type = Type.FULL_NAME;

            public static final class Type {
                public static final String FULL_NAME = "FULL_NAME";
                public static final String FIRST_NAME = "FIRST_NAME";
                public static final String LAST_NAME = "LAST_NAME";
            }
        }

        @Getter @Setter @NoArgsConstructor
        public static class Phone extends ProtoObject {
            @Serializable String number;
            @Serializable String type = Type.MOBILE;

            public static final class Type {
                public static final String MOBILE = "MOBILE";
            }
        }

        @Getter @Setter @NoArgsConstructor
        public static class Picture extends ProtoObject {
            @Serializable String url;
            @Serializable String base;
        }
    }
}
