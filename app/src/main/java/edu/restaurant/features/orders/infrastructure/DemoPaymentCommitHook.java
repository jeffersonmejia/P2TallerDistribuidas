package edu.restaurant.features.orders.infrastructure;

import edu.restaurant.features.orders.application.PaymentCommitHook;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile({"demo", "test"})
public class DemoPaymentCommitHook implements PaymentCommitHook {
    @Override
    public void afterWrites(boolean injectFailure) {
        if (injectFailure) throw new SimulatedPaymentFailure();
    }
}
