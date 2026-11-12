#!/usr/bin/env bash
# Stage ffmpeg + ffprobe (Termux bionic-native) as Android native libraries.
#
# Why: the in-app agent needs `ffmpeg` for media work from the engine
# shell (transcoding clips, extracting audio, probing files with ffprobe).
# Android forbids exec from app-private storage, so the proven pattern
# (same as git/rg/node/xz/curl) is a bionic-native PIE binary staged as
# `jniLibs/<abi>/lib<tool>.so` and symlinked into `files/engine/tools/bin`
# at startup (EngineBinary).
#
# Closure (arm64, extracted sizes, verified by DT_NEEDED walk from the
# ffmpeg + ffprobe binaries): ffmpeg binary 398,960 + ffprobe 197,152 +
# 84 libs totalling ~89.4 MiB. Termux's ffmpeg links the full codec set,
# so the closure is large by construction: libavcodec 14.5 MiB, libx265
# 9.3 MiB, libglslang (libplacebo's shader compiler) 8.0 MiB, libaom
# 7.2 MiB, libavfilter 4.9 MiB, libSvtAv1Enc 4.3 MiB, libjxl 3.5 MiB,
# libavformat 2.9 MiB, libvpx + librav1e 2.3 MiB each, plus the long tail
# (libplacebo, libharfbuzz, libfreetype, libfontconfig, libass, libbluray,
# libopenmpt, libxml2, libsoxr, libvidstab, libvmaf, libzimg, ...). Every
# DT_NEEDED lib must be present or even `ffmpeg -version` fails to load,
# so there is no trimmed subset: shrinking this needs a custom ffmpeg
# build with fewer codecs, which is out of scope for this batch.
#
# Notes:
# - The ffmpeg/ffprobe binaries are staged as `libffmpeg.so` /
#   `libffprobe.so` (tool names `ffmpeg` / `ffprobe`): no library claims
#   those names, so no `-cli` suffix is needed (unlike curl/jq).
# - libvulkan.so comes from `vulkan-loader-generic` (the Khronos ICD
#   loader, 601 KiB), NOT from the system: staging it removes the
#   dependency on the device shipping /system/lib64/libvulkan.so. The
#   smaller `vulkan-loader-android` shim (22 KiB) is NOT usable: its
#   PT_LOAD segments are p_align 0x1000, which the 16 KB page-size gate
#   (and Android 15) rejects; the generic loader is p_align 0x4000.
#   libOpenCL.so likewise comes from `ocl-icd` (the ICD loader).
#   libandroid.so / libmediandk.so are genuine NDK system libraries in
#   /system/lib64 and are allowlisted in the ELF gate instead of staged.
# - libx264 / libx265 are GPL-licensed: bundling them makes this
#   distribution subject to GPL obligations -- see THIRD_PARTY_NOTICES.md.
#
# Usage (CI and local builds):
#   ./script/android/stage-ffmpeg.sh --abi arm64-v8a --jni-out android/app/src/main/jniLibs/arm64-v8a
#   ./script/android/stage-ffmpeg.sh --abi x86_64  --jni-out android/app/src/main/jniLibs/x86_64
#
# Requirements: curl, dpkg-deb, patchelf, readelf (CI installs patchelf via apt).
# Pinned versions come from the Termux `stable` index fetched 2026-11-12.
# To bump: re-fetch the index, update VERSIONS + SHAS below, and re-verify the
# closure table in `hidden_files/riset-tier2-xz-curl.md` (Batch 6 research notes
# live in the PR body + `script/android/stage-*.sh` headers).
set -euo pipefail
ABI=""
JNI_OUT=""
WORK_DIR=""
PATCHELF="${PATCHELF:-patchelf}"

while [ $# -gt 0 ]; do
    case "$1" in
        --abi) ABI="$2"; shift 2 ;;
        --jni-out) JNI_OUT="$2"; shift 2 ;;
        --work-dir) WORK_DIR="$2"; shift 2 ;;
        --patchelf) PATCHELF="$2"; shift 2 ;;
        *) echo "unknown argument: $1" >&2; exit 2 ;;
    esac
done

[ "$ABI" = "arm64-v8a" ] || [ "$ABI" = "x86_64" ] || { echo "--abi must be arm64-v8a or x86_64" >&2; exit 2; }
[ -n "$JNI_OUT" ] || { echo "--jni-out is required" >&2; exit 2; }

