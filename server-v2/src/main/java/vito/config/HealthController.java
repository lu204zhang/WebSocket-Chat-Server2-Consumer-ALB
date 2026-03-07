package vito.config;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;

@RestController
public class HealthController {

    /** Health check for load balancers. @return "OK" */
    @GetMapping("/health")
    public String health() {
        return "OK";
    }
}
