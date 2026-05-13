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

## API

```http
POST /api/workflows/chat/run
Content-Type: application/json

{
  "message": "你好",
  "provider": "tongyi",
  "model": "qwen-max",
  "enableSearch": false
}
```

```http
POST /api/workflows/analysis/run
Content-Type: application/json

{
  "data": "待分析数据",
  "provider": "tongyi",
  "model": "qwen-max",
  "enableSearch": false
}
```

```http
GET /api/executions
```


### Workflow Definition APIs

```http
POST /api/apps
Content-Type: application/json

{
  "name": "默认应用",
  "description": "应用说明"
}
```

```http
GET /api/apps
```

```http
POST /api/workflow-definitions
Content-Type: application/json

{
  "appId": 1,
  "name": "客户问题处理",
  "description": "start -> llm -> end"
}
```

```http
GET /api/workflow-definitions
GET /api/workflow-definitions/{workflowId}
```

```http
PUT /api/workflow-definitions/{workflowId}/draft
Content-Type: application/json

{
  "nodes": [
    {
      "key": "start_001",
      "type": "start",
      "title": "开始",
      "x": 80,
      "y": 180,
      "config": { "inputKey": "query" }
    }
  ],
  "edges": [],
  "variables": {}
}
```
## Persistence

Execution history is stored in an H2 database under `./data/aiflow` by default. The `data/` directory is ignored by git.

The H2 console is enabled for local development:

```text
http://localhost:8080/h2-console
```

Default JDBC URL:

```text
jdbc:h2:file:./data/aiflow;AUTO_SERVER=TRUE
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


