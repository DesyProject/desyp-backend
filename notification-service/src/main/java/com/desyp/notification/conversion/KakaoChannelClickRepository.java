package com.desyp.notification.conversion;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface KakaoChannelClickRepository extends JpaRepository<KakaoChannelClick, Long> {

    // 동시 요청도 (subscriber_id, source) UNIQUE 제약이 막고, 충돌은 오류 없이 무시해 최초 클릭 시각만 남긴다.
    @Modifying
    @Query(value = """
            INSERT INTO kakao_channel_clicks (subscriber_id, source) VALUES (:subscriberId, :source)
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("subscriberId") Long subscriberId, @Param("source") String source);
}
