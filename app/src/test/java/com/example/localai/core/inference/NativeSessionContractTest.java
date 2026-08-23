package com.example.localai.core.inference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * S006 纯 JVM 契约测试：不加载 Native 库，只覆盖错误码契约与 Java 侧守卫。
 * 句柄创建/释放的运行时冒烟依赖 arm64 设备（见工作日志 S004/S006 记录）。
 */
public class NativeSessionContractTest {

    @Test
    public void errorCodes_mirrorNativeContract() {
        assertEquals(0, NativeSession.OK);
        assertEquals(1001, NativeSession.ERR_INVALID_HANDLE);
        assertEquals(1002, NativeSession.ERR_WRONG_STATE);
        assertEquals(1003, NativeSession.ERR_NULL_ARGUMENT);
        assertEquals(1004, NativeSession.ERR_ILLEGAL_ARGUMENT);
        assertEquals(1099, NativeSession.ERR_INTERNAL);
        // P3 新增错误码（与 native_session.cpp 镜像）
        assertEquals(1101, NativeSession.ERR_MODEL_LOAD_FAILED);
        assertEquals(1102, NativeSession.ERR_CONTEXT_CREATE_FAILED);
        assertEquals(1103, NativeSession.ERR_TOKENIZE_FAILED);
    }

    @Test
    public void finishReasons_distinct() {
        assertEquals(0, NativeSession.FINISH_END);
        assertEquals(1, NativeSession.FINISH_STOPPED);
    }

    @Test
    public void ensureOpen_zeroHandle_throwsIllegalState() {
        IllegalStateException ex =
                assertThrows(IllegalStateException.class, () -> NativeSession.ensureOpen(0));
        assertEquals("session closed", ex.getMessage());
        // 非 0 句柄必须放行
        NativeSession.ensureOpen(42);
    }

    @Test
    public void requireNonEmpty_rejectsNullAndEmpty() {
        assertThrows(NullPointerException.class, () -> NativeSession.requireNonEmpty(null, "modelPath"));
        assertThrows(IllegalArgumentException.class, () -> NativeSession.requireNonEmpty("", "modelPath"));
        NativeSession.requireNonEmpty("m.gguf", "modelPath");
    }

    @Test
    public void nativeException_carriesStructuredCode() {
        NativeSession.NativeException ex =
                new NativeSession.NativeException(NativeSession.ERR_WRONG_STATE, "native stop failed");
        assertEquals(NativeSession.ERR_WRONG_STATE, ex.getCode());
        assertTrue(ex.getMessage().contains("code=1002"));
    }
}
