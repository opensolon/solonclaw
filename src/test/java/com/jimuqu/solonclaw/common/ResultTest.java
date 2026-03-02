package com.jimuqu.solonclaw.common;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Result 单元测试
 *
 * @author SolonClaw
 */
class ResultTest {

    @Test
    void testDefaultConstructor() {
        Result result = new Result();

        assertEquals(0, result.getCode());
        assertNull(result.getMessage());
        assertNull(result.getData());
    }

    @Test
    void testConstructorWithAllParams() {
        Result result = new Result(200, "Success", "test-data");

        assertEquals(200, result.getCode());
        assertEquals("Success", result.getMessage());
        assertEquals("test-data", result.getData());
    }

    @Test
    void testSuccessWithoutMessage() {
        Result result = Result.success();

        assertEquals(200, result.getCode());
        assertEquals("Success", result.getMessage());
        assertNull(result.getData());
    }

    @Test
    void testSuccessWithMessage() {
        Result result = Result.success("操作成功");

        assertEquals(200, result.getCode());
        assertEquals("操作成功", result.getMessage());
        assertNull(result.getData());
    }

    @Test
    void testSuccessWithMessageAndData() {
        Object data = Map.of("key", "value");
        Result result = Result.success("操作成功", data);

        assertEquals(200, result.getCode());
        assertEquals("操作成功", result.getMessage());
        assertEquals(data, result.getData());
    }

    @Test
    void testErrorWithMessage() {
        Result result = Result.error("发生错误");

        assertEquals(500, result.getCode());
        assertEquals("发生错误", result.getMessage());
        assertNull(result.getData());
    }

    @Test
    void testErrorWithCodeAndMessage() {
        Result result = Result.error(404, "未找到");

        assertEquals(404, result.getCode());
        assertEquals("未找到", result.getMessage());
        assertNull(result.getData());
    }

    @Test
    void testSetCode() {
        Result result = new Result();
        result.setCode(201);

        assertEquals(201, result.getCode());
    }

    @Test
    void testSetMessage() {
        Result result = new Result();
        result.setMessage("测试消息");

        assertEquals("测试消息", result.getMessage());
    }

    @Test
    void testSetData() {
        Result result = new Result();
        Object data = "test-data";
        result.setData(data);

        assertEquals(data, result.getData());
    }

    @Test
    void testToMapWithNullData() {
        Result result = new Result(200, "Success", null);
        Map<String, Object> map = result.toMap();

        assertEquals(2, map.size());
        assertEquals(200, map.get("code"));
        assertEquals("Success", map.get("message"));
        assertFalse(map.containsKey("data"));
    }

    @Test
    void testToMapWithData() {
        Object data = Map.of("key", "value");
        Result result = new Result(200, "Success", data);
        Map<String, Object> map = result.toMap();

        assertEquals(3, map.size());
        assertEquals(200, map.get("code"));
        assertEquals("Success", map.get("message"));
        assertEquals(data, map.get("data"));
    }

    @Test
    void testToMapWithEmptyStringData() {
        Result result = new Result(200, "Success", "");
        Map<String, Object> map = result.toMap();

        assertEquals(3, map.size());
        assertTrue(map.containsKey("data"));
        assertEquals("", map.get("data"));
    }

    @Test
    void testToMapWithListData() {
        java.util.List<String> data = java.util.List.of("item1", "item2");
        Result result = Result.success("成功", data);
        Map<String, Object> map = result.toMap();

        assertEquals(3, map.size());
        assertEquals(data, map.get("data"));
    }

    @Test
    void testToMapWithComplexData() {
        Map<String, Object> complexData = new java.util.HashMap<>();
        complexData.put("string", "value");
        complexData.put("number", 123);
        complexData.put("boolean", true);

        Result result = Result.success("成功", complexData);
        Map<String, Object> map = result.toMap();

        assertEquals(3, map.size());
        assertEquals(complexData, map.get("data"));
    }

    @Test
    void testIndividualSetters() {
        Result result = new Result();
        result.setCode(201);
        result.setMessage("Created");
        result.setData("new-data");

        assertEquals(201, result.getCode());
        assertEquals("Created", result.getMessage());
        assertEquals("new-data", result.getData());
    }

    @Test
    void testCodeCanBeZero() {
        Result result = new Result(0, "Zero Code", null);

        assertEquals(0, result.getCode());
    }

    @Test
    void testCodeCanBeNegative() {
        Result result = new Result(-1, "Error", null);

        assertEquals(-1, result.getCode());
    }

    @Test
    void testMessageCanBeEmpty() {
        Result result = new Result(200, "", null);

        assertEquals("", result.getMessage());
    }

    @Test
    void testDataCanBeZero() {
        Result result = new Result(200, "Zero Data", 0);

        assertEquals(0, result.getData());
    }

    @Test
    void testDataCanBeFalse() {
        Result result = new Result(200, "False Data", false);

        assertEquals(false, result.getData());
    }

    @Test
    void testToMapDoesNotModifyOriginal() {
        Object data = Map.of("key", "value");
        Result result = new Result(200, "Success", data);

        Map<String, Object> map1 = result.toMap();
        Map<String, Object> map2 = result.toMap();

        // 两次调用应该返回不同的 Map 对象
        assertNotSame(map1, map2);
        // 但内容应该相同
        assertEquals(map1, map2);
    }

    @Test
    void testSuccessMethodReturnsNewInstance() {
        Result result1 = Result.success();
        Result result2 = Result.success();

        // 应该是不同的实例
        assertNotSame(result1, result2);
        // 但内容应该相同
        assertEquals(result1.getCode(), result2.getCode());
        assertEquals(result1.getMessage(), result2.getMessage());
    }

    @Test
    void testErrorMethodReturnsNewInstance() {
        Result result1 = Result.error("Error");
        Result result2 = Result.error("Error");

        // 应该是不同的实例
        assertNotSame(result1, result2);
        // 但内容应该相同
        assertEquals(result1.getCode(), result2.getCode());
        assertEquals(result1.getMessage(), result2.getMessage());
    }
}