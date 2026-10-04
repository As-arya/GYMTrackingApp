package com.example.gymproject

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.os.SystemClock
import android.provider.Settings
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.gymproject.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Base64
import java.util.UUID
import com.example.gymproject.timer.RestTimerService

@RunWith(AndroidJUnit4::class)
class FeaturePersistenceTest {
    @Test fun expiredCountdownRestoresTheAlarmAndOwnerUntilAcknowledged() {
        val base=InstrumentationRegistry.getInstrumentation().targetContext
        val name="timer-test-${UUID.randomUUID()}"
        val context=object:ContextWrapper(base) {
            override fun getSharedPreferences(ignored:String,mode:Int):SharedPreferences=base.getSharedPreferences(name,Context.MODE_PRIVATE)
        }
        try {
            val prefs=context.getSharedPreferences("timer",0)
            prefs.edit().putInt("boot",Settings.Global.getInt(base.contentResolver,Settings.Global.BOOT_COUNT,-1))
                .putLong("deadline",SystemClock.elapsedRealtime()-1000).putBoolean("running",true)
                .putLong("total",30_000).putString("session","workout").putString("target","second").commit()
            val expired=RestTimerService.restore(context)
            assertTrue(expired.finished);assertTrue(expired.active);assertFalse(expired.running)
            assertEquals("workout",RestTimerService.state.value.sessionId)
            assertEquals("second",RestTimerService.state.value.targetKey)
            assertTrue(RestTimerService.restore(context).finished)
            prefs.edit().clear().commit()
            assertFalse(RestTimerService.restore(context).active)
            prefs.edit().putInt("boot",-999).putBoolean("finished",true).commit()
            assertFalse(RestTimerService.restore(context).active)
        } finally { base.deleteSharedPreferences(name);RestTimerService.restore(base) }
    }
    @Test fun selectedPhotoSurvivesSourceRemovalAndBackupRestore() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val source=File.createTempFile("avatar-test", ".png", context.cacheDir)
        val bitmap=Bitmap.createBitmap(1600,800,Bitmap.Config.ARGB_8888).apply{eraseColor(Color.argb(128,0,255,0))}
        source.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
        val photo=readProfilePhoto(context,Uri.fromFile(source));source.delete()
        val decoded=decodeProfilePhoto(photo)!!
        assertEquals(512,decoded.width);assertEquals(256,decoded.height)
        assertEquals(128,Color.alpha(decoded.getPixel(10,10)));decoded.recycle()
        val db=Room.inMemoryDatabaseBuilder(context,GymDatabase::class.java).build()
        try {
            val profile=Profile(name="Offline test",age=25,cm=170.0,goal=goals.first(),photo=photo)
            val original=GymState(true,profile,listOf(Measurement(20000,65.0)))
            importBackup(db,parseBackup(exportBackup(original)))
            assertEquals(profile,db.dao().profile().first())
            assertEquals(original.measurements,db.dao().measurements().first())
            val invalid=original.copy(profile=profile.copy(photo=Base64.getEncoder().encodeToString("not an image".toByteArray())))
            assertTrue(runCatching{importBackup(db,invalid)}.isFailure)
            assertEquals(profile,db.dao().profile().first())
            assertEquals(original.measurements,db.dao().measurements().first())
            importBackup(db,parseBackup(exportBackup(original.copy(profile=null))))
            assertNull(db.dao().profile().first())
            assertEquals(original.measurements,db.dao().measurements().first())
        } finally { db.close() }
    }

    @Test fun activeSessionBlocksImportAndDuplicateSetsStaySingle() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val db=Room.inMemoryDatabaseBuilder(context,GymDatabase::class.java).build()
        try {
            val profile=Profile(name="Offline test",age=25,cm=170.0,goal=goals.first())
            val target=Target(key="target",exerciseId="bench",reps=8,repsMax=12)
            val session=WorkoutSession("session",20000,1000,"Asia/Bangkok","Strength",null,targetsJson(listOf(target)))
            val log=SetLog(session.id,target.key,0,9,20.0,0,0.0,1100)
            db.dao().saveProfile(profile);db.dao().addSession(session)
            db.dao().addSet(log);assertEquals(-1L,db.dao().addSet(log))
            assertTrue(runCatching{importBackup(db,GymState(true,profile))}.isFailure)
            assertEquals(listOf(log),db.dao().logs().first())
            assertEquals(listOf(target),db.dao().active()!!.decoded())
        } finally { db.close() }
    }
}
