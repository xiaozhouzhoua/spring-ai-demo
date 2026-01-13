# Spring AI Demo - 结构化输出示例

基于 Spring AI 1.0 + OpenRouter 的结构化输出演示项目。

## 快速开始

### 1. 配置环境变量

在项目根目录创建 `.env` 文件：

```bash
OPENROUTER_API_KEY=your_api_key_here
```

### 2. 运行项目

```bash
./mvnw spring-boot:run
```

### 3. 测试接口

```bash
curl http://localhost:8080/api/persons
```

## 项目结构

```
├── .env                              # 环境变量（API Key）
├── http/
│   └── api.http                      # IDEA HTTP Client 测试文件
└── src/main/java/com/example/springaidemo/
    ├── SpringAiDemoApplication.java  # 应用入口
    ├── controller/
    │   └── PersonController.java     # REST 控制器
    ├── model/
    │   └── Person.java               # 数据模型
    └── service/
        └── PersonService.java        # AI 服务层
```

## 日志功能

### HTTP 请求日志拦截器

`LoggingInterceptor` 记录所有 HTTP 请求的生命周期：

```
>>> 请求开始 - GET /api/persons | IP: 127.0.0.1
--- 处理完成 - GET /api/persons
<<< 请求结束 - GET /api/persons | 状态: 200 | 耗时: 1523ms
```

记录内容：
- 请求方法和 URI
- 客户端 IP 地址
- 响应状态码
- 请求耗时（毫秒）
- 异常信息（如有）

### Spring AI 日志

使用 `SimpleLoggerAdvisor` 记录 AI 调用详情：

```java
chatClient.prompt()
    .user("...")
    .advisors(new SimpleLoggerAdvisor())
    .call()
    .entity(...);
```

需在 `application.yml` 中启用 DEBUG 日志：

```yaml
logging:
  level:
    org.springframework.ai.chat.client.advisor: DEBUG
```

## 什么是结构化输出？

想象你去餐厅点餐：

- **非结构化输出**：服务员口头告诉你"我们有宫保鸡丁38块，鱼香肉丝32块，还有麻婆豆腐..."——信息零散，你需要自己记录整理
- **结构化输出**：服务员给你一份菜单表格，每道菜都有名称、价格、分类——信息规整，直接可用

在 AI 应用中，LLM 默认返回自然语言文本。结构化输出让 AI 按照预定义的格式（如 JSON）返回数据，程序可以直接解析使用，无需复杂的文本处理。

## 核心代码解析

### 结构化输出（推荐写法）

```java
List<Person> persons = chatClient.prompt()
    .user("Generate a list of 10 persons...")
    .call()
    .entity(new ParameterizedTypeReference<List<Person>>() {});
```

Spring AI 1.0 的 `entity()` 方法内部自动完成：
- 根据 Java 类生成 JSON Schema（数据格式描述）
- 在 prompt 中注入格式要求，告诉 LLM "请按这个格式返回"
- 解析 LLM 返回的 JSON 并反序列化为 Java 对象

例如 `Person` 类会生成这样的 JSON Schema：

```json
{
  "type": "object",
  "properties": {
    "id": { "type": "integer" },
    "name": { "type": "string" },
    "age": { "type": "integer" },
    "email": { "type": "string" }
  }
}
```

**不需要** `StructuredOutputValidationAdvisor`，这是早期版本的写法。Spring AI 1.0 已将结构化输出能力内置到 `entity()` 方法中。

### Advisor（拦截器）使用场景

| 场景 | Advisor |
|------|---------|
| 日志记录 | `SimpleLoggerAdvisor` |
| RAG 检索增强 | `QuestionAnswerAdvisor` |
| 对话历史记忆 | `MessageChatMemoryAdvisor` |
| 自定义前后处理 | 自定义 Advisor |

## 配置说明

`application.yml` 关键配置：

```yaml
spring:
  ai:
    openai:
      api-key: ${OPENROUTER_API_KEY}
      base-url: https://openrouter.ai/api
      chat:
        options:
          model: xiaomi/mimo-v2-flash:free

logging:
  level:
    org.springframework.ai.chat.client.advisor: DEBUG
```

⚠️ **base-url 注意事项**：

OpenRouter 的 `base-url` 应配置为 `https://openrouter.ai/api`（不带 `/v1`），因为 Spring AI 会自动拼接 `/v1/chat/completions`。

如果配置为 `https://openrouter.ai/api/v1`，实际请求会变成 `https://openrouter.ai/api/v1/v1/chat/completions`，导致 405 错误。

## 依赖说明

- Spring Boot 3.4.1
- Spring AI 1.0.0-M4
- spring-dotenv 4.0.0（读取 .env 文件）
