package com.liujii.ledger.data

// 遮罩完全遵循用户设定，图片本身不再暗中抬高最低值。
fun backgroundOverlayAlpha(configured: Float): Float = if(configured.isFinite()) configured.coerceIn(.45f,1f) else .84f
