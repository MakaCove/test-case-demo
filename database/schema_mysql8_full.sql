-- =============================================================================
-- AI 测试用例管理平台 · MySQL 8 全量建库脚本（与当前 backend 实体一致）
-- 字符集：utf8mb4 · 执行前请确认无重要数据（含 DROP TABLE）
-- 用法：mysql -u root -p < schema_mysql8_full.sql
-- 库名与 application.yml 一致：ai_testcase_platform
-- =============================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS test_case_histories;
DROP TABLE IF EXISTS test_case_status_logs;
DROP TABLE IF EXISTS test_cases;
DROP TABLE IF EXISTS api_test_cases;
DROP TABLE IF EXISTS generation_task_refs;
DROP TABLE IF EXISTS generation_tasks;
DROP TABLE IF EXISTS export_records;
DROP TABLE IF EXISTS operation_logs;
DROP TABLE IF EXISTS requirement_assets;
DROP TABLE IF EXISTS project_versions;
DROP TABLE IF EXISTS projects;
DROP TABLE IF EXISTS prompt_templates;
DROP TABLE IF EXISTS model_configs;
DROP TABLE IF EXISTS users;

SET FOREIGN_KEY_CHECKS = 1;

CREATE DATABASE IF NOT EXISTS ai_testcase_platform
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE ai_testcase_platform;

