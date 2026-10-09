package com.local.smsmail;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class SmsReceiver extends BroadcastReceiver {
    private static final ExecutorService IO=Executors.newSingleThreadExecutor();
    @Override public void onReceive(Context context,Intent intent) {
        if(!Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction()) || !Config.enabled(context))return;
        PendingResult result=goAsync();
        IO.execute(()->{
            try {
                SmsMessage[] pieces=Telephony.Sms.Intents.getMessagesFromIntent(intent);
                if(pieces==null || pieces.length==0)return;
                String sender=pieces[0].getDisplayOriginatingAddress();if(sender==null)sender="未知号码";
                StringBuilder body=new StringBuilder();for(SmsMessage sms:pieces) if(sms.getMessageBody()!=null)body.append(sms.getMessageBody());
                Scheduler.sms(context,sender,pieces[0].getTimestampMillis(),body.toString(),intent.getIntExtra("subscription",-1));
            } catch(Exception e) { Config.enable(context,false); StatusNotice.show(context,"转发已停止","无法保存新短信，请打开应用检查设置和可用存储空间。"); }
            finally { result.finish(); }
        });
    }
}
