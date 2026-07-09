package com.sentinelai.controller;

import com.sentinelai.model.operator.OperatorConsole;
import com.sentinelai.service.OperatorConsoleService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/operator")
public class OperatorConsoleController {

    private final OperatorConsoleService operatorConsoleService;

    public OperatorConsoleController(OperatorConsoleService operatorConsoleService) {
        this.operatorConsoleService = operatorConsoleService;
    }

    @GetMapping("/console")
    public OperatorConsole console() {
        return operatorConsoleService.current();
    }
}
