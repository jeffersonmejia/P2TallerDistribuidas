package edu.restaurant.features.customers.presentation;

import edu.restaurant.features.customers.application.CustomerData;
import edu.restaurant.features.customers.application.CustomerQueryPort;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {
    private final CustomerQueryPort customers;

    public CustomerController(CustomerQueryPort customers) {
        this.customers = customers;
    }

    @GetMapping
    public List<CustomerResponse> search(@RequestParam(defaultValue = "") String search) {
        return customers.search(search).stream().map(this::toResponse).toList();
    }

    private CustomerResponse toResponse(CustomerData customer) {
        return new CustomerResponse(customer.id(), customer.name(), customer.email(), customer.phoneNumber());
    }
}