# --- Pinned packages (Termux `stable` index, 2026-11-12) -------------------
# Format: "<package> <version> <deb-filename> <sha256>".
# python-pip and ca-certificates are `all` (same file for both ABIs).
BROTLI_VERSION="1.2.1"
FFMPEG_VERSION="9.0.2-2"
FFTW_VERSION="3.3.11"
FONTCONFIG_VERSION="2.18.3"
FREETYPE_VERSION="2.15.0"
FRIBIDI_VERSION="1.0.18"
GAME_MUSIC_EMU_VERSION="0.6.5"
GLIB_VERSION="2.90.1"
GLSLANG_VERSION="16.6.0"
HARFBUZZ_VERSION="15.1.0-1"
HIGHWAY_VERSION="1.4.0-1"
LIBANDROID_EXECINFO_VERSION="0.1-3"
LIBANDROID_GLOB_VERSION="0.6-3"
LIBANDROID_SHMEM_VERSION="0.7"
LIBAOM_VERSION="3.15.3"
LIBASS_VERSION="0.17.5"
LIBBLURAY_VERSION="1.5.1"
LIBBS2B_VERSION="3.1.0-2"
LIBCPP_VERSION="30"
LIBDAV1D_VERSION="1.5.4"
LIBDVDNAV_VERSION="7.0.0"
LIBDVDREAD_VERSION="7.1.1"
LIBGRAPHITE_VERSION="1.3.15"
LIBICONV_VERSION="1.19.1"
LIBJXL_VERSION="0.13.0"
LIBMP3LAME_VERSION="4.0-1"
LIBMPG123_VERSION="1.33.7"
LIBMYSOFA_VERSION="1.3.5"
LIBOGG_VERSION="1.3.6-1"
LIBOPENCORE_AMR_VERSION="0.1.6-1"
LIBOPENMPT_VERSION="0.8.10"
LIBOPUS_VERSION="1.6.1"
LIBPLACEBO_VERSION="7.387.0"
LIBPNG_VERSION="1.6.59"
LIBRAV1E_VERSION="0.8.1"
LIBSAMPLERATE_VERSION="0.2.2-4"
LIBSODIUM_VERSION="1.0.22-1"
LIBSOXR_VERSION="0.1.3-8"
LIBSRT_VERSION="1.5.7"
LIBSSH_VERSION="0.12.2"
LIBTHEORA_VERSION="1.2.0-1"
LIBUDFREAD_VERSION="1.2.0"
LIBVIDSTAB_VERSION="1.1.2"
LIBVMAF_VERSION="3.2.1"
LIBVO_AMRWBENC_VERSION="0.1.3-2"
LIBVORBIS_VERSION="1.3.7-4"
LIBVPX_VERSION="1:1.17.0"
LIBWEBP_VERSION="1.6.0.0.0.1+really1.6.0"
LIBX264_VERSION="1:0.164.3191-1"
LIBX265_VERSION="4.3"
LIBXAU_VERSION="1.0.12-2"
LIBXCB_VERSION="1.17.0-1"
LIBXDMCP_VERSION="1.1.5-2"
LIBXML2_VERSION="2.15.4-1"
LIBZIMG_VERSION="3.0.6"
LIBZMQ_VERSION="4.3.5-2"
LITTLECMS_VERSION="2.20"
OCL_ICD_VERSION="2.3.5"
PCRE2_VERSION="10.49"
RUBBERBAND_VERSION="4.0.0-1"
SVT_AV1_VERSION="4.3.0"
VULKAN_LOADER_GENERIC_VERSION="1.4.368"
XVIDCORE_VERSION="1.3.7-1"

