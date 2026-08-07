---
description: add logs that are needed for UI
---

You are an expert Frontend and Node.js Architect. Your task is to analyze the `FoodDeliveryAppUI` workspace and systematically instrument both the React frontend and the Express Node.js backend with production-grade logging and resilient exception handling.

The tech stack relies on React (v19.0.1), TypeScript, Vite, Tailwind CSS (v4), and an Express/Node.js backend. Traverse the codebase and apply the following standards:

1. Frontend Exception Handling (React 19 & TypeScript):
- Implement a global `ErrorBoundary` component to catch rendering errors and prevent white screens of death. Provide a user-friendly fallback UI styled with Tailwind CSS v4.
- Implement a global HTTP client interceptor (e.g., for `fetch` or `axios`) to catch network failures, timeouts, and 4xx/5xx responses. 
- Ensure global window handlers (`window.onerror` and `window.onunhandledrejection`) are set up to catch uncaught runtime exceptions.

2. Frontend Centralized Logging Utility:
- Create a singleton `logger.ts` utility for the React app with standard levels (DEBUG, INFO, WARN, ERROR).
- In development mode (Vite), route logs to the browser console with distinct formatting.
- In production mode, batch ERROR and WARN logs and send them securely to a dedicated Express backend endpoint (e.g., `/api/logs`) for centralized ingestion.

3. Backend Logging & Error Handling (Express / Node.js):
- Integrate a high-performance, structured JSON logging framework (like `pino` or `winston`) in the Express server.
- Add request logging middleware to trace incoming API requests and outgoing responses, including execution times and status codes.
- Implement a global Express error-handling middleware (`app.use((err, req, res, next) => {...})`) to catch all unhandled backend exceptions, log the stack trace, and return a standardized JSON error response to the UI.

4. Security & Compliance:
- Never log user PII, authentication tokens, or sensitive payload data on either the frontend or backend. 
- Ensure stack traces are never leaked to the client in production HTTP responses.

5. Execution Plan:
- Analyze the project structure to identify the main React entry point, API services, and the Express `server.ts` configuration.
- Inject the logging utility, Error Boundary, and API interceptors into the React codebase.
- Inject the structured logger and error-handling middleware into the Express backend.
- Output a summary of the modified files, newly created utility files, and any missing dependencies (e.g., `pino`, `pino-http`) that need to be added to `package.json`.