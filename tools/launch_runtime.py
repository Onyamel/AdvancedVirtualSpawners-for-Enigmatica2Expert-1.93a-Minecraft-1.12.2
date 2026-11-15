"""Запуск интеграционных проверок в отдельном профиле Windows/Prism."""
import argparse
import json
import os
import shutil
import stat
import subprocess
from pathlib import Path
from zipfile import ZipFile

project = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--instance", type=Path, required=True)
parser.add_argument("--java-home", type=Path, required=True)
parser.add_argument("--prism", type=Path, default=Path(os.environ["APPDATA"]) / "PrismLauncher")
parser.add_argument("--run-dir", type=Path)
parser.add_argument("--server-core", action="store_true", help="Тест выделенного сервера Cleanroom с основными модами E2E")
args = parser.parse_args()
source = args.instance.resolve()
if not (source / "mods").is_dir():
    source /= "minecraft"
if not (source / "mods").is_dir():
    parser.error("В папке сборки нет minecraft/mods")
source = source.resolve()
default_run = "build/cleanroom-server-core" if args.server_core else "run-e2e"
requested_run = args.run_dir or project / default_run
run = requested_run.resolve()
if run == source or source in run.parents or run in source.parents:
    parser.error("Используйте отдельный каталог для тестового профиля")

def redirected(path):
    return path.is_symlink() or bool(
        path.exists() and getattr(path.lstat(), "st_file_attributes", 0) & stat.FILE_ATTRIBUTE_REPARSE_POINT)

if redirected(requested_run):
    parser.error("Тестовый профиль должен быть обычным каталогом")
writable = ("mods", "config", "scripts", "resources", "structures", "patchouli_books", "natives", "logs",
            "saves", "avs-runtime", "server.properties", "options.txt", "process.json", "launch-output.log",
            ".avs-runtime", ".avs-auto-stop")
for name in writable:
    target = run / name
    if redirected(target):
        parser.error(f"Тестовый профиль содержит перенаправление: {target}")
    if target.is_dir():
        for parent, dirs, files in os.walk(target):
            if any(redirected(Path(parent) / child) for child in dirs + files):
                parser.error(f"Тестовый профиль содержит перенаправление внутри {target}")
if run.exists() and not (run / ".avs-runtime").is_file():
    clean_server_files = {"libraries", "cleanroom-0.6.11-alpha.jar", "minecraft_server.1.12.2.jar", "eula.txt",
                          "installer.log", "cleanroom-0.6.11-alpha-installer.jar"}
    if any(not args.server_core or entry.name not in clean_server_files for entry in run.iterdir()):
        parser.error("Используйте пустой каталог, чистую установку Cleanroom или профиль, созданный этим скриптом")
if args.server_core:
    engine = run / "cleanroom-0.6.11-alpha.jar"
    eula = run / "eula.txt"
    if not engine.is_file():
        parser.error("Установите Cleanroom 0.6.11-alpha в отдельный --run-dir")
    if not eula.is_file() or "eula=true" not in eula.read_text(encoding="utf-8").splitlines():
        parser.error("Для запуска выделенного сервера нужен eula.txt с eula=true в тестовом профиле")
run.mkdir(parents=True, exist_ok=True)
(run / "mods").mkdir(exist_ok=True)
(run / ".avs-runtime").touch()
if args.server_core:
    shutil.copytree(source / "config", run / "config", dirs_exist_ok=True, ignore=shutil.ignore_patterns("user_recipes.xml"))
    prefixes = ("enderio-1.12.2-", "endercore-", "draconic-evolution-", "brandonscore-", "redstoneflux-",
                "appliedenergistics2-", "cofhcore-", "cofhworld-", "thermalfoundation-", "thermaldynamics-",
                "codechickenlib-", "+fugue-", "-red-core-", "scalar legacy-")
    for jar in (source / "mods").glob("*.jar"):
        if jar.name.lower().startswith(prefixes):
            shutil.copy2(jar, run / "mods" / jar.name)
else:
    for name in ("mods", "config", "scripts", "resources", "structures", "patchouli_books"):
        if (source / name).is_dir():
            shutil.copytree(source / name, run / name, dirs_exist_ok=True)
