package edu.restaurant.features.catalog.application;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface MenuQueryPort {
    List<MenuItemData> search(String term);
    Map<Integer, MenuItemData> findByIds(Set<Integer> itemIds);
}
