package cn.boxadb.tests;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Looper;
import java.lang.reflect.Method;
import java.util.Locale;

/** Runs with app_process in an isolated configuration; never changes device language. */
public class LocaleTest {
    public static void main(String[] args) throws Exception {
        Looper.prepareMainLooper();
        Class<?> threadClass = Class.forName("android.app.ActivityThread");
        Method systemMain = threadClass.getDeclaredMethod("systemMain");
        systemMain.setAccessible(true);
        Object thread = systemMain.invoke(null);
        Method systemContext = threadClass.getDeclaredMethod("getSystemContext");
        systemContext.setAccessible(true);
        Context context = ((Context) systemContext.invoke(thread)).createPackageContext("cn.boxadb.helper", 0);
        String[] tags = {"en-US", "en-GB", "fr-FR", "zh-CN", "zh-SG", "zh-Hans-CN", "zh-TW", "zh-HK", "zh-MO", "zh-Hant-TW"};
        boolean[] chinese = {false, false, false, true, true, true, false, false, false, false};
        int labelId = context.getPackageManager().getApplicationInfo("cn.boxadb.helper", 0).labelRes;
        for (int i = 0; i < tags.length; i++) {
            Configuration config = new Configuration(context.getResources().getConfiguration());
            config.setLocale(Locale.forLanguageTag(tags[i]));
            Resources localized = new Resources(context.getAssets(), context.getResources().getDisplayMetrics(), config);
            String label = localized.getString(labelId);
            String expected = chinese[i] ? "盒子 ADB 助手" : "Box ADB Helper";
            if (!expected.equals(label)) throw new AssertionError(tags[i] + ": label " + label);
            int stepId = localized.getIdentifier("step1_body", "string", "cn.boxadb.helper");
            String step = localized.getString(stepId);
            if (chinese[i] && !step.equals("点击上方调试设置按钮，开启USB调试")) throw new AssertionError(tags[i] + ": step1");
            if (!chinese[i] && !step.equals("Select Debug settings above; enable USB debugging.")) throw new AssertionError(tags[i] + ": step1");
            int portId = localized.getIdentifier("port_label", "string", "cn.boxadb.helper");
            if (!localized.getString(portId, 5555).contains("5555")) throw new AssertionError("Port formatting");
            System.out.println("PASS " + tags[i] + " | " + label);
        }
        System.out.println("All 10 locale cases passed. Device language unchanged.");
    }
}
