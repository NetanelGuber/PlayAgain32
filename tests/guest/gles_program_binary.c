#include <EGL/egl.h>
#include <GLES2/gl2.h>
#include <GLES2/gl2ext.h>
#include <stdlib.h>

int test_gles_program_binary(void) {
    EGLDisplay display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    EGLConfig config;
    EGLint count;
    const EGLint attributes[] = {EGL_SURFACE_TYPE, EGL_PBUFFER_BIT, EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT, EGL_NONE};
    const EGLint surface_attributes[] = {EGL_WIDTH, 1, EGL_HEIGHT, 1, EGL_NONE};
    const EGLint context_attributes[] = {EGL_CONTEXT_CLIENT_VERSION, 2, EGL_NONE};
    if (!eglInitialize(display, NULL, NULL) || !eglChooseConfig(display, attributes, &config, 1, &count) || !count) return 1;
    EGLSurface surface = eglCreatePbufferSurface(display, config, surface_attributes);
    EGLContext context = eglCreateContext(display, config, EGL_NO_CONTEXT, context_attributes);
    if (!eglMakeCurrent(display, surface, surface, context)) return 2;
    PFNGLGETPROGRAMBINARYOESPROC get_binary = (PFNGLGETPROGRAMBINARYOESPROC)eglGetProcAddress("glGetProgramBinaryOES");
    PFNGLPROGRAMBINARYOESPROC set_binary = (PFNGLPROGRAMBINARYOESPROC)eglGetProcAddress("glProgramBinaryOES");
    if (!get_binary || !set_binary) return 3;
    const char *vertex = "attribute vec4 position; void main() { gl_Position = position; }";
    const char *fragment = "precision mediump float; void main() { gl_FragColor = vec4(1.0); }";
    GLuint shaders[] = {glCreateShader(GL_VERTEX_SHADER), glCreateShader(GL_FRAGMENT_SHADER)};
    glShaderSource(shaders[0], 1, &vertex, NULL);
    glShaderSource(shaders[1], 1, &fragment, NULL);
    GLuint program = glCreateProgram();
    for (int i = 0; i < 2; ++i) {
        glCompileShader(shaders[i]);
        glAttachShader(program, shaders[i]);
    }
    glLinkProgram(program);
    GLint length = 0;
    glGetProgramiv(program, GL_PROGRAM_BINARY_LENGTH_OES, &length);
    if (length <= 0) return 4;
    void *binary = malloc((size_t)length);
    if (!binary) return 5;
    GLsizei written = 0;
    GLenum format = 0;
    get_binary(program, length, &written, &format, binary);
    GLuint restored = glCreateProgram();
    set_binary(restored, format, binary, written);
    GLint linked = 0;
    glGetProgramiv(restored, GL_LINK_STATUS, &linked);
    int result = written > 0 && written <= length && linked && glGetError() == GL_NO_ERROR ? 0 : 6;
    PFNGLDRAWELEMENTSBASEVERTEXOESPROC draw = (PFNGLDRAWELEMENTSBASEVERTEXOESPROC)eglGetProcAddress("glDrawElementsBaseVertexOES");
    PFNGLDRAWELEMENTSINSTANCEDBASEVERTEXOESPROC draw_instanced =
        (PFNGLDRAWELEMENTSINSTANCEDBASEVERTEXOESPROC)eglGetProcAddress("glDrawElementsInstancedBaseVertexOES");
    if (!draw || !draw_instanced) return 7;
    const GLfloat vertices[] = {0, 0, -1, -1, 3, -1, -1, 3};
    const GLushort indices[] = {0, 1, 2};
    GLuint buffers[2];
    glGenBuffers(2, buffers);
    glBindBuffer(GL_ARRAY_BUFFER, buffers[0]);
    glBufferData(GL_ARRAY_BUFFER, sizeof(vertices), vertices, GL_STATIC_DRAW);
    glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, buffers[1]);
    glBufferData(GL_ELEMENT_ARRAY_BUFFER, sizeof(indices), indices, GL_STATIC_DRAW);
    glUseProgram(restored);
    GLint position = glGetAttribLocation(restored, "position");
    glEnableVertexAttribArray((GLuint)position);
    glVertexAttribPointer((GLuint)position, 2, GL_FLOAT, GL_FALSE, 0, NULL);
    glViewport(0, 0, 1, 1);
    for (int instanced = 0; instanced < 2; ++instanced) {
        glClearColor(0, 0, 0, 0);
        glClear(GL_COLOR_BUFFER_BIT);
        if (instanced) draw_instanced(GL_TRIANGLES, 3, GL_UNSIGNED_SHORT, NULL, 1, 1);
        else draw(GL_TRIANGLES, 3, GL_UNSIGNED_SHORT, NULL, 1);
        unsigned char pixel[4] = {0};
        glReadPixels(0, 0, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, pixel);
        if (pixel[0] != 255 || glGetError() != GL_NO_ERROR) result = 8 + instanced;
    }
    glDeleteBuffers(2, buffers);
    free(binary);
    glDeleteProgram(restored);
    glDeleteProgram(program);
    for (int i = 0; i < 2; ++i) glDeleteShader(shaders[i]);
    eglMakeCurrent(display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
    eglDestroyContext(display, context);
    eglDestroySurface(display, surface);
    eglTerminate(display);
    return result;
}
