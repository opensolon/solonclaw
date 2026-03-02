package com.jimuqu.solonclaw.auth;

import org.noear.solon.annotation.*;
import io.swagger.annotations.*;
import org.noear.solon.annotation.Param;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * 认证接口控制器
 * <p>
 * 提供用户登录、注册、Token 刷新等认证相关接口
 *
 * @author SolonClaw
 */
@Api(tags = "认证接口")
@Controller
@Mapping("/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    @Inject
    private UserService userService;

    @Inject
    private JwtTokenService jwtTokenService;

    /**
     * 用户登录
     * <p>
     * 用户通过用户名和密码登录，返回访问 Token 和用户信息
     */
    @ApiOperation(value = "用户登录", notes = "通过用户名和密码登录，返回访问 Token")
    @Post
    @Mapping("/login")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "username", value = "用户名", required = true),
            @ApiImplicitParam(name = "password", value = "密码", required = true)
    })
    public LoginResult login(
            @Param(value = "用户名", required = true) String username,
            @Param(value = "密码", required = true) String password
    ) {
        log.info("用户登录请求: username={}", username);

        try {
            User user = userService.authenticate(username, password);

            // 生成 Token
            String token = jwtTokenService.generateToken(user);

            log.info("用户登录成功: username={}, userId={}", username, user.getId());

            return LoginResult.success("登录成功", token, UserInfo.of(user));

        } catch (AuthException e) {
            log.warn("用户登录失败: username={}, error={}", username, e.getMessage());
            return LoginResult.error(e.getMessage());

        } catch (Exception e) {
            log.error("登录处理异常", e);
            return LoginResult.error("登录失败，请稍后重试");
        }
    }

    /**
     * 用户注册
     * <p>
     * 创建新用户账户
     */
    @ApiOperation(value = "用户注册", notes = "注册新用户账户")
    @Post
    @Mapping("/register")
    public RegisterResult register(
            @Param(value = "用户名", required = true) String username,
            @Param(value = "密码", required = true) String password,
            @Param(value = "邮箱", required = true) String email
    ) {
        log.info("用户注册请求: username={}, email={}", username, email);

        try {
            // 验证输入
            if (username == null || username.trim().isEmpty()) {
                return RegisterResult.error("用户名不能为空");
            }
            if (password == null || password.length() < 6) {
                return RegisterResult.error("密码长度不能少于 6 位");
            }
            if (email == null || !email.matches("^[\\w.-]+@[\\w.-]+\\.[a-zA-Z]{2,}$")) {
                return RegisterResult.error("邮箱格式不正确");
            }

            // 创建用户（默认为普通用户）
            User user = userService.createUser(username, password, email, UserRole.USER);

            log.info("用户注册成功: username={}, userId={}", username, user.getId());

            return RegisterResult.success("注册成功", UserInfo.of(user));

        } catch (AuthException e) {
            log.warn("用户注册失败: username={}, error={}", username, e.getMessage());
            return RegisterResult.error(e.getMessage());

        } catch (Exception e) {
            log.error("注册处理异常", e);
            return RegisterResult.error("注册失败，请稍后重试");
        }
    }

    /**
     * 刷新 Token
     * <p>
     * 使用旧 Token 获取新的访问 Token
     */
    @ApiOperation(value = "刷新 Token", notes = "使用旧 Token 获取新的访问 Token")
    @Post
    @Mapping("/refresh")
    public RefreshResult refresh(
            @Param(value = "旧的 Token", required = true) String token
    ) {
        log.info("Token 刷新请求");

        try {
            // 从 Token 中获取用户信息
            String userId = jwtTokenService.getUserIdFromToken(token);
            User user = userService.findByUsername(userId);
            if (user == null) {
                return RefreshResult.error("用户不存在");
            }

            // 检查用户是否被禁用
            if (!user.isEnabled()) {
                return RefreshResult.error("账户已被禁用");
            }

            // 刷新 Token
            String newToken = jwtTokenService.refreshToken(token, user);

            log.info("Token 刷新成功: userId={}", userId);

            return RefreshResult.success("刷新成功", newToken);

        } catch (AuthException e) {
            log.warn("Token 刷新失败: {}", e.getMessage());
            return RefreshResult.error(e.getMessage());

        } catch (Exception e) {
            log.error("Token 刷新异常", e);
            return RefreshResult.error("刷新失败，请稍后重试");
        }
    }

    /**
     * 获取当前用户信息
     * <p>
     * 根据 Token 获取当前登录用户的详细信息
     */
    @ApiOperation(value = "获取当前用户信息", notes = "根据 Token 获取当前登录用户的详细信息")
    @Get
    @Mapping("/me")
    public MeResult getCurrentUser(
            @Header("Authorization") String authHeader
    ) {
        try {
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return MeResult.error("未提供有效的认证 Token");
            }

            String token = authHeader.substring(7);
            String userId = jwtTokenService.getUserIdFromToken(token);

            User user = userService.findByUsername(userId);
            if (user == null) {
                return MeResult.error("用户不存在");
            }

            return MeResult.success(UserInfo.of(user));

        } catch (AuthException e) {
            return MeResult.error(e.getMessage());

        } catch (Exception e) {
            log.error("获取用户信息异常", e);
            return MeResult.error("获取用户信息失败");
        }
    }

    /**
     * 登录结果
     */
    public record LoginResult(
            int code,
            String message,
            String token,
            UserInfo user
    ) {
        public static LoginResult success(String message, String token, UserInfo user) {
            return new LoginResult(200, message, token, user);
        }

        public static LoginResult error(String message) {
            return new LoginResult(400, message, null, null);
        }
    }

    /**
     * 注册结果
     */
    public record RegisterResult(
            int code,
            String message,
            UserInfo user
    ) {
        public static RegisterResult success(String message, UserInfo user) {
            return new RegisterResult(200, message, user);
        }

        public static RegisterResult error(String message) {
            return new RegisterResult(400, message, null);
        }
    }

    /**
     * 刷新 Token 结果
     */
    public record RefreshResult(
            int code,
            String message,
            String token
    ) {
        public static RefreshResult success(String message, String token) {
            return new RefreshResult(200, message, token);
        }

        public static RefreshResult error(String message) {
            return new RefreshResult(400, message, null);
        }
    }

    /**
     * 获取当前用户信息结果
     */
    public record MeResult(
            int code,
            String message,
            UserInfo user
    ) {
        public static MeResult success(UserInfo user) {
            return new MeResult(200, "获取成功", user);
        }

        public static MeResult error(String message) {
            return new MeResult(400, message, null);
        }
    }

    /**
     * 用户信息（精简版，不含密码等敏感信息）
     */
    public record UserInfo(
            String id,
            String username,
            String email,
            String role,
            String apiKey,
            boolean enabled
    ) {
        public static UserInfo of(User user) {
            return new UserInfo(
                    user.getId(),
                    user.getUsername(),
                    user.getEmail(),
                    user.getRole().name(),
                    maskApiKey(user.getApiKey()),
                    user.isEnabled()
            );
        }

        /**
         * 隐藏 API 密钥，只显示前 8 位
         */
        private static String maskApiKey(String apiKey) {
            if (apiKey == null) return null;
            if (apiKey.length() <= 10) return "sk-****";
            return apiKey.substring(0, 10) + "****";
        }
    }
}