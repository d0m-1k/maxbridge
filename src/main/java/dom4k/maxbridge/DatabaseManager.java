package dom4k.maxbridge;

import dom4k.maxbridge.models.ChatTopic;
import dom4k.maxbridge.models.MessageRef;
import dom4k.maxbridge.models.User;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;

@Slf4j
public class DatabaseManager {
    private static DatabaseManager instance = null;
    public static synchronized DatabaseManager getInstance() {
        if (instance == null) instance = new DatabaseManager();
        return instance;
    }

    private final SessionFactory sessionFactory;

    private DatabaseManager() {
        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.connection.driver_class", "org.sqlite.JDBC")
                .applySetting("hibernate.connection.url", "jdbc:sqlite:app.db")
                .applySetting("hibernate.dialect", "org.hibernate.community.dialect.SQLiteDialect")
                .applySetting("hibernate.hbm2ddl.auto", "update")
                .applySetting("hibernate.show_sql", "false")
                .build();

        sessionFactory = new MetadataSources(registry)
                .addAnnotatedClass(User.class)
                .addAnnotatedClass(MessageRef.class)
                .addAnnotatedClass(ChatTopic.class)
                .buildMetadata()
                .buildSessionFactory();
    }

    public Session getSessionInstance() {
        try {
            return sessionFactory.openSession();
        } catch (HibernateException e) {
            log.error("failed to create session", e);
            throw new RuntimeException(e);
        }
    }

    public static Session getSession() {
        return getInstance().getSessionInstance();
    }
}
