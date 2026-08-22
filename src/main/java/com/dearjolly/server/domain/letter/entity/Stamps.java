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

    public static Stamps create(String name, String imageKey) {
        return new Stamps(name, imageKey);
    }

    public void updateImageKey(String imageKey) {
        validateImageKey(imageKey);
        this.imageKey = imageKey;
    }

    private Stamps(String name, String imageKey) {
        validateName(name);
        validateImageKey(imageKey);
        this.name = name;
        this.imageKey = imageKey;
    }

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
