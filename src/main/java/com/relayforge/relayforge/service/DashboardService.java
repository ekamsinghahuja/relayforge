package com.relayforge.relayforge.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.Map;

@Service
public class DashboardService {
    private final JdbcTemplate jdbc;
    public DashboardService(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public Summary summary() {
        Map<String,Long> counts=jdbc.queryForMap("""
                SELECT (SELECT COUNT(*) FROM webhook_events) total_events,
                       (SELECT COUNT(*) FROM webhook_endpoints) total_endpoints,
                       (SELECT COUNT(*) FROM webhook_endpoints WHERE active) active_endpoints,
                       (SELECT COUNT(*) FROM webhook_endpoints WHERE NOT active) inactive_endpoints,
                       (SELECT COUNT(*) FROM webhook_deliveries) total_deliveries,
                       (SELECT COUNT(*) FROM webhook_deliveries WHERE status='PENDING') pending,
                       (SELECT COUNT(*) FROM webhook_deliveries WHERE status='PROCESSING') processing,
                       (SELECT COUNT(*) FROM webhook_deliveries WHERE status='RETRY_WAIT') retry_wait,
                       (SELECT COUNT(*) FROM webhook_deliveries WHERE status='DELIVERED') delivered,
                       (SELECT COUNT(*) FROM webhook_deliveries WHERE status='FAILED') failed
                """).entrySet().stream().collect(java.util.stream.Collectors.toMap(Map.Entry::getKey,e -> ((Number)e.getValue()).longValue()));
        long total=counts.get("total_deliveries"), success=counts.get("delivered");
        double rate=total==0?0.0:(double)success/total;
        return new Summary(counts,rate);
    }
    public record Summary(Map<String,Long> counts,double successRate) {}
}
