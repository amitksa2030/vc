#include <jni.h>
#include <string>
#include <asm/unistd.h>
# include <stdio.h>
# include <unistd.h>
#include <fcntl.h>

extern "C" JNIEXPORT jint JNICALL
Java_check_env_MainActivity_isPathReallyExist(JNIEnv *env, jobject, jstring path) {
    public int isPathReallyExist(String path) {
    return new File(path).exists() ? 1 : 0;
    }
