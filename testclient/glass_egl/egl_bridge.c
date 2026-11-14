#include <jni.h>
#include <dlfcn.h>
#include <stdio.h>
typedef void *EGLDisplay; typedef void *EGLConfig; typedef void *EGLSurface; typedef void *EGLContext;
typedef unsigned int EGLBoolean; typedef int EGLint;
extern EGLDisplay eglGetDisplay(void*);
extern void *eglGetProcAddress(const char*);
extern EGLBoolean eglInitialize(EGLDisplay,EGLint*,EGLint*);
extern EGLBoolean eglBindAPI(unsigned int);
extern EGLBoolean eglChooseConfig(EGLDisplay,const EGLint*,EGLConfig*,EGLint,EGLint*);
extern EGLSurface eglCreatePbufferSurface(EGLDisplay,EGLConfig,const EGLint*);
extern EGLContext eglCreateContext(EGLDisplay,EGLConfig,EGLContext,const EGLint*);
extern EGLBoolean eglMakeCurrent(EGLDisplay,EGLSurface,EGLSurface,EGLContext);
extern EGLBoolean eglDestroyContext(EGLDisplay,EGLContext);
extern EGLBoolean eglDestroySurface(EGLDisplay,EGLSurface);
extern EGLBoolean eglTerminate(EGLDisplay);
extern EGLint eglGetError(void);
static EGLDisplay display;static EGLSurface surface;static EGLContext context;
static void fail(JNIEnv *env,const char *stage){char msg[160];snprintf(msg,sizeof msg,"EGL %s failed: 0x%x",stage,eglGetError());(*env)->ThrowNew(env,(*env)->FindClass(env,"java/lang/IllegalStateException"),msg);}
JNIEXPORT jstring JNICALL Java_EglPbufferLauncher_create(JNIEnv *env,jclass cls){
    EGLDisplay (*platform)(unsigned int,void*,const EGLint*)=eglGetProcAddress("eglGetPlatformDisplayEXT");
    display=platform?platform(0x31DD,0,0):eglGetDisplay(0);
    EGLint major=0,minor=0,count=0;
    if(!display||!eglInitialize(display,&major,&minor)){fail(env,"initialize");return 0;}
    if(!eglBindAPI(0x30A2)){fail(env,"OpenGL API");return 0;}
    EGLint attrs[]={0x3033,1,0x3040,8,0x3024,8,0x3023,8,0x3022,8,0x3021,8,0x3025,24,0x3038};
    EGLConfig config;
    if(!eglChooseConfig(display,attrs,&config,1,&count)||count!=1){fail(env,"config");return 0;}
    EGLint pbuffer[]={0x3057,512,0x3056,384,0x3038},ctx[]={0x3038};
    surface=eglCreatePbufferSurface(display,config,pbuffer);context=eglCreateContext(display,config,0,ctx);
    if(!surface||!context||!eglMakeCurrent(display,surface,surface,context)){fail(env,"make current");return 0;}
    char message[128];snprintf(message,sizeof message,"EGL %d.%d software pbuffer with 24-bit depth",major,minor);
    return (*env)->NewStringUTF(env,message);
}
JNIEXPORT void JNICALL Java_EglPbufferLauncher_destroy(JNIEnv *env,jclass cls){
    if(display){eglMakeCurrent(display,0,0,0);if(context)eglDestroyContext(display,context);if(surface)eglDestroySurface(display,surface);eglTerminate(display);}
    display=surface=context=0;
}
