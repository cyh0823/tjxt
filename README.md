# 天机学堂 tianji-ai

基于 Spring Cloud 微服务架构的在线教育平台，在完整业务系统之上扩展了 AI 智能客服模块。

## 项目结构

| 模块 | 说明 |
|------|------|
| tj-gateway | 网关（路由转发、JWT 验签）|
| tj-auth | 认证中心（双 Token：access + refresh 轮换）|
| tj-user | 用户中心 |
| tj-course | 课程中心（分类、目录、教师）|
| tj-trade / tj-pay | 交易、支付（购物车、下单、退款）|
| tj-learning | 学习中心（选课、签到、积分排行榜、笔记、问答互动）|
| tj-promotion | 促销中心（优惠券领取/核销、兑换码、最优折扣方案）|
| tj-remark | 评价（点赞）|
| tj-search | 搜索（Elasticsearch）|
| tj-media | 媒体（文件存储：阿里云/腾讯云）|
| tj-message | 消息（短信、站内信、通知）|
| tj-exam | 考试中心 |
| tj-data | 数据中心（学习数据看板）|
| tj-common / tj-api | 公共配置与 Feign 接口 |
| **tj-aigc** | **AI 智能客服（扩展开发）** |

## AI 智能客服（tj-aigc）

- 基于 Spring AI Alibaba + 通义千问（DashScope）搭建
- 多 Agent 架构：RouteAgent 意图路由 → Buy / Consult / Knowledge / Recommend 四个业务 Agent
- Tool Calling：课程查询、下单等工具调用，策略模式处理工具结果（ToolResultStrategy）
- Redis 实现 ChatMemory 持久化（自定义 RedisChatMemoryRepository）
- 自定义 Advisor（RecordOptimizationAdvisor）优化对话记录
- 支持语音问答（ASR / TTS）、SSE 流式输出

## 技术栈

- Spring Boot 3.3.5
- Spring Cloud Alibaba（Nacos / OpenFeign / Gateway / Sentinel）
- Spring AI 1.0.0
- MyBatis-Plus
- Redis + Redisson
- RabbitMQ
- XXL-JOB
- Elasticsearch
