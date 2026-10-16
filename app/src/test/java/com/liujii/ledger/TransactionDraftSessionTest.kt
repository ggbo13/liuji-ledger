package com.liujii.ledger

import com.liujii.ledger.data.*
import org.junit.Assert.*
import org.junit.Test

class TransactionDraftSessionTest {
    private val beer=TransactionDraft(MoneyType.EXPENSE,"38.86",1,1,1,"2026-10-09","啤酒")

    @Test fun successfulSaveCannotBeWrittenBackByDisposeOrDelayedAutosave() {
        val writes=mutableListOf<TransactionDraft?>()
        val session=TransactionDraftSession{writes.add(it)}
        session.persist(beer)
        session.finish(null)
        // 模拟保存成功同一帧退出，旧页面销毁回调与自动保存仍拿着上一笔的快照。
        session.persist(beer);session.persist(beer)
        assertTrue(session.isClosed)
        assertEquals(listOf(beer,null),writes)
        assertNull(initialTransactionDraft(false,writes.last()))
    }

    @Test fun startingNewEntryDoesNotAutofillOldOrUnfinishedDraft() {
        assertNull(initialTransactionDraft(false,beer))
        assertNull(initialTransactionDraft(false,beer.copy(isCopy=true)))
        assertEquals(beer,initialTransactionDraft(true,beer))
    }

    @Test fun preserveAndExitKeepsExactlyTheConfirmedDraft() {
        var stored:TransactionDraft?=null
        val session=TransactionDraftSession{stored=it}
        session.finish(beer)
        session.persist(beer.copy(note="旧回调",amount="100.00"))
        assertEquals(beer,stored)
    }

    @Test fun discardCannotBeUndoneByDisposeCallback() {
        var stored:TransactionDraft?=beer
        val session=TransactionDraftSession{stored=it}
        session.finish(null);session.persist(beer)
        assertNull(stored)
    }

    @Test fun unfinishedOrFailedSaveStillPreservesNewInput() {
        var stored:TransactionDraft?=null
        val session=TransactionDraftSession{stored=it}
        session.persist(beer)
        session.persist(beer.copy(amount="39.00"))
        assertFalse(session.isClosed)
        assertEquals("39.00",stored?.amount)
    }

    @Test fun finishingTwiceDoesNotChangeAlreadyConfirmedOutcome() {
        val writes=mutableListOf<TransactionDraft?>()
        val session=TransactionDraftSession{writes.add(it)}
        session.finish(null);session.finish(beer)
        assertEquals(listOf<TransactionDraft?>(null),writes)
    }

    @Test fun callbacksFromPreviousSessionCannotOverwriteOrClearNewDraft() {
        val stored=mutableMapOf<String,TransactionDraft?>()
        val sessions=TransactionDraftSessions{key,draft->stored[key]=draft}
        val first=sessions.open("new-EXPENSE")
        first.persist(beer)
        val second=sessions.open("new-EXPENSE")
        val dinner=beer.copy(amount="50.00",note="晚餐")
        second.persist(dinner)
        first.persist(beer);first.finish(null)
        assertEquals(dinner,stored["new-EXPENSE"])
        second.finish(null)
        assertNull(stored["new-EXPENSE"])
    }

    @Test fun incomeExpenseAndEditingDraftsAreIsolated() {
        val stored=mutableMapOf<String,TransactionDraft?>()
        val sessions=TransactionDraftSessions{key,draft->stored[key]=draft}
        val expense=sessions.open("new-EXPENSE")
        val income=sessions.open("new-INCOME")
        val editing=sessions.open("edit-42")
        val salary=beer.copy(type=MoneyType.INCOME,note="工资")
        expense.persist(beer);income.persist(salary);editing.persist(beer.copy(note="修改"))
        expense.finish(null)
        assertEquals(salary,stored["new-INCOME"])
        assertEquals("修改",stored["edit-42"]?.note)
    }

    @Test fun recreatedEditorReusesSubmissionSessionAndSuccessfulSaveClearsWithoutUi() {
        val stored=mutableMapOf<String,TransactionDraft?>()
        val sessions=TransactionDraftSessions{key,draft->stored[key]=draft}
        val original=sessions.open("edit-42","submission-1")
        original.persist(beer.copy(isCopy=true))
        val recreated=sessions.open("edit-42","submission-1")
        assertSame(original,recreated)
        // 模拟应用锁已移除表单，只有 ViewModel 的成功回调执行。
        sessions.finishSubmission("submission-1")
        assertTrue(recreated.isClosed)
        assertNull(stored["edit-42"])
        recreated.persist(beer)
        assertNull(stored["edit-42"])
    }

    @Test fun lateCompletedSubmissionCannotClearANewerEditorDraft() {
        val stored=mutableMapOf<String,TransactionDraft?>()
        val sessions=TransactionDraftSessions{key,draft->stored[key]=draft}
        val first=sessions.open("new-EXPENSE","submission-1")
        first.persist(beer)
        val second=sessions.open("new-EXPENSE","submission-2")
        val dinner=beer.copy(note="新表单的晚餐")
        second.persist(dinner)
        sessions.finishSubmission("submission-1")
        assertEquals(dinner,stored["new-EXPENSE"])
        assertFalse(second.isClosed)
        sessions.finishSubmission("submission-2")
        assertNull(stored["new-EXPENSE"])
    }

    @Test fun completedIdentityStaysClosedAcrossAnotherRecreation() {
        val writes=mutableListOf<TransactionDraft?>()
        val sessions=TransactionDraftSessions{_,draft->writes.add(draft)}
        val original=sessions.open("new-EXPENSE","submission-1")
        sessions.finishSubmission("submission-1")
        val recreated=sessions.open("new-EXPENSE","submission-1")
        assertSame(original,recreated)
        recreated.persist(beer)
        sessions.finishSubmission("unknown")
        assertEquals(listOf<TransactionDraft?>(null),writes)
    }
}
