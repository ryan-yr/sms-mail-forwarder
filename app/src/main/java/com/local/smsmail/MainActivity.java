package com.local.smsmail;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.view.WindowInsets;
import android.widget.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private final int green=Color.rgb(18,107,85), ink=Color.rgb(29,47,40), muted=Color.rgb(98,113,105);
    private EditText account,password,recipient,host,port,senders,keyword;
    private Spinner encryption;
    private Switch enabled;
    private TextView status,permissionState,history;
    private LinearLayout content;
    private boolean updating=false;
    private final Handler handler=new Handler(android.os.Looper.getMainLooper());
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final Runnable refresh=new Runnable(){@Override public void run(){refreshStatus();handler.postDelayed(this,3000);}};
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
        content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(24),dp(20),dp(24),dp(30));scroll.addView(content);
        scroll.setOnApplyWindowInsetsListener((v,insets)->{
            int top,bottom;
            if(Build.VERSION.SDK_INT>=30){android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.ime());top=bars.top;bottom=bars.bottom;}
            else{top=insets.getSystemWindowInsetTop();bottom=insets.getSystemWindowInsetBottom();}
            v.setPadding(0,top,0,bottom);return insets;
        });
        setContentView(scroll);scroll.requestApplyInsets();
        text(content,"SMS → EMAIL",12,green,true);
        TextView title=text(content,"短信邮递",32,ink,true);title.setPadding(0,dp(8),0,0);
        text(content,"收到短信，自动送达你的邮箱。",15,muted,false);
        LinearLayout control=card();
        status=text(control,"自动转发已关闭",20,ink,true);
        text(control,"开启后转发新短信；号码和关键词留空时，转发全部短信，包括验证码。",13,muted,false);
        enabled=new Switch(this);enabled.setText("启用自动转发");enabled.setTextSize(16);control.addView(enabled);
        enabled.setOnCheckedChangeListener((b,on)->{
            if(updating)return;
            if(!on){Config.enable(this,false);refreshStatus();toast("已暂停转发，正在发送的邮件可能已经提交");return;}
            if(!save()) {setSwitch(false);return;}
            if(checkSelfPermission(Manifest.permission.RECEIVE_SMS)!=PackageManager.PERMISSION_GRANTED){setSwitch(false);requestPermissions(new String[]{Manifest.permission.RECEIVE_SMS},100);}
            else enableNow();
        });
        permissionState=text(control,"",12,muted,false);
        LinearLayout form=card();text(form,"01  邮箱设置",18,ink,true);
        account=field(form,"发件 QQ 邮箱","123456@qq.com",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        password=field(form,"QQ 邮箱授权码","在 QQ 邮箱设置中生成",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        password.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        text(form,"使用 SMTP 授权码，填写后仅在手机本地加密保存。",12,muted,false);
        recipient=field(form,"收件邮箱","可以是其他邮箱服务",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        button(form,"保存设置",true,()->save());
        button(form,"发送测试邮件",false,()->{
            if(!save())return;
            if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},101);
            io.execute(()->{try{Scheduler.test(this);runOnUiThread(()->{toast("测试邮件已排队，请查看发送记录");refreshStatus();});}catch(Exception e){runOnUiThread(()->error("无法创建测试邮件，请检查设置和存储空间。"));}});
        });
        button(form,"如何获取 QQ 邮箱授权码",false,()->new AlertDialog.Builder(this).setTitle("QQ 邮箱授权码")
            .setMessage("登录 mail.qq.com，点击右上角头像 → 设置 → 账号与安全 → 安全设置，开启 POP3 / IMAP / SMTP 服务，然后点击“生成授权码”。菜单名称以当前网页为准。\n\n把生成的授权码填入本应用，不需要在聊天里发送。\n\n默认服务器：smtp.qq.com\n端口：465\n加密：SSL/TLS")
            .setPositiveButton("打开 QQ 邮箱",(d,w)->open(new Intent(Intent.ACTION_VIEW,Uri.parse("https://mail.qq.com"))))
            .setNegativeButton("知道了",null).show());
        LinearLayout rules=card();text(rules,"02  可选筛选",18,ink,true);
        senders=field(rules,"只转发这些号码","留空转发全部；多个号码用逗号隔开",InputType.TYPE_CLASS_TEXT);
        keyword=field(rules,"正文包含关键词","留空不筛选正文",InputType.TYPE_CLASS_TEXT);
        text(rules,"号码按短信显示的发送号码精确匹配；两个条件同时满足才转发。修改后请保存。",12,muted,false);
        LinearLayout advanced=card();text(advanced,"03  SMTP 参数",18,ink,true);
        host=field(advanced,"服务器","smtp.qq.com",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        port=field(advanced,"端口","465",InputType.TYPE_CLASS_NUMBER);
        text(advanced,"加密方式",12,muted,true);encryption=new Spinner(this);
        ArrayAdapter<String> options=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,new String[]{"SSL/TLS（QQ 默认 465）","STARTTLS（通常 587）"});options.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);encryption.setAdapter(options);advanced.addView(encryption);
        text(advanced,"QQ 邮箱通常保持默认参数即可。切换加密方式时，请同时修改端口。",12,muted,false);
        LinearLayout battery=card();text(battery,"04  三星后台设置",18,ink,true);
        text(battery,"在应用信息 → 电池中选择“不受限制”。在电池 → 后台使用限制中，确保本应用不在休眠或深度休眠列表；如果列表允许，加入“从不自动休眠的应用”。菜单位置可能随系统版本变化。",13,muted,false);
        button(battery,"打开本应用的系统设置",false,()->open(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName()))));
        text(battery,"重启后先解锁手机。若点过“强行停止”，需重新打开本应用。后台调度或部分验证码的系统保护可能延迟转发。",12,muted,false);
        LinearLayout records=card();text(records,"05  最近发送记录",18,ink,true);
        history=text(records,"暂无记录",13,ink,false);history.setTextIsSelectable(true);
        button(records,"重试待发 / 失败邮件",false,()->{
            io.execute(()->{Scheduler.recover(this,true);runOnUiThread(()->{toast("已安排重试；普通短信需要保持转发开启");refreshStatus();});});
        });
        button(records,"删除暂停和失败的邮件",false,()->new AlertDialog.Builder(this).setTitle("删除未发送邮件？").setMessage("将删除暂停和失败邮件中的短信正文。这些邮件不会再发送。排队或发送中的任务保留。")
            .setPositiveButton("删除",(d,w)->{QueueDb.get(this).deletePausedAndFailed();refreshStatus();}).setNegativeButton("取消",null).show());
        text(content,"短信直接从手机发送到邮箱服务器。无需部署服务器。仅处理安装并开启后的新短信，不支持彩信或 RCS 聊天消息。",12,muted,false);
        text(content,"短信邮递  1.0.0 · 个人安装版",11,muted,false);
        try{Config c=Config.load(this);account.setText(c.account);password.setText(c.password);recipient.setText(c.recipient);host.setText(c.host);port.setText(String.format(java.util.Locale.ROOT,"%d",c.port));encryption.setSelection(c.startTls?1:0);senders.setText(c.senders);keyword.setText(c.keyword);}
        catch(Exception e){Config.enable(this,false);error("无法读取本地邮箱配置，请重新填写并保存。");}
        refreshStatus();
    }
    private LinearLayout card(){LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(18),dp(16),dp(18),dp(16));GradientDrawable bg=new GradientDrawable();bg.setColor(Color.WHITE);bg.setCornerRadius(dp(18));box.setBackground(bg);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(20);content.addView(box,lp);return box;}
    private TextView text(LinearLayout parent,String value,int size,int color,boolean bold){TextView v=new TextView(this);v.setText(value);v.setTextSize(size);v.setTextColor(color);v.setLineSpacing(dp(3),1);v.setPadding(0,dp(4),0,dp(6));if(bold)v.setTypeface(null,Typeface.BOLD);parent.addView(v);return v;}
    private EditText field(LinearLayout parent,String label,String hint,int type){text(parent,label,13,muted,true);EditText f=new EditText(this);f.setSingleLine(true);f.setTextSize(16);f.setTextColor(ink);f.setHint(hint);f.setInputType(type);f.setPadding(dp(10),dp(8),dp(10),dp(8));parent.addView(f,new LinearLayout.LayoutParams(-1,dp(52)));return f;}
    private void button(LinearLayout parent,String label,boolean primary,Runnable action){Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextSize(14);b.setTextColor(primary?Color.WHITE:green);GradientDrawable bg=new GradientDrawable();bg.setCornerRadius(dp(10));bg.setColor(primary?green:Color.rgb(235,244,238));b.setBackground(bg);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(50));lp.topMargin=dp(12);parent.addView(b,lp);b.setOnClickListener(v->action.run());}
    private boolean save(){try{Config c=new Config();c.account=account.getText().toString().trim();c.password=password.getText().toString().trim();c.recipient=recipient.getText().toString().trim();c.host=host.getText().toString().trim();c.port=Integer.parseInt(port.getText().toString().trim());c.startTls=encryption.getSelectedItemPosition()==1;c.senders=senders.getText().toString().trim();c.keyword=keyword.getText().toString();c.save(this);toast("设置已保存");return true;}catch(NumberFormatException e){error("请填写有效的 SMTP 端口");return false;}catch(IllegalArgumentException e){error(e.getMessage());return false;}catch(Exception e){error("保存失败，请检查手机存储空间后重试。");return false;}}
    private void enableNow(){Config.enable(this,true);setSwitch(true);io.execute(()->Scheduler.recover(this,false));refreshStatus();toast("已启用，新收到的短信会自动转发");}
    private void setSwitch(boolean on){updating=true;enabled.setChecked(on);updating=false;}
    private void refreshStatus(){if(status==null)return;boolean allowed=checkSelfPermission(Manifest.permission.RECEIVE_SMS)==PackageManager.PERMISSION_GRANTED;if(!allowed)Config.enable(this,false);boolean on=Config.enabled(this);setSwitch(on);status.setText(on?"自动转发已开启":"自动转发已关闭");status.setTextColor(on?green:ink);permissionState.setText(allowed?"短信权限：已允许":"短信权限：未允许（开启时申请）");List<String> rows=QueueDb.get(this).history();history.setText(rows.isEmpty()?"暂无记录。先发一封测试邮件，再从另一部手机发短信验证。":String.join("\n\n",rows));}
    private void open(Intent intent){try{startActivity(intent);}catch(Exception e){error("无法打开，请在系统设置或浏览器中手动打开。");}}
    private void toast(String t){Toast.makeText(this,t,Toast.LENGTH_SHORT).show();}
    private void error(String t){new AlertDialog.Builder(this).setTitle("需要检查").setMessage(t).setPositiveButton("知道了",null).show();}
    @Override public void onRequestPermissionsResult(int code,String[] perms,int[] results){super.onRequestPermissionsResult(code,perms,results);if(code==100){if(results.length>0 && results[0]==PackageManager.PERMISSION_GRANTED)enableNow();else{setSwitch(false);error("需要允许短信权限才能接收新短信。如果系统不再弹出申请窗口，请在应用信息 → 权限中开启。部分系统安装限制也可能阻止授权。");}}}
    @Override protected void onResume(){super.onResume();handler.removeCallbacks(refresh);handler.post(refresh);}
    @Override protected void onPause(){handler.removeCallbacks(refresh);super.onPause();}
    @Override protected void onDestroy(){handler.removeCallbacks(refresh);io.shutdown();super.onDestroy();}
}
