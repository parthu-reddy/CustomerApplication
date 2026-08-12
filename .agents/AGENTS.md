# Agent Scaffolding and Documentation Rule

Whenever generating configuration, scaffolding, or markdown, ALWAYS prioritize a scalable, directory-based file tree. NEVER generate massive monolithic files that risk hitting context limits. 

Split requirements into modular, interconnected files:
- Use lean executable `SKILL.md` files and place deeper architectural context inside separate `references/` subdirectories.
- Extract shared configurations into their own files and reference them rather than duplicating.

Additionally, always categorize documentation strictly by domain. Do not mix operational agent-level mistakes with microservice application logic mistakes. Ensure the right document is targeted (e.g., `agent-best-practices-and-mistakes.md` vs `microservices-best-practices-and-mistakes.md`).
