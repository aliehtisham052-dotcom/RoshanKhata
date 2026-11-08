#!/usr/bin/env python3
"""Every 64-bit native library in an APK is ready for 16 KB memory pages (3 Nov 2026 audit, L51).

Google Play requires apps targeting Android 15 and above to run on devices whose memory pages are 16 KB. A native
library passes when each of its LOAD segments is aligned to at least 16 KB (p_align >= 16384) and, stored
uncompressed in the APK, its data starts on a 16 KB boundary of the file. The audit checked this APK by hand and
found all eight libraries aligned; nothing stopped a new library, or a new version of one, from breaking it.

32-bit libraries are reported, not failed: 16 KB devices run 64-bit code.

    python3 scripts/check_16kb.py out/RoshanKhata-latest.apk
    python3 scripts/check_16kb.py app/build/outputs/bundle/release/app-release.aab
"""
import struct
import sys
import zipfile

PAGE = 16384
SIXTY_FOUR = ("arm64-v8a", "x86_64")


def load_aligns(data: bytes):
    """The p_align of every PT_LOAD segment of an ELF file."""
    if data[:4] != b"\x7fELF":
        raise ValueError("not an ELF file")
    wide = data[4] == 2
    order = "<" if data[5] == 1 else ">"
    if wide:
        phoff, = struct.unpack_from(order + "Q", data, 0x20)
        phentsize, phnum = struct.unpack_from(order + "HH", data, 0x36)
    else:
        phoff, = struct.unpack_from(order + "I", data, 0x1C)
        phentsize, phnum = struct.unpack_from(order + "HH", data, 0x2A)
    for i in range(phnum):
        at = phoff + i * phentsize
        p_type, = struct.unpack_from(order + "I", data, at)
        if p_type != 1:
            continue
        if wide:
            p_align, = struct.unpack_from(order + "Q", data, at + 0x30)
        else:
            p_align, = struct.unpack_from(order + "I", data, at + 0x1C)
        yield p_align


def data_offset(apk_path: str, info: zipfile.ZipInfo) -> int:
    """Where an entry's bytes start in the APK: after its local header, whose extra field may differ from the central one."""
    with open(apk_path, "rb") as f:
        f.seek(info.header_offset)
        head = f.read(30)
    name_len, extra_len = struct.unpack_from("<HH", head, 26)
    return info.header_offset + 30 + name_len + extra_len


def main(apk_path: str) -> int:
    failures = []
    checked = 0
    with zipfile.ZipFile(apk_path) as z:
        for info in z.infolist():
            name = info.filename
            # An app bundle (.aab, what Play receives, 8 Nov 2026 review L159) keeps its libraries under "<module>/lib/";
            # Play lays them out in the APKs it makes, so only the segments' alignment is checked there.
            bundle = apk_path.endswith(".aab")
            if bundle and "/lib/" in name:
                name = name[name.index("/lib/") + 1:]
            if not (name.startswith("lib/") and name.endswith(".so")):
                continue
            abi = name.split("/")[1]
            wide = abi in SIXTY_FOUR
            problems = []
            aligns = list(load_aligns(z.read(info)))
            low = [a for a in aligns if a < PAGE]
            if low:
                problems.append(f"LOAD segment aligned to {min(low)} bytes")
            if not bundle and info.compress_type == zipfile.ZIP_STORED:
                off = data_offset(apk_path, info)
                if off % PAGE:
                    problems.append(f"stored at offset {off}, not on a 16 KB boundary")
            checked += 1
            if problems and wide:
                failures.append(f"{name}: " + "; ".join(problems))
            elif problems:
                print(f"note (32-bit, not required): {name}: " + "; ".join(problems))
    if failures:
        for f in failures:
            print(f"::error title=16 KB pages::{f}")
        return 1
    print(f"16 KB pages OK: {checked} native libraries checked")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1]))
