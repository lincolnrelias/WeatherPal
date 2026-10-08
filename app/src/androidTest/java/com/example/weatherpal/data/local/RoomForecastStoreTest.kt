package com.example.weatherpal.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.weatherpal.data.local.*
import com.example.weatherpal.domain.model.*
import java.time.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomForecastStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: WeatherDatabase
    private lateinit var store: RoomForecastStore
    private val date = LocalDate.of(2026, 10, 7)

    private fun city(id: Long) = City(id, "City $id", null, null, 1.0, 2.0, "UTC")

    private fun days(t: Double = 22.0) =
        mapOf(
            date to DailyWeather(date, t, 0.0, 10.0, 0.0, 0.0),
            date.plusDays(1) to DailyWeather(date.plusDays(1), 20.0, 0.0, 10.0, 0.0, 0.0),
        )

    private suspend fun commit(id: Long, time: Long, t: Double = 22.0, capacity: Int = 3) =
        store.commit(city(id), days(t), Instant.ofEpochMilli(time), CachePolicy(capacity))

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(context, WeatherDatabase::class.java).build()
        store = RoomForecastStore(db)
    }

    @After
    fun close() {
        db.close()
    }

    @Test
    fun keyedUpdatesEvictionTiesAndPruning() = runBlocking {
        commit(2, 1)
        commit(1, 1)
        commit(3, 2)
        assertEquals(listOf(3L, 1L, 2L), store.cities().first().map { it.city.id })
        commit(4, 3)
        assertNull(db.dao().read(1))
        assertNotNull(db.dao().read(2))
        assertFalse(commit(2, 4))
        assertEquals(listOf(2L, 4L, 3L), store.cities().first().map { it.city.id })
        store.prune(CachePolicy(1))
        assertEquals(listOf(2L), store.cities().first().map { it.city.id })
    }

    @Test
    fun unchangedMetadataOnlyAndOnlyChangedColumnWritten() = runBlocking {
        commit(1, 1)
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER prohibit_day_update BEFORE UPDATE ON days BEGIN SELECT RAISE(ABORT, 'unchanged day written'); END"
        )
        assertFalse(commit(1, 2))
        assertEquals(2, db.dao().read(1)!!.header.updated.toInt())
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER prohibit_day_update")
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER protect_precipitation BEFORE UPDATE OF precipitation ON days BEGIN SELECT RAISE(ABORT, 'unchanged column written'); END"
        )
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER protect_other_day BEFORE UPDATE ON days WHEN OLD.date = '2026-10-08' BEGIN SELECT RAISE(ABORT, 'unchanged date written'); END"
        )
        assertTrue(commit(1, 3, 23.0))
        assertEquals(
            23.0,
            db.dao().read(1)!!.days.first { it.date == date.toString() }.temperature!!,
            0.0,
        )
    }

    @Test
    fun rollbackAfterEvictionAndCoherentObservations() = runBlocking {
        commit(1, 1, capacity = 1)
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_insert BEFORE INSERT ON days WHEN NEW.cityId = 2 BEGIN SELECT RAISE(ABORT, 'forced storage failure'); END"
        )
        try {
            commit(2, 2, capacity = 1)
            fail("expected rollback")
        } catch (e: android.database.sqlite.SQLiteException) {
            /* transaction must restore evicted header and days */
        }
        assertNotNull(db.dao().read(1))
        assertNull(db.dao().read(2))
        assertEquals(1L, db.dao().read(1)!!.header.updated)
        val observed = mutableListOf<ForecastSnapshot>()
        val firstSeen = CompletableDeferred<Unit>()
        val updateSeen = CompletableDeferred<Unit>()
        val collect =
            launch(Dispatchers.Default) {
                store.forecast(1).filterNotNull().collect {
                    synchronized(observed) { observed += it }
                    firstSeen.complete(Unit)
                    if (it.lastSuccessfulUpdate.toEpochMilli() == 3L) updateSeen.complete(Unit)
                }
            }
        withTimeout(5000) { firstSeen.await() }
        commit(1, 3, 24.0, 1)
        withTimeout(5000) { updateSeen.await() }
        collect.cancelAndJoin()
        assertTrue(observed.size >= 2)
        observed.forEach { snapshot ->
            assertEquals(
                if (snapshot.lastSuccessfulUpdate.toEpochMilli() == 1L) 22.0 else 24.0,
                snapshot.days[date]!!.temperatureC!!,
                0.0,
            )
        }
    }

    @Test
    fun reopenPersistsAndPartialResponseDeletesOldDates() = runBlocking {
        val name = "weatherpal-persistence-test.db"
        context.deleteDatabase(name)
        var disk = Room.databaseBuilder(context, WeatherDatabase::class.java, name).build()
        try {
            RoomForecastStore(disk).commit(city(5), days(), Instant.ofEpochMilli(10), CachePolicy())
            disk.close()
            disk = Room.databaseBuilder(context, WeatherDatabase::class.java, name).build()
            assertEquals(2, disk.dao().read(5)!!.days.size)
            RoomForecastStore(disk)
                .commit(
                    city(5),
                    mapOf(date to days()[date]!!),
                    Instant.ofEpochMilli(11),
                    CachePolicy(),
                )
            assertEquals(1, disk.dao().read(5)!!.days.size)
        } finally {
            disk.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun overlappingCityCommitsRemainCapacitySafe() = runBlocking {
        coroutineScope {
            (1L..8L).map { id -> async(Dispatchers.IO) { commit(id, id, capacity = 2) } }.awaitAll()
        }
        assertEquals(2, store.cities().first().size)
        store.cities().first().forEach { assertEquals(2, db.dao().read(it.city.id)!!.days.size) }
    }
}
