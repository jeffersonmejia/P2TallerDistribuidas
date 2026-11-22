package edu.restaurant.features.customers.application;

import java.util.List;
import java.util.Optional;

public interface CustomerQueryPort {
    Optional<CustomerData> findById(int customerId);
    List<CustomerData> search(String term);
}
