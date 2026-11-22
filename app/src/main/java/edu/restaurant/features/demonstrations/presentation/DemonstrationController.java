package edu.restaurant.features.demonstrations.presentation;

import edu.restaurant.features.demonstrations.application.DemoLabStatus;
import edu.restaurant.features.demonstrations.application.DemoRunRequest;
import edu.restaurant.features.demonstrations.application.DemonstrationApplicationService;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@Profile("demo")
@RequestMapping("/api/demo/laboratory")
public class DemonstrationController {
    private final DemonstrationApplicationService service;

    public DemonstrationController(DemonstrationApplicationService service) {
        this.service = service;
    }

    @GetMapping("/scenarios")
    public List<?> scenarios(HttpServletRequest request) {
        requireLoopback(request);
        return service.scenarios();
    }

    @GetMapping("/state")
    public DemoLabStatus state(HttpServletRequest request) {
        requireLoopback(request);
        return service.status();
    }

    @PostMapping("/runs/{scenario}")
    public ResponseEntity<?> run(@PathVariable String scenario,
                                 @RequestBody(required = false) DemoRunRequest body,
                                 HttpServletRequest request) {
        requireLoopback(request);
        var result = service.run(scenario, body);
        return ResponseEntity.status(result.httpStatus()).body(result);
    }

    @PostMapping("/durability/{orderId}")
    public ResponseEntity<?> verifyDurability(@PathVariable Integer orderId, HttpServletRequest request) {
        requireLoopback(request);
        var result = service.run("verify-durability", new DemoRunRequest(orderId));
        return ResponseEntity.status(result.httpStatus()).body(result);
    }

    private static void requireLoopback(HttpServletRequest request) {
        try {
            if (!InetAddress.getByName(request.getRemoteAddr()).isLoopbackAddress()) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "El laboratorio solo acepta conexiones locales.");
            }
        } catch (UnknownHostException exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No se pudo verificar el origen local.");
        }
    }
}
