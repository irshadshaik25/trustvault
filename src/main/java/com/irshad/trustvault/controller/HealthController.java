
package com.irshad.trustvault.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    @GetMapping("/api/health")
    public String healthCheck() {
        return "TrustVault backend is running successfully!";
    }

    @GetMapping("/")
    public String home() {
        return "Welcome to TrustVault! Your secure document-sharing backend is running.";
    }
}
