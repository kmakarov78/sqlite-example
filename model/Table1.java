package anypackage;

import java.util.UUID;

public class Table1 extends Model {
    public String title;
    public String type;

    @Override
    public String getTableName() {
        return "table1";
    }

    @Override
    public Model getInstance() {
        return new Feedback();
    }

    public static List<Table1> getAll() {
        return new LinkedList<Table1>(Model.loadAll(Table1.class));
    }


}
