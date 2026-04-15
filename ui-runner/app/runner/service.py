from __future__ import annotations

import asyncio
import json
import os
import re
import shutil
from dataclasses import dataclass, field
from datetime import datetime
from pathlib import Path
from typing import Dict, List, Optional

from browser_use import Agent, Browser, BrowserProfile, ChatOpenAI
from browser_use.agent.views import AgentHistoryList
from browser_use.browser.profile import ViewportSize

from app.models.schemas import PlannedStep, StepResult


def _now_iso() -> str:
    return datetime.now().isoformat(timespec="seconds")


def _expect_text_from_json(expect_json: Optional[str]) -> str:
    if not expect_json or not expect_json.strip():
        return "（无单独预期描述，以本步操作说明为准）"
    raw = expect_json.strip()
    try:
        obj = json.loads(raw)
        if isinstance(obj, dict) and "expected_result" in obj:
            v = obj.get("expected_result")
            return str(v).strip() if v is not None else raw
    except json.JSONDecodeError:
        pass
    return raw


def _env_flag(name: str) -> bool:
    return (os.getenv(name) or "").strip().lower() in ("1", "true", "yes", "on")


def _parse_browser_window_size() -> Optional[tuple[int, int]]:
    """解析 BROWSER_WINDOW_SIZE=1920x1080 或 BROWSER_WINDOW_WIDTH/HEIGHT。"""
    raw = (os.getenv("BROWSER_WINDOW_SIZE") or "").strip().lower().replace("*", "x")
    if "x" in raw:
        a, b = raw.split("x", 1)
        try:
            w, h = int(a.strip()), int(b.strip())
            if w > 0 and h > 0:
                return w, h
        except ValueError:
            pass
    w_s, h_s = os.getenv("BROWSER_WINDOW_WIDTH"), os.getenv("BROWSER_WINDOW_HEIGHT")
    if w_s and h_s:
        try:
            w, h = int(w_s.strip()), int(h_s.strip())
            if w > 0 and h > 0:
                return w, h
        except ValueError:
            pass
    return None


def _build_browser_profile(headless: bool) -> BrowserProfile:
    """Chrome 路径与可选固定窗口；最大化在 Browser() 创建之后处理（见 _fix_browser_window_maximized）。"""
    profile = BrowserProfile(headless=headless)
    chrome_path = (os.getenv("CHROME_PATH") or "").strip()
    if chrome_path:
        profile.executable_path = chrome_path
    dims = _parse_browser_window_size()
    if dims:
        profile.window_size = ViewportSize(width=dims[0], height=dims[1])
    return profile


def _fix_browser_window_maximized(browser: Browser, headless: bool) -> None:
    """
    browser_use 的 Browser() 会用 model_dump 重建 BrowserProfile 并再次 detect_display_configuration，
    把 headed 下的 window_size 填回整屏像素，导致启动参数同时带 --start-maximized 与 --window-size，
    Chrome 往往按后者绘制，看起来「没最大化」。在 Browser实例化后把 window_size 清掉并保证 --start-maximized。
另：默认 window_position 为 (0,0) 时也会带上 --window-position=0,0；从 p.args 里去掉 window-size/position，
    避免与 --start-maximized 一起传入。Windows 上若仍不像最大化，可去掉默认的 --disable-window-activation
    （设 BROWSER_KEEP_DISABLE_WINDOW_ACTIVATION=1 可恢复该默认开关）。
    """
    if headless:
        return
    dims = _parse_browser_window_size()
    if dims is not None:
        return
    if _env_flag("BROWSER_DISABLE_START_MAXIMIZED"):
        return
    p = browser.browser_profile
    p.window_size = None
    p.window_position = None
    args_list = list(p.args) if p.args else []
    args_list = [a for a in args_list if not str(a).startswith("--window-size")]
    args_list = [a for a in args_list if not str(a).startswith("--window-position")]
    if "--start-maximized" not in args_list:
        args_list.append("--start-maximized")
    p.args = args_list
    # browser_use 默认 CHROME_DEFAULT_ARGS 含 --disable-window-activation；在部分环境下会削弱 --start-maximized 效果。
    if not _env_flag("BROWSER_KEEP_DISABLE_WINDOW_ACTIVATION"):
        ign = p.ignore_default_args
        if isinstance(ign, list) and "--disable-window-activation" not in ign:
            p.ignore_default_args = [*ign, "--disable-window-activation"]


