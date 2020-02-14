/**
 * Copyright (c) 2013, Redsolution LTD. All rights reserved.
 *
 * This file is part of Xabber project; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License, Version 3.
 *
 * Xabber is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License,
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */
package com.xabber.android.data.database;

import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;

import com.xabber.android.data.Application;
import com.xabber.android.data.OnClearListener;
import com.xabber.android.data.OnLoadListener;
import com.xabber.android.data.OnMigrationListener;
import com.xabber.android.data.database.sqlite.AccountTable;
import com.xabber.android.data.database.sqlite.AvatarTable;
import com.xabber.android.data.database.sqlite.AvatarXepTable;
import com.xabber.android.data.database.sqlite.DatabaseTable;
import com.xabber.android.data.database.sqlite.GroupTable;
import com.xabber.android.data.database.sqlite.NotifyVisibleTable;
import com.xabber.android.data.database.sqlite.OTRTable;
import com.xabber.android.data.database.sqlite.PrivateChatTable;
import com.xabber.android.data.database.sqlite.RoomTable;
import com.xabber.android.data.database.sqlite.VCardTable;
import com.xabber.android.data.log.LogManager;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

import io.realm.Realm;

/**
 * Helps to open, create, and upgrade the database file.
 * <p/>
 * All requests to database / file system MUST be called from background thread.
 *
 * @author alexander.ivanov
 */
public class DatabaseManager extends SQLiteOpenHelper implements
        OnLoadListener, OnClearListener {

    private static final String DATABASE_NAME = "xabber.db";
    private static final int DATABASE_VERSION = 71;

    private static final SQLiteException DOWNGRADE_EXCEPTION = new SQLiteException(
            "Database file was deleted");
    private static DatabaseManager instance;

    private final ArrayList<DatabaseTable> registeredTables;

    public static DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }

    private DatabaseManager() {
        super(Application.getInstance(), DATABASE_NAME, null, DATABASE_VERSION);
        registeredTables = new ArrayList<>();
    }

    public void addTables() {
        addTable(AccountTable.getInstance());
        addTable(RoomTable.getInstance());
        addTable(GroupTable.getInstance());
        addTable(PrivateChatTable.getInstance());
        addTable(NotifyVisibleTable.getInstance());
        addTable(VCardTable.getInstance());
        addTable(AvatarTable.getInstance());
        addTable(AvatarXepTable.getInstance());
        addTable(OTRTable.getInstance());
    }



    /**
     * Builds IN statement for specified collection of values.
     *
     * @param <T>
     * @param column
     * @param values
     * @return "column IN (value1, ... valueN)" or
     * "(column IS NULL AND column IS NOT NULL)" if ids is empty.
     */
    public static <T> String in(String column, Collection<T> values) {
        if (values.isEmpty())
            return new StringBuilder("(").append(column)
                    .append(" IS NULL AND ").append(column)
                    .append(" IS NOT NULL)").toString();
        StringBuilder builder = new StringBuilder(column);
        builder.append(" IN (");
        Iterator<T> iterator = values.iterator();
        while (iterator.hasNext()) {
            T value = iterator.next();
            if (value instanceof String)
                builder.append(DatabaseUtils.sqlEscapeString((String) value));
            else
                builder.append(value.toString());
            if (iterator.hasNext())
                builder.append(",");
        }
        builder.append(")");
        return builder.toString();
    }

    public static void execSQL(SQLiteDatabase db, String sql) {
        LogManager.iString(DatabaseManager.class.getName(), sql);
        db.execSQL(sql);
    }

    public static void dropTable(SQLiteDatabase db, String table) {
        execSQL(db, "DROP TABLE IF EXISTS " + table + ";");
    }

    public static void renameTable(SQLiteDatabase db, String table,
                                   String newTable) {
        execSQL(db, "ALTER TABLE " + table + " RENAME TO " + newTable + ";");
    }

    /**
     * Register new table.
     *
     * @param table
     */
    private void addTable(DatabaseTable table) {
        registeredTables.add(table);
    }

    @Override
    public void onLoad() {
        try {
            getWritableDatabase(); // Force onCreate or onUpgrade
        } catch (SQLiteException e) {
            if (e == DOWNGRADE_EXCEPTION) {
                // Downgrade occured
            } else {
                throw e;
            }
        }
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        for (DatabaseTable table : registeredTables)
            table.create(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion > newVersion) {
            LogManager.i(this, "Downgrading database from version "
                    + oldVersion + " to " + newVersion);
            File file = new File(db.getPath());
            file.delete();
            LogManager.i(this, "Database file was deleted");
            throw DOWNGRADE_EXCEPTION;
            // This will interrupt getWritableDatabase() call from
            // DatabaseManager's constructor.
        } else {
            LogManager.i(this, "Upgrading database from version " + oldVersion
                    + " to " + newVersion);
            while (oldVersion < newVersion) {
                oldVersion += 1;
                LogManager.i(this, "Migrate to version " + oldVersion);
                migrate(db, oldVersion);
                for (DatabaseTable table : registeredTables)
                    table.migrate(db, oldVersion);
                for (OnMigrationListener listener : Application.getInstance()
                        .getManagers(OnMigrationListener.class))
                    listener.onMigrate(oldVersion);
            }
        }
    }

    /**
     * Called on database migration.
     *
     * @param db
     * @param toVersion
     */
    private void migrate(SQLiteDatabase db, int toVersion) {
        switch (toVersion) {
            case 42:
                dropTable(db, "geolocs");
                dropTable(db, "locations");
                break;
            default:
                break;
        }
    }

    @Override
    public void onClear() {
        for (DatabaseTable table : registeredTables) {
            table.clear();
        }

        Realm.getDefaultInstance().deleteAll();
    }

}
