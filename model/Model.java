package anypackage;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.util.*;
import java.util.Map.Entry;

public abstract class Model extends EventHub implements DataModel {

    public static final String ID_COLUMN = "id";
    public static final String CHANGED_COLUMN = "changed";
    public static final String DELETION_LOG = "deletion_log";
    public static final String DELETE_ID = "id";
    public static final String DELETE_TABLE = "`table`";
    public static final String DELETE_DATE = "`date`";
    public UUID id;
    public int changed;
    protected boolean mIsNewRecord = true;
    protected HashMap<String, Object> originalValues;
    protected boolean isFromJSON = false;
    protected boolean isFromCursor = false;

    protected Model() {
    }

    protected Model(UUID id) throws ObjectNotFound {
        if (id == null) return;
        SQLiteDatabase db = DBUtil.getDb();
        Cursor cursor = null;
        try{
        cursor = db.query("`" + getTableName() + "`", null, "id=?", new String[]{id.toString()}, null, null, null);
        if (cursor.getCount() == 0)
            throw new ObjectNotFound();
        cursor.moveToFirst();
        mIsNewRecord = false;
        fromCursor(cursor);
        } finally{
        	if(cursor!=null){
        		cursor.close();
        	}
        }
        DBUtil.release();
    }
    
    protected static String makePlaceholders(int len) {
        if (len < 1) {
            // It will lead to an invalid query anyway ..
            throw new RuntimeException("No placeholders");
        } else {
            StringBuilder sb = new StringBuilder(len * 2 - 1);
            sb.append("?");
            for (int i = 1; i < len; i++) {
                sb.append(",?");
            }
            return sb.toString();
        }
    }
	

    public static String getClassTableName(Class<? extends Model> model) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException, InstantiationException {
        return (String) model.getMethod("getTableName").invoke(model.newInstance());
    }

