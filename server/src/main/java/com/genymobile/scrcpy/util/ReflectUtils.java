package com.genymobile.scrcpy.util;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;

public class ReflectUtils {
    
    public static void printAllMethods(Class<?> clazz) {
        try {
            FileOutputStream stdout = new FileOutputStream(FileDescriptor.out);
            stdout.write(clazz.toString().getBytes());
            stdout.write("\n".getBytes());
            Method[] methods = clazz.getDeclaredMethods();
            
            for (Method method : methods) {
                stdout.write(method.toString().getBytes());
                stdout.write("\n".getBytes());
            }
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * 打印对象的所有字段及其值
     * @param obj 要打印的对象
     * @param includeParentFields 是否包含父类的字段
     */
    public static void printObjectFields(Object obj, boolean includeParentFields) {
        try {
            if (obj == null) {
                System.out.println("Object is null");
                return;
            }
            
            FileOutputStream stdout = new FileOutputStream(FileDescriptor.out);
            Class<?> clazz = obj.getClass();
            
            stdout.write(("Object of class " + clazz.getName() + ":\n").getBytes());
            stdout.write("------------------------\n".getBytes());
            
            // 处理当前类及其所有父类的字段
            Class<?> currentClass = clazz;
            while (currentClass != null) {
                stdout.write(("Fields from " + currentClass.getName() + ":\n").getBytes());
                
                Field[] fields = currentClass.getDeclaredFields();
                Arrays.sort(fields, (f1, f2) -> f1.getName().compareTo(f2.getName())); // 字母排序
                
                for (Field field : fields) {
                    field.setAccessible(true); // 允许访问私有字段
                    
                    // 构建字段描述
                    StringBuilder fieldInfo = new StringBuilder();
                    fieldInfo.append("  ")
                            .append(Modifier.toString(field.getModifiers()))
                            .append(" ")
                            .append(field.getType().getName())
                            .append(" ")
                            .append(field.getName())
                            .append(" = ");
                    
                    // 获取字段值
                    try {
                        Object value = field.get(obj);
                        if (value == null) {
                            fieldInfo.append("null");
                        } else if (value.getClass().isArray()) {
                            fieldInfo.append(formatArray(value));
                        } else {
                            fieldInfo.append(value.toString());
                        }
                    } catch (Exception e) {
                        fieldInfo.append("[Exception: ").append(e.getMessage()).append("]");
                    }
                    
                    fieldInfo.append("\n");
                    stdout.write(fieldInfo.toString().getBytes());
                }
                
                // 如果不包含父类字段，则退出循环
                if (!includeParentFields) {
                    break;
                }
                
                currentClass = currentClass.getSuperclass();
                if (currentClass != null && currentClass != Object.class) {
                    stdout.write("\n".getBytes());
                } else {
                    break; // 已到达Object类或没有父类，停止遍历
                }
            }
            
            stdout.write("------------------------\n".getBytes());
            stdout.flush();
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * 打印指定类的所有静态字段及其值
     * @param clazz 要打印静态字段的类
     */
    public static void printClassStaticFields(Class<?> clazz) {
        try {
            FileOutputStream stdout = new FileOutputStream(FileDescriptor.out);
            
            stdout.write(("Static fields of class " + clazz.getName() + ":\n").getBytes());
            stdout.write("------------------------\n".getBytes());
            
            Field[] fields = clazz.getDeclaredFields();
            Arrays.sort(fields, (f1, f2) -> f1.getName().compareTo(f2.getName())); // 字母排序
            
            for (Field field : fields) {
                // 只处理静态字段
                if (!Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                
                field.setAccessible(true);
                
                StringBuilder fieldInfo = new StringBuilder();
                fieldInfo.append("  ")
                        .append(Modifier.toString(field.getModifiers()))
                        .append(" ")
                        .append(field.getType().getName())
                        .append(" ")
                        .append(field.getName())
                        .append(" = ");
                
                try {
                    Object value = field.get(null); // 静态字段，对象参数为null
                    if (value == null) {
                        fieldInfo.append("null");
                    } else if (value.getClass().isArray()) {
                        fieldInfo.append(formatArray(value));
                    } else {
                        fieldInfo.append(value.toString());
                    }
                } catch (Exception e) {
                    fieldInfo.append("[Exception: ").append(e.getMessage()).append("]");
                }
                
                fieldInfo.append("\n");
                stdout.write(fieldInfo.toString().getBytes());
            }
            
            stdout.write("------------------------\n".getBytes());
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * 格式化数组对象为字符串
     */
    private static String formatArray(Object array) {
        if (array == null) {
            return "null";
        }
        
        StringBuilder result = new StringBuilder("[");
        
        if (array instanceof boolean[]) {
            boolean[] arr = (boolean[]) array;
            for (int i = 0; i < Math.min(arr.length, 10); i++) {
                if (i > 0) result.append(", ");
                result.append(arr[i]);
            }
            if (arr.length > 10) result.append(", ... (").append(arr.length - 10).append(" more)");
        } else if (array instanceof byte[]) {
            byte[] arr = (byte[]) array;
            for (int i = 0; i < Math.min(arr.length, 10); i++) {
                if (i > 0) result.append(", ");
                result.append(arr[i]);
            }
            if (arr.length > 10) result.append(", ... (").append(arr.length - 10).append(" more)");
        } else if (array instanceof char[]) {
            char[] arr = (char[]) array;
            for (int i = 0; i < Math.min(arr.length, 10); i++) {
                if (i > 0) result.append(", ");
                result.append('\'').append(arr[i]).append('\'');
            }
            if (arr.length > 10) result.append(", ... (").append(arr.length - 10).append(" more)");
        } else if (array instanceof short[]) {
            short[] arr = (short[]) array;
            for (int i = 0; i < Math.min(arr.length, 10); i++) {
                if (i > 0) result.append(", ");
                result.append(arr[i]);
            }
            if (arr.length > 10) result.append(", ... (").append(arr.length - 10).append(" more)");
        } else if (array instanceof int[]) {
            int[] arr = (int[]) array;
            for (int i = 0; i < Math.min(arr.length, 10); i++) {
                if (i > 0) result.append(", ");
                result.append(arr[i]);
            }
            if (arr.length > 10) result.append(", ... (").append(arr.length - 10).append(" more)");
        } else if (array instanceof long[]) {
            long[] arr = (long[]) array;
            for (int i = 0; i < Math.min(arr.length, 10); i++) {
                if (i > 0) result.append(", ");
                result.append(arr[i]).append("L");
            }
            if (arr.length > 10) result.append(", ... (").append(arr.length - 10).append(" more)");
        } else if (array instanceof float[]) {
            float[] arr = (float[]) array;
            for (int i = 0; i < Math.min(arr.length, 10); i++) {
                if (i > 0) result.append(", ");
                result.append(arr[i]).append("f");
            }
            if (arr.length > 10) result.append(", ... (").append(arr.length - 10).append(" more)");
        } else if (array instanceof double[]) {
            double[] arr = (double[]) array;
            for (int i = 0; i < Math.min(arr.length, 10); i++) {
                if (i > 0) result.append(", ");
                result.append(arr[i]).append("d");
            }
            if (arr.length > 10) result.append(", ... (").append(arr.length - 10).append(" more)");
        } else if (array instanceof Object[]) {
            Object[] arr = (Object[]) array;
            for (int i = 0; i < Math.min(arr.length, 10); i++) {
                if (i > 0) result.append(", ");
                result.append(arr[i] == null ? "null" : arr[i].toString());
            }
            if (arr.length > 10) result.append(", ... (").append(arr.length - 10).append(" more)");
        } else {
            result.append("Unsupported array type: ").append(array.getClass().getName());
        }
        
        result.append("]");
        return result.toString();
    }
}