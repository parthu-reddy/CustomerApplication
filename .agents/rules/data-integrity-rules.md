# Global Operational Mandates for Data Integrity Verification

> **Deployment details** are in the companion rule file: [deployment-context.md](file:///Users/parthureddy/Documents/Food%20Delivery.nosync/CustomerApplication/.agents/rules/deployment-context.md).

## Operational Rules

1. **Artifacts Over Logs**: The agent must prioritize generating structured artifacts (Plans, Diffs, Test Results) over dumping raw tool logs.
2. **Parallel Subagents**: The agent should utilize parallel subagents for boundary checks across frontend, API layer, and persistence/event layers to expedite verification.
3. **Strict Schema Drift Halting**: The agent must immediately halt execution if validation schemas drift between the client and the server, await human review, and provide a Code Diff to consolidate them.
4. **No Destructive Operations Without Approval**: The agent must prompt for human approval before applying any destructive database operations or infrastructure changes, acting in a read-only assessment mode primarily.
5. **Static Analysis & Terminal Testing Only**: The agent must strictly use terminal-based tools, automated tests, and static code analysis. Browser automation (BrowserMCP) is strictly forbidden for testing validation rules.
6. **Cloudflare Domain for All HTTP Requests**: All HTTP-based verification commands targeting the deployed backend MUST use `https://eng-restricted-dad-separately.trycloudflare.com/` as the base URL, never `localhost` or raw IP addresses.

## Sandbox Configuration

Antigravity allows operators to tune safety barriers within the `~/.gemini/antigravity-cli/settings.json` configuration:

| Permission Model | Behavior |
| :---- | :---- |
| **proceed-in-sandbox** | Ideal for auditing. Read-only commands execute autonomously; destructive operations require human approval. |
| **request-review** | Prompts for all write operations. |
| **strict** | Line-by-line transparency for all non-read actions. |

## Antigravity Skills Architecture

Skills follow a progressive disclosure pattern. When a conversation starts, the agent indexes only the names and descriptions. The full instructional body is loaded only when semantically relevant.

| Skill Component | Requirement | Function |
| :---- | :---- | :---- |
| **YAML Frontmatter** | name, description | Semantic trigger for the LLM to recognize relevance. |
| **Instruction Body** | Markdown steps | Step-by-step logic, constraints, decision trees. |
| **Execution Scripts** | Python, Bash, Node | Black-box scripts in `scripts/` folder. |
| **References** | Markdown docs | Deep architectural context in `references/` folder. |

Skills are scoped locally in `.agents/skills/` for this workspace. Three atomic skills are defined:
- `zod-schema-sync` — Frontend-to-backend schema synchronization
- `api-contract-enforcer` — Interservice API contract enforcement
- `audit-outbox-pattern` — Persistence and event broker dual-write prevention
