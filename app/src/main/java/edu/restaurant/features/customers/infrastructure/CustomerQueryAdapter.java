package edu.restaurant.features.customers.infrastructure;

import edu.restaurant.features.customers.application.CustomerData;
import edu.restaurant.features.customers.application.CustomerQueryPort;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CustomerQueryAdapter implements CustomerQueryPort {
    private final JdbcTemplate jdbc;

    public CustomerQueryAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<CustomerData> findById(int customerId) {
        return jdbc.query("select \"customer_id\", \"name\", \"email\", \"phone_number\" from \"Customers\" where \"customer_id\" = ?",
                (rs, row) -> new CustomerData(rs.getInt("customer_id"), rs.getString("name"), rs.getString("email"), rs.getString("phone_number")), customerId)
                .stream().findFirst();
    }

    @Override
    public List<CustomerData> search(String term) {
        String query = term == null ? "" : term.trim().toLowerCase();
        String pattern = "%" + query + "%";
        return jdbc.query("select \"customer_id\", \"name\", \"email\", \"phone_number\" from \"Customers\" " +
                        "where (? = '' or lower(\"name\") like ? or lower(coalesce(\"email\", '')) like ? or lower(coalesce(\"phone_number\", '')) like ?) " +
                        "order by \"name\", \"customer_id\" limit 100",
                (rs, row) -> new CustomerData(rs.getInt("customer_id"), rs.getString("name"), rs.getString("email"), rs.getString("phone_number")),
                query, pattern, pattern, pattern);
    }
}
