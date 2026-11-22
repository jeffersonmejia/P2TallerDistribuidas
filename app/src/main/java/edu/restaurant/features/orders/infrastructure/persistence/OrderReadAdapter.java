package edu.restaurant.features.orders.infrastructure.persistence;

import edu.restaurant.features.orders.application.OrderDetail;
import edu.restaurant.features.orders.application.OrderLineView;
import edu.restaurant.features.orders.application.OrderPage;
import edu.restaurant.features.orders.application.OrderReadPort;
import edu.restaurant.features.orders.application.OrderSummary;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class OrderReadAdapter implements OrderReadPort {
    private static final String SEARCH_FILTER = "(? = '' or cast(o.\"order_id\" as text) like ? or " +
            "lower(coalesce(c.\"name\", '')) like ? or lower(os.\"status_name\") like ? or " +
            "exists (select 1 from \"Order_Items\" si join \"Menu_Items\" sm on sm.\"item_id\" = si.\"item_id\" " +
            "where si.\"order_id\" = o.\"order_id\" and lower(sm.\"name\") like ?))";

    private final JdbcTemplate jdbc;

    public OrderReadAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public OrderPage search(String term, int page, int size) {
        String normalized = term == null ? "" : term.trim().toLowerCase();
        String pattern = "%" + normalized + "%";
        Object[] filter = {normalized, pattern, pattern, pattern, pattern};
        Long total = jdbc.queryForObject("select count(distinct o.\"order_id\") from \"Orders\" o " +
                "join \"Order_Status\" os on os.\"status_id\" = o.\"status_id\" " +
                "left join \"Customers\" c on c.\"customer_id\" = o.\"customer_id\" where " + SEARCH_FILTER,
                Long.class, filter);

        int offset = Math.multiplyExact(page, size);
        Object[] parameters = new Object[filter.length + 2];
        System.arraycopy(filter, 0, parameters, 0, filter.length);
        parameters[filter.length] = size;
        parameters[filter.length + 1] = offset;
        List<OrderSummary> rows = jdbc.query("select o.\"order_id\", o.\"order_date\", coalesce(c.\"name\", 'Orden sin cliente') as customer_name, " +
                        "o.\"order_type\", o.\"table_id\", os.\"status_name\", " +
                        "coalesce(sum(mi.\"price\" * oi.\"quantity\"), 0)::numeric(8,2) as total, (p.\"payment_id\" is not null) as paid " +
                        "from \"Orders\" o join \"Order_Status\" os on os.\"status_id\" = o.\"status_id\" " +
                        "left join \"Customers\" c on c.\"customer_id\" = o.\"customer_id\" " +
                        "left join \"Order_Items\" oi on oi.\"order_id\" = o.\"order_id\" " +
                        "left join \"Menu_Items\" mi on mi.\"item_id\" = oi.\"item_id\" " +
                        "left join \"Payments\" p on p.\"order_id\" = o.\"order_id\" where " + SEARCH_FILTER +
                        " group by o.\"order_id\", o.\"order_date\", c.\"name\", o.\"order_type\", o.\"table_id\", os.\"status_name\", p.\"payment_id\" " +
                        "order by o.\"order_date\" desc, o.\"order_id\" desc limit ? offset ?",
                (rs, row) -> new OrderSummary(rs.getInt("order_id"), rs.getDate("order_date").toLocalDate(), rs.getString("customer_name"),
                        rs.getString("order_type"), (Integer) rs.getObject("table_id"), rs.getString("status_name"),
                        money(rs.getBigDecimal("total")), rs.getBoolean("paid")), parameters);
        long count = total == null ? 0 : total;
        int pages = count == 0 ? 0 : (int) ((count + size - 1) / size);
        return new OrderPage(rows, page, size, count, pages);
    }

    @Override
    public Optional<OrderDetail> detail(int orderId) {
        List<OrderHeader> headers = jdbc.query("select o.\"order_id\", o.\"order_date\", o.\"customer_id\", c.\"name\" as customer_name, " +
                        "o.\"table_id\", o.\"order_type\", os.\"status_name\", p.\"amount\" as paid_amount, p.\"payment_method\" " +
                        "from \"Orders\" o join \"Order_Status\" os on os.\"status_id\" = o.\"status_id\" " +
                        "left join \"Customers\" c on c.\"customer_id\" = o.\"customer_id\" " +
                        "left join \"Payments\" p on p.\"order_id\" = o.\"order_id\" where o.\"order_id\" = ?",
                (rs, row) -> new OrderHeader(rs.getInt("order_id"), rs.getDate("order_date").toLocalDate(),
                        (Integer) rs.getObject("customer_id"), rs.getString("customer_name"), (Integer) rs.getObject("table_id"),
                        rs.getString("order_type"), rs.getString("status_name"), rs.getBigDecimal("paid_amount"), rs.getString("payment_method")), orderId);
        if (headers.isEmpty()) return Optional.empty();

        List<OrderLineView> lines = jdbc.query("select mi.\"item_id\", mi.\"name\", oi.\"quantity\", mi.\"price\" " +
                        "from \"Order_Items\" oi join \"Menu_Items\" mi on mi.\"item_id\" = oi.\"item_id\" " +
                        "where oi.\"order_id\" = ? order by mi.\"name\", mi.\"item_id\"",
                (rs, row) -> {
                    BigDecimal unit = money(rs.getBigDecimal("price"));
                    int quantity = rs.getInt("quantity");
                    return new OrderLineView(rs.getInt("item_id"), rs.getString("name"), quantity, unit, money(unit.multiply(BigDecimal.valueOf(quantity))));
                }, orderId);
        OrderHeader header = headers.getFirst();
        BigDecimal total = lines.stream().map(OrderLineView::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        return Optional.of(new OrderDetail(header.id(), header.date(), header.customerId(), header.customerName(), header.tableId(),
                header.type(), header.status(), lines, money(total), header.paidAmount(), header.paymentMethod()));
    }

    private static BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private record OrderHeader(int id, java.time.LocalDate date, Integer customerId, String customerName, Integer tableId,
                               String type, String status, BigDecimal paidAmount, String paymentMethod) {}
}
