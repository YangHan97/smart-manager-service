# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

This is a Spring Boot 2.7.18 project using Java 8 and Maven Wrapper.

- **Build package**: `export JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-8.jdk/Contents/Home && bash ./mvnw clean package -DskipTests`
- **Run tests**: `bash ./mvnw test`
- **Run a single test**: `bash ./mvnw test -Dtest=ManagerServiceMdMockTest`
- **Run the application**: `java -jar target/manager-service-1.0.0-SNAPSHOT.jar`
- **Service port**: `18081` (configurable in `application.yml`)

If Maven wrapper fails with "Could not find Maven binary", ensure `~/.m2/wrapper/dists/apache-maven-3.9.6/apache-maven-3.9.6/bin/mvn` exists and is executable.

## High-Level Architecture

`manager-service` is an aggregation/orchestration layer that coordinates a downstream test execution service. The core flow is triggered by `POST /api/v1/manager/tasks/create`.

### Core Orchestration Flow (`ManagerTaskService.dispatch`)

1. **Persist** a manager task in MySQL (`manager_tasks` table).
2. **Download Markdown document** (`DocDownloadService`) from `GET /api/v1/stories/{storyId}/docs/download?phase={phase}&docType={docType}`.
   - Supports local mocking via `mock.md-content` or `mock.md-file` properties.
3. **Parse Markdown** (`LlmParseService`) to extract per-test-case metadata (`apiDefinition`, `scenario`, `protocol`, `testData`).
   - **Primary path**: Local markdown table parser (avoids LLM latency/timeouts).
   - **Fallback**: Calls LLM (`/chat/completions`) if local parsing returns empty.
4. **Create downstream tasks** (`TaskCreateService`) by calling `POST /api/v1/tasks/create` for each parsed test case.
   - Request fields use snake_case: `api_definition`, `test_data`.
   - Persists downstream task mappings in `manager_task_details`.
5. **Asynchronous polling** (`pollAndCallback`): each downstream task is polled independently via `POST /api/v1/tasks/status` until all reach `completed`/`failed` or timeout (default 10 min, interval 5s).
6. **Completion callback** (`CallbackService`): sends final statuses to the user-provided `callbackUrl`.
7. **Report generation & upload** (`ReportService` + `ReportUploadService`):
   - Generates a markdown report from `api_test_cases` and `api_execution_results` tables in the downstream DB.
   - Filename format: `[ISTC_TR][storyid][YYYYMMDD]storyName-实例化系统用例-测试报告.md`
   - `storyName` is resolved from `StoryNameService` by querying `GET /api/v1/stories/{storyId}/docs?phase=PRODUCT_DESIGN` and extracting the substring between `[storyid]` and `.md` from the first document's `fileName`.
   - Uploads the report via `POST /api/v1/stories/{storyId}/docs?phase=TEST_EXECUTION&docType=ISTC_TR`.

### HTTP Clients

All outbound HTTP calls use Spring WebFlux `WebClient`:
- `docsDownloadWebClient` — document download / story name lookup / report upload
- `webClient` — downstream test service (`tasks/create`, `tasks/status`)
- `llmWebClient` — LLM API with Bearer token authorization

### Persistence

Uses Spring JDBC (`JdbcTemplate`) with two local tables:
- `manager_tasks` — top-level task state
- `manager_task_details` — mapping of manager tasks to downstream task IDs

Schema is defined in `src/main/resources/schema.sql`.

### Recovery

`TaskRecoveryRunner` (implements `ApplicationRunner`) runs on startup. It queries `manager_tasks` for records with `status = 'running'` and resumes polling for each one by calling `ManagerTaskService.resumePolling`.

## Important API Contracts

- **Incoming `ManagerTaskRequest` fields** (camelCase): `storyId`, `testCaseIdList`, `phase`, `docType`, `callbackUrl`, `envDTO`.
- **Outgoing downstream `TaskCreateRequest` fields**: must use `@JsonProperty("api_definition")` and `@JsonProperty("test_data")` — the downstream service expects snake_case.
- **Controller endpoints** (`ManagerTaskController`):
  - `POST /api/v1/manager/tasks/create`
  - `POST /api/v1/manager/tasks/status`

## Configuration

Key externalized properties in `application.yml`:
- `docsdownload.base-url` — document service base URL
- `downstream.base-url` — test execution service base URL
- `llm.base-url`, `llm.api-key`, `llm.model` — LLM service config
- `polling.interval-seconds`, `polling.timeout-minutes` — polling behavior
- `spring.datasource.*` — MySQL connection (points to downstream DB for report generation and local tables for task tracking)
