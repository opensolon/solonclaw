package com.jimuqu.claw.agent.tool;

import org.junit.jupiter.api.Test;
import org.noear.solon.annotation.Param;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

class ConversationRuntimeToolsTest {
    @Test
    void optionalToolParamsMarkedAsNotRequired() throws Exception {
        assertOptional("notifyUser", "是否标记为进度通知");
        assertOptional("spawnTask", "可选的批次键/计划键");
        assertOptional("listChildRuns", "最大返回条数");
        assertOptional("listChildRuns", "可选批次键");
        assertOptional("getRunStatus", "运行任务标识");
        assertOptional("getChildSummary", "父运行标识");
        assertOptional("getChildSummary", "可选批次键");
    }

    private void assertOptional(String methodName, String descriptionSnippet) throws Exception {
        Method method = Arrays.stream(ConversationRuntimeTools.class.getMethods())
                .filter(candidate -> methodName.equals(candidate.getName()))
                .findFirst()
                .orElseThrow(() -> new NoSuchMethodException(methodName));

        for (Parameter parameter : method.getParameters()) {
            Param annotation = parameter.getAnnotation(Param.class);
            if (annotation != null && annotation.description().contains(descriptionSnippet)) {
                assertFalse(annotation.required(), methodName + " parameter [" + descriptionSnippet + "] should be optional");
                return;
            }
        }

        fail("Missing @Param containing description snippet: " + methodName + " -> " + descriptionSnippet);
    }
}
