-- Manager Service persistence schema

CREATE TABLE IF NOT EXISTS `manager_tasks` (
    `manager_task_id` VARCHAR(32) NOT NULL COMMENT '管理任务ID，如 mtk_xxx',
    `storyid` VARCHAR(64) DEFAULT NULL COMMENT '需求ID',
    `phase` VARCHAR(16) DEFAULT NULL COMMENT '阶段',
    `doctype` VARCHAR(16) DEFAULT NULL COMMENT '文档类型',
    `env_dto` TEXT DEFAULT NULL COMMENT '环境信息',
    `callback_url` VARCHAR(512) DEFAULT NULL COMMENT '回调地址',
    `status` VARCHAR(16) NOT NULL DEFAULT 'pending' COMMENT 'pending/running/completed/failed',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`manager_task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC;

CREATE TABLE IF NOT EXISTS `manager_task_details` (
    `id` BIGINT(20) UNSIGNED NOT NULL AUTO_INCREMENT,
    `manager_task_id` VARCHAR(32) NOT NULL COMMENT '管理任务ID',
    `downstream_task_id` VARCHAR(32) NOT NULL COMMENT '下游任务ID',
    `test_point_id` VARCHAR(32) DEFAULT NULL COMMENT '测试点ID',
    `status` VARCHAR(16) DEFAULT 'pending' COMMENT 'pending/running/completed/failed/unknown',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE KEY `uk_task_detail` (`manager_task_id`, `downstream_task_id`) USING BTREE,
    KEY `idx_manager_task_id` (`manager_task_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC;
