# 测试用例说明

## 概述

本目录包含 SolonClaw 项目的所有单元测试和集成测试。

## 测试覆盖范围

### 1. ToolRegistry 测试
- **文件**: `com.jimuqu.solonclaw.tool.ToolRegistryTest`
- **测试数量**: 7 个
- **覆盖功能**:
  - 工具自动扫描功能
  - 工具注册功能
  - 工具信息获取功能
  - 工具对象列表获取功能
  - ToolInfo 和 ParameterInfo record 创建

### 2. SessionStore 测试
- **文件**: `com.jimuqu.solonclaw.memory.SessionStoreTest`
- **测试数量**: 11 个
- **覆盖功能**:
  - 数据库表初始化
  - 会话创建和获取
  - 消息保存和查询
  - 会话列表获取
  - 消息搜索功能
  - 会话删除功能
  - 消息排序和限制

### 3. MemoryService 测试
- **文件**: `com.jimuqu.solonclaw.memory.MemoryServiceTest`
- **测试数量**: 8 个
- **覆盖功能**:
  - 用户消息保存
  - AI 响应保存
  - 工具结果保存
  - 会话历史获取
  - 会话列表获取
  - 消息搜索功能
  - 会话删除功能
  - 旧会话清理

### 4. ShellTool 测试
- **文件**: `com.jimuqu.solonclaw.tool.impl.ShellToolTest`
- **测试数量**: 10 个
- **覆盖功能**:
  - 简单命令执行
  - 多参数命令
  - 无输出命令
  - 无效命令处理
  - 特殊字符处理
  - 输出长度限制
  - 命令独立性

### 5. GatewayController 测试
- **文件**: `com.jimuqu.solonclaw.gateway.GatewayControllerTest`
- **测试数量**: 13 个
- **覆盖功能**:
  - 健康检查接口
  - 对话接口（正常和边界情况）
  - 获取会话历史接口
  - 清空会话历史接口
  - 获取工具列表接口
  - 统一响应格式
  - Request 和 Response record 创建

## 运行测试

### 运行所有测试
```bash
mvn test
```

### 运行单个测试类
```bash
mvn test -Dtest=ToolRegistryTest
mvn test -Dtest=SessionStoreTest
mvn test -Dtest=MemoryServiceTest
mvn test -Dtest=ShellToolTest
mvn test -Dtest=GatewayControllerTest
```

### 运行特定测试方法
```bash
mvn test -Dtest=ToolRegistryTest#testGetTools_InitiallyEmpty
```

### 生成测试报告
```bash
mvn test surefire-report:report
```

## 测试注意事项

1. **网络依赖**: 部分测试需要下载 JUnit 依赖，确保网络连接正常
2. **数据库测试**: SessionStoreTest 使用内存数据库，不会影响实际数据
3. **Mock 使用**: MemoryService 和 GatewayController 使用 mock 对象进行测试
4. **独立测试**: 每个测试方法独立运行，不依赖其他测试的状态

## 测试统计

- **总测试类数**: 5
- **总测试方法数**: 49
- **覆盖功能模块**: 5 个（ToolRegistry, SessionStore, MemoryService, ShellTool, GatewayController）

## 测试结果预期

所有测试应该通过（PASS），如果出现失败（FAIL），需要：
1. 检查测试代码是否正确
2. 检查被测试的功能实现是否符合预期
3. 修复问题后重新运行测试

## 持续集成

建议在 CI/CD 流程中运行所有测试：
```bash
mvn clean test
```

确保代码提交前所有测试通过。