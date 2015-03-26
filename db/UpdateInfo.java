package anypackage;

public class UpdateInfo {

    public static final String[][] updates = {
//          version 1
            new String[] {
                    "CREATE TABLE table1 (id TEXT PRIMARY KEY,title TEXT,type TEXT NOT NULL,deleted INTEGER DEFAULT 0 NOT NULL,changed integer DEFAULT CURRENT_TIMESTAMP NOT NULL)",
            }

//          Next version
    };


}
