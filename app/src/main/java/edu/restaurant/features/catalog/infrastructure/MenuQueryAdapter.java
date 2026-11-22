package edu.restaurant.features.catalog.infrastructure;

import edu.restaurant.features.catalog.application.MenuItemData;
import edu.restaurant.features.catalog.application.MenuQueryPort;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class MenuQueryAdapter implements MenuQueryPort {
    private final JdbcTemplate jdbc;

    public MenuQueryAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<MenuItemData> search(String term) {
        String query = term == null ? "" : term.trim().toLowerCase();
        String pattern = "%" + query + "%";
        return jdbc.query("select mi.\"item_id\", mi.\"name\", mi.\"price\", coalesce(mc.\"category_name\", '') as category " +
                        "from \"Menu_Items\" mi left join \"Menu_Categories\" mc on mc.\"category_id\" = mi.\"category_id\" " +
                        "where (? = '' or lower(mi.\"name\") like ? or lower(coalesce(mc.\"category_name\", '')) like ?) " +
                        "order by mi.\"name\", mi.\"item_id\" limit 100",
                (rs, row) -> new MenuItemData(rs.getInt("item_id"), rs.getString("name"), rs.getBigDecimal("price"), rs.getString("category")),
                query, pattern, pattern);
    }

    @Override
    public Map<Integer, MenuItemData> findByIds(Set<Integer> itemIds) {
        if (itemIds.isEmpty()) return Map.of();
        String placeholders = String.join(",", java.util.Collections.nCopies(itemIds.size(), "?"));
        Object[] parameters = itemIds.toArray();
        return jdbc.query("select mi.\"item_id\", mi.\"name\", mi.\"price\", coalesce(mc.\"category_name\", '') as category " +
                        "from \"Menu_Items\" mi left join \"Menu_Categories\" mc on mc.\"category_id\" = mi.\"category_id\" " +
                        "where mi.\"item_id\" in (" + placeholders + ") order by mi.\"item_id\"",
                (rs, row) -> new MenuItemData(rs.getInt("item_id"), rs.getString("name"), rs.getBigDecimal("price"), rs.getString("category")), parameters)
                .stream().collect(Collectors.toMap(MenuItemData::id, item -> item));
    }
}
