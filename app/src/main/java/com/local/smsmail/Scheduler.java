package com.local.smsmail;

import android.content.Context;
import androidx.work.*;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;

public final class Scheduler {
    public static void enqueue(Context c,String id) {
        OneTimeWorkRequest task=new OneTimeWorkRequest.Builder(MailWorker.class)
            .setInputData(new Data.Builder().putString("id",id).build())
            .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL,30,TimeUnit.SECONDS)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST).addTag("mail").build();
        WorkManager.getInstance(c).enqueueUniqueWork("mail-"+id,ExistingWorkPolicy.KEEP,task);
    }
    public static void recover(Context c,boolean manual) {
        for(String id:QueueDb.get(c).pending(manual,manual || Config.enabled(c))) enqueue(c,id);
    }
    public static void sms(Context c,String sender,long time,String body,int subscription) throws Exception {
        if(!Config.enabled(c))return;
        Config cfg=Config.load(c);cfg.validate();
        if(!Logic.matches(sender,body,cfg.senders,cfg.keyword))return;
        JSONObject data=new JSONObject();data.put("sender",sender);data.put("time",time);data.put("body",body);data.put("recipient",cfg.recipient);data.put("subscription",subscription);
        String id=Logic.id(sender,time,body);
        if(QueueDb.get(c).insert(id,data,false))enqueue(c,id);
    }
    public static void test(Context c) throws Exception {
        Config cfg=Config.load(c);cfg.validate();String id=java.util.UUID.randomUUID().toString();
        JSONObject data=new JSONObject();data.put("sender","测试");data.put("time",System.currentTimeMillis());data.put("body","这是一封短信邮递的测试邮件。收到此邮件说明 SMTP 设置可以正常使用。真实短信转发还需要短信权限和转发开关。");data.put("recipient",cfg.recipient);data.put("subscription",-1);
        QueueDb.get(c).insert(id,data,true);enqueue(c,id);
    }
    private Scheduler() {}
}
