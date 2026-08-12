---
name: api-contract-enforcer
description: Enforces interservice API contract integrity across microservices. Use when verifying that microservice-to-microservice communication (via Feign clients, REST templates, or Kafka events) honors shared interface agreements. Triggers on mentions of 'API contract', 'contract testing', 'Pact', 'Schemathesis', 'OpenAPI', 'Feign client mismatch', 'breaking change', 'interservice', 'schema registry', 'AsyncAPI', 'consumer-driven contract'.
---

# API Contract Enforcer

> **Deep architectural context**: See [references/contract-testing-architecture.md](references/contract-testing-architecture.md) for the full explanation of CDC, Schemathesis, and contract testing modalities.
> **Deployment details**: See the workspace rule [deployment-context.md](file:///Users/parthureddy/Documents/Food%20Delivery.nosync/CustomerApplication/.agents/rules/deployment-context.md).

## Agent Instructions

1. **Verify Contract Testing is Code**: Scan CI/CD pipeline configuration (GitHub Actions, GitLab CI) to ensure OpenAPI/AsyncAPI specs are linted on every PR using Spectral rulesets.
2. **Execute Consumer-Driven Contracts (CDC)**: Run `npm run test:pact` via the terminal for consumer tests. Verify the provider pulls the Pact contract and executes provider verification tests with correct provider states.
3. **Execute Schema-First Property-Based Testing**: Run Schemathesis against the deployed backend:
   ```bash
   schemathesis run --checks all openapi.yaml --base-url https://eng-restricted-dad-separately.trycloudflare.com/
   ```
4. **Parse Structured Reports**: Do NOT manually read raw test log output. Parse exit codes and structured JSON reports from the testing tools.
5. **Handle Contract Violations**: If a violation is detected (dropped property, changed data type), generate an Artifact explaining the breach. Propose a resolution per semantic versioning rules.

## Constraints

- All HTTP requests MUST target `https://eng-restricted-dad-separately.trycloudflare.com/`, never `localhost`.
- **Halt immediately** on contract violations. Generate an Implementation Plan and await approval.
