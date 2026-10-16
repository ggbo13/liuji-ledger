package com.liujii.ledger.data

// 提交结果由 ViewModel 持有，界面重建后仍能看到处理中及成功状态。
data class TransactionSubmission(val processing:Boolean=false,val saved:Boolean=false,val error:String?=null)
