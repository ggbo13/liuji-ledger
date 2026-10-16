package com.liujii.ledger

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.net.Uri
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.liujii.ledger.data.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class FullBackup116IntegrationTest {
    private class Fixture : AutoCloseable {
        private val base=InstrumentationRegistry.getInstrumentation().targetContext
        private val prefix="backup116-${UUID.randomUUID()}"
        private val directory=File(base.cacheDir,prefix).apply{mkdirs()}
        val context=object:ContextWrapper(base){
            override fun getFilesDir()=directory.resolve("files").apply{mkdirs()}
            override fun getCacheDir()=directory.resolve("cache").apply{mkdirs()}
            override fun getSharedPreferences(name:String,mode:Int)=base.getSharedPreferences("$prefix-$name",mode)
        }
        val db=Room.inMemoryDatabaseBuilder(context,LedgerDatabase::class.java).build()
        val repo=LedgerRepository(context,db.dao())
        val manager=FullBackupManager(context,repo)
        suspend fun seed() {
            db.dao().insertAccount(AccountEntity(id=1,name="测试账本"))
            db.dao().insertCategory(CategoryEntity(id=7,name="测试分类",type=MoneyType.EXPENSE,icon="测",color=0xFFE76F64))
            db.dao().insertPaymentMethod(PaymentMethodEntity(id=2,name="测试方式",color=0xFF4D8BB8))
            db.dao().insertTransaction(TransactionEntity(id=42,type=MoneyType.EXPENSE,amountCents=5678,categoryId=7,accountId=1,occurredAt=1700000000000,note="测试记录",paymentMethodId=2))
        }
        fun file(name:String)=context.filesDir.resolve(name)
        fun image():File=file("image-${UUID.randomUUID()}.png").also{file->
            val bitmap=Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888).apply{eraseColor(android.graphics.Color.BLUE)}
            file.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
        }
        suspend fun export(photo:Boolean=false):File {
            if(photo)repo.saveAppearance(AppearanceSettings(themeMode="dark",backgroundType="image",imageUri=Uri.fromFile(image()).toString(),overlayAlpha=.47f,appLockEnabled=true))
            return file("export-${UUID.randomUUID()}.zip").also{target->target.outputStream().use{manager.export(it)}}
        }
        override fun close(){db.close()}
    }

    private fun rewritten(source:File,target:File,change:(String,ByteArray)->ByteArray):File {
        ZipInputStream(source.inputStream()).use{input->ZipOutputStream(target.outputStream()).use{output->
            while(true){val entry=input.nextEntry?:break;output.putNextEntry(ZipEntry(entry.name));output.write(change(entry.name,input.readBytes()));output.closeEntry()}
        }}
        return target
    }

    @Test fun completeBackupRestoresIdsAmountsAppearanceAndImageWithoutMovingAppLock()=runBlocking {
        Fixture().use{f->
            f.seed();val original=f.db.dao().snapshot();val exported=f.export(photo=true)
            val prepared=f.manager.prepare(Uri.fromFile(exported))
            assertEquals(1,prepared.preview.transactionCount);assertTrue(prepared.preview.hasAppearance);assertNotNull(prepared.preview.backgroundUri)
            f.db.dao().updateTransaction(original.transactions.single().copy(amountCents=100))
            f.repo.saveAppearance(AppearanceSettings(themeMode="light",appLockEnabled=false))
            val result=f.manager.restore(prepared)
            assertTrue(result.restored);assertTrue(result.appearanceRestored)
            assertEquals(original,f.db.dao().snapshot())
            val restored=f.repo.loadAppearance()
            assertEquals("dark",restored.themeMode);assertEquals(.47f,restored.overlayAlpha,0f);assertFalse(restored.appLockEnabled)
            assertEquals(prepared.imageHash,f.context.contentResolver.openInputStream(Uri.parse(restored.imageUri))!!.use(::backupSha256))
            val protection=f.manager.latestRecovery()!!
            assertEquals(100L,f.manager.prepare(Uri.fromFile(protection)).payload.transactions.single().amountCents)
        }
    }
    @Test fun previewAndCancellationNeverChangeCurrentDataOrAppearance()=runBlocking {
        Fixture().use{f->f.seed();val file=f.export(true);val before=f.db.dao().snapshot();val appearance=f.repo.loadAppearance()
            f.manager.prepare(Uri.fromFile(file));assertEquals(before,f.db.dao().snapshot());assertEquals(appearance,f.repo.loadAppearance());assertNull(f.manager.latestRecovery())}
    }
    @Test fun oldJsonVersionsRemainImportableAndKeepCurrentAppearance()=runBlocking {
        Fixture().use{f->f.seed();val appearance=AppearanceSettings(themeMode="dark",overlayAlpha=.62f);f.repo.saveAppearance(appearance)
            val original=f.file("original.json");original.outputStream().use{f.repo.exportJson(it)}
            for(version in 1..3){
                val old=f.file("legacy-$version.json").apply{writeText(JSONObject(original.readText()).put("version",version).toString())}
                val prepared=f.manager.prepare(Uri.fromFile(old));assertFalse(prepared.preview.hasAppearance)
                assertTrue(f.manager.restore(prepared).restored);assertEquals(appearance,f.repo.loadAppearance())
            }
            assertEquals(3,f.manager.recoveryFiles().size)
        }
    }
    @Test fun changedLedgerFailsChecksumBeforeAnyDatabaseWrite()=runBlocking {
        Fixture().use{f->f.seed();val original=f.export();val before=f.db.dao().snapshot()
            val bad=rewritten(original,f.file("changed.zip")){name,bytes->if(name=="ledger.json")bytes+byteArrayOf(32)else bytes}
            assertTrue(runCatching{f.manager.prepare(Uri.fromFile(bad))}.isFailure);assertEquals(before,f.db.dao().snapshot())
        }
    }
    @Test fun unknownArchiveVersionAndInvalidAppearanceAreRejected()=runBlocking {
        Fixture().use{f->f.seed();val original=f.export();val before=f.db.dao().snapshot()
            for(kind in listOf("version","alpha")){
                val bad=rewritten(original,f.file("$kind.zip")){name,bytes->if(name=="manifest.json"){
                    val json=JSONObject(String(bytes,Charsets.UTF_8));if(kind=="version")json.put("version",99)else json.getJSONObject("appearance").put("overlayAlpha",0)
                    json.toString().toByteArray(Charsets.UTF_8)
                }else bytes}
                assertTrue(runCatching{f.manager.prepare(Uri.fromFile(bad))}.isFailure);assertEquals(before,f.db.dao().snapshot())
            }
        }
    }
    @Test fun pathTraversalAndEmptyFilesAreRejected()=runBlocking {
        Fixture().use{f->f.seed();val before=f.db.dao().snapshot()
            val bad=f.file("traversal.zip");ZipOutputStream(bad.outputStream()).use{it.putNextEntry(ZipEntry("../escape"));it.write(1);it.closeEntry()}
            val empty=f.file("empty.json").apply{writeText("")}
            for(file in listOf(bad,empty))assertTrue(runCatching{f.manager.prepare(Uri.fromFile(file))}.isFailure)
            assertEquals(before,f.db.dao().snapshot());assertFalse(f.context.filesDir.resolve("escape").exists())
        }
    }
    @Test fun invalidCurrentImageRequiresExplicitPhotoOmission()=runBlocking {
        Fixture().use{f->f.seed();f.repo.saveAppearance(AppearanceSettings(backgroundType="image",imageUri=Uri.fromFile(f.file("missing.png")).toString()))
            val first=f.file("failed.zip")
            assertTrue(runCatching{first.outputStream().use{f.manager.export(it)}}.exceptionOrNull() is BackgroundUnavailableException)
            assertEquals(0L,f.manager.status().lastExportAt)
            val second=f.file("without-photo.zip");second.outputStream().use{f.manager.export(it,withoutImage=true)}
            val prepared=f.manager.prepare(Uri.fromFile(second));assertNull(prepared.preview.backgroundUri);assertEquals("gradient",prepared.appearance?.backgroundType)
        }
    }
    @Test fun protectionWriteFailureLeavesDataAndAppearanceUntouched()=runBlocking {
        Fixture().use{f->f.seed();val file=f.export();val prepared=f.manager.prepare(Uri.fromFile(file))
            f.db.dao().updateTransaction(prepared.payload.transactions.single().copy(amountCents=111))
            val before=f.db.dao().snapshot();val appearance=f.repo.loadAppearance()
            f.file("backup-recovery").writeText("测试：模拟目录不可写")
            assertTrue(runCatching{f.manager.restore(prepared)}.isFailure)
            assertEquals(before,f.db.dao().snapshot());assertEquals(appearance,f.repo.loadAppearance());assertFalse(prepared.consumed)
        }
    }
    @Test fun onePreparedBackupCannotBeRestoredTwice()=runBlocking {
        Fixture().use{f->f.seed();val prepared=f.manager.prepare(Uri.fromFile(f.export()))
            assertTrue(f.manager.restore(prepared).restored)
            assertTrue(runCatching{f.manager.restore(prepared)}.isFailure)
            assertEquals(1,f.manager.recoveryFiles().size)
        }
    }
    @Test fun concurrentConfirmationsOnlyRestoreOnce()=runBlocking {
        Fixture().use{f->f.seed();val prepared=f.manager.prepare(Uri.fromFile(f.export()))
            val results=(1..3).map{async{runCatching{f.manager.restore(prepared)}}}.awaitAll()
            assertEquals(1,results.count{it.isSuccess});assertEquals(1,f.manager.recoveryFiles().size)
        }
    }
    @Test fun successTimestampOnlyMovesAfterTheDestinationHasClosed()=runBlocking {
        Fixture().use{f->f.seed();f.export();assertEquals(0L,f.manager.status().lastExportAt)
            f.manager.markExportSuccessful();assertTrue(f.manager.status().lastExportAt>0)
            assertFalse(f.manager.status(true).due)
        }
    }
    @Test fun protectionNamesAreUniqueAndNeverOverwriteEarlierFiles()=runBlocking {
        Fixture().use{f->f.seed();val exported=f.export()
            repeat(2){f.manager.restore(f.manager.prepare(Uri.fromFile(exported)))}
            val files=f.manager.recoveryFiles();assertEquals(2,files.size);assertEquals(2,files.map{it.name}.toSet().size)
            files.forEach{assertEquals(5678L,f.manager.prepare(Uri.fromFile(it)).payload.transactions.single().amountCents)}
        }
    }
    @Test fun failedOutputDoesNotRecordSuccessfulBackup()=runBlocking {
        Fixture().use{f->f.seed();val output=object:java.io.OutputStream(){override fun write(value:Int){throw IOException("测试存储失败")}}
            assertTrue(runCatching{f.manager.export(output)}.isFailure);assertEquals(0L,f.manager.status().lastExportAt)
        }
    }
    @Test fun archiveNeverContainsLockSettingsOrDeviceImageUri()=runBlocking {
        Fixture().use{f->f.seed();val file=f.export(true)
            ZipInputStream(file.inputStream()).use{input->
                assertEquals("manifest.json",input.nextEntry.name)
                val manifest=String(input.readBytes(),Charsets.UTF_8)
                assertFalse(manifest.contains("appLockEnabled"));assertFalse(manifest.contains("imageUri"));assertFalse(manifest.contains(f.context.filesDir.absolutePath))
            }
        }
    }
    @Test fun interruptedRestoreOnlyWarnsAndNeverAutomaticallyOverwritesData()=runBlocking {
        Fixture().use{f->f.seed();val before=f.db.dao().snapshot()
            f.context.getSharedPreferences("backup_status",Context.MODE_PRIVATE).edit().putString("pendingRecovery","测试保护点").commit()
            assertTrue(FullBackupManager(f.context,f.repo).status(true).interrupted);assertEquals(before,f.db.dao().snapshot())
        }
    }
    @Test fun changedPhotoAfterPreviewIsRejectedBeforeRestore()=runBlocking {
        Fixture().use{f->f.seed();val prepared=f.manager.prepare(Uri.fromFile(f.export(true)));val before=f.db.dao().snapshot()
            prepared.image!!.appendBytes(byteArrayOf(1))
            assertTrue(runCatching{f.manager.restore(prepared)}.isFailure);assertEquals(before,f.db.dao().snapshot())
        }
    }
}
