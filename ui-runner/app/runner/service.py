from __future__ import annotations

import asyncio
import json
import os
from dataclasses import dataclass, field
from datetime import datetime
from pathlib import Path
from typing import Dict, Optional, List

from browser_use import Agent, Browser, BrowserProfile, ChatOpenAI

from app.models.schemas import StepResult


def _now_iso() -> str:
    return datetime.now().isoformat(timespec="seconds")


@dataclass
class RunState:
    run_id: str
    status: str = "PENDING"
    summary: Optional[str] = None
    error_message: Optional[str] = None
    artifacts_json: Optional[str] = None
    steps: List[StepResult] = field(default_factory=list)
    task_ref: Optional[asyncio.Task] = None


class RunnerService:
    def __init__(self, base_dir: Path):
        self.base_dir = base_dir
        self.runs_dir = self.base_dir / "runs"
        self.runs_dir.mkdir(parents=True, exist_ok=True)
        self._states: Dict[str, RunState] = {}

    def get_state(self, run_id: str) -> Optional[RunState]:
        return self._states.get(run_id)

    async def submit_run(
        self,
        run_id: str,
        task_text: str,
        base_url: Optional[str],
        headless: bool,
        model: Optional[str],
        timeout_seconds: int,
    ) -> RunState:
        state = self._states.get(run_id)
        if state and state.status in {"PENDING", "RUNNING"}:
            return state

        state = RunState(run_id=run_id, status="PENDING")
        self._states[run_id] = state
        state.task_ref = asyncio.create_task(
            self._execute(state, task_text, base_url, headless, model, timeout_seconds)
        )
        return state

    async def cancel_run(self, run_id: str) -> None:
        state = self._states.get(run_id)
        if not state:
            return
        if state.task_ref and not state.task_ref.done():
            state.task_ref.cancel()
            state.status = "CANCELLED"
            state.summary = "manually cancelled"
            self._persist_state(state)

    async def _execute(
        self,
        state: RunState,
        task_text: str,
        base_url: Optional[str],
        headless: bool,
        model: Optional[str],
        timeout_seconds: int,
    ) -> None:
        state.status = "RUNNING"
        self._persist_state(state)
        try:
            run_dir = self.runs_dir / state.run_id
            run_dir.mkdir(parents=True, exist_ok=True)
            task_content = task_text.strip()
            if base_url:
                task_content = f"先打开 {base_url.strip()}，然后执行以下任务：{task_content}"

            llm = ChatOpenAI(
                model=(model or os.getenv("QWEN_MODEL", "qwen3.5-plus")).strip(),
                api_key=os.getenv("OPENAI_API_KEY") or os.getenv("DASHSCOPE_API_KEY"),
                base_url=(os.getenv("OPENAI_BASE_URL") or os.getenv("DASHSCOPE_BASE_URL") or "").strip(),
                temperature=0,
            )
            browser_profile = BrowserProfile(headless=headless)
            chrome_path = (os.getenv("CHROME_PATH") or "").strip()
            if chrome_path:
                browser_profile.executable_path = chrome_path
            browser = Browser(browser_profile=browser_profile)
            agent = Agent(task=task_content, llm=llm, browser=browser)

            result = await asyncio.wait_for(agent.run(), timeout=float(timeout_seconds))
            state.status = "COMPLETED"
            state.summary = str(result)[:5000]
            state.steps = self._build_fake_steps(task_text)
            state.artifacts_json = json.dumps(
                {
                    "runDir": str(run_dir).replace("\\", "/"),
                    "resultFile": str((run_dir / "result.json")).replace("\\", "/"),
                },
                ensure_ascii=False,
            )
            self._persist_state(state)
        except asyncio.CancelledError:
            state.status = "CANCELLED"
            state.summary = "cancelled"
            self._persist_state(state)
            raise
        except Exception as e:  # noqa: BLE001
            state.status = "FAILED"
            state.error_message = str(e)
            state.summary = "runner failed"
            state.steps = self._build_fake_steps(task_text, failed=True, error=str(e))
            self._persist_state(state)

    def _build_fake_steps(self, task_text: str, failed: bool = False, error: Optional[str] = None) -> List[StepResult]:
        lines = [x.strip() for x in task_text.splitlines() if x.strip()]
        if not lines:
            lines = [task_text.strip()]
        out: List[StepResult] = []
        for idx, line in enumerate(lines, start=1):
            out.append(
                StepResult(
                    stepNo=idx,
                    title=f"步骤{idx}",
                    actionType="PLAN",
                    status="FAILED" if failed and idx == len(lines) else ("SUCCESS" if not failed else "PENDING"),
                    errorMessage=error if failed and idx == len(lines) else None,
                    inputValue=line,
                    rawLog=f"[{_now_iso()}] {line}",
                )
            )
        return out

    def _persist_state(self, state: RunState) -> None:
        run_dir = self.runs_dir / state.run_id
        run_dir.mkdir(parents=True, exist_ok=True)
        payload = {
            "runId": state.run_id,
            "status": state.status,
            "summary": state.summary,
            "errorMessage": state.error_message,
            "artifactsJson": state.artifacts_json,
            "steps": [x.model_dump() for x in state.steps],
            "updatedAt": _now_iso(),
        }
        (run_dir / "result.json").write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
