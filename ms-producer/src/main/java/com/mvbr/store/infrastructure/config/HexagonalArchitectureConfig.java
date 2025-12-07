package com.mvbr.store.infrastructure.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Hexagonal Architecture Configuration.
 *
 * This configuration ensures Spring scans all layers:
 * - domain: Pure business logic (no Spring dependencies)
 * - application: Use cases and ports (minimal Spring)
 * - infrastructure: Adapters, configs, and Spring integrations
 *
 * IMPORTANT:
 * - All adapters are annotated with @Component
 * - All use cases are annotated with @Service
 * - Spring will automatically wire dependencies via constructor injection
 * - Dependency Inversion Principle is enforced (adapters implement ports)
 */
@Configuration
@ComponentScan(basePackages = {
        "com.mvbr.store.domain",
        "com.mvbr.store.application",
        "com.mvbr.store.infrastructure"
})
public class HexagonalArchitectureConfig {
    // No explicit bean definitions needed
    // Spring will auto-wire based on:
    // - @Component on adapters
    // - @Service on use cases
    // - Constructor injection everywhere
}
