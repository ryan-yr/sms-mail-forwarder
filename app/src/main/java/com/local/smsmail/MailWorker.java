package com.local.smsmail;

import android.content.Context;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import androidx.annotation.NonNull;
import androidx.work.*;
import org.json.JSONObject;
import javax.mail.AuthenticationFailedException;
import javax.mail.SendFailedException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class MailWorker extends Worker {
    public MailWorker(@NonNull Context c,@NonNull WorkerParameters p) {super(c,p);}
    @NonNull @Override public ForegroundInfo getForegroundInfo() {
        Context c=getApplicationContext();NotificationManager manager=c.getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel("sending","邮件发送",NotificationManager.IMPORTANCE_LOW));
        Notification n=new Notification.Builder(c,"sending").setSmallIcon(R.drawable.ic_mail).setContentTitle("短信邮递").setContentText("正在发送邮件").build();
        return new ForegroundInfo(2,n);
    }
    @NonNull @Override public Result doWork() {
        Context c=getApplicationContext();String id=getInputData().getString("id");if(id==null)return Result.failure();
        QueueDb db=QueueDb.get(c);
        try {
            JSONObject data=db.payload(id);if(data==null)return Result.success();
            boolean test=db.isTest(id);
            if(!test && !Config.enabled(c)) {db.state(id,"paused","转发开关已关闭；开启后可重试");return Result.success();}
            Config cfg=Config.load(c);cfg.validate();
            String sender=data.getString("sender");
            String time=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z",Locale.CHINA).format(new Date(data.getLong("time")));
            String body="发送号码："+sender+"\n接收时间："+time+"\n设备："+android.os.Build.MANUFACTURER+" "+android.os.Build.MODEL+"\n";
            int subscription=data.optInt("subscription",-1);
            if(subscription>=0)body+="SIM 订阅编号："+subscription+"\n";
            body+="\n短信正文：\n"+data.getString("body")+"\n\n转发编号："+id;
            db.state(id,"sending",test?"测试邮件":"短信来自 "+Logic.masked(sender));
            if(isStopped())return Result.retry();
            if(!test && !Config.enabled(c)) {db.state(id,"paused","转发开关已关闭");return Result.success();}
            SmtpSender.send(cfg,data.getString("recipient"),test?"短信邮递 · 测试邮件":"新短信 · "+Logic.masked(sender),body,id);
            db.state(id,"sent",test?"测试邮件发送成功":"短信邮件发送成功");
            if(test)StatusNotice.show(c,"测试邮件已发送","请检查收件箱或垃圾邮件文件夹。");
            return Result.success();
        } catch(AuthenticationFailedException e) {
            db.state(id,"failed","邮箱认证失败：检查 SMTP 服务和授权码");StatusNotice.show(c,"邮件发送失败","邮箱认证失败，请检查设置后重试。");return Result.failure();
        } catch(SendFailedException e) {
            db.state(id,"failed","邮件服务器拒绝发件或收件地址，请检查设置");StatusNotice.show(c,"邮件发送失败","邮件服务器拒绝发送，请检查邮箱设置。");return Result.failure();
        } catch(IllegalArgumentException e) {
            db.state(id,"failed","邮箱配置无效，请重新保存设置");return Result.failure();
        } catch(Exception e) {
            String reason=reason(e);
            if(getRunAttemptCount()>=9) {db.state(id,"failed",reason+"；已达到 10 次尝试，可手动重试");StatusNotice.show(c,"邮件发送失败","请打开应用检查网络与 SMTP 设置后重试。");return Result.failure();}
            db.state(id,"retry",reason+"；第 "+(getRunAttemptCount()+1)+" 次尝试未成功");return Result.retry();
        }
    }
    private String reason(Exception e) {
        Throwable t=e;
        for(int i=0;t!=null && i<8;i++,t=t.getCause()) {
            if(t instanceof javax.net.ssl.SSLException)return "TLS 安全连接失败，请检查服务器和加密方式";
            if(t instanceof java.net.UnknownHostException)return "无法解析 SMTP 服务器，请检查域名或网络";
            if(t instanceof java.net.SocketTimeoutException)return "SMTP 连接超时";
        }
        return "发送未成功（"+e.getClass().getSimpleName()+"），请检查网络和邮箱设置";
    }
}
