package com.desyp.notification.referral;

import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReferralMigrationTest {
    @Test
    void existingReferralsReceiveOneBonusAndDatabaseRejectsInvalidBonuses() throws Exception {
        String url = "jdbc:h2:mem:referral-migration;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url, "sa", "").target("1").load().migrate();
        try (var connection = DriverManager.getConnection(url, "sa", ""); var sql = connection.createStatement()) {
            sql.execute("""
                    INSERT INTO subscribers (id,google_sub,email,email_normalized,invite_token,age_confirmed,consent_at)
                    VALUES (1,'a','a@example.com','a@example.com','token-a',true,CURRENT_TIMESTAMP)
                    """);
            sql.execute("""
                    INSERT INTO subscribers (id,google_sub,email,email_normalized,invite_token,age_confirmed,consent_at,referrer_id)
                    VALUES (2,'b','b@example.com','b@example.com','token-b',true,CURRENT_TIMESTAMP,1)
                    """);
            Flyway.configure().dataSource(url, "sa", "").load().migrate();
            try (var result = sql.executeQuery("SELECT referral_bonus FROM subscribers ORDER BY id")) {
                assertThat(result.next()).isTrue();assertThat(result.getInt(1)).isZero();
                assertThat(result.next()).isTrue();assertThat(result.getInt(1)).isEqualTo(1);
            }
            assertThatThrownBy(() -> sql.executeUpdate("UPDATE subscribers SET referral_bonus=2 WHERE id=2"))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> sql.executeUpdate("UPDATE subscribers SET referral_bonus=1 WHERE id=1"))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> sql.executeUpdate("UPDATE subscribers SET referrer_id=id WHERE id=2"))
                    .isInstanceOf(java.sql.SQLException.class);
        }
    }
}