if [ "$ABI" = "arm64-v8a" ]; then
    DEB_ARCH="aarch64"
    EXPECTED_MACHINE="b700"
    BROTLI_SHA="4fba29438b2b44f8470d52173b4548e938542dc2fc632f8699be5a284cbb9485"
    FFMPEG_SHA="7a785ef62993ed8e0181fb0dcedae39ba7f62827c1150aa1bf805926d9bdf0da"
    FFTW_SHA="0a5140986bbfc69dbfa8f939f78521fb3dc02a193b6c822b7218dae44d99ffd5"
    FONTCONFIG_SHA="7f8b70dd7ca85b1e7cbb14a11e9e6bd4ce3189ab985aa7af51145484d85f2c61"
    FREETYPE_SHA="fe217807b22aa40029874eb1d9e01b89a01b5f1e4f0556c4de9c8e83c4db4256"
    FRIBIDI_SHA="6d9a4f6403a1450d22514dc7dc4de2b2358bec28afbce4576e37a3b224beae4b"
    GAME_MUSIC_EMU_SHA="ffe1c43919d485409b2459d462b2dc6389492afeea869aef701b391a4ed29c67"
    GLIB_SHA="132d8ce961d96a7252fd4bc2228bddd385b1b17b706a46caae6d541ae0e4b2c0"
    GLSLANG_SHA="c5b5d6cb57d5841966c4b07033d319ebf6a3df61347a484b9dc5e332d29cd721"
    HARFBUZZ_SHA="0ad02ed6650599800fb60c06f1a358c9666795fc371940278b7b69ef4c925d88"
    HIGHWAY_SHA="71b8e0c231ab6b66827374543d55997a5cb02d8882f5d53e059ba78cd2511da1"
    LIBANDROID_EXECINFO_SHA="725dd2c6da7fc96e860fcc928e18aa9fb0e85e0bdcaf4f8ce0f0654ca6860fc2"
    LIBANDROID_GLOB_SHA="2276ae8adedf0db76c2f4ffc94cc4cceb2f4f5d78e021b54e2e046d1233e7826"
    LIBANDROID_SHMEM_SHA="0da3a24d558b93c92bcf8d611e0826a99ff96e396b148e6cdf33b47c47c57ff6"
    LIBAOM_SHA="942fa9a844e77a5a0bd73da4928261538f08cf79109c0608d28f0243eaeba1a2"
    LIBASS_SHA="4f94205285ea2062be4f60e695723a6f3350339ec89720943b0dce5ae30db01d"
    LIBBLURAY_SHA="fbe931e6a7d9604fe865bd24b8012ad3b7f04c6bbb6cf9b277760db1b4a8df7e"
    LIBBS2B_SHA="c71660d99e4baa064ba93f0ef750b6b2ea0d1d494a754cf1c67b550bb4baa91d"
    LIBCPP_SHA="53d0b84a7ba7459024257cb94d5b136fe13ef858567f65a8064b35950799f2ca"
    LIBDAV1D_SHA="4c2ffdcbbb805727caaa2e91b56917830dbacf4245dd439cf2e1c0d61a541613"
    LIBDVDNAV_SHA="fd8509a88b5966cdadf9925b2687e50c31d796e773cb326e651e76c0a3b16958"
    LIBDVDREAD_SHA="8e1606f7d2aec40c9e8f748ab595cf0f684c998b4642bc59fa6b18b5fe1d2fb0"
    LIBGRAPHITE_SHA="15ac85737a35150cecb9e46ecb32567ae2a4fc28071cdf58fb95e1b77d7889d9"
    LIBICONV_SHA="a1c6323eff4f660e16ae6f5832356444b3269c19a4f61146355a49e7c4be5ed1"
    LIBJXL_SHA="7ccbe06a6c0d22a8934729e580397bb405c30ea5f25ccf18365c1c79c7103f5c"
    LIBMP3LAME_SHA="59f70ad8885578063bcbea3b11e74fb69e2416ca036b47aa255c1fa4f074565e"
    LIBMPG123_SHA="883b104faae53820b2e758363300ccf99e28f627a263d766af61ae07b73b07b3"
    LIBMYSOFA_SHA="b083050d4bc0bd11db0f73382ae3db8c5dc352520b78aaa44f150d87cff80215"
    LIBOGG_SHA="4f1700a532f251ad3600f44f88cd764f1f6b0542d3343391117944b47a92802d"
    LIBOPENCORE_AMR_SHA="8ad25c12b892d56e85a21517d814146109fea7865b2548b8637743d7d6e0fee2"
    LIBOPENMPT_SHA="ffbd8d03f0f4ca21aefdd5777d133a0b2ddafb44697f1c386e977fb048362235"
    LIBOPUS_SHA="005a9335ce0ec0f510bbe6e0229c901d6f920cc375fcda56461e96cef70f8ff0"
    LIBPLACEBO_SHA="925a54773e29aefc790fd1a6d7759ea3b893d0482db878299c8b885233655f82"
    LIBPNG_SHA="32d61f0b1a60cf7283aa3a80e288737493df2c6b84c8c749606b77c94a10f2b2"
    LIBRAV1E_SHA="ac7842a5896bdff1b4d4974d9c02e60f7d4a587b8618df691118a8591f8dbccf"
    LIBSAMPLERATE_SHA="521d46cc49557c00b2d5c107da1f86f0cdda8a3a3b84bd9958bd7fc52bec2dae"
    LIBSODIUM_SHA="a9fbbc3a7a5a2a0f514474ee706d49b576ac09268b87142a3690edb9f5f3643d"
    LIBSOXR_SHA="a1c5f66aae3b9900e9a492b43901b2b98586870c7905feb9f3c835cc9af464be"
    LIBSRT_SHA="6199d01dd9d1630a32c220f7fdb9bca958adbd7062f90e9bfc010cf75fdef07a"
    LIBSSH_SHA="05cfb1d6f70e9b316f1eac7a5ec9139f884e644d6d91e668e9ca49716aad8ff0"
    LIBTHEORA_SHA="9d4582f14f71df7a48101ee05297a57a898654ead55ef10ca4d73e254151b1e8"
    LIBUDFREAD_SHA="61a7e02c30ae61f9cbe46bf8cefa8e361a16b29010a07cacb43513ff533df81c"
    LIBVIDSTAB_SHA="598d1c5ea7de4e4c76aa2198602e3dc35684671d9690e69d665058d55a89ab75"
    LIBVMAF_SHA="8c65e85997426c00ce6a419343eb820e164d725e4b267e268a9e8e351e2f9001"
    LIBVO_AMRWBENC_SHA="aa913ca492246ed1e6ce57a3c52fe856c323cbb6e8dbdb6904b80214551f6e00"
    LIBVORBIS_SHA="6bfd70c4b570b85594bd06116b75a6f8109ec58cbc123e938b630a6eefdedf4e"
    LIBVPX_SHA="1dbc5be0610c9da58732f480c7a27dbb32cf72a472e88c6c0993bdc0feeae00a"
    LIBWEBP_SHA="8ba7fcfadd1a9957a7632c6407d6ef3e41d34dbd96a41769b245592ed4559045"
    LIBX264_SHA="fe98852cb06ce1c963a54284179d8cda42af2fa82afcac09f7d81e6de4d9bbc1"
    LIBX265_SHA="8dbfd50ad2ceea5ffa2384f0f4cc16a87cbf56ac83b4861fa4f640d1beb352cd"
    LIBXAU_SHA="2d86bed61ce89dd8fdb55e565aede10155f2ce3adaccb61d5f1375aec3314083"
    LIBXCB_SHA="191c3642b6a8c754bc75b69b94a81858ecb5f18766dc085aff55559b8087ad34"
    LIBXDMCP_SHA="caea0cf5c0028af0840774094b8900ef7afffd8f9bbd7b1abfbe66851f0d6077"
    LIBXML2_SHA="d315861bbcf716cfb088f53cddab9fb6a96430cc66769f79774cc69c2f53bcff"
    LIBZIMG_SHA="a776d30cdb5734fa8feb17fb89fe8a67d14d64e491777d9b62b0fd714a9b59e8"
    LIBZMQ_SHA="1d59efb56905e79159808c6b656cc768ffcc210012bc2d9b63f23162f65480d2"
    LITTLECMS_SHA="20be10d39b67e50366e172d11e7f3895fa2a71b5a86139713a101fa1d3b8d015"
    OCL_ICD_SHA="2d76ddf09069ad599a4d5b2aee374c58411e1a3b0618b1ca5412467c9fd7b26a"
    PCRE2_SHA="c27995e5f52b8ecc9b8b3e662eb08e49f82a18bfd3f34343936acc7b23964724"
    RUBBERBAND_SHA="1172030a1e3ed70976114c8ca3eb4da0b82c1714825bd751ac1ada606dbc685a"
    SVT_AV1_SHA="3a9459c012dbe3bf9484ee200ff962a49499c05d3cf2829ac278d37973d03cc0"
    VULKAN_LOADER_GENERIC_SHA="8043459b6d4efd4834b756c6eeb539e8ae184d71046ffddff3ca75e85b1fcb27"
    XVIDCORE_SHA="2f358b11fd750935b43ac19f09bb7c4228e007079426e0b73ee8bae1ddf26fc1"
