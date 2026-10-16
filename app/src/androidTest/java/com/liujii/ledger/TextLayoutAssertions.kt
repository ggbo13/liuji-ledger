package com.liujii.ledger

import androidx.compose.ui.text.TextLayoutResult
import org.junit.Assert.assertTrue

internal fun assertVisibleTextFits(layout: TextLayoutResult, allowEllipsis: Boolean = false) {
    val detail="文字=${layout.layoutInput.text}，size=${layout.size}，段落=${layout.multiParagraph.width}×${layout.multiParagraph.height}"
    val ellipsized=(0 until layout.lineCount).any{layout.isLineEllipsized(it)}
    assertTrue("未授权的省略：$detail",allowEllipsis||!ellipsized)
    assertTrue("文字被截断：$detail",!layout.multiParagraph.didExceedMaxLines||(allowEllipsis&&ellipsized))
    assertTrue("文字没有可见行：$detail",layout.lineCount>0)
    // 简单 Text 的测试语义会按父约束重建段落画布；画布留白不是文字溢出。
    // 检查各行实际文字宽度与行底边，仍能识别真实的横向裁切和高度不足。
    for(line in 0 until layout.lineCount) {
        val width=layout.getLineRight(line)-layout.getLineLeft(line)
        // 省略号的字形度量可有亚像素外伸；仅明确授权的 Ellipsis 行按其裁剪契约判断。
        if(layout.isLineEllipsized(line))assertTrue("省略方式不正确：$detail",allowEllipsis&&layout.layoutInput.overflow==androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        else assertTrue("横向裁切：$detail，行宽=$width",width<=layout.size.width+1f)
        assertTrue("纵向裁切：$detail，行底=${layout.getLineBottom(line)}",layout.getLineBottom(line)<=layout.size.height+1f)
    }
}
