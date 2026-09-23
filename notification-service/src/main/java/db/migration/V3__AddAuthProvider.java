package db.migration;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/**
 * H2(테스트)와 PostgreSQL(운영)이 V1의 인라인 UNIQUE 제약에 서로 다른 이름을 자동으로 붙여서
 * 이름을 하드코딩한 SQL로는 이식성 있게 제거할 수 없다. information_schema로 실제 이름을 찾아 지운다.
 */
public class V3__AddAuthProvider extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE subscribers RENAME COLUMN google_sub TO provider_account_id");
            statement.execute("ALTER TABLE subscribers ADD COLUMN provider TEXT NOT NULL DEFAULT 'GOOGLE'");
            statement.execute("ALTER TABLE subscribers ALTER COLUMN provider DROP DEFAULT");
            statement.execute("ALTER TABLE subscribers DROP CONSTRAINT "
                    + findUniqueConstraintName(connection, "provider_account_id"));
            statement.execute("ALTER TABLE subscribers ADD CONSTRAINT uq_subscribers_provider_account "
                    + "UNIQUE (provider, provider_account_id)");
        }
    }

    private String findUniqueConstraintName(Connection connection, String columnName) throws SQLException {
        String sql = """
                SELECT tc.constraint_name
                FROM information_schema.table_constraints tc
                JOIN information_schema.key_column_usage kcu
                     ON tc.constraint_name = kcu.constraint_name AND tc.table_name = kcu.table_name
                WHERE UPPER(tc.table_name) = 'SUBSCRIBERS' AND tc.constraint_type = 'UNIQUE'
                      AND UPPER(kcu.column_name) = UPPER(?)
                """;
        try (var preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, columnName);
            try (var resultSet = preparedStatement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new IllegalStateException("No unique constraint found on subscribers." + columnName);
                }
                return resultSet.getString(1);
            }
        }
    }
}