-- -----------------------------------------------------------------------------
-- 用户表（登录；密码哈希可由启动时升级为 BCrypt）
-- -----------------------------------------------------------------------------
CREATE TABLE users (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  username VARCHAR(64) NOT NULL COMMENT '登录名，唯一',
  password_hash VARCHAR(255) NOT NULL COMMENT '密码哈希或过渡期明文（由后端引导升级）',
  display_name VARCHAR(64) DEFAULT NULL COMMENT '展示姓名',
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '账户状态：ACTIVE 等',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否 1 是',
  PRIMARY KEY (id),
  UNIQUE KEY uk_users_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统用户';

-- -----------------------------------------------------------------------------
-- 项目
-- -----------------------------------------------------------------------------
CREATE TABLE projects (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  name VARCHAR(128) NOT NULL COMMENT '项目名称',
  code VARCHAR(64) NOT NULL COMMENT '项目编码，业务唯一',
  description TEXT COMMENT '项目描述',
  owner_user_id BIGINT DEFAULT NULL COMMENT '负责人用户 ID（当前接口层可仍返回展示名 admin）',
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '项目状态',
  created_by BIGINT NOT NULL COMMENT '创建人用户 ID',
  updated_by BIGINT NOT NULL COMMENT '最后更新人用户 ID',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否 1 是（前端 archived 等展示可与此映射）',
  PRIMARY KEY (id),
  UNIQUE KEY uk_projects_code (code),
  KEY idx_projects_name (name),
  KEY idx_projects_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目';

-- -----------------------------------------------------------------------------
-- 项目版本
-- -----------------------------------------------------------------------------
CREATE TABLE project_versions (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  project_id BIGINT NOT NULL COMMENT '所属项目 ID',
  version_no VARCHAR(64) NOT NULL COMMENT '版本号（业务展示）',
  name VARCHAR(128) DEFAULT NULL COMMENT '版本名称',
  description TEXT COMMENT '版本说明',
  status VARCHAR(16) NOT NULL DEFAULT 'DRAFT' COMMENT '版本状态：DRAFT/PUBLISHED 等',
  published_at DATETIME DEFAULT NULL COMMENT '发布时间',
  created_by BIGINT NOT NULL COMMENT '创建人用户 ID',
  updated_by BIGINT NOT NULL COMMENT '最后更新人用户 ID',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否 1 是',
  PRIMARY KEY (id),
  UNIQUE KEY uk_project_version (project_id, version_no),
  KEY idx_versions_project (project_id),
  KEY idx_versions_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目版本';

-- -----------------------------------------------------------------------------
-- 需求资产（文本 / 需求文档 / 原型图）
-- -----------------------------------------------------------------------------
CREATE TABLE requirement_assets (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  asset_code VARCHAR(64) NOT NULL COMMENT '资产业务编码，全局唯一',
  relation_code VARCHAR(128) NOT NULL COMMENT '关联批次码：同批上传/同需求组（如 RC- 前缀或 LEGACY- 单条）',
  project_id BIGINT NOT NULL COMMENT '项目 ID',
  version_id BIGINT NOT NULL COMMENT '版本 ID',
  asset_type VARCHAR(32) NOT NULL COMMENT '资产类型：TEXT 文本 / FILE 需求文档 / PROTOTYPE 原型',
  title VARCHAR(255) DEFAULT NULL COMMENT '标题（TEXT 类型常用）',
  content LONGTEXT COMMENT 'TEXT 类型正文；文件类型可为空',
  file_name VARCHAR(255) DEFAULT NULL COMMENT '原始文件名',
  file_path VARCHAR(500) DEFAULT NULL COMMENT '存储相对路径',
  file_size BIGINT DEFAULT NULL COMMENT '文件大小（字节）',
  mime_type VARCHAR(128) DEFAULT NULL COMMENT 'MIME 类型',
  source VARCHAR(32) DEFAULT 'UPLOAD' COMMENT '来源：UPLOAD/MANUAL 等',
  created_by BIGINT NOT NULL COMMENT '创建人用户 ID',
  updated_by BIGINT NOT NULL COMMENT '最后更新人用户 ID',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否 1 是',
  PRIMARY KEY (id),
  UNIQUE KEY uk_assets_asset_code (asset_code),
  KEY idx_assets_relation_code (relation_code),
  KEY idx_assets_version (version_id),
  KEY idx_assets_project (project_id),
  KEY idx_assets_type (asset_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='需求资产';

-- -----------------------------------------------------------------------------
-- Prompt 模板
-- -----------------------------------------------------------------------------
CREATE TABLE prompt_templates (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  name VARCHAR(128) NOT NULL COMMENT '模板名称',
  scope_type VARCHAR(16) NOT NULL DEFAULT 'GLOBAL' COMMENT '作用域：GLOBAL 全局 / PROJECT 项目级',
  scope_id BIGINT DEFAULT NULL COMMENT '作用域 ID（如项目 ID，GLOBAL 时为空）',
  version_no INT NOT NULL DEFAULT 1 COMMENT '模板版本号（业务递增）',
  content LONGTEXT NOT NULL COMMENT 'Prompt 正文',
  status VARCHAR(16) NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED/DISABLED',
  remark VARCHAR(255) DEFAULT NULL COMMENT '备注',
  created_by BIGINT NOT NULL COMMENT '创建人用户 ID',
  updated_by BIGINT NOT NULL COMMENT '最后更新人用户 ID',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否 1 是',
  PRIMARY KEY (id),
  KEY idx_prompt_scope (scope_type, scope_id),
  KEY idx_prompt_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Prompt 模板';

-- -----------------------------------------------------------------------------
-- 大模型配置
-- -----------------------------------------------------------------------------
CREATE TABLE model_configs (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  name VARCHAR(128) NOT NULL COMMENT '配置名称',
  provider VARCHAR(64) NOT NULL COMMENT '厂商标识',
  base_url VARCHAR(255) NOT NULL COMMENT 'API Base URL',
  model_key VARCHAR(128) NOT NULL COMMENT '模型名称/Key',
  api_key_encrypted VARCHAR(512) NOT NULL COMMENT 'API Key（存储为加密或占位，由业务约定）',
  temperature DECIMAL(3,2) DEFAULT 0.30 COMMENT '采样温度',
  max_tokens INT DEFAULT 4096 COMMENT '最大 token',
  status VARCHAR(16) NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED/DISABLED',
  created_by BIGINT NOT NULL COMMENT '创建人用户 ID',
  updated_by BIGINT NOT NULL COMMENT '最后更新人用户 ID',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否 1 是',
  PRIMARY KEY (id),
  KEY idx_model_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='大模型连接配置';

-- -----------------------------------------------------------------------------
-- 生成任务（异步队列）
-- -----------------------------------------------------------------------------
CREATE TABLE generation_tasks (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  project_id BIGINT NOT NULL COMMENT '项目 ID',
  version_id BIGINT NOT NULL COMMENT '目标版本 ID',
  task_no VARCHAR(64) NOT NULL COMMENT '任务业务单号，唯一',
  status VARCHAR(16) NOT NULL COMMENT '状态：QUEUED/RUNNING/COMPLETED/FAILED/CANCELLED',
  queue_no BIGINT DEFAULT NULL COMMENT '队列序号（可选）',
  submitted_by BIGINT NOT NULL COMMENT '提交人用户 ID',
  submitted_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '提交时间',
  started_at DATETIME DEFAULT NULL COMMENT '开始执行时间',
  finished_at DATETIME DEFAULT NULL COMMENT '结束时间',
  interrupt_by BIGINT DEFAULT NULL COMMENT '中断操作人 ID',
  interrupt_reason VARCHAR(255) DEFAULT NULL COMMENT '中断原因',
  error_message TEXT COMMENT '失败错误信息',
  model_config_id BIGINT NOT NULL COMMENT '使用的模型配置 ID',
  prompt_template_id BIGINT NOT NULL COMMENT '使用的 Prompt 模板 ID',
  case_category VARCHAR(16) NOT NULL DEFAULT 'FUNCTIONAL' COMMENT '生成用例类别：FUNCTIONAL 功能 / API 接口',
  payload_json JSON DEFAULT NULL COMMENT '任务提交参数快照（JSON）',
  result_summary JSON DEFAULT NULL COMMENT '结果摘要（JSON，如生成条数等）',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否 1 是',
  PRIMARY KEY (id),
  UNIQUE KEY uk_tasks_task_no (task_no),
  KEY idx_tasks_status_time (status, submitted_at),
  KEY idx_tasks_project_version (project_id, version_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用例生成任务';

-- -----------------------------------------------------------------------------
-- 生成任务引用版本（参考版本多选）
-- -----------------------------------------------------------------------------
CREATE TABLE generation_task_refs (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  task_id BIGINT NOT NULL COMMENT '任务 ID',
  ref_version_id BIGINT NOT NULL COMMENT '被引用版本 ID',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_task_ref (task_id, ref_version_id),
  KEY idx_task_ref_version (ref_version_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='生成任务参考版本关联';

-- -----------------------------------------------------------------------------
-- 功能测试用例
-- -----------------------------------------------------------------------------
CREATE TABLE test_cases (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  case_no VARCHAR(64) NOT NULL COMMENT '用例业务编号，唯一',
  project_id BIGINT NOT NULL COMMENT '项目 ID',
  version_id BIGINT NOT NULL COMMENT '版本 ID',
  source_task_id BIGINT DEFAULT NULL COMMENT '来源生成任务 ID（可空）',
  module_name VARCHAR(128) NOT NULL COMMENT '模块名',
  feature_name VARCHAR(128) NOT NULL COMMENT '功能名',
  title VARCHAR(255) NOT NULL COMMENT '用例标题',
  precondition TEXT COMMENT '前置条件',
  steps LONGTEXT NOT NULL COMMENT '测试步骤',
  test_data TEXT COMMENT '测试数据',
  expected_result LONGTEXT NOT NULL COMMENT '预期结果',
  priority VARCHAR(8) NOT NULL DEFAULT 'P2' COMMENT '优先级：P0-P3',
  execution_status VARCHAR(16) NOT NULL DEFAULT 'NOT_EXECUTED' COMMENT '执行状态：NOT_EXECUTED/EXECUTED/FAILED',
  review_status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT '评审状态：PENDING/APPROVED/REJECTED',
  last_executed_by BIGINT DEFAULT NULL COMMENT '最后执行人',
  last_executed_at DATETIME DEFAULT NULL COMMENT '最后执行时间',
  reviewed_by BIGINT DEFAULT NULL COMMENT '最后评审人',
  reviewed_at DATETIME DEFAULT NULL COMMENT '最后评审时间',
  review_comment TEXT COMMENT '评审意见',
  remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
  created_by BIGINT NOT NULL COMMENT '创建人用户 ID',
  updated_by BIGINT NOT NULL COMMENT '最后更新人用户 ID',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否 1 是',
  PRIMARY KEY (id),
  UNIQUE KEY uk_cases_case_no (case_no),
  KEY idx_cases_project_version (project_id, version_id),
  KEY idx_cases_status (execution_status, review_status),
  KEY idx_cases_module_feature (module_name, feature_name),
  KEY idx_cases_priority (priority)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='功能测试用例';

-- -----------------------------------------------------------------------------
-- 接口测试用例（JSON 字段存文本）
-- -----------------------------------------------------------------------------
CREATE TABLE api_test_cases (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  case_no VARCHAR(64) NOT NULL COMMENT '用例业务编号，唯一',
  project_id BIGINT NOT NULL COMMENT '项目 ID',
  version_id BIGINT NOT NULL COMMENT '版本 ID',
  source_task_id BIGINT DEFAULT NULL COMMENT '来源生成任务 ID（可空）',
  module_name VARCHAR(128) NOT NULL COMMENT '模块名',
  feature_name VARCHAR(128) NOT NULL COMMENT '功能名',
  title VARCHAR(255) NOT NULL COMMENT '用例标题',
  request_json LONGTEXT NOT NULL COMMENT '请求数据 JSON 文本',
  expected_json LONGTEXT NOT NULL COMMENT '预期响应 JSON 文本',
  assertions_json LONGTEXT NOT NULL COMMENT '断言 JSON 文本',
  priority VARCHAR(8) NOT NULL DEFAULT 'P2' COMMENT '优先级：P0-P3',
  execution_status VARCHAR(16) NOT NULL DEFAULT 'NOT_EXECUTED' COMMENT '执行状态',
  review_status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT '评审状态',
  last_executed_by BIGINT DEFAULT NULL COMMENT '最后执行人',
  last_executed_at DATETIME DEFAULT NULL COMMENT '最后执行时间',
  reviewed_by BIGINT DEFAULT NULL COMMENT '最后评审人',
  reviewed_at DATETIME DEFAULT NULL COMMENT '最后评审时间',
  review_comment TEXT COMMENT '评审意见',
  remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
  created_by BIGINT NOT NULL COMMENT '创建人用户 ID',
  updated_by BIGINT NOT NULL COMMENT '最后更新人用户 ID',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否 1 是',
  PRIMARY KEY (id),
  UNIQUE KEY uk_api_cases_case_no (case_no),
  KEY idx_api_cases_project_version (project_id, version_id),
  KEY idx_api_cases_status (execution_status, review_status),
  KEY idx_api_cases_task (source_task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='接口测试用例';

-- -----------------------------------------------------------------------------
-- 功能用例状态变更日志（执行/评审轨迹）
-- -----------------------------------------------------------------------------
CREATE TABLE test_case_status_logs (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  case_id BIGINT NOT NULL COMMENT '功能用例 ID',
  field_name VARCHAR(32) NOT NULL COMMENT '字段：execution_status / review_status',
  old_value VARCHAR(64) DEFAULT NULL COMMENT '旧值',
  new_value VARCHAR(64) NOT NULL COMMENT '新值',
  reason VARCHAR(255) DEFAULT NULL COMMENT '变更原因/说明',
  changed_by BIGINT NOT NULL COMMENT '操作人用户 ID',
  changed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '变更时间',
  PRIMARY KEY (id),
  KEY idx_case_status_logs_case (case_id),
  KEY idx_case_status_logs_time (changed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='功能用例状态日志';

-- -----------------------------------------------------------------------------
-- 功能用例历史快照
-- -----------------------------------------------------------------------------
CREATE TABLE test_case_histories (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  case_id BIGINT NOT NULL COMMENT '功能用例 ID',
  snapshot_json JSON NOT NULL COMMENT '用例内容快照 JSON',
  changed_by BIGINT NOT NULL COMMENT '操作人用户 ID',
  changed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '变更时间',
  change_type VARCHAR(16) NOT NULL COMMENT '变更类型：CREATE/UPDATE/BATCH_UPDATE 等',
  PRIMARY KEY (id),
  KEY idx_case_histories_case (case_id),
  KEY idx_case_histories_time (changed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='功能用例历史';

-- -----------------------------------------------------------------------------
-- 导出记录
-- -----------------------------------------------------------------------------
CREATE TABLE export_records (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  export_no VARCHAR(64) NOT NULL COMMENT '导出业务单号，唯一（展示列可称「编码」）',
  project_id BIGINT NOT NULL COMMENT '项目 ID',
  version_id BIGINT NOT NULL COMMENT '版本 ID',
  format VARCHAR(16) NOT NULL COMMENT '导出格式：md 等',
  scope VARCHAR(16) NOT NULL COMMENT '导出范围：all/filtered/selected 等',
  status VARCHAR(16) NOT NULL COMMENT '状态：QUEUED/RUNNING/SUCCESS/FAILED',
  request_json JSON DEFAULT NULL COMMENT '导出请求参数 JSON（列表中的 exportContent 等可由后端从此解析展示）',
  file_path VARCHAR(500) DEFAULT NULL COMMENT '生成文件路径',
  file_size BIGINT DEFAULT NULL COMMENT '文件大小（字节）',
  error_message TEXT COMMENT '失败原因',
  created_by BIGINT NOT NULL COMMENT '创建人用户 ID',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否 1 是',
  PRIMARY KEY (id),
  UNIQUE KEY uk_export_no (export_no),
  KEY idx_export_version (version_id),
  KEY idx_export_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='导出任务记录';

-- -----------------------------------------------------------------------------
-- 操作审计日志
-- -----------------------------------------------------------------------------
CREATE TABLE operation_logs (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  object_type VARCHAR(32) NOT NULL COMMENT '对象类型：PROJECT/ASSET/TEST_CASE 等',
  object_id BIGINT NOT NULL COMMENT '对象业务 ID',
  action VARCHAR(32) NOT NULL COMMENT '动作：CREATE/UPDATE/DELETE 等',
  before_json JSON DEFAULT NULL COMMENT '变更前快照',
  after_json JSON DEFAULT NULL COMMENT '变更后快照',
  operator_id BIGINT NOT NULL COMMENT '操作人用户 ID',
  operator_name VARCHAR(64) DEFAULT NULL COMMENT '操作人展示名',
  remark VARCHAR(255) DEFAULT NULL COMMENT '备注',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录时间',
  PRIMARY KEY (id),
  KEY idx_logs_object (object_type, object_id),
  KEY idx_logs_operator_time (operator_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='操作日志';

-- -----------------------------------------------------------------------------
-- 初始管理员（密码由后端按 bootstrap 配置校验/升级哈希）
-- -----------------------------------------------------------------------------
INSERT INTO users (username, password_hash, display_name, status, is_deleted)
VALUES ('admin', 'admin123', 'Administrator', 'ACTIVE', 0)
ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP;

-- =============================================================================
-- 结束
-- =============================================================================
