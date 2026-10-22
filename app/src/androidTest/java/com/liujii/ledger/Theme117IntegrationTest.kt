package com.liujii.ledger

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.liujii.ledger.data.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class Theme117IntegrationTest {
    private class Fixture:AutoCloseable {
        val base=InstrumentationRegistry.getInstrumentation().targetContext
        val prefix="themes117-${UUID.randomUUID()}"
        val directory=base.cacheDir.resolve(prefix).apply{mkdirs()}
        val context=object:ContextWrapper(base){
            override fun getFilesDir()=directory.resolve("files").apply{mkdirs()}
            override fun getCacheDir()=directory.resolve("cache").apply{mkdirs()}
            override fun getSharedPreferences(name:String,mode:Int)=base.getSharedPreferences("$prefix-$name",mode)
        }
        val db=Room.inMemoryDatabaseBuilder(context,LedgerDatabase::class.java).build()
        val repo=LedgerRepository(context,db.dao())
        val manager=FullBackupManager(context,repo)
        suspend fun seed(){db.dao().insertAccount(AccountEntity(id=1,name="合成账本"))}
        override fun close(){db.close()}
    }
    @Test fun freshInstallAndLegacyAppearanceHaveDifferentBackgroundDefaults() {
        Fixture().use { f->
            assertTrue(f.repo.loadAppearance().useThemeBackground)
            f.context.getSharedPreferences("appearance",0).edit().putString("backgroundType","image").putString("imageUri","content://example/old").putFloat("overlayAlpha",.61f).commit()
            val legacy=f.repo.loadAppearance()
            assertEquals("mint",legacy.themeId);assertFalse(legacy.useThemeBackground)
            assertEquals("content://example/old",legacy.imageUri);assertEquals(.61f,legacy.overlayAlpha,0f)
        }
    }
    @Test fun themeAndMotionPersistWithoutChangingBackgroundOrLock() {
        Fixture().use { f->
            val chosen=selectThemePreset(AppearanceSettings(themeMode="dark",imageUri="content://example/remember",overlayAlpha=.57f,appLockEnabled=true),"dusk_violet").copy(motionEnabled=false)
            f.repo.saveAppearance(chosen)
            assertEquals(chosen,LedgerRepository(f.context,f.db.dao()).loadAppearance())
        }
    }
    @Test fun zipRoundTripKeepsThemeButNeverExportsInactiveImageOrLock()=runBlocking {
        Fixture().use { f->
            f.seed()
            val chosen=selectThemePreset(AppearanceSettings(themeMode="dark",imageUri="content://private/inactive",overlayAlpha=.57f,appLockEnabled=true),"sea_blue").copy(motionEnabled=false)
            f.repo.saveAppearance(chosen)
            val file=f.directory.resolve("theme.zip")
            file.outputStream().use{f.manager.export(it)}
            val prepared=f.manager.prepare(Uri.fromFile(file))
            assertNull(prepared.preview.backgroundUri);assertNull(prepared.appearance?.imageUri)
            f.repo.saveAppearance(AppearanceSettings())
            assertTrue(f.manager.restore(prepared).restored)
            val restored=f.repo.loadAppearance()
            assertEquals("sea_blue",restored.themeId);assertTrue(restored.useThemeBackground)
            assertFalse(restored.motionEnabled);assertFalse(restored.appLockEnabled)
            assertEquals("dark",restored.themeMode);assertEquals(.57f,restored.overlayAlpha,0f)
        }
    }
    @Test fun oldZipAppearanceKeepsItsCustomColorsAndUnknownThemeFallsBack()=runBlocking {
        Fixture().use { f->
            f.seed();f.repo.saveAppearance(AppearanceSettings(colorStart=0xFF123456,colorEnd=0xFF654321))
            val source=f.directory.resolve("source.zip");source.outputStream().use{f.manager.export(it)}
            for(unknown in listOf(false,true)) {
                val target=f.directory.resolve("legacy-$unknown.zip")
                ZipInputStream(source.inputStream()).use{input->ZipOutputStream(target.outputStream()).use{output->
                    while(true){val entry=input.nextEntry?:break;var bytes=input.readBytes()
                        if(entry.name=="manifest.json"){
                            val json=JSONObject(String(bytes,Charsets.UTF_8));val appearance=json.getJSONObject("appearance")
                            appearance.remove("themeId");appearance.remove("useThemeBackground");appearance.remove("motionEnabled")
                            if(unknown)appearance.put("themeId","future-theme")
                            bytes=json.toString().toByteArray(Charsets.UTF_8)
                        }
                        output.putNextEntry(ZipEntry(entry.name));output.write(bytes);output.closeEntry()
                    }
                }}
                val appearance=f.manager.prepare(Uri.fromFile(target)).appearance!!
                assertEquals("mint",appearance.themeId);assertFalse(appearance.useThemeBackground)
                assertEquals(0xFF123456L,appearance.colorStart);assertEquals(0xFF654321L,appearance.colorEnd)
            }
        }
    }
}
