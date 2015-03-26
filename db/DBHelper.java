package anypackage;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DBHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "exampleDB";

    private static final int CURRENT_VERSION = 1;

    public static final String RESYNC_PHRASE = "RESYNC";

    public DBHelper(Context context) {
        super(context, DB_NAME, null, CURRENT_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        for (int i = 0; i < UpdateInfo.updates.length; i++){
            for(int j = 0; j < UpdateInfo.updates[i].length; j++){
                String sql = UpdateInfo.updates[i][j];
                if (sql.equals(RESYNC_PHRASE))
                    continue;
                db.execSQL(sql);
            }
        }
        db.setVersion(CURRENT_VERSION);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.beginTransaction();
        try{
            for (int i = oldVersion; i<newVersion; i++){
                for (int j = 0; j<UpdateInfo.updates[i].length;j++){
                    String sql = UpdateInfo.updates[i][j];

                    if (sql.equals(RESYNC_PHRASE))
                        setResyncNeeded();
                    else
                        db.execSQL(sql);
                }
            }
            db.setTransactionSuccessful();
        }
        catch (Exception ex){
            MUtil.report(ex);
        }
        finally {
            db.endTransaction();
        }
    }

    private void setResyncNeeded(){
        //TODO Resync setup
    }

    public static void removeDatabase() {
        DBUtil.fullClose();
        MApplication.getInstance().deleteDatabase(DB_NAME);
    }
}