    public static String getEscapedClassTableName(Class<? extends Model> model) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        return "`" + getClassTableName(model) + "`";
    }

    public static List<Model> loadAll(Class<? extends Model> model) throws NoSuchFieldException, IllegalAccessException, InstantiationException, NoSuchMethodException, InvocationTargetException {
        String table = getEscapedClassTableName(model);
        SQLiteDatabase db = DBUtil.getDb();
        Cursor c = null;
        List<Model> models = new ArrayList<Model>();
        try{
        c = db.query(table, null, "deleted = 0 and deleted is not null", null, null, null, null);
        if (c.moveToFirst()) {
            do {
                Model obj = model.newInstance();
                obj.fromCursor(c);
                models.add(obj);
            } while (c.moveToNext());
        }
        }
        finally{
        	if(c!=null){c.close();}
        }
        DBUtil.release();
        return models;
    }

    // instrumentation
    protected Object readCursor(Class c, Cursor cur, int field) {
        Object res = null;
        String val = cur.getString(field);

        // fast ways
        if (val == null || val.equals("null")) {
            if (c.equals(Boolean.class)) {
                return false;
            } else {
                return null;
            }
        }
        if (c == Long.class || c == long.class) {
            return cur.getLong(field);
        } else if (c == String.class) {
            return cur.getString(field);
        } else if (c == BigDecimal.class) {
            return new BigDecimal(val).stripTrailingZeros();
        } else if (c == Integer.class || c == int.class) {
            return cur.getInt(field);
        } else if (c == Float.class || c == float.class) {
            return cur.getFloat(field);
        } else if (c == Boolean.class || c == boolean.class) {
            if (val.equals("false")) return false;
            if (val.equals("0")) return false;
            return true;
        } else if (c == Date.class) {
            return new Date(cur.getLong(field) * 1000l);
        } else if (c.isEnum()) {
            return Enum.valueOf(c, val);
        } else if (c == UUID.class) {
            return UUID.fromString(val);
        } else if (c == TwoDimensionalCoordinate.class) {
            return new TwoDimensionalCoordinate(val);
        }

        // slooooow way
        try {
            if (c.equals(Boolean.class)) {
                if (cur.getString(field).equals("false") || cur.getString(field).equals("0")) {
                    return false;
                } else {
                    return true;
                }
            }

            res = c.getDeclaredConstructor(String.class).newInstance(val);
        } catch (Exception e) {
//            log.error("Failed to read field " + field + " of type " + c.getName() + " from Cursor", e);
        }
        return res;
    }

    public abstract String getTableName();

    public String getServerName() {
        return getTableName();
    }

    public boolean allowExport() {
        return true;
    }

    ;

    public abstract Model getInstance();

    public ContentValues toContenValues() {
        Field[] fields = getClass().getDeclaredFields();
        ContentValues cv = new ContentValues(fields.length);
        for (Field field : fields) {
            try {
                String name = "`" + field.getName() + "`";
                Object value = field.get(this);
                Class c = field.getType();
                if (value == null) {
                    cv.putNull(name);
                } else if (c == Long.class || c == long.class) {
                    cv.put(name, (Long) value);
                } else if (c == String.class) {
                    cv.put(name, (String) value);
                } else if (c == Integer.class || c == int.class) {
                    cv.put(name, (Integer) value);
                } else if (c == Float.class || c == float.class) {
                    cv.put(name, (Float) value);
                } else if (c == Boolean.class || c == boolean.class) {
                    cv.put(name, (Boolean) value);
                } else if (c == Date.class) {
//                    cv.put(name, Math.round(((Date) value).getTime() / 1000L));
                    cv.put(name, Long.valueOf(((Date) value).getTime()) / 1000L);
                } else {
                    cv.put(name, value.toString());
                }

            } catch (IllegalAccessException e) {
                continue;
            }
        }

        if (id != null)
            cv.put("`" + ID_COLUMN + "`", id.toString());

        if (allowExport())
            cv.put("`" + CHANGED_COLUMN + "`", changed);
        

        return cv;
    }

    public void fromJson(JSONObject object) {
        Iterator<String> keys = object.keys();
        isFromJSON = true;
        Class<? extends Model> me = getClass();
        do {
            try {
                String key = keys.next();
                Field target = me.getField(key);
                Object value = readJson(object, target.getType(), key);
                target.set(this, value);
            } catch (Exception e) {
//                Log.e(getClass().getName(), "error", e);
            }
        } while (keys.hasNext());
    }

    protected Object readJson(JSONObject cur, Class c, String field) throws JSONException {
        Object res = null;
        String val = cur.getString(field);

        // fast ways
        if (val == null || val.equals("null")) {
            if (c.equals(Boolean.class)) {
                return false;
            } else {
                return null;
            }
        }
        if (c == Long.class || c == long.class) {
            return cur.getLong(field);
        } else if (c == String.class) {
            return cur.getString(field);
        } else if (c == BigDecimal.class) {
            return new BigDecimal(val).stripTrailingZeros();
        } else if (c == Integer.class || c == int.class) {
            return cur.getInt(field);
        } else if (c == Float.class || c == float.class) {
            return cur.getDouble(field);
        } else if (c == Boolean.class || c == boolean.class) {
            if (val.equals("false")) return false;
            if (val.equals("0")) return false;
            return true;
        } else if (c == Date.class) {
            return new Date(cur.getLong(field) * 1000l);
        } else if (c == TwoDimensionalCoordinate.class) {
            return new TwoDimensionalCoordinate(val);
        } else if (c.isEnum()) {
            return Enum.valueOf(c, val);
        } else if (c == UUID.class) {
            return UUID.fromString(val);
        }

        // slooooow way
        try {
            if (c.equals(Boolean.class)) {
                if (cur.getString(field).equals("false") || cur.getString(field).equals("0")) {
                    return false;
                } else {
                    return true;
                }
            }

            res = c.getDeclaredConstructor(String.class).newInstance(val);
        } catch (Exception e) {
//            log.error("Failed to read field " + field + " of type " + c.getName() + " from Cursor", e);
        }
        return res;
    }

    public void fromCursor(Cursor cursor) {
        isFromCursor = true;
        mIsNewRecord = false;
        String[] columns = cursor.getColumnNames();
        originalValues = new HashMap<String, Object>(columns.length);
        Class<? extends Model> me = getClass();
        for (int i = 0; i < columns.length; i++) {
            String column = columns[i];
            try {
                Field target = me.getField(column);
                Object value = readCursor(target.getType(), cursor, i);
                target.set(this, value);
                originalValues.put(column, value);
            } catch (NoSuchFieldException e) {
            	//e.printStackTrace();
                continue;
            } catch (IllegalAccessException e) {
            	//e.printStackTrace();
                continue;
            }
        }
    }

    public void save() {
        onSaveRequest();
        SQLiteDatabase db = DBUtil.getDb();

        if (!isFromJSON) {
            changed = MUtil.getUnixTime();
            Log.d("MODEL", "SAVE MODEL " + getTableName() + "; CHANGED: " + changed);
        } else if (id != null) {
            Cursor c = db.query("`" + getTableName() + "`", new String[]{"id"}, "id = ?",
                    new String[]{id.toString()}, null, null, null);
            mIsNewRecord = c.getCount() == 0;
            c.close();
        }
        if (mIsNewRecord) {
            if (id == null)
                id = UUID.randomUUID();
            if (!fireEvent(BeforeInsertEvent.class)) return;
            ContentValues cv = toContenValues();
            db.insertOrThrow("`" + getTableName() + "`", null, cv);
            mIsNewRecord = false;
            fireEvent(AfterInsertEvent.class);
        } else {
            if (!fireEvent(BeforeUpdateEvent.class)) return;
            ContentValues cv = toContenValues();
            db.update("`" + getTableName() + "`", cv, "id=?", new String[]{String.valueOf(id)});
            fireEvent(AfterUpdateEvent.class);
        }
        DBUtil.release();
    }

    public void delete() {
        SQLiteDatabase db = DBUtil.getDb();
        onDeleteRequest();
        ContentValues update = new ContentValues();
        update.put("deleted", true);
        update.put("changed", MUtil.getUnixTime());
        if (!fireEvent(BeforeDeleteEvent.class)) return;
        int affected = db.update("`" + getTableName() + "`", update, "id=?", new String[]{id.toString()});
        fireEvent(AfterDeleteEvent.class);
        DBUtil.release();
    }

    @Override
    public ArrayList<ContentValues> exportData(long from) {
        ArrayList<ContentValues> result = new ArrayList<ContentValues>();
        if (!allowExport())
            return result;

        SQLiteDatabase db = DBUtil.getDb();
        Cursor c=null;
        try{
        c = db.query("`" + getTableName() + "`", null, "changed>=?", new String[]{String.valueOf(from)}, null, null, null);

        if (c.getCount() == 0){
            c.close();
        	return result;
        }

        c.moveToFirst();

        try {
            Model m = getInstance();
            do {
                result.add(m.exportRow(c));
            } while (c.moveToNext());
        } catch (Exception e) {
            Log.d(this.getClass().toString(), "Wrong class in mClass");
        }

        } finally{
        	if(c!=null){c.close();}
        }

        DBUtil.release();

        return result;
    }

    @Override
    public boolean importData(ArrayList<Model> model) {
        return false;
    }

    public ContentValues exportRow(Cursor c) {
        ContentValues result = new ContentValues(c.getColumnCount());
        DatabaseUtils.cursorRowToContentValues(c, result);
//        int id;
//        if ((id = c.getColumnIndex("changed")) != -1) {
//            result.remove("changed");
//            result.put("changed", c.getLong(id));
//        }
        return result;
    }

    // events
    public void onInstantiated() {

    }

    public void onSaveRequest() {

    }

    public void onDeleteRequest() {

    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Model)) return false;
        if (id == null) return false;
        if (o == null) return false;
        Model model = (Model) o;

        if (!id.equals(model.id)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    protected boolean fireEvent(Class<? extends EventListener> event) {
        return fireEvent(this, event);
    }

    @Override
    protected boolean fireEvent(EventHub source, Class<? extends EventListener> event) {
        if (!super.fireEvent(source, event)) return false;

        if (!(BeforeEvent.class.equals(event.getClass()) ||
                AfterEvent.class.equals(event.getClass()))) {
            if (BeforeEvent.class.isAssignableFrom(event)) {
                if (!super.fireEvent(this, BeforeEvent.class)) return false;
            } else if (AfterEvent.class.isAssignableFrom(event)) {
                if (!super.fireEvent(this, AfterEvent.class)) return false;
            }
        }
        return true;
    }

    public static LinkedHashMap groupBy(List list, String field) throws NoSuchFieldException, IllegalAccessException {
        LinkedHashMap<Object, Object> result = new LinkedHashMap<Object, Object>();
        Field f;
        Object o;
        for (int i = 0; i < list.size(); i++) {
            o = list.get(i);
            f = o.getClass().getField(field);
            result.put(f.get(o), o);
        }
        return result;
    }

    public static interface BeforeEvent<X extends Model> extends EventListener<X> {
    }

    public static interface AfterEvent<X extends Model> extends EventListener<X> {
    }

    public static interface BeforeInsertEvent<X extends Model> extends BeforeEvent<X> {
    }

    public static interface BeforeUpdateEvent<X extends Model> extends BeforeEvent<X> {
    }

    public static interface BeforeDeleteEvent<X extends Model> extends BeforeEvent<X> {
    }

    public static interface AfterInsertEvent<X extends Model> extends AfterEvent<X> {
    }

    public static interface AfterUpdateEvent<X extends Model> extends AfterEvent<X> {
    }

    public static interface AfterDeleteEvent<X extends Model> extends AfterEvent<X> {
    }


}
