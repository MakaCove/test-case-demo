from __future__ import annotations

import os
from pathlib import Path

from fastapi import FastAPI, Header, HTTPException

from app.models.schemas import RunRequest, RunResponse, StatusResponse
from app.runner.service import RunnerService

BASE_DIR = Path(__file__).resolve().parents[1]


def _load_env_file(path: Path) -> None:
    if not path.exists():
        return
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        key = key.strip()
        value = value.strip()
        if (value.startswith('"') and value.endswith('"')) or (value.startswith("'") and value.endswith("'")):
            value = value[1:-1]
        os.environ[key] = value


def _bootstrap_env() -> None:
    env_file = BASE_DIR / ".env"
    env_source = ".env"
    if env_file.exists():
        _load_env_file(env_file)
    else:
        env_source = ".env.example"
        _load_env_file(BASE_DIR / ".env.example")

    if os.getenv("DASHSCOPE_API_KEY") and not os.getenv("OPENAI_API_KEY"):
        os.environ["OPENAI_API_KEY"] = os.environ["DASHSCOPE_API_KEY"]
    if os.getenv("DASHSCOPE_BASE_URL") and not os.getenv("OPENAI_BASE_URL"):
        os.environ["OPENAI_BASE_URL"] = os.environ["DASHSCOPE_BASE_URL"]
    if os.getenv("QWEN_MODEL") and not os.getenv("OPENAI_MODEL"):
        os.environ["OPENAI_MODEL"] = os.environ["QWEN_MODEL"]

    key = (os.getenv("OPENAI_API_KEY") or "").strip()
    masked_key = "missing"
    if key:
        if len(key) <= 8:
            masked_key = f"{key[:2]}***"
        else:
            masked_key = f"{key[:4]}***{key[-4:]}"
    print(
        "[runner-config] "
        f"source={env_source} "
        f"model={(os.getenv('OPENAI_MODEL') or os.getenv('QWEN_MODEL') or '').strip()} "
        f"base_url={(os.getenv('OPENAI_BASE_URL') or os.getenv('DASHSCOPE_BASE_URL') or '').strip()} "
        f"api_key={masked_key}"
    )


_bootstrap_env()
service = RunnerService(BASE_DIR)
app = FastAPI(title="UI NL Runner", version="0.1.0")


def _check_auth(authorization: str | None) -> None:
    token = (os.getenv("RUNNER_TOKEN") or "").strip()
    if not token:
        return
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="missing bearer token")
    value = authorization[7:].strip()
    if value != token:
        raise HTTPException(status_code=401, detail="invalid token")


@app.get("/health")
async def health():
    return {"ok": True}


@app.post("/run", response_model=RunResponse)
async def run_task(req: RunRequest, authorization: str | None = Header(default=None)):
    _check_auth(authorization)
    state = await service.submit_run(
        run_id=req.runId,
        task_text=req.taskText,
        base_url=req.baseUrl,
        headless=req.headless,
        model=req.model,
        timeout_seconds=req.timeoutSeconds,
    )
    return RunResponse(accepted=True, runnerRunId=state.run_id, message="accepted")


@app.get("/runs/{run_id}", response_model=StatusResponse)
async def get_status(run_id: str, authorization: str | None = Header(default=None)):
    _check_auth(authorization)
    state = service.get_state(run_id)
    if not state:
        raise HTTPException(status_code=404, detail="run not found")
    return StatusResponse(
        runId=state.run_id,
        status=state.status,
        summary=state.summary,
        errorMessage=state.error_message,
        artifactsJson=state.artifacts_json,
        steps=state.steps,
    )


@app.post("/runs/{run_id}/cancel")
async def cancel_run(run_id: str, authorization: str | None = Header(default=None)):
    _check_auth(authorization)
    await service.cancel_run(run_id)
    return {"ok": True}
