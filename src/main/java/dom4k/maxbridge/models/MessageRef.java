package dom4k.maxbridge.models;

import dom4k.maxbridge.DatabaseManager;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.Session;

@Entity
@Table(name = "message_refs", indexes = @Index(name = "idx_msgref_lookup", columnList = "telegramUserId, telegramMessageId"))
@Getter @Setter @AllArgsConstructor @NoArgsConstructor @Builder
public class MessageRef {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false) private long telegramUserId;
    @Column(nullable = false) private long telegramMessageId;
    @Column(nullable = false) private long maxChatId;
    @Column(nullable = false) private String maxMessageId;
    @Builder.Default @Column private Long createdAt = System.currentTimeMillis();

    public void save() {
        try (Session session = DatabaseManager.getSession()) {
            session.beginTransaction();
            session.persist(this);
            session.getTransaction().commit();
        }
    }

    public static MessageRef find(long telegramUserId, long telegramMessageId) {
        try (Session session = DatabaseManager.getSession()) {
            return session.createQuery(
                            "from MessageRef where telegramUserId = :u and telegramMessageId = :m", MessageRef.class)
                    .setParameter("u", telegramUserId)
                    .setParameter("m", telegramMessageId)
                    .setMaxResults(1)
                    .uniqueResult();
        }
    }

    public static int deleteOlderThan(long maxAgeMs) {
        long cutoff = System.currentTimeMillis() - maxAgeMs;
        try (Session session = DatabaseManager.getSession()) {
            session.beginTransaction();
            int n = session.createMutationQuery("delete from MessageRef where createdAt is null or createdAt < :cutoff")
                    .setParameter("cutoff", cutoff)
                    .executeUpdate();
            session.getTransaction().commit();
            return n;
        }
    }

    public static MessageRef findByMax(long telegramUserId, long maxChatId, String maxMessageId) {
        try (Session session = DatabaseManager.getSession()) {
            return session.createQuery(
                            "from MessageRef where telegramUserId = :u and maxChatId = :c and maxMessageId = :m order by id desc",
                            MessageRef.class)
                    .setParameter("u", telegramUserId)
                    .setParameter("c", maxChatId)
                    .setParameter("m", maxMessageId)
                    .setMaxResults(1)
                    .uniqueResult();
        }
    }
}
