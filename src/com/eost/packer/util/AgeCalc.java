package com.eost.packer.util;

import java.util.Calendar;

public class AgeCalc {
    public static String gradeNow() {
        int birthYear = 2014;
        int birthMonth = 9;
        Calendar c = Calendar.getInstance();
        int y = c.get(Calendar.YEAR);
        int m = c.get(Calendar.MONTH) + 1;
        int age = y - birthYear - (m < birthMonth ? 1 : 0);
        int grade = (m >= 9) ? age - 5 : age - 6; // 9月前读六年级，9月后读初一
        if (grade < 1) grade = 1;
        if (grade > 12) grade = 12;
        String[] names = {"", "一年级", "二年级", "三年级", "四年级", "五年级", "六年级",
                "初一", "初二", "初三", "高一", "高二", "高三"};
        return "开发者当前 " + age + " 岁 · " + names[grade] + " · 正在迭代 EOST Packer";
    }
}
