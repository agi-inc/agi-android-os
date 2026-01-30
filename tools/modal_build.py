"""
Modal build script for AGI-Android OS.

Uses ephemeral disk (not volume) because AOSP has millions of files
and Modal volumes have a 500k inode limit.

Strategy:
1. Sync AOSP to ephemeral disk (container-local, ~2 hours)
2. Apply AGI components
3. Build (~4-6 hours)
4. Upload artifacts to volume

Usage:
    # Full build (sync + compile in one run, ~6-8 hours total)
    modal run --env research-android-os tools/modal_build.py --action fullbuild

    # Check artifacts from previous build
    modal run --env research-android-os tools/modal_build.py --action status

    # Download system.img
    modal run --env research-android-os tools/modal_build.py --action download
"""

import modal
import os

app = modal.App("agi-android-os-builder")

# Small volume for build artifacts only (system.img, etc.)
artifacts_volume = modal.Volume.from_name("agi-os-artifacts", create_if_missing=True)

# Build image with AOSP dependencies and large ephemeral disk
aosp_image = (
    modal.Image.from_registry("ubuntu:20.04", add_python="3.11")
    .run_commands(
        "apt-get update",
        "DEBIAN_FRONTEND=noninteractive apt-get install -y git-core gnupg flex bison build-essential zip curl zlib1g-dev gcc-multilib g++-multilib libc6-dev-i386 libncurses5 lib32ncurses5-dev x11proto-core-dev libx11-dev lib32z1-dev libgl1-mesa-dev libxml2-utils xsltproc unzip fontconfig openjdk-11-jdk rsync bc ccache lz4 wget pv",
        "curl https://storage.googleapis.com/git-repo-downloads/repo > /usr/local/bin/repo",
        "chmod a+x /usr/local/bin/repo",
        "git config --global user.email 'build@agi.inc'",
        "git config --global user.name 'AGI Builder'",
        "git config --global color.ui false",
    )
    .env({
        "JAVA_HOME": "/usr/lib/jvm/java-11-openjdk-amd64",
        "PATH": "/usr/lib/jvm/java-11-openjdk-amd64/bin:/usr/local/bin:$PATH",
        "USE_CCACHE": "1",
    })
)


@app.function(
    image=aosp_image,
    volumes={"/artifacts": artifacts_volume},
    timeout=300,
    memory=4096,
)
def check_status():
    """Check artifacts from previous build."""
    import os
    import subprocess

    artifacts = []
    for f in os.listdir("/artifacts"):
        path = f"/artifacts/{f}"
        if os.path.isfile(path):
            size = os.path.getsize(path)
            artifacts.append(f"{f}: {size / (1024*1024):.1f} MB")

    if artifacts:
        return "Build artifacts found:\n" + "\n".join(artifacts)
    return "No artifacts found. Run --action fullbuild first."


