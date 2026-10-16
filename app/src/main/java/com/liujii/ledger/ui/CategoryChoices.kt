package com.liujii.ledger.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.liujii.ledger.data.CategoryEntity
import com.liujii.ledger.data.MoneyType
import com.liujii.ledger.data.orderedCategoryChoices

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CategoryChoices(
    categories: List<CategoryEntity>,
    type: MoneyType,
    selectedId: Long,
    historicalCategoryId: Long? = null,
    enabled: Boolean = true,
    onSelected: (Long) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val availableWidth = maxWidth
        val choices=orderedCategoryChoices(categories,type,historicalCategoryId)
        if(choices.isEmpty())Text("暂无可选分类，请先在设置中新增或启用分类。",style=androidx.compose.material3.MaterialTheme.typography.bodySmall)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            choices.forEach { category ->
                FilterChip(
                    selected = selectedId == category.id,
                    onClick = { onSelected(category.id) },
                    enabled = enabled,
                    modifier = Modifier.widthIn(min = 48.dp, max = availableWidth).heightIn(min = 48.dp),
                    label = {
                        // Text 语义保留完整名称，省略号只影响单行绘制。
                        Text(
                            text = category.name + if (!category.active) "（已停用）" else "",
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                    },
                )
            }
        }
    }
}
