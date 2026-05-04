package com.jimuqu.solon.claw.skillhub.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Hub 已安装技能记录。 */
@Getter
@Setter
@NoArgsConstructor
public class HubInstallRecord {
    private String name;
    private String source;
    private String identifier;
    private String trustLevel;
    private String scanVerdict;
    private String contentHash;
    private String installPath;
    private List<String> files = new ArrayList<String>();
    private Map<String, Object> metadata = new LinkedHashMap<String, Object>();
}
