package com.smarttest.manager.dto;

import lombok.Data;

@Data
public class UiEnvironment {
    private String type;        // WEB / ANDROID / IOS（大小写不敏感）
    private String url;         // WEB 必填：起始 URL
    private String packageUrl;  // APP 必填：安装包 URL
    private String appId;       // APP 必填：应用 ID
}
