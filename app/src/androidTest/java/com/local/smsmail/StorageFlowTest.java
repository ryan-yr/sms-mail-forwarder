package com.local.smsmail;

import android.content.Context;
import android.content.Intent;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.work.WorkManager;
import org.junit.*;
import org.junit.runner.RunWith;
import org.json.JSONObject;
import static org.junit.Assert.*;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public final class StorageFlowTest {
    private Context context;
    @Before public void setup() throws Exception {
        context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        Config.enable(context,false);
        WorkManager.getInstance(context).cancelAllWork().getResult().get(5,TimeUnit.SECONDS);
        QueueDb.get(context).getWritableDatabase().delete("mail",null,null);
        Config cfg=fixture();cfg.save(context);
    }
    private Config fixture(){Config c=new Config();c.host="10.0.2.2";c.port=465;c.account="fixture@example.org";c.password="fixture-only-password";c.recipient="recipient@example.org";return c;}
    @Test public void credentialStorageIsEncrypted() throws Exception {
        String stored=context.getSharedPreferences("settings",0).getString("smtp","");
        assertFalse(stored.contains("fixture-only-password"));assertEquals("fixture-only-password",Config.load(context).password);
    }
    @Test public void queuePreservesLongUnicodeAndWipesSuccessfulPayload() throws Exception {
        String body="短信正文。".repeat(3000);JSONObject data=new JSONObject();data.put("body",body);data.put("recipient","recipient@example.org");
        QueueDb db=QueueDb.get(context);assertTrue(db.insert("long-body",data,false));
        assertEquals(body,db.payload("long-body").getString("body"));
        try(android.database.Cursor c=db.getReadableDatabase().rawQuery("SELECT payload FROM mail WHERE id='long-body'",null)){assertTrue(c.moveToFirst());assertFalse(c.getString(0).contains("短信正文"));}
        db.state("long-body","sent","complete");assertNull(db.payload("long-body"));
        try(android.database.Cursor c=db.getReadableDatabase().rawQuery("SELECT payload FROM mail WHERE id='long-body'",null)){assertTrue(c.moveToFirst());assertEquals("",c.getString(0));}
    }
    @Test public void disabledForwarderDoesNotQueueSms() throws Exception {
        Scheduler.sms(context,"1068",1,"ignored",-1);assertEquals(0,QueueDb.get(context).pending(false,false).size());
    }
    @Test public void duplicateSmsAndRecipientSnapshot() throws Exception {
        Config.enable(context,true);Scheduler.sms(context,"1068",2,"queued while offline",-1);Scheduler.sms(context,"1068",2,"queued while offline",-1);
        assertEquals(1,QueueDb.get(context).pending(false,false).size());
        Config changed=fixture();changed.recipient="changed@example.org";changed.save(context);
        assertEquals("recipient@example.org",QueueDb.get(context).payload(Logic.id("1068",2,"queued while offline")).getString("recipient"));Config.enable(context,false);
    }
    @Test public void activityLaunches() {
        android.app.Instrumentation instrument=InstrumentationRegistry.getInstrumentation();
        android.app.Activity activity=instrument.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        assertNotNull(activity);instrument.runOnMainSync(activity::finish);
    }
    @Test public void prepareSmsFixture() {Config.enable(context,true);}
}
