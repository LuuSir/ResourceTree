"""Publish a verified, non-debuggable APK. Credentials remain in the Git helper and memory."""
import argparse
import hashlib
import json
import os
import pathlib
import re
import subprocess
import urllib.error
import urllib.parse
import urllib.request
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
CERTIFICATE = "3afc7881c8be9742b3075ce0380b55d7338d86264bfba223f935cfe0aa586b4f"


def run(command, **kwargs):
    result = subprocess.run(command, cwd=ROOT, capture_output=True, text=True, encoding="utf-8", errors="replace", **kwargs)
    if result.returncode:
        raise RuntimeError(f"Command failed: {pathlib.Path(command[0]).name} (exit {result.returncode})")
    return result.stdout


def verify_apk(apk, build_tools, tag):
    with zipfile.ZipFile(apk) as archive:
        if archive.testzip() is not None:
            raise RuntimeError("APK ZIP integrity verification failed")
    aapt = build_tools / ("aapt.exe" if os.name == "nt" else "aapt")
    manifest = run([str(aapt), "dump", "badging", str(apk)])
    if "application-debuggable" in manifest:
        raise RuntimeError("Refusing to publish a debuggable APK; build assembleRelease")
    package = re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", manifest)
    if package is None or package[1] != "com.example.resouretree" or package[3] != tag.removeprefix("v"):
        raise RuntimeError("APK package/version does not match this release")
    java_home = os.environ.get("JAVA_HOME")
    java = str(pathlib.Path(java_home) / "bin" / ("java.exe" if os.name == "nt" else "java")) if java_home else "java"
    signature = run([java, "-jar", str(build_tools / "lib/apksigner.jar"), "verify", "--print-certs", str(apk)])
    if f"certificate SHA-256 digest: {CERTIFICATE}" not in signature:
        raise RuntimeError("APK signing certificate differs from existing releases")
    return {"package": package[1], "versionCode": int(package[2]), "versionName": package[3],
            "sha256": hashlib.sha256(apk.read_bytes()).hexdigest(), "size": apk.stat().st_size}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--tag", required=True)
    parser.add_argument("--apk", required=True, type=pathlib.Path)
    parser.add_argument("--build-tools", required=True, type=pathlib.Path)
    parser.add_argument("--notes", type=pathlib.Path)
    parser.add_argument("--check-only", action="store_true")
    parser.add_argument("--direct-git", action="store_true", help="Ignore Git HTTP proxy for this invocation only")
    args = parser.parse_args()
    if not re.fullmatch(r"v\d+\.\d+(?:\.\d+)?", args.tag):
        raise RuntimeError("Invalid release tag")
    metadata = verify_apk(args.apk.resolve(), args.build_tools.resolve(), args.tag)
    print(json.dumps(metadata), flush=True)
    if args.check_only:
        return
    if args.notes is None:
        raise RuntimeError("Release notes are required")
    git = ["git"] + (["-c", "http.proxy=", "-c", "https.proxy="] if args.direct_git else [])
    commit = run(git + ["rev-parse", "HEAD"]).strip()
    refs = run(git + ["for-each-ref", "--format=%(refname)", "refs/heads/main", "refs/tags"]).splitlines()
    objects = run(git + ["rev-list", "--objects"] + refs)
    if "ResourceTree_公众号推文.docx" in objects:
        raise RuntimeError("Private manuscript remains in publishable Git history")
    remote = run(git + ["ls-remote", "origin", "refs/heads/main", f"refs/tags/{args.tag}", f"refs/tags/{args.tag}^{{}}"])
    remote_refs = dict((ref, sha) for sha, ref in (line.split("\t", 1) for line in remote.splitlines()))
    tag_commit = remote_refs.get(f"refs/tags/{args.tag}^{{}}", remote_refs.get(f"refs/tags/{args.tag}"))
    if remote_refs.get("refs/heads/main") != commit or tag_commit != commit:
        raise RuntimeError("Remote main or tag does not point to this release commit")
    credential = dict(line.split("=", 1) for line in run(git + ["credential", "fill"], input="protocol=https\nhost=github.com\n\n").splitlines() if "=" in line)
    headers = {"Authorization": "Bearer " + credential["password"], "Accept": "application/vnd.github+json",
               "User-Agent": "ResourceTree-release", "X-GitHub-Api-Version": "2022-11-28"}
    api = "https://api.github.com/repos/LuuSir/ResourceTree"

    def call(url, method="GET", data=None, binary=False):
        request_headers = dict(headers)
        if data is not None:
            request_headers["Content-Type"] = "application/octet-stream" if binary else "application/json"
            if not binary:
                data = json.dumps(data, ensure_ascii=False).encode("utf-8")
        try:
            with urllib.request.urlopen(urllib.request.Request(url, data=data, headers=request_headers, method=method), timeout=60) as response:
                return json.load(response)
        except urllib.error.HTTPError as error:
            raise RuntimeError(f"GitHub {method} failed with HTTP {error.code}") from None

    release = next((r for r in call(api + "/releases") if r["tag_name"] == args.tag), None)
    if release is not None and not release["draft"]:
        raise RuntimeError("Release is already published; refusing to alter it")
    if release is None:
        release = call(api + "/releases", "POST", {"tag_name": args.tag, "target_commitish": commit,
            "name": f"ResourceTree {args.tag}", "body": args.notes.read_text(encoding="utf-8"), "draft": True, "prerelease": False})
    name = f"ResourceTree-{args.tag}.apk"
    assets = [(name, args.apk.read_bytes()), (name + ".sha256", f"{metadata['sha256']}  {name}\n".encode("ascii"))]
    for asset_name, data in assets:
        asset = next((a for a in release.get("assets", []) if a["name"] == asset_name), None)
        if asset is None:
            url = release["upload_url"].split("{", 1)[0] + "?name=" + urllib.parse.quote(asset_name)
            asset = call(url, "POST", data, binary=True)
        if asset["size"] != len(data) or asset.get("digest") != "sha256:" + hashlib.sha256(data).hexdigest():
            raise RuntimeError("Uploaded asset size or digest mismatch")
    published = call(api + f"/releases/{release['id']}", "PATCH", {"draft": False, "make_latest": "true"})
    if published["draft"]:
        raise RuntimeError("Publication was not confirmed")
    print(published["html_url"], flush=True)


if __name__ == "__main__":
    main()
