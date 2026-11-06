package cl.bovinoapp.rumboaustral.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Point::class, Label::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun pointDao(): PointDao
    abstract fun labelDao(): LabelDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "rumbo-austral.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .addCallback(object : Callback() {
                        // Solo instalaciones nuevas; en las existentes siembra la migración.
                        override fun onCreate(db: SupportSQLiteDatabase) = seedDefaultLabels(db)
                    })
                    .build().also { instance = it }
            }

        /** Agrega la tabla de etiquetas y las tres columnas de etiqueta en points (con FK SET NULL). */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS labels (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "kind TEXT NOT NULL, name TEXT NOT NULL, color INTEGER NOT NULL, sortOrder INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_labels_kind ON labels (kind)")

                // SQLite no permite agregar FKs con ALTER TABLE de forma que Room las valide: se recrea la tabla.
                db.execSQL(
                    "CREATE TABLE points_new (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "name TEXT NOT NULL, latitude REAL NOT NULL, longitude REAL NOT NULL, " +
                        "notes TEXT NOT NULL, createdAt INTEGER NOT NULL, " +
                        "typeLabelId INTEGER, statusLabelId INTEGER, zoneLabelId INTEGER, " +
                        "FOREIGN KEY(typeLabelId) REFERENCES labels(id) ON UPDATE NO ACTION ON DELETE SET NULL, " +
                        "FOREIGN KEY(statusLabelId) REFERENCES labels(id) ON UPDATE NO ACTION ON DELETE SET NULL, " +
                        "FOREIGN KEY(zoneLabelId) REFERENCES labels(id) ON UPDATE NO ACTION ON DELETE SET NULL)"
                )
                db.execSQL(
                    "INSERT INTO points_new (id, name, latitude, longitude, notes, createdAt) " +
                        "SELECT id, name, latitude, longitude, notes, createdAt FROM points"
                )
                db.execSQL("DROP TABLE points")
                db.execSQL("ALTER TABLE points_new RENAME TO points")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_points_typeLabelId ON points (typeLabelId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_points_statusLabelId ON points (statusLabelId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_points_zoneLabelId ON points (zoneLabelId)")

                seedDefaultLabels(db)
            }
        }

        private fun seedDefaultLabels(db: SupportSQLiteDatabase) {
            val defaults = listOf(
                Triple(LabelKind.TYPE, "Aguada", 0xFF1E88E5),
                Triple(LabelKind.TYPE, "Esquinero", 0xFF757575),
                Triple(LabelKind.TYPE, "Poste", 0xFFFB8C00),
                Triple(LabelKind.TYPE, "Animal muerto", 0xFF212121),
                Triple(LabelKind.TYPE, "Otro", 0xFFFDD835),
                Triple(LabelKind.STATUS, "Pendiente", 0xFFE53935),
                Triple(LabelKind.STATUS, "Resuelto", 0xFF43A047)
            )
            val orderByKind = mutableMapOf<LabelKind, Int>()
            for ((kind, name, color) in defaults) {
                val order = orderByKind.getOrDefault(kind, 0)
                orderByKind[kind] = order + 1
                db.execSQL(
                    "INSERT INTO labels (kind, name, color, sortOrder) VALUES (?, ?, ?, ?)",
                    arrayOf<Any>(kind.name, name, color.toInt(), order)
                )
            }
        }
    }
}