else
    DEB_ARCH="x86_64"
    EXPECTED_MACHINE="3e00"
    BROTLI_SHA="de652571f92e74c7f0cfe71e16cfc6ead340b184dda024614e6cab3048234142"
    FFMPEG_SHA="bbcd5b3ae003476aaa4c8db6a27ea5be268277d546667daa57c29e9ed8275415"
    FFTW_SHA="1caa8697eacaf3369f911fc29e228ca2419311917762e923795d0ae9e5b8b3f8"
    FONTCONFIG_SHA="5c40bc38f24e22eb7d6654748aaf8d6a33a5a69ff53cf02a249e8aa3ecefc2e5"
    FREETYPE_SHA="c82adef4b84b9ef502c64c23857e3d2cac611c7bfa6943f7d4c9d0fcfec733e3"
    FRIBIDI_SHA="97ff239b52e7f348bc4597ed3987ee5a3e8b057234ab034c51da0ad147a54370"
    GAME_MUSIC_EMU_SHA="1eaae861eee36635fcce3d8605e0e37c929c563ea9dd4267c1a920f09c2fc62b"
    GLIB_SHA="fb663e2222c3f7c7fb5c98b4694426665a719160adff0a6aec509b8285b63de4"
    GLSLANG_SHA="66651725abea231a6a7207123767906e39d29d0d89393cf1b1afec9b3e386c85"
    HARFBUZZ_SHA="de5518bb7c13b6423635280e2e5df6d025a96a8400f200183475843d7fe51d3a"
    HIGHWAY_SHA="00c3ed53c3d9697e4ec3bca3758fa2ea39b38ee753cf284e08cc839d51fd04e9"
    LIBANDROID_EXECINFO_SHA="348582b47b4b6dec8c13147437d8bd93d029239bc95f30635074cad08efb6220"
    LIBANDROID_GLOB_SHA="33b5d2ce934e89d50363e7ea267cea697da937d14de9fcea78cc7271956ef86e"
    LIBANDROID_SHMEM_SHA="ffa9e4c87467b158b148d0ff92dda796aa038276c2075af3269cdcdb06f25797"
    LIBAOM_SHA="157b5b66e7bed4dd93bc9ba389dfb2bbe6974b923fb147062e1efdcec6ee4748"
    LIBASS_SHA="404eabbea13cb89b29ec79f9a822f60481f0fa4c102d01b41d9b705eb1ce8414"
    LIBBLURAY_SHA="8bc30b6112f4863a586627ded81de017b3d594f529d57cf0303876b38f19ef48"
    LIBBS2B_SHA="048022121af8f3ad3ae7a23bd91f3e1a1e76541b5cf8c57cd77928e35d6ba3ea"
    LIBCPP_SHA="c9bcd9a8067a19c9c0e4893a72d314a2e37d6b67ce8dd05c67b9bbacad044b4f"
    LIBDAV1D_SHA="93f0c7fd4c7706cf89783243c3a1e9676b25a9a78976caa6524f1cad73f4a38c"
    LIBDVDNAV_SHA="686ba8a5bd1530963b5b8a5df3d1aa15bce40c973fb83f268e56337527e93136"
    LIBDVDREAD_SHA="8f74f292de2fbcb9953ac3c04e674b58fe45851b5a4a4a65bf418e9c09dedb29"
    LIBGRAPHITE_SHA="c82bd46d191d4cbf08547f3537200c5c459ec1dcbb82fcd05b3704f05f47bc21"
    LIBICONV_SHA="3eb0c454676ad24d26c79601721b2eb3f9485c611ad0179fb86b17229344f46d"
    LIBJXL_SHA="8800e9126cc92c3aae146b16d85a7dc8526a334fdd3971cdbca9b3842c5ab31e"
    LIBMP3LAME_SHA="1599ab4003ae944c648d5bd7a9b00001bf4ef34a2dde6cf8c23b7ea58a2174a5"
    LIBMPG123_SHA="d39a982b5080b1a01f3d21707d4de72ea6ce13ab795548fa307bafdb4c4c0323"
    LIBMYSOFA_SHA="f81064e2cf5ed2b6d73301b1f594a525f3d699b201fffc3c7c9cb1eb3c156b6d"
    LIBOGG_SHA="55dd0908feb56bec69fdf1c0aaeedb73f6c74cc28495c0d640172845863c9eed"
    LIBOPENCORE_AMR_SHA="820cb0c2be317bea917e3ae5a083c4d2bfa095367ad4b623b4696f258c032db4"
    LIBOPENMPT_SHA="a8937113c514b161961b544ce230125f67b73ce0b08885a33b4c8b551d0034ff"
    LIBOPUS_SHA="724f0215e78a21a02f8dd01dd6c78d6cea71457c8080be99f984f5d8c8f5e4d9"
    LIBPLACEBO_SHA="8a306edec39eef00c9feaadc5799ad41287786e4aeda8900c649cd01dc4950ba"
    LIBPNG_SHA="b5bbc3c552255d072ac5e53ec7641128f373d09e404abd01a8285b1a4a7f324c"
    LIBRAV1E_SHA="6a40a0e0633d49f3eb222ad345dcb579aacdccf211645017ad95c83a15a6f0dc"
    LIBSAMPLERATE_SHA="ab641f508e9846952edbd452c9661810c723787c40dc309e582c7a27a4333fb3"
    LIBSODIUM_SHA="4eb2e0b3b8b1693020505b8e445bfd0e2010c130d6ed3a67b34d0fed35657094"
    LIBSOXR_SHA="3cd66761119a4f4cb621a701279c2b2edc9a6c4e9f1872dbfc8058fb76c6a66f"
    LIBSRT_SHA="a69c915f0c5f8375ae557651d1514c7ea6a0085a04a67501971078030c5fc063"
    LIBSSH_SHA="9090c7d8dcc794f42087f41b15b7f2b4f7ab695db3c90373c366dff3532d98cb"
    LIBTHEORA_SHA="b1903d622483976402e2554df61845c7b789b36a7b86fda087307e19802d2d7c"
    LIBUDFREAD_SHA="b5ce605b0476947c7c9ed64bbb16af18fdbfe1a5fd81060984671507f312e2d9"
    LIBVIDSTAB_SHA="447b6ec815f7240f7900df6b98e2803594d3a2b392437bc6fefd4bbd4841b926"
    LIBVMAF_SHA="f789d61e510dc6da7c19ef3912b8f2f09365d2a40c68f73550ce4d7b66233ead"
    LIBVO_AMRWBENC_SHA="c66b14c24adc1f26436dbe4e5703c039f87c2d0a3dafce360af083ca071e7022"
    LIBVORBIS_SHA="9515c03de58faa17e2e19deb78da09289c90b939c17fcbaf4b171aebfefb65bf"
    LIBVPX_SHA="f7716a1eaad1d727a0eca42f3761188f1539186260f8deca248eef77e0d238d1"
    LIBWEBP_SHA="3274f3b6b3b765ed5cfb3d33cf309a2222de7766ad53f0eaefcbe89563f2f7a3"
    LIBX264_SHA="b71ce302a81561c0e30ae65980b541b508dbe804fadc3eb402ba90085d0c1024"
    LIBX265_SHA="2289658fba654b3e56c22f30f8baef0b8fa1b39ef92887c5744b3063ba997288"
    LIBXAU_SHA="b964143f84f11270ba14baf9a747b9732f5a9b84d2ca0e70ca26ba0364e0ce0b"
    LIBXCB_SHA="38da04c89e95026282c1277ca475fe02aca78735e0af48e3aada6b5aed78b894"
    LIBXDMCP_SHA="814a80d52fdfd193286f3a2e50215a10f03f6e64579cbdef3886cf499adf896d"
    LIBXML2_SHA="b25b5b18185b4d9f61fc63d2d8ec11fadb5755b42e01c6afef9f5ad2abd6fb78"
    LIBZIMG_SHA="a77720138851bd3f517e0a5f1507461f1dfe9152cc38c3763f3478c0a8cec443"
    LIBZMQ_SHA="124f76f0a8b275cdfe15122311aede2fb78483151f105090f9f6dc8a86bf59c9"
    LITTLECMS_SHA="bfc8c267668667787c7b1397bc57473cf25370c104ef5519527df83f6cf21d1b"
    OCL_ICD_SHA="453a41d9c6dfac297f3a01101d9b3f32e37b88a93562027fba292a1bdcdd664b"
    PCRE2_SHA="4a6f66ac12565342897ce9b5cd11ba8ce6fce1fd1a91ae679c3468d7fc2ee541"
    RUBBERBAND_SHA="e730332f93c558c207c26b024bf5a50985bdbe0531361fbc86feab9d7cce7633"
    SVT_AV1_SHA="1113fe77be4c29e5492a22c3f3b7d23c8cbbe9d29f4dd02bc64470ead0531a6f"
    VULKAN_LOADER_GENERIC_SHA="5fc1d27b9872253ac08d38dedf4314077fe2a6a547184cba550919a5f1a70a8e"
    XVIDCORE_SHA="fb523353406710cbe9fbaac4fc4ba0048da4fb1be06e89f0be1369abf75b63ed"
