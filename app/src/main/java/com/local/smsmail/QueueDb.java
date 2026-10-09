package com.local.smsmail;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public final class QueueDb extends SQLiteOpenHelper {
    private static QueueDb instance;
    public static synchronized QueueDb get(Context c) { if (instance==null) instance=new QueueDb(c.getApplicationContext()); return instance; }
    private QueueDb(Context c) { super(c,"queue.db",null,1); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE mail (id TEXT PRIMARY KEY, payload TEXT NOT NULL, created INTEGER NOT NULL, state TEXT NOT NULL, detail TEXT NOT NULL, is_test INTEGER NOT NULL DEFAULT 0)");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int a,int b) { throw new IllegalStateException("数据库版本暂不支持"); }
    public synchronized boolean insert(String id, JSONObject payload, boolean test) throws Exception {
        ContentValues v=new ContentValues();v.put("id",id);v.put("payload",Crypto.encrypt(payload.toString()));v.put("created",System.currentTimeMillis());
        v.put("state","queued");v.put("detail",test?"测试邮件":"新短信");v.put("is_test",test?1:0);
        return getWritableDatabase().insertWithOnConflict("mail",null,v,SQLiteDatabase.CONFLICT_IGNORE)!=-1;
    }
    public synchronized JSONObject payload(String id) throws Exception {
        try (Cursor c=getReadableDatabase().rawQuery("SELECT payload FROM mail WHERE id=? AND state!='sent'",new String[]{id})) {
            return c.moveToFirst()?new JSONObject(Crypto.decrypt(c.getString(0))):null;
        }
    }
    public synchronized boolean isTest(String id) { try(Cursor c=getReadableDatabase().rawQuery("SELECT is_test FROM mail WHERE id=?",new String[]{id})) {return c.moveToFirst() && c.getInt(0)==1;} }
    public synchronized void state(String id,String state,String detail) {
        ContentValues v=new ContentValues();v.put("state",state);v.put("detail",detail);
        if (state.equals("sent")) v.put("payload",""); // Wipe stored SMS and destination after success.
        getWritableDatabase().update("mail",v,"id=?",new String[]{id});
        getWritableDatabase().execSQL("DELETE FROM mail WHERE state='sent' AND id NOT IN (SELECT id FROM mail WHERE state='sent' ORDER BY created DESC LIMIT 200)");
    }
    public synchronized List<String> pending(boolean includeFailed,boolean includePaused) {
        List<String> ids=new ArrayList<>();
        String query="SELECT id FROM mail WHERE state IN ('queued','sending','retry'"+(includeFailed?",'failed'":"")+(includePaused?",'paused'":"")+") ORDER BY created";
        try(Cursor c=getReadableDatabase().rawQuery(query,null)) {while(c.moveToNext()) ids.add(c.getString(0));} return ids;
    }
    public synchronized List<String> history() {
        List<String> rows=new ArrayList<>();
        java.text.DateFormat df=new java.text.SimpleDateFormat("MM-dd HH:mm:ss",java.util.Locale.CHINA);
        try(Cursor c=getReadableDatabase().rawQuery("SELECT created,state,detail FROM mail ORDER BY created DESC LIMIT 20",null)) {
            while(c.moveToNext()) rows.add(df.format(new java.util.Date(c.getLong(0)))+"  "+label(c.getString(1))+"\n"+c.getString(2));
        } return rows;
    }
    public synchronized void deletePausedAndFailed() { getWritableDatabase().delete("mail","state IN ('paused','failed')",null); }
    private static String label(String s) {
        switch(s) {case "sent":return "已交给邮件服务器";case "sending":return "发送中";case "retry":return "等待重试";case "failed":return "发送失败";case "paused":return "已暂停";default:return "等待联网 / 调度";}
    }
}
