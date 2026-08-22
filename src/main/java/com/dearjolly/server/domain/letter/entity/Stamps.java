package com.dearjolly.server.domain.letter.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 우표 마스터 데이터. 우표 종류는 코드가 아니라 이 테이블의 행으로 관리하므로
 * 추가·교체에 배포가 필요 없다. name 은 LLM 이 고른 이름을 행으로
 * 되돌리는 조회 키라서 UNIQUE 다. 이미지는 파일 키만 저장하고 URL 은 조회 시점에 조립한다.
 *
 * <p>우표는 이름과 이미지가 전부다. 이름 자체가 이미 분위기를 담고 있어(장미·초승달)
 * LLM 이 편지에 어울리는 것을 고르는 데 별도 설명이 필요하지 않고,
 * 앱도 이미지만 렌더링하므로 노출 지점이 없다.
 *
 * <p>활성 여부 컬럼도 두지 않는다. 이 테이블에 있는 행은 전부 선택 후보다.
 * 우표를 내리려면 행을 지우는 대신 이미지를 교체하는 것으로 충분하고,
 * 실제로 지우려면 그 우표를 참조하는 편지가 없는지부터 확인해야 한다.
 *
 * <p>이미지는 URL 이 아니라 파일 키(예: {@code stamps/rose.png})만 저장한다.
 * 스토리지 주소는 환경마다 다르고 바뀔 수 있어서, 응답 시점에
 * {@code FileUrlProvider} 가 공개 엔드포인트를 붙여 URL 을 만든다.
 */
@Entity
@Table(name = "STAMPS")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Stamps {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "stamp_id")
    private Long id;

    @Column(name = "name", nullable = false, unique = true, length = 30)
    private String name;

    @Column(name = "image_key", nullable = false, length = 255)
    private String imageKey;

    // ========= 생성 메서드 =========
    public static Stamps create(String name, String imageKey) {
        return new Stamps(name, imageKey);
    }

    // ========= 비즈니스 로직 메서드 =========

    /** 이미지 교체는 파일 키 갱신으로 끝난다. 앱 배포도 서버 배포도 필요 없다. */
    public void updateImageKey(String imageKey) {
        validateImageKey(imageKey);
        this.imageKey = imageKey;
    }

    // ========= 생성자 =========
    private Stamps(String name, String imageKey) {
        validateName(name);
        validateImageKey(imageKey);
        this.name = name;
        this.imageKey = imageKey;
    }

    // ========= 검증 메서드 =========
    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("우표 이름은 필수입니다.");
        }
    }

    private void validateImageKey(String imageKey) {
        if (imageKey == null || imageKey.isBlank()) {
            throw new IllegalArgumentException("우표 이미지 파일 키는 필수입니다.");
        }
    }
}
