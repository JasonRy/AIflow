# Workflow Record

## Direction

Build AIflow toward a Dify-like workflow product, with an emphasis on consumer-friendly workflow creation rather than raw technical configuration.

## Current Branch

`codex/consumer-workflow-ui`

## Progress

- Established Spring Boot workflow APIs and PostgreSQL persistence for apps, workflow definitions, versions, nodes, edges, and execution history.
- Added saved workflow execution beyond fixed demo graphs.
- Supported runtime nodes: `start`, `condition`, `set_variable`, `llm`, and `end`.
- Added execution traces/call trees and persisted them into history.
- Reworked the workflow page from a JSON-heavy debug view into a three-area editor:
  - left: templates and step library
  - center: workflow canvas and trial run
  - right: selected step settings
- Shifted UI language from DSL fields to user-facing concepts such as steps, templates, AI tasks, answer style, and conditions.

## Product Principles

- Users should build by choosing templates and steps, not by editing JSON.
- Technical fields stay available only as advanced settings.
- The main workflow mental model is:
  1. choose or create a flow
  2. add or select a step
  3. describe what the step should do
  4. save and trial run

## Done

### Canvas node deletion (2026-05-13)

File: `src/main/resources/static/index.html`

- Added `deleteWorkflowNode(key)`: removes the node and all connected edges; `start` nodes are protected.
- Canvas card: hover reveals a ✕ button in the top-right corner; click deletes without triggering node selection.
- Inspector panel: a "Delete this step" button appears at the bottom of the editor when a non-`start` node is selected.
- Added `.workflow-node-delete` style: hidden by default, visible on node hover, turns red on button hover.

### Canvas connection and positioning (2026-05-14)

File: `src/main/resources/static/index.html`

- Added node connection ports and drag-to-connect interactions.
- Added click-to-delete for canvas edges.
- Added drag-to-position for canvas nodes; updated positions are saved through the existing draft payload.

### Step library usability (2026-05-14)

File: `src/main/resources/static/index.html`

- Step library entries now create a workflow automatically when no current workflow exists.
- Users can click "AI generation", "condition", or "set content" directly without first pressing "New".

## Next

- Make step adding more robust with proper branch insertion and edge editing.
- Add clearer visual affordances for connection creation and deletion.
- Add publish/version separation.
- Add more Dify-like nodes: HTTP, code, loop, tools, and knowledge retrieval.
