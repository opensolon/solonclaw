package com.jimuqu.solon.claw.skillhub.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** GitHub tap 记录。 */
@Getter
@Setter
@NoArgsConstructor
public class TapRecord {
    private String repo;
    private String path;
}
