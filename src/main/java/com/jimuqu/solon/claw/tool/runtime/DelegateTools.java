package com.jimuqu.solon.claw.tool.runtime;

import cn.hutool.core.util.StrUtil;
import com.jimuqu.solon.claw.core.model.DelegationResult;
import com.jimuqu.solon.claw.core.model.DelegationTask;
import com.jimuqu.solon.claw.core.service.DelegationService;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.noear.snack4.ONode;
import org.noear.solon.ai.annotation.ToolMapping;
import org.noear.solon.annotation.Param;

/** 子代理委托工具。 */
@RequiredArgsConstructor
public class DelegateTools {
    /** 委托服务。 */
    private final DelegationService delegationService;

    /** 当前来源键。 */
    private final String sourceKey;

    /** 支持单任务与批量委托。 */
    @ToolMapping(
            name = "delegate_task",
            description =
                    "Delegate a subtask. mode supports single or batch. batch mode accepts tasks as JSON array.")
    public String delegateTask(
            @Param(name = "mode", description = "委托模式：single 或 batch", required = false)
                    String mode,
            @Param(name = "prompt", description = "单任务模式下的委托目标", required = false) String prompt,
            @Param(name = "tasks", description = "批量模式下的任务 JSON 数组", required = false) String tasks,
            @Param(name = "context", description = "委托补充上下文", required = false) String context,
            @Param(name = "allowedTools", description = "允许子代理使用的工具名 JSON 数组", required = false)
                    String allowedTools,
            @Param(name = "expectedOutput", description = "期望输出格式", required = false)
                    String expectedOutput,
            @Param(name = "writeScope", description = "可写入范围", required = false) String writeScope)
            throws Exception {
        if (delegationService == null) {
            return "Delegate tool is not ready";
        }

        if ("batch".equalsIgnoreCase(mode)) {
            List<DelegationTask> items = parseTasks(tasks);
            List<DelegationResult> results = delegationService.delegateBatch(sourceKey, items);
            return ONode.serialize(results);
        }

        DelegationTask task = new DelegationTask();
        task.setName("delegate");
        task.setPrompt(prompt);
        task.setContext(context);
        task.setAllowedTools(parseStringArray(allowedTools));
        task.setExpectedOutput(expectedOutput);
        task.setWriteScope(writeScope);
        DelegationResult result = delegationService.delegateSingle(sourceKey, task);
        return result.getContent();
    }

    /** 解析批量任务 JSON。 */
    private List<DelegationTask> parseTasks(String tasks) {
        List<DelegationTask> items = new ArrayList<DelegationTask>();
        if (StrUtil.isBlank(tasks)) {
            return items;
        }
        ONode node = ONode.ofJson(tasks);
        if (!node.isArray()) {
            return items;
        }
        for (int i = 0; i < node.size(); i++) {
            ONode item = node.get(i);
            DelegationTask task = new DelegationTask();
            task.setName(item.get("name").getString());
            task.setPrompt(item.get("prompt").getString());
            task.setContext(item.get("context").getString());
            task.setExpectedOutput(item.get("expectedOutput").getString());
            task.setWriteScope(item.get("writeScope").getString());
            task.setAllowedTools(parseStringArray(item.get("allowedTools").toJson()));
            items.add(task);
        }
        return items;
    }

    private List<String> parseStringArray(String json) {
        List<String> values = new ArrayList<String>();
        if (StrUtil.isBlank(json)) {
            return values;
        }
        ONode node = ONode.ofJson(json);
        if (!node.isArray()) {
            return values;
        }
        for (int i = 0; i < node.size(); i++) {
            String value = node.get(i).getString();
            if (StrUtil.isNotBlank(value)) {
                values.add(value.trim());
            }
        }
        return values;
    }
}
