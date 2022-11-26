package com.github.artyomcool.lodinfra.data;

import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Helpers {


    public static final Interpreter interpreter = new Interpreter(stdFunctions());

    private static final Pattern EVAL_PATTERN = Pattern.compile("\\$\\{([^}]*)}");
    public static final Pattern ARRAY = Pattern.compile("(.*)\\[(.*)]");

    private static Map<String, Function<List<Object>, Object>> stdFunctions() {
        Map<String, Function<List<Object>, Object>> stdFunctions = new HashMap<>();

        stdFunctions.put("size", objects -> {
            if (objects.size() != 1) {
                throw new IllegalArgumentException("Not supported: " + objects);
            }
            Object obj = objects.get(0);
            if (obj instanceof String) {
                return (long) ((String) obj).length();
            }
            if (obj instanceof List) {
                return (long) ((List<?>) obj).size();
            }
            return (long) ((Map) obj).size();
        });
        stdFunctions.put("get", objects -> {
            if (objects.size() != 2) {
                throw new IllegalArgumentException("Not supported: " + objects);
            }
            Object obj = objects.get(0);
            Object path = objects.get(1);
            if (obj instanceof List) {
                return ((List<?>) obj).get(Integer.parseInt(string(path)));
            }
            return ((Map) obj).get(string(path));
        });
        stdFunctions.put("regex", objects -> {
            String d = (String) objects.get(0);
            Pattern pattern = Pattern.compile((String) objects.get(1));
            Matcher matcher = pattern.matcher(d);
            if (!matcher.matches()) {
                return null;
            }
            return matcher.group(((Number) objects.get(2)).intValue());
        });
        stdFunctions.put("num", objects -> Long.parseLong(string(objects.get(0))));
        stdFunctions.put("pad", objects -> String.format("%" + Long.parseLong(string(objects.get(1))) + "s", string(objects.get(0))));
        return stdFunctions;
    }

    public static String string(Object o) {
        if (o instanceof List) {
            List<Integer> t = (List<Integer>) o;
            byte[] data = new byte[t.size()];
            for (int i = 0; i < t.size(); i++) {
                data[i] = t.get(i).byteValue();
            }
            return new String(data, Charset.forName("windows-1251"));
        }
        return String.valueOf(o);
    }

    public static String evalString(String str, Map<String, ?> params) {
        if (str.contains("${")) {
            Matcher m = EVAL_PATTERN.matcher(str);
            StringBuilder builder = new StringBuilder();
            while (m.find()) {
                Object e = interpreter.eval(m.group(1), params);
                m.appendReplacement(builder, String.valueOf(e).replace("$", "\\$"));
            }
            m.appendTail(builder);
            return builder.toString();
        }
        return str;
    }

    public static int intFromList(List<Integer> lst, int offset) {
        int t = offset;

        int v4 = lst.get(t++);
        int v3 = lst.get(t++);
        int v2 = lst.get(t++);
        int v1 = lst.get(t++);

        return v1 << 24 | v2 << 16 | v3 << 8 | v4;
    }
}
