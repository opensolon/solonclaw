package com.jimuqu.claw.config;

import com.jimuqu.claw.config.props.AgentProperties;
import com.jimuqu.claw.config.props.ChannelsProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 聚合 SolonClaw 项目的自定义配置。
 */
@Data
@NoArgsConstructor
public class SolonClawProperties implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 工作区目录。 */
    private String workspace = "./workspace";
    /** Agent 相关配置。 */
    private AgentProperties agent = new AgentProperties();
    /** 渠道相关配置。 */
    private ChannelsProperties channels = new ChannelsProperties();
}
