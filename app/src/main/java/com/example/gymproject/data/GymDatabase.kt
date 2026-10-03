package com.example.gymproject.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity data class Profile(@PrimaryKey val id: Int = 1, val name: String, val age: Int, val cm: Double, val goal: String, val target: String = "")
@Entity data class Measurement(@PrimaryKey val day: Long, val kg: Double)
@Entity data class PlanRevision(@PrimaryKey val id: String, val title: String, val start: Long, val weeks: Int, val effective: Long, val created: Long, val days: String) {
    @Ignore fun decoded() = readDays(days)
}
@Entity data class WorkoutSession(@PrimaryKey val id: String, val day: Long, val started: Long, val zone: String, val title: String, val revision: String?, val targets: String, val ended: Long? = null) {
    @Ignore fun decoded() = readTargets(targets)
}
@Entity(primaryKeys = ["sessionId", "targetKey", "index"], foreignKeys = [ForeignKey(entity = WorkoutSession::class,
    parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE)], indices = [Index("sessionId")])
data class SetLog(val sessionId: String, val targetKey: String, val index: Int, val reps: Int, val kg: Double, val seconds: Int, val km: Double, val at: Long)

@Dao interface GymDao {
    @Query("SELECT * FROM Profile WHERE id = 1") fun profile(): Flow<Profile?>
    @Query("SELECT * FROM Measurement ORDER BY day") fun measurements(): Flow<List<Measurement>>
    @Query("SELECT * FROM PlanRevision ORDER BY created") fun plans(): Flow<List<PlanRevision>>
    @Query("SELECT * FROM WorkoutSession ORDER BY started DESC") fun sessions(): Flow<List<WorkoutSession>>
    @Query("SELECT * FROM SetLog ORDER BY at") fun logs(): Flow<List<SetLog>>
    @Query("SELECT * FROM WorkoutSession WHERE ended IS NULL LIMIT 1") suspend fun active(): WorkoutSession?
    @Upsert suspend fun saveProfile(value: Profile)
    @Upsert suspend fun saveMeasurement(value: Measurement)
    @Insert suspend fun addPlan(value: PlanRevision)
    @Insert suspend fun addSession(value: WorkoutSession)
    @Update suspend fun updateSession(value: WorkoutSession)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun addSet(value: SetLog): Long
    @Update suspend fun updateSet(value: SetLog)
    @Query("DELETE FROM SetLog WHERE sessionId = :session AND targetKey = :key AND `index` = :index") suspend fun deleteSet(session: String, key: String, index: Int)
    @Query("DELETE FROM WorkoutSession WHERE id = :id") suspend fun deleteSession(id: String)
    @Query("DELETE FROM PlanRevision WHERE id = :id") suspend fun deletePlan(id: String)
    @Query("DELETE FROM SetLog") suspend fun clearLogs()
    @Query("DELETE FROM WorkoutSession") suspend fun clearSessions()
    @Query("DELETE FROM PlanRevision") suspend fun clearPlans()
    @Query("DELETE FROM Measurement") suspend fun clearMeasurements()
    @Query("DELETE FROM Profile") suspend fun clearProfile()
}

@Database(entities = [Profile::class, Measurement::class, PlanRevision::class, WorkoutSession::class, SetLog::class], version = 1, exportSchema = true)
abstract class GymDatabase : RoomDatabase() { abstract fun dao(): GymDao }
