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

## Local Database

AIflow uses PostgreSQL for local persistence by default. Start the database with Docker:

```powershell
docker compose up -d postgres
```

Default connection:

```text
jdbc:postgresql://localhost:5432/aiflow
username: aiflow
password: aiflow_dev_password
```

Then start the app:

```powershell
./mvnw.cmd spring-boot:run
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

```http
POST /api/workflow-definitions/{workflowId}/run
Content-Type: application/json

{
  "message": "只回复两个字：成功",
  "provider": "tongyi",
  "model": "qwen-turbo",
  "enableSearch": false,
  "variables": {
    "query": "只回复两个字：成功"
  }
}
```
## Persistence

Execution history and workflow definitions are stored in the local Docker PostgreSQL database by default. Tests use an in-memory H2 database.

## Current Features

- Chat workflow execution with provider/model selection.
- Data analysis workflow execution.
- Saved workflow definition execution for `start`, `condition`, `set_variable`, `llm`, and `end` nodes.
- Node timing collection.
- Simple call tree rendering.
- PostgreSQL-backed execution history.
- Markdown, code block, and math rendering in responses.

## Roadmap Toward Dify-like Experience

- Persist workflows, nodes, edges, app metadata, and execution records.
- Replace fixed Java-defined graphs with a workflow JSON DSL.
- Add POST JSON APIs and structured error responses.
- Build a visual workflow editor with configurable node panels.
- Add code, HTTP, loop, tool, and knowledge retrieval nodes.
- Add workflow versioning, publish/run separation, and debug traces.


