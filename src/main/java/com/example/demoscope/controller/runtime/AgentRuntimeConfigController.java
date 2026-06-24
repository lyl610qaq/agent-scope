package com.example.demoscope.controller.runtime;

import com.example.demoscope.service.runtime.AgentRuntimeConfigService;
import com.example.demoscope.service.runtime.AgentRuntimeConfigService.AgentRuntimeConfigResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AgentRuntimeConfigController {

    private final AgentRuntimeConfigService service;

    public AgentRuntimeConfigController(AgentRuntimeConfigService service) {
        this.service = service;
    }

    @GetMapping("/api/config")
    public AgentRuntimeConfigResponse config() {
        return service.config();
    }
}
