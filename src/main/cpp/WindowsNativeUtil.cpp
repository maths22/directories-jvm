#include <shlobj.h>
#include "com_maths22_directories_WindowsNativeUtil.h"

JNIEXPORT jstring JNICALL Java_com_maths22_directories_WindowsNativeUtil_getWinDir
        (JNIEnv * env, jclass, jobject uuid) {
    auto uuidClass = env->FindClass("java/util/UUID");
    auto getLeastSignificantBitsHandle = env->GetMethodID(uuidClass, "getLeastSignificantBits", "()J");
    auto getMostSignificantBitsHandle = env->GetMethodID(uuidClass, "getMostSignificantBits", "()J");
    auto leastSignificantBits = env->CallLongMethod(uuid, getLeastSignificantBitsHandle);
    auto mostSignificantBits = env->CallLongMethod(uuid, getMostSignificantBitsHandle);

    KNOWNFOLDERID folderGuid = KNOWNFOLDERID();
    folderGuid.Data1 = (mostSignificantBits >> (4 * 8)) & 0xFFFFFFFF;
    folderGuid.Data2 = (mostSignificantBits >> (2 * 8)) & 0xFFFF;
    folderGuid.Data3 = mostSignificantBits & 0xFFFF;
    for(int i = 0; i < 8; i++) {
        folderGuid.Data4[i] = (leastSignificantBits >> ((7 - i) * 8)) & 0xFF;
    }

    PWSTR pathRet;
    HRESULT result = SHGetKnownFolderPath(folderGuid, 0, nullptr, &pathRet);
    if(FAILED(result)) {
        CoTaskMemFree(pathRet);
        return nullptr;
    }

    // On windows, a wchar_t is a utf16 string
    auto ret = env->NewString(reinterpret_cast<const jchar *>(pathRet), (jsize) wcslen(pathRet));
    CoTaskMemFree(pathRet);
    return ret;
}