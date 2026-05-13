# Development Log

## 2026-05-13

### Repository setup

- Took over the existing prototype under `D:\project\ai-workflow`.
- Confirmed the project was not yet a git repository.
- Reviewed the current Spring Boot / Spring AI Alibaba Graph structure.
- Verified `./mvnw.cmd test` passes before repository setup.
- Added repository hygiene files and documentation:
  - `.gitignore`
  - `.env.example`
  - `README.md`
  - `DEVELOPMENT_LOG.md`
- Replaced the hard-coded DashScope API key in `application.yaml` with environment-variable placeholders.

### Current architecture notes

- `WorkflowGraph` defines two fixed graphs:
  - `myWorkflowGraph`: intent classification -> answer/default.
  - `analysisGraph`: type classification -> analysis -> summary.
- `GraphController` exposes `/workflow`, `/analysis`, and `/history`.
- Frontend is a single static page at `src/main/resources/static/index.html`.
- Execution history is currently in memory and will be lost after restart.

### Risks and next work

- Rotate any API key that was previously committed, shared, or visible in local files.
- Move GET endpoints with long user input to POST JSON APIs.
- Add persistent storage for workflows and execution records.
- Introduce a workflow definition model before building a Dify-like visual editor.

## 2026-05-13 - API and persistence foundation

### Added

- Added POST JSON APIs for workflow execution:
  - `POST /api/workflows/chat/run`
  - `POST /api/workflows/analysis/run`
- Added persistent execution history with Spring Data JPA and H2.
- Added `GET /api/executions` for the latest execution records.
- Added request DTOs with basic validation.
- Added a global exception handler with structured API errors.
- Updated the static frontend to call the new POST APIs.
- Added local H2 data files to `.gitignore`.

### Notes

- The old `/history` endpoint remains as a compatibility alias for now.
- Execution records currently persist the response-level call tree as JSON. A later runtime engine should split this into per-node execution rows.

## 2026-05-13 - Workflow definition foundation

### Added

- Added Dify-like workflow definition persistence tables:
  - `ai_apps`
  - `workflow_definitions`
  - `workflow_versions`
  - `workflow_nodes`
  - `workflow_edges`
- Added workflow definition APIs:
  - `POST /api/apps`
  - `GET /api/apps`
  - `POST /api/workflow-definitions`
  - `GET /api/workflow-definitions`
  - `GET /api/workflow-definitions/{workflowId}`
  - `PUT /api/workflow-definitions/{workflowId}/draft`
- Added `WorkflowDefinitionService` to create apps, create workflows, read latest draft versions, and save node/edge definitions.

### Verified

- `./mvnw.cmd test` passes.
- Local smoke test created an app, created a workflow, and saved a `start -> llm -> end` draft definition.

### Next work

- Build dynamic runtime execution from saved workflow definitions.
- Add node type contracts for `start`, `llm`, `condition`, `code`, `http`, `set_variable`, and `end`.
- Start replacing the single-page static UI with workflow list/detail/editor views.
