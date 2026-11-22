package edu.restaurant.features.catalog.presentation;

import edu.restaurant.features.catalog.application.MenuItemData;
import edu.restaurant.features.catalog.application.MenuQueryPort;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/catalog/menu-items")
public class MenuController {
    private final MenuQueryPort menu;

    public MenuController(MenuQueryPort menu) {
        this.menu = menu;
    }

    @GetMapping
    public List<MenuItemResponse> search(@RequestParam(defaultValue = "") String search) {
        return menu.search(search).stream().map(this::toResponse).toList();
    }

    private MenuItemResponse toResponse(MenuItemData item) {
        return new MenuItemResponse(item.id(), item.name(), item.price(), item.category());
    }
}
