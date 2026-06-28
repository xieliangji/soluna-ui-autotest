#!/usr/bin/env python3
"""从模板创建最小 Soluna UI autotest asset project。"""

from __future__ import annotations

import argparse
import re
from pathlib import Path


SCRIPT_DIR = Path(__file__).resolve().parent
SKILL_DIR = SCRIPT_DIR.parent
TEMPLATE_ROOT = SKILL_DIR / "assets" / "templates" / "asset-project"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", required=True, help="要创建 asset project 的目录。")
    parser.add_argument("--project-id", required=True, help="稳定的 asset project id。")
    parser.add_argument("--app-id", required=True, help="目标移动 App 的 package 或 bundle id。")
    parser.add_argument("--app-name", required=True, help="可读的 App 名称。")
    parser.add_argument(
        "--product-model",
        help="报告和 DingTalk 通知展示的产品型号或公共 App 名称，默认使用 --app-name。",
    )
    parser.add_argument(
        "--platform",
        required=True,
        choices=("android", "ios"),
        help="要生成 starter 资产的平台。",
    )
    parser.add_argument("--udid", help="单平台项目的设备 UDID。")
    parser.add_argument("--runner-min-version", default="0.1.0", help="最低 Soluna runner 版本。")
    parser.add_argument("--force", action="store_true", help="覆盖已经存在的文件。")
    return parser.parse_args()


def validate_identifier(value: str, label: str) -> None:
    if not re.fullmatch(r"[A-Za-z0-9_.-]+", value):
        raise SystemExit(f"{label} 只能包含字母、数字、点、下划线和连字符: {value}")


def render(text: str, values: dict[str, str]) -> str:
    for key, value in values.items():
        text = text.replace("{{" + key + "}}", value)
        text = text.replace("__" + key + "__", value)
    return text


def write_rendered_files(output: Path, values: dict[str, str], force: bool) -> list[Path]:
    if not TEMPLATE_ROOT.is_dir():
        raise SystemExit(f"模板根目录不存在: {TEMPLATE_ROOT}")

    written: list[Path] = []
    for template in sorted(TEMPLATE_ROOT.rglob("*.tpl")):
        relative = template.relative_to(TEMPLATE_ROOT)
        rendered_relative = Path(render(str(relative), values).removesuffix(".tpl"))
        target = output / rendered_relative
        if target.exists() and not force:
            raise SystemExit(f"文件已存在，未指定 --force，拒绝覆盖: {target}")
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(render(template.read_text(encoding="utf-8"), values), encoding="utf-8")
        written.append(target)
    return written


def create_empty_directories(output: Path, values: dict[str, str]) -> None:
    app_root = output / "apps" / values["APP_ID"]
    for relative in (
        Path("plans") / "debug",
        Path("plans") / "device",
        Path("cases") / "device" / "common",
        Path("data") / "device",
        Path("elements") / "device",
    ):
        (app_root / relative).mkdir(parents=True, exist_ok=True)


def main() -> int:
    args = parse_args()
    validate_identifier(args.project_id, "--project-id")
    validate_identifier(args.app_id, "--app-id")

    primary_platform = args.platform
    primary_udid = args.udid or f"CHANGE_ME_{primary_platform.upper()}_UDID"
    platforms_yaml = f"      - {primary_platform}"

    values = {
        "PROJECT_ID": args.project_id,
        "APP_ID": args.app_id,
        "APP_NAME": args.app_name,
        "PRODUCT_MODEL": args.product_model or args.app_name,
        "PLATFORM": primary_platform,
        "UDID": primary_udid,
        "RUNNER_MIN_VERSION": args.runner_min_version,
        "PLATFORMS_YAML": platforms_yaml,
    }

    output = Path(args.output).expanduser().resolve()
    written = write_rendered_files(output, values, args.force)
    create_empty_directories(output, values)
    plan = output / "apps" / args.app_id / "plans" / "common" / f"{primary_platform}-smoke.yaml"

    print(f"已创建 Soluna asset project: {output}")
    print(f"写入文件数: {len(written)}")
    print()
    print("后续步骤:")
    print(f"  1. 编辑设备配置: {output / 'devices' / primary_platform / (primary_udid + '.yaml')}")
    print("  2. 如需上传产物，将 artifacts/*.template.yaml 复制为 *.local.yaml 并在 plan 中配置 artifactStore。")
    print(f"  3. 运行并触发 schema/policy/reference 校验: soluna run {plan} --run-id {args.project_id}-smoke-001")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
