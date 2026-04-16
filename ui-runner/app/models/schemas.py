"""
与 Java 后端 / FastAPI 路由对齐的请求与响应模型。

命名采用 camelCase 字段名，便于与前端及 UiNl 接口 JSON 直接对应。
"""
from __future__ import annotations

from pydantic import BaseModel, Field, model_validator
from typing import Optional, List


class PlannedStep(BaseModel):
    """单条规划步骤：来自后端的用例步骤，会拼进 Agent 任务正文中按序执行。"""

    stepNo: int
    title: Optional[str] = None
    actionType: Optional[str] = None
    inputValue: Optional[str] = None
    expectJson: Optional[str] = None
    targetJson: Optional[str] = None


class RunRequest(BaseModel):
    """POST /run 请求体：自然语言任务 + 可选步骤列表 + 浏览器与模型参数。"""

    runId: str = Field(..., min_length=3, max_length=64)
    taskText: str = ""
    plannedSteps: Optional[List[PlannedStep]] = None
    baseUrl: Optional[str] = None
    headless: bool = False
    model: Optional[str] = None
    timeoutSeconds: int = Field(default=600, ge=30, le=7200)

    @model_validator(mode="after")
    def task_or_steps(self) -> RunRequest:
        """至少提供 taskText 或 plannedSteps 之一，避免空任务提交给 Agent。"""
        if not (self.taskText or "").strip() and not self.plannedSteps:
            raise ValueError("taskText or plannedSteps is required")
        return self


class RunResponse(BaseModel):
    """POST /run 立即返回：是否受理及 runner 侧 runId（通常与请求 runId 一致）。"""

    accepted: bool
    runnerRunId: str
    message: Optional[str] = None


class StepResult(BaseModel):
    """单步执行结果：与 Agent 历史一条对应，供 GET /runs/{id} 与落盘 result.json 使用。"""

    stepNo: int
    title: Optional[str] = None
    actionType: Optional[str] = None
    status: str = "PENDING"
    errorMessage: Optional[str] = None
    screenshotPath: Optional[str] = None
    durationMs: Optional[int] = None
    targetJson: Optional[str] = None
    inputValue: Optional[str] = None
    expectJson: Optional[str] = None
    rawLog: Optional[str] = None


class StatusResponse(BaseModel):
    """GET /runs/{run_id} 响应：整次运行的状态、摘要、产物索引与步骤列表。"""

    runId: str
    status: str
    summary: Optional[str] = None
    errorMessage: Optional[str] = None
    artifactsJson: Optional[str] = None
    steps: List[StepResult] = Field(default_factory=list)
