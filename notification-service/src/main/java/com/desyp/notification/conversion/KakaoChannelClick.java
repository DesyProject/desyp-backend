package com.desyp.notification.conversion;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;

/**
 * 사전 등록자가 카카오톡 채널 버튼을 누른 기록. 실제 채널 추가 완료를 뜻하지 않는다.
 * 저장은 {@link KakaoChannelClickRepository#insertIfAbsent}가 한다.
 */
@Entity
@Table(name = "kakao_channel_clicks")
@Getter
public class KakaoChannelClick {

    protected KakaoChannelClick() {
    }

    @Id
    private Long id;

    @Column(name = "subscriber_id", nullable = false, updatable = false)
    private Long subscriberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private ConversionSource source;

    @Column(name = "clicked_at", insertable = false, updatable = false)
    private OffsetDateTime clickedAt;
}
