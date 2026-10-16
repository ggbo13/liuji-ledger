package com.liujii.ledger.data

// 保存成功或明确退出后立即封闭本次草稿会话，不等待 Compose 再次重组。
// 这样页面销毁回调、延迟自动保存都不能把已完成的账目写回草稿。
class TransactionDraftSession(private val write: (TransactionDraft?) -> Unit) {
    var isClosed: Boolean = false
        private set

    fun persist(draft: TransactionDraft?) {
        if (!isClosed) write(draft)
    }

    fun finish(draft: TransactionDraft?) {
        if (isClosed) return
        isClosed = true
        write(draft)
    }
}

// 同一草稿槽只允许最新会话写入，防止旧保存回调干扰后来打开的新表单。
class TransactionDraftSessions(private val write: (String, TransactionDraft?) -> Unit) {
    private val owners=mutableMapOf<String,TransactionDraftSession>()
    private val identities=mutableMapOf<String,Pair<String,TransactionDraftSession>>()

    fun open(key: String, identity:String?=null): TransactionDraftSession {
        // 同一个编辑会话跨界面重建时复用对象，保存回调不会拿到失去所有权的旧对象。
        identity?.let{identities[it]?.takeIf{entry->entry.first==key}?.let{entry->return entry.second}}
        lateinit var session:TransactionDraftSession
        session=TransactionDraftSession { draft ->
            if(owners[key]===session) {
                write(key,draft)
                if(session.isClosed)owners.remove(key)
            }
        }
        owners[key]=session
        if(identity!=null)identities[identity]=key to session
        return session
    }

    fun finishSubmission(identity:String) { identities[identity]?.second?.finish(null) }
}

// 新建记录默认留空，暂存草稿只能由用户主动恢复；编辑记录可以恢复自己的草稿。
fun initialTransactionDraft(isEditing: Boolean, storedDraft: TransactionDraft?): TransactionDraft? =
    if (isEditing) storedDraft else null
