package edu.restaurant.features.demonstrations.infrastructure.persistence;

import edu.restaurant.features.demonstrations.application.DemoDatabaseSnapshot;
import edu.restaurant.features.demonstrations.application.DemoEvidencePort;
import edu.restaurant.features.demonstrations.application.DemoOrderEvidence;
import edu.restaurant.features.demonstrations.application.DemoSeedData;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("demo")
public class DemoJdbcEvidenceAdapter implements DemoEvidencePort {
    private final JdbcTemplate jdbc;

    public DemoJdbcEvidenceAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public DemoSeedData seedData() {
        Integer customerId = jdbc.queryForObject("""
                with inserted as (
                    insert into "Customers" ("name", "email", "phone_number")
                    values ('LABORATORIO DEMO', 'lab04@restaurant.invalid', null)
                    on conflict do nothing
                    returning "customer_id"
                )
                select coalesce(
                    (select "customer_id" from inserted),
                    (select "customer_id" from "Customers" where "email"='lab04@restaurant.invalid')
                )
                """, Integer.class);
        List<DemoSeedData> rows = jdbc.query("""
                select m."item_id", m."name", m."price"
                from "Menu_Items" m
                where m."price" > 0
                order by m."item_id"
                limit 1
                """, (rs, rowNum) -> new DemoSeedData(customerId, rs.getInt(1), rs.getString(2), rs.getBigDecimal(3)));
        return rows.stream().findFirst().orElseThrow(() -> new IllegalStateException(
                "No hay clientes y productos sembrados para ejecutar el laboratorio."));
    }

    @Override
    public DemoDatabaseSnapshot snapshot() {
        return new DemoDatabaseSnapshot(
                count("select count(*) from \"Orders\""),
                count("select count(*) from \"Order_Items\""),
                count("select count(*) from \"Payments\""),
                count("select count(*) from \"Audit_Events\" where \"action\"='PAYMENT_ATTEMPT'"),
                count("select count(*) from \"Audit_Events\" where \"action\"='ORDER_COMPLETED'"),
                count("""
                        select count(*) from "Orders" o join "Customers" c on c."customer_id"=o."customer_id"
                        where c."email"='lab04@restaurant.invalid'
                        """));
    }

    @Override
    public DemoOrderEvidence order(int orderId) {
        List<String> statuses = jdbc.query("""
                select s."status_name" from "Orders" o
                join "Order_Status" s on s."status_id"=o."status_id"
                where o."order_id"=?
                """, (rs, rowNum) -> rs.getString(1), orderId);
        boolean exists = !statuses.isEmpty();
        String status = statuses.stream().findFirst().orElse(null);
        long itemCount = count("select count(*) from \"Order_Items\" where \"order_id\"=?", orderId);
        long quantity = count("select coalesce(sum(\"quantity\"),0) from \"Order_Items\" where \"order_id\"=?", orderId);
        long paymentCount = count("select count(*) from \"Payments\" where \"order_id\"=?", orderId);
        BigDecimal amount = jdbc.queryForObject("select coalesce(sum(\"amount\"),0) from \"Payments\" where \"order_id\"=?",
                BigDecimal.class, orderId);
        long attempts = count("select count(*) from \"Audit_Events\" where \"action\"='PAYMENT_ATTEMPT' and \"order_id\"=?", orderId);
        long completed = count("select count(*) from \"Audit_Events\" where \"action\"='ORDER_COMPLETED' and \"order_id\"=?", orderId);
        List<DemoOrderEvidence> latest = jdbc.query("""
                select "actor", "operation_key", "correlation_id"
                from "Audit_Events"
                where "action"='PAYMENT_ATTEMPT' and "order_id"=?
                order by "audit_event_id" desc limit 1
                """, (rs, rowNum) -> new DemoOrderEvidence(orderId, exists, status, itemCount, quantity,
                paymentCount, amount, attempts, completed, rs.getString(1), rs.getString(2), rs.getString(3), null), orderId);
        String actor = latest.isEmpty() ? null : latest.getFirst().lastAttemptActor();
        String key = latest.isEmpty() ? null : latest.getFirst().lastAttemptOperationKey();
        String attemptCorrelation = latest.isEmpty() ? null : latest.getFirst().lastAttemptCorrelationId();
        List<String> completionCorrelations = jdbc.query("""
                select "correlation_id" from "Audit_Events"
                where "action"='ORDER_COMPLETED' and "order_id"=?
                order by "audit_event_id" desc limit 1
                """, (rs, rowNum) -> rs.getString(1), orderId);
        String completedCorrelation = completionCorrelations.stream().findFirst().orElse(null);
        return new DemoOrderEvidence(orderId, exists, status, itemCount, quantity, paymentCount, amount,
                attempts, completed, actor, key, attemptCorrelation, completedCorrelation);
    }

    private long count(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }
}
