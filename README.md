# AIflow

AIflow is a Spring Boot based AI workflow prototype inspired by Dify. It currently provides two runnable workflow demos:

- Chat workflow: user input -> intent classification -> LLM response or default branch.
- Data analysis workflow: data type classification -> analysis -> markdown summary.

The backend uses Spring AI Alibaba Graph and exposes a simple static web UI from `src/main/resources/static/index.html`.

## Tech Stack

- Java 21
- Spring Boot 3.5.x
- Spring AI 1.0.x
- Spring AI Alibaba Graph
- DashScope / Tongyi Qianwen
- DeepSeek through OpenAI-compatible API

## Run Locally

Set API keys first:

```powershell
$env:DASHSCOPE_API_KEY="sk-..."
$env:DEEPSEEK_API_KEY="sk-..."
```

Then start the app:

```powershell
./mvnw.cmd spring-boot:run
```

Open:

```text
http://localhost:8080
```

## Useful Commands

```powershell
./mvnw.cmd test
./mvnw.cmd package
```

## Current Features

- Chat workflow execution with provider/model selection.
- Data analysis workflow execution.
- Node timing collection.
- Simple call tree rendering.
- In-memory execution history.
- Markdown, code block, and math rendering in responses.

## Roadmap Toward Dify-like Experience

- Persist workflows, nodes, edges, app metadata, and execution records.
- Replace fixed Java-defined graphs with a workflow JSON DSL.
- Add POST JSON APIs and structured error responses.
- Build a visual workflow editor with configurable node panels.
- Add variable mapping, branch conditions, loop support, tool nodes, and knowledge retrieval nodes.
- Add workflow versioning, publish/run separation, and debug traces.
