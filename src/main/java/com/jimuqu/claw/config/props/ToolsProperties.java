package com.jimuqu.claw.config.props;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 描述工具能力配置。
 */
@Data
@NoArgsConstructor
public class ToolsProperties implements Serializable {
    /** 序列化版本号。 */
    private static final long serialVersionUID = 1L;

    /** CLI TerminalSkill 是否启用沙盒模式。 */
    private boolean sandboxMode = true;
}
