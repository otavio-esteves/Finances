#!/usr/bin/env python3
"""Inspect the built APKs, including release dependency manifests. Does not install or read app data."""
import argparse
import os
from pathlib import Path
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = "br.com.otavioesteves.finances"


def find_aapt2():
    sdk = os.environ.get("ANDROID_SDK_ROOT") or os.environ.get("ANDROID_HOME")
    if not sdk:
        properties = ROOT / "local.properties"
        if properties.is_file():
            sdk = next((line.partition("=")[2].strip() for line in properties.read_text().splitlines()
                        if line.startswith("sdk.dir=")), None)
    if sdk:
        candidates = list((Path(sdk) / "build-tools").glob("*/aapt2"))
        if candidates:
            return max(candidates, key=lambda p: tuple(int(n) for n in re.findall(r"\d+", p.parent.name)))
    raise RuntimeError("aapt2 não encontrado. Informe --aapt2 ou ANDROID_SDK_ROOT.")


def verify_exclusions(tree, section_paths):
    nodes, stack = [], []
    for line in tree.splitlines():
        element = re.match(r"^(\s*)E: ([\w-]+)", line)
        if element:
            indent = len(element.group(1))
            while stack and stack[-1][0] >= indent:
                stack.pop()
            node = {"path": tuple(item[1]["name"] for item in stack) + (element.group(2),),
                    "name": element.group(2), "attributes": {}}
            nodes.append(node)
            stack.append((indent, node))
        elif stack:
            attribute = re.search(r'A: (domain|path)="([^"]*)"', line)
            if attribute:
                stack[-1][1]["attributes"][attribute.group(1)] = attribute.group(2)
    if any(node["name"] == "include" for node in nodes):
        raise RuntimeError("Regras de backup contêm inclusão explícita de dados.")
    required = {"root", "file", "database", "sharedpref", "external"}
    for section in section_paths:
        if sum(node["path"] == section for node in nodes) != 1:
            raise RuntimeError(f"Seção de exclusão ausente ou repetida: {'/'.join(section)}")
        excluded = {node["attributes"].get("domain") for node in nodes
                    if node["path"] == section + ("exclude",) and node["attributes"].get("path") == "."}
        if not required.issubset(excluded):
            raise RuntimeError(f"Exclusões de backup incompletas em {'/'.join(section)}: {sorted(required - excluded)}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--aapt2", type=Path)
    parser.add_argument("apks", type=Path, nargs="*")
    args = parser.parse_args()
    aapt = args.aapt2 or find_aapt2()
    apks = args.apks or [ROOT / f"app/build/outputs/apk/{flavor}/{build}/app-{flavor}-{build}{'-unsigned' if build == 'release' else ''}.apk"
                        for flavor in ("standalone", "play") for build in ("debug", "release")]
    for apk in apks:
        if not apk.is_file():
            raise RuntimeError(f"APK ausente: {apk}. Execute os builds dos dois flavors.")
        def dump(*arguments):
            return subprocess.check_output([str(aapt), "dump", *arguments, str(apk)], text=True, stderr=subprocess.STDOUT)
        badging = dump("badging")
        package = re.search(r"package: name='([^']+)'", badging)
        if not package or package.group(1) != PACKAGE:
            raise RuntimeError(f"{apk.name}: ID inesperado; refaça o build sem isolatedDeviceValidation.")
        permissions = set(re.findall(r"uses-permission(?:-sdk-\d+)?: name='([^']+)'", dump("permissions")))
        allowed = {"android.permission.POST_NOTIFICATIONS", "android.permission.RECEIVE_BOOT_COMPLETED",
                   f"{PACKAGE}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"}
        if not {"android.permission.POST_NOTIFICATIONS", "android.permission.RECEIVE_BOOT_COMPLETED"}.issubset(permissions):
            raise RuntimeError(f"{apk.name}: configuração de permissões prevista não comprovada.")
        if permissions - allowed:
            raise RuntimeError(f"{apk.name}: permissões inesperadas: {sorted(permissions - allowed)}")
        manifest = dump("xmltree", "--file", "AndroidManifest.xml")
        if not re.search(r"android:allowBackup\([^\n]+\)=(?:false|\(type 0x12\)0x0)\b", manifest):
            raise RuntimeError(f"{apk.name}: allowBackup=false não comprovado no APK.")
        resources = dump("resources")
        for attribute, sections in (
            ("fullBackupContent", [("full-backup-content",)]),
            ("dataExtractionRules", [("data-extraction-rules", "cloud-backup"),
                                     ("data-extraction-rules", "device-transfer")]),
        ):
            resource_id = re.search(rf"android:{attribute}\([^\n]+\)=@(0x[0-9a-fA-F]+)", manifest)
            if not resource_id:
                raise RuntimeError(f"{apk.name}: referência explícita {attribute} ausente.")
            block = re.search(rf"(?ms)^\s*resource {resource_id.group(1)}\b(.*?)(?=^\s*resource |\Z)", resources)
            paths = re.findall(r"\(file\)\s+(\S+)\s+type=XML", block.group(1)) if block else []
            if not paths:
                raise RuntimeError(f"{apk.name}: XML de {attribute} não encontrado no APK.")
            for path in paths:
                verify_exclusions(dump("xmltree", "--file", path), sections)
        print(f"OK {apk.relative_to(ROOT) if apk.is_relative_to(ROOT) else apk}: ID normal, permissões previstas, backup Android desabilitado")


if __name__ == "__main__":
    try:
        main()
    except (RuntimeError, OSError, subprocess.CalledProcessError) as error:
        print(f"FALHA: {error}", file=sys.stderr)
        sys.exit(1)
