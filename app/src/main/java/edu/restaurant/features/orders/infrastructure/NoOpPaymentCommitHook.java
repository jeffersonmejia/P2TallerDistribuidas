package edu.restaurant.features.orders.infrastructure;

import edu.restaurant.features.orders.application.PaymentCommitHook;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!demo & !test")
public class NoOpPaymentCommitHook implements PaymentCommitHook {
    @Override
    public void afterWrites(boolean injectFailure) {
        if (injectFailure) throw new IllegalArgumentException("La inyección de fallos solo está disponible en el perfil demo o test.");
    }
}