fi

BASE_URL="https://packages.termux.dev/apt/termux-main"

# --- Setup ------------------------------------------------------------------
if [ -z "$WORK_DIR" ]; then
    WORK_DIR="$(mktemp -d)"
    trap 'rm -rf "$WORK_DIR"' EXIT
fi
DEBS_DIR="$WORK_DIR/debs"
EXTRACT_DIR="$WORK_DIR/extract"
PREFIX="$EXTRACT_DIR/data/data/com.termux/files/usr"
mkdir -p "$DEBS_DIR" "$EXTRACT_DIR" "$JNI_OUT"

command -v "$PATCHELF" >/dev/null || { echo "patchelf not found (set --patchelf or install it)" >&2; exit 1; }
"$PATCHELF" --version

download() { # $1=filename $2=sha256 $3=pool path
    local file="$1"
    local expected="$2"
    # NOTE: do not merge this into the `local` line above: bash expands all
    # right-hand sides before any of the assignments, so $file would be
    # unset here (and `set -u` aborts).
    local path="$DEBS_DIR/$file"
    if [ ! -f "$path" ]; then
        echo "Downloading $file..."
        curl -f -sL --retry 3 --retry-delay 2 -4 "$BASE_URL/$3" -o "$path"
    fi
    echo "$expected  $path" | sha256sum --check --strict -
}

# $1=pool path, $2=sha256; the .deb filename is the last path segment.
fetch() {
    download "$(basename "$1")" "$2" "$1"
}

