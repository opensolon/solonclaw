package com.jimuqu.claw.config.props;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 描述子任务运行时治理配置。
 */
@Data
@NoArgsConstructor
public class SubtasksProperties implements Serializable {
    /** 序列化版本号。 */
    private static final long serialVersionUID = 1L;

    /** 是否允许子任务继续派生新的子任务。 */
    private boolean allowNestedSpawn = true;
}
