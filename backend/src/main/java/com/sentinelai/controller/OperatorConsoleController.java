package com.sentinelai.controller;

import com.sentinelai.model.intelligence.ErrorEventView;
import com.sentinelai.model.operator.OperatorConsole;
import com.sentinelai.service.ErrorTrackingService;
import com.sentinelai.service.OperatorConsoleService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/operator")
public class OperatorConsoleController {

    private final OperatorConsoleService operatorConsoleService;
    private final ErrorTrackingService errorTrackingService;

    public OperatorConsoleController(
            OperatorConsoleService operatorConsoleService,
            ErrorTrackingService errorTrackingService
    ) {
        this.operatorConsoleService = operatorConsoleService;
        this.errorTrackingService = errorTrackingService;
    }

    @GetMapping("/console")
    public OperatorConsole console() {
        return operatorConsoleService.current();
    }

    @GetMapping("/errors")
    public List<ErrorEventView> errors() {
        return errorTrackingService.recent();
    }
}