fetch "pool/main/b/brotli/brotli_1.2.1_${DEB_ARCH}.deb" "$BROTLI_SHA"
fetch "pool/main/f/ffmpeg/ffmpeg_9.0.2-2_${DEB_ARCH}.deb" "$FFMPEG_SHA"
fetch "pool/main/f/fftw/fftw_3.3.11_${DEB_ARCH}.deb" "$FFTW_SHA"
fetch "pool/main/f/fontconfig/fontconfig_2.18.3_${DEB_ARCH}.deb" "$FONTCONFIG_SHA"
fetch "pool/main/f/freetype/freetype_2.15.0_${DEB_ARCH}.deb" "$FREETYPE_SHA"
fetch "pool/main/f/fribidi/fribidi_1.0.18_${DEB_ARCH}.deb" "$FRIBIDI_SHA"
fetch "pool/main/g/game-music-emu/game-music-emu_0.6.5_${DEB_ARCH}.deb" "$GAME_MUSIC_EMU_SHA"
fetch "pool/main/g/glib/glib_2.90.1_${DEB_ARCH}.deb" "$GLIB_SHA"
fetch "pool/main/g/glslang/glslang_16.6.0_${DEB_ARCH}.deb" "$GLSLANG_SHA"
fetch "pool/main/h/harfbuzz/harfbuzz_15.1.0-1_${DEB_ARCH}.deb" "$HARFBUZZ_SHA"
fetch "pool/main/h/highway/highway_1.4.0-1_${DEB_ARCH}.deb" "$HIGHWAY_SHA"
fetch "pool/main/liba/libandroid-execinfo/libandroid-execinfo_0.1-3_${DEB_ARCH}.deb" "$LIBANDROID_EXECINFO_SHA"
fetch "pool/main/liba/libandroid-glob/libandroid-glob_0.6-3_${DEB_ARCH}.deb" "$LIBANDROID_GLOB_SHA"
fetch "pool/main/liba/libandroid-shmem/libandroid-shmem_0.7_${DEB_ARCH}.deb" "$LIBANDROID_SHMEM_SHA"
fetch "pool/main/liba/libaom/libaom_3.15.3_${DEB_ARCH}.deb" "$LIBAOM_SHA"
fetch "pool/main/liba/libass/libass_0.17.5_${DEB_ARCH}.deb" "$LIBASS_SHA"
fetch "pool/main/libb/libbluray/libbluray_1.5.1_${DEB_ARCH}.deb" "$LIBBLURAY_SHA"
fetch "pool/main/libb/libbs2b/libbs2b_3.1.0-2_${DEB_ARCH}.deb" "$LIBBS2B_SHA"
fetch "pool/main/libc/libc++/libc++_30_${DEB_ARCH}.deb" "$LIBCPP_SHA"
fetch "pool/main/libd/libdav1d/libdav1d_1.5.4_${DEB_ARCH}.deb" "$LIBDAV1D_SHA"
fetch "pool/main/libd/libdvdnav/libdvdnav_7.0.0_${DEB_ARCH}.deb" "$LIBDVDNAV_SHA"
fetch "pool/main/libd/libdvdread/libdvdread_7.1.1_${DEB_ARCH}.deb" "$LIBDVDREAD_SHA"
fetch "pool/main/libg/libgraphite/libgraphite_1.3.15_${DEB_ARCH}.deb" "$LIBGRAPHITE_SHA"
fetch "pool/main/libi/libiconv/libiconv_1.19.1_${DEB_ARCH}.deb" "$LIBICONV_SHA"
fetch "pool/main/libj/libjxl/libjxl_0.13.0_${DEB_ARCH}.deb" "$LIBJXL_SHA"
fetch "pool/main/libm/libmp3lame/libmp3lame_4.0-1_${DEB_ARCH}.deb" "$LIBMP3LAME_SHA"
fetch "pool/main/libm/libmpg123/libmpg123_1.33.7_${DEB_ARCH}.deb" "$LIBMPG123_SHA"
fetch "pool/main/libm/libmysofa/libmysofa_1.3.5_${DEB_ARCH}.deb" "$LIBMYSOFA_SHA"
fetch "pool/main/libo/libogg/libogg_1.3.6-1_${DEB_ARCH}.deb" "$LIBOGG_SHA"
fetch "pool/main/libo/libopencore-amr/libopencore-amr_0.1.6-1_${DEB_ARCH}.deb" "$LIBOPENCORE_AMR_SHA"
fetch "pool/main/libo/libopenmpt/libopenmpt_0.8.10_${DEB_ARCH}.deb" "$LIBOPENMPT_SHA"
fetch "pool/main/libo/libopus/libopus_1.6.1_${DEB_ARCH}.deb" "$LIBOPUS_SHA"
fetch "pool/main/libp/libplacebo/libplacebo_7.387.0_${DEB_ARCH}.deb" "$LIBPLACEBO_SHA"
fetch "pool/main/libp/libpng/libpng_1.6.59_${DEB_ARCH}.deb" "$LIBPNG_SHA"
fetch "pool/main/libr/librav1e/librav1e_0.8.1_${DEB_ARCH}.deb" "$LIBRAV1E_SHA"
fetch "pool/main/libs/libsamplerate/libsamplerate_0.2.2-4_${DEB_ARCH}.deb" "$LIBSAMPLERATE_SHA"
fetch "pool/main/libs/libsodium/libsodium_1.0.22-1_${DEB_ARCH}.deb" "$LIBSODIUM_SHA"
fetch "pool/main/libs/libsoxr/libsoxr_0.1.3-8_${DEB_ARCH}.deb" "$LIBSOXR_SHA"
fetch "pool/main/libs/libsrt/libsrt_1.5.7_${DEB_ARCH}.deb" "$LIBSRT_SHA"
fetch "pool/main/libs/libssh/libssh_0.12.2_${DEB_ARCH}.deb" "$LIBSSH_SHA"
fetch "pool/main/libt/libtheora/libtheora_1.2.0-1_${DEB_ARCH}.deb" "$LIBTHEORA_SHA"
fetch "pool/main/libu/libudfread/libudfread_1.2.0_${DEB_ARCH}.deb" "$LIBUDFREAD_SHA"
fetch "pool/main/libv/libvidstab/libvidstab_1.1.2_${DEB_ARCH}.deb" "$LIBVIDSTAB_SHA"
fetch "pool/main/libv/libvmaf/libvmaf_3.2.1_${DEB_ARCH}.deb" "$LIBVMAF_SHA"
fetch "pool/main/libv/libvo-amrwbenc/libvo-amrwbenc_0.1.3-2_${DEB_ARCH}.deb" "$LIBVO_AMRWBENC_SHA"
fetch "pool/main/libv/libvorbis/libvorbis_1.3.7-4_${DEB_ARCH}.deb" "$LIBVORBIS_SHA"
fetch "pool/main/libv/libvpx/libvpx_1:1.17.0_${DEB_ARCH}.deb" "$LIBVPX_SHA"
fetch "pool/main/libw/libwebp/libwebp_1.6.0.0.0.1+really1.6.0_${DEB_ARCH}.deb" "$LIBWEBP_SHA"
fetch "pool/main/libx/libx264/libx264_1:0.164.3191-1_${DEB_ARCH}.deb" "$LIBX264_SHA"
fetch "pool/main/libx/libx265/libx265_4.3_${DEB_ARCH}.deb" "$LIBX265_SHA"
fetch "pool/main/libx/libxau/libxau_1.0.12-2_${DEB_ARCH}.deb" "$LIBXAU_SHA"
fetch "pool/main/libx/libxcb/libxcb_1.17.0-1_${DEB_ARCH}.deb" "$LIBXCB_SHA"
fetch "pool/main/libx/libxdmcp/libxdmcp_1.1.5-2_${DEB_ARCH}.deb" "$LIBXDMCP_SHA"
fetch "pool/main/libx/libxml2/libxml2_2.15.4-1_${DEB_ARCH}.deb" "$LIBXML2_SHA"
fetch "pool/main/libz/libzimg/libzimg_3.0.6_${DEB_ARCH}.deb" "$LIBZIMG_SHA"
fetch "pool/main/libz/libzmq/libzmq_4.3.5-2_${DEB_ARCH}.deb" "$LIBZMQ_SHA"
fetch "pool/main/l/littlecms/littlecms_2.20_${DEB_ARCH}.deb" "$LITTLECMS_SHA"
fetch "pool/main/o/ocl-icd/ocl-icd_2.3.5_${DEB_ARCH}.deb" "$OCL_ICD_SHA"
fetch "pool/main/p/pcre2/pcre2_10.49_${DEB_ARCH}.deb" "$PCRE2_SHA"
fetch "pool/main/r/rubberband/rubberband_4.0.0-1_${DEB_ARCH}.deb" "$RUBBERBAND_SHA"
fetch "pool/main/s/svt-av1/svt-av1_4.3.0_${DEB_ARCH}.deb" "$SVT_AV1_SHA"
fetch "pool/main/v/vulkan-loader-generic/vulkan-loader-generic_1.4.368_${DEB_ARCH}.deb" "$VULKAN_LOADER_GENERIC_SHA"
fetch "pool/main/x/xvidcore/xvidcore_1.3.7-1_${DEB_ARCH}.deb" "$XVIDCORE_SHA"

