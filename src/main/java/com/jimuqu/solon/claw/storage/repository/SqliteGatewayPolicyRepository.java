package com.jimuqu.solon.claw.storage.repository;

import com.jimuqu.solon.claw.core.enums.PlatformType;
import com.jimuqu.solon.claw.core.model.ApprovedUserRecord;
import com.jimuqu.solon.claw.core.model.HomeChannelRecord;
import com.jimuqu.solon.claw.core.model.PairingRateLimitRecord;
import com.jimuqu.solon.claw.core.model.PairingRequestRecord;
import com.jimuqu.solon.claw.core.model.PlatformAdminRecord;
import com.jimuqu.solon.claw.core.repository.GatewayPolicyRepository;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;

/** SqliteGatewayPolicyRepository 实现。 */
@RequiredArgsConstructor
public class SqliteGatewayPolicyRepository implements GatewayPolicyRepository {
    public static final String ADMIN_CLAIM_CODE = "__ADMIN_CLAIM__";

    private final SqliteDatabase database;

    public HomeChannelRecord getHomeChannel(PlatformType platform) throws Exception {
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "select platform, chat_id, chat_name, updated_at from home_channels where platform = ?");
            statement.setString(1, key(platform));
            ResultSet resultSet = statement.executeQuery();
            try {
                return resultSet.next() ? mapHome(resultSet) : null;
            } finally {
                resultSet.close();
                statement.close();
            }
        } finally {
            connection.close();
        }
    }

    public void saveHomeChannel(HomeChannelRecord record) throws Exception {
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "insert or replace into home_channels (platform, chat_id, chat_name, updated_at) values (?, ?, ?, ?)");
            statement.setString(1, key(record.getPlatform()));
            statement.setString(2, record.getChatId());
            statement.setString(3, record.getChatName());
            statement.setLong(4, record.getUpdatedAt());
            statement.executeUpdate();
            statement.close();
        } finally {
            connection.close();
        }
    }

    public PlatformAdminRecord getPlatformAdmin(PlatformType platform) throws Exception {
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "select platform, user_id, user_name, chat_id, created_at from platform_admins where platform = ?");
            statement.setString(1, key(platform));
            ResultSet resultSet = statement.executeQuery();
            try {
                return resultSet.next() ? mapAdmin(resultSet) : null;
            } finally {
                resultSet.close();
                statement.close();
            }
        } finally {
            connection.close();
        }
    }

    public boolean createPlatformAdminIfAbsent(PlatformAdminRecord record) throws Exception {
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "insert or ignore into platform_admins (platform, user_id, user_name, chat_id, created_at) values (?, ?, ?, ?, ?)");
            statement.setString(1, key(record.getPlatform()));
            statement.setString(2, record.getUserId());
            statement.setString(3, record.getUserName());
            statement.setString(4, record.getChatId());
            statement.setLong(5, record.getCreatedAt());
            int affected = statement.executeUpdate();
            statement.close();
            return affected > 0;
        } finally {
            connection.close();
        }
    }

    public ApprovedUserRecord getApprovedUser(PlatformType platform, String userId)
            throws Exception {
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "select platform, user_id, user_name, approved_at, approved_by from approved_users where platform = ? and user_id = ?");
            statement.setString(1, key(platform));
            statement.setString(2, userId);
            ResultSet resultSet = statement.executeQuery();
            try {
                return resultSet.next() ? mapApproved(resultSet) : null;
            } finally {
                resultSet.close();
                statement.close();
            }
        } finally {
            connection.close();
        }
    }

    public void saveApprovedUser(ApprovedUserRecord record) throws Exception {
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "insert or replace into approved_users (platform, user_id, user_name, approved_at, approved_by) values (?, ?, ?, ?, ?)");
            statement.setString(1, key(record.getPlatform()));
            statement.setString(2, record.getUserId());
            statement.setString(3, record.getUserName());
            statement.setLong(4, record.getApprovedAt());
            statement.setString(5, record.getApprovedBy());
            statement.executeUpdate();
            statement.close();
        } finally {
            connection.close();
        }
    }

    public void revokeApprovedUser(PlatformType platform, String userId) throws Exception {
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "delete from approved_users where platform = ? and user_id = ?");
            statement.setString(1, key(platform));
            statement.setString(2, userId);
            statement.executeUpdate();
            statement.close();
        } finally {
            connection.close();
        }
    }

    public List<ApprovedUserRecord> listApprovedUsers(PlatformType platform) throws Exception {
        List<ApprovedUserRecord> list = new ArrayList<ApprovedUserRecord>();
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "select platform, user_id, user_name, approved_at, approved_by from approved_users where platform = ? order by approved_at asc");
            statement.setString(1, key(platform));
            ResultSet resultSet = statement.executeQuery();
            try {
                while (resultSet.next()) {
                    list.add(mapApproved(resultSet));
                }
            } finally {
                resultSet.close();
                statement.close();
            }
        } finally {
            connection.close();
        }
        return list;
    }

    public int countApprovedUsers(PlatformType platform) throws Exception {
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "select count(*) from approved_users where platform = ?");
            statement.setString(1, key(platform));
            ResultSet resultSet = statement.executeQuery();
            try {
                return resultSet.next() ? resultSet.getInt(1) : 0;
            } finally {
                resultSet.close();
                statement.close();
            }
        } finally {
            connection.close();
        }
    }

    public PairingRequestRecord getPairingRequest(PlatformType platform, String code)
            throws Exception {
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "select platform, code, user_id, user_name, chat_id, created_at, expires_at from pairing_requests where platform = ? and code = ?");
            statement.setString(1, key(platform));
            statement.setString(2, code);
            ResultSet resultSet = statement.executeQuery();
            try {
                return resultSet.next() ? mapPairing(resultSet) : null;
            } finally {
                resultSet.close();
                statement.close();
            }
        } finally {
            connection.close();
        }
    }

    public PairingRequestRecord getAdminClaimRequest(PlatformType platform) throws Exception {
        return getPairingRequest(platform, ADMIN_CLAIM_CODE);
    }

    public PairingRequestRecord getLatestUserPairingRequest(PlatformType platform, String userId)
            throws Exception {
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "select platform, code, user_id, user_name, chat_id, created_at, expires_at from pairing_requests where platform = ? and user_id = ? and code <> ? order by created_at desc limit 1");
            statement.setString(1, key(platform));
            statement.setString(2, userId);
            statement.setString(3, ADMIN_CLAIM_CODE);
            ResultSet resultSet = statement.executeQuery();
            try {
                return resultSet.next() ? mapPairing(resultSet) : null;
            } finally {
                resultSet.close();
                statement.close();
            }
        } finally {
            connection.close();
        }
    }

    public void savePairingRequest(PairingRequestRecord record) throws Exception {
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "insert or replace into pairing_requests (platform, code, user_id, user_name, chat_id, created_at, expires_at) values (?, ?, ?, ?, ?, ?, ?)");
            statement.setString(1, key(record.getPlatform()));
            statement.setString(2, record.getCode());
            statement.setString(3, record.getUserId());
            statement.setString(4, record.getUserName());
            statement.setString(5, record.getChatId());
            statement.setLong(6, record.getCreatedAt());
            statement.setLong(7, record.getExpiresAt());
            statement.executeUpdate();
            statement.close();
        } finally {
            connection.close();
        }
    }

    public boolean createAdminClaimRequestIfAbsent(PairingRequestRecord record) throws Exception {
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "insert or ignore into pairing_requests (platform, code, user_id, user_name, chat_id, created_at, expires_at) values (?, ?, ?, ?, ?, ?, ?)");
            statement.setString(1, key(record.getPlatform()));
            statement.setString(2, ADMIN_CLAIM_CODE);
            statement.setString(3, record.getUserId());
            statement.setString(4, record.getUserName());
            statement.setString(5, record.getChatId());
            statement.setLong(6, record.getCreatedAt());
            statement.setLong(7, record.getExpiresAt());
            try {
                return statement.executeUpdate() > 0;
            } finally {
                statement.close();
            }
        } finally {
            connection.close();
        }
    }

    public void deletePairingRequest(PlatformType platform, String code) throws Exception {
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "delete from pairing_requests where platform = ? and code = ?");
            statement.setString(1, key(platform));
            statement.setString(2, code);
            statement.executeUpdate();
            statement.close();
        } finally {
            connection.close();
        }
    }

    public void deleteExpiredPairingRequests(PlatformType platform, long nowEpochMillis)
            throws Exception {
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "delete from pairing_requests where platform = ? and expires_at < ?");
            statement.setString(1, key(platform));
            statement.setLong(2, nowEpochMillis);
            statement.executeUpdate();
            statement.close();
        } finally {
            connection.close();
        }
    }

    public List<PairingRequestRecord> listPairingRequests(
            PlatformType platform, boolean includeAdminClaim) throws Exception {
        List<PairingRequestRecord> list = new ArrayList<PairingRequestRecord>();
        Connection connection = database.openConnection();
        try {
            String sql =
                    includeAdminClaim
                            ? "select platform, code, user_id, user_name, chat_id, created_at, expires_at from pairing_requests where platform = ? order by created_at asc"
                            : "select platform, code, user_id, user_name, chat_id, created_at, expires_at from pairing_requests where platform = ? and code <> ? order by created_at asc";
            PreparedStatement statement = connection.prepareStatement(sql);
            statement.setString(1, key(platform));
            if (!includeAdminClaim) {
                statement.setString(2, ADMIN_CLAIM_CODE);
            }
            ResultSet resultSet = statement.executeQuery();
            try {
                while (resultSet.next()) {
                    list.add(mapPairing(resultSet));
                }
            } finally {
                resultSet.close();
                statement.close();
            }
        } finally {
            connection.close();
        }
        return list;
    }

    public PairingRateLimitRecord getPairingRateLimit(PlatformType platform, String userId)
            throws Exception {
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "select platform, user_id, requested_at, failed_attempts, lockout_until from pairing_rate_limits where platform = ? and user_id = ?");
            statement.setString(1, key(platform));
            statement.setString(2, userId);
            ResultSet resultSet = statement.executeQuery();
            try {
                return resultSet.next() ? mapRateLimit(resultSet) : null;
            } finally {
                resultSet.close();
                statement.close();
            }
        } finally {
            connection.close();
        }
    }

    public void savePairingRateLimit(PairingRateLimitRecord record) throws Exception {
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "insert or replace into pairing_rate_limits (platform, user_id, requested_at, failed_attempts, lockout_until) values (?, ?, ?, ?, ?)");
            statement.setString(1, key(record.getPlatform()));
            statement.setString(2, record.getUserId());
            statement.setLong(3, record.getRequestedAt());
            statement.setInt(4, record.getFailedAttempts());
            statement.setLong(5, record.getLockoutUntil());
            statement.executeUpdate();
            statement.close();
        } finally {
            connection.close();
        }
    }

    private String key(PlatformType platform) {
        return platform == null ? "UNKNOWN" : platform.name();
    }

    private HomeChannelRecord mapHome(ResultSet resultSet) throws Exception {
        HomeChannelRecord record = new HomeChannelRecord();
        record.setPlatform(PlatformType.fromName(resultSet.getString("platform")));
        record.setChatId(resultSet.getString("chat_id"));
        record.setChatName(resultSet.getString("chat_name"));
        record.setUpdatedAt(resultSet.getLong("updated_at"));
        return record;
    }

    private ApprovedUserRecord mapApproved(ResultSet resultSet) throws Exception {
        ApprovedUserRecord record = new ApprovedUserRecord();
        record.setPlatform(PlatformType.fromName(resultSet.getString("platform")));
        record.setUserId(resultSet.getString("user_id"));
        record.setUserName(resultSet.getString("user_name"));
        record.setApprovedAt(resultSet.getLong("approved_at"));
        record.setApprovedBy(resultSet.getString("approved_by"));
        return record;
    }

    private PairingRequestRecord mapPairing(ResultSet resultSet) throws Exception {
        PairingRequestRecord record = new PairingRequestRecord();
        record.setPlatform(PlatformType.fromName(resultSet.getString("platform")));
        record.setCode(resultSet.getString("code"));
        record.setUserId(resultSet.getString("user_id"));
        record.setUserName(resultSet.getString("user_name"));
        record.setChatId(resultSet.getString("chat_id"));
        record.setCreatedAt(resultSet.getLong("created_at"));
        record.setExpiresAt(resultSet.getLong("expires_at"));
        return record;
    }

    private PlatformAdminRecord mapAdmin(ResultSet resultSet) throws Exception {
        PlatformAdminRecord record = new PlatformAdminRecord();
        record.setPlatform(PlatformType.fromName(resultSet.getString("platform")));
        record.setUserId(resultSet.getString("user_id"));
        record.setUserName(resultSet.getString("user_name"));
        record.setChatId(resultSet.getString("chat_id"));
        record.setCreatedAt(resultSet.getLong("created_at"));
        return record;
    }

    private PairingRateLimitRecord mapRateLimit(ResultSet resultSet) throws Exception {
        PairingRateLimitRecord record = new PairingRateLimitRecord();
        record.setPlatform(PlatformType.fromName(resultSet.getString("platform")));
        record.setUserId(resultSet.getString("user_id"));
        record.setRequestedAt(resultSet.getLong("requested_at"));
        record.setFailedAttempts(resultSet.getInt("failed_attempts"));
        record.setLockoutUntil(resultSet.getLong("lockout_until"));
        return record;
    }
}
