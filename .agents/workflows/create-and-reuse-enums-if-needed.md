---
description: create and reuse enums or constants if needed
---

Act as a Principal Software Engineer and System Architect specializing in microservices and clean code. I need you to perform a thorough, production-level architectural analysis on the strings used within my codebase. 

Your objective is to evaluate all string usages and create a strict, optimal plan for refactoring them into Enums or Static Constants, while maintaining highly organized code and avoiding over-engineering.

Please analyze the provided code/strings against the following strict criteria:

### 1. ENUM EVALUATION (Strict Rules)
*   **Don't over-enumify:** Only suggest an Enum if the strings represent a distinctly bounded, predefined set of related concepts (e.g., `PaymentStatus`, `UserRole`, `TransactionType`). Do not create Enums for arbitrary strings that do not logically group together.
*   **Check for existing:** Before suggesting a new Enum, determine if these strings logically belong to standard, already-existing Enums commonly found in this domain. 
*   **Organization:** Group Enums logically by domain context, not just arbitrarily.

### 2. STATIC CONSTANT EVALUATION
*   If a string is used multiple times but does *not* qualify as an Enum (e.g., config keys, log prefixes, header names, regex patterns, or formatting templates), evaluate if it should be extracted into a `static final` (or equivalent) string constant.
*   Single-use strings that hold no architectural or domain significance (e.g., standard log messages, simple exception messages) should generally be left as inline literals unless project conventions dictate otherwise. 

### 3. MICROSERVICES & COMMON LIBRARY ANALYSIS
*   **Duplicate identification:** Identify any Enums or constants that are duplicated or heavily utilized across *multiple* microservices.
*   **Shared Library vs. Service-Specific:** 
    *   If a concept is fundamentally shared across the ecosystem (e.g., standard HTTP headers, global error codes, core business states), mandate moving it to a **Common/Shared Library**.
    *   If the concept is specific to a single microservice bounded context (even if it has many values), strictly keep it local to that service to avoid creating a bloated, tightly-coupled Common Library.

### OUTPUT FORMAT
Provide a thorough analysis and a structured refactoring plan categorized as follows:
1.  **Replace with Existing Enums:** Strings that map directly to standard/assumed existing domain enums.
2.  **Create New Enums (Local vs. Shared):** Justified proposals for new Enums, specifying exactly *why* it's the correct scenario, and whether they belong in a specific microservice or the Common Library.
3.  **Extract to Static Constants:** Strings that should be constants, grouped by class/context.
4.  **Move to Common Library:** A definitive list of existing duplicates that must be migrated out of individual services into a shared package.
5.  **Leave As-Is (Inline):** Strings evaluated but rejected for extraction, with a brief explanation of why extracting them violates best practices (e.g., over-engineering).

Here is the code, strings, and microservice context to analyze:
[INSERT YOUR CODE, STRING LIST, OR ARCHITECTURE DETAILS HERE]