for deb in "$DEBS_DIR"/*.deb; do
    echo "Extracting $(basename "$deb")..."
    dpkg-deb -x "$deb" "$EXTRACT_DIR"
done

# --- Pick the trimmed closure ------------------------------------------------
# Source file (exact name under $PREFIX/lib) -> staged lib name.
# Versioned names are renamed by patchelf below; the file on disk already
# carries the final name so the package manager sees only `lib<name>.so`.
stage_lib() { # $1=exact filename under $PREFIX/lib $2=staged name
    local matches=("$PREFIX"/lib/$1)
    [ "${#matches[@]}" -eq 1 ] || { echo "expected exactly one match for $1, got ${#matches[@]}" >&2; exit 1; }
    cp "${matches[0]}" "$JNI_OUT/$2"
}

# The ffmpeg/ffprobe binaries live in bin/, not lib/.
cp "$PREFIX/bin/ffmpeg" "$JNI_OUT/libffmpeg.so"
cp "$PREFIX/bin/ffprobe" "$JNI_OUT/libffprobe.so"
# Exact real files (the unversioned names in the .debs are symlinks to these).
stage_lib "libOpenCL.so" "libOpenCL.so"
stage_lib "libSvtAv1Enc.so" "libSvtAv1Enc.so"
stage_lib "libXau.so" "libXau.so"
stage_lib "libXdmcp.so" "libXdmcp.so"
stage_lib "libandroid-execinfo.so" "libandroid-execinfo.so"
stage_lib "libandroid-glob.so" "libandroid-glob.so"
stage_lib "libandroid-shmem.so" "libandroid-shmem.so"
stage_lib "libaom.so" "libaom.so"
stage_lib "libass.so" "libass.so"
stage_lib "libavcodec.so.63.1.102" "libavcodec.so"
stage_lib "libavdevice.so.63.1.102" "libavdevice.so"
stage_lib "libavfilter.so.12.1.102" "libavfilter.so"
stage_lib "libavformat.so.63.1.102" "libavformat.so"
stage_lib "libavutil.so.61.1.102" "libavutil.so"
stage_lib "libbluray.so.4.0.0" "libbluray.so"
stage_lib "libbrotlicommon.so" "libbrotlicommon.so"
stage_lib "libbrotlidec.so" "libbrotlidec.so"
stage_lib "libbrotlienc.so" "libbrotlienc.so"
stage_lib "libbs2b.so" "libbs2b.so"
stage_lib "libc++_shared.so" "libc++_shared.so"
stage_lib "libdav1d.so" "libdav1d.so"
stage_lib "libdvdnav.so.4.4.0" "libdvdnav.so"
stage_lib "libdvdread.so.8.2.0" "libdvdread.so"
stage_lib "libfftw3.so" "libfftw3.so"
stage_lib "libfontconfig.so" "libfontconfig.so"
stage_lib "libfreetype.so" "libfreetype.so"
stage_lib "libfribidi.so" "libfribidi.so"
stage_lib "libglib-2.0.so.0.9000.1" "libglib-2.0.so"
stage_lib "libglslang-default-resource-limits.so" "libglslang-default-resource-limits.so"
stage_lib "libglslang.so" "libglslang.so"
stage_lib "libgme.so" "libgme.so"
stage_lib "libgraphite2.so" "libgraphite2.so"
stage_lib "libharfbuzz.so" "libharfbuzz.so"
stage_lib "libhwy.so" "libhwy.so"
stage_lib "libiconv.so" "libiconv.so"
stage_lib "libjxl.so" "libjxl.so"
stage_lib "libjxl_cms.so" "libjxl_cms.so"
stage_lib "libjxl_threads.so" "libjxl_threads.so"
stage_lib "liblcms2.so" "liblcms2.so"
stage_lib "libmp3lame.so" "libmp3lame.so"
stage_lib "libmpg123.so" "libmpg123.so"
stage_lib "libmysofa.so" "libmysofa.so"
stage_lib "libogg.so" "libogg.so"
stage_lib "libopencore-amrnb.so" "libopencore-amrnb.so"
stage_lib "libopencore-amrwb.so" "libopencore-amrwb.so"
stage_lib "libopenmpt.so" "libopenmpt.so"
stage_lib "libopus.so" "libopus.so"
stage_lib "libpcre2-8.so" "libpcre2-8.so"
stage_lib "libplacebo.so.387" "libplacebo.so"
stage_lib "libpng16.so" "libpng16.so"
stage_lib "librav1e.so.0.8.1" "librav1e.so"
stage_lib "librubberband.so" "librubberband.so"
stage_lib "libsamplerate.so" "libsamplerate.so"
stage_lib "libsharpyuv.so" "libsharpyuv.so"
stage_lib "libsodium.so" "libsodium.so"
stage_lib "libsoxr.so" "libsoxr.so"
stage_lib "libsrt.so" "libsrt.so"
stage_lib "libssh.so" "libssh.so"
stage_lib "libswresample.so.7.1.102" "libswresample.so"
stage_lib "libswscale.so.10.1.102" "libswscale.so"
stage_lib "libtheoradec.so" "libtheoradec.so"
stage_lib "libtheoraenc.so" "libtheoraenc.so"
stage_lib "libudfread.so.3.0.0" "libudfread.so"
stage_lib "libvidstab.so" "libvidstab.so"
stage_lib "libvmaf.so" "libvmaf.so"
stage_lib "libvo-amrwbenc.so.0.0.4" "libvo-amrwbenc.so"
stage_lib "libvorbis.so" "libvorbis.so"
stage_lib "libvorbisenc.so" "libvorbisenc.so"
stage_lib "libvorbisfile.so" "libvorbisfile.so"
stage_lib "libvpx.so.12.1.0" "libvpx.so"
stage_lib "libvulkan.so.1.4.368" "libvulkan.so"
stage_lib "libwebp.so" "libwebp.so"
stage_lib "libwebpmux.so" "libwebpmux.so"
stage_lib "libx264.so.164" "libx264.so"
stage_lib "libx265.so" "libx265.so"
stage_lib "libxcb-shape.so" "libxcb-shape.so"
stage_lib "libxcb-shm.so" "libxcb-shm.so"
stage_lib "libxcb-xfixes.so" "libxcb-xfixes.so"
stage_lib "libxcb.so" "libxcb.so"
stage_lib "libxml2.so.16.1.4" "libxml2.so"
stage_lib "libxvidcore.so.4.3" "libxvidcore.so"
stage_lib "libzimg.so" "libzimg.so"
stage_lib "libzmq.so" "libzmq.so"
# libzstd.so is NOT staged here: nothing in the ffmpeg closure links it
# (it is only needed by python's `compression.zstd` module) -- it is
# staged by script/android/stage-python.sh instead.

# --- patchelf: versioned DT_NEEDED -> unversioned ---------------------------
# Applied to the binary AND to every lib that needs another versioned lib.
# Map (old -> new):
declare -A RENAME=(
    [libavcodec.so.63]=libavcodec.so
    [libavdevice.so.63]=libavdevice.so
    [libavfilter.so.12]=libavfilter.so
    [libavformat.so.63]=libavformat.so
    [libavutil.so.61]=libavutil.so
    [libbluray.so.4]=libbluray.so
    [libdvdnav.so.4]=libdvdnav.so
    [libdvdread.so.8]=libdvdread.so
    [libglib-2.0.so.0]=libglib-2.0.so
    [libplacebo.so.387]=libplacebo.so
    [libswresample.so.7]=libswresample.so
    [libswscale.so.10]=libswscale.so
    [libudfread.so.3]=libudfread.so
    [libvo-amrwbenc.so.0]=libvo-amrwbenc.so
    [libvpx.so.12]=libvpx.so
    [libx264.so.164]=libx264.so
    [libxml2.so.16]=libxml2.so
    [libxvidcore.so.4]=libxvidcore.so
)

for lib in "$JNI_OUT"/lib*.so; do
    name="$(basename "$lib")"
    # Batch every patchelf edit for a file into ONE invocation: each
    # invocation rewrites the whole ELF and pads sections (~40-60 KiB per
    # file, measured on xz/liblzma/libzstd; a second call adds nothing).
    # Skip files that need no edit at all so they keep their exact .deb bytes.
    args=()
    # SONAME: the loader records the name the lib was loaded under; keep it
    # equal to the staged filename, otherwise a second load of the same lib
    # under its versioned SONAME can happen. The CLI binaries are
    # executables, not libraries, so they get no SONAME. Only rewrite the
    # SONAME when it differs (or is missing) -- a no-op --set-soname still
    # triggers a full rewrite.
    case "$name" in
        libffmpeg.so|libffprobe.so) ;;
        *)
            current_soname="$("$PATCHELF" --print-soname "$lib" 2>/dev/null || true)"
            if [ "$current_soname" != "$name" ]; then
                args+=(--set-soname "$name")
            fi
            ;;
    esac
    needed="$("$PATCHELF" --print-needed "$lib" || true)"
    for old in "${!RENAME[@]}"; do
        if printf '%s\n' "$needed" | grep -qx "$old"; then
            args+=(--replace-needed "$old" "${RENAME[$old]}")
        fi
    done
    if [ "${#args[@]}" -gt 0 ]; then
        "$PATCHELF" "${args[@]}" "$lib"
    fi
done

# --- ELF gates (same as stage-engine.ps1 / stage-node.sh) --------------------
check_elf() { # $1=file
    local file="$1" magic machine
    magic="$(od -An -tx1 -N4 "$file" | tr -d ' \n')"
    machine="$(od -An -tx1 -j18 -N2 "$file" | tr -d ' \n')"
    [ "$magic" = "7f454c46" ] || { echo "not an ELF file: $file" >&2; exit 1; }
    [ "$machine" = "$EXPECTED_MACHINE" ] || { echo "architecture mismatch: $file reports e_machine=0x$machine, expected 0x$EXPECTED_MACHINE" >&2; exit 1; }
    # 16 KB gate: every PT_LOAD segment must have p_align >= 0x4000.
    # (readelf -lW prints one line per LOAD segment; the last field is Align.)
    local align
    while IFS= read -r align; do
        [ "$((align))" -ge 16384 ] || { echo "$file has a PT_LOAD segment with p_align=$align; needs 0x4000 for 16 KB page sizes" >&2; exit 1; }
    done < <(readelf -lW "$file" | awk '$1 == "LOAD" { print $NF }')
    # No versioned NEEDED may survive: every needed lib must be a staged file
    # or a system library. Besides libc/libm/libdl/liblog (bionic + NDK),
    # libandroid.so and libmediandk.so are NDK system libraries in
    # /system/lib64 (linked by libavcodec, staged by stage-ffmpeg.sh); the
    # gate runs over the whole jniLibs dir, so it must allow them even
    # though this script stages no lib that needs them -- otherwise a later
    # step's gate fails on the ffmpeg step's libs (same lesson as PR #13's
    # liblog case).
    local n
    while IFS= read -r n; do
        case "$n" in
            libc.so|libm.so|libdl.so|liblog.so|libandroid.so|libmediandk.so) ;;
            lib*.so) [ -f "$JNI_OUT/$n" ] || { echo "$file still needs $n, which is not staged" >&2; exit 1; } ;;
            *) ;;
        esac
    done < <("$PATCHELF" --print-needed "$file")
}

total=0
for lib in "$JNI_OUT"/lib*.so; do
    check_elf "$lib"
    size="$(wc -c < "$lib")"
    total=$((total + size))
    echo "$ABI staged: $(basename "$lib") ($size B)"
done
echo "$ABI ffmpeg closure: $total B (target ~89.4 MiB libs + 0.6 MiB binaries)"

echo "stage-ffmpeg done for $ABI"
