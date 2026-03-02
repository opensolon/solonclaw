package com.jimuqu.solonclaw.common;

import java.util.HashMap;
import java.util.Map;

/**
 * 统一响应结果
 */
public class Result {
    private int code;
    private String message;
    private Object data;

    public Result() {
    }

    public Result(int code, String message, Object data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static Result success() {
        return new Result(200, "Success", null);
    }

    public static Result success(String message) {
        return new Result(200, message, null);
    }

    public static Result success(String message, Object data) {
        return new Result(200, message, data);
    }

    public static Result success(Object data) {
        return new Result(200, "Success", data);
    }

    public static Result failure(String message) {
        return new Result(500, message, null);
    }

    public static Result error(String message) {
        return new Result(500, message, null);
    }

    public static Result error(int code, String message) {
        return new Result(code, message, null);
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("code", code);
        map.put("message", message);
        if (data != null) {
            map.put("data", data);
        }
        return map;
    }
}