package edu.buffalo.cse.cse486_586.simpledynamo;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public class DynamoOpenHelper extends SQLiteOpenHelper {

	private static final int DB_VERSION = 2;
	private static final String DB_NAME = "dynamo.db";
	private static final String MSG_TABLE_NAME = "fl";
	private static final String MSG_SEND_NAME = "provider_key";
	private static final String MSG_CONTENT = "provider_value";
	private static final String MSG_TABLE_CREATE = 
			"CREATE TABLE " + MSG_TABLE_NAME + " (" +
					MSG_SEND_NAME + " TEXT, " +
					MSG_CONTENT + " TEXT);";

	public DynamoOpenHelper(Context context) {
		super(context, DB_NAME, null, DB_VERSION);
	}
	
	public DynamoOpenHelper(Context context, String dbname) {
		super(context, dbname, null, DB_VERSION);
	}
	
	@Override
	public void onCreate(SQLiteDatabase db) {
		db.execSQL(MSG_TABLE_CREATE);
	}
	
	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		Log.i("log", "Upgrade database...");
		// XXX We are not here yet.
	}

}
