package com.jimuqu.solonclaw.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 角色权限测试
 *
 * @author SolonClaw
 */
@DisplayName("用户角色权限测试")
class UserRoleTest {

    @Nested
    @DisplayName("角色权限检查")
    class PermissionTests {

        @Test
        @DisplayName("ADMIN 角色应拥有所有权限")
        void adminShouldHaveAllPermissions() {
            assertTrue(UserRole.ADMIN.hasPermission(UserRole.ADMIN), "ADMIN 应拥有 ADMIN 权限");
            assertTrue(UserRole.ADMIN.hasPermission(UserRole.USER), "ADMIN 应拥有 USER 权限");
            assertTrue(UserRole.ADMIN.hasPermission(UserRole.GUEST), "ADMIN 应拥有 GUEST 权限");
        }

        @Test
        @DisplayName("USER 角色应拥有 USER 和 GUEST 权限")
        void userShouldHaveUserAndGuestPermissions() {
            assertFalse(UserRole.USER.hasPermission(UserRole.ADMIN), "USER 不应拥有 ADMIN 权限");
            assertTrue(UserRole.USER.hasPermission(UserRole.USER), "USER 应拥有 USER 权限");
            assertTrue(UserRole.USER.hasPermission(UserRole.GUEST), "USER 应拥有 GUEST 权限");
        }

        @Test
        @DisplayName("GUEST 角色应只拥有 GUEST 权限")
        void guestShouldOnlyHaveGuestPermission() {
            assertFalse(UserRole.GUEST.hasPermission(UserRole.ADMIN), "GUEST 不应拥有 ADMIN 权限");
            assertFalse(UserRole.GUEST.hasPermission(UserRole.USER), "GUEST 不应拥有 USER 权限");
            assertTrue(UserRole.GUEST.hasPermission(UserRole.GUEST), "GUEST 应拥有 GUEST 权限");
        }

        @Test
        @DisplayName("角色等级应为 ADMIN > USER > GUEST")
        void roleLevelsShouldBeCorrect() {
            assertEquals(100, UserRole.ADMIN.getLevel(), "ADMIN 等级应为 100");
            assertEquals(50, UserRole.USER.getLevel(), "USER 等级应为 50");
            assertEquals(10, UserRole.GUEST.getLevel(), "GUEST 等级应为 10");
        }
    }

    @Nested
    @DisplayName("用户信息")
    class UserInfoTests {

        @Test
        @DisplayName("创建用户应生成唯一 ID")
        void createUserShouldHaveUniqueId() {
            User user1 = new User();
            User user2 = new User();

            assertNotNull(user1.getId(), "用户 ID 不应为空");
            assertNotNull(user2.getId(), "用户 ID 不应为空");
            assertNotEquals(user1.getId(), user2.getId(), "不同用户的 ID 应不同");
        }

        @Test
        @DisplayName("新用户默认角色应为 USER")
        void newUserShouldHaveDefaultUserRole() {
            User user = new User();
            assertEquals(UserRole.USER, user.getRole(), "新用户默认角色应为 USER");
        }

        @Test
        @DisplayName("新用户默认应为启用状态")
        void newUserShouldBeEnabledByDefault() {
            User user = new User();
            assertTrue(user.isEnabled(), "新用户默认应为启用状态");
        }
    }
}