@app.function(
    image=aosp_image,
    volumes={"/artifacts": artifacts_volume},
    # Large ephemeral disk for AOSP source and build (minimum 512GB)
    ephemeral_disk=550 * 1024,  # 550GB ephemeral disk
    timeout=43200,  # 12 hours
    memory=65536,   # 64GB RAM
    cpu=32,         # 32 CPUs for faster build
)
def full_build(
    device_config: str = "",
    system_service: str = "",
    sdk: str = "",
    aidl: str = "",
    patches: str = "",
    target: str = "agi_os_x86_64-eng"
):
    """Sync AOSP and build AGI-Android OS in one run."""
    import subprocess
    import os
    import base64
    import tarfile
    import io
    import shutil

    # Work in ephemeral disk
    os.makedirs("/aosp", exist_ok=True)
    os.chdir("/aosp")

    # Show disk space
    print("=== Disk Space ===")
    subprocess.run(["df", "-h"])
    print()

    # Phase 1: Sync AOSP
    print("=== Phase 1: Syncing AOSP (this takes ~2 hours) ===")

    print("Initializing repo...")
    subprocess.run([
        "repo", "init",
        "-u", "https://android.googlesource.com/platform/manifest",
        "-b", "android-13.0.0_r83",
        "--depth=1",
    ], check=True)

    print("Syncing AOSP...")
    # Use more jobs since we have 32 CPUs
    result = subprocess.run([
        "repo", "sync",
        "-j16",  # 16 parallel downloads
        "-c",    # current branch only
        "--no-tags",
        "--no-clone-bundle",
        "--optimized-fetch",
    ])

    if result.returncode != 0:
        print("WARNING: Sync had errors, attempting to continue...")
        # Try to sync again with fewer jobs
        subprocess.run(["repo", "sync", "-j4", "-c", "--no-tags", "--force-sync"])

    # Check if we have enough to build
    if not os.path.exists("/aosp/build/envsetup.sh"):
        return "ERROR: AOSP sync failed - build/envsetup.sh not found"

    print("AOSP sync complete!")
    subprocess.run(["df", "-h"])

    # Phase 2: Apply AGI components
    print("\n=== Phase 2: Applying AGI Components ===")

    def extract_tar(data_b64, dest):
        if not data_b64:
            return
        data = base64.b64decode(data_b64)
        tar = tarfile.open(fileobj=io.BytesIO(data), mode='r:gz')
        os.makedirs(dest, exist_ok=True)
        tar.extractall(dest)
        tar.close()
        print(f"  Extracted to {dest}")

    print("Applying device configuration...")
    os.makedirs("/aosp/device", exist_ok=True)
    # Extract the entire agi device tree (includes os/ and agi_arm64/ subdirs)
    extract_tar(device_config, "/aosp/device")
    # Verify what we have
    if os.path.exists("/aosp/device/agi/agi_arm64/BoardConfig.mk"):
        print("  Found agi_arm64 device with BoardConfig.mk")
    if os.path.exists("/aosp/device/agi/os/agi_os_arm64.mk"):
        print("  Found os product config (legacy)")

    print("Applying AgentSystemService...")
    os.makedirs("/aosp/packages/services", exist_ok=True)
    extract_tar(system_service, "/aosp/packages/services")

    print("Applying AGI-OS SDK...")
    os.makedirs("/aosp/frameworks", exist_ok=True)
    extract_tar(sdk, "/aosp/frameworks")

    if aidl:
        print("Applying AIDL interfaces...")
        extract_tar(aidl, "/aosp/packages/services/AgentService")
        extract_tar(aidl, "/aosp/frameworks/AgentSDK")

    # Fix LICENSE files
    print("Fixing LICENSE files...")
    for path, src in [
        ("/aosp/external/kotlinx.coroutines", "LICENSE.txt"),
        ("/aosp/external/kotlinc", "license/LICENSE.txt"),
    ]:
        src_path = f"{path}/{src}"
        dst_path = f"{path}/LICENSE"
        if os.path.exists(src_path) and not os.path.exists(dst_path):
            shutil.copy(src_path, dst_path)
            print(f"  Fixed {dst_path}")

    # Attempt 19: Add BUILD_BROKEN flag to AOSP build system
    print("Adding BUILD_BROKEN_MISSING_REQUIRED_MODULES to build config...")
    broken_config = "/aosp/build/make/core/build_broken_config.mk"
    with open(broken_config, "a") as f:
        f.write("\n# Added by AGI-Android OS builder\n")
        f.write("BUILD_BROKEN_MISSING_REQUIRED_MODULES := true\n")
    print(f"  Added to {broken_config}")

    # Apply patches
    if patches:
        print("Applying patches...")
        extract_tar(patches, "/tmp/patches")
        if os.path.exists("/tmp/patches/frameworks_base"):
            os.chdir("/aosp/frameworks/base")
            for patch in os.listdir("/tmp/patches/frameworks_base"):
                if patch.endswith(".patch"):
                    print(f"  Applying {patch}...")
                    subprocess.run(["git", "apply", f"/tmp/patches/frameworks_base/{patch}"], check=False)
            os.chdir("/aosp")

    # Phase 3: Build
    print(f"\n=== Phase 3: Building {target} (this takes several hours) ===")

    # Build with fixes for Modal environment
    # Attempt 19: Skip ART APEX by not building any APEX modules
    build_script = f"""
        set -e
        cd /aosp
        source build/envsetup.sh
        lunch {target}

        # Disable all dex optimization to avoid dex2oat crashes in Modal environment
        export WITH_DEXPREOPT=false
        export DONT_DEXPREOPT_PREBUILTS=true
        export ART_BUILD_HOST_DEBUG=false
        export SKIP_BOOT_JARS_CHECK=true
        export WITH_HOST_DALVIK=false

        # Skip APEX module builds entirely (attempt 19)
        export DEXPREOPT_DISABLED_MODULES="com.android.art com.android.art.debug com.android.art.testing"
        export SOONG_CONFIG_art_module_global_disable_apexes=true

        # Build with make variable to allow missing required modules
        m -j32 BUILD_BROKEN_MISSING_REQUIRED_MODULES=true
    """
    result = subprocess.run(["bash", "-c", build_script])

    if result.returncode != 0:
        print(f"Build failed with code {result.returncode}")
        # Try to save any partial output
        subprocess.run(["df", "-h"])
        return f"Build failed with code {result.returncode}"

    # Phase 4: Copy artifacts to volume
    print("\n=== Phase 4: Saving Artifacts ===")

    # Find output directory
    out_dir = None
    product_dir = "/aosp/out/target/product"
    if os.path.exists(product_dir):
        for d in os.listdir(product_dir):
            path = f"{product_dir}/{d}"
            if os.path.isdir(path) and os.path.exists(f"{path}/system.img"):
                out_dir = path
                break

    if out_dir:
        # Copy key artifacts
        for img in ["system.img", "vbmeta.img", "boot.img", "vendor.img"]:
            src = f"{out_dir}/{img}"
            if os.path.exists(src):
                print(f"Copying {img}...")
                shutil.copy(src, f"/artifacts/{img}")

        artifacts_volume.commit()
        print("Artifacts saved to volume!")

        # List what we have
        subprocess.run(["ls", "-lh", "/artifacts/"])
        return "Build complete! Artifacts saved."
    else:
        return "Build completed but no output found"


