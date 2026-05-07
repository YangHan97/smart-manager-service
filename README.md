# Manager Service API 接口文档

## 1. 创建管理任务

### 接口定义

```
POST /api/v1/manager/tasks/create
Content-Type: application/json
```

### 请求参数（Request Body）

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `storyId` | String | 是 | 需求 ID，如 `STORY-001` |
| `testCaseIdList` | List<String> | 是 | 测试用例 ID 列表，如 `["TP001", "TP002"]` |
| `phase` | String | 是 | 阶段，如 `SIT`、`TEST_DESIGN` |
| `docType` | String | 是 | 文档类型，如 `api`、`STC_TP` |
| `callbackUrl` | String | 是 | 任务完成/失败后的回调地址 |
| `envDTO` | String | 是 | 环境信息字符串（Markdown 格式） |

### 请求示例

```json
{
  "storyId": "STORY-001",
  "phase": "TEST_DESIGN",
  "docType": "STC_TP",
  "testCaseIdList": ["STP_0012", "STP_0013"],
  "envDTO": "## 测试环境\n\n**ZK注册地址**：zookeeper://168.63.65.196:2182",
  "callbackUrl": "http://168.63.17.162:9000/callback"
}
```

### 响应参数（Response Body）

| 字段 | 类型 | 说明 |
|------|------|------|
| `code` | Integer | 业务状态码，成功为 `200` |
| `msg` | String | 提示信息，成功为 `success` |
| `taskId` | String | 管理任务 ID，格式如 `mtk_a1b2c3d4` |

### 响应示例

```json
{
  "code": 200,
  "msg": "success",
  "taskId": "mtk_a1b2c3d4"
}
```

### HTTP 状态码

| 状态码 | 说明 |
|--------|------|
| `202 Accepted` | 任务已创建并开始异步处理 |
| `400 Bad Request` | 请求参数校验失败（字段为空或缺失） |

---

## 2. 查询管理任务状态

### 接口定义

```
POST /api/v1/manager/tasks/status
Content-Type: application/json
```

### 请求参数（Request Body）

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `taskId` | String | 是 | 管理任务 ID |

### 请求示例

```json
{
  "taskId": "mtk_a1b2c3d4"
}
```

### 响应参数（Response Body）

| 字段 | 类型 | 说明 |
|------|------|------|
| `code` | Integer | 业务状态码，成功为 `200` |
| `msg` | String | 提示信息 |
| `taskStatus` | Integer | 聚合状态码，见下表 |

### `taskStatus` 枚举说明

| 值 | 含义 | 判定规则 |
|----|------|----------|
| `0` | 全部完成 | 所有下游任务均为 `completed`，且无 `pending`/`unknown` |
| `1` | 运行中 | 至少有一个下游任务处于 `running`；或存在 `pending`/`unknown` 与 `completed`/`failed` 混合 |
| `2` | 待执行/未知 | 所有下游任务均为 `pending` 或 `unknown`，且无 `completed`/`failed` |
| `-1` | 失败 | 所有下游任务均为 `failed`；或混合终态但已无 `running`/`pending`/`unknown` |

### 响应示例

```json
{
  "code": 200,
  "msg": "success",
  "taskStatus": 1
}
```

### HTTP 状态码

| 状态码 | 说明 |
|--------|------|
| `200 OK` | 查询成功 |
| `400 Bad Request` | 请求参数校验失败（`taskId` 为空） |

---

## 3. 回调通知

当管理任务处理结束（成功完成、失败或无可执行任务）时，服务会向创建任务时传入的 `callbackUrl` 发送一个 `POST` 请求。

### 接口定义

```
POST {callbackUrl}
Content-Type: application/json
```

### 回调参数（Request Body）

| 字段 | 类型 | 说明 |
|------|------|------|
| `manager_task_id` | String | 管理任务 ID |
| `phase` | String | 任务终态：`completed` 或 `failed` |
| `tasks` | List<TaskResult> | 下游任务结果列表 |

### `TaskResult` 字段说明

| 字段 | 类型 | 说明 |
|------|------|------|
| `task_id` | String | 下游任务 ID |
| `test_case_id` | String | 测试用例 ID |
| `status` | String | 下游任务状态：`completed` / `failed` / `unknown` |
| `result_summary` | JSON Object | 下游执行结果摘要，可能为 `null` |

### 回调触发场景

| 场景 | `phase` | `tasks` |
|------|---------|---------|
| 所有下游任务正常完成 + 报告成功 | `completed` | 非空数组，含各任务结果 |
| 所有下游任务正常完成 + 报告失败 | `completed_with_report_error` | 非空数组，含各任务结果 |
| 文档下载失败 | `failed` | `[]` |
| Markdown 解析失败 | `failed` | `[]` |
| 下游任务创建全部失败 | `failed` | `[]` |
| 无可用测试用例（解析结果为空） | `failed` | `[]` |

### 回调示例 — 成功完成

```json
{
  "manager_task_id": "mtk_a1b2c3d4",
  "phase": "completed",
  "tasks": [
    {
      "task_id": "tsk_xxx1",
      "test_case_id": "TP001",
      "status": "completed",
      "result_summary": { }
    },
    {
      "task_id": "tsk_xxx2",
      "test_case_id": "TP002",
      "status": "failed",
      "result_summary": null
    }
  ]
}
```

### 回调示例 — 处理失败

```json
{
  "manager_task_id": "mtk_a1b2c3d4",
  "phase": "failed",
  "tasks": []
}
```

### 回调示例 — 报告失败

```json
{
  "manager_task_id": "mtk_a1b2c3d4",
  "phase": "completed_with_report_error",
  "tasks": [
    {
      "task_id": "tsk_xxx1",
      "test_case_id": "TP001",
      "status": "completed",
      "result_summary": { }
    }
  ]
}
```

---

## 4. 核心处理流程

```
POST /api/v1/manager/tasks/create
  |
  v
1. 持久化 manager_task 记录（status=pending）
  |
  v
2. 异步下载 Markdown 文档
  |-- 失败：更新 status=failed，回调 phase=failed
  |
  v
3. 解析 Markdown（本地优先，LLM fallback）
  |-- 失败：更新 status=failed，回调 phase=failed
  |
  v
4. 逐个创建下游任务（POST /api/v1/tasks/create）
  |-- 全部失败：更新 status=failed，回调 phase=failed
  |-- 无任务可创建：更新 status=completed，回调 phase=failed
  |
  v
5. 更新 status=running，启动异步轮询
  |
  v
6. 轮询下游任务状态（POST /api/v1/tasks/status）
  |-- 每个任务独立轮询，间隔 5s，超时 10 分钟
  |
  v
7. 全部到达终态后：
   - 生成测试报告并上传
   - 发送回调（phase=completed 或 completed_with_report_error，tasks 含结果）
   - 更新 status=completed
```

---

## 5. 通用约定

- 所有入参采用 **camelCase**
- 下游接口字段使用 **snake_case**（如 `api_definition`、`test_data`）
- 时间格式默认使用 `yyyy-MM-dd HH:mm:ss`
- 测试报告文件名格式：`[ISTC_TR][storyid][YYYYMMDD]storyName-实例化系统用例-测试报告.md`
