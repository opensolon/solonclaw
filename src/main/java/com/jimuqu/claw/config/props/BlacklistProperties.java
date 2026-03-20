package com.jimuqu.claw.config.props;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 黑名单配置。
 */
@Data
@NoArgsConstructor
public class BlacklistProperties implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 是否启用黑名单拦截。 */
    private boolean enabled = true;
    /** 用户追加的命令关键词黑名单。 */
    private List<String> extraCommands = new ArrayList<>();
    /** 用户追加的路径黑名单。 */
    private List<String> extraPaths = new ArrayList<>();
    /** 用户追加的正则模式黑名单。 */
    private List<String> extraPatterns = new ArrayList<>();
}
