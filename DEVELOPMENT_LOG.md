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