@app.function(
    image=aosp_image,
    volumes={"/artifacts": artifacts_volume},
    timeout=600,
    memory=4096,
)
def download_artifacts():
    """List artifacts available for download."""
    import os

    result = []
    for f in os.listdir("/artifacts"):
        path = f"/artifacts/{f}"
        if os.path.isfile(path):
            size = os.path.getsize(path)
            result.append({
                "name": f,
                "size_mb": size / (1024*1024),
                "path": path
            })

    return result


@app.local_entrypoint()
def main(action: str = "status"):
    """Entry point."""
    import tarfile
    import io
    import base64
    import os

    if action == "status":
        result = check_status.remote()
        print(result)

    elif action == "fullbuild":
        # Package local code as tarballs
        def make_tarball(path):
            if not os.path.exists(path):
                print(f"Warning: {path} not found")
                return ""
            buf = io.BytesIO()
            with tarfile.open(fileobj=buf, mode='w:gz') as tar:
                tar.add(path, arcname=os.path.basename(path))
            size = buf.tell()
            print(f"Packaged {path}: {size / 1024:.1f} KB")
            return base64.b64encode(buf.getvalue()).decode()

        base = "/Users/jacob/Code/agi-android-os"

        print("=== Packaging AGI-Android OS code ===")
        # Package entire device/agi directory (includes os and agi_arm64)
        device_config = make_tarball(f"{base}/aosp/device/agi")
        system_service = make_tarball(f"{base}/system-service")
        sdk = make_tarball(f"{base}/sdk")
        aidl = make_tarball(f"{base}/aidl")
        patches = make_tarball(f"{base}/aosp/patches")

        print("\n=== Starting full build on Modal ===")
        print("This will take 6-8 hours (sync: ~2h, build: ~4-6h)")
        print("Monitor at: https://modal.com/apps/agi-inc/research-android-os/")

        result = full_build.remote(
            device_config=device_config,
            system_service=system_service,
            sdk=sdk,
            aidl=aidl,
            patches=patches,
        )
        print(result)

    elif action == "download":
        artifacts = download_artifacts.remote()
        if artifacts:
            print("Available artifacts:")
            for a in artifacts:
                print(f"  {a['name']}: {a['size_mb']:.1f} MB")
            print("\nTo download, use Modal CLI:")
            print("  modal volume get agi-os-artifacts system.img ./")
        else:
            print("No artifacts found. Run --action fullbuild first.")

    else:
        print("Usage: modal run --env research-android-os tools/modal_build.py --action [status|fullbuild|download]")
