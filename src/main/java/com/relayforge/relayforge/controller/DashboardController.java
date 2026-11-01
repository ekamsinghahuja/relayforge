package com.relayforge.relayforge.controller;
import com.relayforge.relayforge.service.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
@RestController @RequestMapping("/api/v1/dashboard")
public class DashboardController {
    private final DashboardService service;
    public DashboardController(DashboardService service){this.service=service;}
    @GetMapping("/summary") public DashboardService.Summary summary(){return service.summary();}
}
