# Udhaar Platform v2: Cloud Monitoring, Alerting & SLO Specification

- **Version**: 2.0.0
- **Scope**: Udhaar Cloud Platform Edge (`asia-south1`), Cloud Functions v2, Firestore, PWA & Android.
- **Observability Target**: Zero-blindspot telemetry, p95 $< 200\text{ms}$, 99.9% uptime.

---

## 1. Service Level Indicators (SLIs) & Objectives (SLOs)

| Service Area | Indicator (SLI) | Target Objective (SLO) | Measurement Window | Alert Severity |
| :--- | :--- | :---: | :---: | :---: |
| **API Availability** | $\frac{\text{Successful Requests (2xx, 3xx, 4xx)}}{\text{Total Requests}} \times 100$ | $\ge 99.90\%$ | Rolling 30 Days | **Critical (P1)** |
| **API Latency (p95)** | 95th percentile execution duration for authenticated ledger API calls | $\le 200\text{ms}$ | Rolling 1 Hour | **High (P2)** |
| **API Latency (p99)** | 99th percentile execution duration across all API calls | $\le 500\text{ms}$ | Rolling 1 Hour | **High (P2)** |
| **Internal Server Errors** | $\frac{\text{HTTP 5xx Server Responses}}{\text{Total Requests}} \times 100$ | $\le 0.10\%$ | Rolling 5 Minutes | **Critical (P1)** |
| **WhatsApp Delivery Rate** | $\frac{\text{Delivered WhatsApp Messages}}{\text{Dispatched WhatsApp Messages}} \times 100$ | $\ge 90.00\%$ | Rolling 15 Minutes | **High (P2)** |
| **Client LCP (Web Vitals)** | Largest Contentful Paint on 3G network conditions | $\le 1200\text{ms}$ | 75th Percentile | **Medium (P3)** |
| **Client INP (Web Vitals)** | Interaction to Next Paint | $\le 100\text{ms}$ | 75th Percentile | **Medium (P3)** |
| **Client CLS (Web Vitals)** | Cumulative Layout Shift | $\le 0.05$ | 75th Percentile | **Medium (P3)** |

---

## 2. Cloud Monitoring Alerting Policies

### Alert Policy 1: High 5xx Error Rate Spike
- **Filter**: `resource.type="cloud_function" AND metric.type="logging.googleapis.com/user/http_5xx_count"`
- **Condition**: Count $> 10$ over a 5-minute rolling window.
- **Action**: PagerDuty Critical Incident $\to$ On-Call Engineer $\to$ Auto-trip `MAINTENANCE_MODE` if error rate $> 5\%$.

### Alert Policy 2: High API Latency Degradation
- **Filter**: `metric.type="cloudfunctions.googleapis.com/function/execution_times" AND percentile=95`
- **Condition**: p95 execution time $> 350\text{ms}$ sustained for 10 minutes.
- **Action**: Slack Alert $\to$ Engineering Channel. Auto-scale max Cloud Function instance concurrency from 80 to 200.

### Alert Policy 3: WhatsApp Spend Cap Approaching
- **Filter**: `metric.type="custom.googleapis.com/udhaar/circuit_breaker/whatsapp_daily_spent"`
- **Condition**: Spend $> 400\text{ units}$ (80% of daily 500-unit cap).
- **Action**: Slack Alert $\to$ Billing & Tech Lead to inspect whether to approve expansion.

### Alert Policy 4: Point-in-Time Recovery Snapshot Failure
- **Filter**: `metric.type="logging.googleapis.com/user/backup_snapshot_failed"`
- **Condition**: Daily snapshot job fails or manifest SHA-256 integrity check fails.
- **Action**: PagerDuty High Severity $\to$ SRE Lead for immediate manual snapshot trigger.

---

## 3. Distributed Tracing Protocol (W3C Trace Context)

Every API request propagating across Edge, Cloud Functions, and external providers adheres strictly to the **W3C Trace Context specification**:
```
traceparent: 00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01
              │  └───────────────┬───────────────┘ └───────┬──────┘ └─┬─┘
           Version          Trace ID (32 hex)        Span ID (16)   Flags (01=Sampled)
```
- Injected into response headers as `traceparent`, `X-Correlation-Id`, and `X-Request-Id`.
- Logged with every structured log entry, enabling immediate 1-click cross-service trace correlation in Cloud Trace and Cloud Logging.
