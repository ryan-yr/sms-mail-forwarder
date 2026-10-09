package com.local.smsmail;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;

public final class Config {
    public String host="smtp.qq.com", account="", password="", recipient="", senders="", keyword="";
    public int port=465;
    public boolean startTls=false;
    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences("settings", Context.MODE_PRIVATE); }
    public static Config load(Context context) throws Exception {
        Config c = new Config(); String raw=prefs(context).getString("smtp", ""); if (raw.isEmpty()) return c;
        JSONObject o = new JSONObject(Crypto.decrypt(raw));
        c.host=o.getString("host"); c.account=o.getString("account"); c.password=o.getString("password");
        c.recipient=o.getString("recipient"); c.port=o.getInt("port"); c.startTls=o.getBoolean("startTls");
        c.senders=o.optString("senders"); c.keyword=o.optString("keyword"); return c;
    }
    public void validate() {
        if (!Logic.host(host)) throw new IllegalArgumentException("请填写有效的 SMTP 服务器域名");
        if (!Logic.email(account)) throw new IllegalArgumentException("发件邮箱格式不正确");
        if (!Logic.email(recipient)) throw new IllegalArgumentException("收件邮箱格式不正确（仅支持一个邮箱）");
        if (password.isEmpty()) throw new IllegalArgumentException("请填写邮箱的 SMTP 授权码或应用密码");
        if (port<1 || port>65535) throw new IllegalArgumentException("端口必须在 1–65535 之间");
    }
    public void save(Context context) throws Exception {
        validate(); JSONObject o=new JSONObject(); o.put("host",host);o.put("account",account);o.put("password",password);o.put("recipient",recipient);
        o.put("port",port);o.put("startTls",startTls);o.put("senders",senders);o.put("keyword",keyword);
        if (!prefs(context).edit().putString("smtp",Crypto.encrypt(o.toString())).commit()) throw new IllegalStateException("设置保存失败");
    }
    public static boolean enabled(Context c) { return prefs(c).getBoolean("enabled",false); }
    @android.annotation.SuppressLint("ApplySharedPref") // Persist the switch before the receiver may run.
    public static void enable(Context c, boolean enabled) { prefs(c).edit().putBoolean("enabled",enabled).commit(); }
}
