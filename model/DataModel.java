package anypackage;

import android.content.ContentValues;

import java.util.ArrayList;
import java.util.Map;

public interface DataModel {

    public ArrayList<ContentValues> exportData(long from);

    public boolean importData(ArrayList<Model> model);

    public void save();
    public void delete();

    // events
    public void onInstantiated();
    public void onSaveRequest();
    public void onDeleteRequest();
}
