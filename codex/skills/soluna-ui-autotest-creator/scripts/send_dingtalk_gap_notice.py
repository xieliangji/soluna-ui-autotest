#!/usr/bin/env python3
"""向 DingTalk 自定义机器人发送 Soluna 能力缺口请求。"""

from __future__ import annotations

import argparse
import base64
import hashlib
import hmac
import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path


DEFAULT_WEBHOOK_ENV = "SOLUNA_CODEX_DINGTALK_WEBHOOK"
DEFAULT_SECRET_ENV = "SOLUNA_CODEX_DINGTALK_SECRET"
DEFAULT_TITLE = "Soluna 能力缺口请求"
DEFAULT_DEBUG_WEBHOOK = "https://oapi.dingtalk.com/robot/send?access_token=e68ef40363786b71d0ac834dd51a82eb0a3bfe9fcb22e0d87e0c797df002186a"
DEFAULT_DEBUG_SECRET = "SEC7c49b67a69a0e77e9281217b7e4e45a32ffe88bfc851d364c9117007572b3c59"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    source = parser.add_mutually_exclusive_group()
    source.add_argument("--file", type=Path, help="Markdown/text 能力缺口请求文件。")
    source.add_argument("--message", help="Markdown/text 能力缺口请求正文。")
    parser.add_argument("--title", default=DEFAULT_TITLE, help="DingTalk markdown 标题。")
    parser.add_argument("--webhook", help="DingTalk robot webhook，优先级高于环境变量和内置 debug robot。")
    parser.add_argument("--webhook-env", default=DEFAULT_WEBHOOK_ENV, help="保存 webhook 的环境变量名。")
    parser.add_argument("--secret", help="DingTalk 签名 secret，优先级高于环境变量和内置 debug robot。")
    parser.add_argument("--secret-env", default=DEFAULT_SECRET_ENV, help="保存签名 secret 的环境变量名。")
    parser.add_argument("--no-default-robot", action="store_true", help="禁用内置 Soluna debug robot fallback。")
    parser.add_argument("--at-mobile", action="append", default=[], help="要 @ 的手机号，可重复传入。")
    parser.add_argument("--at-all", action="store_true", help="@ 所有人。")
    parser.add_argument("--timeout", type=float, default=10.0, help="HTTP 超时时间，单位秒。")
    parser.add_argument("--dry-run", action="store_true", help="只打印请求 payload，不发送。")
    return parser.parse_args()


def read_body(args: argparse.Namespace) -> str:
    if args.file:
        return args.file.read_text(encoding="utf-8").strip()
    if args.message:
        return args.message.strip()
    if not sys.stdin.isatty():
        return sys.stdin.read().strip()
    raise SystemExit("请提供 --file、--message，或通过 stdin 传入能力缺口请求。")


def resolve_webhook(args: argparse.Namespace) -> str | None:
    if args.webhook is not None:
        return args.webhook
    env_value = os.environ.get(args.webhook_env)
    if env_value:
        return env_value
    return None if args.no_default_robot else DEFAULT_DEBUG_WEBHOOK


def resolve_secret(args: argparse.Namespace, webhook: str | None) -> str | None:
    if args.secret is not None:
        return args.secret
    env_value = os.environ.get(args.secret_env)
    if env_value:
        return env_value
    if webhook != DEFAULT_DEBUG_WEBHOOK:
        return None
    return None if args.no_default_robot else DEFAULT_DEBUG_SECRET


def resolve_required(value: str | None, env_name: str, label: str, no_default_robot: bool) -> str:
    resolved = value if value is not None else os.environ.get(env_name)
    if not resolved and not no_default_robot and label == "webhook":
        resolved = DEFAULT_DEBUG_WEBHOOK
    if not resolved:
        raise SystemExit(f"{label} 必填。请传入 --{label.lower()}、设置 {env_name}，或去掉 --no-default-robot。")
    return resolved


def sign_webhook(webhook: str, secret: str, timestamp_ms: int) -> str:
    string_to_sign = f"{timestamp_ms}\n{secret}".encode("utf-8")
    digest = hmac.new(secret.encode("utf-8"), string_to_sign, hashlib.sha256).digest()
    signature = urllib.parse.quote_plus(base64.b64encode(digest).decode("utf-8"))
    separator = "&" if "?" in webhook else "?"
    return f"{webhook}{separator}timestamp={timestamp_ms}&sign={signature}"


def build_payload(title: str, body: str, at_mobiles: list[str], at_all: bool) -> dict[str, object]:
    text = f"### {title}\n\n{body}"
    return {
        "msgtype": "markdown",
        "markdown": {
            "title": title,
            "text": text,
        },
        "at": {
            "atMobiles": at_mobiles,
            "isAtAll": at_all,
        },
    }


def masked_webhook(webhook: str | None) -> str:
    if not webhook:
        return "<缺失>"
    parsed = urllib.parse.urlsplit(webhook)
    query = urllib.parse.parse_qs(parsed.query)
    access_token = query.get("access_token", [""])[0]
    if access_token:
        masked = access_token[:4] + "..." + access_token[-4:] if len(access_token) > 8 else "***"
        return urllib.parse.urlunsplit(parsed._replace(query=f"access_token={masked}"))
    return urllib.parse.urlunsplit(parsed._replace(query="<已隐藏>"))


def send_payload(webhook: str, payload: dict[str, object], timeout: float) -> dict[str, object]:
    request = urllib.request.Request(
        webhook,
        data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
        headers={"Content-Type": "application/json; charset=utf-8"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            response_body = response.read().decode("utf-8")
    except urllib.error.HTTPError as err:
        response_body = err.read().decode("utf-8", errors="replace")
        raise SystemExit(f"DingTalk HTTP {err.code}: {response_body}") from err
    except urllib.error.URLError as err:
        raise SystemExit(f"DingTalk 请求失败: {err.reason}") from err

    return json.loads(response_body)


def main() -> int:
    args = parse_args()
    body = read_body(args)
    if not body:
        raise SystemExit("能力缺口请求正文为空。")

    webhook = resolve_webhook(args)
    secret = resolve_secret(args, webhook)
    payload = build_payload(args.title, body, args.at_mobile, args.at_all)

    if args.dry_run:
        print(f"webhook: {masked_webhook(webhook)}")
        print(f"secret: {'已配置' if secret else '未配置'}")
        print(json.dumps(payload, ensure_ascii=False, indent=2))
        return 0

    webhook = resolve_required(args.webhook, args.webhook_env, "webhook", args.no_default_robot)
    if secret:
        webhook = sign_webhook(webhook, secret, int(time.time() * 1000))

    response = send_payload(webhook, payload, args.timeout)
    errcode = response.get("errcode")
    if errcode not in (0, "0", None):
        raise SystemExit(f"DingTalk robot 返回错误: {json.dumps(response, ensure_ascii=False)}")
    print(json.dumps(response, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
