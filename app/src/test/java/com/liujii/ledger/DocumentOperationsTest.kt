package com.liujii.ledger

import com.liujii.ledger.data.writeSelectedDocument
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import org.junit.Assert.*
import org.junit.Test

class DocumentOperationsTest {
    private class TrackingOutputStream(
        private val writeFailure: IOException? = null,
        private val closeFailure: IOException? = null,
    ) : OutputStream() {
        val bytes = ByteArrayOutputStream()
        var closeCount = 0
            private set

        override fun write(value: Int) {
            writeFailure?.let { throw it }
            bytes.write(value)
        }

        override fun close() {
            closeCount += 1
            closeFailure?.let { throw it }
        }
    }

    @Test fun nullOutputFailsWithoutCallingWriter() {
        var openCount = 0
        var writeCount = 0
        val error = assertThrows(IllegalStateException::class.java) {
            writeSelectedDocument(open = { openCount += 1; null }, write = { writeCount += 1 })
        }
        assertEquals("无法打开所选文件，请换一个保存位置", error.message)
        assertEquals(1, openCount)
        assertEquals(0, writeCount)
    }

    @Test fun openingExceptionPropagatesWithoutCallingWriter() {
        val failure = IOException("文件提供方拒绝打开")
        var writeCount = 0
        val error = assertThrows(IOException::class.java) {
            writeSelectedDocument(open = { throw failure }, write = { writeCount += 1 })
        }
        assertSame(failure, error)
        assertEquals(0, writeCount)
    }

    @Test fun successfulWriteUsesOpenedStreamAndClosesItExactlyOnceAfterWriting() {
        val output = TrackingOutputStream()
        val content = "流迹备份".toByteArray(Charsets.UTF_8)
        var openCount = 0
        var writeCount = 0
        writeSelectedDocument(open = { openCount += 1; output }) {
            assertSame(output, it)
            assertEquals(0, output.closeCount)
            writeCount += 1
            it.write(content)
            assertEquals(0, output.closeCount)
        }
        assertEquals(1, openCount)
        assertEquals(1, writeCount)
        assertArrayEquals(content, output.bytes.toByteArray())
        assertEquals(1, output.closeCount)
    }

    @Test fun writerExceptionPropagatesAndStillClosesStream() {
        val output = TrackingOutputStream()
        val failure = IOException("备份编码失败")
        val error = assertThrows(IOException::class.java) {
            writeSelectedDocument({ output }) { it.write(7); throw failure }
        }
        assertSame(failure, error)
        assertArrayEquals(byteArrayOf(7), output.bytes.toByteArray())
        assertEquals(1, output.closeCount)
    }

    @Test fun underlyingStreamWriteExceptionPropagatesAndStillClosesStream() {
        val failure = IOException("文件提供方写入失败")
        val output = TrackingOutputStream(writeFailure = failure)
        val error = assertThrows(IOException::class.java) {
            writeSelectedDocument({ output }) { it.write(byteArrayOf(1, 2)) }
        }
        assertSame(failure, error)
        assertEquals(0, output.bytes.size())
        assertEquals(1, output.closeCount)
    }

    @Test fun closeExceptionIsReportedAfterSuccessfulWrite() {
        val failure = IOException("文件提供方关闭失败")
        val output = TrackingOutputStream(closeFailure = failure)
        val error = assertThrows(IOException::class.java) {
            writeSelectedDocument({ output }) { it.write(9) }
        }
        assertSame(failure, error)
        assertArrayEquals(byteArrayOf(9), output.bytes.toByteArray())
        assertEquals(1, output.closeCount)
    }

    @Test fun writeFailureRemainsPrimaryWhenClosingAlsoFails() {
        val writeFailure = IOException("写入失败")
        val closeFailure = IOException("关闭失败")
        val output = TrackingOutputStream(writeFailure = writeFailure, closeFailure = closeFailure)
        val error = assertThrows(IOException::class.java) {
            writeSelectedDocument({ output }) { it.write(1) }
        }
        assertSame(writeFailure, error)
        assertEquals(listOf(closeFailure), error.suppressed.toList())
        assertEquals(1, output.closeCount)
    }
}
