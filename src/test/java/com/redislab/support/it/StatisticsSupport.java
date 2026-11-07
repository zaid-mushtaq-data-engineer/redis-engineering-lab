package com.redislab.support.it;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
public class StatisticsSupport {

    private final SessionFactory sessionFactory;

    public StatisticsSupport(EntityManagerFactory entityManagerFactory) {
        this.sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
    }

    public Statistics statistics() {
        return sessionFactory.getStatistics();
    }

    public void clear() {
        statistics().clear();
    }

    public long entityLoadCount() {
        return statistics().getEntityLoadCount();
    }

    public long queryExecutionCount() {
        return statistics().getQueryExecutionCount();
    }
}
