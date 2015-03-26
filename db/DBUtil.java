package anypackage;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

public class DBUtil {


    private static SQLiteDatabase mSqLiteDatabase;
    private static int mOpenCounter = 0;
    private static DBHelper mDBHelper;
    private static boolean mWritable = true;
    private static Context mContext;


    public static SQLiteDatabase getDb(){
        open(null);
        return mSqLiteDatabase;
    }

    public static SQLiteDatabase getDb(Context context) {
        open(context);
        return mSqLiteDatabase;
    }

    public static void open(Context context) {
        if (mDBHelper == null || mSqLiteDatabase == null) {
            context = context == null ? mContext : context;
            mDBHelper = new DBHelper(context);
            mSqLiteDatabase = getDatabase();
            mSqLiteDatabase.rawQuery("PRAGMA synchronous=OFF", null);
            mSqLiteDatabase.rawQuery("PRAGMA default_temp_store=MEMORY", null);
        }
        mOpenCounter++;
    }

    protected static SQLiteDatabase getDatabase() {
        SQLiteDatabase db = null;
        if (mWritable) {
            db = mDBHelper.getWritableDatabase();
        } else {
            db = mDBHelper.getReadableDatabase();
        }
        return db;
    }

    public static void release() {
        if (mDBHelper != null) {
            if (--mOpenCounter > 0) {
                return;
            }
            try {
                close();
            } catch (Exception e) {
//                Log.e("error", "error", e);
                return;
            }
        }
    }

    public static void setWritable() {
        if (!mWritable) {
            mWritable = true;
            close();
        }
    }

    public static void setReadable() {
        if (mWritable) {
            mWritable = false;
            close();
        }
    }

    public static void close() {
        if (mSqLiteDatabase != null && mSqLiteDatabase.inTransaction()) {
//            Log.e("error", "database closing in transaction", new Exception());
        }
        if (mDBHelper != null) {
//            mDBHelper.close();
//            mDBHelper = null;
        }
    }

    public static void setContext(Context context) {
        mContext = context;
    }


    public static void fullClose() {
        if (mSqLiteDatabase != null) {
            mSqLiteDatabase.close();
            mSqLiteDatabase = null;
        }
    }
}
