-- =============================================================================
-- AI 测试用例管理平台 · MySQL 8 全量建库脚本（唯一入口，与当前 backend 实体一致）
-- 字符集：utf8mb4 · 执行前请确认无重要数据（含 DROP TABLE）
-- 用法：mysql -u root -p < schema_mysql8_full.sql
-- 库名与 application.yml 一致：ai_testcase_platform
-- 内容：建库、全部业务表、默认 admin 用户、文末全局 Prompt 模板（功能 / 接口 / UI 规划步骤）
-- =============================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS test_case_histories;
DROP TABLE IF EXISTS test_case_status_logs;
DROP TABLE IF EXISTS test_cases;
DROP TABLE IF EXISTS api_test_cases;
DROP TABLE IF EXISTS generation_task_refs;
DROP TABLE IF EXISTS generation_tasks;
DROP TABLE IF EXISTS ui_nl_reports;
DROP TABLE IF EXISTS ui_nl_task_exec_steps;
DROP TABLE IF EXISTS ui_nl_task_steps;
DROP TABLE IF EXISTS ui_nl_tasks;
DROP TABLE IF EXISTS ui_nl_cases;
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
  status VARCHAR(16) NOT NULL COMMENT '状态：PENDING/QUEUED/RUNNING/COMPLETED/FAILED/CANCELLED',
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
-- UI 自然语言用例库
-- -----------------------------------------------------------------------------
CREATE TABLE ui_nl_cases (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  case_no VARCHAR(64) NOT NULL COMMENT '自然语言用例编号，唯一',
  project_id BIGINT NOT NULL COMMENT '项目 ID',
  version_id BIGINT NOT NULL COMMENT '版本 ID',
  title VARCHAR(255) NOT NULL COMMENT '用例标题',
  nl_text LONGTEXT NOT NULL COMMENT '自然语言描述正文',
  precondition TEXT COMMENT '前置条件',
  target_env VARCHAR(64) DEFAULT NULL COMMENT '目标环境标识，如 SIT/UAT',
  base_url VARCHAR(255) DEFAULT NULL COMMENT '目标系统入口地址',
  credential_ref VARCHAR(128) DEFAULT NULL COMMENT '凭据引用（禁止明文密码）',
  status VARCHAR(16) NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED/DISABLED',
  tags_json JSON DEFAULT NULL COMMENT '标签/扩展属性',
  created_by BIGINT NOT NULL COMMENT '创建人用户 ID',
  updated_by BIGINT NOT NULL COMMENT '最后更新人用户 ID',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否 1 是',
  PRIMARY KEY (id),
  UNIQUE KEY uk_ui_nl_cases_case_no (case_no),
  KEY idx_ui_nl_cases_project_version (project_id, version_id),
  KEY idx_ui_nl_cases_status (status),
  KEY idx_ui_nl_cases_title (title(128))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='UI 自然语言用例库';

-- -----------------------------------------------------------------------------
-- UI 自然语言任务
-- -----------------------------------------------------------------------------
CREATE TABLE ui_nl_tasks (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  project_id BIGINT NOT NULL COMMENT '项目 ID',
  version_id BIGINT NOT NULL COMMENT '版本 ID',
  ui_nl_case_id BIGINT NOT NULL COMMENT '关联自然语言用例 ID',
  task_no VARCHAR(64) NOT NULL COMMENT '任务编号，唯一',
  status VARCHAR(16) NOT NULL COMMENT '步骤生成流程：PENDING/QUEUED/PLANNING/READY/FAILED/INTERRUPTED/CANCELLED（不含浏览器执行终态）',
  last_exec_status VARCHAR(16) DEFAULT NULL COMMENT '最近浏览器执行：RUNNING/COMPLETED/FAILED/CANCELLED',
  submitted_by BIGINT NOT NULL COMMENT '提交人用户 ID',
  submitted_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '提交时间',
  plan_started_at DATETIME DEFAULT NULL COMMENT '步骤生成（LLM 规划）开始时间',
  plan_finished_at DATETIME DEFAULT NULL COMMENT '步骤生成结束时间（成功/失败/中断）',
  exec_started_at DATETIME DEFAULT NULL COMMENT '最近一轮浏览器执行开始时间',
  exec_finished_at DATETIME DEFAULT NULL COMMENT '最近一轮浏览器执行结束时间',
  runner_run_id VARCHAR(64) DEFAULT NULL COMMENT 'runner 侧执行 ID',
  model_config_id BIGINT NOT NULL COMMENT '模型配置 ID（来自模型配置管理）',
  prompt_template_id BIGINT NOT NULL COMMENT 'Prompt 模板 ID（来自提示词管理）',
  headless TINYINT NOT NULL DEFAULT 0 COMMENT '是否无头模式：0 否 1 是',
  browser_name VARCHAR(32) NOT NULL DEFAULT 'chromium' COMMENT '浏览器类型',
  model_key VARCHAR(128) DEFAULT NULL COMMENT '规划步骤使用模型标识',
  timeout_seconds INT NOT NULL DEFAULT 600 COMMENT '执行超时时间（秒）',
  result_summary JSON DEFAULT NULL COMMENT '执行结果摘要',
  interrupt_reason VARCHAR(255) DEFAULT NULL COMMENT '中断/取消原因（操作人见 updated_by）',
  error_message TEXT COMMENT '错误信息',
  created_by BIGINT NOT NULL COMMENT '创建人用户 ID',
  updated_by BIGINT NOT NULL COMMENT '最后更新人用户 ID',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否 1 是',
  PRIMARY KEY (id),
  UNIQUE KEY uk_ui_nl_tasks_task_no (task_no),
  UNIQUE KEY uk_ui_nl_tasks_runner_run_id (runner_run_id),
  KEY idx_ui_nl_tasks_status_submitted (status, submitted_at),
  KEY idx_ui_nl_tasks_last_exec_running (last_exec_status, exec_started_at),
  KEY idx_ui_nl_tasks_project_version (project_id, version_id),
  KEY idx_ui_nl_tasks_case (ui_nl_case_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='UI 自然语言任务';

-- -----------------------------------------------------------------------------
-- UI 自然语言任务步骤
-- -----------------------------------------------------------------------------
CREATE TABLE ui_nl_task_steps (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  task_id BIGINT NOT NULL COMMENT '任务 ID',
  step_no INT NOT NULL COMMENT '步骤序号，从 1 开始',
  step_title VARCHAR(255) DEFAULT NULL COMMENT '步骤标题',
  action_type VARCHAR(32) NOT NULL COMMENT '动作类型：CLICK/TYPE/WAIT/ASSERT 等',
  input_value TEXT COMMENT '输入值',
  expect_json JSON DEFAULT NULL COMMENT '断言信息',
  status VARCHAR(16) NOT NULL DEFAULT 'GENERATED' COMMENT '规划侧状态：GENERATED=模型已生成，EDITED=人工已编辑（与执行结果无关；执行见 ui_nl_task_exec_steps）',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否 1 是',
  PRIMARY KEY (id),
  UNIQUE KEY uk_ui_nl_task_step_no (task_id, step_no),
  KEY idx_ui_nl_task_steps_task (task_id),
  KEY idx_ui_nl_task_steps_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='UI 自然语言任务规划步骤';

-- -----------------------------------------------------------------------------
-- UI 自然语言任务执行轨迹（latest-only）
-- -----------------------------------------------------------------------------
CREATE TABLE ui_nl_task_exec_steps (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  task_id BIGINT NOT NULL COMMENT '任务 ID',
  step_no INT NOT NULL COMMENT '步骤序号，从 1 开始',
  step_title VARCHAR(255) DEFAULT NULL COMMENT '步骤标题',
  action_type VARCHAR(32) NOT NULL COMMENT '动作类型：CLICK/TYPE/WAIT/ASSERT 等',
  target_json JSON DEFAULT NULL COMMENT '元素定位信息（selector/text/role 等）',
  input_value TEXT COMMENT '输入值',
  expect_json JSON DEFAULT NULL COMMENT '断言信息',
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING/SUCCESS/FAILED/SKIPPED',
  duration_ms BIGINT DEFAULT NULL COMMENT '步骤耗时（毫秒）',
  error_message TEXT COMMENT '步骤错误信息',
  screenshot_path VARCHAR(500) DEFAULT NULL COMMENT '截图路径',
  started_at DATETIME DEFAULT NULL COMMENT '步骤开始时间',
  finished_at DATETIME DEFAULT NULL COMMENT '步骤结束时间',
  raw_log TEXT COMMENT '步骤原始日志',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否 1 是',
  PRIMARY KEY (id),
  UNIQUE KEY uk_ui_nl_task_exec_step_no (task_id, step_no),
  KEY idx_ui_nl_task_exec_steps_task (task_id),
  KEY idx_ui_nl_task_exec_steps_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='UI 自然语言任务执行轨迹';

-- -----------------------------------------------------------------------------
-- UI 自然语言测试报告
-- -----------------------------------------------------------------------------
CREATE TABLE ui_nl_reports (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  report_no VARCHAR(64) NOT NULL COMMENT '报告编号，唯一',
  task_id BIGINT NOT NULL COMMENT '任务 ID',
  project_id BIGINT NOT NULL COMMENT '项目 ID',
  version_id BIGINT NOT NULL COMMENT '版本 ID',
  status VARCHAR(16) NOT NULL COMMENT '报告状态：SUCCESS/FAILED/CANCELLED',
  total_steps INT NOT NULL DEFAULT 0 COMMENT '总步骤数',
  passed_steps INT NOT NULL DEFAULT 0 COMMENT '成功步骤数',
  failed_steps INT NOT NULL DEFAULT 0 COMMENT '失败步骤数',
  summary TEXT COMMENT '文本摘要',
  artifacts_json JSON DEFAULT NULL COMMENT '附件索引 JSON（截图/trace/video）',
  report_file_path VARCHAR(500) DEFAULT NULL COMMENT '静态 HTML 报告文件路径',
  report_generated_at DATETIME DEFAULT NULL COMMENT '静态 HTML 报告生成时间',
  started_at DATETIME DEFAULT NULL COMMENT '对应任务本轮执行开始时间',
  finished_at DATETIME DEFAULT NULL COMMENT '对应任务本轮执行结束时间',
  created_by BIGINT NOT NULL COMMENT '创建人用户 ID',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否 1 是',
  PRIMARY KEY (id),
  UNIQUE KEY uk_ui_nl_reports_report_no (report_no),
  UNIQUE KEY uk_ui_nl_reports_task (task_id),
  KEY idx_ui_nl_reports_project_version_time (project_id, version_id, created_at),
  KEY idx_ui_nl_reports_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='UI 自然语言测试报告';

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
  status VARCHAR(16) NOT NULL COMMENT '状态：RUNNING/SUCCESS/FAILED',
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

-- -----------------------------------------------------------------------------
-- 可选：全局 Prompt 模板（与 docs/prompts/*.md 系统提示词一致；首次建库写入即可）
-- 若重复执行本全量脚本，会先 DROP 表再建，一般无重复；若需单独重跑本段请先删除同名 name 行
-- UI 自然语言规划步骤：顶层须为 JSON 数组，见 UiNlService.parsePlannedStepsFromJson
-- -----------------------------------------------------------------------------
INSERT INTO prompt_templates (name, scope_type, scope_id, version_no, content, status, remark, created_by, updated_by, is_deleted)
VALUES (
  '功能测试用例-JSON',
  'GLOBAL',
  NULL,
  1,
  '你是一名资深软件功能测试工程师，熟悉黑盒测试与用例设计（等价类、边界值、场景法等）。\n'
  '\n'
  '【任务】\n'
  '根据用户提供的项目/版本背景、需求说明、需求资产正文或摘要、参考版本信息等上下文，设计「功能测试用例」。输出结果将被导入系统的功能用例库（含模块、功能、标题、前置、步骤、测试数据、预期、优先级等字段）。\n'
  '\n'
  '【输出格式（必须严格遵守）】\n'
  '1. 只输出一段合法 JSON 文本，不要 Markdown 标题、不要使用 Markdown 代码围栏、不要任何前言、结语或解释。\n'
  '2. 顶层结构必须是以下两种之一：\n'
  '   - A）JSON 数组：[{...},{...},...]，每个元素是一条用例；\n'
  '   - B）JSON 对象，且包含以下任一键，其值为数组：testCases、cases、caseList、items、data、list。\n'
  '3. 上述数组中每个元素是一个 JSON 对象，表示一条用例。\n'
  '\n'
  '【每条用例的字段】（支持 camelCase 或 snake_case，解析器均识别）\n'
  '- moduleName / module_name / module：模块名（字符串）。\n'
  '- featureName / feature_name / feature：功能或子模块名（字符串）。\n'
  '- title / name / caseTitle：用例标题（字符串，简短可区分场景）。\n'
  '- precondition / pre_condition / preconditions：前置条件（字符串，可选，可省略）。\n'
  '- steps / stepList / step_list / procedure：操作步骤（必填）。可为多行字符串；或为字符串数组（将按 1. 2. 3. 自动编号拼接）。\n'
  '- testData / test_data：测试数据说明（字符串，可选）。\n'
  '- expectedResult / expected_result / expected：预期结果（字符串，必填，可验证）。\n'
  '- priority / level：P0 / P1 / P2 / P3（可选；缺省或非法时系统按 P2）。\n'
  '\n'
  '【数量与质量】\n'
  '- 至少 5 条用例，建议 5～20 条。\n'
  '- 步骤具体可执行，预期与步骤对应、可判定。\n'
  '- 仅根据上下文可合理推断的内容设计用例；不要编造未出现的菜单名、页面名或业务规则。\n'
  '\n'
  '【禁止】\n'
  '- 不要输出 JSON 以外的字符（包括 markdown、注释、自然语言总结）。',
  'ENABLED',
  '与 docs/prompts/功能测试用例_LLM提示词.md 同步；解析：GeneratedTestCaseParser → test_cases',
  1,
  1,
  0
);

INSERT INTO prompt_templates (name, scope_type, scope_id, version_no, content, status, remark, created_by, updated_by, is_deleted)
VALUES (
  '接口测试用例-JSON',
  'GLOBAL',
  NULL,
  1,
  '你是一名资深接口测试工程师，熟悉 RESTful 约定、HTTP 状态码、请求响应结构与常见断言方式。\n'
  '\n'
  '【任务】\n'
  '根据用户提供的接口文档摘要、请求示例、需求资产中的接口描述、错误码说明等上下文，设计「接口测试用例」。输出结果将被导入系统的接口用例库：每条用例包含模块、功能、标题、请求 JSON、预期响应 JSON、断言 JSON、优先级与可选备注。\n'
  '\n'
  '【输出格式（必须严格遵守）】\n'
  '1. 只输出一段合法 JSON 文本，不要 Markdown、不要使用 Markdown 代码围栏、不要任何前言或结语。\n'
  '2. 顶层结构必须是以下两种之一：\n'
  '   - A）JSON 数组：[{...},{...},...]；\n'
  '   - B）JSON 对象，且包含以下任一键的数组：cases、apiCases、testCases。\n'
  '3. 数组中每个元素是一个 JSON 对象，表示一条接口用例。\n'
  '\n'
  '【每条用例的字段】（支持 camelCase 或 snake_case）\n'
  '- moduleName / module_name / module：模块名（字符串）。\n'
  '- featureName / feature_name / feature：接口分组或业务功能名（字符串）。\n'
  '- title / name：用例标题（字符串）。\n'
  '- requestJson / request_json / request：描述一次 HTTP 调用的 JSON 对象。建议含 method（GET/POST 等）、path（可含占位符）、headers（对象）、body（对象或 null）。也可为合法 JSON 字符串。\n'
  '- expectedJson / expected_json / expected：预期响应的 JSON 对象，建议含状态码语义与 body 结构预期。\n'
  '- assertionsJson / assertions_json / assertions：断言定义，JSON 数组或对象；须为合法 JSON。\n'
  '- priority / level：P0～P3。\n'
  '- remark / note：备注（可选）。\n'
  '\n'
  '【数量与质量】\n'
  '- 至少 5 条，建议 5～20 条。\n'
  '- 覆盖主成功路径、典型 4xx/5xx（仅当上下文有依据）、参数校验、鉴权等可推断场景。\n'
  '- requestJson、expectedJson、assertionsJson 必须可被 JSON 解析。\n'
  '\n'
  '【禁止】\n'
  '- 不要编造上下文中未出现的 Base URL；path 与给定上下文一致。\n'
  '- 不要输出 JSON 以外的字符。',
  'ENABLED',
  '与 docs/prompts/接口测试用例_LLM提示词.md 同步；解析：ApiTestCaseParser → api_test_cases',
  1,
  1,
  0
);

INSERT INTO prompt_templates (name, scope_type, scope_id, version_no, content, status, remark, created_by, updated_by, is_deleted)
VALUES (
  'UI自然语言-规划步骤-JSON',
  'GLOBAL',
  NULL,
  1,
  '你是一名资深 UI / 端到端测试设计助手，负责把「自然语言测试意图」拆解为可交由浏览器自动化执行的、按顺序执行的步骤。\n'
  '\n'
  '【输出要求】\n'
  '1. 只输出一段合法 JSON 文本；不要使用 Markdown、不要使用代码围栏、不要任何前言或结语。\n'
  '2. 顶层必须是 JSON 数组 [...]，数组元素为步骤对象。不要输出对象包裹数组（例如不要 {"steps": [...]}）。\n'
  '3. 每个步骤对象须包含：\n'
  '   - description（必填）：本步要执行的操作，用简体中文描述，具体可执行（例如：在地址栏打开某地址、在搜索框输入文本、点击「搜索」按钮）。一步内只描述一类连贯操作，避免堆砌过多动作。\n'
  '   - expected_result 或 expectedResult（可选）：本步完成后界面应达到的状态，简短中文，便于后续自动检查。\n'
  '\n'
  '【设计原则】\n'
  '- 步骤顺序即执行顺序；建议 3～15 步，覆盖主流程；复杂流程可略增。\n'
  '- 若用户描述包含打开页面、输入、点击、断言结果等，应拆成多步，而不是合并成一步模糊描述。\n'
  '- 不要编造用户未提供的具体 URL、账号密码、验证码；入口地址若存在，会在系统提供的上下文中给出（baseUrl），步骤中可写「打开系统首页」等，与上下文一致即可。\n'
  '- 专有名词、URL、英文界面文案可保留原文。\n'
  '\n'
  '【禁止】\n'
  '- 不要输出 JSON 以外的任何字符。\n'
  '- 不要使用顶层对象包裹数组。',
  'ENABLED',
  '与 docs/prompts/UI自然语言_规划步骤_LLM提示词.md 同步；解析：UiNlService.parsePlannedStepsFromJson → ui_nl_task_steps',
  1,
  1,
  0
);

-- =============================================================================
-- 结束
-- =============================================================================