release = "1.1.0"
for previous in ("0.1.0", "1.0.0", "1.0.1"):
    for suffix in (".jar", "-runtime-checks.jar"):
        (run / "mods" / f"advancedvirtualspawners-{previous}{suffix}").unlink(missing_ok=True)
for jar in (f"advancedvirtualspawners-{release}.jar", f"advancedvirtualspawners-{release}-runtime-checks.jar"):
    shutil.copy2(project / "build/libs" / jar, run / "mods" / jar)

def launch(command):
    with (run / "launch-output.log").open("wb") as output:
        process = subprocess.Popen(command, cwd=run, stdout=output, stderr=subprocess.STDOUT,
                                   creationflags=subprocess.CREATE_NO_WINDOW)
    (run / "process.json").write_text(json.dumps({"pid": process.pid}), encoding="utf-8")
    print(f"PID {process.pid}. Журнал: {run / 'launch-output.log'}")

java = str(args.java_home.resolve() / "bin/java.exe")
if args.server_core:
    (run / ".avs-auto-stop").touch()
    (run / "server.properties").write_text(
        "server-ip=127.0.0.1\nserver-port=0\nonline-mode=false\nlevel-type=FLAT\n"
        "level-name=avs-runtime\ngamemode=1\nview-distance=4\nenable-query=false\n", encoding="ascii")
    launch([java, "-Xmx3G", "-Dfile.encoding=UTF-8", "-Davs.test=true", "-jar", engine.name, "nogui"])
    raise SystemExit

for name, enabled, disabled in (
    ("splash.properties", b"enabled=true", b"enabled=false"),
    ("Universal Tweaks - Tweaks.cfg", b'B:"LAN Server Properties"=true', b'B:"LAN Server Properties"=false'),
):
    config = run / "config" / name
    if config.is_file():
        config.write_bytes(config.read_bytes().replace(enabled, disabled))
(run / "options.txt").write_text("lang:ru_ru\nguiScale:3\nrenderDistance:4\nmaxFps:30\nfullscreen:false\npauseOnLostFocus:false\nsoundCategory_master:0.0\n", encoding="utf-8")

natives = run / "natives"
natives.mkdir(exist_ok=True)
classpath = []
for component in ("net.minecraft/1.12.2", "org.lwjgl/2.9.4-nightly-20150209", "net.minecraftforge/14.23.5.2860"):
    meta = json.loads((args.prism / "meta" / (component + ".json")).read_text(encoding="utf-8"))
    for library in meta.get("libraries", []) + ([meta["mainJar"]] if "mainJar" in meta else []):
        rules = library.get("rules", [])
        allowed = not rules
        for rule in rules:
            if rule.get("os", {}).get("name") in (None, "windows"):
                allowed = rule["action"] == "allow"
        if not allowed:
            continue
        group, artifact, version, *classifier = library["name"].split(":")
        base = args.prism / "libraries" / group.replace(".", "/") / artifact / version
        prefix = artifact + "-" + version
        windows = library.get("natives", {}).get("windows")
        if windows:
            with ZipFile(base / (prefix + "-" + windows + ".jar")) as archive:
                for entry in archive.namelist():
                    if entry.lower().endswith(".dll"):
                        (natives / Path(entry).name).write_bytes(archive.read(entry))
        elif "artifact" in library.get("downloads", {}):
            jar = base / (prefix + ("-" + classifier[0] if classifier else "") + ".jar")
            if not jar.is_file():
                raise FileNotFoundError(jar)
            if str(jar) not in classpath:
                classpath.append(str(jar))

launch([
    java, "-Xms1G", "-Xmx7G", "-Dfile.encoding=UTF-8",
    "-Davs.test=true", "-Djava.library.path=" + str(natives), "-cp", os.pathsep.join(classpath),
    "net.minecraft.launchwrapper.Launch", "--tweakClass", "net.minecraftforge.fml.common.launcher.FMLTweaker",
    "--username", "AVSRuntime", "--uuid", "5790e3f4d6bd4d95ad4c2e8e59173eee", "--accessToken", "0",
    "--userType", "mojang", "--version", "1.12.2", "--versionType", "release",
    "--gameDir", str(run), "--assetsDir", str(args.prism / "assets"), "--assetIndex", "1.12",
    "--width", "1100", "--height", "780",
])
