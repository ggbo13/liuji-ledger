package com.liujii.ledger

import com.liujii.ledger.data.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException

class BackupSafety116Test {
    @Test fun textFormulaPrefixesAreNeutralized() {
        listOf("=1+1","+SUM(A1:A2)","-2+3","@SUM(1)","＝1+1","＋1","－1","＠SUM(1)","  =1","\uFEFF=1","\tplain","\rplain","\nplain").forEach{
            assertTrue(it,csvCell(it).startsWith("\"'"))
        }
    }
    @Test fun numbersRemainNumbersAndSafeTextIsUnchanged() {
        assertEquals("\"-1234.56\"",csvCell("-1234.56",text=false))
        assertEquals("\"1234.56\"",csvCell("1234.56",text=false))
        assertEquals("\"餐饮\"",csvCell("餐饮"))
        assertEquals("\"\"",csvCell(""))
        assertEquals("\"含,逗号\n和\"\"引号\"\"\"",csvCell("含,逗号\n和\"引号\""))
    }
    @Test fun boundedCopyAcceptsTheExactLimit() {
        val output=ByteArrayOutputStream()
        assertEquals(4L,copyBackupEntry(ByteArrayInputStream(byteArrayOf(1,2,3,4)),output,4))
        assertArrayEquals(byteArrayOf(1,2,3,4),output.toByteArray())
    }
    @Test fun boundedCopyRejectsOversizeBeforeWritingTheChunk() {
        val output=ByteArrayOutputStream()
        assertThrows(IllegalArgumentException::class.java){copyBackupEntry(ByteArrayInputStream(ByteArray(5)),output,4)}
        assertEquals(0,output.size())
    }
    @Test fun limitedWriterCannotExceedTheDeclaredSize() {
        val output=ByteArrayOutputStream()
        val writer=BackupLimitedOutput(output,3)
        writer.write(byteArrayOf(1,2,3))
        assertThrows(IllegalArgumentException::class.java){writer.write(4)}
        assertEquals(3,output.size())
    }
    @Test fun streamFailuresAreNotConvertedToSuccess() {
        val output=object:java.io.OutputStream(){override fun write(value:Int){throw IOException("存储空间不足")}}
        assertThrows(IOException::class.java){copyBackupEntry(ByteArrayInputStream(byteArrayOf(1)),output,10)}
    }
    @Test fun zipWrapperDoesNotCloseTheSystemStreamEarly() {
        var closed=0
        val raw=object:ByteArrayOutputStream(){override fun close(){closed+=1;super.close()}}
        java.util.zip.ZipOutputStream(BackupNonClosingOutput(raw)).use{archive->archive.putNextEntry(java.util.zip.ZipEntry("test"));archive.write(1);archive.closeEntry()}
        assertEquals(0,closed)
        raw.close();assertEquals(1,closed)
    }
    @Test fun sha256IsStableAndKnown() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",backupSha256(ByteArrayInputStream("abc".toByteArray())))
    }
    @Test fun archiveOnlyAllowsKnownFlatFilesOnce() {
        BACKUP_ENTRY_NAMES.forEach{validateArchiveEntry(it,false,emptySet())}
        listOf("../ledger.json","/ledger.json","folder/ledger.json","unknown.json","background.bin/", "C:\\ledger.json").forEach{
            assertThrows(IllegalArgumentException::class.java){validateArchiveEntry(it,false,emptySet())}
        }
        assertThrows(IllegalArgumentException::class.java){validateArchiveEntry("ledger.json",true,emptySet())}
        assertThrows(IllegalArgumentException::class.java){validateArchiveEntry("ledger.json",false,setOf("ledger.json"))}
    }
    @Test fun archiveLimitsMatchThePublishedContract() {
        assertEquals(64L*1024*1024,BACKUP_ENTRY_LIMIT)
        assertEquals(128L*1024*1024,BACKUP_TOTAL_LIMIT)
    }
    @Test fun overlayAlwaysMatchesValidUserValues() {
        listOf(.45f,.50f,.64f,.65f,.70f,1f).forEach{assertEquals(it,backgroundOverlayAlpha(it),0f)}
    }
    @Test fun invalidOverlayValuesHaveSafeBounds() {
        assertEquals(.45f,backgroundOverlayAlpha(0f),0f)
        assertEquals(1f,backgroundOverlayAlpha(2f),0f)
        assertEquals(.84f,backgroundOverlayAlpha(Float.NaN),0f)
    }
    @Test fun reminderStartsAtSevenDaysAndRespectsSuccessfulExport() {
        val day=24L*60*60*1000
        assertFalse(backupReminderDue(true,true,1,0,7*day))
        assertTrue(backupReminderDue(true,true,1,0,7*day+1))
        assertFalse(backupReminderDue(true,true,1,8*day,10*day))
        assertTrue(backupReminderDue(true,true,1,8*day,15*day))
    }
    @Test fun reminderDoesNotNagEmptyDisabledOrFutureStates() {
        assertFalse(backupReminderDue(false,true,1,0,Long.MAX_VALUE))
        assertFalse(backupReminderDue(true,false,1,0,Long.MAX_VALUE))
        assertFalse(backupReminderDue(true,true,0,0,Long.MAX_VALUE))
        assertFalse(backupReminderDue(true,true,100,0,50))
    }
    @Test fun restoreOutcomesDistinguishDataAppearanceAndReminders() {
        val outcome=BackupRestoreOutcome(true,"外观未恢复",appearanceRestored=false,remindersRestored=false)
        assertTrue(outcome.restored)
        assertFalse(outcome.appearanceRestored)
        assertFalse(outcome.remindersRestored)
    }
}
