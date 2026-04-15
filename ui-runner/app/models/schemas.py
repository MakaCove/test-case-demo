from __future__ import annotations

from pydantic import BaseModel, Field, model_validator
from typing import Optional, List


class PlannedStep(BaseModel):
    stepNo: int
    title: Optional[str] = None
    actionType: Optional[str] = None
    inputValue: Optional[str] = None
    expectJson: Optional[str] = None
    targetJson: Optional[str] = None


class RunRequest(BaseModel):
    runId: str = Field(..., min_length=3, max_length=64)
    taskText: str = ""
    plannedSteps: Optional[List[PlannedStep]] = None
    baseUrl: Optional[str] = None
    headless: bool = False
    model: Optional[str] = None
    timeoutSeconds: int = Field(default=600, ge=30, le=7200)

    @model_validator(mode="after")
    def task_or_steps(self) -> RunRequest:
        if not (self.taskText or "").strip() and not self.plannedSteps:
            raise ValueError("taskText or plannedSteps is required")
        return self


class RunResponse(BaseModel):
    accepted: bool
    runnerRunId: str
    message: Optional[str] = None


class StepResult(BaseModel):
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
    runId: str
    status: str
    summary: Optional[str] = None
    errorMessage: Optional[str] = None
    artifactsJson: Optional[str] = None
    steps: List[StepResult] = Field(default_factory=list)