def _build_execution_task(task_text: str, planned: Optional[List[PlannedStep]], base_url: Optional[str]) -> str:
    chunks: List[str] = [
        "【语言】全程使用简体中文输出：思考、评估、下一步目标(next_goal)、向用户说明的文字均用中文（URL、代码、英文专有名词可保留原文）。",
        "【窗口】有界面模式下默认使用 Chrome --start-maximized；若配置了 BROWSER_WINDOW_SIZE 则为固定窗口。不要用页面脚本改窗口大小。"
        "禁止用 evaluate 执行 window.moveTo、window.resizeTo、requestFullscreen 或拼接 screen.availWidth/Height 等去「最大化」，在自动化里常无效且模型易写出语法错误（如多余逗号）。"
        "若规划步骤含「窗口最大化」「全屏」类描述：不要写任何窗口相关 JS；用 wait 等待 1～2 秒或直接根据页面已可见判定该步完成并进入下一步。",
        "",
    ]
    t = (task_text or "").strip()
    if t:
        chunks.append(t)
    if planned:
        lines: List[str] = [
            "你必须严格按顺序完成以下步骤，不要跳过。",
            "每步完成后根据「预期」自检，确认达到预期后再执行下一步。",
            "",
        ]
        ordered = sorted(planned, key=lambda x: x.stepNo)
        for p in ordered:
            title = (p.title or "").strip() or f"步骤{p.stepNo}"
            body = (p.inputValue or "").strip() or "（无操作描述）"
            exp = _expect_text_from_json(p.expectJson)
            lines.append(f"第 {p.stepNo} 步 — {title}")
            lines.append(f"操作：{body}")
            lines.append(f"预期：{exp}")
            lines.append("")
        chunks.append("\n".join(lines).rstrip())
    body = "\n\n".join(chunks).strip()
    if not body:
        body = "在浏览器中完成用户描述的任务。\n\n【语言】全程使用简体中文描述步骤与结论。"
    if base_url and base_url.strip():
        body = f"先打开 {base_url.strip()}，然后执行以下任务：\n\n{body}"
    return body


def _history_to_step_results(
    history: AgentHistoryList, run_dir: Path, planned_steps: Optional[List[PlannedStep]] = None
) -> List[StepResult]:
    out: List[StepResult] = []
    if not history or not history.history:
        return out

    planned_expect_map: Dict[int, str] = {}
    planned_body_map: Dict[int, str] = {}
    if planned_steps:
        ordered = sorted(planned_steps, key=lambda x: x.stepNo)
        for idx, p in enumerate(ordered, start=1):
            if p.expectJson and p.expectJson.strip():
                planned_expect_map[idx] = p.expectJson.strip()
            title = (p.title or "").strip() or f"步骤{p.stepNo}"
            body = (p.inputValue or "").strip()
            if body:
                planned_body_map[idx] = body
            elif title:
                planned_body_map[idx] = title

    shots_dir = run_dir / "shots"
    shots_dir.mkdir(parents=True, exist_ok=True)

    for idx, h in enumerate(history.history, start=1):
        from_plan = (planned_body_map.get(idx) or "").strip()
        duration_ms: Optional[int] = None
        if h.metadata:
            duration_ms = int(round(h.metadata.duration_seconds * 1000))

        row_error = next((r.error for r in h.result if r.error), None)
        status = "FAILED" if row_error else "SUCCESS"

        action_type: Optional[str] = None
        input_value: Optional[str] = None
        target_json: Optional[str] = None
        if h.model_output and h.model_output.action:
            acts = h.model_output.action
            try:
                action_type = acts[0].__class__.__name__
            except Exception:  # noqa: BLE001
                action_type = "ACTION"
            try:
                dumped = [a.model_dump(exclude_none=True, mode="json") for a in acts]
                target_json = json.dumps(dumped, ensure_ascii=False)
            except Exception:  # noqa: BLE001
                target_json = None
            ng = (h.model_output.next_goal or "").strip()
            if from_plan:
                input_value = from_plan
            elif ng:
                input_value = ng
            elif target_json:
                input_value = target_json[:4000]

        if not target_json and h.state and h.state.interacted_element:
            try:
                els = []
                for el in h.state.interacted_element:
                    if el is None:
                        els.append(None)
                    else:
                        els.append(el.to_dict())
                target_json = json.dumps(els, ensure_ascii=False)
            except Exception:  # noqa: BLE001
                target_json = None

        if input_value is None and from_plan:
            input_value = from_plan
        elif input_value is None and target_json:
            input_value = target_json[:4000]

        log_parts: List[str] = []
        if h.model_output:
            if h.model_output.evaluation_previous_goal:
                log_parts.append("evaluation: " + str(h.model_output.evaluation_previous_goal))
            if h.model_output.memory:
                log_parts.append("memory: " + str(h.model_output.memory))
            if h.model_output.next_goal:
                log_parts.append("next_goal: " + str(h.model_output.next_goal))
        for r in h.result:
            if r.error:
                log_parts.append("error: " + str(r.error))
            if r.long_term_memory:
                log_parts.append("memory(action): " + str(r.long_term_memory))
            if r.extracted_content:
                log_parts.append("extracted: " + str(r.extracted_content)[:2000])

        screenshot_rel: Optional[str] = None
        sp = h.state.screenshot_path if h.state else None
        if sp:
            src = Path(sp)
            if src.is_file():
                # 须使用 \w（单词字符）；写成 \\w 时「排除反斜杠与字母 w」会把 step_2.png 打成 __._，丢失扩展名
                stem = re.sub(r"[^\w.-]+", "_", src.stem, flags=re.ASCII).strip("._") or f"step_{idx}"
                stem = stem[:80]
                ext = (src.suffix or ".png").lower()
                if ext not in (".png", ".jpg", ".jpeg", ".webp", ".gif"):
                    ext = ".png"
                dest = shots_dir / f"{idx:03d}_{stem}{ext}"
                try:
                    shutil.copy2(src, dest)
                    screenshot_rel = str(dest.relative_to(run_dir)).replace("\\", "/")
                except Exception:  # noqa: BLE001
                    screenshot_rel = str(src).replace("\\", "/")
            else:
                screenshot_rel = str(sp).replace("\\", "/")

        title = f"Agent步骤{idx}"
        if action_type:
            title = f"{title} ({action_type})"

        out.append(
            StepResult(
                stepNo=idx,
                title=title,
                actionType=action_type or "AGENT",
                status=status,
                errorMessage=row_error,
                screenshotPath=screenshot_rel,
                durationMs=duration_ms,
                targetJson=target_json,
                inputValue=input_value,
                expectJson=planned_expect_map.get(idx),
                rawLog="\n".join(log_parts) if log_parts else None,
            )
        )

    return out


