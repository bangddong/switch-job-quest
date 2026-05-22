#!/usr/bin/env python3
"""Clarify-Before-Execute Gate Injector.

UserPromptSubmit hook for orchestrator agent.
Appends clarify-before-execute skill content to every user message
so the model cannot ignore the gate even if system prompt is deprioritized.

Hook: UserPromptSubmit
Input: JSON via stdin (Claude Code hook format)
Output: Modified prompt with <clarify-gate> block appended
"""

import json
import sys
from pathlib import Path


def main() -> None:
    raw = sys.stdin.read().strip()

    # Parse user prompt from hook input JSON
    try:
        data = json.loads(raw)
        prompt = data.get("prompt", "")
    except (json.JSONDecodeError, AttributeError):
        # Pass through unchanged if input is malformed
        print(raw)
        return

    # Locate skill file relative to project root
    script_dir = Path(__file__).parent
    skill_path = script_dir.parent / "skills" / "clarify-before-execute.md"

    if not skill_path.exists():
        # Skill file missing — pass through unchanged
        data_out = data.copy()
        data_out["prompt"] = prompt
        print(json.dumps(data_out, ensure_ascii=False))
        return

    skill_content = skill_path.read_text(encoding="utf-8")

    injected = (
        prompt
        + "\n\n<clarify-gate>\n"
        + skill_content
        + "\n</clarify-gate>"
    )

    data_out = data.copy()
    data_out["prompt"] = injected
    print(json.dumps(data_out, ensure_ascii=False))


if __name__ == "__main__":
    main()
