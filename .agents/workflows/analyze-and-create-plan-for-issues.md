---
description: analyze and create plan for issues and implementations
---

Act as a Principal Systems Architect and UX/UI Specialist. I need you to perform a comprehensive, system-wide architectural and design audit on my application. 

Your objective is to identify flaws, integration gaps, performance bottlenecks, and edge cases across the entire stack. 

**CRITICAL INSTRUCTION:** Do NOT write any implementation code, scripts, or fixes during this step. Your output must strictly be a highly detailed diagnostic and strategic remediation plan. I will ask for specific implementations after reviewing your plan.

Please analyze the provided system architecture, workflows, and code against the following strict criteria:

### 1. UI/UX & FRONTEND EVALUATION
*   **Design Principles:** Evaluate if the UI adheres to a modern, minimalist, and clean visual layout. Ensure there is no awkward overlapping of text/imagery and that the user flow is mathematically logical and frictionless.
*   **Edge Cases:** Identify missing loading states, error boundary fallbacks, empty states, and debouncing/throttling on user inputs.
*   **Frontend Performance:** Look for unnecessary re-renders, bloated bundle sizes, and unoptimized asset delivery.

### 2. BACKEND & DISTRIBUTED SYSTEM INTEGRATION
*   **UI to Backend:** Check for inefficient API calls (e.g., N+1 requests from the frontend), missing pagination, and improper HTTP status code usage.
*   **Inter-Service Communication:** Analyze the microservices for tight coupling. Are there missing timeout configurations, circuit breakers, or retry mechanisms? 
*   **Event-Driven Architecture (Kafka):** Check for missing dead-letter queues (DLQs), lack of consumer idempotency, consumer lag vulnerabilities, and improper partition key strategies.

### 3. DATA LAYER & INFRASTRUCTURE
*   **Database:** Identify missing indexes, potential transaction deadlocks, race conditions, and unoptimized queries. 
*   **Caching (Redis):** Audit for cache stampedes, missing TTLs, improper cache invalidation strategies, and over-caching.
*   **Edge & Networking (Cloudflare):** Evaluate caching rules, WAF configurations, missing rate-limiting, and DNS routing inefficiencies. 

### 4. PERFORMANCE & LATENCY OPTIMIZATION
*   Identify the critical path of the main user journeys and aggressively flag anything that could introduce system latency. 
*   Look for synchronous operations that should be asynchronous background tasks.

### OUTPUT FORMAT
Provide your findings in a structured Markdown document using the following sections:
1.  **Architecture & Integration Gaps:** Critical flaws in how services, DBs, and message brokers communicate.
2.  **UI/UX Refinements:** Specific deviations from optimal, clean design and missing frontend edge cases.
3.  **Performance & Latency Bottlenecks:** A prioritized list of issues slowing down the system, from Cloudflare down to the database.
4.  **Missing Resiliency (Edge Cases):** Unhandled failure states, missing idempotency, or lack of fallbacks.
5.  **Proposed Remediation Plan:** A step-by-step roadmap for what we need to fix first based on impact and severity.

Here is the system architecture, code snippets, and component details to analyze:
[INSERT YOUR ARCHITECTURE DETAILS, COMPONENT CODE, AND SYSTEM FLOWS HERE]