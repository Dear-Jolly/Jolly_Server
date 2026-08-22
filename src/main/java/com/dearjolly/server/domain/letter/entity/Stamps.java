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
 * 추가·교체·문구 수정에 배포가 필요 없다. name 은 LLM 이 고른 이름을 행으로
 * 되돌리는 조회 키라서 UNIQUE 다. 이미지는 파일 키만 저장하고 URL 은 조회 시점에 조립한다.
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

    @Column(name = "description", nullable = false, length = 100)
    private String description;

    @Column(name = "image_key", nullable = false, length = 255)
    private String imageKey;

    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    // ========= 생성 메서드 =========
    public static Stamps create(String name, String description, String imageKey) {
        return new Stamps(name, description, imageKey);
    }

    // ========= 비즈니스 로직 메서드 =========

    /**
     * 운영을 중단할 우표는 삭제하지 않고 내린다. LETTERS.stamp_id 가 참조하는
     * 행이 사라지면 안 되기 때문이다. 이미 부여된 우표는 그대로 유지된다.
     */
    public void deactivate() {
        this.isActive = false;
    }

    public void activate() {
        this.isActive = true;
    }

    public void updateImageKey(String imageKey) {
        validateImageKey(imageKey);
        this.imageKey = imageKey;
    }

    public void updateDescription(String description) {
        validateDescription(description);
        this.description = description;
    }

    // ========= 생성자 =========
    private Stamps(String name, String description, String imageKey) {
        validateName(name);
        validateDescription(description);
        validateImageKey(imageKey);
        this.name = name;
        this.description = description;
        this.imageKey = imageKey;
        this.isActive = true;
    }

    // ========= 검증 메서드 =========
    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("우표 이름은 필수입니다.");
        }
    }

    private void validateDescription(String description) {
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("우표 설명은 필수입니다.");
        }
    }

    private void validateImageKey(String imageKey) {
        if (imageKey == null || imageKey.isBlank()) {
            throw new IllegalArgumentException("우표 이미지 파일 키는 필수입니다.");
        }
    }
}
