package com.testcase.backend.common;

/**
 * 全局状态常量词典：
 * <p>
 * - 数据库存储与接口协议统一使用英文枚举；
 * - 前端负责将英文枚举映射为中文展示；
 * - 不同域允许同名值（如 PENDING），但语义可能不同，使用时请按域引用常量。
 */
public final class StatusConstants {
    private StatusConstants() {
    }

    public static final class Switch {
        public static final String ENABLED = "ENABLED";
        public static final String DISABLED = "DISABLED";

        private Switch() {
        }
    }

    public static final class Version {
        public static final String DRAFT = "DRAFT";
        public static final String PUBLISHED = "PUBLISHED";

        private Version() {
        }
    }

    public static final class GenerationTask {
        public static final String PENDING = "PENDING";
        public static final String QUEUED = "QUEUED";
        public static final String RUNNING = "RUNNING";
        public static final String COMPLETED = "COMPLETED";
        public static final String FAILED = "FAILED";
        public static final String CANCELLED = "CANCELLED";

        private GenerationTask() {
        }
    }

    public static final class Export {
        public static final String RUNNING = "RUNNING";
        public static final String SUCCESS = "SUCCESS";
        public static final String FAILED = "FAILED";

        private Export() {
        }
    }

    public static final class CaseReview {
        public static final String PENDING = "PENDING";
        public static final String APPROVED = "APPROVED";
        public static final String REJECTED = "REJECTED";

        private CaseReview() {
        }
    }

    public static final class CaseExecution {
        public static final String NOT_EXECUTED = "NOT_EXECUTED";
        public static final String EXECUTED = "EXECUTED";
        public static final String FAILED = "FAILED";

        private CaseExecution() {
        }
    }

    public static final class UiNlTask {
        /** 规划状态 */
        public static final String PENDING = "PENDING";
        public static final String QUEUED = "QUEUED";
        public static final String PLANNING = "PLANNING";
        public static final String READY = "READY";
        public static final String FAILED = "FAILED";
        public static final String INTERRUPTED = "INTERRUPTED";
        public static final String CANCELLED = "CANCELLED";

        /** 最近执行状态 */
        public static final String RUNNING = "RUNNING";
        public static final String COMPLETED = "COMPLETED";

        private UiNlTask() {
        }
    }
}
