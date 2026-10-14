package dom4k.maxbridge.models;

import dom4k.maxbridge.DatabaseManager;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.Session;

@Entity
@Table(name = "chat_topics", indexes = {
        @Index(name = "idx_topic_chat", columnList = "telegramUserId, maxChatId"),
        @Index(name = "idx_topic_thread", columnList = "telegramUserId, threadId")
})
@Getter @Setter @AllArgsConstructor @NoArgsConstructor @Builder
public class ChatTopic {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false) private long telegramUserId;
    @Column(nullable = false) private long maxChatId;
    @Column(nullable = false) private long threadId;
    @Column private String title;

    public void save() {
        try (Session session = DatabaseManager.getSession()) {
            session.beginTransaction();
            session.persist(this);
            session.getTransaction().commit();
        }
    }

    public static ChatTopic findByChat(long telegramUserId, long maxChatId) {
        try (Session session = DatabaseManager.getSession()) {
            return session.createQuery(
                            "from ChatTopic where telegramUserId = :u and maxChatId = :c order by id desc", ChatTopic.class)
                    .setParameter("u", telegramUserId)
                    .setParameter("c", maxChatId)
                    .setMaxResults(1)
                    .uniqueResult();
        }
    }

    public static ChatTopic findByThread(long telegramUserId, long threadId) {
        try (Session session = DatabaseManager.getSession()) {
            return session.createQuery(
                            "from ChatTopic where telegramUserId = :u and threadId = :t order by id desc", ChatTopic.class)
                    .setParameter("u", telegramUserId)
                    .setParameter("t", threadId)
                    .setMaxResults(1)
                    .uniqueResult();
        }
    }

    public static long count(long telegramUserId) {
        try (Session session = DatabaseManager.getSession()) {
            Long n = session.createQuery("select count(t) from ChatTopic t where t.telegramUserId = :u", Long.class)
                    .setParameter("u", telegramUserId)
                    .uniqueResult();
            return n == null ? 0 : n;
        }
    }

    public static int deleteByChat(long telegramUserId, long maxChatId) {
        try (Session session = DatabaseManager.getSession()) {
            session.beginTransaction();
            int n = session.createMutationQuery("delete from ChatTopic where telegramUserId = :u and maxChatId = :c")
                    .setParameter("u", telegramUserId)
                    .setParameter("c", maxChatId)
                    .executeUpdate();
            session.getTransaction().commit();
            return n;
        }
    }

    public static int deleteAll(long telegramUserId) {
        try (Session session = DatabaseManager.getSession()) {
            session.beginTransaction();
            int n = session.createMutationQuery("delete from ChatTopic where telegramUserId = :u")
                    .setParameter("u", telegramUserId)
                    .executeUpdate();
            session.getTransaction().commit();
            return n;
        }
    }
}
