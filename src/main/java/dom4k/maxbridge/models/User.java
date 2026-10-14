package dom4k.maxbridge.models;

import dom4k.maxbridge.DatabaseManager;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.Session;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

@Entity @Table(name = "users")
@Getter @Setter @AllArgsConstructor @NoArgsConstructor @Builder
public class User {
    @Id
    private Long telegramId;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(nullable = false)
    private State state = User.State.DEFAULT;

    @Builder.Default
    @Column(nullable = false)
    private String deviceUUID = UUID.randomUUID().toString();

    @Builder.Default
    @Column(nullable = true)
    private String authToken = null;

    public void save() {
        try (Session session = DatabaseManager.getSession()) {
            session.beginTransaction();
            session.merge(this);
            session.getTransaction().commit();
        }
    }

    public static void forEach(Consumer<User> action) {
        List<User> users;
        try (Session session = DatabaseManager.getSession()) {
            session.beginTransaction();
            users = session.createQuery("from User", User.class).getResultList();
            session.getTransaction().commit();
        }
        users.forEach(action);
    }

    public static User getById(long id) {
        User user;
        try (Session session = DatabaseManager.getSession()) {
            session.beginTransaction();
            user = session.createQuery("from User where telegramId = :telegramId", User.class)
                    .setParameter("telegramId", id)
                    .uniqueResult();
            session.getTransaction().commit();
        }
        return user;
    }

    public enum State {
        DEFAULT,
    }
}