def _summary_from_history(history: AgentHistoryList) -> str:
    try:
        fr = history.final_result()
        if fr:
            return str(fr)[:5000]
    except Exception:  # noqa: BLE001
        pass
    try:
        return str(history)[:5000]
    except Exception:  # noqa: BLE001
        return ""


@dataclass
class RunState:
    run_id: str
    status: str = "PENDING"
    summary: Optional[str] = None
    error_message: Optional[str] = None
    artifacts_json: Optional[str] = None
    steps: List[StepResult] = field(default_factory=list)
    task_ref: Optional[asyncio.Task] = None
    """当前执行的 Agent，供 /runs/{id}/cancel 调用 stop() 协作退出。"""
    agent_ref: Optional[Agent] = field(default=None, repr=False)


class RunnerService:
    def __init__(self, base_dir: Path):
        self.base_dir = base_dir
        self.runs_dir = self.base_dir / "runs"
        self.runs_dir.mkdir(parents=True, exist_ok=True)
        self._states: Dict[str, RunState] = {}

    def get_state(self, run_id: str) -> Optional[RunState]:
        return self._states.get(run_id)

    def remember_state(self, state: RunState) -> None:
        self._states[state.run_id] = state

    def load_state_from_disk(self, run_id: str) -> Optional[RunState]:
        path = self.runs_dir / run_id / "result.json"
        if not path.is_file():
            return None
        try:
            data = json.loads(path.read_text(encoding="utf-8"))
        except Exception:  # noqa: BLE001
            return None
        steps_raw = data.get("steps") or []
        steps: List[StepResult] = []
        for item in steps_raw:
            try:
                steps.append(StepResult.model_validate(item))
            except Exception:  # noqa: BLE001
                continue
        return RunState(
            run_id=data.get("runId", run_id),
            status=str(data.get("status", "UNKNOWN")),
            summary=data.get("summary"),
            error_message=data.get("errorMessage"),
            artifacts_json=data.get("artifactsJson"),
            steps=steps,
        )

    async def submit_run(
        self,
        run_id: str,
        task_text: str,
        planned_steps: Optional[List[PlannedStep]],
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
            self._execute(state, task_text, planned_steps, base_url, headless, model, timeout_seconds)
        )
        return state

    async def cancel_run(self, run_id: str) -> None:
        state = self._states.get(run_id)
        if not state:
            return
        # 先通知 browser-use 协作停止（设置 stopped标志），再取消协程；否则仅 cancel 任务可能长时间卡在 agent.run() 内
        if state.agent_ref is not None:
            try:
                state.agent_ref.stop()
            except Exception:  # noqa: BLE001
                pass
        if state.task_ref and not state.task_ref.done():
            state.task_ref.cancel()
        state.status = "CANCELLED"
        state.summary = "manually cancelled"
        self._persist_state(state)

    async def _execute(
        self,
        state: RunState,
        task_text: str,
        planned_steps: Optional[List[PlannedStep]],
        base_url: Optional[str],
        headless: bool,
        model: Optional[str],
        timeout_seconds: int,
    ) -> None:
        state.status = "RUNNING"
        self._persist_state(state)
        run_dir = self.runs_dir / state.run_id
        run_dir.mkdir(parents=True, exist_ok=True)

        task_content = _build_execution_task(task_text, planned_steps, base_url)
        agent: Optional[Agent] = None
        try:
            llm = ChatOpenAI(
                model=(model or os.getenv("QWEN_MODEL", "qwen3.5-plus")).strip(),
                api_key=os.getenv("OPENAI_API_KEY") or os.getenv("DASHSCOPE_API_KEY"),
                base_url=(os.getenv("OPENAI_BASE_URL") or os.getenv("DASHSCOPE_BASE_URL") or "").strip(),
                temperature=0,
            )
            browser_profile = _build_browser_profile(headless)
            browser = Browser(browser_profile=browser_profile)
            _fix_browser_window_maximized(browser, headless)
            agent = Agent(task=task_content, llm=llm, browser=browser)
            state.agent_ref = agent

            history = await asyncio.wait_for(agent.run(), timeout=float(timeout_seconds))
            state.status = "COMPLETED"
            state.summary = _summary_from_history(history) or "completed"
            state.steps = _history_to_step_results(history, run_dir, planned_steps)

            hist_path = run_dir / "agent_history.json"
            try:
                history.save_to_file(str(hist_path))
            except Exception:  # noqa: BLE001
                pass

            hist_rel = str(hist_path.relative_to(run_dir)).replace("\\", "/") if hist_path.is_file() else None
            art: Dict[str, str] = {
                "runDir": str(run_dir).replace("\\", "/"),
                "resultFile": str((run_dir / "result.json")).replace("\\", "/"),
            }
            if hist_rel:
                art["agentHistoryFile"] = hist_rel
            state.artifacts_json = json.dumps(art, ensure_ascii=False)
            self._persist_state(state)
        except asyncio.CancelledError:
            state.status = "CANCELLED"
            state.summary = "manually cancelled"
            hist: Optional[AgentHistoryList] = None
            if agent is not None:
                hist = getattr(agent, "history", None)
            if hist and hist.history:
                state.steps = _history_to_step_results(hist, run_dir, planned_steps)
                try:
                    hist.save_to_file(str(run_dir / "agent_history.json"))
                except Exception:  # noqa: BLE001
                    pass
            if agent is not None:
                try:
                    await agent.close()
                except Exception:  # noqa: BLE001
                    pass
            self._persist_state(state)
            raise
        except Exception as e:  # noqa: BLE001
            state.status = "FAILED"
            state.error_message = str(e)
            state.summary = "runner failed"
            hist: Optional[AgentHistoryList] = None
            if agent is not None:
                hist = getattr(agent, "history", None)
            if hist and hist.history:
                state.steps = _history_to_step_results(hist, run_dir, planned_steps)
                try:
                    hist.save_to_file(str(run_dir / "agent_history.json"))
                except Exception:  # noqa: BLE001
                    pass
                if not state.summary or state.summary == "runner failed":
                    state.summary = (_summary_from_history(hist) or str(e))[:5000]
            else:
                state.steps = [
                    StepResult(
                        stepNo=1,
                        title="执行失败",
                        actionType="ERROR",
                        status="FAILED",
                        errorMessage=str(e),
                        inputValue=None,
                        rawLog=str(e)[:8000],
                    )
                ]
            hist_path = run_dir / "agent_history.json"
            art: Dict[str, str] = {
                "runDir": str(run_dir).replace("\\", "/"),
                "resultFile": str((run_dir / "result.json")).replace("\\", "/"),
            }
            if hist_path.is_file():
                art["agentHistoryFile"] = str(hist_path.relative_to(run_dir)).replace("\\", "/")
            state.artifacts_json = json.dumps(art, ensure_ascii=False)
            self._persist_state(state)
        finally:
            state.agent_ref = None

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
