package no.nordicsemi.db;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import org.droidparts.persist.sql.AbstractDBOpenHelper;

public class DBOpenHelper extends AbstractDBOpenHelper {

    private static final String DB_FILE = "Evere.sql";
    private static final int DB_VERSION = 1;

    public DBOpenHelper(Context ctx) {
        super(ctx, DB_FILE, DB_VERSION);
    }

    @Override
    protected void onCreateTables(SQLiteDatabase sqLiteDatabase) {
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        /* TODO: add migration */
        dropTables(db);
        onCreate(db);
    }
}