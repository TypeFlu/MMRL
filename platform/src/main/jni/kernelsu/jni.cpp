#include <jni.h>
#include <sys/prctl.h>
#include <cstring>
#include <kernelsu/ksu.hpp>
#include <logging.hpp>

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_dergoogler_mmrl_platform_ksu_KsuNative_grantRoot(JNIEnv *env, jobject) {
    return grant_root();
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_dergoogler_mmrl_platform_ksu_KsuNative_becomeManager(JNIEnv *env, jobject, jstring pkg) {
    auto cpkg = env->GetStringUTFChars(pkg, nullptr);
    auto result = become_manager(cpkg);
    env->ReleaseStringUTFChars(pkg, cpkg);
    return result;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_dergoogler_mmrl_platform_ksu_KsuNative_getVersion(JNIEnv *env, jobject) {
    return get_version();
}

extern "C"
JNIEXPORT jintArray JNICALL
Java_com_dergoogler_mmrl_platform_ksu_KsuNative_getAllowList(JNIEnv *env, jobject) {
    int uids[1024];
    int size = 0;
    bool result = get_allow_list(uids, &size);
    LOGD("getAllowList: %d, size: %d", result, size);
    if (result) {
        auto array = env->NewIntArray(size);
        env->SetIntArrayRegion(array, 0, size, uids);
        return array;
    }
    return env->NewIntArray(0);
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_dergoogler_mmrl_platform_ksu_KsuNative_isSafeMode(JNIEnv *env, jobject) {
    return is_safe_mode();
}

extern "C"
JNIEXPORT jobject JNICALL
Java_com_dergoogler_mmrl_platform_ksu_KsuNative_isLkmMode(JNIEnv *env, jobject thiz) {
    return reinterpret_cast<jobject>(is_lkm_mode());
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_dergoogler_mmrl_platform_ksu_KsuNative_uidShouldUmount(JNIEnv *env, jobject thiz,
                                                                jint uid) {
    return uid_should_umount(uid);
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_dergoogler_mmrl_platform_ksu_KsuNative_isSuEnabled(JNIEnv *env, jobject thiz) {
    return is_su_enabled();
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_dergoogler_mmrl_platform_ksu_KsuNative_setSuEnabled(JNIEnv *env, jobject thiz,
                                                             jboolean enabled) {
    return set_su_enabled(enabled);
}
extern "C"
JNIEXPORT jstring JNICALL
Java_com_dergoogler_mmrl_platform_ksu_KsuNative_getHookMode(JNIEnv *env, jobject thiz) {
    char mode[16] = {0};
    if (get_hook_mode(mode, sizeof(mode))) {
        return env->NewStringUTF(mode);
    }
    return nullptr;
}