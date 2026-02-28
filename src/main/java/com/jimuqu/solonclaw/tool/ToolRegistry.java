package com.jimuqu.solonclaw.tool;

import org.noear.solon.ai.annotation.ToolMapping;
import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Init;
import org.noear.solon.core.AppContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具注册器
 * <p>
 * 自动扫描并注册所有带有 @ToolMapping 注解的工具方法
 *
 * @author SolonClaw
 */
@Component
public class ToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(ToolRegistry.class);

    /**
     * 工具列表：key = 工具名称, value = 工具对象（包含方法信息）
     */
    private final Map<String, ToolInfo> tools = new HashMap<>();

    /**
     * 工具对象列表：用于传递给 Agent
     */
    private final List<Object> toolObjects = new ArrayList<>();

    /**
     * 初始化时扫描工具
     */
    @Init
    public void scanTools(AppContext context) {
        log.info("开始扫描工具...");

        // 获取所有带有 @Component 注解的 Bean
        List<Object> beans = context.getBeansOfType(Object.class);
        for (Object bean : beans) {
            Class<?> beanClass = bean.getClass();
            Component componentAnnotation = beanClass.getAnnotation(Component.class);

            // 只扫描带有 @Component 注解的类
            if (componentAnnotation != null) {
                scanClassForTools(bean, beanClass);
            }
        }

        log.info("工具扫描完成，共发现 {} 个工具", tools.size());
        if (!tools.isEmpty()) {
            for (Map.Entry<String, ToolInfo> entry : tools.entrySet()) {
                ToolInfo tool = entry.getValue();
                log.debug("  - {}: {} (方法: {}.{})",
                    entry.getKey(), tool.description(),
                    tool.method().getDeclaringClass().getSimpleName(),
                    tool.method().getName()
                );
            }
        }
    }

    /**
     * 扫描类中的工具方法
     */
    private void scanClassForTools(Object bean, Class<?> clazz) {
        Method[] methods = clazz.getMethods();

        for (Method method : methods) {
            ToolMapping toolMapping = method.getAnnotation(ToolMapping.class);
            if (toolMapping != null) {
                registerTool(bean, method, toolMapping);
            }
        }
    }

    /**
     * 注册工具
     */
    private void registerTool(Object bean, Method method, ToolMapping annotation) {
        String toolName = generateToolName(method);

        ToolInfo toolInfo = new ToolInfo(
            toolName,
            annotation.description(),
            bean,
            method
        );

        tools.put(toolName, toolInfo);
        toolObjects.add(bean);

        log.debug("注册工具: {} - {}", toolName, annotation.description());
    }

    /**
     * 生成工具名称
     * 格式：类名.方法名 (如: ShellTool.exec)
     */
    private String generateToolName(Method method) {
        String className = method.getDeclaringClass().getSimpleName();
        String methodName = method.getName();
        return className + "." + methodName;
    }

    /**
     * 获取所有工具信息
     */
    public Map<String, ToolInfo> getTools() {
        return new HashMap<>(tools);
    }

    /**
     * 获取所有工具对象
     * 用于传递给 ReActAgent
     */
    public List<Object> getToolObjects() {
        return new ArrayList<>(toolObjects);
    }

    /**
     * 根据名称获取工具
     */
    public ToolInfo getTool(String name) {
        return tools.get(name);
    }

    /**
     * 检查工具是否存在
     */
    public boolean hasTool(String name) {
        return tools.containsKey(name);
    }

    /**
     * 工具信息
     *
     * @param name        工具名称
     * @param description 工具描述
     * @param bean        工具对象
     * @param method      工具方法
     */
    public record ToolInfo(
            String name,
            String description,
            Object bean,
            Method method
    ) {
        /**
         * 获取方法参数描述
         */
        public List<ParameterInfo> getParameters() {
            List<ParameterInfo> params = new ArrayList<>();
            var parameters = method.getParameters();

            for (var param : parameters) {
                org.noear.solon.annotation.Param paramAnnotation =
                    param.getAnnotation(org.noear.solon.annotation.Param.class);

                if (paramAnnotation != null) {
                    params.add(new ParameterInfo(
                        param.getName(),
                        paramAnnotation.description(),
                        param.getType().getSimpleName()
                    ));
                }
            }

            return params;
        }
    }

    /**
     * 参数信息
     *
     * @param name        参数名
     * @param description 参数描述
     * @param type        参数类型
     */
    public record ParameterInfo(
            String name,
            String description,
            String type
    ) {
    }
}